# Bankflow — Coding Standards

Standards for a **simulated** banking platform built as Spring Boot microservices. Stack: **Java 21 + Spring Boot 3** (all services), **PostgreSQL** (one database per service), **Kafka**, **Redis**, **Spring AI / Python FastAPI** (AI layer), optional **Next.js (App Router)** dashboard.

> `Bankflow` is a placeholder project name. Rename it across packages (`com.<owner>.bankflow`), Docker images and docs.

This file is the rulebook. `ARCHITECTURE.md` explains what the system is; this file explains how code in it must be written. AI agents and humans follow both.

---

## 0. Golden Rules (read first)

1. **Money is deterministic.** No AI, ML model or heuristic ever moves, blocks or reverses money automatically. AI advises; rule-based code executes.
2. **Money is `BigDecimal` / `NUMERIC(19,4)`.** Never `double`, `float` or `long` cents mixed with decimals.
3. **Every money-moving request is idempotent.** Same `Idempotency-Key` → same result, never a second transfer.
4. **Ledger rows are append-only.** No `UPDATE` or `DELETE` on financial records, ever. Corrections are new reversing entries.
5. **No service reads another service's database.** Cross-service data flows through REST (sync) or Kafka (async).
6. **No remote calls inside a database transaction.** Commit locally first, then call out (or use the outbox).
7. **Never log or expose sensitive data** (passwords, tokens, full account numbers, PII). Mask before logging.
8. **This is a simulation.** No real bank data, no real payment gateways, no real PII in seed data or tests.

---

## 1. Backend — Java / Spring Boot

### Structure & Organization
- Each service is its own Spring Boot application with its own `pom.xml`/`build.gradle`, Dockerfile and database.
- Inside a service, organize by **feature modules** (e.g., `account/`, `transfer/`), not by a single global `controller/`, `service/`, `repository/` dump.
- Controllers must be **thin** — no business logic, no repository calls. Delegate everything to the service layer.
- Never access `Repository`/DB logic directly from a `@RestController`.
- Constructor-based dependency injection only. Use `@RequiredArgsConstructor` (Lombok) with `final` fields. No `@Autowired` on fields.
- Strict separation: `Entity` (DB-mapped) ≠ `DTO` (API shape) ≠ `Event` (Kafka payload) ≠ `Service` (business logic) ≠ `Controller` (HTTP). Never return an `Entity` from a controller or put one on Kafka.
- Prefer **Java records** for DTOs and events (immutable).
- No circular dependencies between packages or modules.
- Cross-service types are **not shared via a common library of entities**. Shared code (if any) is limited to a small `bankflow-common` library: event envelope, error response shape, correlation-id utilities.

### Naming Conventions
- **PascalCase** for classes: `TransferService`, `AccountController`, `TransferRequestDto`.
- **camelCase** for variables and methods: `findByIdempotencyKey`, `isCompleted`.
- Descriptive names — no vague abbreviations (`txnRepo` → `transferRepository`).
- Standard suffixes: `Controller`, `Service`, `Repository`, `Dto` (or `Request`/`Response` for API shapes), `Event`, `Client` (outbound service calls), `Mapper`.
  - Examples: `TransferController`, `TransferService`, `TransferRepository`, `TransferRequest`, `TransferResponse`, `TransferCompletedEvent`, `AccountClient`, `Transfer` (entity).
- Constants: `UPPER_SNAKE_CASE`. No magic numbers or strings — extract to constants or configuration properties.

### Controller Rules
- Validate input with `@Valid` + DTOs using `jakarta.validation` (`@NotNull`, `@NotBlank`, `@Positive`, `@Digits`, etc.), then call the service.
- Use Spring Security (`@PreAuthorize`, method security, filter chain) for authorization. Never hand-roll permission checks in controller methods.
- Success responses return the DTO directly with the right HTTP status (`201` on create, `200` on read/update, `204` on empty). Errors use the standard error body (Section "Error Handling").
- Paths: `/api/v1/...`, plural nouns, lowercase, kebab-case where multi-word: `/api/v1/transfers`, `/api/v1/accounts/{accountId}/statements`.
- Internal service-to-service endpoints live under `/internal/...` and are **not routed through the public gateway**.
- Money-moving `POST` endpoints **require** an `Idempotency-Key` header; reject with `400` if missing.
- Accept and return money as decimal strings/numbers with explicit `currency` field. Amount must be `> 0` and scale ≤ 4.

