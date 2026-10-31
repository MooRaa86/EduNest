# API Endpoint Inventory

All base paths and route mappings verified directly against `@RequestMapping`/`@(Get|Post|Put|Delete|Patch)Mapping` annotations (grepped). Auth column reflects the *strictest* verified gate (URL rule / `@PreAuthorize` / service check); details and gaps: [../authentication-security/authorization-rules.md](../authentication-security/authorization-rules.md). Controls: 🔓 public in prod, 🔐 authenticated, ⟡ role-tagged. DTO names included where audits captured them.

## 1. Auth & registration (public by design)

| Endpoint | Purpose |
|---|---|
| 🔓 POST `/login-api` | Login → raw JWT in `Authorization` response header; `apiResponse.status` and `apiResponse.Role` remain. `apiResponse.jwt` is included only when `app.auth.return-token-in-body=true` (transitional default; env `APP_AUTH_RETURN_TOKEN_IN_BODY`). Set false after frontend consumers migrate to the header. Required nonblank email/password and email format are validated; login imposes no new password-length rule. |
| 🔓 POST `/forget-password` | Send reset OTP (`ForgetPasswordRequestDto`) |
| 🔓 POST `/forget-password/verify-otp` | Verify OTP and return a one-time reset token in `apiResponse.resetToken` (`Cache-Control: no-store`) |
| 🔓 POST `/forget-password/reset` | Set new password with email, `resetToken` and `newPassword` (`ResetPasswordRequestDto`) |
| 🔓 POST `/api/v1/register/student` · `/mentor` | Register + verify OTP (`StudentRequestDto` / `MentorRequestDto`) |
| 🔓 POST `/api/v1/register/verify-user` | Validate required email and six-digit numeric `otp`, then verify → `enabled=true`. |
| 🔓 POST `/api/v1/register/send-otp` | Resend verification OTP using required nonblank, valid `email` request parameter. |
| 🔓 POST `/api/v1/register/restore` · `/confirm-restore` | Restore deleted account via OTP |
| 🔓 POST `/api/v1/admin/register` | First-admin bootstrap (`RegisterAdminRequest`) — **one-shot**: succeeds only while zero admins exist (`400 ERR_BUSINESS_RULE "Admin bootstrap is no longer available."` afterwards); response carries **identity only** (no credentials) (P1-09/EB-16) |
| 🔓 POST `/send` | Platform free-form mail (`MailRequest{email,name,message}`) — `PlatformMailController`; **effective path is bare `/send`** due to `@RestController("/mail")` bean-name quirk (no path mapping) — public in prod (MAIL-01) |

Invalid fields on login, verify-user and send-otp return HTTP 400 `ERR_VALIDATION` with a `fields` map; malformed JSON retains `ERR_MALFORMED_BODY`. Frontend coordination has been requested on EB-32; approval is not implied.

## 2. Account settings (authenticated)

| Endpoint | Purpose |
|---|---|
| 🔐 POST `/settings/request-email-change` | OTP to the *new* email (`ChangeEmailRequest`) |
| 🔐 PATCH `/settings/confirm-email-change?otpCode` | Apply pending email → forces re-login |
| 🔐 PATCH `/settings/password` | Change password (old pwd + confirm) |
| 🔐 POST `/settings/delete` · `/confirm-delete?otp` | Soft-delete account (OTP template `delete-account.html`) |
| 🔐 POST `/settings/deactivate?password` | `enabled=false` |

## 3. Admin (ADMIN role ⟡ unless noted)

