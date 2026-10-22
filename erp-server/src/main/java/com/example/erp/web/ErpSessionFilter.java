package com.example.erp.web;

import com.example.erp.common.ApiResult;
import com.example.erp.common.SessionKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 保护 /api/**：除会话查询和登出外，必须已建立 ERP 会话。
 */
@Component
@RequiredArgsConstructor
public class ErpSessionFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain)
            throws ServletException, IOException {
        final String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/") && !"/api/session".equals(path) && !"/api/logout".equals(path)) {
            final var session = request.getSession(false);
            if (session == null || session.getAttribute(SessionKeys.LOGIN_USER) == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                objectMapper.writeValue(response.getWriter(),
                        ApiResult.fail(401, "未登录或会话已失效"));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}