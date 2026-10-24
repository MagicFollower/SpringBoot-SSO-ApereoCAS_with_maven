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

    private static final String LIST_CHANNEL_USERS_SQL = """
            SELECT u.username,
                   u.display_name AS displayName,
                   u.dept_id AS deptId,
                   u.enabled AS userEnabled,
                   uc.status AS grantStatus,
                   uc.source,
                   DATE_FORMAT(uc.valid_from, '%Y-%m-%d %H:%i:%s') AS validFrom,
                   DATE_FORMAT(uc.valid_to, '%Y-%m-%d %H:%i:%s') AS validTo,
                   DATE_FORMAT(uc.updated_at, '%Y-%m-%d %H:%i:%s') AS updatedAt
            FROM t_user_channel uc
            JOIN t_user u ON u.username = uc.user_id
            WHERE uc.channel_code = ?
              AND (? = '' OR u.username LIKE ? OR u.display_name LIKE ?)
              AND (? < 0 OR uc.status = ?)
            ORDER BY u.username
            LIMIT 200
            """;

    private static final String UPDATE_GRANT_SQL = """
            UPDATE t_user_channel
            SET valid_from = ?, valid_to = ?, status = ?
            WHERE user_id = ? AND channel_code = ?
            """;

    private static final String GRANT_STATUS_SQL = """
            SELECT status FROM t_user_channel WHERE user_id = ? AND channel_code = ?
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

    public List<Map<String, Object>> channelUsers(final String channelCode,
                                                  final String query,
                                                  final Integer status) {
        requireChannel(channelCode);
        final int statusFilter = status == null ? -1 : status;
        if (statusFilter != -1 && statusFilter != 0 && statusFilter != 1) {
            throw new IllegalArgumentException("status 只能为 0 或 1");
        }
        final String keyword = query == null ? "" : query.trim();
        final String like = "%" + keyword + "%";
        return jdbcTemplate.queryForList(LIST_CHANNEL_USERS_SQL,
                channelCode, keyword, like, like, statusFilter, statusFilter);
    }

    public Map<String, Object> grantToChannel(final String channelCode,
                                              final String username,
                                              final String validFrom,
                                              final String validTo) {
        final String name = requireUsername(username);
        final List<Integer> statuses = jdbcTemplate.queryForList(
                GRANT_STATUS_SQL, Integer.class, name, channelCode);
        if (!statuses.isEmpty() && statuses.get(0) == 1) {
            throw new IllegalArgumentException("用户已在条线中，请直接编辑授权：" + name);
        }
        return grant(name, new GrantRequest(channelCode, validFrom, validTo));
    }

    public Map<String, Object> updateGrant(final String channelCode,
                                           final String username,
                                           final GrantUpdateRequest request) {
        requireChannel(channelCode);
        final String name = requireUsername(username);
        requireUser(name);
        final Timestamp validFrom = parseTime(request.validFrom(), "validFrom");
        final Timestamp validTo = parseTime(request.validTo(), "validTo");
        if (validFrom != null && validTo != null && validFrom.after(validTo)) {
            throw new IllegalArgumentException("validFrom 不能晚于 validTo");
        }
        final int status = request.status() == null ? 1 : request.status();
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("status 只能为 0 或 1");
        }
        final int rows = jdbcTemplate.update(UPDATE_GRANT_SQL,
                validFrom, validTo, status, name, channelCode);
        if (rows == 0) {
            throw new IllegalArgumentException("用户未加入该条线：" + name + " / " + channelCode);
        }
        return Map.of("username", name, "channelCode", channelCode, "updated", true);
    }

    public Map<String, Object> revokeFromChannel(final String channelCode, final String username) {
        requireChannel(channelCode);
        return revoke(username, channelCode);
    }

    private static String requireUsername(final String username) {
        final String name = username == null ? "" : username.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        return name;
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

    public record GrantUpdateRequest(String validFrom, String validTo, Integer status) {
    }
}