| Endpoint | Purpose |
|---|---|
| ⟡ GET `/api/v1/admin` | List admins — **identity only, no hash material** (P1-09/EB-16 removed the password field from the response; `@PreAuthorize` stays commented out — route-level `hasRole("ADMIN")` covers it) |
| ⟡ DELETE `/api/v1/admin/{email}` | Delete admin account — **the last remaining admin cannot be deleted** (`400 ERR_BUSINESS_RULE "Cannot delete the last remaining admin."`); deletion is transactional (previously a bare 500 — fixed in EB-16) |
| ⟡ GET `/api/v1/admin/users/{id}` · `/role/{roleName}` · `/dashboard-summary` | User directory, paged search, KPIs |
| ⟡ POST `/api/v1/admin/users/send-email` | Direct email to a user (`admin-message.html`) |
| ⟡ POST `/api/v1/admin/users/send-notification` | Direct in-app notification (SUPPORT) |
| ⟡ GET `/api/v1/admin/dashboard/cards` · `/sessions-chart` · `/top-mentors` · `/full` | Platform KPIs, monthly chart, top mentors (gross revenue) |
| ⟡ GET `/api/v1/admin/dashboard/notifications` | Admin notification inbox page |
| ⟡ GET `/api/v1/admin/payments/sales-chart` · `/enrollments` · `/full` | Monthly gross chart, paged payments (with `platformProfit`), combined page |
| ⟡ GET `/api/v1/admin/notifications` · POST `/send` · DELETE `/{id}` · `/delete-all` | Admin notification CRUD & test-send |
| ⟡ POST/PATCH/DELETE `/api/admin/badges(/{badgeId}|/delete/{badgeId}|/assign|/{userBadgeId})` | Platform badge CRUD + assign (paths lack base prefix) |
| 🔐 GET `/api/users/{userId}/badges` · GET `/api/admin/badges` | Badge views — authenticated by URL rule only (SEL-09) |
| ⟡ GET/PATCH `/admin/profile`, PATCH `/admin/profile/image` | Admin own profile |
| ⟡ POST `/admin/profile/change-email/request|confirm`, PATCH `/admin/profile/change-password` | Admin credential changes (OTP) |
| ⟡ GET/PATCH `/admin/commission` | Global commission rate view/update |

\* `AdminUserDirectoryController` class is `hasRole('ADMIN')`; full DTO list in the admin area of the graduation docs (routes verified here).

## 4. Mentorship

| Endpoint | Gate | Purpose |
|---|---|---|
| 🔓 GET `/api/v1/mentorship/explore` (`keyword,category,minPrice,maxPrice,page,size`) | public | Active mentorship search |
| 🔓 GET `/api/v1/mentorship/categories` | public | Distinct categories |
| 🔓 GET `/api/v1/mentorship/{id}` | authenticated | Full details — **no status filter** (MSH-01) |
| 🔐 GET `/api/v1/mentorship` | authenticated | Paged list — all statuses (MSH-01) |
| ⟡ POST `/api/v1/mentorship` (MENTOR) | role | Create (`mentorShipCreateDTO`) |
| ⟡ PATCH `/api/v1/mentorship/{mid}` (MENTOR) | role+owner | Update |
| ⟡ DELETE `/api/v1/mentorship/{mid}` (MENTOR) | role+owner | Delete (DRAFT-only) |
| ⟡ POST `/api/v1/mentorship/update-status/{id}` (MENTOR) | role+owner | Lifecycle transition (`ChangeStatusRequest`) |
| ⟡ POST `/api/v1/mentorship/{mentorshipId}/change-cover-image` (MENTOR, multipart) | role+owner | Upload cover (5MB cap, header-based MIME) |
| ⟡ DELETE `/api/v1/mentorship/{mentorshipId}/delete-cover-image` (MENTOR) | role+owner | Delete cover |
| 🔐 GET `/api/v1/mentorship/{mid}/get-tasks` | authenticated-only | **Task list of any mentorship incl. DRAFT, unguarded ownership/enrollment** (MSH-02) |
| ⟡ GET `/api/v1/mentorship/count-for-mentor`, `/count-students-for-mentor` (MENTOR) | role | Counts |
| ⟡ POST `/api/v1/mentorship/{mentorshipId}/join` (STUDENT) | role | Enroll (ACTIVE-only; commission snapshot) |
| ⟡ POST `/api/v1/mentorship/{mentorshipId}/rate` (STUDENT) | role+enrolled | Create review (`CreateReviewRequest`) |

Student-side views (see also [../mentorship-flow/explore-and-homepage.md](../mentorship-flow/explore-and-homepage.md)):

