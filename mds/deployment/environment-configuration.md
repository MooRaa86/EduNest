# Environment Configuration

> **Authoritative Infrastructure Reference:** See [`ops/environments.md`](../../ops/environments.md) for the ratified staging/production topology (Railway deployment at `https://backend.edunest.dpdns.org/`, Supabase PostgreSQL & S3 storage, and `production` branch CD pipeline) and [`.env.example`](../../.env.example) for the environment template.
>
> **Deployment procedures (P6-09 / EB-80):** [`ops/runbooks/deploy-prod.md`](../../ops/runbooks/deploy-prod.md) (active production path: release PR → `production` → Railway Auto Deploy → verification → rollback) and [`ops/runbooks/deploy-staging.md`](../../ops/runbooks/deploy-staging.md) (staging paths + activation gates — staging is documented as **not provisioned** there; do not treat it as a live environment).

Complete configuration surface (verified against `application.properties`, `application-prod.properties`, `Dockerfile`, and the code that reads env values).

## 1. Required environment variables

| Variable | Used by | Notes |
|---|---|---|
| `PORT` | `server.port` | HTTP container port (`${PORT:8080}`); dynamically injected by Railway/Render |
| `DATABASE_URL` | `spring.datasource.url` | Full JDBC URL expected by the naming convention (e.g. `jdbc:postgresql://<host>:5432/<db>?sslmode=require` — see §7) |
| `DATABASE_NAME` | `spring.datasource.username` | (name holds the *user*) |
| `DATABASE_PASSWORD` | `spring.datasource.password` | – |
| `MAIL_HOST`, `MAIL_PORT` | `spring.mail.*` | SMTP |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | `spring.mail.*` | SMTP auth creds |
| `JWT_SECRET` (**now required**) | `config/security/JwtSecretProvider` (single owner; constructed at startup) | **Implemented 2026-10-28 (P1-01/SEL-01):** the hardcoded fallback was deleted. Boot **fails fast** with `IllegalStateException` if `JWT_SECRET` is missing/blank, shorter than 32 bytes (HS256/256-bit), or equal to the retired default (matched by SHA-256 digest). The key is built once and shared; `LoginServiceI.getJwtRole` now delegates to the shared parser (rotation-safe). Minimum 32 bytes enforced |
| `SPRING_PROFILES_ACTIVE` | profile selection | Defaulted to `prod` in the Dockerfile; absence locally runs the **wide-open `!prod` chain** (SEL-02) |
| `ZOOM_ENABLED` (=false until provisioning) | `app.zoom.enabled` ([ADD-05]/EB-85 groundwork) | Integration master switch; while `false` the placeholder Zoom client is wired and no external call is made |
| `ZOOM_ACCOUNT_ID` | `app.zoom.account-id` (Zoom S2S OAuth) | Marketplace Server-to-Server OAuth app → Account ID; credentials pending (Omar checklist on `ZoomProperties`) |
| `ZOOM_CLIENT_ID` | `app.zoom.client-id` (Zoom S2S OAuth) | Scopes: `meeting:write:admin` + `meeting:read:admin`; no redirect URI needed |
| `ZOOM_CLIENT_SECRET` | `app.zoom.client-secret` (Zoom S2S OAuth) | Env store only, never git; rotation per P6-03 |
| `ZOOM_BASE_URL` (optional) | `app.zoom.base-url` | Defaults to `https://api.zoom.us/v2` |

## 2. Properties matrix (dev/default vs prod profile)

| Setting | default (`application.properties`) | `prod` (`application-prod.properties`) |
|---|---|---|
| `server.port` | `${PORT:8080}` | `${PORT:8080}` |
| DDL | `ddl-auto=validate` | `ddl-auto=validate` (same) — Flyway owns schema; Hibernate only validates (`spring.flyway.enabled=false`, operator migrations via `-Pdb-migrate`; corrected 2026-11-12 from a stale `update` row against `application.properties`/`application-prod.properties`) |
| SQL logging | `show-sql=true`, `format_sql=true` | same (**prod-noisy**, DEP-04) |
| Jackson unknown fields | default (ignored) | `fail-on-unknown-properties=true` (contract drift — API-05) |
| Static resources | – | `static-locations = classpath:/static/, file:uploads/` |
| Multipart caps | 10MB file / 10MB request | same |
| Mail | same `${MAIL_*}` set | same |
| Forward headers | `framework` | `framework` |
| springdoc UI | `doc-expansion=none`, alpha tag sort | inherits (no override block) |

