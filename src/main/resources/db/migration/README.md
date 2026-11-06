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
2. **Immutability After Merge:** Once a migration script is merged into `main`, **it must NEVER be modified or renamed**. Flyway validates SHA-256 checksums on boot; altering a merged migration causes boot failure (`FlywayException: Validate failed: Checksum mismatch`).
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
| **`V5`** | **RESERVED** | `EB-91` | `V5__refresh_token_sessions.sql` | Refresh token session store and revocation infrastructure. |
| **`V6`** | **APPLIED** | `EB-109` | `V6__add_public_uuid_identifiers.sql` | Global Public UUID migration (cryptographically random `public_id` on all `BaseEntity` tables). |

*Note: Do not invent or assign version numbers out of order. Consult the engineering lead and Phase 3 task roadmap before reserving versions beyond V5.*

---

## 3. Environment Strategy & Workflow

```
+---------------------------------------------------------------------------------------------------------+
|                                  Environment Configuration Matrix                                       |
+----------------------+--------------------+---------------------+------------------+--------------------+
| Profile / Env        | Flyway Enabled     | ddl-auto            | show-sql         | baseline-on-migrate|
+----------------------+--------------------+---------------------+------------------+--------------------+
| Base / Dev           | true               | validate            | false            | false              |
| Production (prod)    | true               | validate            | false            | false (STRICT)     |
| Test (H2 in-memory)  | false (D-26 / D-27)| create-drop         | false            | false              |
+----------------------+--------------------+---------------------+------------------+--------------------+
```

### 3.1 Local Development Workflow
1. Developers run against their local or containerized PostgreSQL instance.
2. Hibernate automatic updates are disabled (`ddl-auto=validate`).
3. To introduce a schema change:
   - Create the next sequential migration file under `src/main/resources/db/migration/`.
   - Update the corresponding JPA entity annotation.
   - Run the application. Flyway applies the migration first, and Hibernate validates the matching entity schema.

### 3.2 Automated CI Testing Workflow
- The standard test suite (`mvnw test`) uses the `test` profile with in-memory H2 in MySQL compatibility mode.
- Flyway is explicitly disabled in `application-test.properties` (`spring.flyway.enabled=false`) to isolate fast unit tests from PostgreSQL DDL syntax.
- PostgreSQL migration execution is validated through dedicated integration tests using Testcontainers or isolated PostgreSQL test databases.

---

## 4. Fresh Database Initialization

When spinning up a new development environment, staging environment, or test container against an empty PostgreSQL database:
1. Ensure the target PostgreSQL database and schema (`public`) are provisioned.
2. Boot the Spring Boot application with the intended database configuration. The repository does not configure a Flyway Maven plugin.
3. Because the schema is empty (0 tables), Flyway automatically:
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
Execute the baseline command explicitly using the Flyway Maven plugin or Flyway CLI:
```powershell
.\mvnw.cmd flyway:baseline -Dflyway.baselineVersion=1 -Dflyway.baselineDescription="Baseline schema"
```
Or via a one-time single-command environment variable when launching the JAR:
```bash
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true java -jar target/EduNest-0.0.1-SNAPSHOT.jar
```
*(Remove `SPRING_FLYWAY_BASELINE_ON_MIGRATE` immediately after the first successful boot).*

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
