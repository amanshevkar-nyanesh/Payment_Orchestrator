# Architecture

## System Overview

The Payment Orchestration Platform sits between merchants and multiple Payment Service Providers (PSPs). It handles payment creation, intelligent routing, failure recovery, webhook processing, and reconciliation.

```mermaid
flowchart TB
    subgraph Clients
        M[Merchant API Client]
        PSP_WH[PSP Webhooks]
    end

    subgraph Platform["Payment Orchestrator (Spring Boot)"]
        API[REST Controllers]
        AUTH[JWT Security]
        PS[Payment Service]
        IDEM[Idempotency Service]
        ROUTER[PSP Router]
        SM[State Machine]
        WH[Webhook Service]
        RECON[Reconciliation Job]
        CB[Circuit Breaker]
        RL[Rate Limiter]
        LOCK[Distributed Lock]
    end

    subgraph Infrastructure
        PG[(PostgreSQL)]
        KF[Kafka]
    end

    subgraph PSPs
        A[PSP_A]
        B[PSP_B]
        C[PSP_C]
    end

    M --> API
    API --> AUTH
    AUTH --> PS
    PS --> IDEM
    PS --> RL
    PS --> ROUTER
    ROUTER --> CB
    ROUTER --> LOCK
    ROUTER --> A
    ROUTER --> B
    ROUTER --> C
    ROUTER --> SM
    SM --> PG
    IDEM --> PG
    PS --> PG
    PS --> KF
    WH --> PG
    WH --> SM
    PSP_WH --> WH
    RECON --> PG
    RECON --> A
    RECON --> B
    RECON --> C
```

## Payment Creation Flow

```mermaid
sequenceDiagram
    participant Client
    participant API
    participant Idempotency
    participant PaymentService
    participant Router
    participant PSP
    participant DB
    participant Kafka

    Client->>API: POST /payments (Idempotency-Key)
    API->>Idempotency: Check/create idempotency record
    alt Key exists (completed)
        Idempotency-->>API: Cached response
    else New request
        Idempotency->>DB: INSERT (unique constraint)
        PaymentService->>DB: Create payment (CREATED → PENDING)
        PaymentService->>Kafka: PaymentCreated
        PaymentService-->>Client: { paymentId, PENDING }
        PaymentService->>Router: Async route
        Router->>PSP: Attempt PSP_A
        alt PSP_A fails
            Router->>DB: Log attempt
            Router->>PSP: Attempt PSP_B
        end
        Router->>DB: Update status + audit
        Router->>Kafka: PaymentSucceeded/Failed
        Idempotency->>DB: Store response
    end
```

## Idempotency Strategy

Concurrent requests with the same `Idempotency-Key` are handled via:

1. **Database unique constraint** on `(idempotency_key, merchant_id)` — only one insert succeeds
2. **IN_PROGRESS status** — losing threads poll until completion
3. **Stored response** — all retries return identical JSON

This is safe under concurrent load without external cache.

## Webhook Processing Flow

```mermaid
sequenceDiagram
    participant PSP
    participant WebhookController
    participant WebhookService
    participant DB
    participant StateMachine

    PSP->>WebhookController: POST /webhooks/{psp}
    WebhookController->>WebhookService: processWebhook
    WebhookService->>DB: INSERT webhook_event (dedup hash)
    alt Duplicate payload
        WebhookService-->>PSP: Already processed
    else New event
        WebhookService->>DB: Advisory lock (payment)
        WebhookService->>StateMachine: Validate transition
        alt Out-of-order (SUCCESS already, PROCESSING webhook)
            WebhookService-->>PSP: Ignored
        else Valid transition
            WebhookService->>DB: Update payment + audit
            WebhookService-->>PSP: Processed
        end
    end
```

## Event-Driven Architecture (Kafka)

**Topic:** `payment-events`

| Event Type | Trigger |
|------------|---------|
| `PaymentCreated` | Payment record created |
| `PaymentProcessing` | Routed to a PSP |
| `PaymentSucceeded` | PSP success or webhook |
| `PaymentFailed` | All PSPs exhausted or failure webhook |

**Event structure:**

```json
{
  "eventType": "PaymentCreated",
  "paymentId": "PAY001",
  "merchantId": "M123",
  "customerId": "C456",
  "amount": 100,
  "currency": "EUR",
  "status": "PENDING",
  "pspName": null,
  "correlationId": "uuid",
  "timestamp": "2026-06-15T10:00:00Z"
}
```

## State Machine

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> PENDING
    CREATED --> FAILED
    PENDING --> PROCESSING
    PENDING --> FAILED
    PROCESSING --> SUCCESS
    PROCESSING --> FAILED
    PROCESSING --> PENDING
    SUCCESS --> REFUNDED
    FAILED --> [*]
    REFUNDED --> [*]
```

Invalid transitions (e.g., `SUCCESS → PENDING`) are rejected with HTTP 409.

## Reliability Patterns

| Pattern | Implementation |
|---------|----------------|
| Idempotency | DB unique constraint + response caching |
| Failover | Sequential PSP retry with attempt audit |
| Circuit Breaker | Per-PSP failure counter, OPEN after threshold |
| Distributed Lock | PostgreSQL `pg_advisory_xact_lock` |
| Rate Limiting | Per-merchant sliding window in DB |
| Reconciliation | Hourly job queries PSP for stuck payments |

## Security Model

- **JWT Bearer tokens** on all `/payments/**` endpoints
- **MERCHANT** role: access only own merchant's payments
- **ADMIN** role: access all payments
- Webhooks are unauthenticated (production would use HMAC signature verification)

## Observability

- **Correlation IDs** via `X-Correlation-Id` header (auto-generated if absent)
- **Structured logging** with correlation ID in MDC
- **Micrometer metrics**: `payment.created`, `payment.success`, `psp.latency`, `rate_limit.exceeded`
- **Actuator**: `/actuator/health`, `/actuator/prometheus`
