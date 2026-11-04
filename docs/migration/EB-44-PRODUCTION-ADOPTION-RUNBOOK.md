# EB-44 Production Adoption Runbook — [P3-02] Uniqueness Constraints (V3)

**Task:** EB-44 `[P3-02] Uniqueness constraints with dedupe + conflict UX` (closes DB-04, +MSH-04, +CHAT-05)
**Migration:** `V3__uniqueness_constraints_and_dedupe.sql` (PostgreSQL 17.x / Supabase, schema `public`)
**Status in origin/main after merge:** migration file EXISTS but Flyway **has not applied it to production** — it is applied on the next real app boot of the migrated instance, or manually via the steps below. The registry (`db/migration/README.md`) intentionally keeps `V3` as **RESERVED (BUILD COMPLETE — pending production adoption)**.

**Milestones — keep all three separate at all times (merging proves none of the others):**

| # | Milestone | Evidence |
|---|-----------|----------|
| M1 | **Code merged to `main`** | merge commit + green CI, including the two dedicated EB-44 PostgreSQL suites |
| M2 | **V3 executed on a disposable PostgreSQL instance** | EB-44 suites passing on throwaway containers (dedupe + conflict UX proven on real PostgreSQL) — proves the CODE PATH only, changes nothing real |
| M3 | **V3 applied to the production database** | `flyway_schema_history` row `(3, 'uniqueness constraints and dedupe', true)` + the §5 post-adopt checks on the live database |

---

## 1. What V3 does (and what it never does)

Ordered phases inside the single transaction Flyway manages:

1. **Archive surfaces** — `CREATE TABLE IF NOT EXISTS eb44_archive_enrollments | mentorship_reviews | conversations | messages` (`LIKE ... INCLUDING DEFAULTS`, ids preserved literally) plus `archived_at timestamptz NOT NULL DEFAULT now()` / `archived_by varchar(255) NOT NULL DEFAULT 'EB-44-V3'`.
2. **Enrollments dedupe** — duplicate `(student_id, mentorship_id)` rows: every row that has an earlier sibling is copied verbatim into `eb44_archive_enrollments`, then deleted. Winner = smallest `id` (carries the original `joined_at`).
3. **Reviews dedupe** — duplicate `(mentorship_id, student_id)` pairs (`IS NOT DISTINCT FROM`, so identical NULL/NULL degenerate rows collapse too): losers archived, then deleted.
4. **Conversation hygiene** — rows with a NULL participant (invalid data; `ConversationService` is the only creation path and always writes both) and **every message belonging to them** are archived, then removed; after which `conversations.user1_id` / `user2_id` become `NOT NULL`.
   **Archival ≠ availability:** after V3, that archived conversation history is **NOT visible in the live application** — chat endpoints only read the live tables, there is no archive-browsing UI, and no automatic restore path exists. The data survives in `eb44_archive_*` for a DBA-driven manual restore only (approval gate G2).
5. **Conversation pair dedupe (CHAT-05)** — identity is the unordered pair `(LEAST(user1_id,user2_id), GREATEST(user1_id,user2_id))`. Loser conversations first hand **all their messages** to the winner (ids, timestamps, seen flags untouched), then are archived and deleted.
6. **DDL** —
   - `CREATE UNIQUE INDEX IF NOT EXISTS uk_enrollment_student_mentorship ON enrollments (student_id, mentorship_id)`
   - `CREATE INDEX IF NOT EXISTS idx_enrollments_mentorship` (FK probe; PG does not auto-index FKs)
   - drop obsolete `idx_enrollments_student_mentorship`, `idx_mentorship_student`
   - `CREATE UNIQUE INDEX IF NOT EXISTS uk_mentorship_review_student ON mentorship_reviews (student_id, mentorship_id)`
   - `CREATE UNIQUE INDEX IF NOT EXISTS uk_conversation_unordered_pair ON conversations (LEAST(user1_id, user2_id), GREATEST(user1_id, user2_id))` (functional expression index)
7. **Verification block** (`DO $$ … $$`, same Flyway transaction — any failure rolls V3 back completely): duplicate counts for all three pairs must be 0, NULL-participant count 0, and the conversation unique index must exist.

**Never-destroy doctrine:** the migration cannot permanently shred losing rows — removal is preceded by (and coupled to) an archive insert in the same transaction. Business data divergence from losers is recoverable from the archive tables, never discarded.

---

## 2. Application-coupled behavior shipped with this migration

- `api/v1/mentorship/{id}/join` and `/rate`, on a true constraint race (or, for review/rate, any duplicate pair reaching the insert), now return **HTTP 409 CONFLICT** with envelope code **`ERR_DUPLICATE_RESOURCE`** and clean FE messages ("You are already enrolled in this mentorship." / "You have already reviewed this mentorship." / "A conversation with this recipient already exists."). Previously 400 `ERR_BUSINESS_RULE`.
- `ConversationService` performs the conversation get-or-create inside a `TransactionTemplate` short transaction; a unique-violation race re-fetches the winner conversation and persists the message (chat push is idempotent at the pair level, CHAT-05).
- Duplicate detection at the handler is **SQLSTATE 23505-aware** (PostgreSQL and H2) and maps constraint names to messages; all other integrity violations remain 400 `ERR_CONSTRAINT_VIOLATION`.
- Entities updated accordingly: `Enrollment` / `MentorShipReviews` declare the unique constraints; `Conversation.user1/user2` are `@ManyToOne(optional = false)`.

