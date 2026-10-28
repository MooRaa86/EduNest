# Phase 02 — Testing & CI

## 1. Objective

A test infrastructure that *proves* the Phase 1 fixes (and every later change): Testcontainers-backed integration suites, authorization/IDOR regression matrix, and a CI pipeline whose required checks **cannot skip tests** — turning the audit's `TST-01..04` + `DEP-01` into an enforced gate.

## 2. Scope

- **In scope:** test profile + Testcontainers MySQL; unit/IT suites per domain; regression matrix from `G1–G15` + error contracts; WebSocket tests; CI workflow (build/test/scan) with fail-on-skip policy.
- **Out of scope:** deployment pipeline stages (P6-04), staging E2E (P7-02), static-analysis *tuning* beyond initial config (P2-08 sets minimal baseline only).

## 3. Entry criteria

1. Phase 1 P0 tasks merged or in final review (tests written *alongside* them land here but referencing fixes define behavior).
2. [`mds/testing/MDS.md`](../../../mds/testing/MDS.md) §3 priority list adopted as the suite plan.
3. `spring-boot-starter-test` confirmed present (pom verified); Testcontainers dependency additions approved (task P2-01 names them).

## 4. Task index

| Task | Title | Pri | Size |
|---|---|---|---|
| [P2-01](tasks.md#p2-01) | Test profile + Testcontainers MySQL + fixtures | P1 | M |
| [P2-02](tasks.md#p2-02) | Unit test foundation (math/points/commission/attendance/grading) | P1 | M |
| [P2-03](tasks.md#p2-03) | Auth & authorization regression suite (G-matrix + error shapes) | P0 | L |
| [P2-04](tasks.md#p2-04) | Business-flow integration suites | P1 | L |
| [P2-05](tasks.md#p2-05) | WebSocket integration tests | P1 | M |
| [P2-06](tasks.md#p2-06) | API contract tests (pagination caps, validation, error shapes) | P2 | M |
| [P2-07](tasks.md#p2-07) | CI pipeline with no-skip enforcement | P0 | M |
| [P2-08](tasks.md#p2-08) | Static analysis + dependency scanning baseline | P2 | S |

## 5. Execution order & dependencies

- P2-01 first (suite needs a working profile); P2-03 authoring can start immediately after (it *must* include one test per Phase-1 fix — written with the fix PR, validated here).
- P2-07 lands **as soon as P2-01 exists** — don't wait for suite completeness; it gates merges for Phases 3–5.
- P2-02/04/05/06 run parallel after P2-01; P2-04 partially depends on Phase 3 constraints (rows noted in task).

## 6. Exit criteria

1. `./mvnw.cmd verify` green locally; same in CI with DB via Testcontainers (no developer MySQL needed).
2. CI required checks configured on protected `main`.
3. Every Phase-1 acceptance-corresponding test exists and passes — this is the *proof artifact* for Phase 1's sign-off.
4. Full details: [`acceptance-criteria.md`](acceptance-criteria.md).

## 7. Risks

- Testcontainers on team Windows machines (Docker Desktop requirement) — fallback: CI-only integration runs + local unit profile (documented fallback in P2-01).
- Flaky tests eroding trust — each suite declares its own `@Tag("critical")` for required-vs-advisory split (P2-03).

## 8. Definition of done (phase level)

P2-07 producing green runs on `main`; P2-03 matrix green; suites listed in [`mds/testing/MDS.md` §3](../../../mds/testing/MDS.md) exist (coverage notes recorded honestly, no invented percentages).
