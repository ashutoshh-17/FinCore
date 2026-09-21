-- =============================================================
-- V1__init_auth_schema.sql
-- Auth service initial schema.
-- All constraints are explicitly named (pk_, uq_, fk_, ck_, idx_).
-- Never edit this file after it has been applied to any environment.
-- New changes go in a new migration file.
-- =============================================================

-- ─── users ───────────────────────────────────────────────────────
CREATE TABLE users (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    kyc_status    VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_users               PRIMARY KEY (id),
    CONSTRAINT uq_users_email         UNIQUE      (email),
    CONSTRAINT ck_users_status        CHECK       (status     IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_users_kyc_status    CHECK       (kyc_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

-- ─── roles ───────────────────────────────────────────────────────
CREATE TABLE roles (
    id   UUID        NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,

    CONSTRAINT pk_roles      PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE      (name),
    CONSTRAINT ck_roles_name CHECK       (name IN ('CUSTOMER', 'ADMIN'))
);

-- ─── user_roles (join) ────────────────────────────────────────────
CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,

    CONSTRAINT pk_user_roles         PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user_id FOREIGN KEY (user_id) REFERENCES users  (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role_id FOREIGN KEY (role_id) REFERENCES roles  (id) ON DELETE RESTRICT
);

-- ─── refresh_tokens ──────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_refresh_tokens             PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token_hash  UNIQUE      (token_hash),
    CONSTRAINT fk_refresh_tokens_user_id     FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- ─── outbox_events ────────────────────────────────────────────────
-- Transactional outbox: events are written here in the same DB tx
-- as the state change; a relay publishes them to Kafka asynchronously.
CREATE TABLE outbox_events (
    id             UUID        NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID        NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB       NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

-- ─── Indexes ─────────────────────────────────────────────────────
CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
CREATE INDEX idx_outbox_events_published   ON outbox_events  (published_at) WHERE published_at IS NULL;

-- ─── Seed default roles ───────────────────────────────────────────
INSERT INTO roles (name) VALUES ('CUSTOMER'), ('ADMIN');
