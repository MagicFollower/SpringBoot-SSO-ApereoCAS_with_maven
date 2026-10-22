package com.example.sso.cas;

import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.RegisteredServiceAccessStrategy;
import org.apereo.cas.services.RegisteredServiceAccessStrategyRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 按注册服务上的 channelCode 校验用户-条线授权。
 * <p>
 * 该实例由 CAS 反序列化服务定义时创建，因此不依赖构造器注入；
 * 运行时通过请求携带的 ApplicationContext 获取授权服务。
 */
public class ChannelAccessStrategy implements RegisteredServiceAccessStrategy {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelAccessStrategy.class);

    @Override
    public boolean authorizeRequest(final RegisteredServiceAccessStrategyRequest request) {
        final String channelCode = resolveChannelCode(request.getRegisteredService());
        final String userId = request.getPrincipalId();
        final String serviceUrl = request.getService() == null ? null : request.getService().getId();

        if (channelCode == null) {
            LOGGER.warn("Service [{}] has no channelCode configured; denying user [{}]", serviceUrl, userId);
            return false;
        }
        if (request.getApplicationContext() == null) {
            LOGGER.error("ApplicationContext is unavailable; cannot authorize user [{}] for channel [{}]",
                    userId, channelCode);
            return false;
        }

        final ChannelAuthorizationService authorizationService =
                request.getApplicationContext().getBean(ChannelAuthorizationService.class);
        try {
            final boolean allowed = authorizationService.isAllowed(userId, channelCode);
            if (!allowed) {
                authorizationService.auditDenied(userId, channelCode, serviceUrl, "CHANNEL_NOT_AUTHORIZED");
                LOGGER.info("Denied user [{}] for channel [{}] on service [{}]", userId, channelCode, serviceUrl);
            }
            return allowed;
        } catch (final Exception e) {
            authorizationService.auditDenied(userId, channelCode, serviceUrl, "AUTHORIZATION_ERROR");
            LOGGER.error("Channel authorization failed for user [{}], channel [{}]: {}",
                    userId, channelCode, e.getMessage(), e);
            return false;
        }
    }

    static String resolveChannelCode(final RegisteredService registeredService) {
        if (registeredService == null || registeredService.getProperties() == null) {
            return null;
        }
        final var property = registeredService.getProperties().get("channelCode");
        if (property == null || property.getValues() == null || property.getValues().isEmpty()) {
            return null;
        }
        return property.getValues().iterator().next();
    }
}
