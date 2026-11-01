# Testing & Quality Assurance — Primary Domain Document

## 1. Overview

The project ships with **one smoke test**. There are no unit, integration, or end-to-end tests; no static analysis; no coverage tooling. This document records the as-is state and the stabilization-phase test plan baseline (execution is a next-phase activity; not started here).

> **Implemented 2026-10-28 (P2-01/EB-35, decision D-27 — no Docker):** a `test` profile now exists (`src/test/resources/application-test.properties`), backed by **H2 in-memory in MySQL-compatibility mode** — `mvnw test` boots the whole app on any machine with zero env vars (no MySQL, no Docker, no secrets). Shared bases `IntegrationTestBase` (MockMvc) + `TransactionalIntegrationTestBase` (rollback) live in `com.example.gradproj.EduNest.testing`. The smoke test is now a 3-probe proof: full boot · HTTP public route · the single native query pattern (`ROW_NUMBER() OVER`, `TotalPointsRepository`) runs on H2. Fixture builders land with P2-02.

> **Implemented 2026-10-30 (P2-02/EB-36):** five pure-unit suites (12 quiz-grading + 12 commission + 5 points-delta + 10 attendance + 3 certificate-rank = **42 tests**) now execute the flagged business math through the real production code paths — no Spring context, no DB, mocked collaborators only, and **zero production-code changes** (the pre-existing private seams `calculateScore`, `getSessionAttendance`, `calculateAttendancePoints` are reached via reflection, so the no-refactor constraint holds). Every documented-wrong branch is asserted **as it behaves today**, naming the issue ID in the test (LEARN-04/05/07/09, GAME-03/05, LS-02, certificate rank `i+1`); Phase-04 repair tasks are expected to edit these tests rather than the tests being "fixed" silently. Full suite after this milestone: `Tests run: 56, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS` (was 14 pre-P2-02).

