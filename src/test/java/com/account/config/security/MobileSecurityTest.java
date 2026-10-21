package com.account.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import com.account.config.WebSecurityConfig;
import com.account.controller.AccountController;
import com.account.service.AccountService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitConfig(MobileSecurityTest.Config.class)
@WebAppConfiguration
class MobileSecurityTest {
    // Fixed, test-only RSA key; never used by the application.
    private static final String N = "tP96rCoiy+F+t2XkOhTyWyTX4NWnHPeKaSs0UQNuMoFLPMQni8uFL0exxG2x4qIWKckTQrQOjkzes9oF6rDBby4v0eMKn7y22al/K83NJ17OtbCcnyr1BG0uVMNeHcLLJrfOe1J+IqNLEZi/WIMCv+G/jAfB8aWPnqmwrZuNe+Kyl4VWYv8m3JZ+ljWbob2syu25Lk/72J12i1swGarSeysPiAeyVwfQTvFF8PecNdXZR7rmXvwLHbwbr7olaVD1r851XUlPQcRx8dChDjDaUH5CapXrulkNj35mC3BEWMy/S2t7U1eZ01qrT7rN1bh1TxW//XAwC7dQpIloBDSITQ==";
    private static final String D = "UtEvLUWeZFGKbJo3oofhGOkzyhGYhH2TnGmDZ6o9KujUJzrNqX53M1oHsgxLP5rQr0/s+zFuE5iCsiKJYIh+CcRFZzEOtGZh+BhBMybYvzH3PVVRZNcAA4V9lJVMJCv0ToP9+5mUU38KI0QUEYjooinGPmlySYJqw7t7NwSc6VjdGEEbku4NKOzFmqAHaL5X1xHOqXsoDbebq6kFPvJvsZMQjzxejCHnPJYtiKKLR7xS+pjd/eEWkTFpFH+mzVFE4igimOYAkcxOfOwYjgu41lsIvcgMIxqaHPCDf6D+iCEFra3RGPnQ9rWCQYuH5UwkSieGL/7BLyAwq0IO1ZxIdQ==";
    private static final String ORIGINAL = "/accounts/getAllAccounts";
    private static final String MOBILE = "/api/mobile" + ORIGINAL;
    private static final String WRITE = "/accounts/createAccount";

