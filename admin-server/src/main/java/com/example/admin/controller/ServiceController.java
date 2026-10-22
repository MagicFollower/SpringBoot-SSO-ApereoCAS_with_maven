package com.example.admin.controller;

import com.example.admin.common.ApiResult;
import com.example.admin.service.ServiceDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceDefinitionService serviceDefinitionService;

    @GetMapping
    public ApiResult<List<ServiceDefinitionService.ServiceSummary>> list() {
        return ApiResult.ok(serviceDefinitionService.list());
    }

    @PostMapping
    public ApiResult<ServiceDefinitionService.ServiceSummary> create(
            @RequestBody final ServiceDefinitionService.ServiceRequest request) {
        return ApiResult.ok(serviceDefinitionService.create(request));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Map<String, Object>> delete(@PathVariable("id") final long id) {
        serviceDefinitionService.delete(id);
        return ApiResult.ok(Map.of("id", id, "deleted", true));
    }
}
