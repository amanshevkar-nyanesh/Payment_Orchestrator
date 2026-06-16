CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      VARCHAR(64)  NOT NULL UNIQUE,
    merchant_id     VARCHAR(64)  NOT NULL,
    customer_id     VARCHAR(64)  NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    psp_name        VARCHAR(32),
    psp_reference   VARCHAR(128),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_merchant_id ON payments (merchant_id);
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_created_at ON payments (created_at);

CREATE TABLE payment_attempts (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      VARCHAR(64)  NOT NULL REFERENCES payments(payment_id),
    psp_name        VARCHAR(32)  NOT NULL,
    attempt_number  INT          NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    error_code      VARCHAR(64),
    error_message   TEXT,
    latency_ms      BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_attempts_payment_id ON payment_attempts (payment_id);

CREATE TABLE idempotency_keys (
    id              BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    merchant_id     VARCHAR(64)  NOT NULL,
    payment_id      VARCHAR(64),
    response_body   TEXT,
    status          VARCHAR(32)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_idempotency_key_merchant UNIQUE (idempotency_key, merchant_id)
);

CREATE TABLE webhook_events (
    id              BIGSERIAL PRIMARY KEY,
    psp_name        VARCHAR(32)  NOT NULL,
    payment_id      VARCHAR(64)  NOT NULL,
    event_status    VARCHAR(32)  NOT NULL,
    payload_hash    VARCHAR(64)  NOT NULL,
    raw_payload     TEXT         NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    processed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_webhook_event UNIQUE (psp_name, payment_id, payload_hash)
);

CREATE INDEX idx_webhook_events_payment_id ON webhook_events (payment_id);

CREATE TABLE reconciliation_logs (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      VARCHAR(64)  NOT NULL,
    previous_status VARCHAR(32)  NOT NULL,
    new_status      VARCHAR(32)  NOT NULL,
    psp_name        VARCHAR(32),
    psp_status      VARCHAR(32),
    action          VARCHAR(64)  NOT NULL,
    details         TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reconciliation_logs_payment_id ON reconciliation_logs (payment_id);

CREATE TABLE payment_state_audit (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      VARCHAR(64)  NOT NULL,
    from_status     VARCHAR(32),
    to_status       VARCHAR(32)  NOT NULL,
    reason          VARCHAR(256),
    actor           VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_state_audit_payment_id ON payment_state_audit (payment_id);

CREATE TABLE circuit_breaker_state (
    psp_name        VARCHAR(32) PRIMARY KEY,
    failure_count   INT          NOT NULL DEFAULT 0,
    state           VARCHAR(16)  NOT NULL DEFAULT 'CLOSED',
    opened_at       TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE merchant_rate_limits (
    merchant_id     VARCHAR(64) PRIMARY KEY,
    request_count   INT          NOT NULL DEFAULT 0,
    window_start    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
