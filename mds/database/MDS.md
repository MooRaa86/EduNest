# Database & Persistence — Primary Domain Document

## 1. Overview

MySQL 8 accessed via Spring Data JPA/Hibernate with **`ddl-auto=update` as the only schema-management mechanism** (both profiles). 43 entity classes model 13 sub-domains. No migrations tool (Flyway/Liquibase) is configured anywhere — schema changes happen implicitly from entity edits.

**Business value:** everything user-facing is derived from these tables — enrollments, points, chats, certificates.

**Scope:** `entity/**`, `repository/**` (+ projection read-models), all persistence/transacational behavior. Security-relevant data (OTP, soft-delete) is detailed in [../authentication-security/authentication-flow.md](../authentication-security/authentication-flow.md).

Related documents:
- [entity-relationships.md](entity-relationships.md) — full catalog by domain
- [transactions-consistency.md](transactions-consistency.md) — transactional inventory, races, rollback-visibility
- [indexing.md](indexing.md) — as-built indexes & constraints
- Preserved team analysis: [DATABASE_OPTIMIZATION.md](DATABASE_OPTIMIZATION.md), [database_indexing_recommendations.md](database_indexing_recommendations.md), [search_indexing_plan.md](search_indexing_plan.md)

## 2. Functional Requirements (what persistence must deliver)

| # | Requirement | Status |
|---|---|---|
| R1 | Audited rows (created/updated + who) for accountability | Implemented via `BaseEntity` + JPA auditing (auditor = principal email or `SYSTEM`) |
| R2 | Soft-delete for users | Implemented (`UserEntity.deleted`) — **not** consistently applied in read queries (DB-03) |
| R3 | Per-mentorship points ledger with concurrency safety | Implemented for the `applyDelta` path (pessimistic lock); inconsistent for badge bulk updates |
| R4 | Unique enrollment / certificate / badge-award guarantees | Implemented for TotalPoints/badges/certificates — **missing** for `Enrollment`, `MentorShipReviews`, `Conversation`, `SessionAttendance` |
| R5 | Schema evolution under control | **Not implemented** (`ddl-auto=update`; no versioned migrations) |

## 3. Current Implementation

### 3.1 Entity zoo (43 classes)

Complete catalog: [entity-relationships.md](entity-relationships.md). Landmarks:

- **`BaseEntity`** (`@MappedSuperclass`): `id` IDENTITY, `createdAt` `@CreationTimestamp` (updatable=false), `updatedAt` `@UpdateTimestamp`, `createdBy`/`updatedBy` (`@CreatedBy/@LastModifiedBy`, `nullable=false`) — auditing wired in `auditing/JpaAuditingConfig` + `AuditorAwareImpl`.
- **Exceptions to BaseEntity** (plain `@Id`): `chat/Message`, `chat/Conversation`, `chat/ChatRoomMember`, `skill/StudentSkill`, `entity/Roles` — consequences: private-chat messages carry no audit columns and no `updatedAt` on edits.
- **User model:** `UserEntity` abstract, `@Inheritance(JOINED)` on table `users` → `Student` (table `students`), `Mentor` (table ` mentors`); `Admin` is an independent root entity (table `admins`, no enabled/deleted flags).
- **Points:** `points/TotalPoints` unique (student_id, mentorship_id).
- **Certificates:** `certificate/Certificate` unique (student_id, mentorship_id), `rank` in `student_rank`.

### 3.2 Repositories & read-models

Spring Data interfaces per domain; extensive **projection interfaces** (`repository/**/projection*`) power paged reads (dashboards, leaderboards, admin lists) without loading entities. Notables:

- `UserRepository` — `findAuthUser` (login projection), `isUserEnabledAndNotDeleted` (JWT re-check), `findFullNameByEmail`, `MonthlyUsersProjection`.
- `MentorShipRepository` — `searchMentorShips` (explore, status/price/name filters incl. `priceAfterDiscount` computed in JPQL), dashboard aggregations (native).
- `EnrollmentRepository` — progress/my-learning (`findAllEnrolledMentorshipsWithProgress`, native + countQuery), revenue sums, enrollment existence checks.
- `TotalPointsRepository` — leaderboards (4 query variants incl. `ROW_NUMBER()` native ranks), `findForUpdate` (`@Lock(PESSIMISTIC_WRITE)`), badge bulk `@Modifying updatePointsForBadgeAwards`.
- `NotificationRepository`/`UserNotificationRepository` — fan-out + unread bookkeeping with `@Modifying` mark-read ops.

### 3.3 Schema management (as-built)

- `spring.jpa.hibernate.ddl-auto=update` **and** `show-sql=true`/`format_sql=true` in both profiles.
- `src/main/resources/scripts/script.sql` exists on disk but **is not referenced** by any config (`spring.sql.init` unset; no Docker init hooks) — inert artifact.
- Data files live in `uploads/` (filesystem, not DB) — see [../file-storage/MDS.md](../file-storage/MDS.md).

