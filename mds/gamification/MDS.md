# Gamification (Badges · Points · Ranks) — Primary Domain Document

## 1. Overview

Two separate recognition systems plus a points ledger:

1. **Mentor badges** — mentor-created, mentorship-scoped (`Badge` + `BadgeAward`), worth configurable points that flow into `TotalPoints`.
2. **Admin (platform) badges** — 4 fixed types (`TOP_MENTOR`, `ACADEMIC_EXCELLENCE`, `COMMUNITY_LEADER`, `INNOVATOR_AWARD`), awarded by admins, no points value (recognition only), with email + **iText7 PDF** certificate attachment.
3. **TotalPoints ledger** — per (student, mentorship); authoritative source for leaderboards, dashboard ranks, and certificate ranking.

**Business value:** engagement engine and the ranking basis of the certification promise.

## 2. Current Implementation

### 2.1 Entities
- `entity/badges/Badge` — `mentorship` (required), `title`, `category` (`BadgeCategory`: ACHIEVEMENT, PERFORMANCE, CONSISTENCY, PROBLEM_SOLVING, CREATIVITY, LEADERSHIP, COMMUNITY, SPECIAL_RECOGNITION), `description`, `int points` (`@Min(1) @Max(500)`), `@OneToMany awards`.
- `entity/badges/BadgeAward` — unique (badge_id, student_id); `awardedBy` (Mentor), `note`.
- `entity/points/TotalPoints` — unique (student_id, mentorship_id); `totalPoints` default 0; row **created at enrollment**.
- `entity/admin/AdminBadge` + `UserAdminBadge` — unique (user_id, admin_badge_id); `recognitionNote`, `awardedAt`.

**No default/global badge catalog exists** (grep verified) — badges are purely mentor-authored; the README's "8 configurable categories" refers to the enum.

### 2.2 Services & flows
- `BadgeService#createBadge` — mentor-ownership check; **cap 10 badges per mentorship** (read-then-write, racy).
- `BadgeService#updateBadge` — ownership; **points change retro-applies** the delta to all existing awards via `TotalPointsRepository#updatePointsForBadgeAwards` (bulk `@Modifying`, **no lock, no 0-clamp**).
- `BadgeService#deleteBadge` — ownership; **blocked if ever awarded** (safe).
- `BadgeAwardService#awardBadge` — mentor owns badge; **student must be enrolled** (verified check, L68); no double award (unique + exists-check); side effects: `applyDelta(badge.points)` → **in-app notification** ("/queue/notifications") — no email.
- `AdminBadgeService#awardBadgeToUser` — + email with `badge-award.html` + **PDF attachment** via `BadgePdfGeneratorService` (iText7 — the *only* iText usage in the app); email deferred to `TransactionSynchronization.afterCommit` (correct), while the **STOMP notification is sent pre-commit** (inconsistent); values escaped via `HtmlUtils` before template merge.
- Badge GET reads (`getById`, `getByMentorship`) — **catalog reads stay open by plan** (P1-19: title/category/description/points carry no personal data). Award-list reads are scoped since P1-19 / EB-26: `/badge-awards/mentorship/{id}` + `/badge/{id}` were already owner-mentor-only (403 via `getCurrentMentor`); `/badge-awards/student/{id}` is confined to ADMIN, the student themself (principal-derived email, D-03), or a mentor of one of the student's enrollments.

### 2.3 Points mechanics (summary)
Award sites and sequencing are consolidated in [../learning-content/my-learning-and-achievements.md](../learning-content/my-learning-and-achievements.md#1-points-award-sites-complete-list-verified-callers):
- `TotalPointsServiceImp#applyDelta` — `@Lock(PESSIMISTIC_WRITE)` find-for-update + **clamp at 0**; used by every flow except the badge bulk-update path.
- Leaderboard queries & rank math: same document §2.

## 3. Interfaces & Contracts

- `POST /api/v1/badges` (mentorship-scoped create; MENTOR), `PATCH/DELETE /{id}` (mentor+ownership), `GET /{id}`, `GET /mentorship/{id}` (open).
- `POST /api/v1/badge-awards/{badgeId}/award` ← `AwardBadgeRequest{studentId, note}` (MENTOR); `GET` awards by mentorship/badge (owner-mentor only), by student (since EB-26: self/ADMIN/mentor-of-enrollment).
- Admin badge controller `controller/admin/AdminBadgeController` — **no class-level guard**; service-level ADMIN annotations on mutations; `GET /api/users/{userId}/badges` + `GET /api/admin/badges` open (URL-rule + dev-open caveats).
- DTOs: `CreateBadgeRequest` (`@NotBlank title`, `@NotNull category`, `@NotBlank description`, points 1..500), `AwardBadgeRequest`.

## 4. Known Issues (IDs in ../ISSUES.md)

- **GAME-03 (Medium):** badge-point edit/bulk update applies without lock/clamp → totals can drift negative vs clamped single-user path; and it skips `applyDelta` entirely (inconsistent accounting).
- **GAME-02 (Medium):** FIXED (EB-26 / P1-19) — award-read scoping: `/badge-awards/student/{id}` was fully open (id enumeration) and is now confined to self/ADMIN/mentor-of-enrollment; mentorship/badge award lists were already owner-only; badge catalog intentionally stays open (no personal data). Probes: `BadgeAwardScopingProbesTest` (8) + G11 flip in `FileNotifyBadgeProbesTest`.
- **GAME-04 (Low):** 10-badge cap race (no lock).
- **GAME-05 (Low):** `BadgeCategory` free enum use unvalidated against mentorship context (informational).
- **GAME-01 (High):** combined with leaderboard guard gap (MSH-03) — badge awards + points of any mentorship are readable by any authenticated user.

## 5. Testing

Zero tests today. Recommended: award double-prevention, points clamp, regrade-safe deltas (task path), bulk-update consistency (post-decision), cap enforcement under concurrency.

## 6. Related Documentation

[../learning-content/my-learning-and-achievements.md](../learning-content/my-learning-and-achievements.md) · [../certificates/MDS.md](../certificates/MDS.md) (rank source) · [../notifications/MDS.md](../notifications/MDS.md) (award notifications) · [../ISSUES.md](../ISSUES.md)
