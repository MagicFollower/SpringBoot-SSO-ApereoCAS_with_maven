package com.example.admin.controller;

import com.example.admin.client.CasControlClient;
import com.example.admin.common.ApiResult;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线会话与踢下线：转发至 CAS 控制面（进程内票据注册表）。
 */
@RestController
@RequiredArgsConstructor
public class SessionController {

    private final CasControlClient casControlClient;

    @GetMapping("/api/users/{username}/sessions")
    public ApiResult<JsonNode> sessions(@PathVariable("username") final String username) {
        return ApiResult.ok(casControlClient.sessions(username));
    }

    @PostMapping("/api/users/{username}/kickout")
    public ApiResult<JsonNode> kickout(@PathVariable("username") final String username) {
        return ApiResult.ok(casControlClient.kickout(username));
    }
}
