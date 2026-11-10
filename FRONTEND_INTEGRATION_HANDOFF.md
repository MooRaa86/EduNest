# EduNest — Complete Frontend Integration Handoff Document

## EB-52 — Completion and certificate status (local draft; not merged or deployed)

The following describes the current local EB-52 implementation and is **not yet a released contract**. It requires backend verification and review. Historical numeric resource-ID examples elsewhere in this document are superseded by the EB-110 public UUID contract; JSON identifier field names remain unchanged, but their values are UUID strings.

### Mentor completion

`POST /api/v1/mentorship/update-status/{id}` requires a valid mentor access token and mentorship ownership. `{id}` is the mentorship's persisted public UUID.

```json
{"status":"COMPLETED"}
```

HTTP 200 retains the existing response:

```json
{"apiResponse":{"status":"Status updated successfully"}}
```

Success means the status, ranking snapshot and durable completion job committed. Certificate issuance and notifications follow asynchronously; success does not mean the certificate is immediately available. An authorized repeated COMPLETED request returns the same success and does not create another job. Existing invalid lifecycle, no-enrollment, authentication and ownership errors still apply (typically 400 business-rule errors, 401 authentication/invalid-credentials, or 403 for insufficient role). Continue handling the standard `ApiError` envelope.

### Student certificate status

`GET /api/v1/student/certificates/mentorships/{mentorshipId}/status` requires a valid student access token and checks the caller's own snapshot or legacy certificate. `{mentorshipId}` is the mentorship's persisted public UUID. It takes no request body. Example HTTP 200:

```json
{"apiResponse":{"status":"PENDING"}}
```

`PENDING`: issuance has not committed. `READY`: the student's certificate exists. `FAILED`: certificate issuance exhausted its retries; contact support rather than retrying completion. `NOT_ELIGIBLE`: the completion snapshot has no certificate-ranking entry for this student. A legacy mentorship without a completion job returns READY only when the caller already has a certificate; otherwise the endpoint returns 404. Missing or invalid authentication returns 401; insufficient role returns 403; unauthorized or unavailable status returns 404. Internal worker errors are not exposed.

Frontend should show a pending state and poll with bounded backoff while the page is visible; stop polling on READY, FAILED, NOT_ELIGIBLE, authentication errors or navigation. On READY refresh the existing paginated `GET /api/v1/student/certificates`; its response contract is unchanged. A notification-stage failure does not make an already issued certificate unavailable.

The backend persists completion and certificate notifications after the completion transaction. WebSocket `/queue/notifications` delivery is best effort; reconnecting clients should refresh the persistent notification list instead of assuming every push arrived. The payload's existing `id` field is a UUID string matching the user-notification or admin-notification public ID returned by the corresponding REST list. Completion can be visible before certificate issuance. Previous completion consumers must not treat HTTP 200 as proof that certificates are immediately downloadable.

Frontend action items: handle all four status values and 404/401/403, add bounded polling and retry/support UI, refresh certificates on READY, and reconcile notification lists after reconnect. Existing request and certificate-list response shapes remain compatible; repeated completion is now a successful no-op.

### Administrator-only recovery

`POST /api/v1/admin/mentorships/{mentorshipId}/completion-job/retry` requires an authenticated ADMIN and accepts an exhausted job only. `{mentorshipId}` is the persisted mentorship public UUID:

```json
{"reason":"Verified the failure and resolved its cause"}
```

HTTP 200 returns `{"apiResponse":{"status":"Retry scheduled"}}`. A missing or blank reason, a reason longer than 1,000 characters, or a job that is not exhausted returns 400 with the standard business-rule error envelope. Missing authentication returns 401 and a non-admin role returns 403. The backend records the operator, reason and time; this endpoint schedules a retry and does not guarantee immediate completion. The operator needs the mentorship public UUID from authorized operational inspection.

