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
| G1 | **Chat room subscribe/send over STOMP** — EB-19: SUBSCRIBE runs the D-14 READ rule (enrollment proxy or owning mentor), SEND the WRITE rule (explicit member or owning mentor) via `service/chat/ChatAuthorization`; violations → frame dropped + authored error on the violator's `/user/{email}/queue/errors` (wire-real contract, P2-05 / EB-39); `saveMessage` re-checks WRITE (defense in depth) | `config/webSocket/JwtChannelInterceptor` (guards active); `service/chat/ChatAuthorization`; `ws` probes `WSRoomAuthorizationProbesTest` (10) + socket matrix `WSAuthMatrixIntegrationTest` (9, EB-39) | closed — was: any authenticated WS user (read+write any room) |
| G2 | **Private conversation history** — EB-20: requires conversation participant (student or mentor of pair) or ROLE_ADMIN; outsiders denied with 403 Forbidden | `ConversationRestControllers#getMessages` → `ConversationService.getConversationMessages`; probe `ConversationProbesTest.g2_` | closed — was: any authenticated user by probing conversationId |
| G3 | **Private message edit & delete** — EB-20: sender ownership enforced; outsiders denied with 403 Forbidden; events route strictly to server-derived recipient | `ConversationService.editMessage`, `ConversationService.deleteMessage`; probe `ConversationProbesTest.g3_` | closed — was: any authenticated user |
| G4 | **Lecture reads** (3 GET endpoints) | `LectureService` applies `ContentReadGuard` to the lecture/week/mentorship's actual course | owner mentor, enrolled student or ADMIN; others denied |
| G5 | **Mentorship task list** `GET /api/v1/mentorship/{mid}/get-tasks` | `mentorShipServiceI.getMentorShipTasks` applies the guard and filters student-visible statuses | owner mentor, enrolled student or ADMIN; student drafts hidden |
| G6 | **Task/project detail + mentorship filters and student details** | task/project services apply the guard before returning content or pagination metadata | owner mentor, enrolled student or ADMIN; student drafts hidden |
| G7 | **Mentorship leaderboard** | EB-23: service checks principal role and enrollment/ownership or ADMIN | outsiders denied; rows expose the five fields approved by the D-04 amendment (Ruler B): `studentFullName`, `studentEmail`, `studentProfileImageUrl`, `rank`, `totalPoints`; `studentLevel`/badges excluded; review pending |
| G8 | **Mentorship DRAFT access** — EB-22 (P1-15): DRAFT confined to owner-mentor/ADMIN per D-02 default (a) — detail + student overview return 404 to every other caller (no existence/status leak); public paged overview excludes DRAFT; explore/categories/top-mentorships/recommendations verified ACTIVE-only; join already rejected DRAFT | `mentorShipServiceI#getMentorShipById`; `MentorshipOverviewService#getMentorshipWithEnrollmentStatus`; `MentorShipRepository#findAllByStatusNot`; `ContentReadGuard#isOwnerMentorOrAdmin`; probes `MentorshipDraftConfinementProbesTest` (8) + G8/G8b contract flips in `LeaderboardDraftProbesTest` | closed — was: any user incl. anonymous for the overview path |
| G9 | **Generic file endpoint** `GET /files?filePath=...` | EB-27 resolves current database path and applies typed-resource checks; unknown/ambiguous paths deny | implementation pending Security/Lead review |
| G10 | **Notifications: user-to-user send** — **CLOSED (EB-18 / P1-11)**: `POST /api/v1/notifications/send` deleted per Decision D-05 (now 404 Not Found) | Probes in `FileNotifyBadgeProbesTest.g10_userPushesNotificationToAnyone` verify 404 | arbitrary notification spam closed |
| G11 | **Badge award reads** | `BadgeAwardController` GETs unannotated; `BadgeAwardService#getAwardsByStudent` L108–112 no ownership check | any authenticated user |
| G12 | **Room creation ownership** — EB-17 (P1-10): `createRoom` verifies mentorship ownership (`SecurityService#isMentorOwnMentorship` → `existsByIdAndMentor_Email`) before creating — foreign/non-existent mentorship id → 403 (uniform, no id-space oracle), owner → 200; SEL-09 closed in the same ticket (`AdminBadgeController` class-level `hasRole('ADMIN')`, dashboard `getStats`/`getSalesChartData`/`getStudents` got the sibling `hasRole('MENTOR')`) | `ChatRoomService#createRoom`; probes `MethodSecuritySweepProbesTest` (foreign 403 + no room persisted, owner 200 + row persisted, unknown-id 403, student 403 on the three dashboard reads, admin-badge 403/201 parity) | closed — was: MENTOR gate only, no mentorship-ownership check (any mentor could create rooms in any mentorship) |
| G13 | **Room "membership" = mentorship-enrollment proxy** — EB-19: unified into the two D-14 rules — READ stays the enrollment proxy (documented bridge while `chat_room_members` is sparse), WRITE requires an explicit member row or the owning mentor | `service/chat/ChatAuthorization` (READ/WRITE/PRIVATE); `AuthorizationRepository.isUserMemberOfChatRoom` L82–93; `MembersRepo.existsByChatRoom_IdAndUser_Email` | resolved — semantics centralized (was: 3 inconsistent rules) |
| G14 | **Profile image folder mismatch** — the previous rules listed names different from actual folders | Proposed EB-28 policy uses actual public folder names; admin is non-public | pending review; see §4 |
| G15 | **Question re-parenting** | `QuestionServiceImp#updateQuestion` — target quiz ownership not validated before `question.setQuiz(…)` | mentor can move their question into another mentor's quiz |

