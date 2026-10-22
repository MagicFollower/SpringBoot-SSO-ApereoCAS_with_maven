package com.example.admin.service;

import com.example.admin.client.CasControlClient;
import com.example.admin.config.AdminProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 外置 CAS 服务注册表（JSON 文件）读写。
 * 新增/修改直接写文件由 CAS watcher 热加载；删除必须走 CAS 控制面以卸载运行时服务。
 */
@Service
public class ServiceDefinitionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceDefinitionService.class);

    private static final Pattern FILE_NAME = Pattern.compile("^service-\\d+\\.json$");
    private static final Pattern CHANNEL_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");
    private static final Pattern SERVICE_ID =
            Pattern.compile("^\\^https?://[A-Za-z0-9.-]+(:\\d+)?(/[^\\s]*)?\\$$");
    private static final String ACCESS_STRATEGY_CLASS =
            "com.example.sso.cas.ChannelAccessStrategy";
    private static final String SERVICE_CLASS =
            "org.apereo.cas.services.CasRegisteredService";

    private final Path serviceDir;
    private final JdbcTemplate jdbcTemplate;
    private final CasControlClient casControlClient;
    private final ObjectMapper objectMapper;

    public ServiceDefinitionService(final AdminProperties properties,
                                    final JdbcTemplate jdbcTemplate,
                                    final CasControlClient casControlClient,
                                    final ObjectMapper objectMapper) {
        this.serviceDir = Path.of(properties.getServiceDir()).toAbsolutePath().normalize();
        this.jdbcTemplate = jdbcTemplate;
        this.casControlClient = casControlClient;
        this.objectMapper = objectMapper;
    }

    public List<ServiceSummary> list() {
        if (!Files.isDirectory(serviceDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(serviceDir)) {
            return files
                    .filter(path -> FILE_NAME.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::readSummary)
                    .filter(summary -> summary != null)
                    .toList();
        } catch (final IOException e) {
            throw new IllegalStateException("读取服务注册目录失败：" + e.getMessage(), e);
        }
    }

    public synchronized ServiceSummary create(final ServiceRequest request) {
        final long id = request.id() == null ? 0 : request.id();
        if (id <= 0) {
            throw new IllegalArgumentException("id 必须为正整数");
        }
        final String name = trim(request.name());
        if (name.isEmpty() || name.length() > 128) {
            throw new IllegalArgumentException("name 不能为空且长度不能超过 128");
        }
        final String serviceId = trim(request.serviceId());
        if (!SERVICE_ID.matcher(serviceId).matches()) {
            throw new IllegalArgumentException(
                    "serviceId 必须形如 ^http://host:port/路径$，且禁止全匹配");
        }
        final String channelCode = trim(request.channelCode());
        if (!CHANNEL_CODE.matcher(channelCode).matches()) {
            throw new IllegalArgumentException("channelCode 格式不合法");
        }
        final Long channelCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_channel WHERE channel_code = ?", Long.class, channelCode);
        if (channelCount == null || channelCount == 0) {
            throw new IllegalArgumentException("条线不存在：" + channelCode);
        }
        final String logoutUrl = trim(request.logoutUrl());
        if (logoutUrl.isEmpty()) {
            throw new IllegalArgumentException("logoutUrl 必填：业务系统必须提供 SLO 注销端点");
        }
        if ((!logoutUrl.startsWith("http://") && !logoutUrl.startsWith("https://"))
                || logoutUrl.length() > 512) {
            throw new IllegalArgumentException("logoutUrl 必须为 http(s) 地址且长度不超过 512");
        }
        final String description = trim(request.description());
        if (description.length() > 256) {
            throw new IllegalArgumentException("description 长度不能超过 256");
        }
        final int evaluationOrder = request.evaluationOrder() == null ? 10 : request.evaluationOrder();
        if (evaluationOrder < 0) {
            throw new IllegalArgumentException("evaluationOrder 不能为负数");
        }

        if (!Files.isDirectory(serviceDir)) {
            throw new IllegalStateException("服务注册目录不存在：" + serviceDir);
        }
        final Path target = serviceDir.resolve("service-" + id + ".json");
        if (Files.exists(target) || findByContentId(id) != null) {
            throw new IllegalStateException("服务 id 已存在：" + id);
        }

        final ObjectNode root = objectMapper.createObjectNode();
        root.put("@class", SERVICE_CLASS);
        root.put("serviceId", serviceId);
        root.put("name", name);
        root.put("id", id);
        if (!description.isEmpty()) {
            root.put("description", description);
        }
        root.put("evaluationOrder", evaluationOrder);
        root.put("logoutType", "BACK_CHANNEL");
        root.put("logoutUrl", logoutUrl);

        final ObjectNode properties = root.putObject("properties");
        properties.put("@class", "java.util.HashMap");
        final ObjectNode channelProperty = properties.putObject("channelCode");
        channelProperty.put("@class", "org.apereo.cas.services.DefaultRegisteredServiceProperty");
        final ArrayNode values = channelProperty.putArray("values");
        values.add("java.util.HashSet");
        values.addArray().add(channelCode);

        root.putObject("accessStrategy").put("@class", ACCESS_STRATEGY_CLASS);

        try {
            final Path temp = Files.createTempFile(serviceDir, "service-", ".tmp");
            Files.writeString(temp, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                    StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            throw new IllegalStateException("写入服务定义失败：" + e.getMessage(), e);
        }
        LOGGER.info("Created service definition [{}] with id [{}] and channel [{}]",
                target.getFileName(), id, channelCode);
        return new ServiceSummary(id, name, serviceId, channelCode, description, logoutUrl,
                target.getFileName().toString());
    }

    /**
     * 删除必须由 CAS 进程完成（文件删除 + ServicesManager 运行时卸载）。
     */
    public synchronized void delete(final long id) {
        if (findByContentId(id) == null) {
            throw new IllegalArgumentException("服务不存在：" + id);
        }
        casControlClient.deleteService(id);
        LOGGER.info("Deleted service definition [{}] via CAS control plane", id);
    }

    private Path findByContentId(final long id) {
        if (!Files.isDirectory(serviceDir)) {
            return null;
        }
        try (Stream<Path> files = Files.list(serviceDir)) {
            return files
                    .filter(path -> FILE_NAME.matcher(path.getFileName().toString()).matches())
                    .filter(path -> {
                        final JsonNode node = readTree(path);
                        return node != null && node.path("id").asLong(-1) == id;
                    })
                    .findFirst()
                    .orElse(null);
        } catch (final IOException e) {
            throw new IllegalStateException("读取服务注册目录失败：" + e.getMessage(), e);
        }
    }

    private ServiceSummary readSummary(final Path path) {
        final JsonNode node = readTree(path);
        if (node == null) {
            return null;
        }
        final JsonNode property = node.path("properties").path("channelCode").path("values");
        String channelCode = "";
        if (property.isArray() && property.size() > 1 && property.get(1).isArray()
                && !property.get(1).isEmpty()) {
            channelCode = property.get(1).get(0).asText("");
        }
        return new ServiceSummary(
                node.path("id").asLong(),
                node.path("name").asText(""),
                node.path("serviceId").asText(""),
                channelCode,
                node.path("description").asText(""),
                node.path("logoutUrl").asText(""),
                path.getFileName().toString());
    }

    private JsonNode readTree(final Path path) {
        try {
            return objectMapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
        } catch (final IOException e) {
            LOGGER.warn("Skip unreadable service definition [{}]: {}", path.getFileName(), e.getMessage());
            return null;
        }
    }

    private static String trim(final String value) {
        return value == null ? "" : value.trim();
    }

    public record ServiceSummary(long id, String name, String serviceId, String channelCode,
                                 String description, String logoutUrl, String fileName) {
    }

    public record ServiceRequest(Long id, String name, String serviceId, String channelCode,
                                 String description, String logoutUrl, Integer evaluationOrder) {
    }
}
