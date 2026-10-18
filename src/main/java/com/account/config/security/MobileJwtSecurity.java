package com.account.config.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
public class MobileJwtSecurity {

    @Bean
    public JwtDecoder mobileJwtDecoder(
            @Value("${app.oauth.issuer:${OAUTH_ISSUER:http://localhost:8080}}") String issuer,
            @Value("${app.oauth.jwk-set-uri:${OAUTH_JWK_SET_URI:http://authentication-service:8084/oauth2/jwks}}") String jwkSetUri,
            @Value("${app.oauth.audience:core-banking-api}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(validators(issuer, audience));
        return decoder;
    }

    public static DelegatingOAuth2TokenValidator<Jwt> validators(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            jwt -> {
                if (jwt.getAudience().contains(audience)
                        && jwt.getClaim("sub") instanceof String subject && !subject.isBlank()
                        && jwt.getClaim("customerId") instanceof String customerId && !customerId.isBlank()
                        && jwt.getClaim("role") instanceof String role
                        && Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_USER").contains(role)
                        && jwt.getExpiresAt() != null) {
                    return OAuth2TokenValidatorResult.success();
                }
                return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid banking audience or identity claims", null));
            });
    }

    public static JwtAuthenticationToken authentication(Jwt jwt) {
        var authorities = new ArrayList<>(new JwtGrantedAuthoritiesConverter().convert(jwt));
        String role = jwt.getClaimAsString("role");
        authorities.add(new SimpleGrantedAuthority(role));
        var authentication = new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        var details = new HashMap<String, Object>();
        details.put(SessionAuthentication.DETAIL_USERNAME, jwt.getSubject());
        details.put(SessionAuthentication.DETAIL_CUSTOMER_ID, jwt.getClaimAsString("customerId"));
        details.put(SessionAuthentication.DETAIL_ROLE, role);
        authentication.setDetails(details);
        return authentication;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain mobileSecurityFilterChain(HttpSecurity http, JwtDecoder mobileJwtDecoder)
            throws Exception {
        return http
            .securityMatcher("/api/mobile/**")
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority("SCOPE_banking"))
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt
                .decoder(mobileJwtDecoder).jwtAuthenticationConverter(MobileJwtSecurity::authentication)))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .build();
    }
}
