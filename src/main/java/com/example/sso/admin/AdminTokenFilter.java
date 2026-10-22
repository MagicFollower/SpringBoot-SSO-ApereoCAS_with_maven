package com.example.sso.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * 控制面令牌校验。未配置 CAS_ADMIN_TOKEN 时控制面直接关闭；
 * 控制面（/admin/control/**）只允许本机回环地址访问。
 */
public class AdminTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Token";

    private static final Set<String> LOOPBACK_ADDRESSES =
            Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "::ffff:127.0.0.1");

    private final String adminToken;

    public AdminTokenFilter(final String adminToken) {
        this.adminToken = adminToken;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain)
            throws ServletException, IOException {
        if (!isLoopback(request)) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "control api is only accessible from loopback");
            return;
        }

        if (adminToken == null || adminToken.isBlank()) {
            writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "admin endpoint is disabled; set CAS_ADMIN_TOKEN");
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

    private static boolean isLoopback(final HttpServletRequest request) {
        return LOOPBACK_ADDRESSES.contains(request.getRemoteAddr());
    }

    private static void writeJson(final HttpServletResponse response,
                                  final int status,
                                  final String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
