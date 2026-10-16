# Weeks, Lectures & Curriculum Access Control

Structure and access rules of the mentorship curriculum. Tasks/projects/quizzes/sessions attach to weeks too — their own workflows live in [../learning-content/](../learning-content/MDS.md) and [../live-sessions/MDS.md](../live-sessions/MDS.md).

## 1. Model

- `entity/mentorship/Week` — `title` (NOT NULL), `mentorship` FK; `@OneToMany` cascade ALL + orphanRemoval to `tasks`, `quizzes`, `projects`, `liveSessions`, `lectures`. **No position/order field** — ordering everywhere is `ORDER BY id ASC` (`weekRepository.findByMentorship_IdOrderByIdAsc`); reordering is impossible by design. A dangling `//ToDo` marks planned file lists.
- `entity/lectures/Lecture` — `title`, `lectureUrl` (external video URL), `week` FK (NOT NULL); index `idx_lectures_week_id`. No status/publish enum, no ordering.

## 2. Mentor side (all ownership-checked)

| Endpoint | Enforcement |
|---|---|
| `POST /api/v1/week/create`, `DELETE /{id}`, `PATCH /{id}` (title only, min 3) | `WeekService` — controller `@PreAuthorize("hasRole('MENTOR')")` + `validateMentorOwnership`/`validateMentorOwnsWeek` |
| `GET /{mentorshipId}/weeks`, `GET /{weekId}/contents` | service-level MENTOR + ownership (`WeekService` L107/L116; contents list returns typed items SESSION/QUIZ/TASK/PROJECT/LECTURE) |
| `POST /lectures`, `DELETE /{id}`, `PATCH /{id}` | `LectureService` create/delete/update — `hasRole('MENTOR')`; `validateMentorOwnsLecture` via week→mentorship→mentor chain |
| `DELETE /api/v1/week/{id}` cascade | ⚠️ no `@Transactional` on the service method — works today, flagged fragile (see [../database/transactions-consistency.md](../database/transactions-consistency.md)) |

## 3. Student side (enrollment-gated)

`StudentWeekController` (`/api/v1/student/week/**`, class MENTOR-STUDENT guard) → `StudentWeekService`:
- `GET /{mentorshipId}/weeks` — enrollment check `existsByMentorShip_IdAndStudent_Email` (L66).
- `GET /{weekId}/contents` — `isStudentEnrolledInWeekMentorship` (L141).
- `GET /{mentorshipId}/weeks-with-contents` — enrollment check (L90); batch-fetches all weeks + contents + the student's completions in a small number of queries (documented anti-N+1 effort in code comments); tasks/quizzes/projects filtered to `status != DRAFT`.
- Completion flags: lectures reported **hardcoded `completed=true`** (L170, L230) — there is **no lecture-completion tracking mechanism in the system** (open question: was "watched the video → next item unlocks" ever designed? No code exists).

## 4. Access-control gaps (verified)

| Surface | Evidence | Consequence |
|---|---|---|
| `GET /lectures/{id}` | `LectureService.getLectureById` L95 — no role/enrollment/ownership | any authenticated user reads any lecture (title + external video URL `lectureUrl`) |
| `GET /lectures/week/{weekId}` | L101 — unguarded | enumerate a week's curriculum |
| `GET /lectures/mentorship/{mentorshipId}` | L106 — unguarded (fixed page 0/size 10) | enumerate a mentorship's curriculum |
| `GET /api/v1/mentorship/{mid}/get-tasks` | `mentorShipServiceI.getMentorShipTasks` L239–249 — `existsById` only | any authenticated user reads **all** tasks (incl. DRAFT) with attachment paths |
| Task/project/quiz detail GETs | see [../learning-content/MDS.md](../learning-content/MDS.md) | DRAFT & unpublished content/readability |

Compare: student week/contents reads *are* gated — the gap is specifically lectures + the mentorship-level task getter.

## 5. Test recommendations

1. Unenrolled student requesting student-week reads → 403 (positive control — already enforced).
2. Any authenticated user `GET /lectures/{id}`, `get-tasks` → decide policy; until fixed, security test asserts **leak existence is documented** (regression guard).
3. Week cascade deletion removes tasks/submission cleanup files (files deleted but points retained — noted in [../ISSUES.md](../ISSUES.md) LEARN-12).
