package com.example.sso.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apereo.cas.services.ServicesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 删除外置 CAS 服务定义；文件 watcher 不处理删除事件，必须同步卸载运行时注册表。
 */
public class ServiceDefinitionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceDefinitionService.class);

    private static final Pattern FILE_NAME = Pattern.compile("^service-\\d+\\.json$");

    private final Path serviceDir;
    private final ServicesManager servicesManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ServiceDefinitionService(final Path serviceDir, final ServicesManager servicesManager) {
        this.serviceDir = serviceDir.toAbsolutePath().normalize();
        this.servicesManager = servicesManager;
    }

    public synchronized void delete(final long id) {
        final Path file = findByContentId(id);
        if (file == null) {
            throw new IllegalArgumentException("服务不存在：" + id);
        }
        servicesManager.delete(id);
        try {
            Files.deleteIfExists(file);
        } catch (final IOException e) {
            throw new IllegalStateException("删除服务定义失败：" + e.getMessage(), e);
        }
        LOGGER.info("Deleted service definition [{}]", file.getFileName());
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

    private JsonNode readTree(final Path path) {
        try {
            return objectMapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
        } catch (final IOException e) {
            LOGGER.warn("Skip unreadable service definition [{}]: {}", path.getFileName(), e.getMessage());
            return null;
        }
    }
}
