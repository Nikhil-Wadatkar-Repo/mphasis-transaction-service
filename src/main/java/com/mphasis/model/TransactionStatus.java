package com.mphasis.model;

public enum TransactionStatus {
    RECEIVED,
    PROCESSING,
    PROCESSED,
    DUPLICATE,
    PENDING,
    FAILED,
    RETRY_PENDING
}
