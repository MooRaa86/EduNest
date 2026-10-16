# Testing & Quality Assurance — Primary Domain Document

## 1. Overview

The project ships with **one smoke test**. There are no unit, integration, or end-to-end tests; no static analysis; no coverage tooling. This document records the as-is state and the stabilization-phase test plan baseline (execution is a next-phase activity; not started here).

## 2. Current Implementation (complete inventory)

| # | Test | File | What it covers |
|---|---|---|---|
| 1 | Context-load smoke test | `src/test/java/com/example/gradproj/EduNest/EduNestApplicationTests.java` (`contextLoads()`) | Spring context starts (requires DB + mail env config to succeed) |

- Build test execution: Maven Surefire discovers the single test; **the Dockerfile builds with `-DskipTests`** — tests are skipped in CI-ish flows (Docker build is the only "CI" today).
- No test doubles/profiles exist: no H2/testcontainers config (`application-test` profile absent); the smoke test hits the **real dev/prod env** — it only passes where env vars resolve.
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
.\mvnw.cmd test          # runs the single smoke test (needs DB/mail env)
.\mvnw.cmd clean package -DskipTests   # current "deployment build"
```

Environment note (local Windows): the Maven wrapper runs on JDK 24 while the app/intelliJ run config uses JDK 21 (`pom` targets 21). Unify on 21 for reproducible builds (see [../deployment/environment-configuration.md](../deployment/environment-configuration.md)).

## 6. Known Issues (IDs in ../ISSUES.md)

- **TST-01 (High):** no functional test coverage at all (single context check).
- **TST-02 (Medium):** no test configuration/profile; smoke test depends on external env.
- **TST-03 (Medium):** Docker build skips tests (`-DskipTests`) — green images with zero verification.
- **TST-04 (Low):** no static analysis/coverage tooling configured.
