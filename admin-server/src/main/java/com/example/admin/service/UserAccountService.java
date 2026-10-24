package com.example.admin.service;

import com.example.admin.client.CasControlClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 用户账号管理（t_user）。密码使用 BCrypt，接口不返回密码或哈希。
 * 删除账号时 t_user_channel 由外键级联删除。
 */
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserAccountService.class);

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{2,64}$");
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private static final String ACCOUNT_COLUMNS = """
            SELECT id, username, display_name AS displayName, dept_id AS deptId, enabled,
                   DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt,
                   DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updatedAt
            FROM t_user
            """;

    private static final String LIST_ACCOUNTS_SQL = ACCOUNT_COLUMNS + """
            WHERE (? = '' OR username LIKE ? OR display_name LIKE ?)
              AND (? IS NULL OR enabled = ?)
            ORDER BY username
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_ACCOUNTS_SQL = """
            SELECT COUNT(1) FROM t_user
            WHERE (? = '' OR username LIKE ? OR display_name LIKE ?)
              AND (? IS NULL OR enabled = ?)
            """;

    private static final String FIND_ACCOUNT_SQL = ACCOUNT_COLUMNS + "WHERE username = ?";

    private static final String INSERT_ACCOUNT_SQL = """
            INSERT INTO t_user (username, password_hash, display_name, dept_id, enabled)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_ACCOUNT_SQL = """
            UPDATE t_user SET display_name = ?, dept_id = ?, enabled = ? WHERE username = ?
            """;

    private static final String UPDATE_ACCOUNT_WITH_PASSWORD_SQL = """
            UPDATE t_user
            SET password_hash = ?, display_name = ?, dept_id = ?, enabled = ?
            WHERE username = ?
            """;

    private static final String DELETE_ACCOUNT_SQL = "DELETE FROM t_user WHERE username = ?";

    private final JdbcTemplate jdbcTemplate;
    private final CasControlClient casControlClient;

    public AccountPage list(final String query, final Integer enabled, final int page, final int size) {
        if (enabled != null && enabled != 0 && enabled != 1) {
            throw new IllegalArgumentException("enabled 只能为 0 或 1");
        }
        final int safePage = Math.max(1, page);
        final int safeSize = size < 1 || size > 100 ? 20 : size;
        final String keyword = query == null ? "" : query.trim();
        final String like = "%" + keyword + "%";
        final Long total = jdbcTemplate.queryForObject(COUNT_ACCOUNTS_SQL, Long.class,
                keyword, like, like, enabled, enabled);
        final List<AccountSummary> items = jdbcTemplate.query(LIST_ACCOUNTS_SQL,
                UserAccountService::mapAccount,
                keyword, like, like, enabled, enabled, safeSize, (safePage - 1) * safeSize);
        return new AccountPage(items, total == null ? 0 : total, safePage, safeSize);
    }

    public AccountSummary create(final AccountCreateRequest request) {
        final String username = requireUsername(request.username());
        final String password = request.password() == null ? "" : request.password();
        validatePassword(password);
        final String displayName = normalizeText(request.displayName(), 128, "displayName");
        final String deptId = normalizeText(request.deptId(), 64, "deptId");
        final int enabled = request.enabled() == null ? 1 : validateEnabled(request.enabled());
        final Long exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_user WHERE username = ?", Long.class, username);
        if (exists != null && exists > 0) {
            throw new IllegalStateException("用户名已存在：" + username);
        }
        jdbcTemplate.update(INSERT_ACCOUNT_SQL, username,
                PASSWORD_ENCODER.encode(password), displayName, deptId, enabled);
        LOGGER.info("Created account [{}], enabled=[{}]", username, enabled);
        return requireAccount(username);
    }

    public Map<String, Object> update(final String username, final AccountUpdateRequest request) {
        final AccountSummary current = requireAccount(username);
        final String displayName = request.displayName() == null
                ? current.displayName()
                : normalizeText(request.displayName(), 128, "displayName");
        final String deptId = request.deptId() == null
                ? current.deptId()
                : normalizeText(request.deptId(), 64, "deptId");
        final int enabled = request.enabled() == null
                ? current.enabled()
                : validateEnabled(request.enabled());

        if (request.password() != null && !request.password().isEmpty()) {
            validatePassword(request.password());
            jdbcTemplate.update(UPDATE_ACCOUNT_WITH_PASSWORD_SQL,
                    PASSWORD_ENCODER.encode(request.password()), displayName, deptId, enabled, username);
        } else {
            jdbcTemplate.update(UPDATE_ACCOUNT_SQL, displayName, deptId, enabled, username);
        }

        List<String> warnings = List.of();
        if (current.enabled() == 1 && enabled == 0) {
            warnings = kickoutBestEffort(username);
        }
        LOGGER.info("Updated account [{}], enabled=[{}], passwordReset=[{}]",
                username, enabled, request.password() != null && !request.password().isEmpty());
        return Map.of("user", requireAccount(username), "warnings", warnings);
    }

    public Map<String, Object> delete(final String username) {
        requireAccount(username);
        final List<String> warnings = kickoutBestEffort(username);
        jdbcTemplate.update(DELETE_ACCOUNT_SQL, username);
        LOGGER.info("Deleted account [{}] and cascaded channel grants", username);
        return Map.of("username", username, "deleted", true, "warnings", warnings);
    }

    private AccountSummary requireAccount(final String username) {
        final List<AccountSummary> found = jdbcTemplate.query(
                FIND_ACCOUNT_SQL, UserAccountService::mapAccount, username);
        if (found.isEmpty()) {
            throw new IllegalArgumentException("用户不存在：" + username);
        }
        return found.get(0);
    }

    private List<String> kickoutBestEffort(final String username) {
        try {
            casControlClient.kickout(username);
            return List.of();
        } catch (final RuntimeException e) {
            LOGGER.warn("Kickout after account change failed for [{}]: {}", username, e.getMessage());
            return List.of("CAS 会话注销失败：" + e.getMessage());
        }
    }

    private static AccountSummary mapAccount(final ResultSet resultSet, final int rowNum)
            throws SQLException {
        return new AccountSummary(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                resultSet.getString("displayName"),
                resultSet.getString("deptId"),
                resultSet.getInt("enabled"),
                resultSet.getString("createdAt"),
                resultSet.getString("updatedAt"));
    }

    private static String requireUsername(final String value) {
        final String username = value == null ? "" : value.trim();
        if (!USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "username 必须为 2-64 位字母、数字、点、下划线或连字符");
        }
        return username;
    }

    private static void validatePassword(final String password) {
        if (password.length() < 8 || password.length() > 64) {
            throw new IllegalArgumentException("password 长度必须为 8-64 位");
        }
    }

    private static int validateEnabled(final Integer enabled) {
        if (enabled == null) {
            return 1;
        }
        if (enabled != 0 && enabled != 1) {
            throw new IllegalArgumentException("enabled 只能为 0 或 1");
        }
        return enabled;
    }

    private static String normalizeText(final String value, final int maxLength, final String field) {
        if (value == null) {
            return null;
        }
        final String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + maxLength);
        }
        return text;
    }

    public record AccountCreateRequest(String username, String password, String displayName,
                                       String deptId, Integer enabled) {
    }

    public record AccountUpdateRequest(String password, String displayName, String deptId,
                                       Integer enabled) {
    }

    public record AccountSummary(long id, String username, String displayName, String deptId,
                                 int enabled, String createdAt, String updatedAt) {
    }

    public record AccountPage(List<AccountSummary> items, long total, int page, int size) {
    }
}
