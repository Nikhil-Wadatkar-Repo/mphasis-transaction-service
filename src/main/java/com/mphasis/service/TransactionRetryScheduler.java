package com.mphasis.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TransactionRetryScheduler {
	private final TransactionService transactionService;

	public TransactionRetryScheduler(TransactionService transactionService) {
		this.transactionService = transactionService;
	}

	@Scheduled(fixedDelayString = "${app.retry.poll-interval-ms:1000}")
	public void processDueRetries() {
		transactionService.processDueRetries();
	}
}