> **Document Version:** 2.0.0 (Final Accuracy & Verification Pass)  
> **Target Audience:** Frontend Engineering Team, Tech Leads, QA Automation Engineers  
> **Backend Repository:** [https://github.com/MooRaa86/EduNest](https://github.com/MooRaa86/EduNest)  
> **Sprint Baseline:** Sprint 1 Baseline (October 25, 2026) to Sprint 2 Baseline (November 3, 2026)  
> **Status:** READY FOR FRONTEND REVIEW  

---

## 1. Frontend Must-Know Changes (Executive Action Summary)

This section highlights the critical breaking changes, authentication migrations, and security policies introduced since the Sprint 1 baseline that directly dictate frontend application logic.

### 🚨 Top 10 Breaking Changes & Required Frontend Actions

| # | Domain / Feature | Jira & PR | Nature of Change | Required Frontend Action |
|:---|:---|:---:|:---|:---|
| 1 | **JWT Delivery** | **EB-11** (PR #35) | JWT is now delivered via the HTTP `Authorization` response header (`Bearer <token>`). The body echo `apiResponse.jwt` is a temporary compatibility flag (`app.auth.return-token-in-body=true`) and will be disabled. | **Immediately update login interceptors** to read the token from `response.headers['authorization']`. Send outgoing requests with `Authorization: Bearer <token>`. |
| 2 | **Unified Error Envelope** | **EB-12** (PR #25) | All errors from all backend producers (REST controllers, security entry points, filter rejections) conform to the `ApiError` envelope. `errorCode` is a fixed enum from `ApiErrorCode`. Legacy HTTP 409 leaks and raw MySQL error strings are abolished. | **Branch on `error.response.data.errorCode`** rather than HTTP status alone. Render `error.response.data.message` directly in UI notifications. |
| 3 | **Password Reset Flow** | **EB-14** (PR #30) | Converted from a single OTP submission to an atomic 3-step token exchange. `POST /forget-password/verify-otp` returns a single-use, 15-minute `resetToken`. | **Update UI into 3 distinct views/steps:** Request OTP (`POST /forget-password`) → Verify OTP to capture `resetToken` (`POST /forget-password/verify-otp`) → Submit `resetToken` + `newPassword` (`POST /forget-password/reset`). |
| 4 | **Rate Limiting & Lockout** | **EB-15** (PR #31) | 8 requests/min per bucket; **5 consecutive failed logins triggers a 5-minute account lockout** (HTTP 429 `ERR_ACCOUNT_LOCKED`). Canonical byte-identical 401 `ERR_INVALID_CREDENTIALS` for all failed logins. | **Handle HTTP 429:** Parse the `Retry-After` header (seconds remaining) and show a countdown timer disable state on the login button. |
| 5 | **Upload Access & Private Files** | **EB-28** (PR #23) | Static access to `/uploads/submissions/**`, `task-attachment`, and `project-attachment` returns HTTP 403 `denyAll`. Only 4 public image folders are statically served. | **Do not link directly to static paths for private work.** Route all submission and attachment views through the authenticated `/files/**` gateway with the JWT header. |
| 6 | **Upload Validation & 5MB Cap** | **EB-29** (PR #33) & **EB-87** | Image uploads strictly capped at 5 MB (documents at 10 MB). Server inspects magic bytes and performs JDK decode. WebP supported for avatars/covers; GIF/BMP rejected with 400; **SVG prohibited**. | **Enforce client-side file picker guards:** `file.size <= 5242880` bytes for images, and disallow `.gif`, `.bmp`, `.svg` files before upload. |
| 7 | **Mentorship Explore Pricing** | **EB-59** (PR #76) | Explore price filter evaluates calculated *effective discounted price* (`price * (1 - discount/100)`) instead of raw base price. Wildcard characters (`%`, `_`) are safely escaped. | **Align UI price sliders** with actual payable pricing. Display original price with strikethrough and highlighted effective price. |
| 8 | **Curriculum Content Guards** | **EB-21** (PR #38) | `ContentReadGuard` strictly gates lectures, tasks, projects, and week contents to enrolled students, owning mentors, or admins. | **Render locked state (🔒)** for curriculum items when a student is not enrolled. Unenrolled access returns HTTP 403 Forbidden. |
| 9 | **Quiz Answer Key Scoping** | **EB-25** (PR #55) | `GET /api/v1/my-answers/{quizId}` omits `correctAnswer` (`null` / omitted from JSON) until the attempt is genuinely submitted (`score != null`). | **Do not attempt to render correct answer keys** prior to student quiz submission. |
| 10 | **Live Sessions with Zoom** | **EB-66** (PR #72) & **EB-57** (PR #73) | Jitsi replaced by Server-to-Server Zoom meetings. Host `start_url` with ZAK credentials is provided only to mentors via `hostUrl`; students receive `meetingUrl`. Scheduled sweep auto-closes sessions after 50 min. | **Role-specific launch button:** Mentors click `"Start as Host in Zoom"` (`hostUrl`); students click `"Join Live Session"` (`meetingUrl`). |

---

## 2. Table of Contents

1. [Frontend Must-Know Changes (Executive Action Summary)](#1-frontend-must-know-changes-executive-action-summary)
2. [Table of Contents](#2-table-of-contents)
3. [Executive Summary & Architecture Snapshot](#3-executive-summary--architecture-snapshot)
4. [Backend Change Timeline: Sprint 1 through Sprint 2](#4-backend-change-timeline-sprint-1-through-sprint-2)
   - [4.1 Verified Historical Baseline](#41-verified-historical-baseline)
   - [4.2 Comprehensive Change Matrix](#42-comprehensive-change-matrix)
5. [Merged Subsystem Deep Dives: Supabase Storage & Admin Security](#5-merged-subsystem-deep-dives-supabase-storage--admin-security)
   - [5.1 EB-83 [ADD-03]: Supabase Storage (S3) Dual-Bucket Integration (PR #77)](#51-eb-83-add-03-supabase-storage-s3-dual-bucket-integration-pr-77)
   - [5.2 EB-16 [P1-09]: Admin Bootstrap & Credential Protection (PR #41)](#52-eb-16-p1-09-admin-bootstrap--credential-protection-pr-41)
6. [Authentication, Authorization & Security Architecture](#6-authentication-authorization--security-architecture)
   - [6.1 Token Issuance & Header-Only Contract](#61-token-issuance--header-only-contract)
   - [6.2 Session Invalidation Model (Sprint 1 As-Built vs Sprint 2 Proposed)](#62-session-invalidation-model-sprint-1-as-built-vs-sprint-2-proposed)
   - [6.3 Credential Hardening, Throttling & Account Lockout (EB-15)](#63-credential-hardening-throttling--account-lockout-eb-15)
   - [6.4 Single-Source Public Routes Policy & Graceful Degradation (EB-10)](#64-single-source-public-routes-policy--graceful-degradation-eb-10)
   - [6.5 Role-Based Access Control (RBAC) & Frontend Route Guards](#65-role-based-access-control-rbac--frontend-route-guards)
7. [Unified Error Handling & HTTP Status Contract (EB-12)](#7-unified-error-handling--http-status-contract-eb-12)
   - [7.1 Standard `ApiError` Envelope](#71-standard-apierror-envelope)
   - [7.2 Machine Error Code Catalog (`ApiErrorCode`)](#72-machine-error-code-catalog-apierrorcode)
   - [7.3 Bean Validation Errors (`ERR_VALIDATION`)](#73-bean-validation-errors-err_validation)
   - [7.4 Strict Jackson Deserialization (`ERR_UNKNOWN_FIELD`)](#74-strict-jackson-deserialization-err_unknown_field)
8. [Files, Images & Storage Integration](#8-files-images--storage-integration)
   - [8.1 Storage Architecture: Supabase S3 Integration (Active on main via PR #77)](#81-storage-architecture-supabase-s3-integration-active-on-main-via-pr-77)
   - [8.2 Public Assets Display Strategy](#82-public-assets-display-strategy)
   - [8.3 Private File Streaming & Ownership Verification](#83-private-file-streaming--ownership-verification)
   - [8.4 Upload Matrix, Allowed Types & Validation Rules](#84-upload-matrix-allowed-types--validation-rules)
9. [Verified API Endpoint Inventory (223 Endpoints)](#9-verified-api-endpoint-inventory-223-endpoints)
   - [9.1 Authentication & Password Management (4 Endpoints)](#91-authentication--password-management-4-endpoints)
   - [9.2 Registration & Account Restoration (6 Endpoints)](#92-registration--account-restoration-6-endpoints)
   - [9.3 Account Settings & Identity Management (6 Endpoints)](#93-account-settings--identity-management-6-endpoints)
   - [9.4 Mentorship Catalog, Exploration & Enrollment (19 Endpoints)](#94-mentorship-catalog-exploration--enrollment-19-endpoints)
   - [9.5 Curriculum Management: Weeks & Lectures (14 Endpoints)](#95-curriculum-management-weeks--lectures-14-endpoints)
   - [9.6 Tasks & Submissions (13 Endpoints)](#96-tasks--submissions-13-endpoints)
   - [9.7 Projects & Capstones (12 Endpoints)](#97-projects--capstones-12-endpoints)
   - [9.8 Quizzes, Questions & Grading (22 Endpoints)](#98-quizzes-questions--grading-22-endpoints)
   - [9.9 Live Sessions & Zoom Integration (12 Endpoints)](#99-live-sessions--zoom-integration-12-endpoints)
   - [9.10 Real-Time Chat & Direct Messaging (16 Endpoints + WebSocket STOMP)](#910-real-time-chat--direct-messaging-16-endpoints--websocket-stomp)
   - [9.11 In-App Notifications (7 Endpoints)](#911-in-app-notifications-7-endpoints)
   - [9.12 Profiles & Skills Management (18 Endpoints)](#912-profiles--skills-management-18-endpoints)
   - [9.13 Gamification: Badges & Awards (9 Endpoints)](#913-gamification-badges--awards-9-endpoints)
   - [9.14 Student Progress, Achievements & Certificates (5 Endpoints)](#914-student-progress-achievements--certificates-5-endpoints)
   - [9.15 Mentor Dashboards & Analytics (12 Endpoints)](#915-mentor-dashboards--analytics-12-endpoints)
   - [9.16 Contact Us Support Inquiries (9 Endpoints)](#916-contact-us-support-inquiries-9-endpoints)
   - [9.17 File Download & Streaming Gateway (5 Endpoints)](#917-file-download--streaming-gateway-5-endpoints)
   - [9.18 Admin Operations & Governance (34 Endpoints)](#918-admin-operations--governance-34-endpoints)
   - [9.19 Deleted Breaking Endpoints (3 Endpoints)](#919-deleted-breaking-endpoints-3-endpoints)
10. [API Contract Changes: Before vs After Comparisons](#10-api-contract-changes-before-vs-after-comparisons)
11. [Data Models, Enums & Type Definitions](#11-data-models-enums--type-definitions)
12. [Frontend Integration Checklist](#12-frontend-integration-checklist)
13. [Environment Configuration & Development Setup](#13-environment-configuration--development-setup)
14. [Open Questions & Backend Dependencies](#14-open-questions--backend-dependencies)

---

## 3. Executive Summary & Architecture Snapshot

The EduNest backend is a modular monolithic application built with **Spring Boot 3.5.7** running on **Java 21 LTS**.

### System Architecture Snapshot
- **Security & Session Layer:** Stateless JWT authentication enforced by [ProjectSecurityConfig](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/config/security/ProjectSecurityConfig.java) with fail-fast boot protection.
- **Database Engine:** Supabase Managed PostgreSQL (Decision **D-22**), replacing legacy MySQL 8. Drivers and dialects unified in EB-81 / PR #68.
- **Storage Subsystem:** **Supabase Storage S3** with dual-bucket policies ([EB-83 / PR #77](https://github.com/MooRaa86/EduNest/pull/77), **MERGED ON MAIN** via commits `bf395a7` and `25e2150`), replacing legacy local disk storage.
- **Video Conferencing:** Zoom Server-to-Server OAuth client (EB-66 / PR #72), replacing legacy public Jitsi rooms.
- **Real-Time Communication:** Spring WebSocket with STOMP sub-protocol over SockJS at `/ws`.

---

## 4. Backend Change Timeline: Sprint 1 through Sprint 2

### 4.1 Verified Historical Baseline
The historical baseline is confirmed by Git commit evidence:
- **Pre-Sprint Baseline:** Legacy development branches (`develop`, `omar`, `hazem`, `Rahmah`) ended at commit `223ad9e` (June 16, 2026).
- **Audit & Planning Freeze:** In October 2026, an exhaustive system audit produced the canonical issue registry (`mds/ISSUES.md`).
- **Sprint 1 Inception (October 25, 2026):** Marked by planning baseline commit `c87b393` and the adoption of the Main-Only Pull Request workflow ([PR #18](https://github.com/MooRaa86/EduNest/pull/18), commit `3a42720`).
- **Sprint 2 Inception (November 2, 2026):** Commenced following the merge of Sprint 1 security stabilization and testing net tasks.

---

### 4.2 Comprehensive Change Matrix

The following matrix documents all **33 verified backend changes** since the Sprint 1 baseline (all **33 merged on main**, 0 pending PR merge):

| Jira Issue | PR / Commit | Status | Old Behavior | New Behavior | Affected Endpoints / DTOs | Required Frontend Action |
|:---|:---:|:---:|:---|:---|:---|:---|
| **EB-1** (P0-03) | [PR #14](https://github.com/MooRaa86/EduNest/pull/14) (`9e7ba9a`) | **Merged** | Chat room socket accepted raw string payloads. | Enforced Jackson binding to [ChatMessageRequest](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/chat/ChatMessageRequest.java). | STOMP `/app/chat/{roomId}` | Send JSON `{"message": "text"}`, not raw strings. |
| **EB-9** (P1-02) | [PR #15](https://github.com/MooRaa86/EduNest/pull/15) (`af50e58`) | **Merged** | Split security configs (`ProjectSecurityProdconfig` vs dev). | Single fail-fast config; refuses boot without `prod` or `EDUNEST_UNSAFE_DEV`. | Entire Application | Expect strict production security enforcement. |
| **EB-8** (P1-01) | [PR #13](https://github.com/MooRaa86/EduNest/pull/13) (`8f4555c`) | **Merged** | Hardcoded JWT secret fallback in source code. | Env-only secret via `JwtSecretProvider` (>= 32 bytes). | `POST /login-api` | Standardized token signing; handles secret rotation. |
| **EB-13** (P1-06) | [PR #19](https://github.com/MooRaa86/EduNest/pull/19) (`d3a14a2`) | **Merged** | OTPs were verified globally without type isolation. | Scoped verification queries strictly by `OtpType` enum. | `POST /api/v1/register/verify-user`, `/forget-password/verify-otp` | OTPs cannot be cross-utilized between flows. |
| **EB-28** (P1-21) | [PR #23](https://github.com/MooRaa86/EduNest/pull/23) (`171c6a1`) | **Merged** | All files in `uploads/` statically reachable. | Statically serves only 4 public folders; private folders 403 `denyAll`. | `GET /uploads/submissions/**` (Blocked) | Use `/files/**` download gateway with JWT auth. |
| **EB-12** (P1-05) | [PR #25](https://github.com/MooRaa86/EduNest/pull/25) (`f7fb8ad`) | **Merged** | Inconsistent error responses; raw 409 database leaks. | Unified [ApiError](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/exception/ApiError.java) envelope and [ApiErrorCode](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/exception/ApiErrorCode.java). | All Endpoints | Bind error handlers to `error.errorCode` and `message`. |
| **EB-14** (P1-07) | [PR #30](https://github.com/MooRaa86/EduNest/pull/30) (`52452a0`) | **Merged** | Single OTP submission immediately changed password. | 3-step token flow: `/verify-otp` returns single-use `resetToken`. | `POST /forget-password/verify-otp`, `/reset` | Implement 3-screen password recovery wizard. |
| **EB-15** (P1-08) | [PR #31](https://github.com/MooRaa86/EduNest/pull/31) (`4529aeb`) | **Merged** | No rate limits; user enumeration leaks on login 401s. | 8 req/min rate limit; 5-failure lockout (429); neutral 401. | `POST /login-api`, credential endpoints | Parse `Retry-After` header on 429; show lockout timer. |
| **EB-29** (P1-22) | [PR #33](https://github.com/MooRaa86/EduNest/pull/33) (`6642aa5`) | **Merged** | Server trusted client Content-Type; deletes old file first. | Magic byte sniffing; save-before-delete; 400 `ERR_BUSINESS_RULE`. | All Upload Endpoints | Display clean error messages on upload rejections. |
| **EB-11** (P1-04) | [PR #35](https://github.com/MooRaa86/EduNest/pull/35) (`a3dcde3`) | **Merged** | JWT delivered only in JSON body response. | JWT delivered in HTTP `Authorization` response header. | `POST /login-api` | Read JWT from header: `response.headers['authorization']`. |
| **EB-32** (P1-25) | [PR #37](https://github.com/MooRaa86/EduNest/pull/37) (`082432e`) | **Merged** | Unvalidated login/registration parameter strings. | Bean validation (`@Valid`); returns 400 `ERR_VALIDATION` + `fields`. | `POST /login-api`, `/register/**` | Render field-level validation errors under input fields. |
| **EB-21** (P1-14) | [PR #38](https://github.com/MooRaa86/EduNest/pull/38) (`8175e28`) | **Merged** | Lectures and tasks accessible to unenrolled users. | `ContentReadGuard` gates access to enrolled learners, mentors, admins. | `/lectures/**`, `/api/v1/task/**`, `/project/**` | Render locked view (🔒) for unenrolled users. |
| **EB-23** (P1-16) | [PR #39](https://github.com/MooRaa86/EduNest/pull/39) (`2dbea23`) | **Merged** | Leaderboard was open and leaked badge/level data. | Gated to enrolled cohort; rows expose name, email, avatar, rank, pts. | `GET /api/v1/student/mentorships/{id}/leaderboard` | Update leaderboard table to match new row schema. |
| **EB-27** (P1-20) | [PR #40](https://github.com/MooRaa86/EduNest/pull/40) (`9a99f7f`) | **Merged** | No generic file resolver with ownership verification. | Added `GET /files?filePath=...&download=...` with DB reference checks. | `GET /files` | Universal authenticated download gateway. |
| **EB-19** (P1-12) | [PR #43](https://github.com/MooRaa86/EduNest/pull/43) (`7b6797a`) | **Merged** | WebSocket room subscriptions lacked authorization checks. | `ChatAuthorization` validates permissions on SUBSCRIBE and SEND. | STOMP `/topic/room/{roomId}` | Catch STOMP ERROR frames on unauthorized access. |
| **EB-30** (P1-23) | [PR #44](https://github.com/MooRaa86/EduNest/pull/44) (`2544ac1`) | **Merged** | Insecure credentialed wildcard CORS combination. | `allowCredentials=false`, wildcard allowed, `Authorization` exposed. | CORS Configuration | Send JWT in header without expecting ambient cookies. |
| **EB-20** (P1-13) | [PR #47](https://github.com/MooRaa86/EduNest/pull/47) (`a41219a`) | **Merged** | Direct message IDORs; client passed `recipientEmail`. | History requires participant; server derives event recipient. | `GET /api/v1/conversation/{id}/messages` | Do not pass `recipientEmail`; server resolves participants. |
| **EB-18** (P1-11) | [PR #48](https://github.com/MooRaa86/EduNest/pull/48) (`917df99`) | **Merged** | Unsecured test mail and push endpoints existed. | Permanently deleted `POST /send` and `/api/v1/notifications/send`. | `/send`, `/notifications/send` | Remove any frontend calls to these deleted paths. |
| **EB-22** (P1-15) | [PR #51](https://github.com/MooRaa86/EduNest/pull/51) (`230a274`) | **Merged** | DRAFT courses visible in student explore/detail views. | DRAFT mentorships return 404 for non-owners/admins (no leak). | `/api/v1/mentorship/{id}`, `/overview` | Handle 404 gracefully if course is not published. |
| **EB-24** (P1-17) | [PR #52](https://github.com/MooRaa86/EduNest/pull/52) (`67830f5`) | **Merged** | Homepage progress accessible for arbitrary course IDs. | Gated to caller's active enrollment; foreign access 403. | `GET /api/v1/homepage/progress/{id}` | Only request progress for enrolled mentorships. |
| **EB-26** (P1-19) | [PR #53](https://github.com/MooRaa86/EduNest/pull/53) (`4bc8a52`) | **Merged** | Questions could be re-parented to foreign quizzes. | Enforced `isMentorOwnQuiz`; scoped badge award reads. | `/api/v1/question/{id}`, `/badge-awards/...` | Questions locked to owner's quiz mold. |
| **EB-10** (P1-03) | [PR #54](https://github.com/MooRaa86/EduNest/pull/54) (`82163a5`) | **Merged** | Stale JWT on public route triggered immediate 401. | Graceful degradation: clears context and proceeds as guest. | Public endpoints (`/explore`, `/overview`) | Guest browsing with expired tokens will not crash. |
| **EB-25** (P1-18) | [PR #55](https://github.com/MooRaa86/EduNest/pull/55) (`1253d83`) | **Merged** | `correctAnswer` exposed in quiz review before submit. | `correctAnswer` omitted from JSON (`null`) until submitted. | `GET /api/v1/my-answers/{quizId}` | Do not display answer keys prior to submission. |
| **EB-40** (P2-06) | [PR #67](https://github.com/MooRaa86/EduNest/pull/67) (`a9c097a`) | **Merged** | Unknown JSON fields ignored or returned generic 500. | Strict production rejection (400 `ERR_UNKNOWN_FIELD`). | All JSON Request Payloads | Never send undeclared UI properties in payloads. |
| **EB-87** (FILE-02)| Commit `49bf2d7` | **Merged** | Format allowances on images were inconsistent. | Locked coverage: JPEG, PNG, WebP allowed; GIF/BMP rejected; SVG banned. | Cover & Avatar Uploads | Frontend file pickers must filter out GIF, BMP, SVG. |
| **EB-81** (ADD-01) | [PR #68](https://github.com/MooRaa86/EduNest/pull/68) (`d21fbd4`) | **Merged** | App configured for MySQL 8 JDBC. | Adopted PostgreSQL driver and Supabase environment matrix. | Database Layer | Foundation for Supabase PostgreSQL hosting. |
| **EB-66** (P4-17) | [PR #72](https://github.com/MooRaa86/EduNest/pull/72) (`5170889`) | **Merged** | Live sessions generated public Jitsi Meet links. | Server-to-Server Zoom OAuth; separates `hostUrl` and `meetingUrl`. | `POST /api/v1/liveSession/start/{id}` | Render host launch button for mentors (`hostUrl`). |
| **EB-57** (P4-08) | [PR #73](https://github.com/MooRaa86/EduNest/pull/73) (`740a2f7`) | **Merged** | Stale sessions remained LIVE indefinitely. | Scheduled sweep (5m) auto-closes sessions; dormant attendance policy. | `POST /api/v1/liveSession/end/{id}` | Display remaining session countdown timer. |
| **EB-84** (ADD-04) | [PR #75](https://github.com/MooRaa86/EduNest/pull/75) (`498c9fa`) | **Merged** | Unsecured test admin notification endpoint existed. | Permanently deleted `POST /api/v1/admin/notifications/send`. | Admin Notification Route | Endpoint returns 404 Not Found. |
| **EB-59** (P4-10) | [PR #76](https://github.com/MooRaa86/EduNest/pull/76) (`5b275ce`) | **Merged** | Price filter matched raw price; unescaped wildcards. | Filters by calculated effective price; escapes search wildcards. | `GET /api/v1/mentorship/explore` | Price range filters now reflect real payable cost. |
| **EB-82** (ADD-02) | [PR #74](https://github.com/MooRaa86/EduNest/pull/74) (`92dd772`) | **Merged** | `@Lob` mapping issues on PostgreSQL. | Replaced `@Lob` with `TEXT`; `@Builder.Default` on entity/DTOs. | Entity & DTO Layer | Asserts serialization stability on PostgreSQL. |
| **EB-83** (ADD-03) | [PR #77](https://github.com/MooRaa86/EduNest/pull/77) (`bf395a7`, `25e2150`) | **Merged** | Files written to local server disk (`uploads/`). | Dual Supabase S3 buckets: `edunest-public` (CDN) & `edunest-private` (proxy). | Public Images & Downloads | Image renderers must handle absolute Supabase CDN URLs (`https://...`). Private downloads stream through `/files/**` with JWT auth. |
| **EB-16** (P1-09) | [PR #41](https://github.com/MooRaa86/EduNest/pull/41) (`38a9709`) | **Merged** | Admin bootstrap multi-use; admin list leaked password hash. | One-shot bootstrap; last-admin guard; password stripped from response. | `POST /api/v1/admin/register`, `GET /admin` | Admin directory table must not expect password hash (field permanently removed from `AdminAccResponse`). |

---

## 5. Merged Subsystem Deep Dives: Supabase Storage & Admin Security

> [!NOTE]
> **Subsystem Merge Status:**  
> Both core subsystems below have been merged into `origin/main` following CI validation and live smoke testing:
> - **EB-83 (Supabase Storage S3):** Merged via [Pull Request #77](https://github.com/MooRaa86/EduNest/pull/77) (commits `bf395a7`, `25e2150`).
> - **EB-16 (Admin Bootstrap & Credential Protection):** Merged via [Pull Request #41](https://github.com/MooRaa86/EduNest/pull/41) (commit `38a9709`).
> 
> All frontend integration requirements for these subsystems are fully active on `origin/main`.

### 5.1 EB-83 [ADD-03]: Supabase Storage (S3) Dual-Bucket Integration (PR #77)
- **Status:** **Merged on main ([PR #77](https://github.com/MooRaa86/EduNest/pull/77), commits `bf395a7`, `25e2150`)**
- **Repository Sources on main:** [SupabaseStorageService.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/service/storage/SupabaseStorageService.java), [SupabaseStorageConfig.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/config/storage/SupabaseStorageConfig.java), [FileController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/file/FileController.java)
- **Architecture:**
  - Implements AWS S3 SDK targeting Supabase Storage S3-compliant endpoints.
  - Public Assets Bucket: `edunest-public` (mentorship covers, student/mentor/admin avatars, chatroom images).
  - Private Files Bucket: `edunest-private` (student task/project submissions, task/project attachments).
  - Production Guard: Fail-closed in `prod` profile if Supabase credentials are not configured.
  - Transactional Compensation: Automatic cleanup deletes uploaded S3 objects if the database transaction rolls back.
- **Frontend Impact:**
  - Public image responses return full Supabase CDN URLs (`https://<project-id>.supabase.co/storage/v1/object/public/edunest-public/...`).
  - Private file streaming is accessed through `/files/**` via server-side authenticated proxying with JWT header.

### 5.2 EB-16 [P1-09]: Admin Bootstrap & Credential Protection (PR #41)
- **Status:** **Merged on main ([PR #41](https://github.com/MooRaa86/EduNest/pull/41), commit `38a9709`)**
- **Repository Sources on main:** [AdminController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/admin/AdminController.java), [AdminAccResponse.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/auth/AdminAccResponse.java)
- **Architecture:**
  - `POST /api/v1/admin/register` is synchronized and one-shot: succeeds only while zero admins exist in the database. Returns 400 `ERR_BUSINESS_RULE` thereafter.
  - `AdminAccResponse` **permanently deletes the `password` field**.
  - `DELETE /api/v1/admin/{email}` rejects deletion of the last remaining admin account.
- **Frontend Impact:**
  - Admin management UI must never expect or attempt to display password hashes from `GET /api/v1/admin`.

---

## 6. Authentication, Authorization & Security Architecture

### 6.1 Token Issuance & Header-Only Contract

EduNest utilizes stateless **JSON Web Tokens (JWT)** signed via HMAC-SHA256 (`HS256`).

#### Login Flow (`POST /login-api`)
1. **Credentials:** Email and password validated via Bean Validation (`@Valid`).
2. **Token Delivery:** Delivered in the standard HTTP response header:
   ```http
   Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
   ```
3. **Frontend Interceptor Requirement:**
   Extract token from `response.headers['authorization']`. Store securely in memory or storage. Outgoing requests must include:
   ```http
   Authorization: Bearer <token>
   ```

---

### 6.2 Session Invalidation Model (Sprint 1 As-Built vs Sprint 2 Proposed)

#### Sprint 1 As-Built Contract (`main`)
- **Token Validity:** **7 days** (`604,800,000 ms`) hardcoded in `JwtService`.
- **Client-Side Erasure:** There is **no server-side `/logout` endpoint** in Sprint 1. The client logs out by deleting the stored JWT.
- **Immediate Invalidation Triggers:** Active JWTs are immediately rejected with 401 `ERR_INVALID_TOKEN` if:
  1. The user soft-deletes their account (`deleted == true`).
  2. The user deactivates their account (`enabled == false`).
  3. The user confirms an email change (the username claim no longer resolves).
- **Residual Risk:** Password changes via `PATCH /settings/password` do not invalidate active JWTs in Sprint 1.

#### Sprint 2 Proposed Architecture (EB-91 / SEL-06)
- 15-minute access JWTs coupled with rotating opaque refresh tokens (SHA-256 hash stored).
- Adds endpoints: `POST /login-api/refresh`, `POST /login-api/logout`, `POST /login-api/logout-all`.

---

### 6.3 Credential Hardening, Throttling & Account Lockout (EB-15)

`AuthRateLimitFilter` protects the 6 public credential endpoints:
- **Rate Limit:** **8 requests per minute** per `IP + Bucket + Email`.
- **Account Lockout:** **5 consecutive failed login attempts** triggers a **5-minute (300s) account lockout**.
- **Response:** HTTP 429 `ERR_ACCOUNT_LOCKED` accompanied by `Retry-After: <seconds>` header.
- **Canonical 401:** Returns byte-identical `ERR_INVALID_CREDENTIALS` for wrong passwords, missing users, unverified accounts, or deleted accounts.

---

### 6.4 Single-Source Public Routes Policy & Graceful Degradation (EB-10)

`PublicRoutesPolicy` governs public access:
- **Graceful Fallback:** If an expired or invalid JWT is passed to a public endpoint (e.g. `GET /api/v1/mentorship/explore`), the backend clears context and serves the response as an anonymous guest rather than failing with 401.
- **Dual-Mode Preservation:** Passing a valid JWT to public endpoints populates user context, enabling personalization flags (`isEnrolled: true`).

---

### 6.5 Role-Based Access Control (RBAC) & Frontend Route Guards

The backend recognizes three core roles:
- `ROLE_STUDENT` — Enrolled learners.
- `ROLE_MENTOR` — Instructors and course authors.
- `ROLE_ADMIN` — System administrators.

#### Frontend Route Guard Rules:
- `/admin/**` → Gated to `ROLE_ADMIN`.
- `/mentor/**` → Gated to `ROLE_MENTOR`.
- `/student/**` → Gated to `ROLE_STUDENT`.
- `/settings/**` → Gated to any authenticated user.

---

## 7. Unified Error Handling & HTTP Status Contract (EB-12)

### 7.1 Standard `ApiError` Envelope

Every error response produced by the backend adheres strictly to the `ApiError` JSON contract:

```json
{
  "timestamp": "2026-11-03T18:30:00",
  "status": 403,
  "error": "Forbidden",
  "errorCode": "ERR_FORBIDDEN",
  "message": "You do not have permission to access this resource.",
  "path": "/api/v1/student/mentorships/12/leaderboard",
  "refId": null,
  "fields": null
}
```

---

### 7.2 Machine Error Code Catalog (`ApiErrorCode`)

| Error Code | HTTP Status | Typical Root Cause | User-Facing Message |
|:---|:---:|:---|:---|
| `ERR_UNAUTHENTICATED` | 401 | Missing `Authorization` header on protected route | `"Authentication required. Please log in."` |
| `ERR_INVALID_TOKEN` | 401 | Expired, tampered, or deactivated JWT | `"Invalid JWT token, please login again"` |
| `ERR_INVALID_CREDENTIALS` | 401 | Invalid email, wrong password, or unverified account | `"Invalid email or password."` |
| `ERR_FORBIDDEN` | 403 | Insufficient role or failed course-level ownership guard | `"You do not have permission to access this resource."` |
| `ERR_VALIDATION` | 400 | Spring Bean Validation failure (`@Valid`) | `"Please check the submitted fields."` (accompanied by `fields` map) |
| `ERR_UNKNOWN_FIELD` | 400 | Undeclared JSON property sent in production profile | `"Field 'xyz' is not allowed."` (includes `invalidField` and `allowedFields`) |
| `ERR_MALFORMED_BODY` | 400 | Invalid JSON syntax or type conversion mismatch | `"Malformed JSON request or invalid data type."` |
| `ERR_BUSINESS_RULE` | 400 | Domain rule violation (`globalLogicEx`) | Clean domain message (e.g. `"Quiz is already submitted."`) |
| `ERR_INVALID_OTP` | 400 | Incorrect 6-digit OTP code | `"Invalid verification code."` |
| `ERR_ALREADY_VERIFIED` | 400 | Account has already completed verification | `"Account is already verified."` |
| `ERR_OTP_EXPIRED` | 410 | OTP expired past validity window | `"Verification code has expired."` |
| `ERR_RESOURCE_NOT_FOUND`| 404 | Entity ID not found in database | `"The requested item was not found."` |
| `ERR_USER_NOT_FOUND` | 404 | User email or ID not found | `"User not found."` |
| `ERR_EMAIL_IN_USE` | 409 | Registration with already registered email | `"Email is already registered."` |
| `ERR_CONSTRAINT_VIOLATION`| 400 | Database constraint conflict (raw SQL stripped) | `"This value is already in use."` |
| `ERR_RATE_LIMITED` | 429 | Rate limit exceeded on credential endpoints | `"Too many attempts. Please wait a moment and try again."` |
| `ERR_ACCOUNT_LOCKED` | 429 | 5 consecutive failed logins | `"Account temporarily locked. Please try again in 5 minutes."` |
| `ERR_INTERNAL_SYSTEM` | 500 | Unhandled server exception (sanitized) | `"An unexpected system error occurred. Please try again."` (has `refId`) |

---

### 7.3 Bean Validation Errors (`ERR_VALIDATION`)

When payload validation fails, the server populates the `fields` map:

```json
{
  "timestamp": "2026-11-03T18:35:10",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "ERR_VALIDATION",
  "message": "Please check the submitted fields.",
  "path": "/api/v1/register/student",
  "refId": null,
  "fields": {
    "email": "Invalid email format.",
    "password": "Password must be at least 8 characters long."
  }
}
```

---

### 7.4 Strict Jackson Deserialization (`ERR_UNKNOWN_FIELD`)

In `prod` profile, unknown JSON fields are rejected with HTTP 400:

```json
{
  "timestamp": "2026-11-03T18:40:00",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "ERR_UNKNOWN_FIELD",
  "message": "Field 'extraField' is not allowed.",
  "path": "/api/v1/task",
  "refId": null,
  "fields": {
    "invalidField": "extraField",
    "allowedFields": "title, description, durationMinutes, deadline"
  }
}
```

---

## 8. Files, Images & Storage Integration

### 8.1 Storage Architecture: Supabase S3 Integration (Active on main via PR #77)

| Dimension | Legacy Subsystem (Pre-PR #77 Disk) | Active on `main` (Supabase S3 via PR #77) |
|:---|:---|:---|
| **Public Assets** | Stored on local disk under `uploads/{folder}/`. Served via relative paths `/uploads/...`. | Stored in S3 bucket `edunest-public`. Served via absolute CDN URLs `https://...`. |
| **Private Files** | Stored on local disk under `uploads/{folder}/`. Static route `/uploads/**` denies access. | Stored in S3 bucket `edunest-private`. Static route `/uploads/**` denies access. |
| **Download Gateway** | Served via `/files/**` with local filesystem streaming. | Served via `/files/**` with authenticated S3 streaming proxy. |
| **Fail-Closed Guard** | Local disk fallback available in development. | `prod` refuses boot and rejects writes if S3 is unconfigured. |
| **Rollback Cleanup** | No transactional rollback cleanup for files. | Synchronized hook deletes S3 objects if DB tx rolls back. |

---

### 8.2 Public Assets Display Strategy

> [!NOTE]
> **Active Supabase CDN URL Format:**  
> With [PR #77](https://github.com/MooRaa86/EduNest/pull/77) merged into `main`, public asset endpoints return absolute Supabase CDN URLs (`https://<project-id>.supabase.co/storage/v1/object/public/edunest-public/...`). For development resilience and backwards compatibility with test fixtures, frontend image components should resolve URLs using this bundler-agnostic helper:

Frontend image components should resolve URLs using this bundler-agnostic helper:

```typescript
export function resolveMediaUrl(url: string | null | undefined): string {
  if (!url) return '/assets/default-placeholder.png';
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url; // Absolute CDN URL (Supabase S3)
  }
  // Relative path fallback (e.g., local mock or default assets)
  const baseUrl = 
    (typeof process !== 'undefined' && process.env?.NEXT_PUBLIC_MEDIA_BASE_URL) ||
    (typeof process !== 'undefined' && process.env?.REACT_APP_MEDIA_BASE_URL) ||
    (typeof import.meta !== 'undefined' && (import.meta as any).env?.VITE_MEDIA_BASE_URL) ||
    'http://localhost:8080';
  return `${baseUrl.replace(/\/$/, '')}/${url.replace(/^\//, '')}`;
}
```

---

### 8.3 Private File Streaming & Ownership Verification

Private files must **never be addressed via `/uploads/**`**. Frontend clients must download or stream documents using the authenticated gateway endpoints:
- `GET /files/task-submissions/{submissionId}`
- `GET /files/project-submissions/{submissionId}`
- `GET /files/tasks/{taskId}/attachment`
- `GET /files/projects/{projectId}/attachment`
- `GET /files?filePath={path}&download={true|false}`

---

### 8.4 Upload Matrix, Allowed Types & Validation Rules

| Surface | Max File Size | Formats Permitted | Formats Prohibited | Verification Mechanism |
|:---|:---:|:---|:---|:---|
| **Mentorship Covers** | 5 MB | JPEG, PNG, WebP | GIF, BMP, SVG, EXE | Magic bytes + full JDK decode; normalized extension |
| **User Profile Avatars** | 5 MB | JPEG, PNG, WebP | GIF, BMP, SVG | Magic bytes + full pixel decode; structural WebP check |
| **Chatroom Avatars** | 5 MB | JPEG, PNG | WebP, GIF, BMP, SVG | WebP explicitly rejected for chatroom avatars |
| **Task Attachments** | 10 MB | PDF, DOCX, XLSX, PPTX, TXT, ZIP | EXE, SH, JS, HTML | Apache Tika MIME sniffing + structural ZIP parsing |
| **Project Attachments**| 10 MB | PDF, DOCX, XLSX, PPTX, TXT, ZIP | EXE, SH, JS, HTML | Apache Tika MIME sniffing + structural ZIP parsing |
| **Student Submissions** | 10 MB | PDF, DOCX, XLSX, PPTX, TXT, ZIP | Executable binaries | MIME verification + file size constraints |

---

## 9. Verified API Endpoint Inventory (223 Endpoints)

An exhaustive audit of all 42 Spring Boot controller classes in `com.example.gradproj.EduNest.controller` confirms **223 distinct method-level REST endpoints** (plus WebSocket STOMP messaging channels and 3 permanently deleted endpoints).

---

### 9.1 Authentication & Password Management (4 Endpoints)
**Source:** [AuthController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/auth/AuthController.java)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `POST` | `/login-api` | 🔓 Public | [LoginRequestDto](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/auth/LoginRequestDto.java) | `SimpleResponse` (Header: `Authorization: Bearer <jwt>`) |
| `POST` | `/forget-password` | 🔓 Public | [ForgetPasswordRequestDto](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/auth/forgetPassword/ForgetPasswordRequestDto.java) | `SimpleResponse {"message": "OTP has sent to your email"}` |
| `POST` | `/forget-password/verify-otp`| 🔓 Public | [VerifyForgetPasswordOtpDto](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/auth/forgetPassword/VerifyForgetPasswordOtpDto.java) | `SimpleResponse {"message": "...", "resetToken": "uuid"}` |
| `POST` | `/forget-password/reset` | 🔓 Public | [ResetPasswordRequestDto](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/auth/forgetPassword/ResetPasswordRequestDto.java) | `SimpleResponse {"message": "password reset successfully"}` |

#### Valid JSON Request Example (`POST /login-api`):
```json
{
  "email": "student@example.com",
  "password": "Password123!"
}
```

#### Valid JSON Response Example (`POST /login-api`):
```json
{
  "apiResponse": {
    "status": "Login successful",
    "Role": "ROLE_STUDENT",
    "jwt": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

---

### 9.2 Registration & Account Restoration (6 Endpoints)
**Source:** [UserManagementController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/register/UserManagementController.java)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `POST` | `/api/v1/register/student` | 🔓 Public | [StudentRequestDto](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/register/StudentRequestDto.java) | `SimpleResponse` (Status: 201 Created) |
| `POST` | `/api/v1/register/mentor` | 🔓 Public | [MentorRequestDto](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/register/MentorRequestDto.java) | `SimpleResponse` (Status: 201 Created) |
| `POST` | `/api/v1/register/verify-user` | 🔓 Public | [VerifyAccountDto](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/register/VerifyAccountDto.java) | `SimpleResponse` (Account verified) |
| `POST` | `/api/v1/register/send-otp` | 🔓 Public | `?email=user@example.com` | `SimpleResponse` (OTP sent) |
| `POST` | `/api/v1/register/restore` | 🔓 Public | [RestoreAccountRequest](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/account/request/RestoreAccountRequest.java) | `SimpleResponse` (Restoration OTP dispatched) |
| `POST` | `/api/v1/register/confirm-restore` | 🔓 Public | [ConfirmRestoreAccountRequest](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/account/request/ConfirmRestoreAccountRequest.java) | `SimpleResponse` (Account restored) |

---

### 9.3 Account Settings & Identity Management (6 Endpoints)
**Source:** [SettingsController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/account/SettingsController.java)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `POST` | `/settings/request-email-change` | 🔐 Authenticated | [ChangeEmailRequest](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/account/request/ChangeEmailRequest.java) | `SimpleResponse` (OTP sent to new email) |
| `PATCH`| `/settings/confirm-email-change` | 🔐 Authenticated | `?otpCode=123456` | `SimpleResponse` (Forces re-login) |
| `PATCH`| `/settings/password` | 🔐 Authenticated | [ChangePasswordRequest](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/account/request/ChangePasswordRequest.java) | `SimpleResponse` (Password updated) |
| `POST` | `/settings/delete` | 🔐 Authenticated | None | `SimpleResponse` (Deletion OTP sent) |
| `POST` | `/settings/confirm-delete` | 🔐 Authenticated | `?otp=123456` | `SimpleResponse` (Account soft-deleted) |
| `POST` | `/settings/deactivate` | 🔐 Authenticated | `?password=Password123!` | `SimpleResponse` (Account deactivated) |

---

### 9.4 Mentorship Catalog, Exploration & Enrollment (19 Endpoints)
**Sources:** [mentorShipControllers.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/mentorShip/mentorShipControllers.java) (15), [MentorshipOverviewController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/studentMentorship/MentorshipOverviewController.java) (4)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `GET` | `/api/v1/mentorship/explore` | 🔓 Public | `keyword, category, minPrice, maxPrice, page, size` | `PageResponse<MentorshipCardResponse>` |
| `GET` | `/api/v1/mentorship/categories` | 🔓 Public | None | `List<String>` |
| `GET` | `/api/v1/mentorship/{id}` | 🔐 Authenticated | `id` (path) | `MentorshipDetailsDto` |
| `GET` | `/api/v1/mentorship` | 🔐 Authenticated | `page, size` | `PageResponse<MentorshipResponseDto>` |
| `POST`| `/api/v1/mentorship` | ⟡ `ROLE_MENTOR` | `mentorShipCreateDTO` | `SimpleResponse` (Status: 201 Created) |
| `PATCH`| `/api/v1/mentorship/{mid}` | ⟡ `ROLE_MENTOR` (Owner)| `mid` (path), `MentorShipUpdateDTO` | `SimpleResponse` |
| `DELETE`| `/api/v1/mentorship/{mid}` | ⟡ `ROLE_MENTOR` (Owner)| `mid` (path) | `SimpleResponse` (DRAFT-only deletion) |
| `GET` | `/api/v1/mentorship/{mid}/get-tasks`| 🔐 Enrolled / Owner / Admin| `mid` (path) | `List<TaskResponse>` |
| `GET` | `/api/v1/mentorship/count-for-mentor`| ⟡ `ROLE_MENTOR` | None | `SimpleResponse {"count": 5}` |
| `GET` | `/api/v1/mentorship/count-students-for-mentor`| ⟡ `ROLE_MENTOR` | None | `SimpleResponse {"count": 42}` |
| `POST`| `/api/v1/mentorship/update-status/{id}`| ⟡ `ROLE_MENTOR` (Owner)| `id` (path), `ChangeStatusRequest` | `SimpleResponse` (Transition DRAFT/ACTIVE/COMPLETED) |
| `POST`| `/api/v1/mentorship/{mentorshipId}/change-cover-image`| ⟡ `ROLE_MENTOR` (Owner)| `file` (MultipartFile, max 5MB) | `SimpleResponse {"coverImageUrl": "..."}` |
| `DELETE`| `/api/v1/mentorship/{mentorshipId}/delete-cover-image`| ⟡ `ROLE_MENTOR` (Owner)| `mentorshipId` (path) | `SimpleResponse` |
| `POST`| `/api/v1/mentorship/{mentorshipId}/join`| ⟡ `ROLE_STUDENT` | `mentorshipId` (path) | `SimpleResponse` (Enrolls student) |
| `POST`| `/api/v1/mentorship/{mentorshipId}/rate`| ⟡ `ROLE_STUDENT` (Enrolled)| `CreateReviewRequest` | `SimpleResponse` |
| `GET` | `/api/v1/student/mentorships/{mentorshipId}/reviews`| 🔓 Public | `mentorshipId` (path), `page, size` | `PageResponse<ReviewsResponse>` |
| `GET` | `/api/v1/student/mentorships/mentor/{mentorEmail}/top-mentorships`| 🔐 Authenticated | `mentorEmail` (path) | `List<TopMentorshipResponse>` |
| `GET` | `/api/v1/student/mentorships/{mentorshipId}/overview`| 🔓 Public (Dual-Mode) | `mentorshipId` (path) | `MentorshipOverviewDto` (`isEnrolled` flag) |
| `GET` | `/api/v1/student/mentorships/{mentorshipId}/leaderboard`| 🔐 Enrolled / Owner / Admin| `mentorshipId` (path) | `MsLeaderBoardDto` |

---

### 9.5 Curriculum Management: Weeks & Lectures (14 Endpoints)
**Sources:** [StudentWeekController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/weeks/StudentWeekController.java) (3), [WeekController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/weeks/WeekController.java) (5), [LectureController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/lecture/LectureController.java) (6)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `GET` | `/api/v1/student/week/{mentorshipId}/weeks` | ⟡ `ROLE_STUDENT` (Enrolled)| `mentorshipId` (path) | `MentorshipWeeksResponse` |
| `GET` | `/api/v1/student/week/{weekId}/contents` | ⟡ `ROLE_STUDENT` (Enrolled)| `weekId` (path) | `StudentWeekContentsResponse` |
| `GET` | `/api/v1/student/week/{mentorshipId}/weeks-with-contents`| ⟡ `ROLE_STUDENT` (Enrolled)| `mentorshipId` (path) | `MentorshipWeeksWithContentsResponse` |
| `POST`| `/api/v1/week/create` | ⟡ `ROLE_MENTOR` (Owner)| `CreateWeekrequest` | `SimpleResponse` |
| `DELETE`| `/api/v1/week/{id}` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path) | `SimpleResponse` |
| `PATCH`| `/api/v1/week/{id}` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path), `UpdateWeekRequest` | `SimpleResponse` |
| `GET` | `/api/v1/week/{mentorshipId}/weeks` | ⟡ `ROLE_MENTOR` (Owner)| `mentorshipId` (path) | `MentorshipWeeksResponse` |
| `GET` | `/api/v1/week/{weekId}/contents` | ⟡ `ROLE_MENTOR` (Owner)| `weekId` (path) | `WeekContentsResponse` |
| `POST`| `/lectures` | ⟡ `ROLE_MENTOR` (Owner)| `LectureRequestDTO` | `SimpleResponse` |
| `GET` | `/lectures/{id}` | 🔐 Enrolled / Owner / Admin| `id` (path) | `LectureResponseDTO` |
| `DELETE`| `/lectures/{id}` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path) | `SimpleResponse` |
| `PATCH`| `/lectures/{id}` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path), `LectureRequestDTO` | `SimpleResponse` |
| `GET` | `/lectures/week/{weekId}` | 🔐 Enrolled / Owner / Admin| `weekId` (path) | `List<LectureResponseDTO>` |
| `GET` | `/lectures/mentorship/{mentorshipId}`| 🔐 Enrolled / Owner / Admin| `mentorshipId` (path) | `List<LectureResponseDTO>` |

---

### 9.6 Tasks & Submissions (13 Endpoints)
**Sources:** [TaskController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/tasks/TaskController.java) (10), [TaskSubmissionController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/tasks/TaskSubmissionController.java) (3)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `POST` | `/api/v1/task` | ⟡ `ROLE_MENTOR` | Multipart: `req` (`CreateTaskRequest`), `file` (optional, max 10MB) | `SimpleResponse` |
| `PATCH`| `/api/v1/task/{id}/status` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path), `UpdateTaskStatusRequest` | `SimpleResponse` |
| `GET`  | `/api/v1/task/{id}` | 🔐 Enrolled / Owner / Admin| `id` (path) | `TaskResponse` |
| `PATCH`| `/api/v1/task/{id}` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path), Multipart: `req`, `file` | `SimpleResponse` |
| `DELETE`| `/api/v1/task/{id}` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path) | `SimpleResponse` |
| `GET`  | `/api/v1/task/filter/{msid}` | 🔐 Enrolled / Owner / Admin| `msid` (path) | `List<TaskResponse>` |
| `GET`  | `/api/v1/task/dashboard/{mentorshipId}`| ⟡ `ROLE_MENTOR` (Owner)| `mentorshipId` (path) | `TaskDashboardDTO` |
| `GET`  | `/api/v1/task/{taskId}/statistics` | ⟡ `ROLE_MENTOR` (Owner)| `taskId` (path) | `TaskStatisticsDTO` |
| `GET`  | `/api/v1/task/full-dashboard/{mentorshipId}`| ⟡ `ROLE_MENTOR` (Owner)| `mentorshipId` (path) | `FullTaskDashBoardDto` |
| `GET`  | `/api/v1/task/{taskId}/student` | 🔐 Enrolled Student | `taskId` (path) | `TaskWithSubmissionForStudentResponse` |
| `POST` | `/api/v1/task-submission/{taskId}` | ⟡ `ROLE_STUDENT` (Enrolled)| `taskId` (path), Multipart: `file`, `fileUrl` | `SimpleResponse` (`submission` object) |
| `GET`  | `/api/v1/task-submission/{taskId}` | ⟡ `ROLE_MENTOR` (Owner)| `taskId` (path), `page, size` | `SimpleResponse` (`submissions` list) |
| `POST` | `/api/v1/task-submission/{submissionId}/grade`| ⟡ `ROLE_MENTOR` (Owner)| `submissionId` (path), `GradeTaskSubmissionRequest` | `SimpleResponse` |

---

### 9.7 Projects & Capstones (12 Endpoints)
**Sources:** [ProjectController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/projects/ProjectController.java) (9), [ProjectSubmissionController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/projects/ProjectSubmissionController.java) (3)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `POST` | `/api/v1/project` | ⟡ `ROLE_MENTOR` | Multipart: `req` (`CreateProjectRequest`), `file` | `SimpleResponse` |
| `PATCH`| `/api/v1/project/{id}/status` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path), `UpdateProjectStatusRequest` | `SimpleResponse` |
| `GET`  | `/api/v1/project/{id}` | 🔐 Enrolled / Owner / Admin| `id` (path) | `ProjectResponse` |
| `PATCH`| `/api/v1/project` | ⟡ `ROLE_MENTOR` (Owner)| Multipart: `req` (`PatchProjectRequest`), `file` | `SimpleResponse` |
| `DELETE`| `/api/v1/project/{id}` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path) | `SimpleResponse` |
| `GET`  | `/api/v1/project/filter/{msid}` | 🔐 Enrolled / Owner / Admin| `msid` (path) | `List<ProjectResponse>` |
| `GET`  | `/api/v1/project/{projectId}/statistics`| ⟡ `ROLE_MENTOR` (Owner)| `projectId` (path) | `ProjectStatisticsDTO` |
| `GET`  | `/api/v1/project/full-dashboard/{mentorshipId}`| ⟡ `ROLE_MENTOR` (Owner)| `mentorshipId` (path) | `FullProjectDashBoardDto` |
| `GET`  | `/api/v1/project/{projectId}/student`| ⟡ `ROLE_STUDENT` (Enrolled)| `projectId` (path) | `ProjectWithSubmissionForStudentResponse` |
| `POST` | `/api/v1/project/{projectId}/submissions`| ⟡ `ROLE_STUDENT` (Enrolled)| `projectId` (path), Multipart: `file`, `fileUrl` | `SimpleResponse` |
| `GET`  | `/api/v1/project/{projectId}/submissions`| ⟡ `ROLE_MENTOR` (Owner)| `projectId` (path), `page, size` | `SimpleResponse` |
| `POST` | `/api/v1/project/submissions/{submissionId}/grade`| ⟡ `ROLE_MENTOR` (Owner)| `submissionId` (path), `GradeProjectSubmissionRequest` | `SimpleResponse` |

---

### 9.8 Quizzes, Questions & Grading (22 Endpoints)
**Sources:** [QuizController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/quiz/QuizController.java) (11), [QuestionController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/quiz/QuestionController.java) (6), [QuizSubmissionController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/quiz/QuizSubmissionController.java) (5)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `POST` | `/api/v1/quiz` | ⟡ `ROLE_MENTOR` | `QuizCreateDTO` | `SimpleResponse` |
| `PATCH`| `/api/v1/quiz/{id}` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path), `QuizUpdateDTO` | `SimpleResponse` |
| `DELETE`| `/api/v1/quiz/{id}` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path) | `SimpleResponse` |
| `GET`  | `/api/v1/quiz/{id}` | 🔐 Enrolled / Owner | `id` (path) | `QuizResponseDTO` |
| `GET`  | `/api/v1/quiz/filter/{mentorshipId}`| ⟡ `ROLE_MENTOR` (Owner)| `mentorshipId` (path) | `List<QuizResponseDTO>` |
| `GET`  | `/api/v1/quiz/student-quizzes/{mentorshipId}`| ⟡ `ROLE_STUDENT` (Enrolled)| `mentorshipId` (path) | `List<QuizResponseDTO>` |
| `GET`  | `/api/v1/quiz/dashboard/{mentorshipId}`| ⟡ `ROLE_MENTOR` (Owner)| `mentorshipId` (path) | `QuizDashboardResponseDto` |
| `GET`  | `/api/v1/quiz/statistics/{id}` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path) | `QuizStatisticsResponseDto` |
| `POST` | `/api/v1/quiz/change-status/{id}` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path), `?status=PUBLISHED` | `SimpleResponse` |
| `GET`  | `/api/v1/quiz/mentorshipQuizzesOverview/{mentorshipId}`| ⟡ `ROLE_MENTOR` (Owner)| `mentorshipId` (path) | `List<QuizOverviewDto>` |
| `GET`  | `/api/v1/quiz/QuizOverview/{quizId}`| ⟡ `ROLE_MENTOR` (Owner)| `quizId` (path) | `QuizOverviewResponseDto` |
| `POST` | `/api/v1/question` | ⟡ `ROLE_MENTOR` (Owner)| `QuestionCreateDTO` | `SimpleResponse` |
| `PATCH`| `/api/v1/question/{id}` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path), `QuestionUpdateDTO` | `SimpleResponse` |
| `DELETE`| `/api/v1/question/{quizId}/{questionId}`| ⟡ `ROLE_MENTOR` (Owner)| `quizId, questionId` (paths) | `SimpleResponse` |
| `GET`  | `/api/v1/question/student/fetch/{quizId}`| ⟡ `ROLE_STUDENT` (Enrolled)| `quizId` (path) | `List<StudentQuestionResponseDTO>` (Timer starts!) |
| `GET`  | `/api/v1/question/fetch/{quizId}`| ⟡ `ROLE_MENTOR` (Owner)| `quizId` (path) | `List<QuestionResponseDTO>` |
| `GET`  | `/api/v1/question/{id}` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path) | `QuestionResponseDTO` |
| `POST` | `/api/v1/submit-quiz-answer/{quizId}`| ⟡ `ROLE_STUDENT` (Enrolled)| `List<StudentAnswerDTO>` | `QuizSubmissionResponseDTO` |
| `GET`  | `/api/v1/answer/{studentId}/{quizId}`| ⟡ `ROLE_MENTOR` (Owner)| `studentId, quizId` (paths) | `List<StudentAnswerResponseDTO>` |
| `GET`  | `/api/v1/my-answers/{quizId}` | ⟡ `ROLE_STUDENT` (Enrolled)| `quizId` (path) | `StudentQuizReviewDTO` (`correctAnswer` scoped) |
| `GET`  | `/api/v1/submissions/quiz/{quizId}`| ⟡ `ROLE_MENTOR` (Owner)| `quizId` (path) | `List<QuizSubmissionDTO>` |
| `GET`  | `/api/v1/submissions/student/{studentId}`| ⟡ `ROLE_MENTOR` | `studentId` (path) | `List<QuizSubmissionDTO>` |

---

### 9.9 Live Sessions & Zoom Integration (12 Endpoints)
**Source:** [LiveSessionController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/livesession/LiveSessionController.java)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `POST` | `/api/v1/liveSession/create` | ⟡ `ROLE_MENTOR` | `CreateSessionDto` | `SimpleResponse` |
| `PATCH`| `/api/v1/liveSession/update/{sessionId}`| ⟡ `ROLE_MENTOR` (Owner)| `sessionId` (path), `UpdateSessionDto` | `SimpleResponse` |
| `DELETE`| `/api/v1/liveSession/delete/{sessionId}`| ⟡ `ROLE_MENTOR` (Owner)| `sessionId` (path) | `SimpleResponse` |
| `POST` | `/api/v1/liveSession/start/{sessionId}`| ⟡ `ROLE_MENTOR` (Owner)| `sessionId` (path) | `SessionResponseDto` (`hostUrl` populated) |
| `GET`  | `/api/v1/liveSession/join/{sessionId}`| ⟡ `ROLE_STUDENT` (Enrolled)| `sessionId` (path) | `SessionResponseDto` (`meetingUrl` only) |
| `POST` | `/api/v1/liveSession/end/{sessionId}`| ⟡ `ROLE_MENTOR` (Owner)| `sessionId` (path) | `SimpleResponse` |
| `POST` | `/api/v1/liveSession/snapshot/{sessionId}`| ⟡ `ROLE_MENTOR` (Owner)| `sessionId` (path), `List<Long> studentIds` | `SimpleResponse` |
| `GET`  | `/api/v1/liveSession/attendance/{sessionId}`| ⟡ `ROLE_MENTOR` (Owner)| `sessionId` (path) | `List<AttendanceResponse>` |
| `GET`  | `/api/v1/liveSession/myAttendance/{sessionId}`| ⟡ `ROLE_STUDENT` (Enrolled)| `sessionId` (path) | `AttendanceResponse` |
| `GET`  | `/api/v1/liveSession/student/upcoming`| ⟡ `ROLE_STUDENT` | None | `List<StudentUpcomingSessionResponse>` |
| `GET`  | `/api/v1/liveSession/{sessionId}`| 🔐 Enrolled / Owner | `sessionId` (path) | `SessionResponseDto` |
| `GET`  | `/api/v1/liveSession/mentorship`| ⟡ `ROLE_MENTOR` | None | `List<SessionResponseDto>` |

---

### 9.10 Real-Time Chat & Direct Messaging (16 Endpoints + WebSocket STOMP)
**Sources:** [chatRestControllers.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/chat/chatRestControllers.java) (12), [ConversationRestControllers.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/chat/ConversationRestControllers.java) (4)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `POST` | `/api/v1/chat-room/create/{mentorshipId}`| ⟡ `ROLE_MENTOR` (Owner)| `mentorshipId` (path), `roomCreateDto` | `SimpleResponse` |
| `GET`  | `/api/v1/chat-room/{mid}` | 🔐 Member / Owner | `mid` (path) | `ChatRoomResponse` |
| `GET`  | `/api/v1/chat-room/{roomId}/messages`| 🔐 Room Member | `roomId` (path), `page, size` | `PageResponse<ChatMessageResponse>` |
| `GET`  | `/api/v1/chat-room/{roomId}/members`| 🔐 Room Member | `roomId` (path) | `List<ChatRoomMemberResponse>` |
| `GET`  | `/api/v1/chat-room/mentorship/{mentorshipId}/rooms-with-status`| 🔐 Enrolled / Owner | `mentorshipId` (path) | `List<MentorshipRoomDetailsResponse>` |
| `POST` | `/api/v1/chat-room/{roomId}/join`| 🔐 Authenticated | `roomId` (path) | `SimpleResponse` |
| `GET`  | `/api/v1/chat-room/my-rooms` | 🔐 Authenticated | None | `List<ChatRoomResponse>` |
| `PUT`  | `/api/v1/chat-room` | ⟡ `ROLE_MENTOR` (Owner)| Multipart: `roomId`, `file` (avatar, max 5MB) | `SimpleResponse` |
| `GET`  | `/api/v1/chat-room/mentor/mentorships`| ⟡ `ROLE_MENTOR` | None | `List<MentorMentorshipsChatRoomsResponse>` |
| `DELETE`| `/api/v1/chat-room/{roomId}`| ⟡ `ROLE_MENTOR` (Owner)| `roomId` (path) | `SimpleResponse` |
| `PATCH`| `/api/v1/chat-room/{roomId}/messages/{messageId}`| 🔐 Message Sender | `roomId, messageId` (paths), `EditMessageRequest` | `SimpleResponse` |
| `DELETE`| `/api/v1/chat-room/{roomId}/messages/{messageId}`| 🔐 Message Sender | `roomId, messageId` (paths) | `SimpleResponse` |
| `GET`  | `/api/v1/conversation/{conversationId}/messages`| 🔐 Participant / Admin | `conversationId` (path), `page, size` | `PageResponse<ConversationMessageResponse>` |
| `PATCH`| `/api/v1/conversation/messages/{messageId}`| 🔐 Message Sender | `messageId` (path), `EditMessageRequest` | `SimpleResponse` |
| `DELETE`| `/api/v1/conversation/messages/{messageId}`| 🔐 Message Sender | `messageId` (path) | `SimpleResponse` |
| `GET`  | `/api/v1/conversation/all` | 🔐 Authenticated | None | `List<ConversationListResponse>` |

#### WebSocket STOMP Interface (`/ws`)
- **Transport:** SockJS + STOMP.
- **CONNECT Header:** `Authorization: Bearer <jwt_token>` (required).
- **Subscribe Destinations:**
  - `/topic/room/{roomId}` — Classroom group chat.
  - `/user/queue/messages` — 1-on-1 direct message feed.
  - `/user/queue/notifications` — Real-time notification push.
  - `/user/queue/errors` — Socket exception push.
- **Send Destinations:**
  - `/app/chat/{roomId}` — Group message payload: `{"message": "Hello"}`.
  - `/app/chat.private` — Direct message payload: `ConversationMessageRequest`.

---

### 9.11 In-App Notifications (7 Endpoints)
**Source:** [NotificationController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/notification/NotificationController.java)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `GET`  | `/api/v1/notifications` | 🔐 Authenticated | `page, size` | `PageResponse<NotificationResponseDto>` |
| `GET`  | `/api/v1/notifications/unread` | 🔐 Authenticated | `page, size` | `PageResponse<NotificationResponseDto>` |
| `GET`  | `/api/v1/notifications/unread/count`| 🔐 Authenticated | None | `SimpleResponse {"count": 7}` |
| `PATCH`| `/api/v1/notifications/mark-all-read`| 🔐 Authenticated | None | `SimpleResponse` |
| `PATCH`| `/api/v1/notifications/{id}/mark-read`| 🔐 Authenticated (Owner)| `id` (path) | `SimpleResponse` |
| `DELETE`| `/api/v1/notifications/delete/{id}`| 🔐 Authenticated (Owner)| `id` (path) | `SimpleResponse` |
| `DELETE`| `/api/v1/notifications/delete-for-user`| 🔐 Authenticated | None | `SimpleResponse` |

---

### 9.12 Profiles & Skills Management (18 Endpoints)
**Sources:** [StudentProfileController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/profile/StudentProfileController.java) (3), [MentorProfileInfoController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/profile/MentorProfileInfoController.java) (3), [MentorProfileForStudent.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/profile/MentorProfileForStudent.java) (4), [MentorViewStudentProfileController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/profile/MentorViewStudentProfileController.java) (4), [StudentSkillsController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/skill/StudentSkillsController.java) (3), [HomePageController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/homepage/HomePageController.java) (1)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `GET`  | `/student/profile` | ⟡ `ROLE_STUDENT` | None | `StudentProfileResponse` |
| `PATCH`| `/student/profile` | ⟡ `ROLE_STUDENT` | `StudentProfileUpdateRequest` | `SimpleResponse` |
| `PATCH`| `/student/profile/image` | ⟡ `ROLE_STUDENT` | Multipart: `image` (max 5MB) | `SimpleResponse {"imageUrl": "..."}` |
| `GET`  | `/mentor/profile` | ⟡ `ROLE_MENTOR` | None | `MentorProfileResponse` |
| `PATCH`| `/mentor/profile` | ⟡ `ROLE_MENTOR` | `UpdateMentorProfileRequest` | `SimpleResponse` |
| `PATCH`| `/mentor/profile/image` | ⟡ `ROLE_MENTOR` | Multipart: `image` (max 5MB) | `SimpleResponse {"imageUrl": "..."}` |
| `GET`  | `/api/v1/profile/mentor/{mentorEmail}`| 🔓 Public | `mentorEmail` (path) | `SimpleResponse {"mentorProfile": {...}}` |
| `GET`  | `/api/v1/profile/mentor/{mentorEmail}/mentorships`| 🔓 Public | `mentorEmail` (path), `page, size` | `SimpleResponse {"mentorships": [...]}` |
| `GET`  | `/api/v1/profile/mentor/{mentorEmail}/reviews`| 🔓 Public | `mentorEmail` (path), `page, size` | `SimpleResponse {"reviews": [...]}` |
| `GET`  | `/api/v1/profile/mentor/{mentorEmail}/full`| 🔓 Public | `mentorEmail` (path), pagination params | `SimpleResponse {"profile": {...}}` |
| `GET`  | `/profile/students/{studentId}`| ⟡ `ROLE_MENTOR` | `studentId` (path) | `SimpleResponse {"studentProfile": {...}}` |
| `GET`  | `/profile/students/{studentId}/mentorships-progress`| ⟡ `ROLE_MENTOR` | `studentId` (path) | `SimpleResponse {"progress": [...]}` |
| `GET`  | `/profile/students/students/{studentId}/projects`| ⟡ `ROLE_MENTOR` | `studentId` (path), `page, size` | `SimpleResponse {"projects": [...]}` |
| `GET`  | `/profile/students/{studentId}/full-profile`| ⟡ `ROLE_MENTOR` | `studentId` (path) | `SimpleResponse {"profile": {...}}` |
| `GET`  | `/student/skills` | ⟡ `ROLE_STUDENT` | None | `List<SkillResponse>` |
| `POST` | `/student/skills` | ⟡ `ROLE_STUDENT` | `AddSkillRequest {"skillName": "React"}` | `SkillResponse` |
| `DELETE`| `/student/skills` | ⟡ `ROLE_STUDENT` | `?skillId=10` | `SimpleResponse` |
| `GET`  | `/api/v1/homepage/full` | ⟡ `ROLE_STUDENT` | None | `HomePageResponse` |

---

### 9.13 Gamification: Badges & Awards (9 Endpoints)
**Sources:** [BadgeController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/badges/BadgeController.java) (5), [BadgeAwardController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/badges/BadgeAwardController.java) (4)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `POST` | `/api/v1/badges/mentorship/{mentorshipId}`| ⟡ `ROLE_MENTOR` (Owner)| `mentorshipId` (path), `CreateBadgeRequest` | `BadgeResponse` (Max 10 badges/course) |
| `GET`  | `/api/v1/badges/{badgeId}` | 🔐 Authenticated | `badgeId` (path) | `BadgeResponse` |
| `GET`  | `/api/v1/badges/mentorship/{mentorshipId}`| 🔐 Authenticated | `mentorshipId` (path) | `List<BadgeResponse>` |
| `PATCH`| `/api/v1/badges/{badgeId}` | ⟡ `ROLE_MENTOR` (Owner)| `badgeId` (path), `UpdateBadgeRequest` | `BadgeResponse` |
| `DELETE`| `/api/v1/badges/{badgeId}` | ⟡ `ROLE_MENTOR` (Owner)| `badgeId` (path) | `SimpleResponse` |
| `POST` | `/api/v1/badge-awards/{badgeId}/award`| ⟡ `ROLE_MENTOR` (Owner)| `badgeId` (path), `AwardBadgeRequest` | `BadgeAwardResponse` |
| `GET`  | `/api/v1/badge-awards/mentorship/{mentorshipId}`| ⟡ `ROLE_MENTOR` (Owner)| `mentorshipId` (path) | `List<BadgeAwardResponse>` |
| `GET`  | `/api/v1/badge-awards/student/{studentId}`| 🔐 Self / Admin / Mentor | `studentId` (path) | `List<BadgeAwardResponse>` (EB-26) |
| `GET`  | `/api/v1/badge-awards/badge/{badgeId}`| ⟡ `ROLE_MENTOR` (Owner)| `badgeId` (path) | `List<BadgeAwardResponse>` |

---

### 9.14 Student Progress, Achievements & Certificates (5 Endpoints)
**Sources:** [StudentAchievementController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/studentAchievementController/StudentAchievementController.java) (1), [StudentCertificatesController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/studentCertificates/StudentCertificatesController.java) (1), [MyLearningController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/mylearning/MyLearningController.java) (1), [HomePageController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/homepage/HomePageController.java) (2)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `GET` | `/api/v1/student/achievements` | ⟡ `ROLE_STUDENT` | `page, size` | `StudentAchievementResponse` |
| `GET` | `/api/v1/student/certificates` | ⟡ `ROLE_STUDENT` | `page, size` | `PageResponse<CertificateResponse>` |
| `GET` | `/api/v1/my-learning` | ⟡ `ROLE_STUDENT` | `page, size` | `PageResponse<MyLearningResponseDto>` |
| `GET` | `/api/v1/homepage/progress/{id}` | ⟡ `ROLE_STUDENT` (Enrolled)| `id` (path) | `SimpleResponse {"progress": 75}` (EB-24) |
| `GET` | `/api/v1/homepage/recommended` | ⟡ `ROLE_STUDENT` | None | `List<MentorshipCardResponse>` |

---

### 9.15 Mentor Dashboards & Analytics (12 Endpoints)
**Source:** [MentorDashboardController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/MentorDashboard/MentorDashboardController.java)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `GET` | `/api/v1/dashboard/cards` | ⟡ `ROLE_MENTOR` | None | `SimpleResponse {"cards": DashboardCardsResponse}` |
| `GET` | `/api/v1/dashboard/reviews` | ⟡ `ROLE_MENTOR` | `page, size` | `SimpleResponse {"reviews": PageResponse}` |
| `GET` | `/api/v1/dashboard/sessions` | ⟡ `ROLE_MENTOR` | `page, size` | `SimpleResponse {"sessions": PageResponse}` |
| `GET` | `/api/v1/dashboard/revenue-chart` | ⟡ `ROLE_MENTOR` | `?months=6` | `SimpleResponse {"sales-chart": List}` |
| `GET` | `/api/v1/dashboard/mentorships` | ⟡ `ROLE_MENTOR` | `page, size` | `SimpleResponse {"mentorships": PageResponse}` |
| `GET` | `/api/v1/dashboard/{id}/stats` | ⟡ `ROLE_MENTOR` (Owner)| `id` (path) | `MentorshipStatsResponse` |
| `GET` | `/api/v1/dashboard/{id}/reviews`| ⟡ `ROLE_MENTOR` (Owner)| `id` (path), `page, size` | `SimpleResponse {"reviews": PageResponse}` |
| `GET` | `/api/v1/dashboard/{id}/top-learners`| ⟡ `ROLE_MENTOR` (Owner)| `id` (path), `page, size` | `SimpleResponse {"top-learners": PageResponse}` |
| `GET` | `/api/v1/dashboard/{id}/students-ranks`| ⟡ `ROLE_MENTOR` (Owner)| `id` (path), `page, size` | `SimpleResponse {"students-ranks": PageResponse}` |
| `GET` | `/api/v1/dashboard/students` | ⟡ `ROLE_MENTOR` | `page, size` | `SimpleResponse {"students": PageResponse}` |
| `GET` | `/api/v1/dashboard` | ⟡ `ROLE_MENTOR` | Pagination params for sub-sections | `SimpleResponse {"dashboard": MentorDashboardResponse}` |
| `GET` | `/api/v1/dashboard/mentorship/{id}`| ⟡ `ROLE_MENTOR` (Owner)| `id` (path), pagination params | `SimpleResponse {"dashboard": MentorshipDashboardResponse}` |

---

### 9.16 Contact Us Support Inquiries (9 Endpoints)
**Source:** [ContactMessageController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/contactus/ContactMessageController.java)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `POST` | `/api/v1/contact/save-message` | 🔓 Public | [ContactMessageRequestDto](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/contactus/ContactMessageRequestDto.java) | `SimpleResponse` (Status: 201 Created) |
| `GET`  | `/api/v1/contact/all-messages` | ⟡ `ROLE_ADMIN` | None | `SimpleResponse {"Data": List<ContactMessageResponseDto>}` |
| `GET`  | `/api/v1/contact/message/{id}` | ⟡ `ROLE_ADMIN` | `id` (path) | `SimpleResponse {"Data": ContactMessageResponseDto}` |
| `PUT`  | `/api/v1/contact/message/{id}/status`| ⟡ `ROLE_ADMIN` | `id` (path), `?status=UNDER_REVIEW` | `SimpleResponse` |
| `GET`  | `/api/v1/contact/messages/filter` | ⟡ `ROLE_ADMIN` | `?status=PENDING`, `page, size` | `SimpleResponse {"Data": PageResponse}` |
| `POST` | `/api/v1/contact/message/{id}/reply`| ⟡ `ROLE_ADMIN` | `id` (path), `String reply` (HTML-escaped, EB-31) | `SimpleResponse` |
| `POST` | `/api/v1/contact/message/{id}/notification`| ⟡ `ROLE_ADMIN` | `id` (path), `?title=...`, `String content` | `SimpleResponse` |
| `DELETE`| `/api/v1/contact/message/{id}` | ⟡ `ROLE_ADMIN` | `id` (path) | `SimpleResponse` |
| `DELETE`| `/api/v1/contact/messages` | ⟡ `ROLE_ADMIN` | None | `SimpleResponse` (Clears all messages) |

---

### 9.17 File Download & Streaming Gateway (5 Endpoints)
**Source on main:** [FileController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/file/FileController.java) (Merged via [PR #77](https://github.com/MooRaa86/EduNest/pull/77))

> [!NOTE]
> **Subsystem Status:** All 5 endpoints below stream files through authenticated server proxying directly from the Supabase Storage private S3 bucket (`edunest-private`) with strict ownership validation, fully active on `origin/main` via [PR #77](https://github.com/MooRaa86/EduNest/pull/77).

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `GET` | `/files/task-submissions/{id}` | 🔐 Owner Student / Mentor / Admin | `id` (path), `?download=false` | Binary Stream (`application/octet-stream`) [Proxied from Supabase S3] |
| `GET` | `/files/tasks/{id}/attachment` | 🔐 Enrolled / Mentor / Admin | `id` (path), `?download=false` | Binary Stream (`application/octet-stream`) [Proxied from Supabase S3] |
| `GET` | `/files/project-submissions/{id}` | 🔐 Owner Student / Mentor / Admin | `id` (path), `?download=false` | Binary Stream (`application/octet-stream`) [Proxied from Supabase S3] |
| `GET` | `/files/projects/{id}/attachment` | 🔐 Enrolled / Mentor / Admin | `id` (path), `?download=false` | Binary Stream (`application/octet-stream`) [Proxied from Supabase S3] |
| `GET` | `/files` | 🔐 Authenticated | `?filePath=...&download=false` | Binary Stream (`application/octet-stream`) (EB-27) [Proxied from Supabase S3] |

---

### 9.18 Admin Operations & Governance (34 Endpoints)
**Sources:** [AdminController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/admin/AdminController.java) (3, bootstrap protection merged via [PR #41](https://github.com/MooRaa86/EduNest/pull/41)), [AdminDashboardController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/admin/AdminDashboardController.java) (5), [AdminNotificationController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/admin/AdminNotificationController.java) (3), [AdminPaymentsController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/admin/AdminPaymentsController.java) (3), [AdminProfileAndSettingsController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/admin/AdminProfileAndSettingsController.java) (8), [AdminUserDirectoryController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/admin/AdminUserDirectoryController.java) (5), [AdminBadgeController.java](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/controller/admin/AdminBadgeController.java) (7)

| Method | Full Path | Auth | Request Body / Params | Response Body Structure |
|:---|:---|:---:|:---|:---|
| `POST` | `/api/v1/admin/register` | 🔓 Public (One-Shot) | [RegisterAdminRequest](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/auth/RegisterAdminRequest.java) | `SimpleResponse` (Status: 201 Created) — One-shot bootstrap guard active on `main` (PR #41) |
| `GET`  | `/api/v1/admin` | ⟡ `ROLE_ADMIN` | None | `SimpleResponse {"admins": List<AdminAccResponse>}` — Password hash field removed on `main` (PR #41) |
| `DELETE`| `/api/v1/admin/{email}` | ⟡ `ROLE_ADMIN` | `email` (path) | `SimpleResponse` — Last-admin deletion guard active on `main` (PR #41) |
| `GET`  | `/api/v1/admin/dashboard/cards` | ⟡ `ROLE_ADMIN` | None | `SimpleResponse {"cards": ...}` |
| `GET`  | `/api/v1/admin/dashboard/sessions-chart`| ⟡ `ROLE_ADMIN` | `?months=6` | `SimpleResponse {"chart": ...}` |
| `GET`  | `/api/v1/admin/dashboard/notifications`| ⟡ `ROLE_ADMIN` | `page, size` | `SimpleResponse {"notifications": ...}` |
| `GET`  | `/api/v1/admin/dashboard/top-mentors`| ⟡ `ROLE_ADMIN` | `?limit=5` | `SimpleResponse {"topMentors": ...}` |
| `GET`  | `/api/v1/admin/dashboard/full` | ⟡ `ROLE_ADMIN` | Combined params | `SimpleResponse {"dashboard": ...}` |
| `GET`  | `/api/v1/admin/notifications` | ⟡ `ROLE_ADMIN` | `page, size` | `PageResponse<AdminNotificationResponse>` |
| `DELETE`| `/api/v1/admin/notifications/{id}` | ⟡ `ROLE_ADMIN` | `id` (path) | `SimpleResponse` |
| `DELETE`| `/api/v1/admin/notifications/delete-all`| ⟡ `ROLE_ADMIN` | None | `SimpleResponse` |
| `GET`  | `/api/v1/admin/payments/sales-chart`| ⟡ `ROLE_ADMIN` | `?months=6` | `SimpleResponse {"chart": List<SalesChartResponse>}` |
| `GET`  | `/api/v1/admin/payments/enrollments`| ⟡ `ROLE_ADMIN` | `page, size` | `PageResponse<EnrollmentPaymentResponse>` |
| `GET`  | `/api/v1/admin/payments/full` | ⟡ `ROLE_ADMIN` | Combined params | `SimpleResponse {"data": FullPaymentPageResponse}` |
| `GET`  | `/admin/profile` | ⟡ `ROLE_ADMIN` | None | `AdminProfileResponse` |
| `PATCH`| `/admin/profile` | ⟡ `ROLE_ADMIN` | `UpdateAdminProfileRequest` | `SimpleResponse` |
| `PATCH`| `/admin/profile/image` | ⟡ `ROLE_ADMIN` | Multipart: `image` (max 5MB) | `SimpleResponse {"imageUrl": "..."}` |
| `POST` | `/admin/profile/change-email/request`| ⟡ `ROLE_ADMIN` | `ChangeEmailRequest` | `SimpleResponse` |
| `POST` | `/admin/profile/change-email/confirm`| ⟡ `ROLE_ADMIN` | `?otp=123456` | `SimpleResponse` |
| `PATCH`| `/admin/profile/change-password` | ⟡ `ROLE_ADMIN` | `ChangePasswordRequest` | `SimpleResponse` |
| `GET`  | `/admin/commission` | ⟡ `ROLE_ADMIN` | None | `SimpleResponse {"commission": 15.0}` |
| `PATCH`| `/admin/commission` | ⟡ `ROLE_ADMIN` | `UpdateCommissionRequest {"rate": 20.0}` | `SimpleResponse` |
| `GET`  | `/api/v1/admin/users/{id}` | ⟡ `ROLE_ADMIN` | `id` (path) | `AdminStudentDetailResponse` or Mentor |
| `GET`  | `/api/v1/admin/users/role/{roleName}`| ⟡ `ROLE_ADMIN` | `roleName` (path), `page, size` | `PageResponse<UserDirectoryItemResponse>` |
| `GET`  | `/api/v1/admin/users/dashboard-summary`| ⟡ `ROLE_ADMIN` | None | `AdminDashboardSummaryResponse` |
| `POST` | `/api/v1/admin/users/send-email` | ⟡ `ROLE_ADMIN` | [SendUserEmailRequest](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/admin/request/SendUserEmailRequest.java) | `SimpleResponse` |
| `POST` | `/api/v1/admin/users/send-notification`| ⟡ `ROLE_ADMIN` | [SendUserNotificationRequest](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/admin/request/SendUserNotificationRequest.java) | `SimpleResponse` |
| `POST` | `/api/admin/badges` | ⟡ `ROLE_ADMIN` | [CreateAdminBadgeRequest](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/admin/request/CreateAdminBadgeRequest.java) | `AdminBadgeResponse` |
| `PATCH`| `/api/admin/badges/{badgeId}` | ⟡ `ROLE_ADMIN` | `badgeId` (path), `UpdateAdminBadgeRequest` | `AdminBadgeResponse` |
| `DELETE`| `/api/admin/badges/delete/{badgeId}`| ⟡ `ROLE_ADMIN` | `badgeId` (path) | `SimpleResponse` |
| `POST` | `/api/admin/badges/assign` | ⟡ `ROLE_ADMIN` | [AssignAdminBadgeRequest](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/admin/request/AssignAdminBadgeRequest.java) | `UserAdminBadgeResponse` |
| `GET`  | `/api/users/{userId}/badges` | 🔐 Authenticated | `userId` (path) | `List<UserAdminBadgeResponse>` |
| `DELETE`| `/api/admin/badges/{userBadgeId}` | ⟡ `ROLE_ADMIN` | `userBadgeId` (path) | `SimpleResponse` |
| `GET`  | `/api/admin/badges` | ⟡ `ROLE_ADMIN` | None | `List<AdminBadgeResponse>` |

---

### 9.19 Deleted Breaking Endpoints (3 Endpoints)

| Method | Deleted Endpoint | Former Purpose | Disposal Status |
|:---|:---|:---|:---|
| `POST` | `/send` | Legacy unauthenticated platform mail dispatch | **Permanently Deleted in PR #48 (EB-18 / D-05). Returns 404.** |
| `POST` | `/api/v1/notifications/send` | Legacy unauthenticated test push notification helper | **Permanently Deleted in PR #48 (EB-18 / D-05). Returns 404.** |
| `POST` | `/api/v1/admin/notifications/send` | Legacy admin notification test helper | **Permanently Deleted in PR #75 (EB-84 / D-05). Returns 404.** |

---

## 10. API Contract Changes: Before vs After Comparisons

### Comparison 1: User Login Response Contract
- **Endpoint:** `POST /login-api`

#### Before Sprint 1:
```json
{
  "apiResponse": {
    "status": "Login successful",
    "jwt": "eyJhbGciOi...",
    "Role": "ROLE_STUDENT"
  }
}
```

#### After Sprint 1 (Current):
```http
HTTP/1.1 200 OK
Authorization: Bearer eyJhbGciOi...
Content-Type: application/json

{
  "apiResponse": {
    "status": "Login successful",
    "Role": "ROLE_STUDENT",
    "jwt": "eyJhbGciOi..."
  }
}
```
*Note: `jwt` in body is transitional. Frontend must read the `Authorization` header.*

---

### Comparison 2: Password Reset Recovery
- **Endpoints:** `POST /forget-password/**`

#### Before Sprint 1:
Single POST containing email, OTP, and new password in one step.

#### After Sprint 1 (Current 3-Step Wizard):
1. `POST /forget-password` (Sends OTP)
2. `POST /forget-password/verify-otp` (Returns `resetToken`)
   ```json
   {
     "apiResponse": {
       "message": "OTP verified successfully.",
       "resetToken": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
     }
   }
   ```
3. `POST /forget-password/reset` (Consumes `resetToken` atomically)
   ```json
   {
     "email": "student@example.com",
     "resetToken": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
     "newPassword": "NewPassword123!"
   }
   ```

---

### Comparison 3: Error Envelope Contract
- **All Endpoints**

#### Before Sprint 1:
Inconsistent structures; raw SQL exceptions leaked index names; HTTP 409 returned for business rules.

#### After Sprint 1 (Current):
```json
{
  "timestamp": "2026-11-03T18:30:00",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "ERR_VALIDATION",
  "message": "Please check the submitted fields.",
  "path": "/api/v1/register/student",
  "refId": null,
  "fields": {
    "email": "Invalid email format."
  }
}
```

---

### Comparison 4: Quiz Review Answer Keys
- **Endpoint:** `GET /api/v1/my-answers/{quizId}`

#### Before Sprint 1:
`correctAnswer` returned immediately upon opening the quiz attempt.

#### After Sprint 1 (Current):
`correctAnswer` is set to `null` (or omitted) until the student genuinely submits their answers (`score != null`).
```json
{
  "quizId": 12,
  "score": null,
  "questions": [
    {
      "questionId": 101,
      "studentAnswer": "B",
      "correctAnswer": null
    }
  ]
}
```

---

### Comparison 5: Admin Directory Password Removal (EB-16) — Merged on main via PR #41
- **PR:** [Pull Request #41](https://github.com/MooRaa86/EduNest/pull/41) (commit `38a9709`)
- **Endpoint:** `GET /api/v1/admin`

#### Before Sprint 1:
The admin directory DTO (`AdminAccResponse`) included the password hash field:
```json
{
  "apiResponse": {
    "admins": [
      {
        "id": 1,
        "email": "admin@edunest.com",
        "name": "Super Admin",
        "password": "$2a$10$e8w..."
      }
    ]
  }
}
```

#### After Sprint 1 / Active on main:
The `password` field is permanently removed from [AdminAccResponse](https://github.com/MooRaa86/EduNest/blob/main/src/main/java/com/example/gradproj/EduNest/dto/auth/AdminAccResponse.java):
```json
{
  "apiResponse": {
    "admins": [
      {
        "id": 1,
        "email": "admin@edunest.com",
        "name": "Super Admin"
      }
    ]
  }
}
```

---

### Comparison 6: Public Media Asset Delivery (EB-83) — Merged on main via PR #77
- **PR:** [Pull Request #77](https://github.com/MooRaa86/EduNest/pull/77) (commits `bf395a7`, `25e2150`)
- **Endpoints:** `POST /api/v1/mentorship/{id}/change-cover-image`, `PATCH /{student|mentor|admin}/profile/image`

#### Before PR #77 (Local Disk Storage):
Returns server-relative path:
```json
{
  "apiResponse": {
    "imageUrl": "/uploads/student/45-EduNest-7a8f9c12.png"
  }
}
```

#### After PR #77 / Active on main (Supabase Storage S3 CDN):
Returns absolute Supabase CDN URL:
```json
{
  "apiResponse": {
    "imageUrl": "https://xyzcompany.supabase.co/storage/v1/object/public/edunest-public/student/45-EduNest-7a8f9c12.png"
  }
}
```

---

## 11. Data Models, Enums & Type Definitions

The canonical TypeScript enum definitions matching backend Java sources:

```typescript
export enum ThemeMode {
  LIGHT = "LIGHT",
  DARK = "DARK"
}

export enum AdminBadgeType {
  TOP_MENTOR = "TOP_MENTOR",
  ACADEMIC_EXCELLENCE = "ACADEMIC_EXCELLENCE",
  COMMUNITY_LEADER = "COMMUNITY_LEADER",
  INNOVATOR_AWARD = "INNOVATOR_AWARD"
}

export enum BadgeCategory {
  ACHIEVEMENT = "ACHIEVEMENT",
  PERFORMANCE = "PERFORMANCE",
  CONSISTENCY = "CONSISTENCY",
  PROBLEM_SOLVING = "PROBLEM_SOLVING",
  CREATIVITY = "CREATIVITY",
  LEADERSHIP = "LEADERSHIP",
  COMMUNITY = "COMMUNITY",
  SPECIAL_RECOGNITION = "SPECIAL_RECOGNITION"
}

export enum SessionStatus {
  SCHEDULED = "SCHEDULED",
  LIVE = "LIVE",
  ENDED = "ENDED",
  CANCELLED = "CANCELLED"
}

export enum DifficultyLevel {
  ALL_LEVEL = "ALL_LEVEL",
  BEGINNER = "BEGINNER",
  INTERMEDIATE = "INTERMEDIATE",
  EXPERT = "EXPERT"
}

export enum Status {
  DRAFT = "DRAFT",
  ACTIVE = "ACTIVE",
  COMPLETED = "COMPLETED"
}

export enum MessageStatus {
  PENDING = "PENDING",
  UNDER_REVIEW = "UNDER_REVIEW",
  COMPLETED = "COMPLETED"
}

export enum NotificationType {
  ANNOUNCEMENT = "ANNOUNCEMENT",
  QUIZ = "QUIZ",
  SESSION = "SESSION",
  TASK = "TASK",
  PROJECT = "PROJECT",
  SUPPORT = "SUPPORT",
  BADGE = "BADGE",
  CERTIFICATE = "CERTIFICATE",
  LIVE_SESSION = "LIVE_SESSION",
  MENTORSHIP = "MENTORSHIP",
  REVIEW = "REVIEW"
}

export enum ProjectStatus {
  DRAFT = "DRAFT",
  PUBLISHED = "PUBLISHED",
  CLOSED = "CLOSED"
}

export enum AnswerChoices {
  A = "A",
  B = "B",
  C = "C",
  D = "D"
}

export enum QuizStatus {
  DRAFT = "DRAFT",
  PUBLISHED = "PUBLISHED",
  CLOSED = "CLOSED"
}

export enum EducationalLevel {
  FRESH = "FRESH",
  JUNIOR = "JUNIOR",
  SENIOR = "SENIOR"
}

export enum OtpType {
  VERIFY = "VERIFY",
  RESET_PENDING = "RESET_PENDING",
  RESET = "RESET",
  DEFAULT = "DEFAULT",
  DELETE = "DELETE",
  RESTORE = "RESTORE",
  CHANGE_EMAIL = "CHANGE_EMAIL"
}

export enum Media {
  GITHUB = "GITHUB",
  LINKEDIN = "LINKEDIN",
  FACEBOOK = "FACEBOOK"
}

export enum SubmissionStatus {
  SUBMITTED = "SUBMITTED",
  GRADED = "GRADED",
  CLOSED = "CLOSED",
  IN_PROGRESS = "IN_PROGRESS"
}

export enum TaskStatus {
  DRAFT = "DRAFT",
  PUBLISHED = "PUBLISHED",
  CLOSED = "CLOSED"
}
```

---

## 12. Frontend Integration Checklist

### Ready to Integrate Now (Merged on `main`)
- [ ] **Authorization Header:** Read JWT token from response header on `/login-api`.
- [ ] **Lockout & Rate Limiting:** Show countdown on HTTP 429 using `Retry-After`.
- [ ] **Password Reset:** Implement 3-step token exchange recovery flow.
- [ ] **Unified Error Handling:** Axios interceptor parsing `ApiError` envelope and `errorCode`.
- [ ] **Effective Price Filter:** Explore search slider filtering on discounted effective price.
- [ ] **Curriculum Read Guards:** Padlock (🔒) indicators for non-enrolled students on course lectures.
- [ ] **Quiz Attempt Wizard:** Modal confirmation before fetching questions (starts timer).
- [ ] **Quiz Answer Scoping:** Omit answer key view prior to attempt submission.
- [ ] **Live Sessions:** Mentor host button (`hostUrl`) vs Student join button (`meetingUrl`).
- [ ] **WebSocket STOMP:** SockJS connection to `/ws` with JWT auth in `CONNECT` frame.
- [ ] **Upload File Size & Type:** Client validation for 5MB image cap and rejection of GIF/BMP/SVG.
- [ ] **Supabase S3 CDN Display (EB-83 / PR #77):** Ensure avatar and cover image renderers handle absolute `https://...` Supabase CDN URLs.
- [ ] **Admin Directory Password Removal & Bootstrap Guard (EB-16 / PR #41):** Ensure admin user directory components do not expect password hashes.

### Pending Pull Request Merge (Do Not Deploy to Production Yet)
- *None — All 33 baseline changes and security stabilization tasks are merged into `origin/main`.*

### Deferred / Backlog (Sprint 2+)
- [ ] Silent Token Refresh (`/login-api/refresh`) — Awaiting EB-91 implementation.
- [ ] Stripe Payment Elements — Awaiting EB-108 implementation.

---

## 13. Environment Configuration & Development Setup

### Client-Side Environment Variables by Framework

> [!IMPORTANT]
> **Browser Security & Prefix Requirements:**  
> Modern frontend build tooling (Vite, Next.js, Create React App / Webpack) **does not expose unprefixed environment variables to browser runtime code**. Unprefixed variables (e.g., `API_BASE_URL`) will evaluate to `undefined` in client bundles.  
> You **must** prefix variables according to your framework:

#### 1. Vite (`.env` or `.env.local`)
Vite strictly requires the `VITE_` prefix to expose variables to browser client code via `import.meta.env`:
```env
# API Gateway Base URL
VITE_API_BASE_URL=http://localhost:8080

# WebSocket STOMP Endpoint
VITE_WS_BROKER_URL=http://localhost:8080/ws

# Authentication Token Key in Client Storage
VITE_AUTH_TOKEN_STORAGE_KEY=edunest_jwt_token

# Static Media Base URL (Fallback for local disk mode on main)
VITE_MEDIA_BASE_URL=http://localhost:8080
```
*Client Code Access:*
```typescript
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
```

#### 2. Next.js (`.env.local` or `.env.production`)
Next.js strictly requires the `NEXT_PUBLIC_` prefix to expose variables to browser client code via `process.env`:
```env
# API Gateway Base URL
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080

# WebSocket STOMP Endpoint
NEXT_PUBLIC_WS_BROKER_URL=http://localhost:8080/ws

# Authentication Token Key in Client Storage
NEXT_PUBLIC_AUTH_TOKEN_STORAGE_KEY=edunest_jwt_token

# Static Media Base URL (Fallback for local disk mode on main)
NEXT_PUBLIC_MEDIA_BASE_URL=http://localhost:8080
```
*Client Code Access:*
```typescript
const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;
```

#### 3. Create React App / Webpack (`.env` or `.env.local`)
Create React App strictly requires the `REACT_APP_` prefix to inject variables into client bundles via `process.env`:
```env
# API Gateway Base URL
REACT_APP_API_BASE_URL=http://localhost:8080

# WebSocket STOMP Endpoint
REACT_APP_WS_BROKER_URL=http://localhost:8080/ws

# Authentication Token Key in Client Storage
REACT_APP_AUTH_TOKEN_STORAGE_KEY=edunest_jwt_token

# Static Media Base URL (Fallback for local disk mode on main)
REACT_APP_MEDIA_BASE_URL=http://localhost:8080
```
*Client Code Access:*
```typescript
const apiBaseUrl = process.env.REACT_APP_API_BASE_URL;
```

### CORS Configuration Contract
- `allowedOrigins`: `*` (wildcard permitted).
- `allowedMethods`: `GET, POST, PUT, DELETE, PATCH, OPTIONS`.
- `allowedHeaders`: `*`.
- `exposedHeaders`: `Authorization`.
- `allowCredentials`: `false` (Strictly `false` per Decision D-23; tokens must be transmitted in HTTP headers, not cookies).

---

## 14. Open Questions & Backend Dependencies

1. **Sprint 2 Refresh Token Architecture (EB-91):**
   - The proposed rotating refresh token architecture is pending review by the team lead. Once merged, frontend will implement an Axios silent-refresh queue.
2. **Reviewer Email Privacy (MSH-09 / D-04):**
   - Course reviews currently include reviewer `studentEmail`. Frontend should verify whether UI design requires reviewer emails; if not, backend will remove this field.
3. **Generic vs Typed File Downloads:**
   - Frontend developers may use either typed endpoints (`/files/task-submissions/{id}`) or the generic resolver (`/files?filePath=...&download=true`).

---

*End of Frontend Integration Handoff Document (Verified v2.0.0).*