### Service Rules
- Single Responsibility — one service class per aggregate or use case group (`TransferService`, not `BankService`).
- Keep methods small and independently testable. Prefer early returns / guard clauses over deeply nested `if` blocks.
- Put `@Transactional` on **service** methods only, never on controllers or repositories. Keep transactions short.
- Never call another service (REST or Kafka publish) from inside a `@Transactional` block. Use the **transactional outbox** for events (see Section 5).
- Throw specific domain exceptions (`InsufficientFundsException`, `AccountNotFoundException`, `DuplicateTransferException`) — never leak raw `RuntimeException` or persistence exceptions upward.
- Concurrency: use optimistic locking (`@Version`) on balance-bearing entities and handle `OptimisticLockingFailureException` with a bounded retry (max 3, with jitter). If pessimistic locking is used, document why in the class Javadoc.

### Outbound Calls (service → service)
- Use **Spring Cloud OpenFeign** wrapped in a dedicated `*Client` interface per target service. Do not use `RestClient` or `RestTemplate`.
- Every outbound call has an **explicit timeout** and is wrapped with **Resilience4j** (retry with backoff for idempotent calls, circuit breaker, bulkhead where relevant).
- Only retry calls that are idempotent. Pass the transfer id / idempotency key downstream so retries are safe.
- Propagate `X-Correlation-Id` on every call.

### Error Handling
- Centralize with one `@RestControllerAdvice` per service (`GlobalExceptionHandler`) in `common/exception/`.
- Use the **RFC 9457 `ProblemDetail`** shape for all errors, with a stable machine-readable `code` (e.g., `INSUFFICIENT_FUNDS`, `IDEMPOTENCY_KEY_REUSED`, `VALIDATION_FAILED`) plus `correlationId`.
- Never expose stack traces, SQL errors or internal exception messages. Return sanitized, user-safe messages.
- Map domain exceptions to statuses deliberately: `404` not found, `409` conflict/optimistic lock/duplicate, `422` business rule violation, `400` malformed input, `401/403` auth, `503` downstream unavailable.
- Always handle failure paths explicitly. Unknown exceptions log with full context server-side and return a generic `500` with the correlation id only.

### Security
- Passwords hashed with **BCrypt** (`BCryptPasswordEncoder`). Never plaintext, never custom hashing.
- JWTs are validated at the **gateway and again in each service** (defense in depth). Services must not trust headers like `X-User-Id` unless set by the gateway on an internal network.
- Roles: at minimum `CUSTOMER`, `ADMIN`. Enforce ownership checks in the service layer (a customer may only act on their own accounts).
- Treat all incoming data as untrusted: validate every DTO field and every path/query param.
- **Never log**: passwords, JWTs, refresh tokens, full account numbers, full card-like numbers, emails/phones in bulk. Log masked values (`****1234`).
- All secrets (DB credentials, `JWT_SECRET`, Kafka/Redis credentials, LLM API keys) live in **environment variables**. Never hardcoded, never committed. `.env.example` lists names only.
- Rate limit sensitive endpoints (login, transfer) at the gateway.

### Testing
- Unit test all service methods (JUnit 5 + Mockito). Mock repositories and outbound clients.
- Cover failure paths and edge cases: insufficient funds, duplicate idempotency key, key reused with different payload, optimistic lock conflict, downstream timeout, compensation path.
- Tests are isolated and repeatable — no shared mutable state between tests.
- **Integration tests use Testcontainers from day one** (PostgreSQL, Kafka, Redis). Do not mock the database in repository tests.
- Mandatory system-level tests:
  - **Concurrency test:** N parallel transfers between the same accounts → total money in the system unchanged, no negative balances.
  - **Idempotency test:** the same request sent repeatedly (and in parallel) creates exactly one transfer.
  - **Ledger invariant test:** for every transfer, sum of debits equals sum of credits.
  - **Saga failure test:** force a failure at each step and assert compensation leaves consistent state.
- Naming: `methodName_condition_expectedResult` (e.g., `createTransfer_duplicateKey_returnsOriginalResult`).

---

## 2. Database Naming & Migration Conventions

