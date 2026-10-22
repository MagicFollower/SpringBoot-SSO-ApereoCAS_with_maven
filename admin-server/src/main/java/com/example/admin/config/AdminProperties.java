package com.example.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * admin-server 配置：UI 令牌、服务注册目录与 CAS 控制面。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    private String token = "";
    private String serviceDir = "../etc/cas/services";
    private Control control = new Control();

    @Getter
    @Setter
    public static class Control {
        private String baseUrl = "http://127.0.0.1:8080/cas/admin/control";
        private String token = "";
        private int timeoutSeconds = 5;
    }
}