> **Implemented 2026-11-01 (P1-18/EB-25, LEARN-01):** quiz answer-key scoping — `getStudentQuizReview` (`GET /api/v1/my-answers/{quizId}`) populates `correctAnswer` **only once the attempt is genuinely submitted** (`QuizSubmission#score != null`, the same marker `submitQuizAnswers` uses as its "already submitted" guard); pre-submission the field is **omitted from the JSON entirely** (field-level `@JsonInclude(NON_NULL)` on `StudentQuizReviewDTO`). Mentor answer view `/answer/{studentId}/{quizId}` keeps full keys with its explicit ownership assert pinned (foreign mentor 403 / owner 200). Prod-chain probes `QuizAnswerKeyScopingProbesTest` (3) — full suite: `Tests run: 462, Failures: 0, Errors: 0, Skipped: 2 — BUILD SUCCESS` (the 2 skips and the rest of the 459 are main's suites from the teammate merges, recorded in their own domain docs).

> **Implemented 2026-11-01 (P0-04/EB-2 — V-02 contact-reply HTML-injection POC, SEL-15):** `ContactReplyInjectionPocTest` (2 tests, prod-chain, `@Tag("critical")`) turns SEL-15 from ⚠️ suspected into **CONFIRMED**: the real prod chain runs public `POST /api/v1/contact/save-message` (name validated `@NotBlank` only) → ADMIN-only reply → the real `templates/emails/admin-reply.html` merge; the finished email is captured at the `EmailService` boundary by a recording subclass ("sentinel inbox" — plain bean, NOT a Mockito mock: Spring's `@Async` auto-proxy wraps the `EmailService` bean, so an injected mock reference is a Spring proxy Mockito cannot stub — `UnfinishedStubbing`/`InvalidUseOfMatchers` divergence), while template loading stays genuine production code. Verdict: hostile `<img src=x onerror=...>` (attacker name) and `<script>...` (admin reply text) survive **verbatim** — zero `&lt;` escaping anywhere on the path (contrast `AdminBadgeService` `HtmlUtils.htmlEscape`). Fix scheduled as P1-24/EB-31 (escape at merge time). Full suite after this milestone: `Tests run: 461, Failures: 0, Errors: 0, Skipped: 2 — BUILD SUCCESS` (2 pre-existing skips in `FileResponseBuilderTests`; baseline before this PR was 459). Note: rows 2–9 below predate the teammate-suite expansion — row-by-row reconciliation stays tracked under TST-01. *(Re-based onto main 2026-11-01 after the EB-25/EB-6/EB-17 merges — suite re-run green on the new base.)*

> **Implemented 2026-11-01 (P0-05/EB-3 — V-03 explore keyword wildcard POC, MSH-12):** `MentorshipExploreWildcardPocTest` (2 tests, prod-chain, `@Tag("critical")`) turns MSH-12 from ⚠️ suspected into **CONFIRMED**: the PUBLIC explore route (`GET /api/v1/mentorship/explore`, anonymous) binds `:keyword` RAW into `LIKE LOWER(CONCAT('%', :keyword, '%'))` across title/subtitle/description/category/mentor name — no `ESCAPE` clause, no pre-escaping. Verdicts: every canonical match-all payload (`%`, `%%`, `%_`, `_%`, `__`) returns the **full ACTIVE candidate set** (keyword destroyed as a selector; the seeded DRAFT stays confined — the status predicate is a separate AND-term), and `%`/`_` inside an otherwise-literal keyword silently **widens the match** (sibling pairs search as 2 instead of 1). Every `[V-03]` log line `param-received`-echoes the received keyword, proving raw `%`/`_` reached the bound SQL value (MockMvc lesson in the javadoc: pass query params via `queryParam` — an inline percent-encoded query string did not deliver the encoded keyword). H2 latencies printed, not asserted — severity sized by result-set effect. Fix scheduled as P4-10 (escape `%`/`_` with the LIKE `ESCAPE` clause, Small). *(Re-based onto main 2026-11-01 after the EB-25/EB-6/EB-17/EB-2 merges — suite re-run green on the new base.)*

## 2. Current Implementation (complete inventory)

| # | Test | File | What it covers |
|---|---|---|---|
| 1 | Context-load smoke test (3-probe) | `src/test/java/com/example/gradproj/EduNest/EduNestApplicationTests.java` | Full app boot on the `test` profile (H2, zero env vars) · public HTTP route · the single native-query pattern (`ROW_NUMBER() OVER`, `TotalPointsRepository`) on H2 |
| 2 | JWT secret fail-fast provider (P1-01/SEL-01) | `src/test/java/com/example/gradproj/EduNest/config/security/JwtSecretProviderTest.java` | Refuses boot on missing / <32-byte / retired-default secret; env-only rule |
| 3 | Rotation-safe JWT role parsing (P1-01/SEL-01) | `src/test/java/com/example/gradproj/EduNest/service/jwt/JwtRoleRotationTest.java` | Role-string rotation semantics in the login chain |
| 4 | Security profile boot matrix (P1-02/SEL-02) | `src/test/java/com/example/gradproj/EduNest/config/security/SecurityProfileBootMatrixTest.java` | Boot matrix 4/4 (profiles × `EDUNEST_UNSAFE_DEV` gate) |
| 5 | Quiz grading math (P2-02 suite 1) | `src/test/java/com/example/gradproj/EduNest/service/quiz/submission/QuizSubmissionGradingMathTest.java` | `calculateScore` per-question math, late penalty `-1 pt/min`, `(int)` truncated delta (LEARN-09) — 12 tests |
| 6 | Commission money math (P2-02 suite 2) | `src/test/java/com/example/gradproj/EduNest/service/mentorShip/MentorCommissionCalcTest.java` | Latest-rate lookup, 0..100 validation, platform cut / mentor payout split — 12 tests |
| 7 | Points delta accounting (P2-02 suite 3) | `src/test/java/com/example/gradproj/EduNest/service/points/TotalPointsApplyDeltaTest.java` | `applyDelta`: zero early return, create-on-miss, add/reduce, 0-clamp floor (GAME-05 accepted model) — 5 tests |
| 8 | Attendance math (P2-02 suite 4) | `src/test/java/com/example/gradproj/EduNest/service/livesession/LiveSessionAttendanceMathTest.java` | 5-min snapshot grid %, `>= 75%` threshold, >100% possible, snapshotless students absent from report (LS-02), Present +5 / Absent 0 hand-off — 10 tests |
| 9 | Certificate rank assignment (P2-02 suite 5) | `src/test/java/com/example/gradproj/EduNest/service/certificate/CertificateRankAssignmentTest.java` | Leaderboard-order ranks `i+1` (no tie-aware renumber), ghost student skipped without rank shift, empty-board noop — 3 tests |
| 10 | Quiz answer-key scoping probes (P1-18/EB-25, LEARN-01) | `src/test/java/com/example/gradproj/EduNest/security/probes/QuizAnswerKeyScopingProbesTest.java` | Prod-chain probes: `/my-answers` omits `correctAnswer` while the attempt is opened-but-unsubmitted (score==null, LEARN-07 side effect), key + selectedAnswer present after a genuine submit, mentor `/answer/{student}/{quiz}` foreign 403 / owner 200 — 3 tests |
| 11 | Contact-reply HTML-injection POC (P0-04/EB-2, SEL-15) | `src/test/java/com/example/gradproj/EduNest/security/probes/ContactReplyInjectionPocTest.java` | V-02 evidence: PUBLIC save → ADMIN reply → real `admin-reply.html` merge, captured at the `EmailService` boundary (recording subclass "sentinel inbox", no Mockito); hostile `<img src=x onerror>` / `<script>` survive verbatim, zero `&lt;` escaping — verdict CONFIRMED, fix = P1-24 — 2 tests |
| 12 | Explore keyword wildcard POC (P0-05/EB-3, MSH-12) | `src/test/java/com/example/gradproj/EduNest/security/probes/MentorshipExploreWildcardPocTest.java` | V-03 evidence: anonymous PUBLIC explore with canonical match-all payloads `{%, %%, %_, _%, __}` → full ACTIVE candidate set (keyword destroyed as selector; seeded DRAFT confined), `Summer%Off`/`Fundamentals_101` sibling pairs prove `%`/`_` semantic alteration (2 vs 1), raw `%`/`_` proven to reach `:keyword` via the `[V-03]` `param-received` echo — verdict CONFIRMED, fix = P4-10 — 2 tests |

*Rows 2–4 are the Phase-1 security suites (`8f4555c`, `af50e58`) that predate this inventory's last update — listed here so the table is actually complete.*

- Build test execution: `.\mvnw.cmd test` runs all nine classes — **56 tests, 0 failures, BUILD SUCCESS** with zero env vars (test-profile H2, per the P2-01 note above). **The Dockerfile still builds with `-DskipTests`** (TST-03; the P2-07 CI workflow runs the full suite on PRs and `main`).
- The five P2-02 suites are **pure unit tests**: no Spring context, no DB — mocked collaborators, reflection only into the pre-existing private seams (`calculateScore`, `getSessionAttendance`, `calculateAttendancePoints`), so no production code was refactored.
- Known-wrong branches are asserted **as they behave today** with the issue ID cited in each test (LEARN-04/05/07/09, GAME-03/05, LS-02, the rank quirk); Phase-4 repair tasks are expected to edit the test in the same PR as the fix (TEAM-WORKFLOW §6 contract).
- No frontend tests exist in this repo (API backend only).

## 3. Requirements the suite must eventually cover (from this documentation's risk register)

Top priorities (highest leverage first — each maps to a documented defect):

| Priority | Suite | Drive-by issue(s) |
|---|---|---|
| P0 | Room-membership authorization on STOMP subscribe/send (after architectural fix) | WS-01 |
| P0 | Quiz answer review may not expose keys before submission | LEARN-01 |
| P0 | JWT: exp validation, enabled/deleted re-check, secret env override (`LoginServiceI` role parse) | SEL-01, SEL-06 |
| P0 | Uploaded-path download ownership (`/files/**` incl. generic endpoint) | FILE-01, LEARN-03 |
| P1 | Mentorship lifecycle transition matrix + completion certificate issuance (ranks, uniqueness) | MSH-lifecycle, CERT-* |
| P1 | Enrollment concurrency (unique constraint + clean "already enrolled") | MSH-04, DB-04 |
| P1 | Grading → points delta idempotence (regrade, resubmit, badge bulk-update) | LEARN-06, GAME-03 |
| P1 | Quiz attempt lifecycle (timer start side effect, single attempt, late penalty math) | quizzes-workflow |
| P2 | OTP flows (expiry, reuse, type filtering, cross-flow interference) | SEL-05, SEL-04 |
| P2 | Notification fan-out (ghost-push on rollback; mark-read ownership) | NOTIF-03 |
| P2 | Stat/progress math (`pendingReview` bug, My-Learning page-bound KPIs, opened-quiz inflation) | LEARN-04/07 |
| P3 | Error-contract shape tests (409 catch-all, three 401 shapes) | SEL-14, api/error-responses |

## 4. Recommended strategy (baseline; not yet executed)

1. **Layered test pyramid:** thin unit tests for pure logic (commission math, attendance math, points delta) + API-level integration tests (MockMvc/Testcontainers MySQL) per domain aggregate + a handful of WebSocket tests via `@MessageMapping` support (reconnectable CONNECT with JWT).
2. **Fixtures:** seed SQL via Testcontainers per test class; use the `![CDATA[...]]`-free setup the repositories already expect; target the same folders as prod where env-dependent (`uploads/` under a temp dir via properties).
3. **Test profile:** add an `application-test.properties` (test DB, fixed secrets, disabled mail async) — nothing exists today.
4. **Coverage gates only where risk lives** (auth, points, certificates) rather than chase-a-number tooling.
5. **Regression guards first:** because many security gaps are understood-but-unfixed, write tests that fail on the *gap* once fixed (marked expected-to-fail now) to lock repairs in the stabilization phase.

## 5. Commands (as-built)

```bash
.\mvnw.cmd test          # 56 tests, zero env vars (test-profile H2, P2-01)
.\mvnw.cmd clean package -DskipTests   # current "deployment build" (CI runs the full suite per P2-07)
```

Environment note (local Windows): the Maven wrapper runs on JDK 24 while the app/intelliJ run config uses JDK 21 (`pom` targets 21). Unify on 21 for reproducible builds (see [../deployment/environment-configuration.md](../deployment/environment-configuration.md)).

## 6. Known Issues (IDs in ../ISSUES.md)

- **TST-01 (High):** no functional test coverage at all (single context check).
- **TST-02 (Medium):** no test configuration/profile; smoke test depends on external env.
- **TST-03 (Medium):** Docker build skips tests (`-DskipTests`) — green images with zero verification.
- **TST-04 (Low):** no static analysis/coverage tooling configured.
