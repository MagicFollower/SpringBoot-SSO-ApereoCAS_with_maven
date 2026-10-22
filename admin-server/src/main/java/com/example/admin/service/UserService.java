package com.example.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 用户查询与条线授权（t_user / t_user_channel）。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final DateTimeFormatter SPACE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String LIST_USERS_SQL = """
            SELECT username,
                   display_name AS displayName,
                   dept_id AS deptId,
                   enabled,
                   DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt
            FROM t_user
            WHERE (? = '' OR username LIKE ? OR display_name LIKE ?)
            ORDER BY username
            LIMIT 200
            """;

    private static final String LIST_GRANTS_SQL = """
            SELECT uc.channel_code AS channelCode,
                   c.channel_name AS channelName,
                   c.status AS channelStatus,
                   uc.status,
                   uc.source,
                   uc.valid_from AS validFrom,
                   uc.valid_to AS validTo,
                   DATE_FORMAT(uc.updated_at, '%Y-%m-%d %H:%i:%s') AS updatedAt
            FROM t_user_channel uc
            JOIN t_channel c ON c.channel_code = uc.channel_code
            WHERE uc.user_id = ?
            ORDER BY uc.channel_code
            """;

    private static final String UPSERT_GRANT_SQL = """
            INSERT INTO t_user_channel (user_id, channel_code, source, valid_from, valid_to, status)
            VALUES (?, ?, 'MANUAL', ?, ?, 1)
            ON DUPLICATE KEY UPDATE
                status = 1,
                source = 'MANUAL',
                valid_from = ?,
                valid_to = ?
            """;

    private static final String REVOKE_GRANT_SQL = """
            UPDATE t_user_channel SET status = 0 WHERE user_id = ? AND channel_code = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> search(final String query) {
        final String keyword = query == null ? "" : query.trim();
        final String like = "%" + keyword + "%";
        return jdbcTemplate.queryForList(LIST_USERS_SQL, keyword, like, like);
    }

    public List<Map<String, Object>> grants(final String username) {
        requireUser(username);
        return jdbcTemplate.queryForList(LIST_GRANTS_SQL, username);
    }

    public Map<String, Object> grant(final String username, final GrantRequest request) {
        requireUser(username);
        final String channelCode = request.channelCode() == null ? "" : request.channelCode().trim();
        requireChannel(channelCode);
        final Timestamp validFrom = parseTime(request.validFrom(), "validFrom");
        final Timestamp validTo = parseTime(request.validTo(), "validTo");
        if (validFrom != null && validTo != null && validFrom.after(validTo)) {
            throw new IllegalArgumentException("validFrom 不能晚于 validTo");
        }
        jdbcTemplate.update(UPSERT_GRANT_SQL, username, channelCode, validFrom, validTo,
                validFrom, validTo);
        return Map.of("userId", username, "channelCode", channelCode, "granted", true);
    }

    public Map<String, Object> revoke(final String username, final String channelCode) {
        final int rows = jdbcTemplate.update(REVOKE_GRANT_SQL, username, channelCode);
        if (rows == 0) {
            throw new IllegalArgumentException("授权不存在：" + username + " / " + channelCode);
        }
        return Map.of("userId", username, "channelCode", channelCode, "revoked", true);
    }

    private void requireUser(final String username) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_user WHERE username = ?", Long.class, username);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("用户不存在：" + username);
        }
    }

    private void requireChannel(final String channelCode) {
        if (channelCode.isEmpty()) {
            throw new IllegalArgumentException("channelCode 不能为空");
        }
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_channel WHERE channel_code = ?", Long.class, channelCode);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("条线不存在：" + channelCode);
        }
    }

    private static Timestamp parseTime(final String value, final String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Timestamp.valueOf(LocalDateTime.parse(value.trim(), SPACE_FORMAT));
        } catch (final DateTimeParseException e) {
            try {
                return Timestamp.valueOf(LocalDateTime.parse(value.trim()));
            } catch (final DateTimeParseException ex) {
                throw new IllegalArgumentException(
                        field + " 格式应为 yyyy-MM-dd HH:mm:ss 或 ISO-8601");
            }
        }
    }

    public record GrantRequest(String channelCode, String validFrom, String validTo) {
    }
}
