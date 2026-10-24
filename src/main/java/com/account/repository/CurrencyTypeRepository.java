package com.account.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.account.model.CurrencyType;

@Repository
public interface CurrencyTypeRepository extends JpaRepository<CurrencyType, String> {
    List<CurrencyType> findAllByOrderBySortOrderAsc();
}
