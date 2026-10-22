package com.example.erp.config;

import com.example.erp.service.ErpSessionRegistry;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ErpWebConfig {

    @Bean
    public HttpSessionListener erpSessionLifecycleListener(final ErpSessionRegistry sessionRegistry) {
        return new HttpSessionListener() {
            @Override
            public void sessionDestroyed(final HttpSessionEvent event) {
                sessionRegistry.unregister(event.getSession());
            }
        };
    }
}