| Endpoint | Gate | Purpose |
|---|---|---|
| 🔓 GET `/api/v1/student/mentorships/{id}/overview` | public | Details + top-3 + upcoming + enrollment flag |
| 🔓 GET `/api/v1/student/mentorships/{id}/reviews` | public | Paged reviews; reviewer display name only (no email or profile image) |
| 🔐 GET `/api/v1/student/mentorships/mentor/{mentorEmail}/top-mentorships` | authenticated | Mentor's top-list |
| 🔐 GET `/api/v1/student/mentorships/{id}/leaderboard` | enrolled student, owning mentor or ADMIN | Rows and current-user expose `studentFullName`, `studentEmail`, `studentProfileImageUrl`, `rank` and `totalPoints` — the D-04 amendment (Ruler B, Engineering Lead-approved 2026-10-31): enrollment-gated access supersedes the earlier name-and-points-only leaderboard-row rule; `studentLevel` and badge fields stay excluded. Pagination, total count and `userRank` remain. Existing off-page current-user append is retained. |

## 5. Weeks, lectures (curriculum)

| Endpoint | Gate | Purpose |
|---|---|---|
| ⟡ POST `/api/v1/week/create`, DELETE `/api/v1/week/{id}`, PATCH `/api/v1/week/{id}` (MENTOR) | role+owner | Week CRUD (title ≥3 chars) |
| ⟡ GET `/api/v1/week/{mentorshipId}/weeks`, `/{weekId}/contents` (MENTOR) | role+owner | Curriculum views incl. typed content items |
| ⟡ GET `/api/v1/student/week/{mentorshipId}/weeks`, `/{weekId}/contents`, `/{mentorshipId}/weeks-with-contents` (STUDENT, class) | role+enrolled | Student curriculum (DRAFT content filtered; lecture completion flags **hardcoded true**) |
| ⟡ POST/DELETE/PATCH `/lectures`… (MENTOR via service) | role+owner | Lecture CRUD |
| 🔐 GET `/lectures/{id}` · `/lectures/week/{weekId}` · `/lectures/mentorship/{mentorshipId}` | authenticated-only | **Unguarded curriculum reads** (MSH-02/G4) |

## 6. Tasks

| Endpoint | Gate | Purpose |
|---|---|---|
| ⟡ POST `/api/v1/task` (multipart) · PATCH `/{id}/status` · PATCH `/{id}` (multipart) · DELETE `/{id}` (MENTOR) | role+owner | Task CRUD (`CreateTaskRequest`), publish/close, attachment mgmt |
| ⟡ GET `/api/v1/task/dashboard/{mentorshipId}` · `/full-dashboard/{mentorshipId}` (MENTOR) | role+owner | Dashboards |
| ⟡ GET `/api/v1/task/{taskId}/statistics` (MENTOR) | role+owner | Stats (**pendingReview bug**, LEARN-04) |
| 🔐 GET `/api/v1/task/{id}` · `/filter/{msid}` | authenticated-only | **Unguarded reads** (LEARN-02) |
| 🔐 GET `/api/v1/task/{taskId}/student` | authenticated-only | Task + own submission — **no enrollment check** (LEARN-02) |
| ⟡ GET `/api/v1/task-submission/{taskId}` (MENTOR) | service-validated | Paged submissions of a task |
| ⟡ POST `/api/v1/task-submission/{taskId}` (STUDENT, multipart `file`/`fileUrl`) | role+enrolled | Submit (unique row per task+student; resubmit overwrites) |
| ⟡ POST `/api/v1/task-submission/{submissionId}/grade` (MENTOR) | role+owner | Grade (`GradeTaskSubmissionRequest`) |

## 7. Projects

Same twin structure under `/api/v1/project` (shared base for `ProjectController` + `ProjectSubmissionController`):

- ⟡ POST `` (multipart), PATCH `/{id}/status`, PATCH `/{id}` (multipart), DELETE `/{id}` — MENTOR role+owner.
- ⟡ GET `/{projectId}/statistics`, `/full-dashboard/{mentorshipId}` — MENTOR.
- 🔐 GET `/{id}` · `/filter/{msid}` — authenticated-only (**unguarded**, LEARN-02).
- ⟡ GET `/{projectId}/student` — STUDENT role + **enforced enrollment** (`validateEnrolled`).
- ⟡ POST `/{projectId}/submissions` (STUDENT, multipart, enforced enrollment).
- ⟡ GET `/{projectId}/submissions` (MENTOR), POST `/submissions/{submissionId}/grade` (MENTOR).

