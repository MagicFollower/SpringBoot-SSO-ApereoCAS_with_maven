package com.example.erp.dto;

/**
 * 登录后写入 Session 的用户信息。
 */
public record LoginUser(String username, String displayName, String deptId, String channelCode) {
}