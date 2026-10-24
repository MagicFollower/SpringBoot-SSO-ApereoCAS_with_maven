package org.apereo.cas.config;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CAS 双端口连接器：在主端口（server.port，HTTP）之外追加浏览器 HTTPS 端口。
 *
 * <ul>
 *   <li>主端口 8080（HTTP）：服务间调用——erp-server 票据校验、admin-server 控制面代理、
 *       demo 客户端与 bin 脚本探活，全部保持既有 URL 不变；</li>
 *   <li>追加端口 8083（HTTPS）：浏览器访问入口，消除"非安全连接"横幅并启用 TGC 安全加固。</li>
 * </ul>
 *
 * <p>所有端口/证书参数可通过环境变量覆盖；keystore 不存在时仅打 WARN 跳过，
 * 保证未生成证书的环境仍可按原 HTTP 模式启动。</p>
 */
@AutoConfiguration
public class CasHttpsConnectorConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(CasHttpsConnectorConfig.class);

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> casHttpsConnectorCustomizer(
            @Value("${sso.https.port:8083}") final int httpsPort,
            @Value("${sso.https.enabled:true}") final boolean httpsEnabled,
            @Value("${sso.https.keystore:etc/cas/keystore.p12}") final String keystorePath,
            @Value("${sso.https.keystore-password:changeit}") final String keystorePassword,
            @Value("${sso.debug.http-port:8081}") final int debugHttpPort,
            @Value("${sso.debug.http-enabled:false}") final boolean debugHttpEnabled) {

        return factory -> {
            LOGGER.info("[cas-https] customizer invoked: httpsEnabled={}, httpsPort={}, keystore={}, debugHttp={}",
                    httpsEnabled, httpsPort, keystorePath, debugHttpEnabled);
            if (httpsEnabled) {
                final Path keystore = Path.of(keystorePath);
                if (!Files.isReadable(keystore)) {
                    // 证书缺失不阻断启动：保留纯 HTTP 模式，方便首次拉起后再补证书
                    LOGGER.warn("[cas-https] keystore not readable: {} — HTTPS connector skipped", keystore.toAbsolutePath());
                    return;
                }
                LOGGER.info("[cas-https] adding HTTPS connector on port {} with keystore {}", httpsPort, keystore.toAbsolutePath());
                final Connector https = new Connector(Http11NioProtocol.class.getName());
                https.setScheme("https");
                https.setSecure(true);
                https.setPort(httpsPort);
                // Tomcat 8.5+ 靠此属性开启 TLS；缺失时 connector 以明文 HTTP 运行（曾踩坑）
                https.setProperty("SSLEnabled", "true");
                https.setProperty("sslImplementationName", "org.apache.tomcat.util.net.jsse.JSSEImplementation");

                final SSLHostConfig sslHostConfig = new SSLHostConfig();
                sslHostConfig.setHostName("_default_");
                sslHostConfig.setProtocols("TLSv1.2,TLSv1.3");

                final SSLHostConfigCertificate cert =
                        new SSLHostConfigCertificate(sslHostConfig, Type.UNDEFINED);
                cert.setCertificateKeystoreFile(keystore.toAbsolutePath().toString());
                cert.setCertificateKeystorePassword(keystorePassword);
                cert.setCertificateKeystoreType("PKCS12");
                sslHostConfig.addCertificate(cert);

                https.addSslHostConfig(sslHostConfig);
                factory.addAdditionalTomcatConnectors(https);
            }

            if (debugHttpEnabled) {
                // 可选的本机调试 HTTP 端口，仅回环绑定
                final Connector http = new Connector(Http11NioProtocol.class.getName());
                http.setScheme("http");
                http.setSecure(false);
                http.setPort(debugHttpPort);
                http.setProperty("address", "127.0.0.1");
                factory.addAdditionalTomcatConnectors(http);
            }
        };
    }

    /**
     * 浏览器 HTTP 访问守卫：只挂在主 HTTP connector 的分发路径上（/cas/*）。
     * HTTPS 请求直接放行；HTTP 的浏览器端点被 302 升级到 HTTPS 入口并附中文提示。
     * 服务间端点（/p3/**、/admin/control/**）虽也会被本过滤器扫过，但守卫逻辑只改写
     * 浏览器端点之外的一律放行——见 CasHttpBrowserGuardFilter 的端点判断。
     */
    @Bean
    public FilterRegistrationBean<CasHttpBrowserGuardFilter> casHttpBrowserGuardFilter(
            @Value("${sso.https.port:8083}") final int httpsPort) {
        final var guard = new CasHttpBrowserGuardFilter(httpsPort) {
            @Override
            protected boolean shouldNotFilter(final HttpServletRequest request) {
                final String path = request.getRequestURI().substring(request.getContextPath().length());
                // 只拦截浏览器端点；服务间端点与静态资源放行
                final boolean browserEndpoint = path.equals("/login") || path.equals("/logout")
                        || path.startsWith("/login/") || path.startsWith("/logout/");
                return !browserEndpoint || request.isSecure();
            }
        };
        final FilterRegistrationBean<CasHttpBrowserGuardFilter> registration =
                new FilterRegistrationBean<>(guard);
        registration.setName("casHttpBrowserGuardFilter");
        // servlet url-pattern 匹配的是 context 内路径（context-path=/cas 已被剥掉），必须用 /*
        registration.addUrlPatterns("/*");
        registration.setOrder(Integer.MIN_VALUE + 900);
        return registration;
    }
}
