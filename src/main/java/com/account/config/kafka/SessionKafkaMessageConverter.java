package com.account.config.kafka;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

import com.account.config.security.SessionAuthentication;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resolves the user behind a Kafka message by looking up the shared Redis session whose id is
 * carried in the {@value #SESSION_ID_HEADER} header (set by Transaction / Loan producers).
 */
@Component
public class SessionKafkaMessageConverter {

    public static final String SESSION_ID_HEADER = "X-Session-Id";

    private static final Logger log = LoggerFactory.getLogger(SessionKafkaMessageConverter.class);

    private final SessionRepository<? extends Session> sessionRepository;

    public SessionKafkaMessageConverter(SessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public SessionInfo extractSessionInfo(Headers headers) {
        Header header = headers != null ? headers.lastHeader(SESSION_ID_HEADER) : null;
        if (header == null || header.value() == null) {
            log.warn("No {} header found in Kafka message", SESSION_ID_HEADER);
            return SessionInfo.invalid(null, "No session id header found");
        }

        String sessionId = new String(header.value(), StandardCharsets.UTF_8).trim();
        if (sessionId.isEmpty()) {
            return SessionInfo.invalid(null, "Empty session id header");
        }

        try {
            Session session = sessionRepository.findById(sessionId);
            if (session == null) {
                log.warn("Session referenced by Kafka message not found or expired");
                return SessionInfo.invalid(sessionId, "Session not found or expired");
            }

            Object attribute = session.getAttribute(SessionAuthentication.SECURITY_CONTEXT_ATTRIBUTE);
            if (!(attribute instanceof SecurityContext securityContext)
                    || securityContext.getAuthentication() == null) {
                log.warn("Session referenced by Kafka message has no security context");
                return SessionInfo.invalid(sessionId, "Session has no security context");
            }

            Authentication authentication = securityContext.getAuthentication();
            String username = SessionAuthentication.detail(authentication, SessionAuthentication.DETAIL_USERNAME);
            if (username == null) {
                username = authentication.getName();
            }
            String role = SessionAuthentication.detail(authentication, SessionAuthentication.DETAIL_ROLE);
            if (role == null && !authentication.getAuthorities().isEmpty()) {
                role = authentication.getAuthorities().iterator().next().getAuthority();
            }
            String customerId = SessionAuthentication.detail(authentication, SessionAuthentication.DETAIL_CUSTOMER_ID);

            log.debug("Resolved Kafka message session for user: {}, role: {}, customerId: {}", username, role, customerId);
            return new SessionInfo(sessionId, username, role, customerId, true, null);
        } catch (RuntimeException e) {
            log.error("Error resolving session from Kafka message: {}", e.getMessage());
            return SessionInfo.invalid(sessionId, "Session lookup failed: " + e.getMessage());
        }
    }

    public Map<String, Object> createSecurityContext(SessionInfo sessionInfo) {
        Map<String, Object> securityContext = new HashMap<>();
        if (sessionInfo != null && sessionInfo.isValid()) {
            securityContext.put("username", sessionInfo.getUsername());
            securityContext.put("role", sessionInfo.getRole());
            securityContext.put("customerId", sessionInfo.getCustomerId());
            securityContext.put("sessionId", sessionInfo.getSessionId());
            securityContext.put("authenticated", true);
        } else {
            securityContext.put("authenticated", false);
            securityContext.put("error", sessionInfo != null ? sessionInfo.getError() : "No session info");
        }
        return securityContext;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionInfo {
        private String sessionId;
        private String username;
        private String role;
        private String customerId;
        private boolean valid;
        private String error;

        static SessionInfo invalid(String sessionId, String error) {
            return new SessionInfo(sessionId, null, null, null, false, error);
        }

        @Override
        public String toString() {
            // Never log the raw session id: it is a bearer credential.
            return "SessionInfo{username=" + username + ", role=" + role + ", customerId=" + customerId
                    + ", valid=" + valid + ", error=" + error + "}";
        }
    }
}
