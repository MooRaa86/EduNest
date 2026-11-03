-- ============================================================================
-- EduNest Database Migration (Flyway V2)
-- Task: EB-59 [P4-10] / EB-43 [P3-01] Mentorship explore trigram & performance indexes
-- Dialect: PostgreSQL 17.x
-- ============================================================================

-- 1. Enable pg_trgm extension (standard contrib extension in PostgreSQL/Supabase)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. Create GIN index on text search target columns in mentorship table
-- Accelerates substring / trigram searches (LIKE '%keyword%') on explore route
CREATE INDEX IF NOT EXISTS idx_mentorship_explore_trgm
ON mentorship USING gin (
    title gin_trgm_ops,
    subtitle gin_trgm_ops,
    description gin_trgm_ops,
    category gin_trgm_ops
);

-- 3. Composite B-Tree index for explore filter criteria (status + price + discount)
-- Supports index filtering for status = 'ACTIVE' and price range checks (MSH-07)
CREATE INDEX IF NOT EXISTS idx_mentorship_active_price
ON mentorship (status, price, discount_percentage);

-- 4. Expression GIN index on mentor's full name in the users table
-- Enables accelerated search when matching mentor full name CONCAT(firstName, ' ', lastName)
CREATE INDEX IF NOT EXISTS idx_users_mentor_name_trgm
ON users USING gin ((first_name || ' ' || last_name) gin_trgm_ops);