Additional prod-profile-only hardcoding: none for JWT (see §1).

## 3. Filesystem layout expectations

| Path | Creator | Persisted how |
|---|---|---|
| `uploads/` (CWD-relative; `${app.upload.base-dir:uploads}`) | `FileStorageService#@PostConstruct` + Dockerfile `mkdir -p uploads` | **container-local** (no volume — DEP-03) |
| `uploads/mentorship-cover-images/{id}-EduNest-{uuid}.{ext}` | `ImageStorageService` | same |
| `uploads/{student|mentor|admin|chatroom}/…` | `ImageStorageService.saveImage` | same |
| `uploads/task-attachment|project-attachment|submissions/…` | `FileStorageService` | same |

`config/WebConfig` serves `/uploads/**` → `file:uploads/` (public in both profiles).

## 4. Profile-activation pitfalls

1. **Default (no profile) = dev chain:** `anyRequest().permitAll()` + httpBasic + formLogin enabled. IDE runs and CLI `spring-boot:run` without `-Dspring.profiles.active=prod` are **not** production-safe (SEL-02 / ARCH-001).
2. The Dockerfile always sets `prod`; when running locally, set the env var yourself to exercise the hardened chain — `SPRING_PROFILES_ACTIVE=prod` (watch for typos: a misspelled variable silently degrades to the open dev chain).
3. The `prod` profile also flips static-locations (adds `file:uploads/`); earlier team docs recorded only the file-serving diff, but the Jackson strictness delta is prod-only too (see matrix).

## 5. Local runbook (JDK note)

- JDK: `pom` targets 21; wrapper historically ran JDK 24 on this workstation; IDE run config uses `C:\Program Files\Java\jdk-21`. Align builds on 21.
- Commands: `.\mvnw.cmd dependency:resolve -U` (first-time), `.\mvnw.cmd clean compile -DskipTests`, `.\mvnw.cmd spring-boot:run` with env vars (or set `prod` profile to test the hardened chain).
- Grace period: `EduNestApplicationTests` needs DB+mail env; run `.\mvnw.cmd test` only with env resolved.

## 6. Configuration debt (IDs in ../ISSUES.md)

- DEP-05: JWT secret handling (above).
- DEP-07: no compose/k8s manifest tracked; no `.env.example`.
- SEL-01/SEL-02 cross-references; DB-01 (`ddl-auto`), DEP-04 (show-sql), API-05 (Jackson strictness delta).

## 7. Supabase PostgreSQL — implemented state ([ADD-01]/EB-81, `feat/add-01-supabase-pg`, PR #68)

Implemented in code: `postgresql` JDBC driver (Spring Boot-managed version) replaces `mysql-connector-j` in `pom.xml`; `org.postgresql.Driver` + `org.hibernate.dialect.PostgreSQLDialect` set in **both** `application.properties` and `application-prod.properties`; `DATABASE_URL`/`DATABASE_NAME`/`DATABASE_PASSWORD` env contract unchanged. **Merged via PR #68 (EB-81 Done).**

**Verified evidence (development integration test, 2026-11-02) — against the Hazem-approved DEVELOPMENT Supabase project (`ciogysvcykzxrjuskxmk`):**
- ✅ PostgreSQL Session Pooler connection successful: authentication ok via `aws-0-eu-west-1.pooler.supabase.com:5432` (`sslmode=require`; TLS terminates at the Supavisor edge — `pg_stat_ssl` reports `ssl=f` server-side, standard pooler behavior).
- ✅ Spring Boot **prod-profile startup successful**: `Started EduNestApplication in 27.31s` (Hibernate 6.6.33; HikariPool-1 obtained `org.postgresql.jdbc.PgConnection`; Tomcat on 8080).
- ✅ Hibernate (`ddl-auto=update`, dev-test only) created **42 tables** in the previously **empty** development database — no failed statements, nothing pre-existing touched.
- ✅ `enrollments.platform_profit` mapped to **`double precision` default `0.0`** in the real database (the Enrollment.java portability fix verified live).
- ✅ Controlled SQL **insert/read/delete smoke successful** (disposable `roles` probe row: 1/1/1; removed afterwards; no stray rows).
- ✅ HTTP authentication smoke returned the **expected 401** (`POST /login-api/login` with unknown credentials — full prod security chain + user-table read).
- ✅ Full Maven H2 suite: **511 tests, 0 failures, 0 errors, 2 skipped** (D-27 intact); GitHub Actions `tests` workflow green on both PR heads.

