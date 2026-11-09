# EduNest Database Migration Framework (Flyway)

**Task Reference:** EB-43 (`[P3-01] Flyway baseline + retire ddl-auto=update + prod hygiene`)  
**Parent Plan:** `plan/phases/phase-03-database-integrity/tasks.md`  
**Target Engine:** PostgreSQL 17.x (Supabase)  
**Schema:** `public`  
**Driver / Dialect:** `org.postgresql.Driver` / `org.hibernate.dialect.PostgreSQLDialect`

---

## 1. Migration Naming Conventions & Rules

Flyway applies migrations strictly in alphanumeric version order. All migration files in `src/main/resources/db/migration` must strictly adhere to the following convention:

```
V<version>__<snake_case_description>.sql
```

### Critical Rules:
1. **Version Format:** `V<integer>__` (double underscore separator after the version number). Example: `V1__initial_schema.sql`.
2. **Immutability After Merge:** Once a migration script is merged into `main`, **it must NEVER be modified or renamed**. Standalone Flyway validation checks recorded checksums; modifying an applied migration causes validation failure.
3. **Forward-Only Mutations:** Any bug fix, schema addition, column alteration, or index drop must be implemented as a new, forward-only sequential migration (`V3`, `V4`, etc.).
4. **Targeted Dialect:** All migrations must strictly use PostgreSQL 17.x syntax (e.g. `IDENTITY`, `TEXT`, `DOUBLE PRECISION`, `TIMESTAMP(6)`).

---

## 2. Version Registry & Reservation Table

To prevent version collisions across concurrent feature branches in Phase 3, migrations are registered and reserved centrally as defined in `plan/phases/phase-03-database-integrity/tasks.md`:

| Version | Status | Task Key | Migration File | Description |
| :--- | :--- | :--- | :--- | :--- |
| **`V1`** | **APPLIED** | `EB-43` / `P3-01` | `V1__initial_schema.sql` | Canonical baseline schema (42 entity tables, primary identities, constraints, indexes, and initial roles). |
| **`V2`** | **APPLIED** | `EB-59` / `P4-10` | `V2__mentorship_explore_trigram_index.sql` | `pg_trgm` extension activation and specialized GIN search indexes on mentorship and user full names. |
| **`V3`** | **BUILD COMPLETE — applied to Supabase; merge pending** | `EB-44` / `P3-02` | `V3__uniqueness_constraints_and_dedupe.sql` | Uniqueness guarantees with deterministic dedupe (`earliest id wins`): one enrollment per (student, mentorship), one review per student per mentorship, unordered `(LEAST, GREATEST)` pair-unique conversations + `NOT NULL` participant invariant. All removed rows archived verbatim into the one-time `eb44_archive_*` surfaces; loser-conversation messages are reassigned, never dropped. See `docs/migration/EB-44-PRODUCTION-ADOPTION-RUNBOOK.md` for adoption evidence and requirements for other environments. |
| **`V4`** | **APPLIED** | `EB-47` / `P3-05` | `V4__seed_default_commission.sql` | Default commission rate idempotent initialization (`app.commission.default`). |
| **`V5`** | **APPLIED** | `EB-91` | `V5__refresh_token_sessions.sql` | Refresh token session store and revocation infrastructure. |
| **`V6`** | **APPLIED** | `EB-109` | `V6__add_public_uuid_identifiers.sql` | Global Public UUID migration (cryptographically random `public_id` on all `BaseEntity` tables). |
| **`V7`** | **MERGED — deployment not verified here** | `EB-54` | `V7__certificate_issuance_snapshot.sql` | Nullable certificate issuance snapshots and delivery metadata. |
| **`V8`** | **MERGED** | `EB-62` / `INT-02` | `V8__failed_email_attempts.sql` | Outbox-style persistence table for asynchronous delivery failure observability and bounded retries. |
| **`V9`** | **MERGED** | `EB-51` | `V9__badge_and_total_points_versions.sql` | Adds version columns for badges and total points optimistic locking. |
| **`V10`** | **MERGED** | `EB-51` | `V10__unique_session_attendance_result.sql` | Creates an archive surface, fails closed on duplicate attendance results, and enforces uniqueness. The V9 labels in its SQL are historical and must not be edited. |

