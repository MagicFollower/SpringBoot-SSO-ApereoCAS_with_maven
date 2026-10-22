package com.example.sso.admin;

import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.logout.LogoutManager;
import org.apereo.cas.logout.slo.SingleLogoutExecutionRequest;
import org.apereo.cas.ticket.TicketGrantingTicket;
import org.apereo.cas.ticket.registry.TicketRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线会话查询与强制踢下线，仅控制面（/admin/control/**，回环可访问）。
 */
@RestController
public class AdminSessionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminSessionController.class);

    private final TicketRegistry ticketRegistry;
    private final LogoutManager logoutManager;
    private final JdbcTemplate jdbcTemplate;

    public AdminSessionController(final TicketRegistry ticketRegistry,
                                  final LogoutManager logoutManager,
                                  final JdbcTemplate jdbcTemplate) {
        this.ticketRegistry = ticketRegistry;
        this.logoutManager = logoutManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/admin/control/users/{username}/sessions")
    public List<Map<String, Object>> sessions(@PathVariable("username") final String username) {
        return userSessions(username).stream()
                .map(ticket -> {
                    final Map<String, Object> session = new LinkedHashMap<>();
                    session.put("ticketId", ticket.getId());
                    session.put("createdAt", ticket.getCreationTime() == null
                            ? null : ticket.getCreationTime().toString());
                    session.put("services", ticket.getServices().values().stream()
                            .map(Service::getId)
                            .distinct()
                            .toList());
                    return session;
                })
                .toList();
    }

    @PostMapping("/admin/control/users/{username}/kickout")
    public Map<String, Object> kickout(@PathVariable("username") final String username) {
        final List<TicketGrantingTicket> tickets = userSessions(username);
        final List<String> errors = new ArrayList<>();
        int logoutRequests = 0;

        for (final TicketGrantingTicket ticket : tickets) {
            try {
                final var requests = logoutManager.performLogout(
                        SingleLogoutExecutionRequest.builder()
                                .ticketGrantingTicket(ticket)
                                .build());
                logoutRequests += requests.size();
                LOGGER.info("Admin kick-out [{}] on [{}] produced [{}] logout request(s)",
                        username, ticket.getId(), requests.size());
            } catch (final Exception e) {
                errors.add(ticket.getId() + ": " + e.getMessage());
                LOGGER.warn("Admin kick-out failed to notify services for [{}] on [{}]: {}",
                        username, ticket.getId(), e.getMessage());
            }
            try {
                ticketRegistry.deleteTicket(ticket.getId());
            } catch (final Exception e) {
                errors.add(ticket.getId() + " delete: " + e.getMessage());
                LOGGER.warn("Failed to delete ticket [{}] for [{}]: {}",
                        ticket.getId(), username, e.getMessage());
            }
        }

        auditKickout(username, tickets.size(), logoutRequests, errors.size());
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", username);
        result.put("sessions", tickets.size());
        result.put("logoutRequests", logoutRequests);
        result.put("errors", errors);
        result.put("kickedOut", !tickets.isEmpty() && errors.isEmpty());
        return result;
    }

    private List<TicketGrantingTicket> userSessions(final String username) {
        return ticketRegistry.getSessionsFor(username)
                .filter(TicketGrantingTicket.class::isInstance)
                .map(TicketGrantingTicket.class::cast)
                .toList();
    }

    private void auditKickout(final String username, final int sessions,
                              final int logoutRequests, final int errorCount) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_login_audit (user_id, result, reason)
                    VALUES (?, 'KICKOUT', ?)
                    """, username, "ADMIN_KICKOUT sessions=" + sessions
                    + " slo=" + logoutRequests + " errors=" + errorCount);
        } catch (final Exception e) {
            LOGGER.warn("Failed to audit admin kick-out for [{}]: {}", username, e.getMessage());
        }
    }
}
