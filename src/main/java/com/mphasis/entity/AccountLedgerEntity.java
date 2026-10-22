package com.mphasis.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "account_ledger")
public class AccountLedgerEntity {
	@Id
	private String accountId;
	@Column(nullable = false, length = 3)
	private String currency;
	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal balance;
	@Column(nullable = false)
	private long lastSequence;
	@Version
	private long version;

	public String getAccountId() {
		return accountId;
	}

	public void setAccountId(String accountId) {
		this.accountId = accountId;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public long getLastSequence() {
		return lastSequence;
	}

	public void setLastSequence(long lastSequence) {
		this.lastSequence = lastSequence;
	}
}
