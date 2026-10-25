package com.account.model.DTO;

import java.util.List;

import com.account.model.Account;

public record AccountPage(List<Account> content, long totalElements, int totalPages, int number, int size) {
}
