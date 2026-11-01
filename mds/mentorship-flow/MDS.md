# Mentorship Lifecycle & Enrollment — Primary Domain Document

## 1. Overview

The commercial core: a mentor creates a structured program (`MentorShip`), publishes it (`DRAFT → ACTIVE`), students enroll (creating `Enrollment` + a commission snapshot + a `TotalPoints` leaderboard row), progress happens through weeks/lectures/tasks/projects/quizzes/live sessions, and the mentor completes it (`ACTIVE → COMPLETED`) — which triggers certificate issuance to all enrolled students ranked by earned points.

**Business value:** the product's monetization (price/discount/commission) and its certification promise both hang off this lifecycle.

**Scope:** `controller/mentorShip/mentorShipControllers`, `service/mentorShip/*` (incl. `CommissionService`, `ImageStorageService`), `entity/mentorship/*`, `repository/mentorShip/*`, enrollment state logic; student-side views in `controller/studentMentorship`, `service/studentMentorship`; homepage/explore in `controller/homepage`.

Supporting documents:
- [mentorship-lifecycle.md](mentorship-lifecycle.md) — statuses, transition rules, completion side effects
- [enrollment-and-pricing.md](enrollment-and-pricing.md) — join flow, discount/commission math, revenue reporting
- [weeks-and-lectures.md](weeks-and-lectures.md) — curriculum structure & access control
- Overview/explore/homepage detail: [explore-and-homepage.md](explore-and-homepage.md)

## 2. Functional Requirements (as implemented)

| # | Requirement | Status |
|---|---|---|
| R1 | CRUD for mentor-owned mentorships incl. cover image upload/delete | Implemented |
| R2 | Lifecycle transitions with business guards | Implemented (`updateMentorShipStatus`) |
| R3 | Public explore/search/category browse (ACTIVE only) | Implemented (`searchMentorShips` LIKE query) |
| R4 | Enrollment of students into ACTIVE mentorships (self-service, no payment gateway) | Implemented |
| R5 | Commission captured per enrollment (admin-set global rate) | Implemented |
| R6 | Manual student rating/review with denormalized rating on mentorship | Implemented |
| R7 | Complete-on-demand → auto-certificate + notifications | Implemented |
| R8 | Payment processing | **Not implemented** (enrollment is free-claim; "payments" are revenue *reporting*) |
| R9 | Scheduled start dates / degree of public browse during DRAFT | Partially (inconsistent, see gaps §7) |

## 3. Current Implementation — key classes

- `entity/mentorship/MentorShip` — title/subtitle/description(2000), `category` (free text, lowercased — **not an enum**), `rating` (denormalized avg), `difficultyLevel` enum, `status` enum `DRAFT/ACTIVE/COMPLETED`, `price`, `discountPercentage` (0–100), `coverImageUrl`, `duration`; `@OneToMany` cascade to WhatWillLearn/Tags/Enrollments/Reviews/Weeks/ChatRooms/TotalPoints/Badges/Certificates; `@ManyToOne mentor`. Indexes `idx_mentorship_mentor_id`, `idx_mentorship_status`. `//ToDo StartDate` comment (no start date field exists).
- `entity/mentorship/Enrollment` — student FK, mentorship FK, `price`, `platformProfit` (snapshot), `joinedAt`; ordinary index on (student_id, mentorship_id) **not unique**.
- `entity/mentorship/MentorShipReviews` — `feedBack` (≤500), `rating` (primitive long), FKs; **no unique constraint** (mentorship_id, student_id) — enforced only by service logic.
- `entity/mentorship/Week`, `Commission` (append-only rate history), `Tags`, `WhatWillLearn`.
- `service/mentorShip/mentorShipServiceI` (impl) — CRUD, status transitions, join/rate, cover image, counts, task access.
- `service/mentorShip/CommissionService` — latest-rate lookup + `calculatePlatformCut`/`calculateMentorPayout` (latter dead code); `@PreAuthorize("hasRole('ADMIN')")` set.
- `service/mentorShip/ImageStorageService` — cover images to `uploads/mentorship-cover-images`, plus generic `saveImage/deleteImage` used by profile/admin/chatroom flows.

**Entity note:** mentorship deletion (`deleteMentorShip`) allowed **only in DRAFT**; clears enrollments and deletes the cover file.

## 4. Architecture & Dependencies

- **Depends on:** users domain (mentor ownership), weeks/lectures (curriculum), learning content, gamification (TotalPoints row at join), certificates (on completion), notifications, file storage (cover images).
- **Enables:** dashboards (revenue/commission), My-Learning, leaderboards, chat room creation (rooms belong to mentorship via `entity/chat/ChatRoom`).

## 5. Workflows → detail docs

