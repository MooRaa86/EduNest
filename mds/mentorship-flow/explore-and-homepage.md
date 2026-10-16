# Explore, Homepage & Student Mentorship Views

Public discovery and the authenticated student home experience.

## 1. Public endpoints (permitAll verified in prod config)

| Endpoint | Behavior | Source |
|---|---|---|
| `GET /api/v1/mentorship/explore` | filters `keyword`, `category`, `minPrice`, `maxPrice`; returns only `status=ACTIVE` + not-deleted mentors; `MentorShipRepository#searchMentorShips` (L91–121) — LIKE across title/subtitle/description/category/mentor name (case-insensitive), category exact, price bounds on **base** price (UI displays `priceAfterDiscount` — mismatch MSH-07); defaults page 0/size 5 | `service/mentorShip/mentorShipServiceI` |
| `GET /api/v1/mentorship/categories` | distinct lowercased categories from ACTIVE mentorships | repo `findAllCategories` |
| `GET /api/v1/student/mentorships/{id}/overview` | mentorship details + top-3 mentorships + upcoming sessions/tasks/projects + enrollment flag for authenticated callers | `MentorshipOverviewService#getMentorshipWithEnrollmentStatus` (L62–133); underlying detail query has **no status filter** → DRAFT leak (MSH-01) |
| `GET /api/v1/student/mentorships/{id}/reviews` | paginated reviews incl. reviewer emails/image (MSH-09) | `ReviewsRepository#findMentorshipReviews` |

## 2. Authenticated student homepage (`HomePageController`, STUDENT role)

- `GET /full` — `HomePageService.getStudentHomePage`: top-2 "continue learning" (native `findContinueLearningWithProgress`), up to **3 items each** of upcoming sessions/tasks/projects (each `PageRequest.of(0,2)` post-filter) merged, sorted by due date.
- `GET /recommended` — category-affinity recommendation engine: native query weighting category-match `100 + rating*15 + enrollmentCount*0.5`, `LIMIT 5`, excludes enrolled (queries the student's own enrollments first).
- `GET /progress/{mentorshipId}` — content counts + the student's own submission counts (native `getContentProgress`). ⚠️ **No enrollment check** — any student can query any mentorship's content composition (MSH-11), and the in-course comment literally warns "don't try".
- **In-memory pagination caution:** `getUpcomingItemsByMentorship` (L161–219) loads *all* upcoming items of a mentorship then `subList(start,end)`s — unbounded sets scale badly; dev `//ToDo make only 2 items return for each type`.

## 3. My-Learning & achievements (student)

- `GET /api/v1/my-learning` (STUDENT, paged) — `MyLearningService` computes per-mentorship progress = `(submittedTasks+submittedQuizzes+submittedProjects)*100/(totals)` from native `EnrollmentRepository#findAllEnrolledMentorshipsWithProgress`; ⚠️ header KPIs computed **from the current page only**; "opened-but-not-submitted" quizzes inflate progress (LEARN-15).
- `GET /api/v1/student/achievements` — paged badges (order newest first) + graded project submissions (portfolio seeds).

## 4. Test recommendations

1. Explore respects status/category/price filters (and documents the base-price-vs-discounted decision).
2. Overview of a DRAFT mentorship as anonymous — currently leaks; assert chosen policy post-fix.
3. Recommendation determinism given a fresh student (no enrollments) — category dict empty path.