*Note: Do not invent or assign version numbers out of order. Consult the engineering lead and Phase 3 task roadmap before reserving versions beyond V5.*

---

## 3. Environment Strategy & Workflow

```
+---------------------------------------------------------------------------------------------------------+
|                                  Environment Configuration Matrix                                       |
+----------------------+--------------------+---------------------+------------------+--------------------+
| Profile / Env        | Flyway Enabled     | ddl-auto            | show-sql         | baseline-on-migrate|
+----------------------+--------------------+---------------------+------------------+--------------------+
| Base / Dev           | false              | validate            | false            | false              |
| Production (prod)    | false              | validate            | false            | false (STRICT)     |
| Test (H2 in-memory)  | false (D-26 / D-27)| create-drop         | false            | false              |
+----------------------+--------------------+---------------------+------------------+--------------------+
```

### 3.1 Local Development Workflow
1. Developers run against their local or containerized PostgreSQL instance.
2. Hibernate automatic updates are disabled (`ddl-auto=validate`).
3. To introduce a schema change, create the next sequential migration, execute the standalone procedure below against an isolated local database, then run the application. Hibernate validates the matching entity schema.

### 3.1.1 Explicit migration deployment (PowerShell)

Normal application startup never migrates by default, including the production profile. Property and environment overrides can re-enable Flyway: restrict access to the DDL-capable credential and use a separate runtime role without schema ownership or DDL privileges. If credential separation is not yet available, protect the shared credential and treat manual process controls as temporary, not as an authorization boundary. Disabling out-of-order execution enforces ordering only.

An authorized operator uses the exact release checkout, a verified target and backup, and a migration-capable database role. Supply secrets through a protected process environment, not command-line arguments, source control or Maven debug logs. Set `FLYWAY_URL`, `FLYWAY_USER`, and `FLYWAY_PASSWORD` in that environment; the URL is a PostgreSQL JDBC URL. Never copy credentials into the commands below. The profile supplies the historical V4 default of 10.0; for a fresh database requiring a different rate, explicitly supply `-Dflyway.placeholders.defaultCommissionRate=<approved-rate>` on migrate.

```powershell
.\mvnw.cmd -B -Pdb-migrate flyway:info
if ($LASTEXITCODE -ne 0) { throw 'Flyway info failed' }
.\mvnw.cmd -B -Pdb-migrate '-Dflyway.ignoreMigrationPatterns=*:pending' flyway:validate
if ($LASTEXITCODE -ne 0) { throw 'Pre-migration validation failed' }
.\mvnw.cmd -B -Pdb-migrate flyway:migrate
if ($LASTEXITCODE -ne 0) { throw 'Migration failed; do not deploy' }
.\mvnw.cmd -B -Pdb-migrate '-Dflyway.ignoreMigrationPatterns=' flyway:validate
if ($LASTEXITCODE -ne 0) { throw 'Strict validation failed; do not deploy' }
.\mvnw.cmd -B -Pdb-migrate flyway:info
if ($LASTEXITCODE -ne 0) { throw 'Flyway info failed' }
```

Check that the last info output reports exactly the release's required version (V10 for this release), all migrations successful and none pending or future. Only then deploy the matching application artifact. Archive tables created by V3 and V10 remain in `public` unless an independently approved development-only cleanup is performed; see `docs/migration/DEVELOPMENT-DATABASE-RESET.md`.

Do not deploy after a failed or skipped migration; investigate before retrying and never use Flyway repair as a routine rollback. Hibernate validation is an additional entity-schema check, not a complete Flyway release-state gate.

### 3.2 Automated CI Testing Workflow
- The standard test suite (`mvnw test`) uses the `test` profile with in-memory H2 in MySQL compatibility mode.
- Flyway is explicitly disabled in `application-test.properties` (`spring.flyway.enabled=false`) to isolate fast unit tests from PostgreSQL DDL syntax.
- PostgreSQL migration execution is validated through dedicated integration tests using Testcontainers or isolated PostgreSQL test databases.

---

## 4. Fresh Database Initialization

