package com.account.model.DTO;

import java.util.List;

public record AccountLookupsDTO(List<String> accountTypes, List<String> currencyTypes) {
}
