package com.example.sso.cas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 查询用户是否拥有某个条线的有效授权，并记录拒绝审计。
 */
public class ChannelAuthorizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelAuthorizationService.class);

    private static final String AUTHORIZATION_SQL = """
            SELECT COUNT(1)
            FROM t_user_channel uc
            JOIN t_channel c ON c.channel_code = uc.channel_code
            WHERE uc.user_id = ?
              AND uc.channel_code = ?
              AND uc.status = 1
              AND c.status = 1
              AND (uc.valid_from IS NULL OR uc.valid_from <= NOW())
              AND (uc.valid_to IS NULL OR uc.valid_to >= NOW())
            """;

    private static final String DENY_AUDIT_SQL = """
            INSERT INTO t_login_audit (user_id, channel_code, service_url, result, reason)
            VALUES (?, ?, ?, 'DENY', ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public ChannelAuthorizationService(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAllowed(final String userId, final String channelCode) {
        final Long count = jdbcTemplate.queryForObject(
                AUTHORIZATION_SQL, Long.class, userId, channelCode);
        return count != null && count > 0;
    }

    public void auditDenied(final String userId, final String channelCode,
                            final String serviceUrl, final String reason) {
        try {
            jdbcTemplate.update(DENY_AUDIT_SQL, userId, channelCode,
                    truncate(serviceUrl, 512), truncate(reason, 256));
        } catch (final Exception e) {
            LOGGER.warn("Failed to write deny audit for user [{}], channel [{}]: {}",
                    userId, channelCode, e.getMessage());
        }
    }

    private static String truncate(final String value, final int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
