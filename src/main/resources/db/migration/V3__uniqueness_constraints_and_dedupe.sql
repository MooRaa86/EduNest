-- ============================================================================
-- EduNest Database Migration (Flyway V3)
-- Task: EB-44 [P3-02] Uniqueness constraints with dedupe + conflict UX
--       Closes DB-04 (+ MSH-04 enrollments, reviews; CHAT-05 conversations).
-- Dialect: PostgreSQL 17.x (Supabase)
--
-- SECTION A  Deterministic dedupe with full archival (losing rows are never
--            destroyed; every removed row is copied verbatim, timestamps kept,
--            into eb44_archive_* tables adding only archived_at + archived_by).
-- SECTION B  Conversation hygiene: rows with incomplete (NULL) participants are
--            NOT silently merged; they and their messages are archived and
--            removed, after which both participant columns become NOT NULL.
-- SECTION C  Pair dedupe for conversations (unordered-pair identity), with
--            message reassignment to the winning conversation.
-- SECTION D  Uniqueness DDL + redundant-index cleanup.
-- SECTION E  Transactional post-migration verification (fails the migration).
--
-- Winner rule (business-approved plan rule, applied ONLY where the schema
-- allows deterministic picks): the EARLIEST row (smallest id) of each duplicate
-- group survives. Enrollments carry joined_at on the earliest row; review
-- updates mutate the surviving row in place (rateMentorship upsert semantics);
-- conversation winners keep the earliest conversation id. Diverging business
-- data from losers is preserved in the archive tables, never discarded.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- SECTION A.0 -- one-time FBI-style archive surfaces (plain heap copies;
-- LIKE copies columns, nullability and defaults but NOT identity, PK, FK or
-- index definitions, so archive ids stay literal historical values).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS eb44_archive_enrollments  (LIKE enrollments        INCLUDING DEFAULTS);
CREATE TABLE IF NOT EXISTS eb44_archive_mentorship_reviews (LIKE mentorship_reviews INCLUDING DEFAULTS);
CREATE TABLE IF NOT EXISTS eb44_archive_conversations (LIKE conversations    INCLUDING DEFAULTS);
CREATE TABLE IF NOT EXISTS eb44_archive_messages      (LIKE messages         INCLUDING DEFAULTS);

ALTER TABLE eb44_archive_enrollments
    ADD COLUMN IF NOT EXISTS archived_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS archived_by varchar(255) NOT NULL DEFAULT 'EB-44-V3';
ALTER TABLE eb44_archive_mentorship_reviews
    ADD COLUMN IF NOT EXISTS archived_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS archived_by varchar(255) NOT NULL DEFAULT 'EB-44-V3';
ALTER TABLE eb44_archive_conversations
    ADD COLUMN IF NOT EXISTS archived_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS archived_by varchar(255) NOT NULL DEFAULT 'EB-44-V3';
ALTER TABLE eb44_archive_messages
    ADD COLUMN IF NOT EXISTS archived_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS archived_by varchar(255) NOT NULL DEFAULT 'EB-44-V3';

-- ---------------------------------------------------------------------------
-- SECTION A.1 -- enrollments dedupe: duplicate (student_id, mentorship_id)
-- groups are collapsed to their earliest row; losers archived first.
-- Zero FK references exist into enrollments (verified against V1), so removal
-- of losers cannot orphan anything.
-- ---------------------------------------------------------------------------
INSERT INTO eb44_archive_enrollments (id, platform_profit, price, created_at, joined_at,
                                      mentorship_id, student_id, updated_at,
                                      created_by, updated_by, archived_at)
SELECT e.id, e.platform_profit, e.price, e.created_at, e.joined_at,
       e.mentorship_id, e.student_id, e.updated_at,
       e.created_by, e.updated_by, now()
FROM enrollments e
WHERE EXISTS (
    SELECT 1 FROM enrollments d
    WHERE d.student_id = e.student_id
      AND d.mentorship_id = e.mentorship_id
      AND d.id < e.id
);

DELETE FROM enrollments e
WHERE EXISTS (
    SELECT 1 FROM enrollments d
    WHERE d.student_id = e.student_id
      AND d.mentorship_id = e.mentorship_id
      AND d.id < e.id
);

-- ---------------------------------------------------------------------------
-- SECTION A.2 -- mentorship_reviews dedupe: duplicate
-- (mentorship_id, student_id) pairs collapse to the earliest row. The pair
-- columns are nullable in the baseline, so grouping uses IS NOT DISTINCT FROM
-- so that identical NULL/NULL degenerate rows also collapse deterministically
-- (nothing is silently merged: full rows are archived first).
-- ---------------------------------------------------------------------------
INSERT INTO eb44_archive_mentorship_reviews (id, feed_back, created_at, rating,
                                             mentorship_id, student_id, updated_at,
                                             created_by, updated_by, archived_at)
