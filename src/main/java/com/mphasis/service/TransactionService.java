package com.mphasis.service;

import com.mphasis.dto.ProcessingSummary;
import com.mphasis.dto.TransactionRequest;
import com.mphasis.dto.TransactionResult;
import com.mphasis.entity.AccountLedgerEntity;
import com.mphasis.entity.TransactionRecordEntity;
import com.mphasis.repo.AccountLedgerRepo;
import com.mphasis.model.TransactionStatus;
import com.mphasis.model.TransactionType;
import com.mphasis.repo.TransactionRepo;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Currency;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

@Service
public class TransactionService {
    private static final Logger log = Logger.getLogger(TransactionService.class.getName());
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_RETRY_DELAY_MILLIS = 1_000;
    private static final long MAX_RETRY_DELAY_MILLIS = 60_000;
    private static final java.util.regex.Pattern ACCOUNT_ID_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private final TransactionRepo transactionRepo;
    private final AccountLedgerRepo accountLedgerRepo;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate transactionTemplate;

    public TransactionService(
            TransactionRepo transactionRepo,
            AccountLedgerRepo accountLedgerRepo,
            MeterRegistry meterRegistry,
            TransactionTemplate transactionTemplate) {
        this.transactionRepo = transactionRepo;
        this.accountLedgerRepo = accountLedgerRepo;
        this.meterRegistry = meterRegistry;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public TransactionResult submit(TransactionRequest request) {
        String validationError = validate(request);
        if (validationError != null) {
            String id = request == null || blank(request.getTransactionId())
                    ? "UNKNOWN" : request.getTransactionId().trim();
            log.warning("Rejected invalid transaction id=" + id + ", reason=" + validationError);
            return metric(new TransactionResult(id, TransactionStatus.FAILED, 0, validationError));
        }

        String transactionId = request.getTransactionId().trim();
        TransactionType type = TransactionType.valueOf(
                request.getTransactionType().trim().toUpperCase(Locale.ROOT));

        TransactionRecordEntity existing = transactionRepo.findById(transactionId).orElse(null);
        if (existing != null) {
            return metric(duplicateOrConflict(existing, request, type));
        }

        String accountId = request.getAccountId().trim();
        String currency = request.getCurrency().trim().toUpperCase(Locale.ROOT);
        accountLedgerRepo.createIfMissing(accountId, currency);
        AccountLedgerEntity account = accountLedgerRepo.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalStateException("Account ledger row was not created"));
        if (!account.getCurrency().equals(currency)) {
            return metric(new TransactionResult(transactionId, TransactionStatus.FAILED, 0,
                    "Account currency is " + account.getCurrency() + "; received " + currency));
        }

        existing = transactionRepo.findById(transactionId).orElse(null);
        if (existing != null) {
            return metric(duplicateOrConflict(existing, request, type));
        }

        if (transactionRepo.existsByAccountIdAndSequenceNumber(
                accountId, request.getSequenceNumber())) {
            return metric(new TransactionResult(transactionId, TransactionStatus.FAILED, 0,
                    "sequenceNumber has already been submitted for this account"));
        }

        TransactionRecordEntity record = toEntity(request, type);
        record = transactionRepo.save(record);
        log.info("Received transaction id=" + transactionId + ", account=" + record.getAccountId());

        long expected = account.getLastSequence() + 1;
        if (record.getSequenceNumber() > expected) {
            setStatus(record, TransactionStatus.PENDING);
            transactionRepo.save(record);
            log.info("Transaction pending due to sequence gap: " + transactionId
                    + ", expected=" + expected);
            return metric(toResult(record));
        }

        if (record.getSequenceNumber() < expected) {
            setStatus(record, TransactionStatus.FAILED);
            setErrorMessage(record, "Stale sequence number; expected sequence " + expected);
            record.setFailureCategory("SEQUENCE");
            transactionRepo.save(record);
            return metric(toResult(record));
        }

        processRecord(record, account);
        if (record.getStatus() == TransactionStatus.PROCESSED) {
            drainPending(account);
        }
        return metric(toResult(record));
    }

