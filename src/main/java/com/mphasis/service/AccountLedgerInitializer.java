package com.mphasis.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.mphasis.entity.AccountLedgerEntity;
import com.mphasis.entity.TransactionRecordEntity;
import com.mphasis.model.TransactionStatus;
import com.mphasis.model.TransactionType;
import com.mphasis.repo.AccountLedgerRepo;
import com.mphasis.repo.TransactionRepo;

@Component
public class AccountLedgerInitializer implements ApplicationRunner {
	private final TransactionRepo transactionRepo;
	private final AccountLedgerRepo accountLedgerRepo;
	private final TransactionTemplate transactionTemplate;

	public AccountLedgerInitializer(
			TransactionRepo transactionRepo,
			AccountLedgerRepo accountLedgerRepo,
			TransactionTemplate transactionTemplate) {
		this.transactionRepo = transactionRepo;
		this.accountLedgerRepo = accountLedgerRepo;
		this.transactionTemplate = transactionTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		transactionTemplate.executeWithoutResult(status -> initializeMissingLedgers());
	}

	private void initializeMissingLedgers() {
		Map<String, LedgerSnapshot> snapshots = new HashMap<>();
		List<TransactionRecordEntity> processed = transactionRepo.findAll().stream()
				.filter(record -> record.getStatus() == TransactionStatus.PROCESSED)
				.toList();

		for (TransactionRecordEntity record : processed) {
			BigDecimal effect = record.getTransactionType() == TransactionType.CREDIT
					? record.getAmount() : record.getAmount().negate();
			LedgerSnapshot snapshot = snapshots.computeIfAbsent(record.getAccountId(),
					accountId -> new LedgerSnapshot(record.getCurrency()));
			if (!snapshot.currency.equals(record.getCurrency())) {
				throw new IllegalStateException("Cannot initialize ledger for account "
						+ record.getAccountId() + ": processed history contains multiple currencies");
			}
			snapshot.balance = snapshot.balance.add(effect);
			snapshot.lastSequence = Math.max(snapshot.lastSequence, record.getSequenceNumber());
		}

		snapshots.forEach((accountId, snapshot) -> {
			accountLedgerRepo.createIfMissing(accountId, snapshot.currency);
			AccountLedgerEntity ledger = accountLedgerRepo.findByAccountIdForUpdate(accountId)
					.orElseThrow(() -> new IllegalStateException(
							"Account ledger row was not created for " + accountId));
			if (!ledger.getCurrency().equals(snapshot.currency)) {
				throw new IllegalStateException("Cannot initialize ledger for account "
						+ accountId + ": ledger currency does not match processed history");
			}
			if (ledger.getLastSequence() == 0) {
				ledger.setBalance(snapshot.balance);
				ledger.setLastSequence(snapshot.lastSequence);
				accountLedgerRepo.save(ledger);
			}
		});
	}

	private static class LedgerSnapshot {
		private final String currency;
		private BigDecimal balance = BigDecimal.ZERO;
		private long lastSequence;

		private LedgerSnapshot(String currency) {
			this.currency = currency;
		}
	}
}
