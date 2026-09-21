-- ── account_db schema ─────────────────────────────────────────────
-- Managed by Flyway. Never ALTER/DROP; add new migrations instead.

-- Accounts (the core entity)
CREATE TABLE accounts (
    id             UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(20)              NOT NULL UNIQUE,
    owner_id       UUID                     NOT NULL,
    type           VARCHAR(20)              NOT NULL,           -- SAVINGS | CHECKING | SYSTEM
    currency       VARCHAR(3)               NOT NULL DEFAULT 'USD',
    balance        NUMERIC(19, 4)           NOT NULL DEFAULT 0 CHECK (balance >= 0),
    status         VARCHAR(20)              NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | FROZEN | CLOSED
    version        BIGINT                   NOT NULL DEFAULT 0,         -- optimistic locking
    created_at     TIMESTAMPTZ              NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ              NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_owner_id ON accounts (owner_id);
CREATE INDEX idx_accounts_status   ON accounts (status);

-- Outbox events (transactional outbox pattern)
CREATE TABLE outbox_events (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

-- Audit log (immutable record of every mutating action)
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

CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
