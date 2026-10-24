package com.account.config;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.account.model.AccountType;
import com.account.model.CurrencyType;
import com.account.repository.AccountTypeRepository;
import com.account.repository.CurrencyTypeRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LookupDataInitializer implements ApplicationRunner {

    private static final List<String> CURRENCY_CODES = List.of("THB", "USD", "SGD", "JPY", "CNY", "GBP", "EUR");
    private static final List<String> ACCOUNT_TYPE_CODES = List.of("SAVING", "CURRENT", "FIXED", "RECURRING", "NRI", "LOAN");

    private final CurrencyTypeRepository currencyTypeRepository;
    private final AccountTypeRepository accountTypeRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // ddl-auto=update keeps the check constraints generated from the former enum columns, which would reject new codes.
        jdbcTemplate.execute("ALTER TABLE IF EXISTS account DROP CONSTRAINT IF EXISTS account_currency_check");
        jdbcTemplate.execute("ALTER TABLE IF EXISTS account DROP CONSTRAINT IF EXISTS account_account_type_check");

        for (int i = 0; i < CURRENCY_CODES.size(); i++) {
            String code = CURRENCY_CODES.get(i);
            if (!currencyTypeRepository.existsById(code)) {
                currencyTypeRepository.save(new CurrencyType(code, i + 1));
            }
        }
        for (int i = 0; i < ACCOUNT_TYPE_CODES.size(); i++) {
            String code = ACCOUNT_TYPE_CODES.get(i);
            if (!accountTypeRepository.existsById(code)) {
                accountTypeRepository.save(new AccountType(code, i + 1));
            }
        }
    }
}
