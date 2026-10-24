package com.example.admin.controller;

import com.example.admin.common.ApiResult;
import com.example.admin.service.UserService;
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

import java.util.List;
import java.util.Map;

/**
 * 条线维度的用户授权管理。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/channels/{channelCode}/users")
public class ChannelUserController {

    private final UserService userService;

    @GetMapping
    public ApiResult<List<Map<String, Object>>> list(
            @PathVariable("channelCode") final String channelCode,
            @RequestParam(name = "q", defaultValue = "") final String query,
            @RequestParam(name = "status", required = false) final Integer status) {
        return ApiResult.ok(userService.channelUsers(channelCode, query, status));
    }

    @PostMapping
    public ApiResult<Map<String, Object>> add(
            @PathVariable("channelCode") final String channelCode,
            @RequestBody final ChannelUserRequest request) {
        return ApiResult.ok(userService.grantToChannel(
                channelCode, request.username(), request.validFrom(), request.validTo()));
    }

    @PutMapping("/{username}")
    public ApiResult<Map<String, Object>> update(
            @PathVariable("channelCode") final String channelCode,
            @PathVariable("username") final String username,
            @RequestBody final UserService.GrantUpdateRequest request) {
        return ApiResult.ok(userService.updateGrant(channelCode, username, request));
    }

    @DeleteMapping("/{username}")
    public ApiResult<Map<String, Object>> remove(
            @PathVariable("channelCode") final String channelCode,
            @PathVariable("username") final String username) {
        return ApiResult.ok(userService.revokeFromChannel(channelCode, username));
    }

    public record ChannelUserRequest(String username, String validFrom, String validTo) {
    }
}
