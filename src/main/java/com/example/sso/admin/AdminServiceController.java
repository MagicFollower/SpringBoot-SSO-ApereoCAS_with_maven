package com.example.sso.admin;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 接入系统（CAS 服务定义）控制面接口，仅回环可访问。
 */
@RestController
public class AdminServiceController {

    private final ServiceDefinitionService serviceDefinitionService;

    public AdminServiceController(final ServiceDefinitionService serviceDefinitionService) {
        this.serviceDefinitionService = serviceDefinitionService;
    }

    @DeleteMapping("/admin/control/services/{id}")
    public Map<String, Object> delete(@PathVariable("id") final long id) {
        serviceDefinitionService.delete(id);
        return Map.of("id", id, "deleted", true);
    }
}
