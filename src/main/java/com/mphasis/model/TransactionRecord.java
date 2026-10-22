package com.mphasis.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.mphasis.dto.TransactionRequest;

public class TransactionRecord {
    private final String transactionId;
    private final String requestId;
    private final String accountId;
    private final TransactionType transactionType;
    private final BigDecimal amount;
    private final String currency;
    private final long sequenceNumber;
    private final int simulatedFailures;
    private TransactionStatus status;
    private int attempts;
    private String errorMessage;
    private final Instant receivedAt;
    private Instant updatedAt;

    public TransactionRecord(TransactionRequest request, TransactionType type) {
        this.transactionId = request.getTransactionId().trim();
        this.requestId = request.getRequestId().trim();
        this.accountId = request.getAccountId().trim();
        this.transactionType = type;
        this.amount = request.getAmount();
        this.currency = request.getCurrency().trim().toUpperCase();
        this.sequenceNumber = request.getSequenceNumber();
        this.simulatedFailures = request.getSimulateTransientFailures() == null
                ? 0 : request.getSimulateTransientFailures();
        this.status = TransactionStatus.RECEIVED;
        this.receivedAt = Instant.now();
        this.updatedAt = receivedAt;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    public void incrementAttempts() {
        this.attempts++;
        this.updatedAt = Instant.now();
    }

    public String getTransactionId() { return transactionId; }
    public String getRequestId() { return requestId; }
    public String getAccountId() { return accountId; }
    public TransactionType getTransactionType() { return transactionType; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public long getSequenceNumber() { return sequenceNumber; }
    public int getSimulatedFailures() { return simulatedFailures; }
    public TransactionStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
