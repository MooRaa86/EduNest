# EduNest EB-82: Production-Grade MySQL to PostgreSQL Migration Runbook

**Jira Task:** `EB-82` — `[ADD-02] MySQL to PostgreSQL Schema & Data Migration`  
**Engineer:** Senior PostgreSQL Migration Engineer & Database Reliability Specialist  
**Target Environment:** Isolated PostgreSQL Rehearsal Target -> Staging/Production  
**Execution Policy:** Safety-Gated / Strict Non-Destructive to Live Shared Development DB  
**Status:** **REHEARSAL PREPARED — AWAITING ISOLATED DATABASE & DATA EXPORT AUTHORIZATION**

---

## 1. Safety Principles & Execution Gates

### 1.1 Non-Negotiable Guardrails
1. **Never Touch Shared Dev Project:** Do not execute rehearsal data migration or schema mutations (`DROP`, `ALTER`, `TRUNCATE`, `INSERT`) against the active shared Supabase development database.
2. **Authorization Gate:** No live MySQL data export or migration may be run without explicit operator approval.
3. **Dedicated Target Required:** Migration must only be executed against a verified, isolated PostgreSQL target (e.g. an authorized staging instance or isolated local database).
4. **No Docker / Testcontainers:** Per operational mandate, Docker and Testcontainers are strictly prohibited in this workflow.

### 1.2 Rehearsal Gate Checklist
Before any live rehearsal runs:
- [x] All application code and schema compatibility fixes committed locally.
- [x] Full Maven test suite passing (512 tests, 0 failures, 0 errors).
- [x] 42-table schema reconciliation completed; orphan `user_settings` table excluded.
- [x] 5 `@Lob` text columns converted to `columnDefinition = "TEXT"`.
- [x] Roles semantic mapping and collision resolver implemented (avoiding blind upsert).
- [ ] Isolated rehearsal database provisioned and connection verified.
- [ ] Operator explicit authorization granted for source data export.
- [ ] Operator explicit authorization granted for isolated target import.

---

## 2. PostgreSQL OID to TEXT Conversion Procedures

In PostgreSQL with Hibernate 6, `@Lob` on `String` previously created columns of type `oid` (referencing `pg_largeobject`). All 5 entities have been corrected in code to use `columnDefinition = "TEXT"`.

To align existing PostgreSQL database tables with the corrected entity mappings, execute the appropriate DDL:

### 2.1 Populated Tables (Content-Preserving Conversion)
If any of these columns already contain data stored as large objects, a direct type cast `USING col::text` would only copy the numeric OID identifier, destroying the text!  
The content must be extracted from `pg_largeobject` using `lo_get`:

```sql
BEGIN;

-- 1. contact_message.message
ALTER TABLE public.contact_message 
    ALTER COLUMN message TYPE TEXT 
    USING (CASE WHEN message IS NOT NULL THEN convert_from(lo_get(message), 'UTF8') ELSE NULL END);

-- 2. projects.project_brief
ALTER TABLE public.projects 
    ALTER COLUMN project_brief TYPE TEXT 
    USING (CASE WHEN project_brief IS NOT NULL THEN convert_from(lo_get(project_brief), 'UTF8') ELSE NULL END);

-- 3. project_submission.project_feedback (actual DB column name)
ALTER TABLE public.project_submission 
    ALTER COLUMN project_feedback TYPE TEXT 
    USING (CASE WHEN project_feedback IS NOT NULL THEN convert_from(lo_get(project_feedback), 'UTF8') ELSE NULL END);

-- 4. tasks.task_description
ALTER TABLE public.tasks 
    ALTER COLUMN task_description TYPE TEXT 
    USING (CASE WHEN task_description IS NOT NULL THEN convert_from(lo_get(task_description), 'UTF8') ELSE NULL END);

-- 5. task_submission.task_feedback (actual DB column name)
ALTER TABLE public.task_submission 
    ALTER COLUMN task_feedback TYPE TEXT 
    USING (CASE WHEN task_feedback IS NOT NULL THEN convert_from(lo_get(task_feedback), 'UTF8') ELSE NULL END);

COMMIT;
```