**Not covered by the above — do not overstate:** production deployment was **NOT tested**; a separately provisioned **staging** environment was **NOT tested** (the Jira DoD wording says "staging" — the boot evidence was obtained on the approved development project; formal staging sign-off belongs to Omar per D-22). Schema/data migration remains owned by **EB-82** (note: the dev DB now holds the 42 Hibernate-generated tables — see the EB-82 handoff notes). Flyway and the removal of `ddl-auto=update` remain owned by **EB-43**. PR **#68 still requires code review and merge**.

**Connection structure (non-secret identifiers):**
| Item | Value |
|---|---|
| Project ref | `ciogysvcykzxrjuskxmk` |
| Connection method | Supavisor **Session Pooler** |
| Host | `aws-0-eu-west-1.pooler.supabase.com` |
| Port | `5432` (session-mode pooler) |
| Database | `postgres` |
| Username | `postgres.ciogysvcykzxrjuskxmk` |
| SSL | required (`sslmode=require` in JDBC URL params) |
| Password | environment-injected only (`DATABASE_PASSWORD`) — never committed, never logged |

**Connection-variant glossary (do not mix these up):**
- **PostgreSQL URI** (`postgresql://user:pw@host:port/db`) — the URI form used by `psql`/tools; **invalid** as `spring.datasource.url`. Prepend `jdbc:`.
- **JDBC URL** (`jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require`) — what `spring.datasource.url` (`DATABASE_URL`) must contain. The driver also supports single-prefixed `jdbc:postgresql://`; anything without the `jdbc:` prefix fails driver detection.
- **Direct connection** — `db.<project-ref>.supabase.co:5432`, hits the database server directly; fine for long-lived services (IPv6-note per plan RISK register; IPv4 on paid add-on).
- **Session Pooler** (current choice, port `5432` via pooler host) — Supavisor session mode: one real PostgreSQL session per client connection; server-prepared/managed statements and per-connection state work as expected → **suitable for HikariCP + Hibernate**; also minimizes IPv4 issues since the pooler is reachable over IPv4.
- **Transaction Pooler** (`6543` via pooler host) — statement-level multiplexing: a transaction may land on a different physical session each time; breaks persistent session state and can corrupt server-prepared statements. **Not appropriate** for this app today. Ports 5432/6543 are **not** interchangeable per mode.

**Environment variable matrix:**

| Variable | Development (local run) | Staging (container) | Production (container) |
|---|---|---|
| `PORT` | `server.port` | HTTP container port (`${PORT:8080}`); dynamically injected by Railway/Render |---|
| `DATABASE_URL` | `jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require` | same shape; secrets stored in GitHub Actions environment secret (`stg`) | same shape (separate Supabase project expected — distinct host/user/password); secret in GitHub Actions environment secret (`prod`) |
| `DATABASE_NAME` | `postgres.ciogysvcykzxrjuskxmk` (holds the **user**) | separates per environment | separates per environment |
| `DATABASE_PASSWORD` | local process environment / secret store — **never in a file** | GH environment secret | GH environment secret |
| `JWT_SECRET` | ≥32 bytes local value | GH environment secret | GH environment secret |
| `MAIL_*` | local SMTP/dev values | staging SMTP env | prod SMTP env |
| `SPRING_PROFILES_ACTIVE` | `prod` required locally to exercise the hardened chain (Dockerfile always sets `prod`) | `prod` | `prod` |

> The staging and production values must point at **different Supabase projects** (D-22). The existing single project is **authorized for development + integration testing only** (Hazem, 2026-11-02) — it must **not** serve staging or production, and production requires an independently approved Supabase project/configuration. Staging/prod provisioning remains unconfirmed; the rows describe the connection *design* per environment.

