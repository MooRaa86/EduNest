# P2-03 / EB-37 — Authorization Probe Matrix (traceability registry)

**Doctrine: Option A** — probes document *today's* behavior, named after the audit
register (`mds/authentication-security/authorization-rules.md`). Every probe carries
`@Tag("critical")` and a `Gxx / today…` name; the comment block inside each test names
the EB whose fix FLIPS the expectation. When a fix lands, flip the probe in the SAME
PR as the fix — a probe that fails after its fix-EB landed is the suite doing its job.

**Base:** `ProdChainIntegrationTestBase` — `@ActiveProfiles({"prod","test"})` (prod
first → real JWT chain + authorization matrix boot; test last → H2 + fake env wins
over `application-prod.properties`). Zero env setup, no Docker (D-27).

**Fixture:** `AuthFixture` — seeds enabled/not-deleted users per role, builds the
minimal mentorship chain, mints real JWTs signed with the test-profile secret.

> ⚠️ **APP CONTRACT QUIRK found while building this suite** (P1-05 / EB-12 candidate):
> `JwtTokenValidatorFilter` passes the **raw** `Authorization` header into
> `validateToken` — the app does **not** accept a `Bearer ` prefix. Probes send the
> raw JWT (as the FE does today).

## Registry — G1..G15 + SEL set