SELECT r.id, r.feed_back, r.created_at, r.rating,
       r.mentorship_id, r.student_id, r.updated_at,
       r.created_by, r.updated_by, now()
FROM mentorship_reviews r
WHERE EXISTS (
    SELECT 1 FROM mentorship_reviews d
    WHERE d.mentorship_id IS NOT DISTINCT FROM r.mentorship_id
      AND d.student_id     IS NOT DISTINCT FROM r.student_id
      AND d.id < r.id
);

DELETE FROM mentorship_reviews r
WHERE EXISTS (
    SELECT 1 FROM mentorship_reviews d
    WHERE d.mentorship_id IS NOT DISTINCT FROM r.mentorship_id
      AND d.student_id     IS NOT DISTINCT FROM r.student_id
      AND d.id < r.id
);

-- ---------------------------------------------------------------------------
-- SECTION B -- conversations with incomplete participants.
-- Both participants are always written by ConversationService (the only
-- creation path); a NULL participant therefore marks an invalid row. DE-44
-- rule (no silent merging, NULL participant data is not valid data): exactly
-- one duplicate group can never exist for such rows, so instead of forcing a
-- canonical form on garbage, the full conversation row AND every message that
-- belonged to it are archived verbatim before removal. This preserves all
-- evidence while allowing a genuine NOT NULL invariant.
-- ---------------------------------------------------------------------------
INSERT INTO eb44_archive_messages
       (seen, updated, conversation_id, id, sender_id, sent_at, content, archived_at)
SELECT m.seen, m.updated, m.conversation_id, m.id, m.sender_id, m.sent_at, m.content, now()
FROM messages m
JOIN conversations c ON c.id = m.conversation_id
WHERE c.user1_id IS NULL OR c.user2_id IS NULL;

INSERT INTO eb44_archive_conversations (created_at, id, user1_id, user2_id, archived_at)
SELECT c.created_at, c.id, c.user1_id, c.user2_id, now()
FROM conversations c
WHERE c.user1_id IS NULL OR c.user2_id IS NULL;

DELETE FROM messages m
USING conversations c
WHERE c.id = m.conversation_id
  AND (c.user1_id IS NULL OR c.user2_id IS NULL);

DELETE FROM conversations c
WHERE c.user1_id IS NULL OR c.user2_id IS NULL;

-- Nullability safeguard (now provably safe: no NULL-participant row remains).
ALTER TABLE conversations ALTER COLUMN user1_id SET NOT NULL;
ALTER TABLE conversations ALTER COLUMN user2_id SET NOT NULL;

-- ---------------------------------------------------------------------------
-- SECTION C -- unordered conversation-pair dedupe (CHAT-05).
-- Identity of a conversation is the UNORDERED participant pair
-- (LEAST, GREATEST). Loser = any row sharing the same unordered pair as an
-- earlier row. All messages of losers are first re-pointed at the winner so
-- chat history is preserved verbatim (message ids, sent_at, content, seen
-- flags unchanged). Loser rows are then archived and only afterwards removed.
-- ---------------------------------------------------------------------------
WITH losers AS (
    SELECT l.id, l.user1_id, l.user2_id,
           (SELECT w.id FROM conversations w
            WHERE LEAST(w.user1_id, w.user2_id) IS NOT DISTINCT FROM LEAST(l.user1_id, l.user2_id)
              AND GREATEST(w.user1_id, w.user2_id) IS NOT DISTINCT FROM GREATEST(l.user1_id, l.user2_id)
            ORDER BY w.id
            LIMIT 1) AS winner_id
    FROM conversations l
    WHERE EXISTS (
        SELECT 1 FROM conversations d
        WHERE d.id < l.id
          AND LEAST(d.user1_id, d.user2_id) IS NOT DISTINCT FROM LEAST(l.user1_id, l.user2_id)
          AND GREATEST(d.user1_id, d.user2_id) IS NOT DISTINCT FROM GREATEST(l.user1_id, l.user2_id))
)
UPDATE messages m
SET conversation_id = losers.winner_id
FROM losers
WHERE m.conversation_id = losers.id;

