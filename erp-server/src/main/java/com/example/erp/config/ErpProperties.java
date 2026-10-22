package com.example.erp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ERP 与 CAS 集成配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "erp")
public class ErpProperties {

    private String channelCode = "ERP";
    private String serviceBaseUrl = "http://localhost:8081/erp";
    private String frontendBaseUrl = "http://localhost:5173";
    private Cas cas = new Cas();

    @Getter
    @Setter
    public static class Cas {
        private String baseUrl = "http://localhost:8080/cas";
        private String validatePath = "/p3/serviceValidate";
        private int httpTimeoutSeconds = 5;
    }

    public String callbackUrl() {
        return serviceBaseUrl + "/login/callback";
    }

    public String casLoginUrl() {
        return cas.getBaseUrl() + "/login";
    }

    public String casLogoutUrl(final String targetService) {
        return cas.getBaseUrl() + "/logout?service="
                + java.net.URLEncoder.encode(targetService, java.nio.charset.StandardCharsets.UTF_8);
    }
}