| ID | Hole / boundary | Probe (class → test) | EXPECT TODAY | Flip target |
|----|-----------------|----------------------|--------------|-------------|
| G1 | WS/STOMP surface security | **deferred** → [P2-05] EB-39 | — | EB-39 |
| G2 | stranger reads private conversation | `ConversationProbesTest#s g2_` | **403 Forbidden** (CHAT-02 fixed) | [P1-13] EB-20 (delivered) |
| G3 | stranger edits foreigner's DM message | `ConversationProbesTest#s g3_` | **403 Forbidden** (CHAT-03 fixed) | [P1-13] EB-20 (delivered) |
| G3b | stranger edits foreigner's room message | `ConversationProbesTest#s g3b_` | **400** — sender-only IS enforced on room surface (⚠️ register correction; room-membership gap tracked by G13) | EB-19 revisit w/ EB-12 (may restrengthen to 403) |
| G4 | lecture read x3 (id / mentorship / week) | `ContentReadProbesTest` (3 tests) | 200 (media URLs leak to any authed user) | [P1-14] EB-21 → 403 |
| G5 | foreigner lists tasks (incl. DRAFT) | `ContentReadProbesTest g5_` | 200 | [P1-14] EB-21 → 403 |
| G6 | task/project reads + filter list | `ContentReadProbesTest` (3 tests) | 200 | [P1-14] EB-21 → 403 |
| G7 | foreign leaderboard w/ member emails | `LeaderboardDraftProbesTest g7_` | 200 + member email IN body (assert on PII leak, not just status) | [P1-16] EB-23 → 403 |
| G8 | DRAFT mentorship detail readable | `LeaderboardDraftProbesTest g8_` | 200 | [P1-15] → 404/403 |
| G8b | ANON reads DRAFT overview (public path) | `LeaderboardDraftProbesTest g8b_` | 2xx (query has no status filter) | [P1-15] → 404 |
| G9 | generic `/files?filePath=` no ownership | `FileNotifyBadgeProbesTest g9_` | 200 + file bytes for foreigner | [P1-20] EB-27 → 403 |
| G9r | traversal guard-rail (FIXED behavior) | same test, second call | **400 forever — do not flip** | stays |
| G10 | notification push to any email | `FileNotifyBadgeProbesTest g10_` | **404 Not Found** (deleted per D-05) | [P1-11] EB-18 (delivered) |
| G11 | foreign student's badge awards | `FileNotifyBadgeProbesTest g11_` | 200 | [P1-19] → 403 |
| G12 | foreign mentor creates room | `RoomProbesTest g12_` | **403 Forbidden** (mentorship ownership enforced) | [P1-10] EB-17 (delivered) |
| G13 | enrolled-but-never-joined reads room | `RoomProbesTest g13_` | 200 (membership = enrollment proxy) | [P1-12] EB-19 → 403 |
| G14 | `/uploads/**` anonymous-public (folder mismatch) | `UploadsServingProbeTest g14_` | 200 for ANON | [P1-21] EB-28 / D-25 → private/404 |
| G15 | question re-parent, target-quiz unvalidated | `QuestionReparentProbeTest g15_` | **200 + moved into foreign quiz** (⚠️ register correction: gate checks CURRENT quiz's owner only; actor = own question → foreign quiz) | [P1-19] → 403 |

## Route matrix pins ([P1-03] as coded after EB-9 collapse & EB-10 single-source policy)

| Boundary | Probe (`PublicRouteMatrixProbeTest`) | EXPECT |
|----------|--------------------------------------|--------|
| `/v3/api-docs` public | `apiDocsPublic` | 200 ANON |
| `/api/v1/contact/save-message` public | `contactSavePublic` | 201 (valid body) |
| `/api/v1/contact/all-messages` protected | `adminContactMessagesAnon401` | 401 ANON |
| `/api/v1/admin` protected | `adminListAnon401` | 401 ANON |
| `/student/profile/**` protected | `studentProfileAnon401` | 401 ANON |
| `hasRole(ADMIN)` boundary | `adminRouteStudent403` | 403 STUDENT |
| `/settings/**` protected | `settingsAnon401` | 401 ANON |
| Public API + stale/garbage token (3 token shapes) | `staleTokenOnApiDocsPublic200`, `staleTokenOnContactSavePublic201`, `staleTokenOnExploreMentorshipPublic200`, `staleTokenOnCategoriesPublic200` | 200/201 (graceful anonymous fallback) |
| Public upload + stale/garbage token | `staleTokenOnPublicUploadPathNot401` | 404 resource (NOT 401 JWT reject) |
| Protected route + stale/garbage token | `staleTokenOnSettingsProtected401`, `staleTokenOnAdminProtected401`, `staleTokenOnContactAllMessagesProtected401` | 401 `ERR_INVALID_TOKEN` |
| Dual-mode public overview (anonymous / stale / valid) | `mentorshipOverviewAnonymous`, `mentorshipOverviewStaleTokenFallback`, `mentorshipOverviewValidTokenEnrolled` | 200 unauthenticated view vs 200 authenticated view |
| Protected route + valid token (ADMIN) | `validTokenAdminOnContactAllMessages200` | 200 OK |

## Error-shape snapshots ([P1-05] / SEL-14, flips with EB-12)

| ID | Shape today | Probe (`ErrorShapeProbeTest`) | Flip |
|----|-------------|------------------------------|------|
| E1 | unexpected exception → **409** w/ raw `e.getMessage()` (catch-all L223) | `e1_` | EB-12 → 500 + sanitized |
| E2 | 403 access-denied body (today's dialect) | `e2_` | EB-12 normalizes |
| E3 | 401 entry-point body (today's dialect) | `e3_` | EB-12 normalizes |
| E4 | field-validation map → **400** (`D-26`: KEPT contract) | `e4_` | never flips |

## Boundary / absence probes (SEL set)

| ID | Reality today | Probe (`SecurityBoundaryProbesTest`) | Flip |
|----|---------------|--------------------------------------|------|
| P1-08 | five bad logins → all 401, **no 429 anywhere** (no limiter) | `p08_` | [P1-08] EB-15 → 429 |
| P1-09 | `/api/v1/admin/register` anonymous-reachable (not 401/403) | `p09_` | [P1-09] EB-16 |
| P1-07 | reset endpoint accepts a fresh RESET OTP → 2xx (flow reachable); strict single-use semantics themselves are EB-14's delivery — EB-14 reuses this probe for the reuse-assert | `p07_` | [P1-07] EB-14 |

## Method-security & ownership probes ([P1-10] / EB-17 / SEL-09 / TD-07)

| Boundary | Probe (`MethodSecurityOwnershipProbesTest`) | EXPECT |
|---|---|---|
| AdminBadge anonymous access rejected | `adminBadge_anonymous_returns401` | 401 ANON |
| AdminBadge non-admin access rejected | `adminBadge_student_returns403`, `adminBadge_mentor_returns403` | 403 Forbidden |
| AdminBadge admin access allowed | `adminBadge_admin_returnsSuccess` | 200 OK |
| MentorDashboard anonymous access rejected | `mentorDashboard_anonymous_returns401` | 401 ANON |
| MentorDashboard non-mentor access rejected | `mentorDashboard_student_returns403`, `mentorDashboard_admin_returns403` | 403 Forbidden |
| MentorDashboard mentor access allowed | `mentorDashboard_salesChartData_mentor_returns200`, `mentorDashboard_students_mentor_returns200` | 200 OK |
| Mentorship stats anonymous access rejected | `mentorshipDashboardStats_anonymous_returns401` | 401 ANON |
| Mentorship stats non-mentor access rejected | `mentorshipDashboardStats_student_returns403`, `mentorshipDashboardStats_admin_returns403` | 403 Forbidden |
| Mentorship stats foreign mentor rejected | `mentorshipDashboardStats_foreignMentor_returns403` | 403 Forbidden |
| Mentorship stats owner mentor allowed | `mentorshipDashboardStats_ownerMentor_returns200` | 200 OK |

## Referenced-from-EB-9/EB-8/EB-13 (green already, NOT duplicated)

boot matrix (prod/dev chains), secret guard, OTP type isolation — see
`SecurityProfileBootMatrixTest`, `JwtSecretProviderTest`, `OtpTypeIsolationIntegrationTest`.

## Red-run note (EB-37 plan AC)

Reality-check history during bring-up (local, before commit):
1. `Bearer `-prefix assumption → 401 storm across probe classes; app contract is raw
   JWT (documented above; fixture corrected, not the app).
2. Token-holders not seeded in probe methods (`STRANGER`/`OUTSIDER` rows missing) →
   validator rejects unknown users → 401; fixture semantics made explicit per test.
3. Lombok `@SuperBuilder` wipes field initializers lacking `@Builder.Default` —
   `Task.status`, `Quiz.status/durationMinutes`, `Project.status/startAt/endAt`
   arrived NULL on builder-path inserts (NOT NULL violations). Probes set them
   explicitly. ⚠️ **This is a PRODUCTION data-integrity class too**: any service that
   constructs these entities via builders silently inserts NULL status fields. New
   candidate ticket for the backlog.
4. G3 edit 409 was a payload artifact (`recipientEmail` missing → WS push throws);
   with the FE-shaped payload the hole is exactly as audited: open.
5. G3b / G15 register corrections as marked in the table above.
