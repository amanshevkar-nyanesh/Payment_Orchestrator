# API Documentation

Base URL: `http://localhost:8080`

All authenticated endpoints require:

```
Authorization: Bearer <jwt-token>
```

Optional on all endpoints:

```
X-Correlation-Id: <uuid>
```

---

## Authentication

### POST /auth/login

Obtain a JWT token.

**Request:**

```json
{
  "username": "merchant_m123",
  "password": "password"
}
```

**Response (200):**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "MERCHANT",
  "merchantId": "M123"
}
```

**Errors:**

| Status | Error | Description |
|--------|-------|-------------|
| 401 | INVALID_CREDENTIALS | Wrong username/password |

---

## Payments

### POST /payments

Create a new payment. Returns immediately with `PENDING` status; routing happens asynchronously.

**Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes | Bearer JWT |
| `Idempotency-Key` | No | Unique key for idempotent creation |
| `Content-Type` | Yes | `application/json` |

**Request:**

```json
{
  "merchantId": "M123",
  "amount": 100,
  "currency": "EUR",
  "customerId": "C456"
}
```

**Response (201):**

```json
{
  "paymentId": "PAY001",
  "status": "PENDING"
}
```

**Validation:**

| Field | Rules |
|-------|-------|
| merchantId | Required, non-blank |
| amount | Required, minimum 0.01 |
| currency | Required, exactly 3 characters |
| customerId | Required, non-blank |

**Authorization:**

- MERCHANT: can only create payments for their own `merchantId`
- ADMIN: can create payments for any merchant

**Errors:**

| Status | Error | Description |
|--------|-------|-------------|
| 403 | ACCESS_DENIED | Merchant accessing wrong merchantId |
| 429 | RATE_LIMIT_EXCEEDED | Exceeded 100 requests/minute |
| 400 | VALIDATION_ERROR | Invalid request body |

**Idempotency:**

Repeating the same request with the same `Idempotency-Key` returns the identical response without creating a duplicate payment. Safe under concurrent requests.

---

### GET /payments/{paymentId}

Get payment details.

**Response (200):**

```json
{
  "paymentId": "PAY001",
  "merchantId": "M123",
  "customerId": "C456",
  "amount": 100,
  "currency": "EUR",
  "status": "SUCCESS",
  "pspName": "PSP_B",
  "createdAt": "2026-06-15T10:00:00Z",
  "updatedAt": "2026-06-15T10:00:05Z"
}
```

**Authorization:**

- MERCHANT: only own payments
- ADMIN: all payments

**Errors:**

| Status | Error | Description |
|--------|-------|-------------|
| 403 | ACCESS_DENIED | Merchant accessing another merchant's payment |
| 400 | BUSINESS_ERROR | Payment not found |

---

### GET /payments

List payments.

**Authorization:**

- MERCHANT: returns only own merchant's payments
- ADMIN: returns all payments

**Response (200):** Array of payment detail objects.

---

## Webhooks

### POST /webhooks/{psp}

Receive asynchronous status updates from PSPs. No authentication required (production would use HMAC signatures).

**Path parameter:** `psp` — PSP identifier (e.g., `psp_a`, `PSP_A`)

**Request:**

```json
{
  "paymentId": "PAY001",
  "status": "SUCCESS"
}
```

**Valid status values:** `SUCCESS`, `FAILED`, `PROCESSING`, `PENDING`

**Response (200):**

```json
{
  "paymentId": "PAY001",
  "status": "SUCCESS",
  "message": "Webhook processed successfully"
}
```

**Duplicate webhook response:**

```json
{
  "paymentId": "PAY001",
  "status": "SUCCESS",
  "message": "Duplicate webhook - already processed"
}
```

**Out-of-order webhook response:**

```json
{
  "paymentId": "PAY001",
  "status": "SUCCESS",
  "message": "Out-of-order webhook ignored"
}
```

---

## Health & Metrics

### GET /actuator/health

Application health status. No authentication required.

### GET /actuator/prometheus

Prometheus metrics scrape endpoint. No authentication required.

### GET /actuator/metrics

Available application metrics.

**Key metrics:**

| Metric | Description |
|--------|-------------|
| `payment.created` | Payments created (by merchant) |
| `payment.success` | Successful payments (by PSP) |
| `payment.failed` | Failed payments |
| `psp.latency` | PSP response time (by PSP) |
| `psp.failure` | PSP failures (by PSP and reason) |
| `rate_limit.exceeded` | Rate limit hits (by merchant) |
| `reconciliation.runs` | Reconciliation job executions |

---

## Error Response Format

All errors follow this structure:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description",
  "correlationId": "uuid"
}
```

| Status | Error Code | Description |
|--------|------------|-------------|
| 400 | VALIDATION_ERROR | Request validation failed |
| 400 | BUSINESS_ERROR | Business rule violation |
| 401 | INVALID_CREDENTIALS | Authentication failed |
| 403 | ACCESS_DENIED | Authorization failed |
| 409 | INVALID_STATE_TRANSITION | Invalid payment state change |
| 429 | RATE_LIMIT_EXCEEDED | Too many requests |
| 500 | INTERNAL_ERROR | Unexpected server error |
