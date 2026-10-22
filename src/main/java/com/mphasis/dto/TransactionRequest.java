package com.mphasis.dto;

import java.math.BigDecimal;

public class TransactionRequest {
    private String transactionId;
    private String requestId;
    private String accountId;
    private String transactionType;
    private BigDecimal amount;
    private String currency;
    private Long sequenceNumber;

    /*
     * Assessment-only failure injection. Set to 1 to simulate one transient
     * failure before success, 3 to exhaust the default retry limit, etc.
     * In a real service this would be replaced by an infrastructure failure.
     */
    private Integer simulateTransientFailures = 0;

    public TransactionRequest() {}

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public Integer getSimulateTransientFailures() { return simulateTransientFailures; }
    public void setSimulateTransientFailures(Integer simulateTransientFailures) {
        this.simulateTransientFailures = simulateTransientFailures;
    }
}
