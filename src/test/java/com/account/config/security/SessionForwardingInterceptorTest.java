package com.account.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class SessionForwardingInterceptorTest {

    private final SessionForwardingInterceptor interceptor = new SessionForwardingInterceptor();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void forwardsCookieAndCsrfHeaderFromInboundRequest() throws Exception {
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader(HttpHeaders.COOKIE, "SESSION=abc123; XSRF-TOKEN=tok");
        inbound.addHeader("X-XSRF-TOKEN", "tok");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://CUSTOMER-SERVICE/x"));
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(execution.execute(any(), any())).thenReturn(response);

        ClientHttpResponse result = interceptor.intercept(outbound, new byte[0], execution);

        assertThat(result).isSameAs(response);
        assertThat(outbound.getHeaders().getFirst(HttpHeaders.COOKIE)).isEqualTo("SESSION=abc123; XSRF-TOKEN=tok");
        assertThat(outbound.getHeaders().getFirst("X-XSRF-TOKEN")).isEqualTo("tok");
        assertThat(outbound.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
        verify(execution).execute(outbound, new byte[0]);
    }

    @Test
    void doesNotOverrideExplicitOutboundHeaders() throws Exception {
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader(HttpHeaders.COOKIE, "SESSION=inbound");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://x/y"));
        outbound.getHeaders().set(HttpHeaders.COOKIE, "SESSION=explicit");
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);

        interceptor.intercept(outbound, new byte[0], execution);

        assertThat(outbound.getHeaders().get(HttpHeaders.COOKIE)).containsExactly("SESSION=explicit");
        assertThat(outbound.getHeaders().containsHeader("X-XSRF-TOKEN")).isFalse();
    }

    @Test
    void passesThroughWhenNoInboundRequest() throws Exception {
        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://x/y"));
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);

        interceptor.intercept(outbound, new byte[0], execution);

        assertThat(outbound.getHeaders().containsHeader(HttpHeaders.COOKIE)).isFalse();
        verify(execution).execute(outbound, new byte[0]);
    }
}