    public List<TransactionResult> submitBatch(List<TransactionRequest> requests) {
        List<TransactionResult> results = new ArrayList<>();
        if (requests == null) {
            results.add(new TransactionResult("UNKNOWN", TransactionStatus.FAILED, 0,
                    "Request body must be a JSON array"));
            return results;
        }
        for (TransactionRequest request : requests) {
            try {
                results.add(transactionTemplate.execute(status -> submit(request)));
            } catch (RuntimeException ex) {
                String id = request == null || blank(request.getTransactionId())
                        ? "UNKNOWN" : request.getTransactionId().trim();
                log.warning("Unexpected record-level failure for id=" + id);
                results.add(new TransactionResult(id, TransactionStatus.FAILED, 0,
                        "Record could not be processed"));
            }
        }
        return results;
    }

    @Transactional(readOnly = true)
    public List<TransactionResult> getTransactions() {
        return transactionRepo.findAll().stream()
                .sorted(Comparator.comparing(TransactionRecordEntity::getReceivedAt))
                .map(TransactionService::toResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResult> getFailedTransactions() {
        return transactionRepo.findByStatusOrderByReceivedAtAsc(TransactionStatus.FAILED)
                .stream().map(TransactionService::toResult).toList();
    }

    @Transactional(readOnly = true)
    public ProcessingSummary getSummary() {
        List<TransactionRecordEntity> records = transactionRepo.findAll();
        Map<TransactionStatus, Long> counts = new EnumMap<>(TransactionStatus.class);
        for (TransactionStatus status : TransactionStatus.values()) {
            counts.put(status, 0L);
        }

        for (TransactionRecordEntity record : records) {
            counts.merge(record.getStatus(), 1L, Long::sum);
        }
        Map<String, BigDecimal> balances = new TreeMap<>();
        accountLedgerRepo.findAll().stream()
                .filter(account -> account.getLastSequence() > 0)
                .forEach(account -> balances.put(account.getAccountId(), account.getBalance()));
        return new ProcessingSummary(records.size(), counts, new TreeMap<>(balances));
    }

    @Transactional
    public TransactionResult retry(String transactionId) {
        TransactionRecordEntity record = transactionRepo.findById(transactionId).orElse(null);
        if (record == null) {
            return metric(new TransactionResult(transactionId, TransactionStatus.FAILED, 0,
                    "Transaction not found"));
        }
        if (record.getStatus() != TransactionStatus.FAILED) {
            return metric(toResult(record));
        }
        AccountLedgerEntity account = accountLedgerRepo.findByAccountIdForUpdate(record.getAccountId())
                .orElseThrow(() -> new IllegalStateException("Account ledger row was not found"));
        record = transactionRepo.findById(transactionId).orElseThrow();
        long expected = account.getLastSequence() + 1;
        if (record.getSequenceNumber() != expected) {
            return metric(new TransactionResult(transactionId, record.getStatus(), record.getAttempts(),
                    "Cannot retry yet; expected sequence " + expected));
        }

        record.setAttempts(0);
        setErrorMessage(record, null);
        record.setFailureCategory(null);
        record.setNextAttemptAt(null);
        processRecord(record, account);
        if (record.getStatus() == TransactionStatus.PROCESSED) {
            drainPending(account);
        }
        return metric(toResult(record));
    }

    @Transactional
    public void processDueRetries() {
        Instant now = Instant.now();
        List<TransactionRecordEntity> due = transactionRepo
                .findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        TransactionStatus.RETRY_PENDING, now);
        for (TransactionRecordEntity candidate : due) {
            AccountLedgerEntity account = accountLedgerRepo
                    .findByAccountIdForUpdate(candidate.getAccountId()).orElse(null);
            if (account == null) {
                log.severe("Missing ledger row for retry transaction id=" + candidate.getTransactionId());
                continue;
            }
            TransactionRecordEntity record = transactionRepo.findById(candidate.getTransactionId())
                    .orElse(null);
            if (record == null || record.getStatus() != TransactionStatus.RETRY_PENDING
                    || record.getNextAttemptAt() == null || record.getNextAttemptAt().isAfter(now)
                    || record.getSequenceNumber() != account.getLastSequence() + 1) {
                continue;
            }
            processRecord(record, account);
            if (record.getStatus() == TransactionStatus.PROCESSED) {
                drainPending(account);
            }
        }
    }

    private void processRecord(TransactionRecordEntity record, AccountLedgerEntity account) {
        if (record.getAttempts() >= MAX_ATTEMPTS) {
            fail(record, "Retry limit exhausted after " + MAX_ATTEMPTS + " attempts", "TRANSIENT");
            return;
        }
        setStatus(record, TransactionStatus.PROCESSING);
        record.setAttempts(record.getAttempts() + 1);
        touch(record);
        try {
            if (record.getAttempts() <= record.getSimulatedFailures()) {
                throw new TransientProcessingException("Simulated transient failure");
            }

            BigDecimal newBalance = record.getTransactionType() == TransactionType.CREDIT
                    ? account.getBalance().add(record.getAmount())
                    : account.getBalance().subtract(record.getAmount());
            if (newBalance.signum() < 0) {
                fail(record, "Insufficient funds", "BUSINESS_RULE");
                return;
            }

            account.setBalance(newBalance);
            account.setLastSequence(record.getSequenceNumber());
            setStatus(record, TransactionStatus.PROCESSED);
            setErrorMessage(record, null);
            record.setFailureCategory(null);
            record.setNextAttemptAt(null);
            accountLedgerRepo.save(account);
            transactionRepo.save(record);
            log.info("Processed transaction id=" + record.getTransactionId()
                    + ", attempts=" + record.getAttempts());
        } catch (TransientProcessingException ex) {
            setErrorMessage(record, ex.getMessage());
            record.setFailureCategory("TRANSIENT");
            if (record.getAttempts() < MAX_ATTEMPTS) {
                setStatus(record, TransactionStatus.RETRY_PENDING);
                record.setNextAttemptAt(Instant.now().plusMillis(retryDelayMillis(record.getAttempts())));
                log.warning("Scheduled retry for transaction id=" + record.getTransactionId()
                        + ", attempt=" + record.getAttempts());
            } else {
                fail(record, "Retry limit exhausted after " + MAX_ATTEMPTS + " attempts", "TRANSIENT");
                log.warning("Retry limit exhausted for transaction id=" + record.getTransactionId());
            }
            transactionRepo.save(record);
        }
    }

    private void drainPending(AccountLedgerEntity account) {
        while (true) {
            long expected = account.getLastSequence() + 1;
            TransactionRecordEntity next = transactionRepo
                    .findByAccountIdAndStatusOrderBySequenceNumberAsc(
                            account.getAccountId(), TransactionStatus.PENDING)
                    .stream()
                    .filter(record -> record.getSequenceNumber() == expected)
                    .findFirst()
                    .orElse(null);
            if (next == null) {
                return;
            }

            processRecord(next, account);
            if (next.getStatus() != TransactionStatus.PROCESSED) {
                return;
            }
        }
    }

    private String validate(TransactionRequest r) {
        if (r == null) return "Transaction record must not be null";
        if (blank(r.getTransactionId())) return "transactionId is required";
        if (blank(r.getRequestId())) return "requestId is required";
        if (blank(r.getAccountId())) return "accountId is required";
        if (!ACCOUNT_ID_PATTERN.matcher(r.getAccountId().trim()).matches()) {
            return "accountId must be 1-64 characters using letters, digits, dot, underscore, or hyphen";
        }
        if (blank(r.getTransactionType())) return "transactionType is required";
        if (r.getAmount() == null || r.getAmount().signum() <= 0) return "amount must be greater than zero";
        BigDecimal normalizedAmount = r.getAmount().stripTrailingZeros();
        if (normalizedAmount.precision() - normalizedAmount.scale() > 15) {
            return "amount exceeds the supported account balance precision";
        }
        if (blank(r.getCurrency()) || !r.getCurrency().trim().matches("[A-Za-z]{3}")) {
            return "currency must be a three-letter code";
        }
        Currency currency;
        try {
            currency = Currency.getInstance(r.getCurrency().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return "currency must be a recognized ISO-4217 code";
        }
        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0 || r.getAmount() != null
                && r.getAmount().stripTrailingZeros().scale() > fractionDigits) {
            return "amount has more fractional digits than the currency supports";
        }
        if (r.getSequenceNumber() == null || r.getSequenceNumber() < 1) {
            return "sequenceNumber must be greater than zero";
        }
        if (r.getSimulateTransientFailures() != null
                && (r.getSimulateTransientFailures() < 0 || r.getSimulateTransientFailures() > 10)) {
            return "simulateTransientFailures must be between 0 and 10";
        }
        try {
            TransactionType.valueOf(r.getTransactionType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return "transactionType must be CREDIT or DEBIT";
        }
        return null;
    }

    private boolean sameBusinessRequest(
            TransactionRecordEntity old, TransactionRequest incoming, TransactionType type) {
        return Objects.equals(old.getAccountId(), incoming.getAccountId().trim())
                && old.getTransactionType() == type
                && old.getAmount().compareTo(incoming.getAmount()) == 0
                && Objects.equals(old.getCurrency(), incoming.getCurrency().trim().toUpperCase(Locale.ROOT))
                && old.getSequenceNumber() == incoming.getSequenceNumber();
    }

    private TransactionRecordEntity toEntity(TransactionRequest request, TransactionType type) {
        Instant now = Instant.now();
        TransactionRecordEntity record = new TransactionRecordEntity();
        record.setTransactionId(request.getTransactionId().trim());
        record.setRequestId(request.getRequestId().trim());
        record.setAccountId(request.getAccountId().trim());
        record.setTransactionType(type);
        record.setAmount(request.getAmount());
        record.setCurrency(request.getCurrency().trim().toUpperCase(Locale.ROOT));
        record.setSequenceNumber(request.getSequenceNumber());
        record.setSimulatedFailures(request.getSimulateTransientFailures() == null
                ? 0 : request.getSimulateTransientFailures());
        record.setStatus(TransactionStatus.RECEIVED);
        record.setAttempts(0);
        record.setFailureCategory(null);
        record.setNextAttemptAt(null);
        record.setReceivedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private static TransactionResult toResult(TransactionRecordEntity record) {
        return new TransactionResult(record.getTransactionId(), record.getStatus(),
                record.getAttempts(), record.getErrorMessage());
    }

    private void setStatus(TransactionRecordEntity record, TransactionStatus status) {
        record.setStatus(status);
        touch(record);
    }

    private void setErrorMessage(TransactionRecordEntity record, String message) {
        record.setErrorMessage(message);
        touch(record);
    }

    private void touch(TransactionRecordEntity record) {
        record.setUpdatedAt(Instant.now());
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private TransactionResult duplicateOrConflict(
            TransactionRecordEntity existing, TransactionRequest request, TransactionType type) {
        if (!sameBusinessRequest(existing, request, type)) {
            return new TransactionResult(existing.getTransactionId(), TransactionStatus.FAILED, 0,
                    "transactionId was already used with different transaction data");
        }
        log.info("Duplicate transaction detected: " + existing.getTransactionId());
        return new TransactionResult(existing.getTransactionId(), TransactionStatus.DUPLICATE,
                existing.getAttempts(), "Duplicate ignored; no business effect applied");
    }

    private void fail(TransactionRecordEntity record, String reason, String category) {
        setStatus(record, TransactionStatus.FAILED);
        setErrorMessage(record, reason);
        record.setFailureCategory(category);
        record.setNextAttemptAt(null);
        transactionRepo.save(record);
    }

    private long retryDelayMillis(int attempt) {
        long exponential = Math.min(MAX_RETRY_DELAY_MILLIS,
                BASE_RETRY_DELAY_MILLIS << Math.max(0, attempt - 1));
        double jitter = ThreadLocalRandom.current().nextDouble(0.75, 1.25);
        return Math.max(1, (long) (exponential * jitter));
    }

    private TransactionResult metric(TransactionResult result) {
        meterRegistry.counter("transactions.results", "status", result.status().name()).increment();
        return result;
    }

    private static class TransientProcessingException extends RuntimeException {
        private TransientProcessingException(String message) {
            super(message);
        }
    }
}
