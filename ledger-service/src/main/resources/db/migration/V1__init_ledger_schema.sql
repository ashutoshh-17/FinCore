-- ============================================================
-- Ledger Service — V1 initial schema
-- Append-only by policy: no UPDATE or DELETE ever runs on
-- ledger_entries (enforced by invariants I2, I3).
-- ============================================================

CREATE TABLE IF NOT EXISTS ledger_entries (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    transfer_id      UUID        NOT NULL,
    account_id       UUID        NOT NULL,
    -- Positive = credit to this account, negative = debit from this account.
    -- Every transfer produces exactly two rows that sum to zero.
    amount           NUMERIC(19, 4) NOT NULL,
    entry_type       VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    currency         VARCHAR(3)  NOT NULL,
    description      TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Fast lookups by account (statement) and by transfer (reconciliation)
CREATE INDEX IF NOT EXISTS idx_ledger_account_id    ON ledger_entries (account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ledger_transfer_id   ON ledger_entries (transfer_id);
