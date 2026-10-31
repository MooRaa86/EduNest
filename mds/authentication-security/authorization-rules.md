# Authorization Rules — URL Matrix, Method Security, Object-Level Checks

How "who may do what" is enforced (and where it isn't). Companion: [security-configuration.md](security-configuration.md) (URL matchers verbatim), [../ISSUES.md](../ISSUES.md) (full issue list incl. IDOR group).

## 1. Enforcement layers (as built)

1. **Authentication gate (prod only):** URL matchers in `ProjectSecurityProdconfig` + `.anyRequest().authenticated()`.
2. **Role gate:** `@PreAuthorize("hasRole('X')")` on controllers/services (`@EnableMethodSecurity`).
3. **Object-level gate:** service-layer checks — `SecurityService` helpers and `AuthorizationRepository` existence queries (`service/security/SecurityService`, `repository/AuthorizationRepository`), invoked with `CurrentUserProvider`/`SecurityContextHolder` email.
4. **Dev profile:** layers 1–3 remain only where annotations exist; layer 1 is absent (`permitAll`).

## 2. Controller-level `@PreAuthorize` inventory (verified survey)

Class-level guards:

| Controller | Guard |
|---|---|
| `StudentProfileController`, `MyLearningController`, `HomePageController`, `StudentWeekController`, `StudentAchievementController`, `StudentSkillsController`, `StudentCertificatesController` | `hasRole('STUDENT')` |
| `MentorProfileInfoController`, `MentorViewStudentProfileController` | `hasRole('MENTOR')` |
| `SettingsController`, `NotificationController`, `FileController`*(none — see §3)* | `isAuthenticated()` (settings/notifications only) |
| `AdminUserDirectoryController`, `AdminProfileAndSettingsController`, `AdminNotificationController`, `AdminPaymentsController`, `AdminDashboardController` | `hasRole('ADMIN')` |
| `AdminBadgeController` | **no class-level guard** (relies on prod URL rules + service annotations) |
| `ContactMessageController` | **no annotations at all** (URL-rule only; public save-message intended) |
| `MentorProfileForStudent` | none (public by design) |
| `MentorDashboardController` | none (service-level `hasRole('MENTOR')` on most methods; **`getSalesChartData` and `getStudents` lack it**) |

Method-level guards (representative, verified lines):
- Mentorship management: `mentorShipControllers` L86/101/117/138/149/160/179/192 → MENTOR; L204/218 → STUDENT.
- Weeks: `WeekController` L29/39/48 → MENTOR.
- Lectures: `LectureController` — **no annotations at all**; guards live in `LectureService` create/delete/update only — the three **GETs have no gate** (see §3).
- Tasks: `TaskController` 7× MENTOR; `TaskSubmissionController` L30 STUDENT, L60 MENTOR. **`GET /{id}`, `/filter/{msid}`, `/{taskId}/student` unannotated.**
- Projects: `ProjectController` 6× MENTOR; `ProjectSubmissionController` L30 STUDENT, L45/60 MENTOR. **`GET /{id}`, `/filter/{msid}` unannotated.**
- Quizzes: `QuizController` 9× MENTOR, L112 STUDENT; `QuestionController` 5× MENTOR, L77 STUDENT; `QuizSubmissionController` L33/58 STUDENT, L44/70/86 MENTOR.
- Live sessions: `LiveSessionController` — MENTOR×6, STUDENT×3, `getSession` unannotated (service membership check).
- Badges: `BadgeController` create/update/delete MENTOR (GETs open); `BadgeAwardController` POST MENTOR (3 GETs open).
- Admin: `AdminController` — **`getAllAdmins`'s `@PreAuthorize` commented out** (L33).
- Dashboard service-level: `DashboardService#getDashboardCardsDetails/getReviewsInDashboard/getUpcomingSessionsForDashboard/getFullDashboard` MENTOR; `MentorshipDashboardService` MENTOR on 5 methods (**`getStats` unannotated** but email-match enforced inside its repository WHERE).

## 3. Verified object-level gaps (IDOR-style)

The team's own `legacy-audits/IDOR_Audit_Report.md` tracked protection roll-out; the following gaps are **confirmed by re-reading current code** (re-verified Oct 2026):

| # | Surface | Evidence | Who can exploit |
|---|---|---|---|
| G1 | **Chat room subscribe/send over STOMP** — membership checks commented out | `config/webSocket/JwtChannelInterceptor` L87–105, L130–140; `ChatMessageService.saveMessage` has no membership check | any authenticated WS user (read+write any room) |
| G2 | **Private conversation history** | `ConversationRESTControllers#getMessages` → `ConversationService.getConversationMessages` (no ownership check) | any authenticated user, by probing `conversationId` |
| G3 | **Private message edit** — ownership check commented out | `ConversationService.editMessage` L119–121 | any authenticated user |
| G4 | **Lecture reads** (3 GET endpoints) | `LectureController` (no annotations), `LectureService.getLectureById/getLecturesByWeekId/getLecturesByMentorshipId` (L95/101/106) | any authenticated user (media URL exposure) |
| G5 | **Mentorship task list** `GET /api/v1/mentorship/{mid}/get-tasks` | `mentorShipServiceI.getMentorShipTasks` L239–249 — only `existsById` | any authenticated user (incl. DRAFT/unpublished tasks, attachment paths) |
| G6 | **Task/project detail + mentorship filters** | `TaskController#getById`, `TaskServiceImpl#getTaskById`; `ProjectController#getById`; `/filter/{msid}` variants | any authenticated user |
| G7 | **Mentorship leaderboard** | EB-23: service checks principal role and enrollment/ownership or ADMIN | outsiders denied; rows expose the five fields approved by the D-04 amendment (Ruler B): `studentFullName`, `studentEmail`, `studentProfileImageUrl`, `rank`, `totalPoints`; `studentLevel`/badges excluded; review pending |
| G8 | **Mentorship DRAFT access** | `getMentorShipById`/`getMentorShips` no status filter; public overview query `findMentorshipDetailsById` has only `WHERE m.id = :id` (`MentorShipRepository` L244–248) | any user incl. anonymous for the overview path |
| G9 | **Generic file endpoint** `GET /files?filePath=...` | `FileController#serveFile` now denies all requests; typed endpoints perform ownership checks | disabled pending ownership-aware replacement and review |
| G10 | **Notifications: user-to-user send** | `POST /api/v1/notifications/send` — class guard `isAuthenticated()` only; `NotificationService.sendToUserByEmail` has no admin gate | any authenticated user can push arbitrary notification content to any email's `/queue/notifications` |
| G11 | **Badge award reads** | `BadgeAwardController` GETs unannotated; `BadgeAwardService#getAwardsByStudent` L108–112 no ownership check | any authenticated user |
| G12 | **Room creation ownership gap** | `ChatRoomService.createRoom` L43–79 — MENTOR gate but no mentorship-ownership check | any mentor can create rooms in any mentorship |
| G13 | **Room "membership" = mentorship-enrollment proxy** | `AuthorizationRepository.isUserMemberOfChatRoom` L82–93 — enrolled students can read rooms they never joined; the room-join (`joinRoom`) uses a different student-only query | inconsistent membership semantics |
| G14 | **Profile image folder mismatch** — the previous rules listed names different from actual folders | Proposed EB-28 policy uses actual public folder names; admin is non-public | pending review; see §4 |
| G15 | **Question re-parenting** | `QuestionServiceImp#updateQuestion` — target quiz ownership not validated before `question.setQuiz(…)` | mentor can move their question into another mentor's quiz |

Correctly-enforced (verified, for balance): grading ownership (task/project/quiz), week/lecture/task/project mentor ownership, submission enrollment gates (task/project/quiz submit paths), notification mark-read/delete ownership, file downloads through the 4 typed `/files/**` endpoints (`FileAccessService`), certificate issuance mentor ownership, live-session audience checks (`joinSession` enrolled/mentor), admin-badge award flow.

## 4. Public-surface privacy effects

- `/api/v1/student/mentorships/{id}/reviews` and public mentor-profile review lists expose reviewer display names and reviewer profile images (`userProfileImageUrl`), while reviewer emails stay private (no email fields in any review payload). The image restore is a D-04 follow-up amendment pending Engineering Lead confirmation — the record in `plan/DECISIONS-REQUIRED.md` stays authoritative until Omar approves. User-supplied review text remains unchanged.
- The leaderboard is NOT public (D-04 amendment, Ruler B — Engineering Lead-approved 2026-10-31; supersedes the earlier name-and-points-only leaderboard-row rule for the leaderboard surface only). Enrollment rows remain the membership condition, so student rows are the five-field Ruler B contract (`studentFullName`, `studentEmail`, `studentProfileImageUrl`, `rank`, `totalPoints`); `studentLevel` and badge fields stay excluded. Page/count and current-user rank are retained as technical metadata. Anonymous requests are 401, outsiders and nonexistent mentorships are 403. The row-contract change requires frontend coordination.
- Public mentor profile (`/api/v1/profile/mentor/**`) exposes mentor email as identifier.
- Swagger enabled anonymously in prod (`/swagger-ui/**`).
- `/uploads/**` — proposed EB-28 policy permits only `mentorship-cover-images`, `student`, `mentor`, and `chatroom`; other folders are denied by the chain and lack explicit MVC handlers. The generic `/files?filePath=` endpoint is disabled as a temporary cross-scope safeguard, subject to Eng Lead and FE approval; EB-27's guard-first replacement remains open. FE coordination is required for submission and attachment links, generic downloads, and admin avatars. Review and verification remain pending.

## 5. Open questions

- Confirm intended access for `GET /lectures/**` and `GET /api/v1/mentorship/{id}/get-tasks` (closed-content leak vs accidental omission).
- Confirm whether `/api/v1/notifications/send` should be admin-only (it was likely added for testing; it is a live phishing vector).
- Confirm desired room-membership semantics: enrollment-proxy (current REST) vs explicit room membership (`chat_room_members`).