**Pooling note (not added to `application-prod.properties`):** for Session Pooler, connectivity and a full prod-profile boot were verified **with default Hikari settings**; a modest intentional pool sync (`maximum-pool-size` ≤ 10, `minimum-idle` low) remains a deliberate follow-up decision — no speculative pool tuning was added under EB-81.

**Migration-era note (updated):** the PostgreSQL boot blocker was **resolved**: `entity/mentorship/Enrollment.java` now uses `columnDefinition = "double precision default 0.0"` (portable across PostgreSQL / MySQL / H2; verified live in the development database). The **broader** schema-compatibility sweep (`@Lob` field wire-format, MySQL-dialect native queries, constraint-naming deltas for the converted dump) remains owned by **EB-82**.

**File storage:** **implemented via [ADD-03]/EB-83 (Done)** — `supabase.storage.*` / `SUPABASE_*` contract, dual buckets, fail-closed prod; see `mds/deployment` coverage above. §1–§6 remain the operating contract except where §7 overrides the database rows.

## 8. Canonical environment-variable matrix (P5-04 / EB-70, synced 2026-11-10)

Single canonical list, cross-checked against `.env.example` and `ops/environments.md` (the deployment-facing matrix). Classification per plan: **Required** / Optional / **Dev-only-secret**.

