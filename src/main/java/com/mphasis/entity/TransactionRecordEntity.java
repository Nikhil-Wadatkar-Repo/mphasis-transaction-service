package com.mphasis.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.mphasis.model.TransactionStatus;
import com.mphasis.model.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
@Table(name = "transaction_record")
@Entity
public class TransactionRecordEntity {
	@Id
	private String transactionId;
	@Column
	private String requestId;
	@Column
	private String accountId;
	@Column
	private TransactionType transactionType;
	@Column
	private BigDecimal amount;
	@Column
	private String currency;
	@Column
	private long sequenceNumber;
	@Column
	private int simulatedFailures;
	@Column
	private TransactionStatus status;
	@Column
	private int attempts;
	@Column
	private String errorMessage;
	@Column
	private String failureCategory;
	@Column
	private Instant nextAttemptAt;
	@Column
	private Instant receivedAt;
	@Column
	private Instant updatedAt;
	public String getTransactionId() {
		return transactionId;
	}
	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}
	public String getRequestId() {
		return requestId;
	}
	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}
	public String getAccountId() {
		return accountId;
	}
	public void setAccountId(String accountId) {
		this.accountId = accountId;
	}
	public TransactionType getTransactionType() {
		return transactionType;
	}
	public void setTransactionType(TransactionType transactionType) {
		this.transactionType = transactionType;
	}
	public BigDecimal getAmount() {
		return amount;
	}
	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}
	public String getCurrency() {
		return currency;
	}
	public void setCurrency(String currency) {
		this.currency = currency;
	}
	public long getSequenceNumber() {
		return sequenceNumber;
	}
	public void setSequenceNumber(long sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}
	public int getSimulatedFailures() {
		return simulatedFailures;
	}
	public void setSimulatedFailures(int simulatedFailures) {
		this.simulatedFailures = simulatedFailures;
	}
	public TransactionStatus getStatus() {
		return status;
	}
	public void setStatus(TransactionStatus status) {
		this.status = status;
	}
	public int getAttempts() {
		return attempts;
	}
	public void setAttempts(int attempts) {
		this.attempts = attempts;
	}
	public String getErrorMessage() {
		return errorMessage;
	}
	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
	public String getFailureCategory() {
		return failureCategory;
	}
	public void setFailureCategory(String failureCategory) {
		this.failureCategory = failureCategory;
	}
	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}
	public void setNextAttemptAt(Instant nextAttemptAt) {
		this.nextAttemptAt = nextAttemptAt;
	}
	public Instant getReceivedAt() {
		return receivedAt;
	}
	public void setReceivedAt(Instant receivedAt) {
		this.receivedAt = receivedAt;
	}
	public Instant getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
	@Override
	public String toString() {
		return "TransactionRecordEntity [transactionId=" + transactionId
				+ ", requestId=" + requestId + ", accountId=" + accountId
				+ ", transactionType=" + transactionType + ", amount=" + amount
				+ ", currency=" + currency + ", sequenceNumber="
				+ sequenceNumber + ", simulatedFailures=" + simulatedFailures
				+ ", status=" + status + ", attempts=" + attempts
				+ ", errorMessage=" + errorMessage + ", failureCategory="
				+ failureCategory + ", nextAttemptAt=" + nextAttemptAt + ", receivedAt="
				+ receivedAt + ", updatedAt=" + updatedAt + "]";
	}

}