Correctly-enforced (verified, for balance): grading ownership (task/project/quiz), week/lecture/task/project mentor ownership, submission enrollment gates (task/project/quiz submit paths), notification mark-read/delete ownership, file downloads through the 4 typed `/files/**` endpoints (`FileAccessService`), certificate issuance mentor ownership, live-session audience checks (`joinSession` enrolled/mentor), admin-badge award flow.

## 4. Public-surface privacy effects

- Public mentorship reviews expose `studentFullName`, `studentEmail` and `userProfileImageUrl`; public mentor-profile reviews expose `userName`, `userEmail` and `userProfileImageUrl`. The temporary D-04 compatibility exception is Engineering Lead approved. Frontend usage verification remains pending, and future removal of reviewer emails requires coordination with the separate frontend project. User-supplied review text remains unchanged.
- The leaderboard is NOT public (D-04 amendment, Ruler B — Engineering Lead-approved 2026-10-31; supersedes the earlier name-and-points-only leaderboard-row rule for the leaderboard surface only). Enrollment rows remain the membership condition, so student rows are the five-field Ruler B contract (`studentFullName`, `studentEmail`, `studentProfileImageUrl`, `rank`, `totalPoints`); `studentLevel` and badge fields stay excluded. Page/count and current-user rank are retained as technical metadata. Anonymous requests are 401, outsiders and nonexistent mentorships are 403. The row-contract change requires frontend coordination.
- Public mentor profile (`/api/v1/profile/mentor/**`) exposes mentor email as identifier.
- Swagger enabled anonymously in prod (`/swagger-ui/**`).
- `/uploads/**` permits only `mentorship-cover-images`, `student`, `mentor`, and `chatroom`; other folders are denied by the chain and lack explicit MVC handlers. EB-27 implements a separately authenticated generic path with current-reference checks; FE use and Security/Lead review remain unconfirmed.

## 5. Open questions

EB-21 / D-03: the production chain rejects anonymous callers with 401; authenticated outsiders and missing content are denied with 403. Student task/project reads expose only PUBLISHED or CLOSED content; owner mentors and ADMIN can read drafts. Student-submission projections and student-week membership checks use the authenticated identity, not a caller-supplied email. An enrollment row is the current authoritative membership condition; there is no individual unenrollment or revocation feature. Any future cancellation or revocation feature must revisit the content-read checks. Account deactivation and deletion are enforced separately by JWT validation. Frontend coordination and Security/Lead review remain pending.

- Confirm intended access for `GET /lectures/**` and `GET /api/v1/mentorship/{id}/get-tasks` (closed-content leak vs accidental omission).
- Confirm whether `/api/v1/notifications/send` should be admin-only (it was likely added for testing; it is a live phishing vector).
- Confirm desired room-membership semantics: enrollment-proxy (current REST) vs explicit room membership (`chat_room_members`).
