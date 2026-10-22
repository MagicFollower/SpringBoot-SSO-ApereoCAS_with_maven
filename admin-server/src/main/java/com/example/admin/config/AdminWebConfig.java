package com.example.admin.config;

import com.example.admin.web.AdminTokenFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 鉴权过滤器注册。前端通过同源反代访问，不配置 CORS。
 */
@Configuration
@RequiredArgsConstructor
public class AdminWebConfig {

    private final AdminProperties properties;

    @Bean
    public FilterRegistrationBean<AdminTokenFilter> adminTokenFilter() {
        final var registration =
                new FilterRegistrationBean<>(new AdminTokenFilter(properties.getToken()));
        registration.setName("adminTokenFilter");
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Integer.MIN_VALUE + 1_001);
        return registration;
    }
}
