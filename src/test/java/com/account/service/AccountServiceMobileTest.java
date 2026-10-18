package com.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.account.config.security.SessionForwardingInterceptor;
import com.account.model.Account;
import com.account.model.CurrencyType;
import com.account.model.DTO.AccountDTO;
import com.account.repository.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AccountServiceMobileTest {
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mobileAccountCreationValidatesCustomerUsingBearerAliasBeforeSaving() {
        UUID customerId = UUID.randomUUID();
        var jwt = Jwt.withTokenValue("validated-mobile-token").header("alg", "RS256")
            .subject("admin@example.test").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        var rest = new RestTemplate();
        rest.getInterceptors().add(new SessionForwardingInterceptor());
        var server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("http://CUSTOMER-SERVICE/api/mobile/customers/validateById?customerId=" + customerId))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer validated-mobile-token"))
            .andExpect(headerDoesNotExist(HttpHeaders.COOKIE))
            .andExpect(headerDoesNotExist("X-XSRF-TOKEN"))
            .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));
        var repository = mock(AccountRepository.class);
        when(repository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new AccountService(repository, rest);
        var payload = new AccountDTO(customerId, BigDecimal.TEN, CurrencyType.values()[0], "SAVING");

        Account saved = service.createAccount(payload);

        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getBalance()).isEqualTo(BigDecimal.TEN);
        verify(repository).save(saved);
        server.verify();
    }
}
