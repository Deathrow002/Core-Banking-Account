package com.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.account.exception.InvalidLookupValueException;
import com.account.model.Account;
import com.account.model.DTO.AccountDTO;
import com.account.repository.AccountRepository;
import com.account.repository.AccountTypeRepository;
import com.account.repository.CurrencyTypeRepository;
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
        var currencyRepository = mock(CurrencyTypeRepository.class);
        var accountTypeRepository = mock(AccountTypeRepository.class);
        when(currencyRepository.existsById("THB")).thenReturn(true);
        when(accountTypeRepository.existsById("SAVING")).thenReturn(true);
        var service = new AccountService(repository, currencyRepository, accountTypeRepository, rest);
        var payload = new AccountDTO(customerId, BigDecimal.TEN, "THB", "SAVING");

        Account saved = service.createAccount(payload);

        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getBalance()).isEqualTo(BigDecimal.TEN);
        verify(repository).save(saved);
        server.verify();
    }

    @Test
    void accountCreationRejectsCurrencyMissingFromLookupTable() {
        var repository = mock(AccountRepository.class);
        var currencyRepository = mock(CurrencyTypeRepository.class);
        var accountTypeRepository = mock(AccountTypeRepository.class);
        when(currencyRepository.existsById("XYZ")).thenReturn(false);
        var service = new AccountService(repository, currencyRepository, accountTypeRepository, new RestTemplate());
        var payload = new AccountDTO(UUID.randomUUID(), BigDecimal.TEN, "XYZ", "SAVING");

        assertThatThrownBy(() -> service.createAccount(payload))
            .isInstanceOf(InvalidLookupValueException.class)
            .hasMessageContaining("XYZ");
        verify(repository, never()).save(any(Account.class));
    }
}