| Class | Variable | Binding | Default | Notes |
|---|---|---|---|---|
| Req | `DATABASE_URL` / `DATABASE_NAME` (holds user) / `DATABASE_PASSWORD` | `spring.datasource.*` | – | Postgres JDBC URL; password env-injected only |
| Req | `JWT_SECRET` | `JwtSecretProvider` | – | ≥32 bytes; boot fails fast (P1-01/SEL-01) |
| Req | `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | `spring.mail.*` | – | SMTP creds |
| Req (container) | `SPRING_PROFILES_ACTIVE` | profile selection | – | `prod` in Dockerfile; must never be unset in real deployments |
| Opt | `PORT` | `server.port` | `8080` | Railway/Render injects |
| Opt | `APP_AUTH_RETURN_TOKEN_IN_BODY` | `app.auth.return-token-in-body` | `true` | Transitional flag; clear at JWT v2 |
| Opt | `APP_COMMISSION_DEFAULT` | `app.commission.default` | `10.0` | Flyway V4 seed (EB-47 / D-17) |
| Opt | `ZOOM_ENABLED` / `ZOOM_ACCOUNT_ID` / `ZOOM_CLIENT_ID` / `ZOOM_CLIENT_SECRET` / `ZOOM_BASE_URL` | `app.zoom.*` | `false`/blank | S2S OAuth (EB-85/D-29); secret = client secret |
| Opt | `APP_ZOOM_HOST_USER_ID` / `APP_ZOOM_MAX_MEETING_MINUTES` / `APP_ZOOM_OAUTH_TOKEN_URL` | `app.zoom.*` | `me` / `40` / zoom.us | EB-66 runtime knobs |
| Opt | `SUPABASE_STORAGE_ENABLED` / `SUPABASE_URL` / `SUPABASE_S3_ENDPOINT` / `SUPABASE_S3_REGION` / `SUPABASE_S3_ACCESS_KEY` / `SUPABASE_S3_SECRET_KEY` / `SUPABASE_STORAGE_PUBLIC_BUCKET` / `SUPABASE_STORAGE_PRIVATE_BUCKET` | `supabase.storage.*` | per `SupabaseStorageProperties` | EB-83/D-25; secrets = access/secret keys |
| Opt | `APP_LIVESESSION_AUTO_CLOSE_*` (enabled, grace-minutes, max-duration-minutes, sweep-rate-ms) | `app.livesession.auto-close.*` | `true` / `10` / `40` / `300000` | Active sweep (EB-57/D-15/Omar grace=10) |
| Opt (dormant) | `APP_LIVESESSION_ATTENDANCE_*` (enforced, pass-threshold, snapshot-grid-minutes, bonus-points, absence-floor) | `app.livesession.attendance.*` | `false` / `75` / `5` / `5` / `0` | **Dormant** per D-15 — built, compiled, off |
| Opt | `APP_RATE_LIMIT_ENABLED` (+ per-bucket `…_LIMIT` / `…_WINDOW_SECONDS`) | `app.rate-limit.*` | `true` (8/60; lockout 5/300) | EB-15/P1-08, no library (D-27-safe) |
| Opt | `APP_UPLOAD_BASE_DIR` | `app.upload.base-dir` | `uploads` | local-disk rollback lane (D-25) |
| Opt | `APP_AUTH_RESET_TOKEN_TTL` | `app.auth.reset-token-ttl` | `PT5M` | reset-token lifetime |
| Opt | `APP_CERTIFICATE_ISSUANCE_BATCH_SIZE` | `app.certificate.issuance-batch-size` | `100` (clamped 1..1000) | EB-54 issuance |
| Opt | `STRIPE_ENABLED` / `STRIPE_CURRENCY` | `app.stripe.enabled` / `app.stripe.currency` | `false` / `USD` | [ADD-06]/EB-108 (D-28): `false` keeps the legacy direct-join on `/join` (C decision); currency hardcoded USD (E decision) |
| Sec | `STRIPE_SECRET_KEY` | `app.stripe.secret-key` | *(env store)* | Stripe Secret key (test mode first: `sk_test_...`); secrets class — env store only, rotation per §9 |
| Sec | `STRIPE_WEBHOOK_SECRET` | `app.stripe.webhook-secret` | *(env store)* | Webhook signing secret (`whsec_...`); endpoint `POST /api/v1/payments/webhook` (public route, HMAC fail-closed) |
| Opt | `STRIPE_SUCCESS_URL` / `STRIPE_CANCEL_URL` | `app.stripe.success-url` / `app.stripe.cancel-url` | *(empty)* | FE landing routes Stripe redirects to; required once `STRIPE_ENABLED=true` (gateway fails fast without them) |
| Dev-only | `EDUNEST_UNSAFE_DEV` | `ProjectSecurityConfig` gate | `false` | Boot refusal without it when `prod` absent; **never set true outside local dev** |

**Not env-configurable (inventory facts):** CORS (`ProjectSecurityConfig`, `*` in both chains), Jackson prod strictness (`application-prod.properties`), multipart caps (10MB in files), `springdoc` UI settings.

**Deferred vars:** none — the previously deferred `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` (D-28 / EB-108) are now wired: see the rows above and section 6b of `.env.example`. Live-mode keys remain gated to P7 provisioning (D-28).

**Guard:** `EnvContractGuardTest` keeps this list in sync with `.env.example` — `${ENV_VAR}` placeholders found in `application*.properties` must exist there, secret shapes must never appear, and dormant defaults are pinned.

## 9. Secrets management (P6-03 / EB-74, 2026-11-11)

The **secret store = Railway environment dashboard** (D-22 amendment): values are injected into the container at boot, never baked into the image (enforced by `SecretsHygieneGuardTest`), never committed to git, and never logged. Supabase owns the DB password and S3 key pairs it issues; local development uses a gitignored `.env` per developer. Vault remains optional per scale (D-22) — not adopted.

Secret-classified variables (`JWT_SECRET`, `DATABASE_PASSWORD`, `MAIL_PASSWORD`, `ZOOM_CLIENT_SECRET`, `SUPABASE_S3_ACCESS_KEY`/`SECRET_KEY`), their holders, rotation cadences, and the full per-secret rotation procedures live in **[`ops/secrets/`](../../ops/secrets/README.md)** — the store bookkeeping: `secrets-inventory.md` (register, zero values), `rotation.md` (stable + app restart, incl. the JWT re-auth window), `JWT-GENERATION-RECORD.md` (provenance only), and CSPRNG generator scripts.

Hygiene gates in CI (both plain-JUnit, D-27-clean): `EnvContractGuardTest` (P5-04) pins template parity; `SecretsHygieneGuardTest` (P6-03) enforces no secret bakes in the Dockerfile, placeholder-only secret bindings, `.gitignore` coverage of `.env*`/`*.pem`/`ops/secrets/local/`, and that git tracks no secret artifacts.

**Injection evidence (prod):** the production container at `https://backend.edunest.dpdns.org/` boots healthy with `JWT_SECRET` dashboard-injected — the fail-fast `JwtSecretProvider` (P1-01/SEL-01) would refuse to boot otherwise, so healthy startup *is* the injection smoke. The staging-container smoke runs with P6-02/EB-73 when the staging project exists.
