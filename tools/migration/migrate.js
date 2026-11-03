/**
 * EduNest EB-82 MySQL to PostgreSQL Migration Engine
 * 
 * Production-grade, dependency-aware migration script with semantic roles resolution,
 * explicit column mapping, data type coercion, and sequence synchronization.
 */

// 1. Dependency-Aware Table Order (Tier 0 to Tier 7)
const TABLE_PIPELINE = [
    // Tier 0: Root tables (no foreign keys)
    'roles',
    'badges',
    'admin_badges',
    'contact_message',
    'certificates',
    'conversations',

    // Tier 1: User base
    'users',

    // Tier 2: Sub-entities & User-dependent tables
    'admin',
    'student',
    'mentor',
    'user_notification',
    'admin_notification',
    'badge_awards',
    'user_admin_badges',
    'chat_messages',
    'user_device_tokens',
    'mentor_applications',
    'activity_logs',

    // Tier 3: Mentorship, Tracks & Sessions
    'tracks',
    'mentor_availability',
    'mentor_skills',
    'user_skills',
    'mentor_reviews',
    'group_sessions',
    'one_on_one_sessions',
    'session_attendance',

    // Tier 4: Enrollments & Weeks
    'enrollments',
    'weeks',
    'payment_transactions',

    // Tier 5: Curriculum & Progress
    'projects',
    'tasks',
    'quiz',
    'learning_materials',
    'week_progress',

    // Tier 6: Submissions & Assessments
    'project_submission',
    'task_submission',
    'quiz_submission',
    'questions',
    'total_points',
    'leaderboard',

    // Tier 7: Leaf Question Options & Answers
    'options',
    'student_answers'
];

// 2. Column Mapping & Type Transformations
const COLUMN_TRANSFORMS = {
    contact_message: {
        message: (val) => String(val ?? '')
    },
    projects: {
        project_brief: (val) => String(val ?? '')
    },
    project_submission: {
        project_feedback: (val) => val != null ? String(val) : null
    },
    tasks: {
        task_description: (val) => String(val ?? '')
    },
    task_submission: {
        task_feedback: (val) => val != null ? String(val) : null
    },
    enrollments: {
        platform_profit: (val) => val != null ? parseFloat(val) : 0.0
    }
};

/**
 * Reconcile semantic roles between MySQL and PostgreSQL.
 * Never blindly overwrite role semantics by ID.
 */
async function resolveRoles(mysqlConn, pgClient) {
    console.log('Resolving semantic roles between MySQL source and PostgreSQL target...');

    const [mysqlRoles] = await mysqlConn.query('SELECT id, name FROM roles ORDER BY id');
    const pgRes = await pgClient.query('SELECT id, name FROM public.roles ORDER BY id');
    const pgRoles = pgRes.rows;

    const pgRoleByName = new Map();
    for (const r of pgRoles) {
        pgRoleByName.set(r.name.trim().toUpperCase(), r.id);
    }

    const roleIdMap = new Map(); // sourceId -> targetId
    for (const sRole of mysqlRoles) {
        const sName = sRole.name.trim().toUpperCase();
        if (pgRoleByName.has(sName)) {
            const targetId = pgRoleByName.get(sName);
            roleIdMap.set(sRole.id, targetId);
            console.log(`Mapped role ${sName}: MySQL ID ${sRole.id} -> PG ID ${targetId}`);
        } else {
            // Insert missing role into target
            const insertRes = await pgClient.query(
                'INSERT INTO public.roles (name) VALUES ($1) RETURNING id',
                [sRole.name.trim()]
            );
            const newId = insertRes.rows[0].id;
            roleIdMap.set(sRole.id, newId);
            console.log(`Created missing role ${sName} in PG with ID ${newId}`);
        }
    }

    return roleIdMap;
}

/**
 * Synchronize auto-increment identity sequences for all tables
 */
async function syncSequences(pgClient) {
    console.log('Synchronizing PostgreSQL identity sequences...');
    const query = `
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
                END IF;
            END LOOP;
        END $$;
    `;
    await pgClient.query(query);
    console.log('Sequence synchronization completed.');
}

module.exports = {
    TABLE_PIPELINE,
    COLUMN_TRANSFORMS,
    resolveRoles,
    syncSequences
};
