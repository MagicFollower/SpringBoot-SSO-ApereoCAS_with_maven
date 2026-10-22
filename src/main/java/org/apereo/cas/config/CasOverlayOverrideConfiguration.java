package org.apereo.cas.config;

import com.example.sso.admin.AdminApiExceptionHandler;
import com.example.sso.admin.AdminServiceController;
import com.example.sso.admin.AdminSessionController;
import com.example.sso.admin.AdminTokenFilter;
import com.example.sso.admin.ServiceDefinitionService;
import com.example.sso.cas.ChannelAuthorizationService;
import com.example.sso.cas.ChannelCodeValidationFilter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.authentication.principal.WebApplicationService;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.web.CasWebSecurityConfigurer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;

/**
 * CAS 扩展入口：注册 channelCode 校验、MySQL 授权查询与条线准入所需的 Bean。
 */
@AutoConfiguration
public class CasOverlayOverrideConfiguration {

    @Bean(name = "ssoDataSource", destroyMethod = "close")
    public DataSource ssoDataSource(
            @Value("${sso.datasource.url}") final String url,
            @Value("${sso.datasource.username}") final String username,
            @Value("${sso.datasource.password:}") final String password,
            @Value("${sso.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}")
            final String driverClassName) {
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(3_000);
        // 允许 CAS 在数据库暂不可用时先启动；首次授权查询会失败并拒绝访问
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate ssoJdbcTemplate(@Qualifier("ssoDataSource") final DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public ChannelAuthorizationService channelAuthorizationService(
            @Qualifier("ssoJdbcTemplate") final JdbcTemplate jdbcTemplate) {
        return new ChannelAuthorizationService(jdbcTemplate);
    }

    @Bean
    public FilterRegistrationBean<ChannelCodeValidationFilter> channelCodeValidationFilter(
            final ServicesManager servicesManager,
            @Qualifier(WebApplicationService.BEAN_NAME_FACTORY)
            final ServiceFactory<WebApplicationService> webApplicationServiceFactory) {
        final var registration = new FilterRegistrationBean<>(
                new ChannelCodeValidationFilter(servicesManager, webApplicationServiceFactory));
        registration.setName("channelCodeValidationFilter");
        registration.addUrlPatterns("/login");
        registration.setOrder(Integer.MIN_VALUE + 1_000);
        return registration;
    }

    @Bean
    public ServiceDefinitionService serviceDefinitionService(
            final ServicesManager servicesManager,
            @Value("${sso.service-dir}") final String serviceDir) {
        return new ServiceDefinitionService(Path.of(serviceDir), servicesManager);
    }

    @Bean
    public AdminServiceController adminServiceController(
            final ServiceDefinitionService serviceDefinitionService) {
        return new AdminServiceController(serviceDefinitionService);
    }

    @Bean
    public AdminSessionController adminSessionController(
            final org.apereo.cas.ticket.registry.TicketRegistry ticketRegistry,
            final org.apereo.cas.logout.LogoutManager logoutManager,
            @Qualifier("ssoJdbcTemplate") final JdbcTemplate jdbcTemplate) {
        return new AdminSessionController(ticketRegistry, logoutManager, jdbcTemplate);
    }

    @Bean
    public AdminApiExceptionHandler adminApiExceptionHandler() {
        return new AdminApiExceptionHandler();
    }

    @Bean
    public FilterRegistrationBean<AdminTokenFilter> adminTokenFilter(
            @Value("${sso.admin.token:}") final String adminToken) {
        final var registration = new FilterRegistrationBean<>(new AdminTokenFilter(adminToken));
        registration.setName("adminTokenFilter");
        registration.addUrlPatterns("/admin/control/*");
        registration.setOrder(Integer.MIN_VALUE + 1_001);
        return registration;
    }

    /**
     * 控制面不参与 CAS 自带安全链：由 AdminTokenFilter 单独鉴权。
     * CAS 会为每个前缀自动追加 /**，此处只能返回不带通配符的路径前缀。
     */
    @Bean
    public CasWebSecurityConfigurer<HttpSecurity> adminWebSecurityConfigurer() {
        return new CasWebSecurityConfigurer<>() {
            @Override
            public List<String> getIgnoredEndpoints() {
                return List.of("/admin");
            }
        };
    }
}
