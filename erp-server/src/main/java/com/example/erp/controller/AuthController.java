package com.example.erp.controller;

import com.example.erp.common.ApiResult;
import com.example.erp.common.PageResult;
import com.example.erp.common.SessionKeys;
import com.example.erp.config.ErpProperties;
import com.example.erp.dto.LoginUser;
import com.example.erp.entity.CasUser;
import com.example.erp.service.CasAuthService;
import com.example.erp.service.ErpSessionRegistry;
import com.example.erp.service.ErpUserService;
import com.example.erp.util.SloXmlParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CAS 登录、会话查询与登出。
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final ErpProperties properties;
    private final CasAuthService casAuthService;
    private final ErpUserService erpUserService;
    private final ErpSessionRegistry sessionRegistry;

    @GetMapping({"", "/"})
    public ResponseEntity<Void> index() {
        return redirect(properties.getFrontendBaseUrl() + "/");
    }

    @GetMapping("/login/cas")
    public ResponseEntity<Void> loginViaCas() {
        final String url = properties.casLoginUrl()
                + "?service=" + encode(properties.callbackUrl())
                + "&channelCode=" + encode(properties.getChannelCode());
        return redirect(url);
    }

    @GetMapping("/login/callback")
    public ResponseEntity<Void> loginCallback(@RequestParam("ticket") final String ticket,
                                              final HttpServletRequest request) {
        final var validation = casAuthService.validate(ticket, properties.callbackUrl());
        if (!validation.success()) {
            LOGGER.warn("CAS login rejected: {}", validation.message());
            return redirect(properties.getFrontendBaseUrl()
                    + "/login?cas=fail&message=" + encode(validation.message()));
        }

        final CasUser dbUser = erpUserService.findByUsername(validation.username());
        final LoginUser loginUser = new LoginUser(
                validation.username(),
                dbUser == null ? validation.username() : dbUser.getDisplayName(),
                dbUser == null ? null : dbUser.getDeptId(),
                properties.getChannelCode());

        final HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            sessionRegistry.unregister(oldSession);
            oldSession.invalidate();
        }
        final HttpSession session = request.getSession(true);
        session.setAttribute(SessionKeys.LOGIN_USER, loginUser);
        sessionRegistry.register(ticket, session);
        LOGGER.info("ERP login success: user [{}], ST [{}]", loginUser.username(), ticket);
        return redirect(properties.getFrontendBaseUrl() + "/login?cas=ok");
    }

    @GetMapping("/api/session")
    public ApiResult<Map<String, Object>> session(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        final Object user = session == null ? null : session.getAttribute(SessionKeys.LOGIN_USER);
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("authenticated", user != null);
        data.put("user", user);
        return ApiResult.ok(data);
    }

    @PostMapping("/api/logout")
    public ApiResult<Map<String, String>> logout(final HttpServletRequest request) {
        invalidateSession(request);
        return ApiResult.ok(Map.of("casLogoutUrl", properties.casLogoutUrl(logoutTarget())));
    }

    @GetMapping("/logout")
    public ResponseEntity<Void> frontChannelLogout(final HttpServletRequest request) {
        invalidateSession(request);
        return redirect(properties.casLogoutUrl(logoutTarget()));
    }

    /**
     * CAS 登出后的回跳地址必须落在已注册服务范围内，再由本端点转回前端。
     */
    private String logoutTarget() {
        return properties.getServiceBaseUrl() + "/";
    }

    @PostMapping("/logout/slo")
    public ResponseEntity<String> sloPost(final HttpServletRequest request) throws IOException {
        String payload = request.getParameter("logoutRequest");
        if (payload == null || payload.isBlank()) {
            payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        return handleSlo(payload);
    }

    @GetMapping("/logout/slo")
    public ResponseEntity<String> sloGet(final HttpServletRequest request) {
        return handleSlo(request.getParameter("logoutRequest"));
    }

    private ResponseEntity<String> handleSlo(final String payload) {
        final String sessionIndex = SloXmlParser.extractSessionIndex(payload);
        final boolean removed = sessionIndex != null
                && sessionRegistry.invalidateBySessionIndex(sessionIndex);
        LOGGER.info("ERP SLO request: sessionIndex=[{}], removed=[{}]", sessionIndex, removed);
        return ResponseEntity.ok("logout " + (removed ? "accepted" : "ignored"));
    }

    private void invalidateSession(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        if (session != null) {
            sessionRegistry.unregister(session);
            session.invalidate();
        }
    }

    private static ResponseEntity<Void> redirect(final String url) {
        return ResponseEntity.status(302).header("Location", url).build();
    }

    private static String encode(final String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}