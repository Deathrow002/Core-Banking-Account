package com.account.config.security;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Keeps browser session forwarding separate from bearer-only mobile service calls.
 */
public class SessionForwardingInterceptor implements ClientHttpRequestInterceptor {

    static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final List<String> FORWARDED_HEADERS = List.of(HttpHeaders.COOKIE, CSRF_HEADER);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                && jwt.isAuthenticated()) {
            request.getHeaders().remove(HttpHeaders.COOKIE);
            request.getHeaders().remove(CSRF_HEADER);
            request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
            URI uri = request.getURI();
            String path = uri.getRawPath();
            if (!path.equals("/api/mobile") && !path.startsWith("/api/mobile/")) {
                URI mobileUri = URI.create(uri.getScheme() + "://" + uri.getRawAuthority()
                    + "/api/mobile" + path
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                    + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment()));
                request = new HttpRequestWrapper(request) {
                    @Override
                    public URI getURI() {
                        return mobileUri;
                    }
                };
            }
            return execution.execute(request, body);
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest inbound = servletAttributes.getRequest();
            for (String name : FORWARDED_HEADERS) {
                String value = inbound.getHeader(name);
                if (value != null && !request.getHeaders().containsHeader(name)) {
                    request.getHeaders().set(name, value);
                }
            }
        }
        return execution.execute(request, body);
    }
}
