package com.example.erp.service;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地会话索引：ST -> HttpSession，供 CAS 后端 SLO 按 SessionIndex 注销会话。
 */
@Component
public class ErpSessionRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErpSessionRegistry.class);

    private final Map<String, HttpSession> sessionsByTicket = new ConcurrentHashMap<>();

    public void register(final String serviceTicket, final HttpSession session) {
        sessionsByTicket.put(serviceTicket, session);
    }

    public void unregister(final HttpSession session) {
        sessionsByTicket.entrySet().removeIf(entry -> entry.getValue() == session);
    }

    public boolean invalidateBySessionIndex(final String sessionIndex) {
        final HttpSession session = sessionsByTicket.remove(sessionIndex);
        if (session == null) {
            return false;
        }
        try {
            session.invalidate();
            LOGGER.info("Invalidated ERP session by SLO SessionIndex [{}]", sessionIndex);
        } catch (final IllegalStateException e) {
            LOGGER.debug("Session already invalid for SessionIndex [{}]", sessionIndex);
        }
        return true;
    }

    public int activeSessionCount() {
        return sessionsByTicket.size();
    }
}