When spinning up a new development environment, staging environment, or test container against an empty PostgreSQL database:
1. Ensure the target PostgreSQL database and schema (`public`) are provisioned.
2. Run the standalone migration procedure in §3.1.1 with local database credentials.
3. Because the schema is empty (0 tables), Flyway:
   - Creates the metadata table `flyway_schema_history`.
   - Executes `V1__initial_schema.sql` (creating all 42 tables and initial roles).
   - Executes `V2__mentorship_explore_trigram_index.sql` (creating trigram indexes).
   - Executes `V3__uniqueness_constraints_and_dedupe.sql` (creating the uniqueness indexes/constraints and the four one-time `eb44_archive_*` dedupe surfaces; on an empty database these are no-op data steps).
4. Hibernate's `ddl-auto=validate` executes, verifying zero schema drift between entities and database.

---

## 5. Existing Database Baselining Runbook (Supabase Migration)

> [!CAUTION]
> **Production Safety Rule:**
> `spring.flyway.baseline-on-migrate` is set to `false` across all profiles.
> Never enable `baseline-on-migrate=true` in ambient application property files. An ambient setting will silently mark drifted, partial, or wrong databases as valid V1 baselines.

When adopting Flyway on a database that already has the 42 tables provisioned prior to Flyway (such as the existing Supabase PostgreSQL database):

### Step 5.1 — Pre-Baseline Schema Drift Audit
Before baselining, verify that the existing database matches the JPA entity metamodel:
```powershell
# Run the read-only live schema validation test
.\mvnw.cmd test -Dtest=PostgreSqlLiveSchemaValidationTest
```
- Assert that all 42 application tables are present.
- Assert that all 5 text columns (`contact_message.message`, `projects.project_brief`, `project_submission.project_feedback`, `tasks.task_description`, `task_submission.task_feedback`) are type `text` (not `oid`).
- Assert that `ddl-auto=validate` completes with 0 errors in a read-only transaction.

### Step 5.2 — Database Identity & Target Sanity Check
Confirm you are connected to the intended target project and database ref:
```sql
SELECT current_database(), current_schema(), inet_server_addr(), version();
```

### Step 5.3 — Pre-Migration Backup
Before running migration or baseline commands on production or persistent staging databases:
```bash
# Take a point-in-time schema and data dump
pg_dump --clean --if-exists --no-owner --no-privileges -h <host> -U <user> -d <database> -f pre_flyway_baseline_backup.sql
```

### Step 5.4 — Explicit One-Time Manual Baseline
Only after explicit approval, execute a standalone baseline using the isolated migration profile and credentials described in §3.1.1:
```powershell
.\mvnw.cmd -B -Pdb-migrate '-Dflyway.baselineVersion=1' '-Dflyway.baselineDescription=Baseline schema' flyway:baseline
```
Never use an application startup to baseline or migrate a shared database.

### Step 5.5 — Post-Baseline Verification
Inspect `flyway_schema_history`:
```sql
SELECT installed_rank, version, description, type, script, success 
FROM flyway_schema_history 
ORDER BY installed_rank;
```
Expected output:
- Row 1: `version = 1`, `description = 'Baseline schema'`, `type = 'BASELINE'`, `success = true`.
- Row 2: `version = 2`, `description = 'mentorship explore trigram index'`, `type = 'SQL'`, `success = true`.

---

## 6. Critical Operational Safeguards

1. **`clean-disabled=true` is Permanent:**
   `spring.flyway.clean-disabled=true` is hardcoded across all configuration profiles. Calling `flyway.clean()` drops all schemas and tables in the target database. It must remain disabled at all times.
2. **PostgreSQL Extension Privileges (`pg_trgm`):**
   `CREATE EXTENSION IF NOT EXISTS pg_trgm` requires superuser or `extensions` management privileges in managed cloud PostgreSQL. In Supabase, standard database connections have extension creation rights, but in hardened environments, the extension must be pre-installed by the DBA before migration execution.
3. **Advisory Locks:**
   Flyway uses PostgreSQL advisory locks to guarantee that concurrent application instances in clustered deployments execute migrations serially without race conditions.
