package com.account.service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.account.model.AccountType;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.account.exception.AccountAlreadyExistsException;
import com.account.exception.InvalidLookupValueException;
import com.account.model.Account;
import com.account.model.CurrencyType;
import com.account.model.DTO.AccountDTO;
import com.account.model.DTO.AccountLookupsDTO;
import com.account.repository.AccountRepository;
import com.account.repository.AccountTypeRepository;
import com.account.repository.CurrencyTypeRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {
    String checkCustomerUrl  = "http://CUSTOMER-SERVICE/customers/validateById";

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    
    private final AccountRepository accountRepository;

    private final CurrencyTypeRepository currencyTypeRepository;

    private final AccountTypeRepository accountTypeRepository;

    private final RestTemplate restTemplate;

    //Check Account Balance
    public Optional<Account> checkBalance(UUID AccNo){
        return accountRepository.findById(AccNo);
    }

    //Get Lookup Values
    public AccountLookupsDTO getLookups() {
        return new AccountLookupsDTO(
            accountTypeRepository.findAllByOrderBySortOrderAsc().stream().map(AccountType::getCode).toList(),
            currencyTypeRepository.findAllByOrderBySortOrderAsc().stream().map(CurrencyType::getCode).toList()
        );
    }

    //Create Account
    @Transactional
    public Account createAccount(@NotNull AccountDTO accountDTO) {
        validateLookupValues(accountDTO);
        try {
            log.debug("Starting account creation for customer ID: {}", accountDTO.getCustomerId());

            // Check customer existence using the caller's browser or mobile authentication.
            if(!validateCustomer(accountDTO.getCustomerId())){
                log.error("Customer ID {} does not exist", accountDTO.getCustomerId());
                throw new AccountAlreadyExistsException("Customer ID does not exist");
            }

            // Create and save the account
            Account account = new Account(
                accountDTO.getCustomerId(),
                accountDTO.getBalance(),
                accountDTO.getCurrency(),
                accountDTO.getAccountType()
            );

            // Save the account
            Account savedAccount = accountRepository.save(account);

            // Log success
            log.info("Account successfully created for customer ID: {}", accountDTO.getCustomerId());
            return savedAccount;
        } catch (AccountAlreadyExistsException e) {
            log.error("Account creation failed: {}", e.getMessage());
            throw new AccountAlreadyExistsException("Customer ID does not exist");
        }  catch (RuntimeException e) {
            log.error("Error during account creation: {}", e.getMessage(), e);
            throw new RuntimeException("Account creation failed", e);
        }
    }

    //Update Account Balance
    @Transactional
    public Account updateAccountBalance(@NotNull AccountDTO accountDTO){
        //Prepare Payload
        Account payload = accountRepository.findById(UUID.fromString(accountDTO.getAccountId().toString())).get();

        //Set New Balance to Payload
        payload.setBalance(accountDTO.getBalance());

        //Update Balance
        return accountRepository.save(payload);
    }

    //Search Accounts (paged); blank query returns all
    public Page<Account> searchAccounts(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return accountRepository.findAll(pageable);
        }
        return accountRepository.search(query.trim(), pageable);
    }

    //Update Account details (type, currency, balance)
    @Transactional
    public Account updateAccount(@NotNull AccountDTO accountDTO) {
        validateLookupValues(accountDTO);
        Account account = accountRepository.findById(accountDTO.getAccountId())
            .orElseThrow(() -> new NoSuchElementException("Account not found."));
        account.setAccountType(accountDTO.getAccountType());
        account.setCurrency(accountDTO.getCurrency());
        if (accountDTO.getBalance() != null) {
            account.setBalance(accountDTO.getBalance());
        }
        return accountRepository.save(account);
    }

    //Delete Account
    public void deleteAccount(UUID AccNo){
        accountRepository.deleteById(AccNo);
    }

    //Get All Accounts
    public List<Account> getAllAccounts(){
        return accountRepository.findAll();
    }

    //Get Account by Customer ID
    public List<Account> searchByCustomerId(UUID customerId){
        return accountRepository.findByCustomerId(customerId);
    }

    //Check if Account Exists
    public boolean existsById(UUID AccNo) {
        return accountRepository.existsById(AccNo);
    }

    //Check if Customer Exists
    @CircuitBreaker(name = "customerService", fallbackMethod = "customerServiceFallback")
    public boolean validateCustomer(UUID customerId) {
        try {
            String requestUrl = checkCustomerUrl + "?customerId=" + customerId;

            ResponseEntity<Boolean> response = restTemplate.getForEntity(requestUrl, Boolean.class);
            log.info("Response: {}", response);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Valid Response: {}", response.getBody());
                return Boolean.TRUE.equals(response.getBody());
            } else {
                log.warn("Non-success HTTP response: {} for customer {} from URL: {}",
                    response.getStatusCode(), customerId, requestUrl);
                return false;
            }
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("RestClientException while validating customer {}: {}", customerId, e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException while validating customer {}: {}", customerId, e.getMessage());
            return false;
        }
    }

    public boolean customerServiceFallback(UUID customerId, Throwable t) {
        log.error("Fallback triggered for validateCustomer: Customer service unavailable. customerId={}, error={}", customerId, t.getMessage());
        return false;
    }

    private void validateLookupValues(AccountDTO accountDTO) {
        if (accountDTO.getCurrency() == null || !currencyTypeRepository.existsById(accountDTO.getCurrency())) {
            throw new InvalidLookupValueException("Unknown currency: " + accountDTO.getCurrency());
        }
        if (accountDTO.getAccountType() == null || !accountTypeRepository.existsById(accountDTO.getAccountType())) {
            throw new InvalidLookupValueException("Unknown account type: " + accountDTO.getAccountType());
        }
    }
}
