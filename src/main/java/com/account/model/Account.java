package com.account.model;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "Account")
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID accountId;

    @Column(name = "customerId", nullable = false)
    private UUID customerId;

    @Column(name = "balance", nullable = false)
    @PositiveOrZero
    private BigDecimal balance;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "accountType", nullable = false)
    private String accountType;

    public Account(UUID customerId, BigDecimal balance, String currency, String accountType){
        this.customerId = customerId;
        this.balance = balance;
        this.currency = currency;
        this.accountType = accountType;
    }
}
