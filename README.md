# CTS — Idempotent Background Transaction Processing Service

A lightweight Spring Boot implementation of the CTS FDE L2 assessment. It demonstrates idempotency, duplicate detection, sequence-based ordering, bounded retry behavior, transaction status reporting, input validation, and unit tests.

## Requirements

- Java 17+
- Maven 3.8+

## Run

```bash
mvn clean test
mvn spring-boot:run
```

The service starts on `http://localhost:8080`.

## Endpoints

- `POST /api/transactions` — submit one transaction
- `POST /api/transactions/batch` — submit a batch; record-level invalid input is isolated
- `GET /api/transactions` — current canonical transaction states
- `GET /api/summary` — counts by status and account balances
- `POST /api/transactions/{transactionId}/retry` — retry a failed transaction if its sequence is currently expected
- `GET /api/health` — basic health endpoint

## Sample request

```json
{
  "transactionId": "TXN-2001",
  "requestId": "REQ-1",
  "accountId": "ACC-201",
  "transactionType": "CREDIT",
  "amount": 100,
  "currency": "INR",
  "sequenceNumber": 1,
  "simulateTransientFailures": 0
}
```

Example response:

```json
{
  "transactionId": "TXN-2001",
  "status": "PROCESSED",
  "attempts": 1,
  "message": null
}
```

## Demonstrating assessment scenarios

1. Submit `TXN-2002`, account `ACC-201`, sequence 2, DEBIT 40. It should be `PENDING` while sequence 1 is missing.
2. Submit `TXN-2001`, same account, sequence 1, CREDIT 100. It should process and unblock sequence 2. Final balance should be 60.
3. Submit `TXN-2002` again with the same business data. The response should be `DUPLICATE`, with no second debit.
4. Submit a negative amount. It should be rejected as `FAILED`.
5. Submit a transaction with `"simulateTransientFailures": 1`. The first attempt fails transiently and the second succeeds.
6. Set `"simulateTransientFailures": 3` to exhaust the three-attempt limit and produce `FAILED`.

For batch testing, send an array of these objects to `POST /api/transactions/batch`.

## Design decisions

### Idempotency
`transactionId` is the business idempotency identifier. An already-seen ID with matching business data returns `DUPLICATE` and does not reapply the effect. Reusing the same ID with different business data is rejected. The canonical transaction's status is not overwritten by a duplicate request.

### Ordering
Sequence numbers are expected to start at 1 independently for each account. A future sequence is held in `PENDING` until the missing sequence arrives. A failed expected sequence blocks later sequences until it is successfully retried. This is a deliberately simple policy for the assessment.

### Retry
Transient failures are deterministically simulated using `simulateTransientFailures`, which is an assessment-only test hook. A maximum of three attempts is made. A real system would use an actual transient exception classification, backoff, jitter, and often a durable retry queue.

### Consistency and concurrency
All service operations are synchronized, so the in-memory implementation serializes calls within one JVM. This is not a distributed lock and does not protect multiple application instances. Balance updates occur only after the simulated failure point and are applied once for a successfully processed canonical transaction.

### Observability and security
The service logs transaction IDs, statuses, sequence gaps, and retry attempts, but does not log amounts or customer-sensitive payloads. `/api/summary` reports transaction counts and balances for this demo.

## Limitations and production hardening

- In-memory state is lost on restart.
- Replace in-memory maps with a database and enforce a unique constraint on the idempotency/business key.
- Update the ledger and idempotency record in one database transaction.
- Use optimistic locking or safe row-level locking for concurrent workers.
- Persist pending records, retry attempts, and failure reasons for recovery.
- Add exponential backoff with jitter, retry classification, and a dead-letter queue or failed-record workflow.
- Define currency/account validation and debit business rules explicitly.
- Add authentication, authorization, rate limiting, metrics, tracing, and structured logs.
- For external side effects, use an outbox/idempotent consumer strategy.

## AI-assisted development note

AI assistance was used to accelerate scaffolding and test-case creation. The implementation should be reviewed manually, executed with `mvn clean test`, and validated against the scenarios above before submission. The candidate should be able to explain the idempotency key, ordering policy, retry behavior, limitations of in-memory synchronization, and production-hardening plan.