## 8. Quizzes

| Endpoint | Gate | Purpose |
|---|---|---|
| ⟡ POST `api/v1/quiz` · PATCH/DELETE `/{id}` · POST `/change-status/{id}` (MENTOR) | role+owner | Quiz CRUD + status (`?status=`) |
| ⟡ GET `api/v1/quiz/{id}` | role-conditional | Details (mentor or enrolled; DRAFT hidden from students) |
| ⟡ GET `/filter/{mentorshipId}` · `/dashboard/{mentorshipId}` · `/statistics/{id}` · `/mentorshipQuizzesOverview/{mentorshipId}` · `/QuizOverview/{quizId}` (MENTOR) | role+owner | Dashboards/overviews |
| ⟡ GET `/student-quizzes/{mentorshipId}` (STUDENT) | role+enrolled | Student quiz list |
| ⟡ POST `api/v1/question` · PATCH `/{id}` · DELETE `/{quizId}/{questionId}` (MENTOR) | role+owner (create only while DRAFT) | Question authoring (`QuestionCreateDTO`) |
| ⟡ GET `api/v1/question/student/fetch/{quizId}` (STUDENT) | role+enrolled+PUBLISHED | **Starts attempt timer** on first fetch (side effect) |
| ⟡ GET `api/v1/question/fetch/{quizId}` · `/{id}` (MENTOR) | role+owner | Authoring views |
| ⟡ POST `api/v1/submit-quiz-answer/{quizId}` (STUDENT) | role+enrolled | Submit answers → auto-grade (single attempt, late −1 pt/min) |
| ⟡ GET `api/v1/answer/{studentId}/{quizId}` (MENTOR) | role+owner | Inspect a student's answers |
| 🔐 GET `api/v1/my-answers/{quizId}` (STUDENT) | role+enrolled | **Answer-key leak — returns correctAnswer pre-submission** (LEARN-01, Critical) |
| ⟡ GET `api/v1/submissions/quiz/{quizId}` (MENTOR) | role+owner | Submission list ("Passed"/"Failed" ≥50%) |
| ⟡ GET `api/v1/submissions/student/{studentId}` (MENTOR) | role (access-to-student check) | All quiz submissions of one student |

## 9. Live sessions (`/api/v1/liveSession`)

| Endpoint | Gate |
|---|---|
| ⟡ POST `/create` · PATCH `/update/{sessionId}` · DELETE `/delete/{sessionId}` · POST `/start/{sessionId}` · POST `/end/{sessionId}` · POST `/snapshot/{sessionId}` · GET `/attendance/{sessionId}` — MENTOR (owner via service) |
| ⟡ GET `/join/{sessionId}` · GET `/myAttendance/{sessionId}` · GET `/student/upcoming` — STUDENT (enrolled via service) |
| 🔐 GET `/{sessionId}` — either role via audience check; GET `/mentorship` — mentor's session list |

Details incl. attendance math: [../live-sessions/MDS.md](../live-sessions/MDS.md).

## 10. Chat REST

Base `/api/v1/chat-room` and `/api/v1/conversation` — full table with auth semantics: [../websocket/messaging-flow.md](../websocket/messaging-flow.md) (⚠️ includes the IDOR list).

## 11. Notifications (`/api/v1/notifications`, authenticated)

| Endpoint | Purpose |
|---|---|
| 🔐 GET `` · `/unread` · `/unread/count` (paged) | Inbox; 🔐 unbounded `size` |
| 🔐 PATCH `/mark-all-read` · `/{id}/mark-read` | Read ops (ownership-checked) |
| 🔐 DELETE `/delete/{id}` · `/delete-for-user` | Deletions (ownership-checked) |
| 🔐 POST `/send` | **Any-user → any-email push** (`NotificationDto` request; NOTIF-01) |

Admin mirror: `/api/v1/admin/notifications` (⟡ list/delete/delete-all/send).

## 12. Profiles & skills