INSERT INTO eb44_archive_conversations (created_at, id, user1_id, user2_id, archived_at)
SELECT c.created_at, c.id, c.user1_id, c.user2_id, now()
FROM conversations c
WHERE EXISTS (
    SELECT 1 FROM conversations d
    WHERE d.id < c.id
      AND LEAST(d.user1_id, d.user2_id) IS NOT DISTINCT FROM LEAST(c.user1_id, c.user2_id)
      AND GREATEST(d.user1_id, d.user2_id) IS NOT DISTINCT FROM GREATEST(c.user1_id, c.user2_id));

DELETE FROM conversations c
WHERE EXISTS (
    SELECT 1 FROM conversations d
    WHERE d.id < c.id
      AND LEAST(d.user1_id, d.user2_id) IS NOT DISTINCT FROM LEAST(c.user1_id, c.user2_id)
      AND GREATEST(d.user1_id, d.user2_id) IS NOT DISTINCT FROM GREATEST(c.user1_id, c.user2_id));

-- ---------------------------------------------------------------------------
-- SECTION D -- uniqueness DDL and redundant-index cleanup.
-- ---------------------------------------------------------------------------
-- MSH-04: one enrollment per student per mentorship. The pre-existing
-- non-unique pair index is consumed by the new constraint (its job is taken
-- over by the unique index); the mentor-side single-column index is ADDED
-- because PostgreSQL FKs do not auto-index and mentor-scoped queries/
-- repository joins probe mentorship_id alone.
-- Index DDL is idempotent (IF NOT EXISTS) so that the adoption runbook and
-- deployment tooling can re-run this migration safely against an environment
-- where Flyway state and live DDL may have diverged.
CREATE UNIQUE INDEX IF NOT EXISTS uk_enrollment_student_mentorship
    ON enrollments (student_id, mentorship_id);

CREATE INDEX IF NOT EXISTS idx_enrollments_mentorship
    ON enrollments (mentorship_id);

DROP INDEX IF EXISTS idx_enrollments_student_mentorship;

-- One review per student per mentorship (rateMentorship upsert semantics).
CREATE UNIQUE INDEX IF NOT EXISTS uk_mentorship_review_student
    ON mentorship_reviews (student_id, mentorship_id);

DROP INDEX IF EXISTS idx_mentorship_student;

-- CHAT-05: unordered pair uniqueness on surviving (complete) conversations.
-- Functional expression index: enforces pair uniqueness regardless of which
-- side stored user1/user2.
CREATE UNIQUE INDEX IF NOT EXISTS uk_conversation_unordered_pair
    ON conversations (LEAST(user1_id, user2_id), GREATEST(user1_id, user2_id));

-- ---------------------------------------------------------------------------
-- SECTION E -- post-migration verification (runs in the SAME Flyway managed
-- transaction; any failure aborts and rolls V3 back entirely).
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    v_enroll_pair_dupes   bigint;
    v_review_pair_dupes   bigint;
    v_conversation_dupes  bigint;
    v_null_participants   bigint;
BEGIN
    SELECT count(*) INTO v_enroll_pair_dupes FROM (
        SELECT 1 FROM enrollments
        GROUP BY student_id, mentorship_id HAVING count(*) > 1) t;

    SELECT count(*) INTO v_review_pair_dupes FROM (
        SELECT 1 FROM mentorship_reviews
        GROUP BY mentorship_id, student_id HAVING count(*) > 1) t;

    SELECT count(*) INTO v_conversation_dupes FROM (
        SELECT 1 FROM conversations
        GROUP BY LEAST(user1_id, user2_id), GREATEST(user1_id, user2_id)
        HAVING count(*) > 1) t;

    SELECT count(*) INTO v_null_participants
    FROM conversations WHERE user1_id IS NULL OR user2_id IS NULL;

    IF v_enroll_pair_dupes <> 0 THEN
        RAISE EXCEPTION 'EB-44 V3 verification: duplicate enrollments remain (%)', v_enroll_pair_dupes;
    END IF;
    IF v_review_pair_dupes <> 0 THEN
        RAISE EXCEPTION 'EB-44 V3 verification: duplicate reviews remain (%)', v_review_pair_dupes;
    END IF;
    IF v_conversation_dupes <> 0 THEN
        RAISE EXCEPTION 'EB-44 V3 verification: duplicate conversations remain (%)', v_conversation_dupes;
    END IF;
    IF v_null_participants <> 0 THEN
        RAISE EXCEPTION 'EB-44 V3 verification: NULL-participant conversations remain (%)', v_null_participants;
    END IF;
    IF to_regclass('uk_conversation_unordered_pair') IS NULL THEN
        RAISE EXCEPTION 'EB-44 V3 verification: uk_conversation_unordered_pair missing';
    END IF;
END $$;