## 3. Pre-adopt checklist (read-only)

1. Confirm old schema state and counts (Supabase SQL editor):
   ```sql
   SELECT current_database(), current_schema(), version();
   SELECT count(*) FROM (
       SELECT 1 FROM enrollments GROUP BY student_id, mentorship_id HAVING count(*) > 1) e;
   SELECT count(*) FROM (
       SELECT 1 FROM mentorship_reviews GROUP BY mentorship_id, student_id HAVING count(*) > 1) r;
   SELECT count(*) FROM conversations WHERE user1_id IS NULL OR user2_id IS NULL;
   SELECT count(*) FROM (
       SELECT 1 FROM conversations c
       GROUP BY LEAST(c.user1_id, c.user2_id), GREATEST(c.user1_id, c.user2_id)
       HAVING count(*) > 1) cv;
   ```
   These numbers must match what the archive tables will receive in step 5 ( losers `count` per pair minus the winner per pair ).
2. Database context sanity (same as EB-43 runbook): `git pull` the latest `main` into production build; Flyway `spring.flyway.enabled=true` stays on.
3. **Backup first** (PITR or `pg_dump` pattern from `db/migration/README.md` §5.3).

## 4. Adopt

Option A (default): boot the app normally — Flyway applies V3 automatically under its advisory lock before Hibernate `validate()` runs.

Option B (manual, explicit):
```powershell
.\mvnw.cmd flyway:migrate
```

## 5. Post-adopt verification (one-shot, safe to re-run)

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
-- expect a final row: (3, 'uniqueness constraints and dedupe', true) after V1 baseline + V2

SELECT count(*) FROM eb44_archive_enrollments;        -- removed duplicate enrollment rows
SELECT count(*) FROM eb44_archive_mentorship_reviews; -- removed duplicate review rows
SELECT count(*) FROM eb44_archive_conversations;      -- removed conversation rows (dupes + NULL-participant)
SELECT count(*) FROM eb44_archive_messages;           -- messages of removed conversations (verbatim)

SELECT indexname FROM pg_indexes
WHERE indexname IN ('uk_enrollment_student_mentorship',
                    'uk_mentorship_review_student',
                    'uk_conversation_unordered_pair',
                    'idx_enrollments_mentorship',
                    'idx_enrollments_student_mentorship',  -- must be ABSENT
                    'idx_mentorship_student');             -- must be ABSENT

-- app-coupled smoke: second-rate should now return 409 ERR_DUPLICATE_RESOURCE instead of 400
```

## 6. Rollback story

Because archives live in real production tables (not backups only), removal is reversible per-row by hand — but the migration itself is not:
- Logical rollback: not provided by design. Once V3 succeeds, new writes are constrained by the unique indexes/constraints — there is no "un-violation" state to return to; adoption is one-way.
- If a live issue occurred while the migration was still running (e.g. the verification block failed), Flyway's transactional DDL has ALREADY rolled the entire V3 back: the old V2 state (tables + data) is intact, including any archive rows partially written inside the same failed transaction.

---

## 7. Approval gates — REQUIRED because V3 mutates live production data

Nothing in V3 is auto-approved. Each gate below must be signed (approver name + date) BEFORE the production boot/manual migrate; an unfilled table means **do not adopt**.

| Gate | What V3 will do to production data | Risk the approver accepts | Approver | Date |
|------|-----------------------------------|---------------------------|----------|------|
| G1 | NULL-participant conversations will be moved to `eb44_archive_conversations` and removed from the live table | Those conversations stop being reachable in the app (archival is not availability) | | |
| G2 | All messages of the conversations in G1 will be moved to `eb44_archive_messages` and removed from the live table | Users lose in-app access to that history; restoring requires a manual DBA action from the archive | | |
| G3 | Duplicate enrollment/review rows will be archived (`eb44_archive_enrollments` / `eb44_archive_mentorship_reviews`) and removed from the live tables | Duplicate history (double enrollments / double reviews) disappears from the FE | | |
| G4 | The winner rule is deterministic and fixed: smallest `id` (earliest `joined_at`, first review). Losing rows go to the archive. | Re-picking a different winner later means a manual archive restore; the rule cannot be changed retroactively | | |

The migration itself refuses half-states: the §1.7 verification block fails the WHOLE migration (Flyway transaction) if any duplicate or NULL count survives.

---

> [!CAUTION]
> **V3 performs a data remediation against four live application tables.** It is NOT silently approved: every destructive surface in §7 (G1–G4) requires a named approval entry before production adoption. The test matrix (T1–T5 migration/verification, U1–U8 app-on-PostgreSQL behavior) proves the code path on disposable databases only — passing suites are code evidence, not production approval. Keep the backup from step 3 untouched until the post-adopt checklist has passed twice (two consecutive app boots) plus one week of production traffic.

---

*Keep this runbook immutable together with the migration itself; adjustments go into a follow-up task.*