## 4. Architecture & Dependencies

- Repositories → Hibernate/MySQL; all entities cascade-owned via `@OneToMany(cascade=ALL, orphanRemoval=true)` from their aggregates (MentorShip → weeks/etc., Week → content, Quiz → questions/submissions, Task → submissions…).
- Cross-aggregate links are predominately **`@ManyToOne(LAZY)`** + service-implemented integrity rules (no DB-level FK text beyond generated constraints from the mappings).
- OSIV (default ON in Spring Boot) is **relied upon**: several read paths navigate lazy collections outside transactional service methods (e.g. quiz dashboard navigations, `mentorShipServiceI.mapToMentorShipResponse`) — [transactions-consistency.md §3](transactions-consistency.md#3-open-session-in-view-osiv-reliance).

## 5. Workflows & Data Flow

Domain-level flows with their DB writes are documented per domain (enrollment & points: [../mentorship-flow/enrollment-and-pricing.md](../mentorship-flow/enrollment-and-pricing.md), grading: [../learning-content/my-learning-and-achievements.md](../learning-content/my-learning-and-achievements.md), completion: [../certificates/MDS.md](../certificates/MDS.md)). Cross-cutting transaction/visibility concerns: [transactions-consistency.md](transactions-consistency.md).

## 6. Interfaces & Contracts

The HTTP/DTO layer never exposes entities directly; responses flow through projections/DTO mappers. Entity ↔ DTO mapping is hand-rolled static mapping methods inside services (no MapStruct).

## 7. Data Model — integrity & constraints summary

| Guarantee | Where | Mechanism | Gap |
|---|---|---|---|
| One enrollment per (student, mentorship) | — | service exists-check | **No DB unique** (protected incidentally by the `TotalPoints` unique in the same TX — DB-04) |
| One review per (student, mentorship) | — | service only | **No DB unique** (DB-04) |
| One conversation per user pair | — | get-or-create | **No DB unique** on (user1,user2) (CHAT-05) |
| One submission per (student, task/project/quiz) | ✓ | DB unique | – |
| One badge award per (badge, student) | ✓ | DB unique | – |
| One certificate per (student, mentorship) | ✓ | DB unique | – |
| One admin badge per (user, admin badge) | ✓ | DB unique | – |
| One OTP row per user | ✓ | service delete-then-create | No DB constraint (multi-user burst races unlikely; admin OTP rows separate) |
| Points never go below 0 | `applyDelta` | clamp + pessimistic lock | bulk badge-update path bypasses both (GAME-03) |

## 8. Security

- Sensitive auth data at rest: BCrypt hashes (users/admins), **plaintext** OTP codes, email addresses on nearly every projection. `AdminAccResponse` includes the password hash in admin listings (SEL-08) — DB content itself is sound here.
- Soft-delete (`deleted`) is *not* enforced in most read queries — deleted users can appear in leaderboards/reviews until product decisions + query filters land (DB-03).

## 9. Known Issues (IDs in ../ISSUES.md)

- **DB-01 (High):** `ddl-auto=update` in prod + no migrations → uncontrolled schema drift, no rollback path, no freeze on destructive entity edits.
- **DB-02 (Medium):** `script.sql` inert; no schema source-of-truth aside from entities.
- **DB-03 (Medium):** soft-delete not applied in read queries (leaderboards, reviews, chats).
- **DB-04 (Medium):** missing unique constraints (enrollment, review, conversation, attendance) — see §7.
- **DB-05 (Medium):** inconsistent `@Transactional` coverage (missing on `WeekService.deleteWeek`, `startLiveSession`, `recordSnapshot`) — details in [transactions-consistency.md](transactions-consistency.md).
- **DB-06 (Low):** OSIV reliance for lazy navigation in read paths.
- **DB-07 (Low):** hand-loop persistence instead of batching (attendance snapshots, per-student certificate/notification saves).

## 10. Open Questions

- Is a real migration foundation (Flyway/Liquibase with a baseline) wanted in the stabilization phase? This decides how entity edits are deployed forever after.
- Are deleted-user artifacts (leaderboard rows, reviews, conversations) supposed to disappear from public views? (Query policy decision.)
- Confirm the desired rank tie-break (current: `students.id ASC`) and whether final ranks should be frozen at completion (they are, by certificate row).

## 11. Related Documentation

[entity-relationships.md](entity-relationships.md) · [transactions-consistency.md](transactions-consistency.md) · [indexing.md](indexing.md) · [../authentication-security/MDS.md](../authentication-security/MDS.md) · [../ISSUES.md](../ISSUES.md)