- `snake_case` for all table and column names — never camelCase in the DB.
- Plural table names: `users`, `accounts`, `transfers`, `ledger_entries`.
- Primary key column is always `id`, type **`UUID`** (generated in the application layer or `gen_random_uuid()`).
- Foreign keys follow `table_name_id`: `account_id`, `transfer_id`. Cross-service references (IDs owned by another service) are plain columns with **no FK constraint**, and are documented in the table comment.
- **Money columns:** `NUMERIC(19,4) NOT NULL`, with a companion `currency CHAR(3) NOT NULL` where the currency can vary. Add `CHECK (amount > 0)` on transfer/ledger amounts; add `CHECK (balance >= 0)` on account balances unless overdraft is explicitly designed.
- Status/direction columns use `VARCHAR` + a `CHECK` constraint or a Postgres enum; the Java enum stays in sync.
- **Name every constraint explicitly from the first migration**: `pk_`, `fk_`, `uq_`, `ck_`, `idx_` prefixes (e.g., `uq_transfers_initiated_by_idempotency_key`, `ck_ledger_entries_amount_positive`).
- **Flyway migrations from day one.** `spring.jpa.hibernate.ddl-auto=validate`. Hibernate never alters the schema. Migration files: `V<number>__<description>.sql`, never edited after merge.
- Application DB role has DML grants only (`SELECT/INSERT/UPDATE/DELETE`). Migrations run under a separate migration role.
- `spring.jpa.open-in-view=false`. Lazy loading must be explicit (`JOIN FETCH` or DTO projections), never implicit in controllers.
- Index every foreign key and every column used in frequent lookups (`idx_ledger_entries_account_id_created_at`).

### Ledger-specific rules
- `ledger_entries` is **append-only**. Enforce in the database too: `REVOKE UPDATE, DELETE` from the application role (or a trigger that raises an exception).
- Each transfer writes **two or more** entries (debit and credit) in one local transaction; the sum of signed amounts per `transfer_id` is zero.
- Account `balance` in the Account service is a **cached projection**. The ledger is the source of truth. A reconciliation job compares them and reports drift; it never silently "fixes" balances.

---

## 3. Audit Column Standards

- `created_at`: `TIMESTAMPTZ NOT NULL DEFAULT now()`. **Always `TIMESTAMPTZ`** in this project (financial timestamps must be timezone-safe).
- `updated_at`: `TIMESTAMPTZ NOT NULL`, updated on every change via `@PreUpdate` or `@LastModifiedDate` (Spring Data auditing). Omit only on append-only tables (`ledger_entries`, `audit_logs`, `processed_events`).
- `created_by` / `updated_by` (user id or service name) on tables where actor attribution matters (`transfers`, `accounts`, admin actions).
- Never modify `created_at` after insert. Client requests must never set audit fields — always server-assigned.
- **Audit log:** privileged and financial actions (account status changes, admin overrides, transfer state transitions) write to an append-only `audit_logs` table (who, what, when, before/after summary, correlation id).
- **No hard deletes of financial data.** Accounts are closed via `status = CLOSED`. Non-financial data may use soft delete via nullable `deleted_at TIMESTAMPTZ` (not a boolean flag).

---

## 4. Repository & Folder Structure

### Monorepo layout

```
bankflow/
├── ARCHITECTURE.md
├── CODING_STANDARDS.md
├── docker-compose.yml
├── .env.example
├── .github/workflows/          # CI: build, test, lint per service
├── bankflow-common/            # event envelope, error shape, correlation-id filter
├── gateway/
├── auth-service/
├── account-service/
├── transaction-service/
├── ledger-service/
├── notification-service/
├── fraud-service/              # Java (rules) or Python FastAPI (ML) — see ARCHITECTURE.md
├── ai-assistant-service/
├── frontend/                   # optional Next.js dashboard
├── load-tests/                 # k6 scripts
└── docs/                       # diagrams, ADRs
```

### Inside each Java service

```
src/main/java/com/<owner>/bankflow/<service>/
├── config/            # SecurityFilterChain, Kafka, Resilience4j, OpenAPI beans
├── security/          # JWT filter/utilities (where applicable)
├── common/
│   ├── exception/     # domain exceptions + GlobalExceptionHandler
│   └── util/          # masking, correlation id
├── outbox/            # outbox entity, repository, relay (services that publish events)
├── messaging/         # Kafka producers/consumers, event mappers
├── client/            # outbound REST clients to other services
└── modules/
    ├── transfer/
    │   ├── Transfer.java              (entity)
    │   ├── TransferRepository.java
    │   ├── TransferService.java
    │   ├── TransferController.java
    │   ├── TransferStatus.java
    │   └── dto/
    └── ...
src/main/resources/
├── application.yml
└── db/migration/      # Flyway: V1__init.sql, ...
```

