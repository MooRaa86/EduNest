-- =====================================================================
-- Migration V15: Private Conversation Message Public UUID Identifiers
-- Purpose: Extend the EB-109 public UUID convention to the private 1:1
--          `messages` table so both chat surfaces (cohort rooms and
--          private conversations) expose opaque UUIDs to clients.
--          V6 covered `chat_messages` but intentionally skipped
--          `messages`, so this migration is purely additive.
-- Scope:   ADD COLUMN + UNIQUE INDEX only. The internal bigint identity
--          primary key `messages.id` is untouched: foreign keys and the
--          keyset plan (`m.id < :beforeId`) stay on the internal id.
--          Existing rows are backfilled by the column DEFAULT.
-- Note:    V13 remains RESERVED for the test-only Flyway startup
--          sentinel (src/test/resources/startup-migrations) and must
--          never be used by a real migration.
-- =====================================================================

ALTER TABLE messages ADD COLUMN IF NOT EXISTS public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_public_id ON messages(public_id);
