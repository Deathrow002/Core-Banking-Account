package com.account.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.account.model.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Override
    boolean existsById(@NonNull UUID id);

    boolean existsByCustomerId(@NonNull UUID customerId);
    
    List<Account> findByCustomerId(UUID customerId);

    @Query("select a from Account a where lower(a.accountType) like lower(concat('%', :q, '%')) "
            + "or lower(a.currency) like lower(concat('%', :q, '%')) "
            + "or lower(cast(a.accountId as string)) like lower(concat('%', :q, '%')) "
            + "or lower(cast(a.customerId as string)) like lower(concat('%', :q, '%'))")
    Page<Account> search(@Param("q") String query, Pageable pageable);
}
