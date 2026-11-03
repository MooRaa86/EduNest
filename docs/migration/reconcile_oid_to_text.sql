-- ====================================================================
-- EduNest EB-82: Non-Destructive OID to TEXT Schema Reconciliation
-- Target Database: postgres
-- Target Schema: public
-- Target Project: Supabase PostgreSQL 17.11 Development Environment
-- Scope: Schema-Only Alignment for 5 Text Columns
-- Safety: Lock-gated, assertion-verified, empty-table conversion
-- Execution: Requires explicit operator authorization. DO NOT RUN AUTOMATICALLY.
-- ====================================================================

BEGIN;

-- 1. Enforce strict timeouts to prevent hanging or transaction pileup
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15s';

-- 2. Target Project & Schema Sanity Assertion
DO $$
BEGIN
    IF current_database() <> 'postgres' THEN
        RAISE EXCEPTION 'Target Project Check Failed: Current database is %, expected postgres', current_database();
    END IF;
    IF current_schema() <> 'public' THEN
        RAISE EXCEPTION 'Target Schema Check Failed: Current schema is %, expected public', current_schema();
    END IF;
END $$;

-- 3. Acquire table locks to guarantee zero concurrent inserts during alteration
LOCK TABLE 
    public.contact_message, 
    public.projects, 
    public.project_submission, 
    public.tasks, 
    public.task_submission 
IN ACCESS EXCLUSIVE MODE;

-- 4. Assert all 5 tables are strictly EMPTY while locks are held
DO $$
DECLARE
    c_cm bigint;
    c_prj bigint;
    c_ps bigint;
    c_tsk bigint;
    c_ts bigint;
BEGIN
    SELECT count(*) INTO c_cm FROM public.contact_message;
    SELECT count(*) INTO c_prj FROM public.projects;
    SELECT count(*) INTO c_ps FROM public.project_submission;
    SELECT count(*) INTO c_tsk FROM public.tasks;
    SELECT count(*) INTO c_ts FROM public.task_submission;

    IF c_cm > 0 THEN 
        RAISE EXCEPTION 'Precondition Failed: public.contact_message contains % rows (expected 0). Aborting.', c_cm; 
    END IF;
    IF c_prj > 0 THEN 
        RAISE EXCEPTION 'Precondition Failed: public.projects contains % rows (expected 0). Aborting.', c_prj; 
    END IF;
    IF c_ps > 0 THEN 
        RAISE EXCEPTION 'Precondition Failed: public.project_submission contains % rows (expected 0). Aborting.', c_ps; 
    END IF;
    IF c_tsk > 0 THEN 
        RAISE EXCEPTION 'Precondition Failed: public.tasks contains % rows (expected 0). Aborting.', c_tsk; 
    END IF;
    IF c_ts > 0 THEN 
        RAISE EXCEPTION 'Precondition Failed: public.task_submission contains % rows (expected 0). Aborting.', c_ts; 
    END IF;
END $$;

-- 5. Assert all 5 target columns exist and currently have type 'oid'
DO $$
DECLARE
    rec RECORD;
    found_count integer := 0;
BEGIN
    FOR rec IN 
        SELECT table_name, column_name, udt_name 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND (
            (table_name = 'contact_message' AND column_name = 'message') OR
            (table_name = 'projects' AND column_name = 'project_brief') OR
            (table_name = 'project_submission' AND column_name = 'project_feedback') OR
            (table_name = 'tasks' AND column_name = 'task_description') OR
            (table_name = 'task_submission' AND column_name = 'task_feedback')
          )
    LOOP
        found_count := found_count + 1;
        IF rec.udt_name <> 'oid' THEN
            RAISE EXCEPTION 'Precondition Failed: %.% is % (expected oid). Aborting.', rec.table_name, rec.column_name, rec.udt_name;
        END IF;
    END LOOP;

    IF found_count <> 5 THEN
        RAISE EXCEPTION 'Precondition Failed: Found only % of 5 target columns in catalog. Aborting.', found_count;
    END IF;
