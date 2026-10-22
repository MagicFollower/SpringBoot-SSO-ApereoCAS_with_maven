package com.example.erp.service;

import com.example.erp.common.PageResult;
import com.example.erp.entity.CasUser;
import com.example.erp.mapper.CasUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

import java.util.List;

/**
 * 用户查询服务（基于 tk-mapper Example）。
 */
@Service
@RequiredArgsConstructor
public class ErpUserService {

    private final CasUserMapper casUserMapper;

    public PageResult<CasUser> search(final String keyword, final int page, final int size) {
        final int safePage = Math.max(page, 1);
        final int safeSize = Math.min(Math.max(size, 1), 100);

        final Example example = new Example(CasUser.class);
        final String trimmed = keyword == null ? "" : keyword.trim();
        if (!trimmed.isEmpty()) {
            final String like = "%" + trimmed + "%";
            example.createCriteria().andLike("username", like);
            example.or().andLike("displayName", like);
        }
        example.orderBy("username").asc();

        final List<CasUser> all = casUserMapper.selectByExample(example);
        final int from = Math.min((safePage - 1) * safeSize, all.size());
        final int to = Math.min(from + safeSize, all.size());
        return new PageResult<>(all.size(), all.subList(from, to));
    }

    public CasUser findByUsername(final String username) {
        final Example example = new Example(CasUser.class);
        example.createCriteria().andEqualTo("username", username);
        final List<CasUser> users = casUserMapper.selectByExample(example);
        return users.isEmpty() ? null : users.get(0);
    }
}