package com.account.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "account_type")
@NoArgsConstructor
@AllArgsConstructor
public class AccountType {
    @Id
    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
