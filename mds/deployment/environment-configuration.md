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

## 7. Supabase PostgreSQL — implemented state ([ADD-01]/EB-81, `feat/add-01-supabase-pg`, uncommitted)

Implemented in code so far: `postgresql` JDBC driver (Spring Boot-managed version) replaces `mysql-connector-j` in `pom.xml`; `org.postgresql.Driver` + `org.hibernate.dialect.PostgreSQLDialect` set in **both** `application.properties` and `application-prod.properties`; `DATABASE_URL`/`DATABASE_NAME`/`DATABASE_PASSWORD` env contract unchanged. **EB-81 is NOT complete** — its DoD ("app boots against Supabase staging under the prod profile") is still open.

**Status (verified, 2026-11-02):**
- Supabase project exists; connection identifiers obtained (below). ✅
- Staging status **confirmed for disposable use: NO** — the project is not yet approved as a disposable staging target.
- Database authentication: **NOT tested**. Application startup against Supabase: **NOT validated**. **No remote DB operation is authorized yet.**
- Schema/data migration belongs to **EB-82**; the Flyway schema lifecycle belongs to **EB-43** (which will also correct `ddl-auto=update` — do not boot the `prod` profile against any real database until then).

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

> The staging and production values must point at **different Supabase projects** (D-22). **The existing single project must NOT automatically serve both development and staging:** until a dedicated staging arrangement is confirmed, the `Development` column above is design-only, not an approved target. **Production requires an independently approved Supabase project/configuration** — never the development or staging project. Staging provisioning state has not been confirmed; until then `DATABASE_URL` above describes the connection *design*, not an approved target.

**Pooling note (not added to `application-prod.properties`):** for Session Pooler, a modest Hikari sync (`maximum-pool-size` ≤ 10, `minimum-idle` low) is reasonable once connectivity is verified; no speculative pool tuning was added under EB-81 — pool sizing is a deliberate decision after a successful connectivity/boot test.

**Migration-era caveat (carried from the plan):** `entity/mentorship/Enrollment.java` uses `columnDefinition = "double default 0.0"` — a literal DDL fragment PostgreSQL will reject for a clean schema (`double` is not a PG type) — masking currently invisible because H2 MODE=MySQL accepts it. Owned by EB-82 (full compatibility sweep) unless explicitly pre-approved under EB-81. This means a prod-profile boot against Supabase will currently fail during DDL.

**File storage:** unchanged — still `uploads/` local path ([ADD-03]/Supabase Storage decision recorded in D-25, **NOT yet implemented**). §1–§6 remain the operating contract except where §7 overrides the database rows.
