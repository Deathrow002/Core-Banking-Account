package com.account.config.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.MapSession;
import org.springframework.session.SessionRepository;

@ExtendWith(MockitoExtension.class)
class SessionKafkaMessageConverterTest {

    private static final String SESSION_ID = "0b6f1c0e-5d1e-4a8f-9b0a-3c2d1e0f9a8b";
    private static final String CUSTOMER_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Mock
    private SessionRepository<MapSession> sessionRepository;

    private SessionKafkaMessageConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SessionKafkaMessageConverter(sessionRepository);
    }

    private static RecordHeaders headersWithSession(String sessionId) {
        RecordHeaders headers = new RecordHeaders();
        headers.add(SessionKafkaMessageConverter.SESSION_ID_HEADER, sessionId.getBytes(StandardCharsets.UTF_8));
        return headers;
    }

    private static SecurityContextImpl contractContext(String username, String customerId, String role) {
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.authenticated(
                username, null, List.of(new SimpleGrantedAuthority(role)));
        Map<String, Object> details = new HashMap<>();
        details.put("username", username);
        details.put("customerId", customerId);
        details.put("role", role);
        token.setDetails(details);
        return new SecurityContextImpl(token);
    }

    @Test
    void validSessionResolvesUser() {
        MapSession session = new MapSession(SESSION_ID);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                contractContext("alice", CUSTOMER_ID, "ROLE_USER"));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(session);

        SessionKafkaMessageConverter.SessionInfo info = converter.extractSessionInfo(headersWithSession(SESSION_ID));

        assertThat(info.isValid()).isTrue();
        assertThat(info.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(info.getUsername()).isEqualTo("alice");
        assertThat(info.getRole()).isEqualTo("ROLE_USER");
        assertThat(info.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(info.getError()).isNull();
        assertThat(info.toString()).doesNotContain(SESSION_ID);

        Map<String, Object> context = converter.createSecurityContext(info);
        assertThat(context).containsEntry("authenticated", true)
                .containsEntry("username", "alice")
                .containsEntry("role", "ROLE_USER")
                .containsEntry("customerId", CUSTOMER_ID)
                .containsEntry("sessionId", SESSION_ID);
    }

    @Test
    void adminSessionWithoutCustomerIdIsValid() {
        MapSession session = new MapSession(SESSION_ID);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                contractContext("root", null, "ROLE_ADMIN"));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(session);

        SessionKafkaMessageConverter.SessionInfo info = converter.extractSessionInfo(headersWithSession(SESSION_ID));

        assertThat(info.isValid()).isTrue();
        assertThat(info.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(info.getCustomerId()).isNull();
    }

    @Test
    void missingHeaderIsInvalid() {
        SessionKafkaMessageConverter.SessionInfo info = converter.extractSessionInfo(new RecordHeaders());

        assertThat(info.isValid()).isFalse();
        assertThat(info.getError()).isEqualTo("No session id header found");
        verify(sessionRepository, never()).findById(org.mockito.ArgumentMatchers.anyString());
        assertThat(converter.createSecurityContext(info)).containsEntry("authenticated", false);
    }

    @Test
    void missingOrExpiredSessionIsInvalid() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(null);

        SessionKafkaMessageConverter.SessionInfo info = converter.extractSessionInfo(headersWithSession(SESSION_ID));

        assertThat(info.isValid()).isFalse();
        assertThat(info.getError()).isEqualTo("Session not found or expired");
        assertThat(info.getUsername()).isNull();
    }

    @Test
    void sessionWithoutSecurityContextIsInvalid() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(new MapSession(SESSION_ID));

        SessionKafkaMessageConverter.SessionInfo info = converter.extractSessionInfo(headersWithSession(SESSION_ID));

        assertThat(info.isValid()).isFalse();
        assertThat(info.getError()).isEqualTo("Session has no security context");
    }

    @Test
    void securityContextWithoutAuthenticationIsInvalid() {
        MapSession session = new MapSession(SESSION_ID);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl());
        when(sessionRepository.findById(SESSION_ID)).thenReturn(session);

        SessionKafkaMessageConverter.SessionInfo info = converter.extractSessionInfo(headersWithSession(SESSION_ID));

        assertThat(info.isValid()).isFalse();
        assertThat(info.getError()).isEqualTo("Session has no security context");
    }

    @Test
    void repositoryFailureIsInvalid() {
        when(sessionRepository.findById(SESSION_ID)).thenThrow(new IllegalStateException("redis down"));

        SessionKafkaMessageConverter.SessionInfo info = converter.extractSessionInfo(headersWithSession(SESSION_ID));

        assertThat(info.isValid()).isFalse();
        assertThat(info.getError()).contains("redis down");
    }
}