END $$;

-- 6. Execute DDL with USING NULL::text (Safe strictly for locked empty tables)
-- Preserves NOT NULL constraints where defined in JPA entity specifications

-- Table 1: contact_message (message: NOT NULL)
ALTER TABLE public.contact_message 
    ALTER COLUMN message TYPE TEXT USING NULL::text,
    ALTER COLUMN message SET NOT NULL;

-- Table 2: projects (project_brief: NOT NULL)
ALTER TABLE public.projects 
    ALTER COLUMN project_brief TYPE TEXT USING NULL::text,
    ALTER COLUMN project_brief SET NOT NULL;

-- Table 3: project_submission (project_feedback: nullable)
ALTER TABLE public.project_submission 
    ALTER COLUMN project_feedback TYPE TEXT USING NULL::text;

-- Table 4: tasks (task_description: NOT NULL)
ALTER TABLE public.tasks 
    ALTER COLUMN task_description TYPE TEXT USING NULL::text,
    ALTER COLUMN task_description SET NOT NULL;

-- Table 5: task_submission (task_feedback: nullable)
ALTER TABLE public.task_submission 
    ALTER COLUMN task_feedback TYPE TEXT USING NULL::text;

-- 7. Post-Conversion Assertions: Verify types are text and tables remain empty
DO $$
DECLARE
    rec RECORD;
    verified_count integer := 0;
    c_cm bigint;
    c_prj bigint;
    c_ps bigint;
    c_tsk bigint;
    c_ts bigint;
    c_roles bigint;
    c_users bigint;
    c_students bigint;
BEGIN
    -- Verify row counts remain 0 on affected tables
    SELECT count(*) INTO c_cm FROM public.contact_message;
    SELECT count(*) INTO c_prj FROM public.projects;
    SELECT count(*) INTO c_ps FROM public.project_submission;
    SELECT count(*) INTO c_tsk FROM public.tasks;
    SELECT count(*) INTO c_ts FROM public.task_submission;

    IF (c_cm + c_prj + c_ps + c_tsk + c_ts) > 0 THEN
        RAISE EXCEPTION 'Post-condition Failed: Affected tables are no longer empty after conversion. Rolling back.';
    END IF;

    -- Verify existing records in other tables are completely unharmed
    SELECT count(*) INTO c_roles FROM public.roles;
    SELECT count(*) INTO c_users FROM public.users;
    SELECT count(*) INTO c_students FROM public.students;

    IF c_roles <> 3 THEN
        RAISE EXCEPTION 'Post-condition Failed: roles table count drifted to % (expected 3). Rolling back.', c_roles;
    END IF;
    IF c_users <> 1 THEN
        RAISE EXCEPTION 'Post-condition Failed: users table count drifted to % (expected 1). Rolling back.', c_users;
    END IF;
    IF c_students <> 1 THEN
        RAISE EXCEPTION 'Post-condition Failed: students table count drifted to % (expected 1). Rolling back.', c_students;
    END IF;

    -- Verify all 5 columns are now 'text'
    FOR rec IN 
        SELECT table_name, column_name, udt_name 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND (
            (table_name = 'contact_message' AND column_name = 'message') OR
            (table_name = 'projects' AND column_name = 'project_brief') OR
            (table_name = 'project_submission' AND column_name = 'project_feedback') OR
            (table_name = 'tasks' AND column_name = 'task_description') OR
            (table_name = 'task_submission' AND column_name = 'task_feedback')
          )
    LOOP
        IF rec.udt_name <> 'text' THEN
            RAISE EXCEPTION 'Post-condition Failed: %.% is % (expected text). Rolling back.', rec.table_name, rec.column_name, rec.udt_name;
        END IF;
        verified_count := verified_count + 1;
    END LOOP;

    IF verified_count <> 5 THEN
        RAISE EXCEPTION 'Post-condition Failed: Expected 5 text columns, found %. Rolling back.', verified_count;
    END IF;
END $$;

COMMIT;
