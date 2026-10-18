package com.account.config.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class MobileMoneyMutationGuard {
    private MobileMoneyMutationGuard() {
    }

    public static boolean isMobile() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken;
    }

    public static ResponseEntity<String> rejectIfMobile() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken)) {
            return null;
        }
        var scopes = authentication.getAuthorities();
        boolean banking = scopes.stream().anyMatch(authority -> authority.getAuthority().equals("SCOPE_banking"));
        boolean writes = scopes.stream().anyMatch(authority -> authority.getAuthority().equals("SCOPE_transactions.write"));
        if (!banking || !writes) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Mobile money mutations require banking and transactions.write scopes.");
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body("Mobile money mutations are disabled in Phase 1 pending signed commands and an outbox.");
    }
}
