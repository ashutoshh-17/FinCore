-- ─────────────────────────────────────────────────────────────────
-- Bankflow — create one logical database per service
-- This script runs once on first Postgres container startup.
-- Each service's Flyway migrations manage schema within its own DB.
-- ─────────────────────────────────────────────────────────────────

CREATE DATABASE auth_db;
CREATE DATABASE account_db;
CREATE DATABASE transaction_db;
CREATE DATABASE ledger_db;
CREATE DATABASE notification_db;
CREATE DATABASE fraud_db;
CREATE DATABASE ai_db;
