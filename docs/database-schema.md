# Database Schema

## Entity Relationship Diagram

```mermaid
erDiagram
    payments ||--o{ payment_attempts : has
    payments ||--o{ payment_state_audit : has
    payments ||--o{ webhook_events : receives
    payments ||--o{ reconciliation_logs : has
    idempotency_keys }o--|| payments : references

    payments {
        bigserial id PK
        varchar payment_id UK
        varchar merchant_id
        varchar customer_id
        numeric amount
        varchar currency
        varchar status
        varchar psp_name
        varchar psp_reference
        bigint version
        timestamptz created_at
        timestamptz updated_at
    }

    payment_attempts {
        bigserial id PK
        varchar payment_id FK
        varchar psp_name
        int attempt_number
        varchar status
        varchar error_code
        text error_message
        bigint latency_ms
        timestamptz created_at
    }

    idempotency_keys {
        bigserial id PK
        varchar idempotency_key
        varchar merchant_id
        varchar payment_id
        text response_body
        varchar status
        timestamptz created_at
        timestamptz updated_at
    }

    webhook_events {
        bigserial id PK
        varchar psp_name
        varchar payment_id
        varchar event_status
        varchar payload_hash
        text raw_payload
        varchar processing_status
        timestamptz processed_at
        timestamptz created_at
    }

    reconciliation_logs {
        bigserial id PK
        varchar payment_id
        varchar previous_status
        varchar new_status
        varchar psp_name
        varchar psp_status
        varchar action
        text details
        timestamptz created_at
    }

    payment_state_audit {
        bigserial id PK
        varchar payment_id
        varchar from_status
        varchar to_status
        varchar reason
        varchar actor
        timestamptz created_at
    }

    circuit_breaker_state {
        varchar psp_name PK
        int failure_count
        varchar state
        timestamptz opened_at
        timestamptz updated_at
    }

    merchant_rate_limits {
        varchar merchant_id PK
        int request_count
        timestamptz window_start
    }
```

## Table Descriptions

### payments

Core payment records. Optimistic locking via `version` column prevents lost updates during concurrent webhook/state changes.

| Column | Purpose |
|--------|---------|
| `payment_id` | Business identifier (e.g., PAY001) |
| `status` | Current state in the state machine |
| `psp_name` | PSP that processed (or is processing) the payment |
| `version` | JPA `@Version` for optimistic concurrency |

### payment_attempts

Complete audit trail of PSP routing attempts. One row per PSP tried, including failures, timeouts, and latency.

### idempotency_keys

Ensures exactly-once payment creation per `(idempotency_key, merchant_id)` pair.

| Status | Meaning |
|--------|---------|
| `IN_PROGRESS` | Payment creation in flight |
| `COMPLETED` | Response cached and safe to replay |
| `FAILED` | Creation failed; retries should not auto-succeed |

**Unique constraint:** `(idempotency_key, merchant_id)` — the primary concurrency guard.

### webhook_events

Stores every webhook received with SHA-256 payload hash for deduplication.

**Unique constraint:** `(psp_name, payment_id, payload_hash)`

### reconciliation_logs

Audit log for the hourly reconciliation job actions.

### payment_state_audit

Immutable log of every state transition with reason and actor (system, webhook, reconciliation).

### circuit_breaker_state

Tracks per-PSP failure counts and OPEN/CLOSED/HALF_OPEN state.

### merchant_rate_limits

Sliding window counter for merchant-level rate limiting.

## Design Decisions

1. **Separate audit tables** rather than JSON blobs — enables efficient querying and compliance reporting.
2. **Advisory locks** instead of Redis — reduces infrastructure dependencies while providing transaction-scoped locking.
3. **Optimistic locking on payments** — `@Version` prevents race conditions between webhooks and async routing.
4. **Idempotency response stored as JSON text** — simple replay without re-executing business logic.
5. **No soft deletes** — payment records are immutable for audit integrity.

## Indexes

| Table | Index | Purpose |
|-------|-------|---------|
| payments | merchant_id | Merchant-scoped queries |
| payments | status | Reconciliation job |
| payment_attempts | payment_id | Attempt history lookup |
| webhook_events | payment_id | Webhook audit lookup |

## Migrations

Managed by Flyway: `src/main/resources/db/migration/V1__init_schema.sql`
