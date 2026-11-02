# Environment Configuration

Complete configuration surface (verified against `application.properties`, `application-prod.properties`, `Dockerfile`, and the code that reads env values).

## 1. Required environment variables

| Variable | Used by | Notes |
|---|---|---|
| `DATABASE_URL` | `spring.datasource.url` | Full JDBC URL expected by the naming convention (e.g. `jdbc:postgresql://<host>:5432/<db>?sslmode=require` — see §7) |
| `DATABASE_NAME` | `spring.datasource.username` | (name holds the *user*) |
| `DATABASE_PASSWORD` | `spring.datasource.password` | – |
| `MAIL_HOST`, `MAIL_PORT` | `spring.mail.*` | SMTP |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | `spring.mail.*` | SMTP auth creds |
| `JWT_SECRET` (**now required**) | `config/security/JwtSecretProvider` (single owner; constructed at startup) | **Implemented 2026-10-28 (P1-01/SEL-01):** the hardcoded fallback was deleted. Boot **fails fast** with `IllegalStateException` if `JWT_SECRET` is missing/blank, shorter than 32 bytes (HS256/256-bit), or equal to the retired default (matched by SHA-256 digest). The key is built once and shared; `LoginServiceI.getJwtRole` now delegates to the shared parser (rotation-safe). Minimum 32 bytes enforced |
| `SPRING_PROFILES_ACTIVE` | profile selection | Defaulted to `prod` in the Dockerfile; absence locally runs the **wide-open `!prod` chain** (SEL-02) |

## 2. Properties matrix (dev/default vs prod profile)

| Setting | default (`application.properties`) | `prod` (`application-prod.properties`) |
|---|---|---|
| DDL | `ddl-auto=update` | `ddl-auto=update` (same) |
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

Implemented in code: `postgresql` JDBC driver (Spring Boot-managed version) replaces `mysql-connector-j` in `pom.xml`; `org.postgresql.Driver` + `org.hibernate.dialect.PostgreSQLDialect` set in **both** `application.properties` and `application-prod.properties`; `DATABASE_URL`/`DATABASE_NAME`/`DATABASE_PASSWORD` env contract unchanged. The changes are **committed and pushed on `feat/add-01-supabase-pg` and under review via PR #68** — EB-81 closes only after that review/merge.

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
|---|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require` | same shape; secrets stored in GitHub Actions environment secret (`stg`) | same shape (separate Supabase project expected — distinct host/user/password); secret in GitHub Actions environment secret (`prod`) |
| `DATABASE_NAME` | `postgres.ciogysvcykzxrjuskxmk` (holds the **user**) | separates per environment | separates per environment |
| `DATABASE_PASSWORD` | local process environment / secret store — **never in a file** | GH environment secret | GH environment secret |
| `JWT_SECRET` | ≥32 bytes local value | GH environment secret | GH environment secret |
| `MAIL_*` | local SMTP/dev values | staging SMTP env | prod SMTP env |
| `SPRING_PROFILES_ACTIVE` | `prod` required locally to exercise the hardened chain (Dockerfile always sets `prod`) | `prod` | `prod` |

> The staging and production values must point at **different Supabase projects** (D-22). The existing single project is **authorized for development + integration testing only** (Hazem, 2026-11-02) — it must **not** serve staging or production, and production requires an independently approved Supabase project/configuration. Staging/prod provisioning remains unconfirmed; the rows describe the connection *design* per environment.

**Pooling note (not added to `application-prod.properties`):** for Session Pooler, connectivity and a full prod-profile boot were verified **with default Hikari settings**; a modest intentional pool sync (`maximum-pool-size` ≤ 10, `minimum-idle` low) remains a deliberate follow-up decision — no speculative pool tuning was added under EB-81.

**Migration-era note (updated):** the PostgreSQL boot blocker was **resolved**: `entity/mentorship/Enrollment.java` now uses `columnDefinition = "double precision default 0.0"` (portable across PostgreSQL / MySQL / H2; verified live in the development database). The **broader** schema-compatibility sweep (`@Lob` field wire-format, MySQL-dialect native queries, constraint-naming deltas for the converted dump) remains owned by **EB-82**.

**File storage:** unchanged — still `uploads/` local path ([ADD-03]/Supabase Storage decision recorded in D-25, **NOT yet implemented**). §1–§6 remain the operating contract except where §7 overrides the database rows.
