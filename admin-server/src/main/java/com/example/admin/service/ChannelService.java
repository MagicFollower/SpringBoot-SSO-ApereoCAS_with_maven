package com.example.admin.service;

import com.example.admin.entity.Channel;
import com.example.admin.mapper.ChannelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 条线管理（t_channel）。
 */
@Service
@RequiredArgsConstructor
public class ChannelService {

    private static final Pattern CHANNEL_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");

    private static final String UPSERT_SQL = """
            INSERT INTO t_channel (channel_code, channel_name, status, theme, mfa_required)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                channel_name = ?,
                status = ?,
                theme = ?,
                mfa_required = ?
            """;

    private final ChannelMapper channelMapper;
    private final JdbcTemplate jdbcTemplate;

    public List<Channel> list() {
        final Example example = new Example(Channel.class);
        example.orderBy("channelCode").asc();
        return channelMapper.selectByExample(example);
    }

    public Map<String, Object> upsert(final ChannelRequest request) {
        final String code = request.channelCode() == null ? "" : request.channelCode().trim();
        if (!CHANNEL_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "channelCode 必须为 2-32 位大写字母、数字或下划线，且以字母开头");
        }
        final String name = request.channelName() == null ? "" : request.channelName().trim();
        if (name.isEmpty() || name.length() > 128) {
            throw new IllegalArgumentException("channelName 不能为空且长度不能超过 128");
        }
        final int status = request.status() == null ? 1 : request.status();
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("status 只能为 0 或 1");
        }
        final int mfaRequired = request.mfaRequired() == null ? 0 : request.mfaRequired();
        if (mfaRequired != 0 && mfaRequired != 1) {
            throw new IllegalArgumentException("mfaRequired 只能为 0 或 1");
        }
        jdbcTemplate.update(UPSERT_SQL, code, name, status, request.theme(), mfaRequired,
                name, status, request.theme(), mfaRequired);
        return Map.of("channelCode", code, "updated", true);
    }

    public record ChannelRequest(String channelCode, String channelName, Integer status,
                                 String theme, Integer mfaRequired) {
    }
}
