package com.mphasis.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mphasis.entity.AccountLedgerEntity;

import jakarta.persistence.LockModeType;

@Repository
public interface AccountLedgerRepo extends JpaRepository<AccountLedgerEntity, String> {
	@Modifying
	@Query(value = """
			INSERT INTO account_ledger (account_id, currency, balance, last_sequence, version)
			VALUES (:accountId, :currency, 0, 0, 0)
			ON CONFLICT (account_id) DO NOTHING
			""", nativeQuery = true)
	int createIfMissing(@Param("accountId") String accountId, @Param("currency") String currency);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select account from AccountLedgerEntity account where account.accountId = :accountId")
	Optional<AccountLedgerEntity> findByAccountIdForUpdate(@Param("accountId") String accountId);
}
