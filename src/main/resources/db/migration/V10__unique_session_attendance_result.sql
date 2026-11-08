-- ============================================================================
-- EduNest Database Migration (Flyway V9)
-- Task: EB-51 Points Ledger Integrity — Session Attendance Result Uniqueness
-- Dialect: PostgreSQL 17.x (Supabase)
--
-- SECTION A: Archive surface creation for authorized administrative remediation
-- SECTION B: Pre-migration duplicate check (FAIL-CLOSED: NO SILENT DEDUPLICATION)
-- SECTION C: Unique constraint on (session_id, student_id)
-- SECTION D: Transactional post-migration verification
-- ============================================================================

-- SECTION A: Archive surface for session attendance results
CREATE TABLE IF NOT EXISTS eb51_archive_session_attendance_result (LIKE session_attendance_result INCLUDING DEFAULTS);

ALTER TABLE eb51_archive_session_attendance_result
    ADD COLUMN IF NOT EXISTS archived_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS archived_by varchar(255) NOT NULL DEFAULT 'EB-51-V9';

-- SECTION B: Pre-migration duplicate check (FAIL-CLOSED GUARD)
-- Per EB-51 Architecture Review: Migration MUST NOT silently delete or mutate historical data.
-- If duplicates exist, migration fails closed with an explicit deployment blocker exception,
-- requiring authorized administrative data remediation before the unique index can be applied.
DO $$
DECLARE
    v_dupe_count bigint;
BEGIN
    SELECT count(*) INTO v_dupe_count FROM (
        SELECT session_id, student_id FROM session_attendance_result
        GROUP BY session_id, student_id HAVING count(*) > 1) t;

    IF v_dupe_count > 0 THEN
        RAISE EXCEPTION 'EB-51 V9 deployment blocked: session_attendance_result contains % duplicate (session_id, student_id) pairs. Silent deduplication of production data is strictly prohibited. An authorized data remediation plan must be approved and executed before creating the unique constraint.', v_dupe_count;
    END IF;
END $$;

-- SECTION C: Unique index enforcing one evaluation per student per session
CREATE UNIQUE INDEX IF NOT EXISTS uk_session_attendance_result_session_student
    ON session_attendance_result (session_id, student_id);

-- SECTION D: Transactional post-migration verification
DO $$
DECLARE
    v_dupes bigint;
BEGIN
    SELECT count(*) INTO v_dupes FROM (
        SELECT 1 FROM session_attendance_result
        GROUP BY session_id, student_id HAVING count(*) > 1) t;

    IF v_dupes <> 0 THEN
        RAISE EXCEPTION 'EB-51 V9 verification: duplicate session attendance results remain (%)', v_dupes;
    END IF;
    IF to_regclass('uk_session_attendance_result_session_student') IS NULL THEN
        RAISE EXCEPTION 'EB-51 V9 verification: uk_session_attendance_result_session_student index missing';
    END IF;
END $$;