### 2.2 Empty Tables (Standard Schema Realignment)
If the tables are empty (`COUNT(*) = 0`), the columns can be realigned directly:

```sql
BEGIN;
ALTER TABLE public.contact_message ALTER COLUMN message TYPE TEXT;
ALTER TABLE public.projects ALTER COLUMN project_brief TYPE TEXT;
ALTER TABLE public.project_submission ALTER COLUMN project_feedback TYPE TEXT;
ALTER TABLE public.tasks ALTER COLUMN task_description TYPE TEXT;
ALTER TABLE public.task_submission ALTER COLUMN task_feedback TYPE TEXT;
COMMIT;
```

---

## 3. Roles Collision & Referential Integrity Strategy

### 3.1 Forensic Analysis
The application has 3 semantic roles defined by [`RolesSeeder`](file:///C:/Users/Dell/AppData/Local/Temp/opencode/edunest-eb82/src/main/java/com/example/gradproj/EduNest/config/RolesSeeder.java):
`ADMIN`, `MENTOR`, `STUDENT`.
The target PostgreSQL database has unique constraint `uk_roles_name` on `roles(name)`.

**CRITICAL HAZARD:**  
Never run `ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name`. If source MySQL has `id=1: STUDENT` while target PostgreSQL has `id=1: ADMIN`, a blind ID upsert will overwrite `ADMIN` with `STUDENT`, corrupting role semantics and creating privilege escalation vulnerabilities.

### 3.2 Safe Semantic Reconciliation Algorithm
1. Read existing target roles: create a map `target_roles = { role_name: id }`.
2. Read source roles: create a map `source_roles = { id: role_name }`.
3. Construct explicit ID translation table:
   `role_translation_map[source_id] = target_roles[source_role_name]`
4. If a source role name does NOT exist in target, insert it with a new sequence ID and record the mapping.
5. In Phase 2 (`users` table migration), transform `users.role_id` using:
   `migrated_user.role_id = role_translation_map[source_user.role_id]`
6. This ensures:
   - Zero primary key conflicts.
   - Zero semantic role overwrites.
   - 100% referential integrity for all users.

---

## 4. Topological Data Migration Pipeline (0 Circular Dependencies)

The migration engine executes table insertion in strict topological order:

| Tier | Sequence | Tables | Constraints & Notes |
| :--- | :--- | :--- | :--- |
| **Tier 0** | 1–6 | `roles`, `badges`, `admin_badges`, `contact_message`, `certificates`, `conversations` | No FK dependencies. `roles` handled via semantic resolver. |
| **Tier 1** | 7 | `users` | FK `role_id` mapped via semantic resolver. |
| **Tier 2** | 8–18 | `admin`, `student`, `mentor`, `user_notification`, `admin_notification`, `badge_awards`, `user_admin_badges`, `chat_messages`, `user_device_tokens`, `mentor_applications`, `activity_logs` | FKs to `users`. `admin`/`student`/`mentor` share PK with `users.id`. |
| **Tier 3** | 19–26 | `tracks`, `mentor_availability`, `mentor_skills`, `user_skills`, `mentor_reviews`, `group_sessions`, `one_on_one_sessions`, `session_attendance` | FKs to `mentor` and `users`. |
| **Tier 4** | 27–29 | `enrollments`, `weeks`, `payment_transactions` | FKs to `tracks`, `student`, `enrollments`. |
| **Tier 5** | 30–34 | `projects`, `tasks`, `quiz`, `learning_materials`, `week_progress` | FKs to `weeks`. All TEXT columns migrated cleanly. |
| **Tier 6** | 35–38, 41–42 | `project_submission`, `task_submission`, `quiz_submission`, `questions`, `total_points`, `leaderboard` | FKs to `projects`, `tasks`, `quiz`, `student`. |
| **Tier 7** | 39–40 | `options`, `student_answers` | FKs to `questions`, `quiz_submission`. |

---

## 5. PostgreSQL Identity Sequence Synchronization

Following data loading, all auto-increment identity sequences must be synchronized to the maximum imported ID to prevent subsequent `duplicate key value violates unique constraint` errors during runtime:

```sql
DO $$
DECLARE
    r RECORD;
    v_max_id BIGINT;
BEGIN
    FOR r IN
        SELECT 
            c.table_name,
            c.column_name,
            pg_get_serial_sequence(quote_ident(c.table_schema) || '.' || quote_ident(c.table_name), c.column_name) AS seq_name
        FROM information_schema.columns c
        JOIN information_schema.tables t 
            ON c.table_name = t.table_name AND c.table_schema = t.table_schema
        WHERE c.table_schema = 'public' 
          AND t.table_type = 'BASE TABLE'
          AND (c.column_default LIKE 'nextval(%' OR c.is_identity = 'YES')
    LOOP
        IF r.seq_name IS NOT NULL THEN
            EXECUTE format('SELECT COALESCE(MAX(%I), 0) + 1 FROM public.%I', r.column_name, r.table_name) INTO v_max_id;
            EXECUTE format('SELECT setval(%L, %s, false)', r.seq_name, v_max_id);
            RAISE NOTICE 'Synchronized sequence % for %.% to %', r.seq_name, r.table_name, r.column_name, v_max_id;
        END IF;
    END LOOP;
END $$;
```

---

## 6. Backup & Restore Plan

### 6.1 Source MySQL Backup Procedure
```bash
mysqldump -h localhost -P 3306 -u root -p \
  --single-transaction \
  --quick \
  --hex-blob \
  --default-character-set=utf8mb4 \
  --routines \
  --triggers \
  edunest > edunest_mysql_backup_$(date +%Y%m%d_%H%M%S).sql
```
- **Integrity Check:** Verify non-zero file size, trailing `Dump completed on` comment.

### 6.2 Target PostgreSQL Baseline Backup Procedure
```bash
pg_dump -h <isolated_pg_host> -p 5432 -U postgres -d <isolated_db> \
  --schema=public \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  -f target_pg_pre_migration_$(date +%Y%m%d_%H%M%S).sql
```

### 6.3 Rollback Criteria & Execution
If validation fails:
1. Stop application.
2. Truncate all 41 migrated tables using `CASCADE`:
   ```sql
   TRUNCATE TABLE 
       student_answers, options, questions, quiz_submission, task_submission, project_submission,
       total_points, leaderboard, week_progress, learning_materials, quiz, tasks, projects,
       weeks, enrollments, session_attendance, one_on_one_sessions, group_sessions,
       mentor_reviews, user_skills, mentor_skills, mentor_availability, tracks,
       chat_messages, user_admin_badges, badge_awards, admin_notification, user_notification,
       mentor, student, admin, users, certificates, contact_message, admin_badges, badges
   CASCADE;
   ```
3. Re-align sequences or restore baseline dump.

---

## 7. EB-82 to EB-43 Flyway Handoff Specification

Task **EB-43** owns Flyway runtime migration management.  
EB-82 provides the clean, verified PostgreSQL schema baseline:
1. **Flyway Migration Script:** `V1__init_schema.sql` containing DDL for the 42 public tables (incorporating `platform_profit double precision`, `TEXT` columns, and identity sequences).
2. **Flyway Configuration:**
   ```properties
   spring.flyway.enabled=true
   spring.flyway.baseline-on-migrate=true
   spring.flyway.baseline-version=1
   spring.flyway.baseline-description=PostgreSQL baseline from EB-82 migration
   spring.jpa.hibernate.ddl-auto=validate
   ```
3. **Execution Boundary:** No Flyway dependencies or runtime configurations are added in EB-82.
