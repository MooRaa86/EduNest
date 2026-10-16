# Indexing & Constraints — As-Built Inventory

What Hibernate/MySQL currently creates from the entities (verified) + pointers to the team's preserved optimization analyses in this directory.

## 1. Explicit indexes declared on entities

| Table | Index | Columns | Notes |
|---|---|---|---|
| `mentorship` | `idx_mentorship_mentor_id`, `idx_mentorship_status` | mentor_id / status | status supports explore/publish scans |
| `enrollments` | `idx_enrollments_student_mentorship` | (student_id, mentorship_id) | **ordinary index, NOT unique** — dedupe relies on service checks + the points unique (MSH-04) |
| `mentorship_reviews` | `idx_mentorship_only`, `idx_mentorship_student` | mentorship_id / (mentorship_id, student_id) | no unique pair constraint |
| `lectures` | `idx_lectures_week_id` | week_id | – |
| `tasks` | `idx_task_week_id` | week_id | – |
| `sessions` | `idx_sessions_week_id` | week_id | – |
| `total_points` | unique `uk_student_mentorship` | (student_id, mentorship_id) | the ledger's identity |
| `task_submissions`, `project_submissions` | unique (task/project_id, student_id) | – | submission identity |
| `quiz_submissions` | unique (student_id, quiz_id) | – | attempt identity |
| `student_answers` | unique (submission_id, question_id) | – | one answer per question |
| `badge_awards` | unique (badge_id, student_id) | – | – |
| `user_admin_badges` | unique (user_id, admin_badge_id) | – | – |
| `user_notifications` | unique (user_id, notification_id) + `idx_notifications_user_id` | – | read-state lookup |
| `certificates` | unique (student_id, mentorship_id) | – | issues rank rows |
| `users` | unique(email) | – | + composite supporting queries (email flags in JPA-generated queries) |

Everything else (FK indexes on `@ManyToOne` bind columns) is generated automatically by Hibernate `ddl-auto=update`.

## 2. Constraints a real deployment should add (decide in stabilization)

1. `UNIQUE (student_id, mentorship_id)` on `enrollments` (+ rename current index accordingly).
2. `UNIQUE (mentorship_id, student_id)` on `mentorship_reviews` (service currently enforces; data may already contain dupes → dedupe migration first).
3. `UNIQUE (user1_id, user2_id)` on the conversation pair (canonicalize direction via the service's ordered-pair logic first).
4. Consider `CHECK`s / NOT NULLs where entities rely on defaults (e.g., `platformProfit default 0.0` is column-definition-based).

## 3. Query-profile observations (from the audits)

- Explore/list queries filter `status`, `category`, mentor name, and price on `mentorship` — indexes above cover mentor/status; category is free-text lowercased (no prefix index useful for `LIKE '%x%'`).
- My-Learning/leaderboard native queries join several tables per row; the `idx_notifications_user_id`-style compound indexes cover the hot user-scoped reads.
- Unbounded in-memory pagination (homepage upcoming items) and N+1 patterns (mentorship listing `whatWillLearn`) degrade scaling before indexes can help — see [../mentorship-flow/explore-and-homepage.md](../mentorship-flow/explore-and-homepage.md) and MSH-08.

## 4. Preserved team analyses (in this directory)

- [DATABASE_OPTIMIZATION.md](DATABASE_OPTIMIZATION.md) — prior optimization review of query patterns.
- [database_indexing_recommendations.md](database_indexing_recommendations.md) — earlier indexing proposals.
- [search_indexing_plan.md](search_indexing_plan.md) — plan for external search indexing (not implemented).

*These are pre-existing documents consolidated here during the documentation phase; treat them as historical proposals — the as-built state above is authoritative.*
