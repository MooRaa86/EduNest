# Phase 02 — Tasks

Format canon: [`templates/TASK-TEMPLATE.md`](../../templates/TASK-TEMPLATE.md); `<a id>` anchors are short link targets (`tasks.md#p2-nn`).

<a id="p2-01"></a>
### P2-01 — Test profile + H2 (D-27: no Docker) + bases

**Priority:** P1 — every suite slot in [`mds/testing/MDS.md` §3](../../../mds/testing/MDS.md) waits on this.  **Size:** M
**Owner role:** QA Engineer + Backend Developer   **Reviewers:** Eng Lead
**Issues:** TST-02   **Domain:** testing
**Components:** `src/test/**` (base classes), `pom.xml` (`com.h2database:h2`, test scope), `src/test/resources/application-test.properties`

**Context (updated 2026-10-28 by D-27):** zero usable tests today: the smoke test hits the **real dev/prod env** and only passes where env vars resolve. The original plan prescribed Testcontainers MySQL with the rationale "native SQL rules out H2"; re-verification found exactly ONE special query (`TotalPointsRepository` rank, `ROW_NUMBER() OVER`) which H2 2.x supports. **Decision D-27 (user-approved): H2 in-memory, no Docker — hot tails off; team no-Docker policy.**
**Evidence:** ISSUES TST-02; `pom.xml` (starter-test present, no test DB); `application.properties` env placeholder audit (`DATABASE_URL`, `MAIL_*`, JWT secret, chain guard, upload dir).
**Expected outcome:** `@SpringBootTest` boots the full app on ANY machine with zero setup; `IntegrationTestBase` (MockMvc, full context) + `TransactionalIntegrationTestBase` (auto-rollback) shared bases; smoke test un-gated and green.
**Approach:** profile `test` (`src/test/resources/application-test.properties`): H2 `MODE=MySQL` in-memory + `create-drop` + `H2Dialect`, stubbed mail (placeholders resolved, never connects), fixed fake `JWT_SECRET`, `EDUNEST_UNSAFE_DEV=true` (dev chain for tests; EB-37 adds a `{"test","prod"}` variant later), uploads → `target/test-uploads`. Seeds-deferring fixture builders to P2-02; WS/async non-tx base to P2-05; rate-limiter toggle when P1-08 exists.
**FE / API impact:** none.  **Environment access:** none (explicitly zero — no MySQL, no Docker).  **Decisions:** D-27.
**Depends on:** —  **Blocks:** P2-02..P2-08.
**Tests:** the infra itself: full-boot smoke (context + HTTP probe + window-function probe).
**Acceptance criteria:**
- [x] `mvnw test` runs green with no local MySQL **and zero env vars** (proven 2026-10-28: 14/14, env stripped).
- [x] Bases used by ≥2 suites written in this phase (`EduNestApplicationTests` extends the full-boot base; bases exported for P2-02+).
**DoD:** workflow + README of test-suite plan updated.  **Docs:** `mds/testing/MDS.md` §2 note.
**Status:** ✅ **Verified** (2026-10-28) — merged to `main` commit `2a74075`; final-suite proof: env vars stripped, `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`. Fixture builders (P2-02) and prod-chain variant (EB-37) inherit these bases. Jira EB-35 = Done.

<a id="p2-02"></a>
### P2-02 — Unit test foundation (pure logic)

**Priority:** P1 — business-math defects (points deltas, commission, attendance math, grading) are the ones later refactors break silently.  **Size:** M
**Owner role:** QA Engineer   **Reviewers:** Backend Lead
**Issues:** TST-01 (payload 1)   **Domain:** testing / gamification / learning
**Components:** `QuizSubmissionServiceImpl` grading, `MentorShipCommissionService`, `LiveSessionServiceImp#snapshotGrid` (`:394-481`), `TotalPoints` delta computation, `MentorshipCompletionService` rank choice

**Evidence:** ISSUES LEARN-04/05/07, GAME-03, LS-02 (the exact math the audit flagged); [`mds/learning-content/quizzes-workflow.md`](../../../mds/learning-content/quizzes-workflow.md), [`mds/live-sessions/MDS.md`](../../../mds/live-sessions/MDS.md).
**Expected outcome:** each documented formula/branch has a unit test table (fixture → expected), including flagged wrong branches (*documented as they behave today*, later corrected by Phase 4 tasks with test edits — never "fixed" silently in the test).
**Approach:** pure-logic extraction only where a seam already exists (no refactor); table-driven JUnit params; explicit comments citing issue ID per known-wrong case.
**Depends on:** P2-01.  **Blocks:** P4-02/04/05/08 (tests they will update).
**Acceptance criteria:** suites exist for: grading math, commission calc, points-delta, attendance snapshot, rank selection; all respect the "current-behavior" documentation rule.
**Docs:** testing/MDS.md checklist line.

