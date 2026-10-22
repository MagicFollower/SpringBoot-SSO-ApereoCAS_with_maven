package com.example.erp.controller;

import com.example.erp.common.ApiResult;
import com.example.erp.common.SessionKeys;
import com.example.erp.dto.LoginUser;
import com.example.erp.dto.MenuNode;
import com.example.erp.service.ErpMenuService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MenuController {

    private final ErpMenuService erpMenuService;

    @GetMapping("/api/menus")
    public ApiResult<List<MenuNode>> menus(final HttpServletRequest request) {
        final LoginUser user = (LoginUser) request.getSession()
                .getAttribute(SessionKeys.LOGIN_USER);
        return ApiResult.ok(erpMenuService.menuTree(user.channelCode()));
    }
}