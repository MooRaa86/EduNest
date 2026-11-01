# Explore, Homepage & Student Mentorship Views

Public discovery and the authenticated student home experience.

## 1. Public endpoints (permitAll verified in prod config)

| Endpoint | Behavior | Source |
|---|---|---|
| `GET /api/v1/mentorship/explore` | filters `keyword`, `category`, `minPrice`, `maxPrice`; returns only `status=ACTIVE` + not-deleted mentors; `MentorShipRepository#searchMentorShips` (L91–121) — LIKE across title/subtitle/description/category/mentor name (case-insensitive), category exact, price bounds on **base** price (UI displays `priceAfterDiscount` — mismatch MSH-07); `:keyword` is bound **RAW** into the LIKE pattern (no `ESCAPE` clause — wildcard-blind, MSH-12 POC-confirmed by EB-3, see §4.4); defaults page 0/size 5 | `service/mentorShip/mentorShipServiceI` |
| `GET /api/v1/mentorship/categories` | distinct lowercased categories from ACTIVE mentorships | repo `findAllCategories` |
| `GET /api/v1/student/mentorships/{id}/overview` | mentorship details + top-3 mentorships + upcoming sessions/tasks/projects + enrollment flag for authenticated callers | `MentorshipOverviewService#getMentorshipWithEnrollmentStatus` — since P1-15 / EB-22, a **DRAFT** mentorship 404s for every caller except the owner-mentor and ADMIN (no existence leak; the underlying detail query itself is still id-only) |
| `GET /api/v1/student/mentorships/{id}/reviews` | paginated reviews incl. reviewer emails/image (MSH-09) | `ReviewsRepository#findMentorshipReviews` |

## 2. Authenticated student homepage (`HomePageController`, STUDENT role)

- `GET /full` — `HomePageService.getStudentHomePage`: top-2 "continue learning" (native `findContinueLearningWithProgress`), up to **3 items each** of upcoming sessions/tasks/projects (each `PageRequest.of(0,2)` post-filter) merged, sorted by due date.
- `GET /recommended` — category-affinity recommendation engine: native query weighting category-match `100 + rating*15 + enrollmentCount*0.5`, `LIMIT 5`, excludes enrolled (queries the student's own enrollments first).
- `GET /progress/{mentorshipId}` — content counts + the student's own submission counts (native `getContentProgress`). Since P1-17 / EB-24: **enrollment required** — otherwise `AccessDeniedException` → 403 (nonexistent ids 403 too, no existence oracle); the endpoint remains STUDENT-only by role gate. Probes: `security/probes/HomepageProgressEnrollmentProbesTest` (5).
- **In-memory pagination caution:** `getUpcomingItemsByMentorship` (L161–219) loads *all* upcoming items of a mentorship then `subList(start,end)`s — unbounded sets scale badly; dev `//ToDo make only 2 items return for each type`.

## 3. My-Learning & achievements (student)

- `GET /api/v1/my-learning` (STUDENT, paged) — `MyLearningService` computes per-mentorship progress = `(submittedTasks+submittedQuizzes+submittedProjects)*100/(totals)` from native `EnrollmentRepository#findAllEnrolledMentorshipsWithProgress`; ⚠️ header KPIs computed **from the current page only**; "opened-but-not-submitted" quizzes inflate progress (LEARN-15).
- `GET /api/v1/student/achievements` — paged badges (order newest first) + graded project submissions (portfolio seeds).

## 4. Test recommendations

1. Explore respects status/category/price filters (and documents the base-price-vs-discounted decision).
2. ~~Overview of a DRAFT mentorship as anonymous — currently leaks; assert chosen policy post-fix.~~ **DONE (P1-15 / EB-22, D-02 (a))** — DRAFT overview 404s for anonymous/non-owners; owner-mentor and ADMIN get 200. Probes: `security/probes/MentorshipDraftConfinementProbesTest` (8) + G8/G8b flips in `LeaderboardDraftProbesTest`.
3. Recommendation determinism given a fresh student (no enrollments) — category dict empty path.
4. ✅ **DONE (P0-05 / EB-3, MSH-12 — V-03 wildcard POC, 2026-11-01):** wildcard-blind keyword searching **CONFIRMED** via `security/probes/MentorshipExploreWildcardPocTest` (2 tests, prod-chain, anonymous): every match-all payload (`%`, `%%`, `%_`, `_%`, `__`) returns the **full ACTIVE candidate set** from the PUBLIC route — the keyword is destroyed as a selector, while the seeded DRAFT stays confined (the status predicate is a separate AND-term, so wildcards scope to the candidate set, not the table); and `%`/`_` inside an otherwise-literal keyword silently widen the match (`eb3-Summer%Off` vs `eb3-SummerXOff` and `eb3-Fundamentals_101` vs `eb3-FundamentalsX101` both search as **2** instead of 1). Every `[V-03]` log line echo-records the received `keyword` param (raw `%`/`_` provably reached the bound SQL value). **Fix = P4-10: escape `%`/`_` with the LIKE `ESCAPE` clause (Small — a full-text engine is NOT forced by the evidence).** H2 in-memory latencies (4–9 ms) are printed but deliberately not asserted — not representative of prod row-scan cost; severity was sized by result-set effect.
