# Mentorship Lifecycle & Status Transitions

> **EB-52 local implementation draft:** completion creates a durable job and frozen PostgreSQL ranking snapshot in the status transaction. The worker issues the full certificate cohort in one later transaction, then persists notifications in another. Certificates may not be immediately available after the completion response. This draft has not been merged or deployed.
>
> The ranking snapshot sees points committed when its SQL statement begins; grading transactions committing later are not included. Enrollment and completion use the mentorship L0 lock. Repeating an authorized COMPLETED request is a no-op. WebSocket delivery after notification persistence is best effort.
>
> Configure `app.completion.worker.enabled` and `app.completion.worker.poll-ms`; global scheduling must also be enabled. Operators must investigate exhausted jobs and existing certificate/notification records before an administrator uses `POST /api/v1/admin/completion-jobs/{jobId}/retry` with JSON `{"reason":"..."}`. The operation accepts only exhausted jobs and records the actor, reason and time; preserve structured logs for prior attempts. Never change points or remove notification records to force a retry.

Verified walkthrough of `service/mentorShip/mentorShipServiceI#updateMentorShipStatus` (lines 253–319) and related flows.

## 1. States

```text
DRAFT ──create default──▶ DRAFT ──publish──▶ ACTIVE ──complete──▶ COMPLETED (terminal)
```

Enum: `enums/mentorShip/Status` = `DRAFT, ACTIVE, COMPLETED`. Entity default `DRAFT`.

## 2. Transition rules (`updateMentorShipStatus`)

| Guard | Behavior |
|---|---|
| Ownership | mentor must own the mentorship (line 261) |
| Same status | COMPLETED → COMPLETED is an authorized no-op; other same-status requests are rejected |
| ACTIVE → DRAFT | **rejected** (line 269) |
| DRAFT → COMPLETED | **rejected** — publication first (line 273) |
| COMPLETED → a different status | **rejected — terminal** |
| → COMPLETED prerequisite | `enrollmentRepository.countStudentsByMentorship(mentorshipId) > 0` (lines 281–286) — cannot complete with zero students |
| Allowed | DRAFT → ACTIVE, ACTIVE → COMPLETED |

## 3. Side effects per transition (EB-52 local implementation)

| Transition | Effects |
|---|---|
| → ACTIVE | `NotificationService.sendToAdmin("Mentorship Published", …)` |
| → COMPLETED | Commit the status, ranking snapshot and completion job; the worker issues all certificates atomically and then persists certificate, student and admin notifications atomically. STOMP pushes are best effort after notification commit. |

The certificate and notification transactions are separate from the completion transaction. A failed stage is retried up to five times; committed earlier stages are not rolled back. Detail: [../certificates/MDS.md](../certificates/MDS.md) and [../notifications/MDS.md](../notifications/MDS.md).

## 4. Deletion

`deleteMentorShip` (lines 184–207): **DRAFT only**; calls `getEnrollments().clear()` (drop unrelated enrollments, if any), deletes cover image file via `ImageStorageService.deleteOldCoverImage`, then deletes the mentorship (cascade orphanRemoval across WhatWillLearn/Tags/Weeks/ChatRooms/TotalPoints/Badges/Certificates collections). Content inside weeks (tasks/projects/quizzes/lectures/sessions) is cascade-owned by Week.

## 5. Creation & editing

- `createMentorShip(mentorShipCreateDTO)` — MENTOR role; fields limited by DTO (no weeks yet; no start date field).
- `updateMentorShip(mentorShipUpdateDTO)` — ownership-checked; editing is allowed in any status including COMPLETED (no edit-freeze rule exists).
- Category: lowercased free text (`@Size(max=30)`) — categories are therefore open-ended; `/categories` aggregates distinct values of ACTIVE mentorships.

## 6. Student-facing lifecycle views

- `GET /api/v1/student/mentorships/{id}/overview` (**public**) — composes mentorship details + top-3 mentorships of its mentor + upcoming sessions/page of upcoming tasks/projects + `isEnrolled` when the caller is authenticated. Source: `MentorshipOverviewService.getMentorshipWithEnrollmentStatus` (lines 62–133).
- `GET /{id}/reviews` (public, paginated) — includes reviewer **emails** (privacy finding MSH-09).
- `GET /{id}/leaderboard` (authenticated) — points-ranked students; **enrollment guard commented out** (`MentorshipLeaderboardService` L26–28).
- `GET /mentor/{mentorEmail}/top-mentorships` — top-3 by rating/creation of a mentor (authenticated).

## 7. Status-transition test matrix (recommended baseline tests)

1. DRAFT→ACTIVE ok; ACTIVE→DRAFT rejected; DRAFT→COMPLETED rejected; COMPLETED→ACTIVE rejected.
2. Complete with 0 enrollments rejected; with 1 enrollment triggers certificate issuance (verify rows + notification fans) *and* is idempotent-guarded (COMPLETED terminal).
3. Mentor B attempting status change on mentor A's mentorship → 403/400.
4. Edit of COMPLETED mentorship — document decided behavior (currently allowed).