- Create → publish → complete → certificates: [mentorship-lifecycle.md](mentorship-lifecycle.md)
- Join → pricing snapshot → points row: [enrollment-and-pricing.md](enrollment-and-pricing.md)
- Week/lecture build & student views: [weeks-and-lectures.md](weeks-and-lectures.md)

## 6. Interfaces & Contracts (high level)

Full inventory in [../api/endpoints.md](../api/endpoints.md). Base paths:
- `/api/v1/mentorship` — mentor CRUD + lifecycle (`POST /update-status/{id}`), join/rate (student), explore/categories (public), counts for mentor, cover image endpoints.
- `/api/v1/student/mentorships` — overview (public), reviews (public; `studentFullName`, `studentEmail`, `userProfileImageUrl`), leaderboard (enrolled student, owning mentor or ADMIN; rows carry `studentFullName`, `studentEmail`, `studentProfileImageUrl`, `rank`, `totalPoints` per the D-04 amendment (Ruler B); `studentLevel`/badges stay excluded), top mentorships by mentor. The temporary public-review compatibility exception is Engineering Lead approved; frontend usage verification remains pending and future email removal requires frontend coordination.
- `/api/v1/week`, `/lectures` — curriculum CRUD (mentor) and reads.
- Validation: `mentorShipCreateDTO`/`UpdateDTO` (+ 0–100 discount); review ≤500 chars.

## 7. Known Issues (summary — IDs & evidence in ../ISSUES.md)

- **MSH-01 (High):** **FIXED (P1-15 / EB-22, D-02 default (a)):** DRAFT mentorships confined to owner-mentor + ADMIN on every read surface — `GET /api/v1/mentorship/{id}` and the student overview 404 for everyone else (no existence/status leak), public paged overview excludes DRAFT (`findAllByStatusNot`); explore/categories/top-mentorships/recommendations verified ACTIVE-only. Probes: `MentorshipDraftConfinementProbesTest` (8) + G8/G8b flips in `LeaderboardDraftProbesTest` (pending review).
- **MSH-02 (High):** loaded-content leak — `GET /{mid}/get-tasks`, `GET /lectures/**` (3 GETs unguarded — any authenticated user reads lecture media URLs of any mentorship).
- **MSH-03 (Medium):** EB-23 adds the leaderboard membership/ownership guard; review pending.
- **MSH-04 (Medium):** enrollment double-insert protected only by the `TotalPoints` unique constraint (Enrollment itself lacks one) + race on `exists`-check pattern.
- **MSH-05 (Medium):** commission rate is **append-only history with no default** — first enrollment fails with `Commission rate not set` on a fresh deployment until an admin sets a rate.
- **MSH-06 (Medium):** cover-image upload trusts client `Content-Type`; extension taken raw from multipart filename; no Tika/magic-byte validation (contrast with submission files); old image deleted-before-new-uploaded (failure window).
- **MSH-07 (Low):** explore price filters compare **base** price while UI shows discounted price.
- **MSH-08 (Low):** N+1 on mentorship listing (`whatWillLearn` lazy per row) and in-memory pagination in homepage upcoming-items aggregation.
- **MSH-09 (Medium):** Public mentorship and mentor-profile reviews expose reviewer emails and profile images under an Engineering Lead-approved temporary compatibility exception; frontend usage verification remains pending.
- **MSH-10 (Medium):** status side effects (completion → certificates + fan-out notifications) run **inside** the status transaction — entangled transactional boundary.
- **MSH-11 (Medium):** homepage progress endpoint `GET /api/v1/homepage/progress/{mentorshipId}` performs no enrollment check — any student queries any mentorship's content composition.
- **MSH-12 (Medium, ⚠️ suspected):** explore keyword search builds `LIKE '%kw%'` without wildcard escaping — unbounded scans / pattern abuse under load.

## 8. Open Questions

- Is DRAFT visibility to students acceptable as "coming soon" behavior, or must it be hard-404? Current mixed behavior suggests accident.
- Should enrollment require a paid transaction (no gateway exists) — confirm the free-claim flow is intended for launch.
- `//ToDo StartDate` on `MentorShip` — scheduled launches were planned? No field, no logic exists.
- Certificate issuance requires ≥1 enrolled student; confirm zero-enrollment completions should be blocked (current behavior) vs issue nothing silently.

## 9. Related Documentation

[mentorship-lifecycle.md](mentorship-lifecycle.md) · [enrollment-and-pricing.md](enrollment-and-pricing.md) · [weeks-and-lectures.md](weeks-and-lectures.md) · [explore-and-homepage.md](explore-and-homepage.md) · [../certificates/MDS.md](../certificates/MDS.md) · [../gamification/MDS.md](../gamification/MDS.md) · [../ISSUES.md](../ISSUES.md)
