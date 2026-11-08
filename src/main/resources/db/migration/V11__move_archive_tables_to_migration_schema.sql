-- Archive tables are retained for recovery; Flyway history remains in public.
-- Fail closed on missing, misplaced, or conflicting objects.
DO $$
DECLARE
    archive_name text;
    source_oid oid;
    original_owner oid;
    original_acl aclitem[];
    archive_names text[] := ARRAY[
        'eb44_archive_enrollments', 'eb44_archive_mentorship_reviews',
        'eb44_archive_conversations', 'eb44_archive_messages',
        'eb51_archive_session_attendance_result'
    ];
BEGIN
    FOREACH archive_name IN ARRAY archive_names LOOP
        source_oid := to_regclass(format('public.%I', archive_name));
        IF source_oid IS NULL OR NOT EXISTS (
            SELECT 1 FROM pg_class WHERE oid = source_oid AND relkind = 'r'
        ) THEN
            RAISE EXCEPTION 'V11 aborted: expected ordinary table public.%', archive_name;
        END IF;
        IF to_regclass(format('migration_archive.%I', archive_name)) IS NOT NULL THEN
            RAISE EXCEPTION 'V11 aborted: target migration_archive.% already exists', archive_name;
        END IF;
    END LOOP;

    CREATE SCHEMA IF NOT EXISTS migration_archive;

    FOREACH archive_name IN ARRAY archive_names LOOP
        SELECT c.oid, c.relowner, c.relacl INTO source_oid, original_owner, original_acl
        FROM pg_class c WHERE c.oid = to_regclass(format('public.%I', archive_name));
        EXECUTE format('ALTER TABLE public.%I SET SCHEMA migration_archive', archive_name);
        IF to_regclass(format('public.%I', archive_name)) IS NOT NULL
           OR to_regclass(format('migration_archive.%I', archive_name)) IS DISTINCT FROM source_oid
           OR NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.oid = source_oid
                          AND c.relowner = original_owner AND c.relacl IS NOT DISTINCT FROM original_acl) THEN
            RAISE EXCEPTION 'V11 aborted: relocation verification failed for %', archive_name;
        END IF;
    END LOOP;
END $$;
