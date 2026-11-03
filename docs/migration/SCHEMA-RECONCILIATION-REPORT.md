# EduNest Database Schema Reconciliation Report (Schema-Only Scope)

**Task:** EB-82 — MySQL to PostgreSQL Schema Reconciliation & Compatibility
**Authoritative Scope:** **SCHEMA-ONLY RECONCILIATION** (Project Owner Decision)
**Target Database:** Supabase PostgreSQL 17.11 (`postgres`, `public` schema)
**Historical Reference Database:** MySQL 8.0.41 (`edunest`)
**Target Application Tables:** 42 active public tables
**Legacy Excluded Tables:** 1 (`user_settings`)
**Data Migration Status:** **CANCELLED & EXCLUDED** — All 396 legacy MySQL records are confirmed obsolete development data and discarded. No data export or transfer is performed.

---

## 1. Project Owner Scope Change Directive

> **Official Decision (November 3, 2026):**
> *"Existing MySQL records are obsolete development data. EB-82 is intentionally limited to PostgreSQL schema reconciliation and compatibility. No MySQL data export or import is required."*

### Impact on Scope:
- **No Data Migration:** All 396 MySQL development records are permanently discarded.
- **No Data Pipeline Execution:** No `mysqldump`, no record transformation, no user/mentor record migration, no role foreign key remapping.
- **Supabase Target Preserved:** The existing Supabase PostgreSQL development database is retained. Existing data (the 3 pre-seeded `roles` records) is preserved. No tables are dropped or recreated.
- **Schema Alignment Objective:** Ensure the 42 public PostgreSQL application tables are 100% structurally aligned and compatible with current Spring Boot JPA entities, specifically resolving the 5 `@Lob` (OID vs `TEXT`) column definitions.

---

## 2. Table-by-Table Inventory & Alignment Matrix (42 Tables)

The Supabase PostgreSQL database contains exactly 42 application tables in schema `public`. All tables (except `roles`) contain 0 records.

