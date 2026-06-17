## What Would Change for Production

The current implementation prioritizes correctness, reliability, concurrency safety, and maintainability while remaining appropriately scoped for the assignment. The following enhancements would be recommended before deploying the platform in a large-scale production environment.

### 1. Request Hash Validation for Idempotency

Current implementation stores only the Idempotency-Key.

Enhancement:

* Add a `request_hash` column to `idempotency_keys`.
* Store a SHA-256 hash of the original request payload.
* Validate that subsequent requests using the same Idempotency-Key contain an identical payload.
* Reject requests where the key matches but the payload differs.

Benefits:

* Prevents accidental key reuse.
* Aligns with approaches used by Stripe, Adyen, and other payment providers.

### 2. Stronger Referential Integrity

Current implementation stores payment identifiers in audit tables without explicit foreign key constraints.

Enhancement:

* Add foreign key relationships from:

    * `payment_state_audit`
    * `reconciliation_logs`
    * other audit tables
* Reference the `payments` table directly.

Benefits:

* Improved data integrity.
* Prevention of orphaned records.
* Easier long-term maintenance and reporting.

### 3. Reconciliation Query Optimization

Current implementation uses individual indexes on status and timestamps.

Enhancement:

```sql
CREATE INDEX idx_payments_status_updated
ON payments(status, updated_at);
```

Benefits:

* Faster reconciliation scans.
* Lower database load.
* Better scalability as transaction volume grows.

### 4. Transactional Outbox Pattern

Current implementation publishes events directly to Kafka after business operations complete.

Enhancement:

* Persist business data and outbox events in the same database transaction.
* Use a dedicated background publisher to deliver events to Kafka.
* Mark events as delivered after successful publication.

Benefits:

* Guaranteed event delivery.
* Better resilience during Kafka outages.
* Stronger consistency between database state and published events.

### 5. Enhanced Webhook Deduplication

Current implementation deduplicates webhooks using:

* PSP name
* Payment ID
* Payload hash

Enhancement:

* Store PSP-provided event identifiers when available.
* Deduplicate using provider-generated event IDs.

Benefits:

* More reliable duplicate detection.
* Simpler processing logic.
* Reduced dependency on payload hashing.

### 6. Distributed Cache for Rate Limiting

Current implementation stores rate-limiting state in PostgreSQL.

Enhancement:

* Move rate limiting to Redis using atomic counters and expiration windows.

Benefits:

* Reduced database writes.
* Higher throughput.
* Better horizontal scalability.

### 7. Advanced Observability

Current implementation provides:

* Structured logging
* Correlation IDs
* Health checks
* Metrics

Enhancement:

* Integrate OpenTelemetry for distributed tracing.
* Export traces to Jaeger, Grafana Tempo, or Datadog.

Benefits:

* End-to-end request visibility.
* Easier troubleshooting of asynchronous workflows.
* Better insight into PSP latency and failure patterns.

### 8. Infrastructure and Security Enhancements

Additional production-grade improvements:

* Real PSP SDK integrations with configurable timeout and retry policies.
* Webhook signature verification using HMAC-SHA256.
* OAuth2 / API key management for merchant authentication.
* Secrets management using Vault or cloud-native secret stores.
* Database read replicas for reporting and query-heavy workloads.
* Kubernetes deployment with horizontal pod autoscaling.
* Dead-letter queues for failed routing and webhook processing.
* PCI DSS compliance controls for card-based payment integrations.
* Multi-region disaster recovery and backup strategy.
