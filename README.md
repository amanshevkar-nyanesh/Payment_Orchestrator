# Payment Orchestration Platform

A production-oriented Spring Boot payment orchestrator that routes merchant payments across multiple PSPs with idempotency, failover, webhooks, reconciliation, JWT security, and Kafka event publishing.

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker & Docker Compose

## Quick Start

### 1. Start infrastructure

```bash
docker compose up -d
```

This starts PostgreSQL (port 5432) and Kafka (port 9092).

### 2. Run the application

```bash
mvn spring-boot:run
```

The API is available at `http://localhost:8080`.

### 3. Authenticate

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"merchant_m123","password":"password"}'
```

**Demo users:**

| Username       | Password  | Role     | Merchant |
|----------------|-----------|----------|----------|
| merchant_m123  | password  | MERCHANT | M123     |
| merchant_m456  | password  | MERCHANT | M456     |
| admin          | admin123  | ADMIN    | —        |

### 4. Create a payment

```bash
TOKEN="<jwt-from-login>"

curl -X POST http://localhost:8080/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: abc123" \
  -d '{
    "merchantId": "M123",
    "amount": 100,
    "currency": "EUR",
    "customerId": "C456"
  }'
```

**Response:**

```json
{
  "paymentId": "PAY001",
  "status": "PENDING"
}
```

### 5. Send a webhook

```bash
curl -X POST http://localhost:8080/webhooks/psp_a \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"PAY001","status":"SUCCESS"}'
```

## Architecture

See [docs/architecture.md](docs/architecture.md) for the full architecture diagram and event flows.

```
Merchant → API Gateway → Payment Service → PSP Router → PSP_A / PSP_B / PSP_C
                              ↓
                         PostgreSQL
                              ↓
                           Kafka (domain events)
                              ↓
                    Webhooks / Reconciliation Job
```

## Features Implemented

| Part | Feature | Implementation |
|------|---------|----------------|
| 1 | Payment Creation API | `POST /payments` |
| 2 | Idempotency | DB unique constraint + pessimistic locking + polling |
| 3 | PSP Routing | Configurable merchant PSP preferences (YAML) |
| 4 | Retry & Failover | Sequential PSP attempts with audit trail |
| 5 | State Machine | Enforced valid transitions |
| 6 | Webhooks | Dedup, out-of-order handling, idempotent processing |
| 7 | Reconciliation | Hourly scheduled job for stuck PROCESSING payments |
| 8 | Events | Kafka: PaymentCreated, Processing, Succeeded, Failed |
| 9 | Security | JWT + RBAC (MERCHANT / ADMIN) |
| 10 | Observability | Correlation IDs, Micrometer metrics, Actuator health |

**Bonus:**

- Circuit breaker per PSP (failure threshold + cooldown)
- Merchant rate limiting (100 req/min)
- Distributed locking via PostgreSQL advisory locks

## API Documentation

See [docs/api.md](docs/api.md).

## Database Schema

See [docs/database-schema.md](docs/database-schema.md).

## Assumptions & Trade-offs

See [docs/assumptions.md](docs/assumptions.md).

## Testing

```bash
mvn test
```

Integration tests use Testcontainers (PostgreSQL + Kafka).

## Observability Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health check |
| `/actuator/metrics` | Application metrics |
| `/actuator/prometheus` | Prometheus scrape endpoint |

All requests support `X-Correlation-Id` header for distributed tracing.

## Configuration

Key settings in `application.yml`:

- `app.psp.routing.merchants` — merchant-specific PSP order
- `app.psp.simulation.*` — PSP failure simulation rates
- `app.circuit-breaker.*` — circuit breaker thresholds
- `app.rate-limit.*` — merchant rate limits
- `app.reconciliation.cron` — reconciliation schedule

## Project Structure

```
src/main/java/com/payment/orchestrator/
├── config/          # Security, Kafka, PSP routing config
├── controller/      # REST endpoints
├── domain/          # JPA entities
├── dto/             # Request/response objects
├── event/           # Kafka domain events
├── exception/       # Error handling
├── job/             # Reconciliation scheduler
├── observability/   # Correlation ID filter
├── psp/             # Simulated PSP clients
├── repository/      # Data access
├── security/        # JWT authentication
└── service/         # Business logic
```