---

## 5. Events & Messaging (Kafka)

- **Topic naming:** `<domain>.<event-or-stream>` in lowercase with dots: `transaction.events`, `ledger.posted`, `fraud.alerts`. Dead-letter topics: `<topic>.dlq`.
- **Event envelope (required on every message):**
  ```json
  {
    "eventId": "uuid",
    "eventType": "TransferCompleted",
    "eventVersion": 1,
    "occurredAt": "2025-01-01T10:00:00Z",
    "correlationId": "uuid",
    "producer": "transaction-service",
    "payload": { }
  }
  ```
- **Partition key = `accountId`** (or `transferId` where per-transfer ordering is needed) so related events stay ordered.
- **Transactional outbox:** services that must publish events write the event to an `outbox_events` table in the **same DB transaction** as the state change; a relay publishes to Kafka afterwards. Never "save to DB then publish" as two independent steps.
- **Idempotent consumers:** every consumer records processed `eventId`s (`processed_events` table) and skips duplicates. Assume at-least-once delivery.
- **Retries and DLQ:** consumers use bounded retries with backoff, then route to `<topic>.dlq`. A poison message must never block a partition forever.
- Events are **versioned and backward compatible**: add fields, never rename or remove. Breaking changes require a new `eventVersion`.
- Events carry **minimal, non-sensitive data**: IDs, amounts, statuses. No passwords, tokens or full account numbers.

---

## 6. API Standards

- Base path `/api/v1`. Version in the path; breaking changes go to `/api/v2`.
- JSON with `camelCase` field names. ISO-8601 UTC timestamps. UUID ids.
- **Pagination** on all list endpoints: `page`, `size` (max 100), `sort`; response includes `content`, `page`, `size`, `totalElements`.
- Headers:
  - `Authorization: Bearer <jwt>` on all non-public endpoints
  - `Idempotency-Key: <uuid>` on money-moving `POST`s
  - `X-Correlation-Id` accepted and echoed; generated at the gateway if absent
- Idempotency behavior: same key + same payload → return the original response; same key + **different** payload → `409` with code `IDEMPOTENCY_KEY_REUSED`.
- Every service exposes OpenAPI docs (springdoc) and Actuator health/metrics endpoints (health public inside the network only).
- Standard error body (ProblemDetail):
  ```json
  {
    "type": "https://bankflow.dev/errors/insufficient-funds",
    "title": "Insufficient funds",
    "status": 422,
    "code": "INSUFFICIENT_FUNDS",
    "detail": "Source account balance is too low for this transfer.",
    "correlationId": "…"
  }
  ```

---

## 7. AI Layer Standards

The AI layer (fraud scorer, assistant) is **advisory only**.

- AI services **consume events and read data**. They never call money-moving endpoints and hold no credentials capable of doing so.
- **Fraud scoring is asynchronous.** It must never block or slow a transfer. Synchronous protections (daily limits, velocity caps) are plain rules in the Transaction service.
- A fraud decision produces an **alert for admin review**. It does not auto-reverse, freeze or block. Any block/freeze action is a separate, explicit, audited admin operation.
- **LLM assistant uses tool calling against read-only endpoints.** The model never computes balances or totals itself; tools return the numbers, the model only phrases them. Tools are scoped to the authenticated user's data.
- **Data minimization for models:** mask account numbers, strip names/emails, send only fields needed for the task. No secrets or tokens in prompts.
- Log every AI interaction (redacted prompt, tool calls, response, model name/version, latency) for auditability.
- Every AI call has a timeout and a **deterministic fallback** (rules-only fraud score; a plain "assistant unavailable" message).
- Version ML models and record `model_version` with every score. Keep training data synthetic and the training script reproducible in-repo.
- Evaluate and record metrics: categorization accuracy, fraud precision/recall on a held-out synthetic set, added latency.
- Prompts live in versioned files (`resources/prompts/`), not inline strings.

---

