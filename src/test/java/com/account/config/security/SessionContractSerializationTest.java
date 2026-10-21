package com.account.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;

/**
 * Verifies the shared session contract: the SecurityContext written by the Authentication service
 * survives the default Spring Session (JDK) Redis serialization and is readable here.
 */
class SessionContractSerializationTest {

    static SecurityContext contractContext(String username, String customerId, String role) {
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.authenticated(
                username, null, List.of(new SimpleGrantedAuthority(role)));
        Map<String, Object> details = new HashMap<>();
        details.put(SessionAuthentication.DETAIL_USERNAME, username);
        details.put(SessionAuthentication.DETAIL_CUSTOMER_ID, customerId);
        details.put(SessionAuthentication.DETAIL_ROLE, role);
        token.setDetails(details);
        return new SecurityContextImpl(token);
    }

    @Test
    void securityContextRoundTripsThroughJdkSerializer() {
        String customerId = UUID.randomUUID().toString();
        SecurityContext original = contractContext("alice", customerId, "ROLE_CUSTOMER");

        JdkSerializationRedisSerializer serializer = new JdkSerializationRedisSerializer();
        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored).isInstanceOf(SecurityContext.class);
        Authentication auth = ((SecurityContext) restored).getAuthentication();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_CUSTOMER");
        assertThat(auth.getDetails()).isInstanceOf(HashMap.class);
        assertThat(SessionAuthentication.detail(auth, SessionAuthentication.DETAIL_USERNAME)).isEqualTo("alice");
        assertThat(SessionAuthentication.detail(auth, SessionAuthentication.DETAIL_CUSTOMER_ID)).isEqualTo(customerId);
        assertThat(SessionAuthentication.detail(auth, SessionAuthentication.DETAIL_ROLE)).isEqualTo("ROLE_CUSTOMER");
    }

    @Test
    void nullCustomerIdIsPreservedForAdmins() {
        JdkSerializationRedisSerializer serializer = new JdkSerializationRedisSerializer();
        SecurityContext restored = (SecurityContext) serializer.deserialize(
                serializer.serialize(contractContext("root", null, "ROLE_ADMIN")));

        Authentication auth = restored.getAuthentication();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        assertThat(SessionAuthentication.detail(auth, SessionAuthentication.DETAIL_CUSTOMER_ID)).isNull();
        assertThat(SessionAuthentication.detail(auth, SessionAuthentication.DETAIL_ROLE)).isEqualTo("ROLE_ADMIN");
    }
}