    @Autowired WebApplicationContext context;
    @Autowired AccountService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test
    void signedJwtUsesUsernameAndIdenticalRoleAndDetailsWithoutCreatingSession() throws Exception {
        when(service.getAllAccounts()).thenAnswer(invocation -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isInstanceOf(JwtAuthenticationToken.class);
            assertThat(authentication.getName()).isEqualTo("admin@example.test");
            assertThat(authentication.getDetails()).isInstanceOf(HashMap.class);
            assertThat(authentication.getAuthorities()).extracting("authority").contains("ROLE_ADMIN");
            assertThat(SessionAuthentication.detail(authentication, "username")).isEqualTo("admin@example.test");
            assertThat(SessionAuthentication.detail(authentication, "customerId")).isEqualTo("customer-123");
            assertThat(SessionAuthentication.detail(authentication, "role")).isEqualTo("ROLE_ADMIN");
            return List.of();
        });
        var result = mvc.perform(get(MOBILE).header("Authorization", "Bearer " + token("valid")))
            .andExpect(status().isOk()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    void mobileWriteUsesSameControllerWithoutCsrf() throws Exception {
        mvc.perform(post("/api/mobile" + WRITE).contentType("application/json").content("{}")
                .header("Authorization", "Bearer " + token("valid")))
            .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid", "write-only", "money-write"})
    void mobileBalanceMutationIsScopeCheckedThenDisabledBeforeBusinessLogic(String scopes) throws Exception {
        mvc.perform(put("/api/mobile/accounts/updateAccountBalance")
                .header("Authorization", "Bearer " + token(scopes))
                .contentType("application/json").content("{\"balance\":10}"))
            .andExpect(scopes.equals("money-write") ? status().isServiceUnavailable() : status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid", "write-only", "money-write"})
    void mobileFundedOpeningCannotBypassMoneyMutationGate(String scopes) throws Exception {
        mvc.perform(post("/api/mobile/accounts/createAccount")
                .header("Authorization", "Bearer " + token(scopes))
                .contentType("application/json").content("{\"balance\":10}"))
            .andExpect(scopes.equals("money-write") ? status().isServiceUnavailable() : status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid", "write-only", "money-write"})
    void mobileDeletionCannotDestroyBalancesOutsidePhaseOneGate(String scopes) throws Exception {
        mvc.perform(delete("/api/mobile/accounts/deleteAccount")
                .param("accNo", "01f60908-5fcf-48f0-b7d4-e0fba563d5ec")
                .header("Authorization", "Bearer " + token(scopes)))
            .andExpect(scopes.equals("money-write") ? status().isServiceUnavailable() : status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void mobileZeroBalanceOpeningRemainsAvailableWithoutMoneyWriteScope() throws Exception {
        mvc.perform(post("/api/mobile/accounts/createAccount")
                .header("Authorization", "Bearer " + token("valid"))
                .contentType("application/json").content("{\"balance\":0}"))
            .andExpect(status().isCreated());
        verify(service).createAccount(any());
    }

    @Test
    void browserBalanceWriteRetainsSessionAndCsrfBehavior() throws Exception {
        mvc.perform(put("/accounts/updateAccountBalance").session(browserSession())
                .contentType("application/json").content("{\"balance\":10}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        mvc.perform(put("/accounts/updateAccountBalance").session(browserSession())
                .cookie(new Cookie("XSRF-TOKEN", "browser-csrf")).header("X-XSRF-TOKEN", "browser-csrf")
                .contentType("application/json").content("{\"balance\":10}"))
            .andExpect(status().isOk());
        verify(service).updateAccountBalance(any());
    }

    @Test
    void cookieOnlyMobileRequestCannotLoadBrowserSession() throws Exception {
        mvc.perform(get(MOBILE).session(browserSession()).cookie(new Cookie("SESSION", "existing")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void mobileJwtCannotBorrowBrowserAdminRole() throws Exception {
        var session = browserSession();
        mvc.perform(get(ORIGINAL).session(session)).andExpect(status().isOk());
        mvc.perform(get(MOBILE).session(session).header("Authorization", "Bearer " + token("user")))
            .andExpect(status().isForbidden());
        mvc.perform(get(ORIGINAL).session(session)).andExpect(status().isOk());
    }

    @Test
    void originalRoutesDoNotAcceptBearerAuthentication() throws Exception {
        mvc.perform(get(ORIGINAL).header("Authorization", "Bearer " + token("valid")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void browserUnauthorizedRequestsNeverSaveServletRequestsOrCreateSessions() throws Exception {
        var result = mvc.perform(get(ORIGINAL).accept("text/html"))
            .andExpect(status().isUnauthorized()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
            .noneMatch(cookie -> cookie.startsWith("SESSION="));

        var session = new MockHttpSession();
        var existing = mvc.perform(get(ORIGINAL).session(session).cookie(new Cookie("SESSION", "existing"))
                .accept("text/html"))
            .andExpect(status().isUnauthorized()).andReturn();
        assertThat(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST")).isNull();
        assertThat(java.util.Collections.list(session.getAttributeNames()))
            .noneMatch(name -> session.getAttribute(name) instanceof org.springframework.security.web.savedrequest.SavedRequest);
        assertThat(existing.getResponse().getHeaders("Set-Cookie"))
            .noneMatch(cookie -> cookie.startsWith("SESSION="));
    }

    @Test
    void browserWritesStillRequireCsrfAndAcceptCookieHeaderPair() throws Exception {
        mvc.perform(post(WRITE).session(browserSession()).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post(WRITE).session(browserSession()).contentType("application/json").content("{}")
                .cookie(new Cookie("XSRF-TOKEN", "browser-csrf")).header("X-XSRF-TOKEN", "browser-csrf"))
            .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "issuer", "audience", "future", "no-expiry", "bad-role", "no-customer"})
    void invalidSignedTokensAreRejected(String problem) throws Exception {
        mvc.perform(get(MOBILE).header("Authorization", "Bearer " + token(problem)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void bankingScopeIsMandatory() throws Exception {
        mvc.perform(get(MOBILE).header("Authorization", "Bearer " + token("scope")))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"login", "csrf", "logout"})
    void noMobileAuthenticationUtilityEndpoints(String path) throws Exception {
        mvc.perform(post("/api/mobile/" + path).session(browserSession()))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/mobile/" + path).header("Authorization", "Bearer " + token("valid")))
            .andExpect(status().isNotFound());
    }

    @Test
    void everyOriginalControllerRouteHasExactMobileAlias() {
        var mapping = context.getBean(
            org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
        var routes = mapping.getHandlerMethods().entrySet().stream()
            .filter(entry -> entry.getValue().getBeanType().equals(AccountController.class)).toList();
        assertThat(routes).hasSize(7);
        routes.forEach(entry -> {
            var paths = entry.getKey().getPatternValues();
            var original = paths.stream().filter(path -> !path.startsWith("/api/mobile")).findFirst().orElseThrow();
            assertThat(paths).containsExactlyInAnyOrder(original, "/api/mobile" + original);
        });
    }

    private MockHttpSession browserSession() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated("admin@example.test", null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        var session = new MockHttpSession();
        session.setAttribute(SessionAuthentication.SECURITY_CONTEXT_ATTRIBUTE, securityContext);
        return session;
    }

    private String token(String variation) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
            .subject("admin@example.test")
            .issuer(variation.equals("issuer") ? "http://evil.invalid" : "http://localhost:8080")
            .audience(variation.equals("audience") ? "other-api" : "core-banking-api")
            .issueTime(Date.from(now.minusSeconds(600)))
            .notBeforeTime(Date.from(variation.equals("future") ? now.plusSeconds(600) : now.minusSeconds(600)))
            .claim("scope", variation.equals("scope") ? "openid"
                : variation.equals("write-only") ? "transactions.write"
                : variation.equals("money-write") ? "banking transactions.write" : "banking")
            .claim("role", variation.equals("user") ? "ROLE_CUSTOMER" : variation.equals("bad-role") ? "ADMIN" : "ROLE_ADMIN");
        if (!variation.equals("no-customer")) claims.claim("customerId", "customer-123");
        if (!variation.equals("no-expiry")) {
            claims.expirationTime(Date.from(variation.equals("expired") ? now.minusSeconds(120) : now.plusSeconds(300)));
        }
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        var privateKey = KeyFactory.getInstance("RSA").generatePrivate(new RSAPrivateKeySpec(number(N), number(D)));
        jwt.sign(new RSASSASigner(privateKey));
        return jwt.serialize();
    }

    private static BigInteger number(String value) {
        return new BigInteger(1, Base64.getDecoder().decode(value));
    }

    @Configuration
    @EnableWebMvc
    @Import({WebSecurityConfig.class, MobileJwtSecurity.class, SessionConfig.class,
        RestAuthenticationEntryPoint.class, AccountController.class})
    static class Config {
        @Bean AccountService accountService() { return mock(AccountService.class); }

        @Bean
        @Primary
        JwtDecoder testDecoder() throws Exception {
            var publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(number(N), BigInteger.valueOf(65537)));
            var decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
            decoder.setJwtValidator(MobileJwtSecurity.validators("http://localhost:8080", "core-banking-api"));
            return decoder;
        }
    }
}
