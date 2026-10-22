package com.example.admin.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 管理 API 令牌校验。未配置 ADMIN_UI_TOKEN 时接口关闭（503）。
 * 服务仅绑定 127.0.0.1，控制面由本服务再以控制令牌转发。
 */
public class AdminTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Token";

    private final String adminToken;

    public AdminTokenFilter(final String adminToken) {
        this.adminToken = adminToken;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain)
            throws ServletException, IOException {
        if (adminToken == null || adminToken.isBlank()) {
            writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "admin endpoint is disabled; set ADMIN_UI_TOKEN");
            return;
        }
        final String provided = request.getHeader(HEADER);
        if (provided == null || !MessageDigest.isEqual(
                adminToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid admin token");
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        filterChain.doFilter(request, response);
    }

    private static void writeJson(final HttpServletResponse response,
                                  final int status,
                                  final String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\""
                + message + "\",\"data\":null}");
    }
}