<a id="p2-03"></a>
### P2-03 — Auth & authorization regression suite (G-matrix + error shapes)

**Priority:** P0 — this suite is the *definition* of "authorization fixed"; it also encodes the boot/security matrix (SEL-01/02).  **Size:** L
**Owner role:** Security Reviewer + QA Engineer   **Reviewers:** Eng Lead
**Issues:** TST-01 (payload 2), TST-03; locks SEL-01..16, WS-01, CHAT-02/03/04, MSH-02/03/11, LEARN-01/02, QZ-06, GAME-02, TD-07
**Domain:** testing / authentication-security
**Components:** `src/test/java/**/security/**` — probes per G-register row, public-route matrix (P1-03), boot matrix (P1-02), secret rotation (P1-01), OTP isolation (P1-06), reset single-use (P1-07), rate limits (P1-08), admin exposure (P1-09), uploads policy (P1-21), error shapes (P1-05)

**Evidence:** [`mds/authentication-security/authorization-rules.md` G1–G15](../../../mds/authentication-security/authorization-rules.md) + [`mds/api/error-responses.md`](../../../mds/api/error-responses.md).
**Expected outcome:** one test class per G-row family `@Tag("critical")`; each test traces to task ID + issue ID in its name/comment; running the suite against pre-fix code fails (documented once via the "red-run" note in the task row).
**Approach:** `MockMvc` for HTTP probes; WebSocket probes deferred to P2-05; parameterized probe tables; helper `expect(status).json(…)` shape asserts shared with P2-06.
**Depends on:** P2-01; Phase-1 fixes define the expectations.  **Blocks:** Phase-1 sign-off evidence; P2-07 gating value.
**Acceptance criteria:**
- [ ] ≥1 critical test per G1–G15 row + SEL/CHAT/LEARN security set (matrix table in file).
- [ ] Error-shape contract set green.
- [ ] "Red-run" note recorded (suite run against the pre-fix baseline failed in expected places).
**Docs:** testing/MDS.md §3 matrix completion.

<a id="p2-04"></a>
### P2-04 — Business-flow integration suites

**Priority:** P1 — end-to-end business invariants for the flows the product exists for; several rows deliberately depend on Phase 3 constraints.  **Size:** L
**Owner role:** QA Engineer + Backend Developer   **Reviewers:** Eng Lead
**Issues:** TST-01 (payload 3)   **Domain:** testing / mentorship / learning / certificates
**Components:** enrollment (✅ after P3-02 constraint), grading→points→leaderboard chain, quiz lifecycle (create→edit→open→submit→grade→review), completion→certificates/badges, notifications afterCommit, chat DMs history

**Evidence:** domain docs under [`mds/mentorship-flow/`](../../../mds/mentorship-flow/MDS.md), [`mds/learning-content/`](../../../mds/learning-content/MDS.md), [`mds/gamification/`](../../../mds/gamification/MDS.md), [`mds/certificates/MDS.md`](../../../mds/certificates/MDS.md).
**Expected outcome:** happy-path + one documented failure-path per flow; notification/count assertions via DB introspection helpers.
**Approach:** suite classes per flow with seeded fixtures; assert *observable effects* (points totals, notification rows, certificate rows), not internals.
**Depends on:** P2-01; P3-02 (enrollment + conversation uniqueness rows); P4-01/02 for post-fix assertions.
**Acceptance criteria:** suites exist for the 6 flows; known-pending rows marked with dependency notes rather than skipped.

<a id="p2-05"></a>
### P2-05 — WebSocket integration tests

**Priority:** P1 — proves P1-12/13 behavior stays correct (the riskiest FE-visible security change).  **Size:** M
**Owner role:** QA Engineer   **Reviewers:** WS implementer
**Issues:** TST-01 (payload 4), locks WS-01/CHAT-02/03/04/06/08
**Domain:** testing / websocket
**Components:** `WebSocketStompClient` against local port; `JwtChannelInterceptor` auth states; room membership D-14 matrix; conversation event routing (server-derived recipient); error-frame contract