## 8. Observability & Operations

- **Structured JSON logging** in all services, each line carrying `correlationId`, `service`, and (where safe) `userId`, `transferId`.
- Actuator + Micrometer → Prometheus metrics; Grafana dashboards for request rate, latency, error rate, Kafka lag, saga failures, DLQ depth.
- Health checks (`/actuator/health`) for liveness/readiness; Docker healthchecks in Compose.
- Dockerfiles: multi-stage builds, non-root user, no secrets baked into images. `docker compose up` must bring the full system up locally.
- Configuration via `application.yml` + environment variables; profiles: `local`, `test`, `prod`.
- CI (GitHub Actions) runs on every PR: build, unit tests, Testcontainers integration tests, static analysis. PRs cannot merge on a red pipeline.

---

## 9. Frontend — Next.js (App Router) *(optional dashboard)*

A minimal dashboard is optional; Swagger UI and Postman collections are sufficient for the core project. If built:

### Naming Conventions
- **PascalCase** for components/files: `TransferForm.tsx`, `AccountCard.tsx`.
- **camelCase** for utilities and hooks (`useAccounts.ts`, `formatCurrency()`).
- Booleans: `isLoading`, `hasError`, `canTransfer`.
- Constants: `UPPER_SNAKE_CASE`. Types/interfaces: PascalCase (`Account`, `TransferResponse`).
- Directories: lowercase-with-dashes.

### Rules
- Default to **Server Components**; `"use client"` only for state, effects, browser APIs or real interactivity.
- Always handle **loading and error states** explicitly. Wrap API calls in try/catch, check `response.ok`, never swallow errors.
- No `any`, no `// @ts-ignore`. Type all props and API responses; keep `types/api.ts` mirroring backend DTOs.
- Centralize env access in `config/env.ts`; client-exposed vars prefixed `NEXT_PUBLIC_`. Never expose secrets client-side.
- **Money display:** format with `Intl.NumberFormat`; treat amounts as strings/decimals from the API, never do float arithmetic on money in the client.
- Always send an `Idempotency-Key` (generated once per form submission) when creating transfers; disable the submit button while a request is in flight.
- Never trust client-side validation or role checks; the backend re-validates everything.
- Import order: external packages → `@/` absolute imports → relative imports. Components under ~300 lines; extract logic into hooks.

---

## 10. Git Standards

Branching model: `main` (protected, releases) ← `dev` (protected, integration) ← `feature/*` branches. PR-only merges in both directions; at least one review (or self-review checklist for solo work).

**Branch naming:**
```
feature/idempotent-transfers
feature/ledger-service
fix/optimistic-lock-retry
refactor/outbox-relay-cleanup
```

**Commit messages** (Conventional Commits):
```
feat: add idempotency key handling to transfer endpoint
fix: prevent double debit on retried saga step
refactor: extract compensation logic into TransferSaga
test: add concurrent transfer consistency test
docs: add architecture overview
```

### Third-Party Library Usage
Before adding any dependency (Maven or npm):
- Is the problem complex/standardized enough to justify it (vs. <30–40 lines in-house)?
- Actively maintained (release/commit within the last 6–12 months)?
- Reasonable adoption and community trust?
- Security check: `mvn dependency-check` / OWASP Dependency-Check for Java, `npm audit` (and Snyk Advisor) for npm.
- The PR adding it states: reason, security/health check results, alternatives considered.

---

## 11. Definition of Done (per feature)

- [ ] Code follows this document; no business logic in controllers
- [ ] Input validated; errors mapped to `ProblemDetail` with stable codes
- [ ] Flyway migration added (if schema changed); constraints named
- [ ] Unit tests + Testcontainers integration test; failure paths covered
- [ ] Money-moving change: idempotency and concurrency behavior tested
- [ ] No sensitive data in logs or events
- [ ] OpenAPI updated; `ARCHITECTURE.md` updated if design changed
- [ ] CI green

---

## Notes / Deviations (document here as decisions are made)

- Balances are a **cached projection** of the ledger, reconciled periodically; the ledger is the source of truth.
- Transfers use an **orchestrated saga** in the Transaction service (not choreography).
- Only in-network `/internal/**` endpoints are callable service-to-service; the gateway never routes them.
- *(Add ADRs for anything that deviates from this file. Keep them short: context, decision, consequences.)*
