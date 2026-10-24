package com.example.admin.controller;

import com.example.admin.common.ApiResult;
import com.example.admin.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户账号管理：增删改查 t_user。密码只写不读。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user-accounts")
public class UserAccountController {

    private final UserAccountService userAccountService;

    @GetMapping
    public ApiResult<UserAccountService.AccountPage> list(
            @RequestParam(name = "q", defaultValue = "") final String query,
            @RequestParam(name = "enabled", required = false) final Integer enabled,
            @RequestParam(name = "page", defaultValue = "1") final int page,
            @RequestParam(name = "size", defaultValue = "20") final int size) {
        return ApiResult.ok(userAccountService.list(query, enabled, page, size));
    }

    @PostMapping
    public ApiResult<UserAccountService.AccountSummary> create(
            @RequestBody final UserAccountService.AccountCreateRequest request) {
        return ApiResult.ok(userAccountService.create(request));
    }

    @PutMapping("/{username}")
    public ApiResult<Map<String, Object>> update(
            @PathVariable("username") final String username,
            @RequestBody final UserAccountService.AccountUpdateRequest request) {
        return ApiResult.ok(userAccountService.update(username, request));
    }

    @DeleteMapping("/{username}")
    public ApiResult<Map<String, Object>> delete(
            @PathVariable("username") final String username) {
        return ApiResult.ok(userAccountService.delete(username));
    }
}