**Evidence:** [`mds/websocket/messaging-flow.md`](../../../mds/websocket/messaging-flow.md), [`connection-lifecycle.md`](../../../mds/websocket/connection-lifecycle.md).
**Expected outcome:** happy subscribe/post/receive + violation (denied, drop frame/error, no persistence) + token scenarios (valid/absent/foreign-room) + edit/delete broadcast recipients.
**Depends on:** P1-12/13 merged.  **Blocks:** —
**Acceptance criteria:** WS suite green incl. AUTH matrix; error frame shape asserted (P1-12 contract).

<a id="p2-06"></a>
### P2-06 — API contract tests

**Priority:** P2 — pagination caps (API-02/USR-04), Bean-validation 400s (SEL-07), error shapes (SEL-14), Jackson parity guard (API-05) compile the *contract* as tests.  **Size:** M
**Owner role:** QA Engineer   **Reviewers:** Eng Lead
**Issues:** TST-01 (payload 5); covers API-02, SEL-07, SEL-14, API-05 expectations
**Domain:** testing / api
**Components:** `MockMvc` parameterized suites: cap clamps (size=0/−1/10000), validation payloads, error shape property tests, Jackson strictness probe (dev vs prod property set)

**Evidence:** [`mds/api/MDS.md`](../../../mds/api/MDS.md), endpoints.md, error-responses.md.
**Expected outcome:** tests assert the contracts the API docs claim; mismatch = failing test that forces doc-or-code reconciliation.
**Depends on:** P2-01.  **Blocks:** —  **Acceptance:** suites green; endpoint docs updated where tests revealed drift.

<a id="p2-07"></a>
### P2-07 — CI pipeline with no-skip enforcement

**Priority:** P0 — `.github/` is empty (verified); without CI the merge gate is a promise, not a check (DEP-01, TST-03).  **Size:** M
**Owner role:** DevOps Engineer + Backend Developer   **Reviewers:** Eng Lead
**Issues:** DEP-01, TST-03   **Domain:** testing / deployment
**Components:** `.github/workflows/tests.yml` (build+test+scan matrix), branch protection for `main` (required checks), artifact naming; JDK 21 runner

**Evidence:** ISSUES DEP-01/TST-03; [`mds/deployment/production-readiness.md`](../../../mds/deployment/production-readiness.md); repo state (empty workflows dir).
**Expected outcome:** every PR to `main`: `mvnw package` (JDK 21; **no skipped tests**), `critical` suite always runs (not `may-fail`), test report artifacts; required checks configured on the branch; red = merge impossible. The original plan targeted `develop`; the verified implementation and approved workflow target `main`.
**Approach:** single workflow, strict mode on surefire (`-Dmaven.test.failure.ignore=false`), explicit test-coverage *reporting* (advisory) not gating (until suites mature); cached wrapper; static scan job (P2-08) advisory initially.
**FE / API impact:** none.  **Environment access:** none for tests (EB-35 made the suite self-contained; D-27). GitHub Actions runner only.
**Depends on:** P2-01.  **Blocks:** every P3+ merge gate; P6-04 (extends this workflow).
**Tests:** the pipeline itself (run on the PR adding it).
**Acceptance criteria:** green run link; red run evidence (deliberately failing branch test) documented; protection configured screenshot/check listing.
**Docs:** deployment/MDS.md + testing/MDS.md; ISSUE-MAPPING DEP-01/TST-03.
**Status:** ✅ **Verified** (2026-10-28) — fix PR merged (`0cd3464`; exec-bit fix `793e696`); green run verified via API (push→main AND PR run both success); red-run evidence documented in Jira EB-41 (exit 126 → `mvnw` mode 100644→100755, setup-java v5); **branch protection ON** (Omar, same day): red workflow now physically blocks merge. Docs synced same-commit.

<a id="p2-08"></a>
### P2-08 — Static analysis + dependency scanning baseline

**Priority:** P2 — catches null-slip-ups equivalent to audit classes of defects; advisory before Phase 4 refactor waves.  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Eng Lead
**Issues:** TST-04   **Domain:** testing
**Components:** SpotBugs or NullAway (choose minimal), OWASP dependency-check plugin (CI advisory), suppression file with reasons

**Evidence:** ISSUES TST-04; audits' code-quality families.
**Expected outcome:** scan job on CI (advisory), findings triaged into `ISSUE-MAPPING.md` rows or suppressions-with-reason; zero unknown findings.
**Depends on:** P2-07 job slot.  **Blocks:** P8-07 (cadence uses this).
**Acceptance:** first-triage report linked; suppressions carry justifications.
**Docs:** testing/MDS.md note; ISSUE-MAPPING TST-04 row.