| # | Public Table Name | Primary Key | Supabase Identity / Seq | Supabase Row Count | JPA Entity | Compatibility Status |
|---|-------------------|-------------|-------------------------|--------------------|------------|----------------------|
| 1 | `roles` | `id` | `IDENTITY` | **3** | `Roles.java` | **COMPATIBLE** (Preserved as-is) |
| 2 | `admin` | `id` | `IDENTITY` | 0 | `Admin.java` | **COMPATIBLE** |
| 3 | `admin_badges` | `id` | `IDENTITY` | 0 | `AdminBadge.java` | **COMPATIBLE** |
| 4 | `admin_notification` | `id` | `IDENTITY` | 0 | `AdminNotification.java` | **COMPATIBLE** |
| 5 | `badge_awards` | `id` | `IDENTITY` | 0 | `BadgeAward.java` | **COMPATIBLE** |
| 6 | `badges` | `id` | `IDENTITY` | 0 | `Badge.java` | **COMPATIBLE** |
| 7 | `certificates` | `id` | `IDENTITY` | 0 | `Certificate.java` | **COMPATIBLE** |
| 8 | `chat_messages` | `id` | `IDENTITY` | 0 | `ChatMessage.java` | **COMPATIBLE** |
| 9 | `chat_room_members` | `id` | `IDENTITY` | 0 | `ChatRoomMember.java` | **COMPATIBLE** |
| 10 | `chat_rooms` | `id` | `IDENTITY` | 0 | `ChatRoom.java` | **COMPATIBLE** |
| 11 | `commission` | `id` | `IDENTITY` | 0 | `Commission.java` | **COMPATIBLE** |
| 12 | `contact_message` | `id` | `IDENTITY` | 0 | `ContactMessageEntity.java` | **REQUIRES DDL:** `message` (OID -> `TEXT`) |
| 13 | `conversations` | `id` | `IDENTITY` | 0 | `Conversation.java` | **COMPATIBLE** |
| 14 | `enrollments` | `id` | `IDENTITY` | 0 | `Enrollment.java` | **COMPATIBLE** (`platform_profit float8`) |
| 15 | `lectures` | `id` | `IDENTITY` | 0 | `Lecture.java` | **COMPATIBLE** |
| 16 | `mentors` | `mentor_id` | `None` (maps to `users.id`) | 0 | `Mentor.java` | **COMPATIBLE** |
| 17 | `mentorship` | `id` | `IDENTITY` | 0 | `MentorShip.java` | **COMPATIBLE** |
| 18 | `mentorship_reviews` | `id` | `IDENTITY` | 0 | `MentorShipReviews.java` | **COMPATIBLE** |
| 19 | `mentorship_what_will_learn` | `id` | `IDENTITY` | 0 | `WhatWillLearn.java` | **COMPATIBLE** |
| 20 | `messages` | `id` | `IDENTITY` | 0 | `Message.java` | **COMPATIBLE** |
| 21 | `notification` | `id` | `IDENTITY` | 0 | `Notification.java` | **COMPATIBLE** |
| 22 | `otp` | `id` | `IDENTITY` | 0 | `Otp.java` | **COMPATIBLE** |
| 23 | `project_submission` | `id` | `IDENTITY` | 0 | `ProjectSubmission.java` | **REQUIRES DDL:** `project_feedback` (OID -> `TEXT`) |
| 24 | `projects` | `id` | `IDENTITY` | 0 | `Project.java` | **REQUIRES DDL:** `project_brief` (OID -> `TEXT`) |
| 25 | `questions` | `id` | `IDENTITY` | 0 | `Question.java` | **COMPATIBLE** |
| 26 | `quiz` | `id` | `IDENTITY` | 0 | `Quiz.java` | **COMPATIBLE** |
| 27 | `quiz_submission` | `id` | `IDENTITY` | 0 | `QuizSubmission.java` | **COMPATIBLE** |
| 28 | `session_attendance` | `id` | `IDENTITY` | 0 | `SessionAttendance.java` | **COMPATIBLE** |
| 29 | `session_attendance_result` | `id` | `IDENTITY` | 0 | `SessionAttendanceResult.java` | **COMPATIBLE** |
| 30 | `sessions` | `id` | `IDENTITY` | 0 | `Session.java` | **COMPATIBLE** |
| 31 | `social_media` | `id` | `IDENTITY` | 0 | `SocialMedia.java` | **COMPATIBLE** |
| 32 | `student_answer` | `id` | `IDENTITY` | 0 | `StudentAnswer.java` | **COMPATIBLE** |
| 33 | `student_skill` | `skill_id` | `IDENTITY` | 0 | `StudentSkill.java` | **COMPATIBLE** |
| 34 | `students` | `student_id` | `None` (maps to `users.id`) | 0 | `Student.java` | **COMPATIBLE** |
| 35 | `tags` | `id` | `IDENTITY` | 0 | `Tags.java` | **COMPATIBLE** |
| 36 | `task_submission` | `id` | `IDENTITY` | 0 | `TaskSubmission.java` | **REQUIRES DDL:** `task_feedback` (OID -> `TEXT`) |
| 37 | `tasks` | `id` | `IDENTITY` | 0 | `Task.java` | **REQUIRES DDL:** `task_description` (OID -> `TEXT`) |
| 38 | `total_points` | `id` | `IDENTITY` | 0 | `TotalPoints.java` | **COMPATIBLE** |
| 39 | `user_admin_badges` | `id` | `IDENTITY` | 0 | `UserAdminBadge.java` | **COMPATIBLE** |
| 40 | `user_notification` | `id` | `IDENTITY` | 0 | `UserNotification.java` | **COMPATIBLE** |
| 41 | `users` | `id` | `IDENTITY` | 0 | `UserEntity.java` | **COMPATIBLE** |
| 42 | `weeks` | `id` | `IDENTITY` | 0 | `Week.java` | **COMPATIBLE** |

---

## 3. Exclusion of Legacy `user_settings` Table

