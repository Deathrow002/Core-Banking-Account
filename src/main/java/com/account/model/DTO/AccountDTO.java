package com.account.model.DTO;

import java.math.BigDecimal;
import java.util.UUID;

import com.account.model.CurrencyType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountDTO {
    private UUID accountId;

    private UUID customerId;

    private BigDecimal balance;

    private CurrencyType currency;

    private String accountType;

    public AccountDTO(UUID customerId, BigDecimal balance, CurrencyType currency, String accountType) {
        this.customerId = customerId;
        this.balance = balance;
        this.currency = currency;
        this.accountType = accountType;
    }
}
