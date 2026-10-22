package com.example.admin.controller;

import com.example.admin.common.ApiResult;
import com.example.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResult<List<Map<String, Object>>> search(
            @RequestParam(name = "q", defaultValue = "") final String query) {
        return ApiResult.ok(userService.search(query));
    }

    @GetMapping("/{username}/grants")
    public ApiResult<List<Map<String, Object>>> grants(
            @PathVariable("username") final String username) {
        return ApiResult.ok(userService.grants(username));
    }

    @PostMapping("/{username}/grants")
    public ApiResult<Map<String, Object>> grant(
            @PathVariable("username") final String username,
            @RequestBody final UserService.GrantRequest request) {
        return ApiResult.ok(userService.grant(username, request));
    }

    @DeleteMapping("/{username}/grants/{channelCode}")
    public ApiResult<Map<String, Object>> revoke(
            @PathVariable("username") final String username,
            @PathVariable("channelCode") final String channelCode) {
        return ApiResult.ok(userService.revoke(username, channelCode));
    }
}