- **MySQL Status:** Table exists with 0 records.
- **PostgreSQL Status:** Table **DOES NOT EXIST** in Supabase PostgreSQL.
- **Codebase Status:** No entity `@Table(name = "user_settings")` exists. The application's `SettingsController` interacts directly with `UserEntity` for profile and password operations.
- **Referential Integrity:** 0 foreign keys inbound, 0 foreign keys outbound.
- **Decision:** **Do NOT create `user_settings` in PostgreSQL.** The table is confirmed dead legacy prototype code.

---

## 4. The Five Incompatible OID Columns & Corrective DDL

### 4.1 Verified Current Column States in Supabase PostgreSQL
1. `contact_message.message` — currently type `oid` (Table row count: 0)
2. `projects.project_brief` — currently type `oid` (Table row count: 0)
3. `project_submission.project_feedback` — currently type `oid` (Table row count: 0)
4. `tasks.task_description` — currently type `oid` (Table row count: 0)
5. `task_submission.task_feedback` — currently type `oid` (Table row count: 0)

### 4.2 Entity Code Fix (Committed in EB-82)
In commit `09f61a5`, all 5 entities were updated to remove `@Lob` and declare `@Column(..., columnDefinition = "TEXT")`:
- `ContactMessageEntity.message`
- `Project.brief`
- `ProjectSubmission.feedBack` (maps to `project_feedback`)
- `Task.description`
- `TaskSubmission.feedBack` (maps to `task_feedback`)

### 4.3 Required DDL for Supabase Execution (Awaiting Operator Authorization)
Because all 5 tables currently contain **0 records**, the type alteration executes cleanly without data transformation overhead:

```sql
BEGIN;

-- 1. contact_message.message: convert from oid to text
ALTER TABLE public.contact_message
    ALTER COLUMN message TYPE TEXT
    USING (CASE WHEN message IS NOT NULL THEN convert_from(lo_get(message), 'UTF8') ELSE NULL END);

-- 2. projects.project_brief: convert from oid to text
ALTER TABLE public.projects
    ALTER COLUMN project_brief TYPE TEXT
    USING (CASE WHEN project_brief IS NOT NULL THEN convert_from(lo_get(project_brief), 'UTF8') ELSE NULL END);

-- 3. project_submission.project_feedback: convert from oid to text
ALTER TABLE public.project_submission
    ALTER COLUMN project_feedback TYPE TEXT
    USING (CASE WHEN project_feedback IS NOT NULL THEN convert_from(lo_get(project_feedback), 'UTF8') ELSE NULL END);

-- 4. tasks.task_description: convert from oid to text
ALTER TABLE public.tasks
    ALTER COLUMN task_description TYPE TEXT
    USING (CASE WHEN task_description IS NOT NULL THEN convert_from(lo_get(task_description), 'UTF8') ELSE NULL END);

-- 5. task_submission.task_feedback: convert from oid to text
ALTER TABLE public.task_submission
    ALTER COLUMN task_feedback TYPE TEXT
    USING (CASE WHEN task_feedback IS NOT NULL THEN convert_from(lo_get(task_feedback), 'UTF8') ELSE NULL END);

COMMIT;
```
*(Note: Uses `convert_from(lo_get(...), 'UTF8')` as a defensive universal guarantee that preserves content even if records were inserted prior to execution).*

---

## 5. Constraints, Indexes & Sequence Compatibility

- **Primary Keys:** All 42 tables have their primary keys established matching entity `@Id` annotations.
- **Foreign Keys:** Established across all relational child entities.
- **Unique Constraints:** Confirmed for `users.email`, `roles.name`, `certificates.certificate_code`, and composite unique constraints (`(project_id, student_id)`, `(task_id, student_id)`, `(quiz_id, student_id)`).
- **Existing Roles:** The 3 rows in `public.roles` (`ADMIN`, `MENTOR`, `STUDENT`) remain intact and untouched.
- **No Extra Tables or Duplicate Indexes:** No `CREATE TABLE` or drop statements are needed.
