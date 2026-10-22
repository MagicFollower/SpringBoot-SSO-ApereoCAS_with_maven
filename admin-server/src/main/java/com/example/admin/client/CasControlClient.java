package com.example.admin.client;

import com.example.admin.config.AdminProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * CAS 控制面客户端：仅通过 127.0.0.1 调用，携带控制令牌。
 */
@Component
public class CasControlClient {

    private final AdminProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CasControlClient(final AdminProperties properties, final ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        final int timeout = Math.max(1, properties.getControl().getTimeoutSeconds());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .build();
    }

    public JsonNode sessions(final String username) {
        return call("GET", "/users/" + encode(username) + "/sessions");
    }

    public JsonNode kickout(final String username) {
        return call("POST", "/users/" + encode(username) + "/kickout");
    }

    public JsonNode deleteService(final long id) {
        return call("DELETE", "/services/" + id);
    }

    private JsonNode call(final String method, final String path) {
        final String base = properties.getControl().getBaseUrl().replaceAll("/+$", "");
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(Math.max(1, properties.getControl().getTimeoutSeconds())))
                .header("X-Admin-Token", properties.getControl().getToken())
                .header("Accept", "application/json");
        switch (method) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.noBody());
            case "DELETE" -> builder.DELETE();
            default -> builder.GET();
        }
        try {
            final HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new ControlCallException(response.statusCode(),
                        errorMessage(response.body(), response.statusCode()));
            }
            return objectMapper.readTree(response.body());
        } catch (final IOException e) {
            throw new ControlCallException(502, "CAS 控制面不可达：" + e.getMessage());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ControlCallException(502, "CAS 控制面调用被中断");
        }
    }

    private String errorMessage(final String body, final int status) {
        try {
            final JsonNode node = objectMapper.readTree(body);
            if (node.hasNonNull("error")) {
                return node.get("error").asText();
            }
            if (node.hasNonNull("message")) {
                return node.get("message").asText();
            }
        } catch (final Exception ignored) {
            // 非 JSON 响应按状态码处理
        }
        return "CAS 控制面返回 " + status;
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
