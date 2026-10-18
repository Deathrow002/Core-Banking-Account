package com.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.account.config.security.RestAuthenticationEntryPoint;

import lombok.RequiredArgsConstructor;

/**
 * Authenticates requests from the shared Redis session created by the Authentication service.
 * This service never logs users in; it only reads the {@code SPRING_SECURITY_CONTEXT} attribute.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final RestAuthenticationEntryPoint unauthorizedHandler;
    private final SecurityContextRepository securityContextRepository;

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher(new NegatedRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher("/api/mobile/**")))
            // XSRF-TOKEN cookie is issued by the Authentication service; clients echo it in X-XSRF-TOKEN.
            .csrf(csrf -> csrf.spa())
            .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.NEVER))
            .securityContext(context -> context.securityContextRepository(securityContextRepository))
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .build();
    }
}