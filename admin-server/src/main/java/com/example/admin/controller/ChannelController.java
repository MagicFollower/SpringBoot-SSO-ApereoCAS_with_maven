package com.example.admin.controller;

import com.example.admin.common.ApiResult;
import com.example.admin.entity.Channel;
import com.example.admin.service.ChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/channels")
public class ChannelController {

    private final ChannelService channelService;

    @GetMapping
    public ApiResult<List<Channel>> list() {
        return ApiResult.ok(channelService.list());
    }

    @PostMapping
    public ApiResult<Map<String, Object>> upsert(
            @RequestBody final ChannelService.ChannelRequest request) {
        return ApiResult.ok(channelService.upsert(request));
    }
}
