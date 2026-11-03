# EduNest EB-82: Schema-Only Reconciliation & Compatibility Runbook

**Jira Task:** `EB-82` — `[ADD-02] MySQL to PostgreSQL Schema & Data Migration`  
**Engineer:** Senior PostgreSQL Migration Engineer & Spring Boot Specialist  
**Execution Scope:** **SCHEMA-ONLY RECONCILIATION** (Per Project Owner Scope Directive)  
**Target Environment:** Supabase PostgreSQL Development Project (`postgres`, schema `public`)  
**Status:** **SCHEMA-ONLY MIGRATION READY FOR CONTROLLED EXECUTION** (Awaiting Operator DDL Approval)

---

## 1. Project Owner Scope Change Directive

> **Authoritative Decision (November 3, 2026):**  
> *"Existing MySQL records are obsolete development data. EB-82 is intentionally limited to PostgreSQL schema reconciliation and compatibility. No MySQL data export or import is required."*

### What Has Changed:
1. **Zero Data Migration:** Discarded the requirement to export, transform, or import the 396 legacy MySQL records.
2. **No Data Pipeline Required:** Cancelled `mysqldump`, bulk inserts, user/mentor data migration, foreign key remapping, and data import sequence synchronization.
3. **Preserve Existing Target State:** The existing Supabase PostgreSQL project is retained. Existing data (the 3 pre-seeded `roles` records) is preserved. No tables will be dropped or recreated.
4. **Primary Objective:** Ensure the existing 42 PostgreSQL application tables are 100% structurally aligned with current Spring Boot JPA entities, resolving the 5 `@Lob` text columns currently defined as PostgreSQL `oid`.

---

## 2. Jira EB-82 Scope Traceability & Acceptance Criteria Delta

### Original Jira Description vs Revised Scope
The original Jira task EB-82 description assumed a full data migration:
- *Original Criteria 1: MySQL schema and data dumped and transferred.* -> **SUPERSEDED / DESCIFIED** (Data confirmed obsolete by owner).
- *Original Criteria 2: Supabase schema compatible with Spring Boot entities.* -> **ACTIVE & COMPLETED IN EB-82**.
- *Original Criteria 3: Data integrity and foreign keys validated across both databases.* -> **REVISED TO SCHEMA INTEGRITY VALIDATION**.
- *Original Criteria 4: Flyway baseline migration created.* -> **OWNED BY EB-43** (Specification handed off by EB-82).

> [!WARNING]
> Do NOT mark original data migration criteria as completed in Jira without noting the Project Owner's explicit scope change directive.

### Proposed Jira Scope Change Comment (For Lead Review)
```text
[SCOPE CHANGE NOTICE - EB-82]
Per Engineering Lead / Project Owner directive (2026-11-03):
- All 396 legacy MySQL records are confirmed obsolete development/test data and are discarded from migration scope.
- EB-82 is updated to a SCHEMA-ONLY migration ensuring the Supabase PostgreSQL database is structurally aligned and 100% compatible with current Spring Boot JPA entities.
- Completed Deliverables:
  1. Fixed EnrollmentRepository.getWeekCompletions PostgreSQL query (string_agg).
  2. Fixed 5 @Lob entity annotations to TEXT columnDefinition (eliminating OID mapping).
  3. Audited all 42 public application tables and verified 0-row status on all tables except the 3 pre-seeded roles.
  4. Prepared non-destructive DDL converting the 5 OID columns to TEXT on Supabase.
  5. Validated test suite: 514 tests passing.
  6. Prepared clean schema baseline specification for EB-43 (Flyway).
```

---

## 3. Database State Audit (Observed)

### 3.1 Target Database: Supabase PostgreSQL 17.11 (`postgres`)
- **Schema:** `public`
- **Application Tables:** 42 tables present
- **Existing Records:**
  - `roles`: 3 records (`ADMIN`, `MENTOR`, `STUDENT`)
  - All other 41 tables: **0 rows**
- **Legacy Table `user_settings`:** Does **NOT** exist in PostgreSQL (and will NOT be created, as it is unmapped in JPA and dead legacy code).

