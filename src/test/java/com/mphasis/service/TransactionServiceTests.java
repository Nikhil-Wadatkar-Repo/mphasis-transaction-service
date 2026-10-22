package com.mphasis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import com.mphasis.dto.TransactionRequest;
import com.mphasis.dto.TransactionResult;
import com.mphasis.entity.AccountLedgerEntity;
import com.mphasis.entity.TransactionRecordEntity;
import com.mphasis.model.TransactionStatus;
import com.mphasis.repo.AccountLedgerRepo;
import com.mphasis.repo.TransactionRepo;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.mockito.Mockito;

class TransactionServiceTests {
	private TransactionRepo transactionRepo;
	private AccountLedgerRepo accountLedgerRepo;
	private TransactionService transactionService;
	private AccountLedgerEntity ledger;
	private List<TransactionRecordEntity> savedRecords;

	@BeforeEach
	void setUp() {
		transactionRepo = Mockito.mock(TransactionRepo.class);
		accountLedgerRepo = Mockito.mock(AccountLedgerRepo.class);
		transactionService = new TransactionService(
				transactionRepo, accountLedgerRepo, new SimpleMeterRegistry(),
				Mockito.mock(TransactionTemplate.class));
		savedRecords = new ArrayList<>();

		ledger = new AccountLedgerEntity();
		ledger.setAccountId("account-1");
		ledger.setCurrency("USD");
		ledger.setBalance(BigDecimal.ZERO);
		ledger.setLastSequence(0);

		when(transactionRepo.findById("transaction-1")).thenReturn(Optional.empty());
		when(accountLedgerRepo.findByAccountIdForUpdate("account-1"))
				.thenReturn(Optional.of(ledger));
		when(transactionRepo.save(any(TransactionRecordEntity.class)))
				.thenAnswer(invocation -> {
					TransactionRecordEntity record = invocation.getArgument(0);
					savedRecords.add(copy(record));
					return record;
				});
		when(accountLedgerRepo.save(any(AccountLedgerEntity.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(transactionRepo.findByAccountIdAndStatusOrderBySequenceNumberAsc(
				"account-1", TransactionStatus.PENDING)).thenReturn(List.of());
	}

	@Test
	void recordsTransactionAndLedgerEffectTogetherForSuccessfulCredit() {
		TransactionResult result = transactionService.submit(request("CREDIT", "12.50", 0));

		assertEquals(TransactionStatus.PROCESSED, result.status());
		assertEquals(new BigDecimal("12.50"), ledger.getBalance());
		assertEquals(1, ledger.getLastSequence());
		verify(transactionRepo, times(2)).save(any(TransactionRecordEntity.class));
		verify(accountLedgerRepo).save(ledger);
		assertEquals(TransactionStatus.PROCESSED,
				savedRecords.get(savedRecords.size() - 1).getStatus());
	}

	@Test
	void persistsDueTimeAndAttemptForTransientFailure() {
		TransactionResult result = transactionService.submit(request("CREDIT", "12.50", 1));

		assertEquals(TransactionStatus.RETRY_PENDING, result.status());
		assertEquals(1, result.attempts());
		TransactionRecordEntity saved = savedRecords.get(savedRecords.size() - 1);
		assertEquals(TransactionStatus.RETRY_PENDING, saved.getStatus());
		assertEquals(1, saved.getAttempts());
		assertNotNull(saved.getNextAttemptAt());
		assertEquals("TRANSIENT", saved.getFailureCategory());
		verify(accountLedgerRepo, never()).save(any(AccountLedgerEntity.class));
	}

	@Test
	void persistsBusinessFailureWithoutChangingLedger() {
		TransactionResult result = transactionService.submit(request("DEBIT", "12.50", 0));

		assertEquals(TransactionStatus.FAILED, result.status());
		assertEquals("Insufficient funds", result.message());
		assertEquals(BigDecimal.ZERO, ledger.getBalance());
		assertEquals(0, ledger.getLastSequence());
		TransactionRecordEntity saved = savedRecords.get(savedRecords.size() - 1);
		assertEquals(TransactionStatus.FAILED, saved.getStatus());
		assertEquals("BUSINESS_RULE", saved.getFailureCategory());
		assertEquals("Insufficient funds", saved.getErrorMessage());
		verify(accountLedgerRepo, never()).save(any(AccountLedgerEntity.class));
	}

	@Test
	void rejectsAnAlreadyClaimedAccountSequence() {
		when(transactionRepo.existsByAccountIdAndSequenceNumber("account-1", 1))
				.thenReturn(false, true);
		TransactionResult first = transactionService.submit(request("CREDIT", "12.50", 0));
		TransactionRequest duplicateSequence = request("CREDIT", "5.00", 0);
		duplicateSequence.setTransactionId("transaction-2");

		TransactionResult second = transactionService.submit(duplicateSequence);

		assertEquals(TransactionStatus.PROCESSED, first.status());
		assertEquals(TransactionStatus.FAILED, second.status());
		assertEquals("sequenceNumber has already been submitted for this account", second.message());
		assertEquals(2, savedRecords.size());
	}

	private TransactionRequest request(String type, String amount, int failures) {
		TransactionRequest request = new TransactionRequest();
		request.setTransactionId("transaction-1");
		request.setRequestId("request-1");
		request.setAccountId("account-1");
		request.setTransactionType(type);
		request.setAmount(new BigDecimal(amount));
		request.setCurrency("USD");
		request.setSequenceNumber(1L);
		request.setSimulateTransientFailures(failures);
		return request;
	}

	private TransactionRecordEntity copy(TransactionRecordEntity source) {
		TransactionRecordEntity target = new TransactionRecordEntity();
		target.setTransactionId(source.getTransactionId());
		target.setRequestId(source.getRequestId());
		target.setAccountId(source.getAccountId());
		target.setTransactionType(source.getTransactionType());
		target.setAmount(source.getAmount());
		target.setCurrency(source.getCurrency());
		target.setSequenceNumber(source.getSequenceNumber());
		target.setSimulatedFailures(source.getSimulatedFailures());
		target.setStatus(source.getStatus());
		target.setAttempts(source.getAttempts());
		target.setErrorMessage(source.getErrorMessage());
		target.setFailureCategory(source.getFailureCategory());
		target.setNextAttemptAt(source.getNextAttemptAt());
		target.setReceivedAt(source.getReceivedAt());
		target.setUpdatedAt(source.getUpdatedAt());
		return target;
	}
}
