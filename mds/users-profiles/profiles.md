# Profile Endpoints Deep-Dive

Companion to [MDS.md](MDS.md). Endpoint-level contracts as implemented.

## 1. Student self-profile (`/student/profile`, class STUDENT)

- `GET /student/profile` → badges (order `createdAt DESC`), 10 latest **graded** project submissions (`findGradedForStudentProfile`, `PageRequest.of(0,10)`), skills, 10 certificates; resolved from `SecurityContextHolder`.
- `PATCH /student/profile` ← `UpdateStudentProfileRequest` (names/bio/jobTitle/social links/skill names — **no bean validation on the request fields**): upserts `SocialMedia` by `Media` enum; skill **diff-sync** (removeIf not-present + add missing).
- `PATCH /student/profile/image` (multipart `image`) → delete old + save via `ImageStorageService.saveImage("student", id, image)` → `uploads/student/{id}-EduNest-{uuid}.{ext}`.

## 2. Mentor self-profile (`/mentor/profile`, class MENTOR + service annotations)

- `GET /mentor/profile` → `MentorProfileInformationResponse` (uses `MentorRepository.findMentorProfileByEmail`).
- `PATCH /mentor/profile` ← `UpdateMentorProfileRequest` (**strict** — `@JsonIgnoreProperties(ignoreUnknown=false)`).
- `PATCH /mentor/profile/image` → `uploads/mentor/...`.

## 3. Public mentor profile (`/api/v1/profile/mentor/**` — permitAll)

- `GET /{mentorEmail}` → stats projection (image, names, years, totalLearners, totalReviews, avgReviewRate, bio, email, social links — social links fetched separately by email).
- `GET /{mentorEmail}/mentorships` and `/reviews` (paginated; limited to ACTIVE/public mentorships), `GET /{mentorEmail}/full` (combined).

## 4. Mentor → student profile (`/profile/students/**`, class MENTOR + service `validateMentorHasAccessToStudent` → `enrollmentRepository.existsByMentorIdAndStudentId`)

- `GET /students/{studentId}` — names/email/address/KPIs/social links/points.
- `GET /students/{id}/mentorships-progress` — joined/enrolled mentorship progress rows.
- `GET /students/{id}/projects` — `status` request **param dropped** (always passes null → filter broken; USR-01).
- `GET /students/{studentId}/full-profile` — aggregation.

## 5. Admin profile & settings (`/admin/**`, ADMIN)

- `GET/PATCH /admin/profile`, `PATCH /admin/profile/image` (`uploads/admin/`), `POST /admin/profile/change-email/request|confirm` (OTP to the **new** address, 2-min), `PATCH /admin/profile/change-password`, `GET/PATCH /admin/commission` (global rate).

## 6. Skills (`/student/skills`, class STUDENT)

- `StudentSkillService` backs the skill set on the student profile; skills are free-text strings (`StudentSkill.skill`), no taxonomy.

## 7. Dashboards (related composition surfaces)

- **Mentor dashboard** `/api/v1/dashboard/**` — cards (students counted as **enrollments**), sales chart (net revenue), upcoming sessions, reviews, students w/ per-mentorship counts, full dashboard. Service annotations on most methods; `getSalesChartData`/`getStudents` lack them (URL-gated only in prod).
- **Mentorship dashboard** `MentorshipDashboardService` — per-mentorship counts (lessons/quizzes/assignments/sessions/projects), top learners, ranks; email-match enforced in the repository WHERE; `getStats` unannotated but email-match still applied.
- **Admin dashboard** `/api/v1/admin/dashboard/**` — totals (gross revenue), monthly session chart, top mentors (gross), admin notifications page, full aggregate.
- **Admin payments** `/api/v1/admin/payments/**` — monthly gross chart, paged enrollment payments (incl. `platformProfit`), full page.

## 8. Test recommendations

1. Mentor may view only students they mentor (403 otherwise — positive control on the existing guard).
2. Image replace deletes previous file (currently old-delete-then-new-save; failure leaves broken URL — see [../file-storage/MDS.md](../file-storage/MDS.md)).
3. `students/{id}/projects?status=` — document or fix the ignored filter.
