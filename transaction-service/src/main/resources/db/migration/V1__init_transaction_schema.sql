-- ── transaction_db schema ────────────────────────────────────────
-- Managed by Flyway. Never ALTER/DROP; add new migrations instead.

-- Transfer status enum values: PENDING, COMPLETED, FAILED, COMPENSATING
-- Stored as VARCHAR for readability in the DB.

CREATE TABLE transfers (
    id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key  VARCHAR(255)    NOT NULL,
    request_hash     VARCHAR(64)     NOT NULL,   -- SHA-256 of the request body for duplicate detection
    initiated_by     UUID            NOT NULL,   -- auth user id (no FK — cross-service, I8)
    from_account_id  UUID            NOT NULL,
    to_account_id    UUID            NOT NULL,
    amount           NUMERIC(19, 4)  NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)      NOT NULL DEFAULT 'USD',
    status           VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    failure_reason   TEXT,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),

    -- I1: same user + same idempotency key → exactly one transfer
    CONSTRAINT uq_transfers_idempotency UNIQUE (initiated_by, idempotency_key)
);

CREATE INDEX idx_transfers_initiated_by    ON transfers (initiated_by);
CREATE INDEX idx_transfers_from_account_id ON transfers (from_account_id);
CREATE INDEX idx_transfers_to_account_id   ON transfers (to_account_id);
CREATE INDEX idx_transfers_status          ON transfers (status);

-- Outbox events (transactional outbox pattern)
CREATE TABLE outbox_events (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_txn_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

-- Audit log
CREATE TABLE audit_logs (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    actor          VARCHAR(255) NOT NULL,
    action         VARCHAR(100) NOT NULL,
    entity_type    VARCHAR(100) NOT NULL,
    entity_id      UUID         NOT NULL,
    details        JSONB,
    correlation_id VARCHAR(36),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_txn_audit_entity ON audit_logs (entity_type, entity_id);
