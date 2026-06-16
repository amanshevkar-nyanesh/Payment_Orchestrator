# Assumptions and Trade-offs

## Assumptions

### Business

1. **Payment amounts** are in major currency units (e.g., 100 EUR, not cents).
2. **Merchant IDs** are pre-provisioned strings (M123, M456); no merchant onboarding API.
3. **PSPs are simulated** — no real external PSP integration; behavior is controlled via configurable probability rates.
4. **Webhooks are trusted** in this demo — production would verify HMAC signatures from each PSP.
5. **Single currency per payment** — no multi-currency conversion.
6. **Refunds** are modeled in the state machine but not exposed via API (webhook-driven only).

### Technical

1. **PostgreSQL is the single source of truth** — no Redis or separate cache layer.
2. **Kafka is fire-and-forget** for domain events — no guaranteed delivery to consumers in this scope.
3. **JWT tokens are stateless** — no token revocation mechanism.
4. **User accounts are in-memory** — production would use a user store or OAuth2 provider.
5. **Payment ID format** (PAY001) uses an in-memory sequence — production would use a DB sequence or UUID.
6. **Async routing** runs in a thread pool — not a separate worker service or message queue.

## Trade-offs

### Idempotency: Database vs. Redis

**Chosen:** PostgreSQL unique constraint + response caching.

| Pros | Cons |
|------|------|
| No additional infrastructure | Slightly higher DB load under extreme concurrency |
| ACID guarantees | Polling wait for concurrent requests (100ms × 50 max) |
| Survives restarts | |

**Alternative considered:** Redis SETNX with TTL — faster but adds dependency and lacks durability without Redis persistence.

### Distributed Locking: Advisory Locks vs. Redis/Redlock

**Chosen:** PostgreSQL `pg_advisory_xact_lock`.

| Pros | Cons |
|------|------|
| Transaction-scoped (auto-release) | Tied to DB connection pool |
| No extra infrastructure | Not suitable for cross-service locking |
| Works for webhooks, routing, reconciliation | |

### Async Processing: @Async vs. Message Queue

**Chosen:** Spring `@Async` thread pool.

| Pros | Cons |
|------|------|
| Simple, no extra consumer service | Lost tasks if JVM crashes mid-routing |
| Fast to implement | No retry/DLQ for routing failures |
| Sufficient for demo scope | |

**Production recommendation:** Publish to Kafka/SQS and process with dedicated workers.

### PSP Routing: Static Config vs. Dynamic Rules Engine

**Chosen:** YAML-based merchant → PSP list mapping.

| Pros | Cons |
|------|------|
| Easy to configure and test | No country/currency/amount-based routing (bonus feature) |
| Supports merchant preferences | Rules require restart to change |

**Bonus extension:** Add a rules table with conditions (currency, amount range, country) evaluated at runtime.

### Circuit Breaker: DB-backed vs. Resilience4j In-Memory

**Chosen:** DB-backed circuit breaker state.

| Pros | Cons |
|------|------|
| Shared across instances | DB write on every failure |
| Survives restarts | Higher latency than in-memory |
| Visible in admin queries | |

### Rate Limiting: DB vs. Token Bucket (Redis)

**Chosen:** DB-backed sliding window per merchant.

| Pros | Cons |
|------|------|
| Consistent across instances | DB lock contention at high volume |
| Simple implementation | Not as precise as Redis token bucket |

**Production recommendation:** Redis sliding window or API gateway rate limiting.

### Webhook Security: Open vs. Signed

**Chosen:** Open endpoint (no auth).

| Pros | Cons |
|------|------|
| Easy PSP integration testing | Vulnerable to spoofed webhooks |
| Dedup + state machine still protect integrity | |

**Production requirement:** HMAC-SHA256 signature verification per PSP.

### Event Publishing: Sync vs. Outbox Pattern

**Chosen:** Direct Kafka publish after DB commit.

| Pros | Cons |
|------|------|
| Simple | Event loss if Kafka is down after DB commit |
| Low latency | No guaranteed exactly-once delivery |

**Production recommendation:** Transactional outbox pattern with a relay process.

## Scalability Considerations

| Component | Current | Scale Path |
|-----------|---------|------------|
| API | Single instance | Horizontal scaling behind load balancer |
| Idempotency | DB constraint | Sharded by merchant_id if needed |
| Routing | Async thread pool | Dedicated worker fleet + Kafka |
| Reconciliation | Single scheduled job | Leader election or partitioned by payment range |
| Events | Kafka 3 partitions | Increase partitions by merchant hash |

## What Would Change for Production

1. Real PSP SDK integrations with timeout/retry configuration
2. Webhook signature verification
3. Transactional outbox for events
4. OAuth2 / API key management for merchants
5. Secrets management (Vault) for JWT keys
6. OpenTelemetry distributed tracing
7. Database read replicas for query endpoints
8. Kubernetes deployment with HPA
9. Dead letter queues for failed routing/webhook processing
10. PCI DSS compliance considerations for card data (not in scope — no card numbers stored)
