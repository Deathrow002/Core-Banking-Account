package com.account.config.security;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * Reads the {@link Authentication} stored in the shared Redis session by the Authentication
 * service. The principal is the username and the details are a {@link java.util.HashMap} with
 * {@code username}, {@code customerId} and {@code role}.
 */
public final class SessionAuthentication {

    public static final String SECURITY_CONTEXT_ATTRIBUTE =
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

    public static final String DETAIL_USERNAME = "username";
    public static final String DETAIL_CUSTOMER_ID = "customerId";
    public static final String DETAIL_ROLE = "role";

    private SessionAuthentication() {
    }

    @SuppressWarnings("unchecked")
    public static String detail(Authentication authentication, String key) {
        if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details) {
            Object value = ((Map<String, Object>) details).get(key);
            return value != null ? value.toString() : null;
        }
        return null;
    }
}
