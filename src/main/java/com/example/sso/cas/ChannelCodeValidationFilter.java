package com.example.sso.cas;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.authentication.principal.WebApplicationService;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.ServicesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 在 CAS 登录入口校验请求中的 channelCode 是否与已注册服务绑定值一致。
 * channelCode 只作为接入方声明；真正的授权依据仍然是服务注册表中的绑定值。
 */
public class ChannelCodeValidationFilter extends OncePerRequestFilter {

    public static final String PARAM_CHANNEL_CODE = "channelCode";
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelCodeValidationFilter.class);

    private final ServicesManager servicesManager;
    private final ServiceFactory<WebApplicationService> serviceFactory;

    public ChannelCodeValidationFilter(final ServicesManager servicesManager,
                                       final ServiceFactory<WebApplicationService> serviceFactory) {
        this.servicesManager = servicesManager;
        this.serviceFactory = serviceFactory;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain)
            throws ServletException, IOException {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String serviceId = request.getParameter("service");
        if (serviceId == null || serviceId.isBlank()) {
            serviceId = request.getParameter("targetService");
        }
        if (serviceId == null || serviceId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        final RegisteredService registeredService;
        try {
            final WebApplicationService service = serviceFactory.createService(serviceId);
            if (service == null) {
                reject(response, HttpServletResponse.SC_BAD_REQUEST, "无效的 service 参数");
                return;
            }
            registeredService = servicesManager.findServiceBy(service);
        } catch (final Exception e) {
            LOGGER.warn("Unable to resolve service [{}]: {}", serviceId, e.getMessage());
            reject(response, HttpServletResponse.SC_BAD_REQUEST, "无效的 service 参数");
            return;
        }

        if (registeredService == null) {
            LOGGER.warn("Rejected unregistered service [{}]", serviceId);
            reject(response, HttpServletResponse.SC_FORBIDDEN, "服务未在 CAS 注册");
            return;
        }

        final String expectedChannel = ChannelAccessStrategy.resolveChannelCode(registeredService);
        if (expectedChannel == null) {
            LOGGER.warn("Service [{}] has no channelCode configured", serviceId);
            reject(response, HttpServletResponse.SC_FORBIDDEN, "服务未配置 channelCode");
            return;
        }

        final String requestedChannel = request.getParameter(PARAM_CHANNEL_CODE);
        if (requestedChannel == null || !expectedChannel.equals(requestedChannel)) {
            LOGGER.warn("channelCode mismatch for service [{}]: expected [{}], requested [{}]",
                    serviceId, expectedChannel, requestedChannel);
            reject(response, HttpServletResponse.SC_FORBIDDEN, "channelCode 与注册服务不匹配");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static void reject(final HttpServletResponse response,
                               final int status,
                               final String message) throws IOException {
        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("""
                <!doctype html>
                <html lang="zh-CN">
                <head><meta charset="UTF-8"><title>CAS 接入校验失败</title></head>
                <body><h3>CAS 接入校验失败</h3><p>%s</p></body>
                </html>
                """.formatted(message));
    }
}