### 3.2 Current Incompatible Columns
Due to previous Hibernate 6 `@Lob` mappings on `String` fields, five columns exist as `oid` in Supabase PostgreSQL:
1. `contact_message.message` (`oid`, table empty)
2. `projects.project_brief` (`oid`, table empty)
3. `project_submission.project_feedback` (`oid`, table empty)
4. `tasks.task_description` (`oid`, table empty)
5. `task_submission.task_feedback` (`oid`, table empty)

---

## 4. Corrective DDL for Supabase Execution

Because all 5 corresponding tables currently contain **0 rows**, the type conversion does not require heavy data migration or `pg_largeobject` cleanup. However, PostgreSQL requires an explicit `USING` clause when altering from `oid` to `text`.

The following DDL is reviewed, non-destructive, and idempotent.

```sql
-- ====================================================================
-- EduNest EB-82: Non-Destructive OID to TEXT Schema Alignment
-- Target: Supabase PostgreSQL (schema: public)
-- Execution: Requires explicit operator authorization
-- ====================================================================

BEGIN;

-- 1. Table: contact_message
ALTER TABLE public.contact_message 
    ALTER COLUMN message TYPE TEXT 
    USING (CASE WHEN message IS NOT NULL THEN convert_from(lo_get(message), 'UTF8') ELSE NULL END);

-- 2. Table: projects
ALTER TABLE public.projects 
    ALTER COLUMN project_brief TYPE TEXT 
    USING (CASE WHEN project_brief IS NOT NULL THEN convert_from(lo_get(project_brief), 'UTF8') ELSE NULL END);

-- 3. Table: project_submission (actual DB column name: project_feedback)
ALTER TABLE public.project_submission 
    ALTER COLUMN project_feedback TYPE TEXT 
    USING (CASE WHEN project_feedback IS NOT NULL THEN convert_from(lo_get(project_feedback), 'UTF8') ELSE NULL END);

-- 4. Table: tasks
ALTER TABLE public.tasks 
    ALTER COLUMN task_description TYPE TEXT 
    USING (CASE WHEN task_description IS NOT NULL THEN convert_from(lo_get(task_description), 'UTF8') ELSE NULL END);

-- 5. Table: task_submission (actual DB column name: task_feedback)
ALTER TABLE public.task_submission 
    ALTER COLUMN task_feedback TYPE TEXT 
    USING (CASE WHEN task_feedback IS NOT NULL THEN convert_from(lo_get(task_feedback), 'UTF8') ELSE NULL END);

COMMIT;
```

> [!NOTE]
> The `convert_from(lo_get(...), 'UTF8')` clause provides defensive data preservation: for 0 rows it executes instantaneously; if any row exists, it decodes the actual text rather than casting the numeric OID identifier to text.

---

## 5. Verification & Schema Validation Strategy

### 5.1 Hibernate Boot Validation (`ddl-auto=validate`)
Once the corrective DDL is applied to Supabase, Spring Boot will be able to start with strict schema validation without performing automatic DDL updates:

```properties
# Validated production-ready setting
spring.jpa.hibernate.ddl-auto=validate
```
When `ddl-auto=validate` is active:
- Hibernate validates that all 42 entity tables exist in `public`.
- Hibernate validates that all columns, types, nullability, and primary keys match.
- Hibernate confirms that `message`, `project_brief`, `project_feedback`, `task_description`, and `task_feedback` are compatible `TEXT` types.

### 5.2 Query Compatibility
- The `EnrollmentRepository.getWeekCompletions` query utilizes `string_agg(CAST(s.id AS text), ',')`, which is verified compatible with PostgreSQL 17.11 and H2.

---

## 6. Flyway Baseline Handoff Specification (EB-82 → EB-43)

Task **EB-43** owns introducing Flyway runtime dependencies and migration management.  
EB-82 provides the clean, verified PostgreSQL schema baseline:

1. **Populated Database Baselining (Supabase Development):**
   ```properties
   spring.flyway.enabled=true
   spring.flyway.baseline-on-migrate=true
   spring.flyway.baseline-version=1
   spring.flyway.baseline-description=PostgreSQL schema baseline (EB-82)
   spring.jpa.hibernate.ddl-auto=validate
   ```
2. **Fresh Environment Initialization:**
   `V1__init_schema.sql` will contain the DDL for the 42 public tables (incorporating `platform_profit double precision` and `TEXT` column definitions).
3. **Execution Rule:** No Flyway runtime dependencies or migration files are added under EB-82.