| Endpoint | Gate |
|---|---|
| 🔐 GET/PATCH `/student/profile` · PATCH `/student/profile/image` (STUDENT class) | Self profile + image (`uploads/student/…`) |
| ⟡ GET/PATCH `/mentor/profile` · PATCH `/mentor/profile/image` (MENTOR) | Strict `UpdateMentorProfileRequest` |
| 🔓 GET `/api/v1/profile/mentor/{mentorEmail}` · `/{mentorEmail}/mentorships` · `/reviews` · `/full` | **Public mentor profile & content** |
| ⟡ GET `/profile/students/{studentId}` · `/students/{studentId}/mentorships-progress` · `/students/{studentId}/projects` (`status` param **ignored** — USR-01) · `/{studentId}/full-profile` (MENTOR + access check) | Mentor→student view |
| 🔐 GET/POST/DELETE `/student/skills` (STUDENT) | Skill CRUD (free-text) |

## 13. Badges & awards

| Endpoint | Gate |
|---|---|
| ⟡ POST/PATCH/DELETE `/api/v1/badges(/{id})` (MENTOR role; ownership in service) | Create/patch/delete (cap 10/mentorship) |
| 🔐 GET `/api/v1/badges/{id}` · `/mentorship/{id}` | Open reads |
| ⟡ POST `/api/v1/badge-awards/{badgeId}/award` (MENTOR; enrolled-student check) | Award (`AwardBadgeRequest{studentId,note}`) |
| 🔐 GET `/api/v1/badge-awards/…` | Award listings by mentorship/student/badge — **no ownership check** (GAME-02) |

## 14. Certificates & student aggregates

- 🔐 GET `/api/v1/student/certificates` (STUDENT, paged) — ranked list projection.
- 🔐 GET `/api/v1/my-learning` (STUDENT, paged) — progress aggregation ([../learning-content/my-learning-and-achievements.md](../learning-content/my-learning-and-achievements.md)).
- 🔐 GET `/api/v1/student/achievements` (STUDENT, paged) — badges + graded projects.
- 🔐 GET `/api/v1/student/week/**` (see §5) · 🔐 GET `/api/v1/homepage/full|progress|recommended` (STUDENT).

## 15. Dashboards (mentor)

Base `/api/v1/dashboard` (class/routes verified):
`GET /cards`, `/reviews`, `/sessions`, `/revenue-chart`, `/mentorships`, `/{id}/stats`, `/{id}/reviews`, `/{id}/top-learners`, `/{id}/students-ranks`, `/students`, `` *(root composition)*, `/mentorship/{id}` — MENTOR enforced at service level for most; `getSalesChartData`/`getStudents` annotations absent (URL-rule only — [../authentication-security/authorization-rules.md](../authentication-security/authorization-rules.md#2-controller-level-preauthorize-inventory-verified-survey)).

## 16. Contact-us (`/api/v1/contact`)

| Endpoint | Gate |
|---|---|
| 🔓 POST `/save-message` | Guest/any contact message |
| ⟡ GET `/all-messages` · `/message/{id}` · `/messages/filter` · PUT `/message/{id}/status` · POST `/message/{id}/reply` · POST `/message/{id}/notification` · DELETE `/message/{id}` · DELETE `/messages` | ADMIN (URL rule `/api/v1/contact/**` — note broad match also covers `/save-message` publicly repeated) |

State machine (PENDING→UNDER_REVIEW→COMPLETED) + email side effects: `admin-reply.html`; notification flow fails for unregistered senders (NOTIF-02).

## 17. Files (`/files`)

| Endpoint | Gate |
|---|---|
| 🔐 GET `/files/task-submissions/{id}` · `/files/project-submissions/{id}` · `/files/tasks/{id}/attachment` · `/files/projects/{id}/attachment` | Ownership-verified via `FileAccessService` |
| 🔐 GET `/files?filePath=…` | **No ownership check** (FILE-01/LEARN-03) — traversal is blocked |

## 18. WebSocket (STOMP, non-HTTP)

- Endpoint: `/ws` (SockJS, permitAll) — CONNECT requires `Authorization` JWT native header.
- SEND: `/app/chat/{roomId}`, `/app/chat.private`.
- Subscribe: `/topic/room/{roomId}`, `/user/queue/{messages|notifications|errors}`.
- Full surface incl. commented-out authorization: [../websocket/connection-lifecycle.md](../websocket/connection-lifecycle.md).
