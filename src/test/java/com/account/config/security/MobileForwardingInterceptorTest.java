package com.account.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class MobileForwardingInterceptorTest {
    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/customers/validateById", "/api/mobile/customers/validateById"})
    void authenticatedJwtForwardsOnlyBearerAndPrefixesPathExactlyOnce(String originalPath) {
        var inbound = new MockHttpServletRequest();
        inbound.addHeader(HttpHeaders.COOKIE, "SESSION=browser; XSRF-TOKEN=csrf");
        inbound.addHeader("X-XSRF-TOKEN", "csrf");
        inbound.addHeader(HttpHeaders.AUTHORIZATION, "Bearer untrusted-inbound");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));
        var jwt = Jwt.withTokenValue("validated-access-token").header("alg", "RS256")
            .subject("alice@example.test").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        var rest = new RestTemplate();
        rest.setInterceptors(List.of(new SessionForwardingInterceptor()));
        rest.getInterceptors().addFirst((request, body, execution) -> {
            request.getHeaders().set(HttpHeaders.COOKIE, "SESSION=explicit");
            request.getHeaders().set("X-XSRF-TOKEN", "explicit");
            return execution.execute(request, body);
        });
        var server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("http://CUSTOMER-SERVICE/api/mobile/customers/validateById?email=a%2Bb%40test"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer validated-access-token"))
            .andExpect(headerDoesNotExist(HttpHeaders.COOKIE))
            .andExpect(headerDoesNotExist("X-XSRF-TOKEN"))
            .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));

        var response = rest.getForObject(java.net.URI.create(
            "http://CUSTOMER-SERVICE" + originalPath + "?email=a%2Bb%40test"), Boolean.class);
        assertThat(response).isTrue();
        server.verify();
    }
}
