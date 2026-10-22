package com.mphasis.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.mphasis.model.TransactionStatus;

public record ProcessingSummary(
        int totalTransactions,
        Map<TransactionStatus, Long> countsByStatus,
        Map<String, BigDecimal> accountBalances
) {}
