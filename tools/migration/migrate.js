/**
 * EduNest EB-82 Schema Reconciliation & DDL Specification Tool
 *
 * Defines the authoritative 42-table public application schema for PostgreSQL,
 * the 5 TEXT column conversions (eliminating OID), and the Hibernate schema validation settings.
 *
 * Note: Per Project Owner directive (2026-11-03), all MySQL data transfer has been
 * cancelled. This tool is strictly for schema reconciliation and verification.
 */

// 1. Authoritative 42 Public Application Tables (Schema-Only Scope)
const APPLICATION_TABLES = [
    'admin',
    'admin_badges',
    'admin_notification',
    'badge_awards',
    'badges',
    'certificates',
    'chat_messages',
    'chat_room_members',
    'chat_rooms',
    'commission',
    'contact_message',
    'conversations',
    'enrollments',
    'lectures',
    'mentors',
    'mentorship',
    'mentorship_reviews',
    'mentorship_what_will_learn',
    'messages',
    'notification',
    'otp',
    'project_submission',
    'projects',
    'questions',
    'quiz',
    'quiz_submission',
    'roles',
    'session_attendance',
    'session_attendance_result',
    'sessions',
    'social_media',
    'student_answer',
    'student_skill',
    'students',
    'tags',
    'task_submission',
    'tasks',
    'total_points',
    'user_admin_badges',
    'user_notification',
    'users',
    'weeks'
];

// 2. The Five Text Columns to Reconcile from OID to TEXT
const TEXT_COLUMNS_TO_CONVERT = [
    { table: 'contact_message', column: 'message', notNull: true },
    { table: 'projects', column: 'project_brief', notNull: true },
    { table: 'project_submission', column: 'project_feedback', notNull: false },
    { table: 'tasks', column: 'task_description', notNull: true },
    { table: 'task_submission', column: 'task_feedback', notNull: false }
];

/**
 * Generate reviewed, non-destructive DDL for empty target tables.
 * Uses USING col::text for empty tables, or defensive convert_from(lo_get(...), 'UTF8')
 */
function generateCorrectiveDdl(contentPreserving = true) {
    const lines = [
        '-- ====================================================================',
        '-- EduNest EB-82: Non-Destructive OID to TEXT Schema Alignment',
        '-- Target: Supabase PostgreSQL (schema: public)',
        '-- Precondition: Tables are empty (0 rows) or contain verified OID LOBs',
        '-- ====================================================================',
        'BEGIN;'
    ];

    for (const c of TEXT_COLUMNS_TO_CONVERT) {
        if (contentPreserving) {
            lines.push(`ALTER TABLE public.${c.table} ALTER COLUMN ${c.column} TYPE TEXT USING (CASE WHEN ${c.column} IS NOT NULL THEN convert_from(lo_get(${c.column}), 'UTF8') ELSE NULL END);`);
        } else {
            lines.push(`ALTER TABLE public.${c.table} ALTER COLUMN ${c.column} TYPE TEXT USING ${c.column}::text;`);
        }
    }

    lines.push('COMMIT;');
    return lines.join('\n');
}

module.exports = {
    APPLICATION_TABLES,
    TEXT_COLUMNS_TO_CONVERT,
    generateCorrectiveDdl
};
