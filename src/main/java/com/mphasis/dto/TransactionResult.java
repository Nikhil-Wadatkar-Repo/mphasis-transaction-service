package com.mphasis.dto;

import com.mphasis.model.TransactionRecord;
import com.mphasis.model.TransactionStatus;

public record TransactionResult(
        String transactionId,
        TransactionStatus status,
        int attempts,
        String message
) {
    public static TransactionResult from(TransactionRecord record) {
        return new TransactionResult(
                record.getTransactionId(),
                record.getStatus(),
                record.getAttempts(),
                record.getErrorMessage()
        );
    }
}
