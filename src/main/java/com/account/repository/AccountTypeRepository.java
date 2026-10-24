package com.account.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.account.model.AccountType;

@Repository
public interface AccountTypeRepository extends JpaRepository<AccountType, String> {
    List<AccountType> findAllByOrderBySortOrderAsc();
}
