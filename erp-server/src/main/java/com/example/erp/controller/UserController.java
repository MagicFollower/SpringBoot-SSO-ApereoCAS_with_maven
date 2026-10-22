package com.example.erp.controller;

import com.example.erp.common.ApiResult;
import com.example.erp.common.PageResult;
import com.example.erp.entity.CasUser;
import com.example.erp.service.ErpUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final ErpUserService erpUserService;

    @GetMapping("/api/users")
    public ApiResult<PageResult<CasUser>> users(
            @RequestParam(name = "q", defaultValue = "") final String keyword,
            @RequestParam(name = "page", defaultValue = "1") final int page,
            @RequestParam(name = "size", defaultValue = "10") final int size) {
        return ApiResult.ok(erpUserService.search(keyword, page, size));
    }
}