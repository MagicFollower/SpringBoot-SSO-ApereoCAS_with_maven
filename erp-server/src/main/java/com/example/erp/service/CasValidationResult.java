package com.example.erp.service;

import com.example.erp.dto.LoginUser;

public record CasValidationResult(boolean success, String username, String message) {

    public static CasValidationResult ok(final String username) {
        return new CasValidationResult(true, username, "ok");
    }

    public static CasValidationResult fail(final String message) {
        return new CasValidationResult(false, null, message);
    }

    public LoginUser toLoginUser(final String displayName, final String deptId, final String channelCode) {
        return new LoginUser(username, displayName, deptId, channelCode);
    }
}