# Domain: Testing — Implementation Guide

Canonical audit sources: [`mds/testing/MDS.md`](../../../mds/testing/MDS.md) (zero-coverage state + prioritized suite plan §3), [`mds/api/error-responses.md`](../../../mds/api/error-responses.md) and [`mds/api/endpoints.md`](../../../mds/api/endpoints.md) as contract sources, [`mds/authentication-security/authorization-rules.md`](../../../mds/authentication-security/authorization-rules.md) G-register as probe matrix.

*(Full relative paths: `../../../mds/testing/MDS.md`, `../../../mds/api/error-responses.md`, `../../../mds/authentication-security/authorization-rules.md`.)*

## 1. Purpose & components owned

Test profile + fixtures (`P2-01`), unit table-tests for pure logic (`P2-02`), auth/authz regression matrix (`P2-03`), business-flow suites (`P2-04`), WS suites (`P2-05`), API contract suites (`P2-06`), CI workflow + no-skip enforcement (`P2-07`), static/dependency scanning baseline (`P8-07` cadence).

## 2. Review checklist (mandatory for any PR here)

- [ ] Every test name/comment carries the canonical issue ID and/or task ID it locks in.
- [ ] No silent skips of the required path (`@Disabled`, conditional assumes, `assumeTrue` on env-availability, `continue-on-error`).
- [ ] Fixtures stay shared/dry (builders from P2-01; no ad-hoc user tangles).
- [ ] A behavior change updates the suite in the same PR — never leaves it red for a follow-up.
- [ ] Red-run discipline: where a suite proves a fix, the "fails before fix" note is kept in the task row (Phase-1 sign-off evidence).

## 3. Implementation requirements (per canonical issue)

| Issue | Work | Task |
|---|---|---|
| TST-02 | Testcontainers profile + fixtures | [P2-01](../../phases/phase-02-testing-and-ci/tasks.md#p2-01) |
| TST-01 | Five payload families (unit/table, auth matrix, flows, WS, contract) | [P2-02](../../phases/phase-02-testing-and-ci/tasks.md#p2-02) / [P2-03](../../phases/phase-02-testing-and-ci/tasks.md#p2-03) / [P2-04](../../phases/phase-02-testing-and-ci/tasks.md#p2-04) / [P2-05](../../phases/phase-02-testing-and-ci/tasks.md#p2-05) / [P2-06](../../phases/phase-02-testing-and-ci/tasks.md#p2-06) |
| TST-03 | CI mandatory checks | [P2-07](../../phases/phase-02-testing-and-ci/tasks.md#p2-07) |
| TST-04 | Static + dependency scan baseline & cadence | [P2-08](../../phases/phase-02-testing-and-ci/tasks.md#p2-08) / [P8-07](../../phases/phase-08-post-launch-operations/tasks.md#p8-07) |

*(Tasks P2-03..P2-06 likewise at `../../phases/phase-02-testing-and-ci/tasks.md#p2-03` … `#p2-06`.)*

## 4. Specific implementation rules

1. **Native SQL compatibility:** window functions/`ROW_NUMBER` queries exclude H2 — MySQL 8 Testcontainers only.
2. **Flake policy:** any suite flaky twice gets quarantine task + fix (never quiet delete).
3. **Contract tests mirror docs:** circular check (docs ↔ tests) keeps endpoints.md truthful.
4. **Security probes are separate from sanity:** `@Tag("critical")` suite always runs in CI; the rest can be partitioned later (P8-02 tooling unaffected).
5. **Manual-verification tasks** (drag P6/P7 infra) get checklists with artifact links — the checklist artifact is a "test".

## 5. Definition of done (domain)

[`mds/testing/MDS.md` §3](../../../mds/testing/MDS.md) priorities 1–5 have live suites; CI enforced (P2-07) — no test can be silently skipped; suites referenced by phase sign-offs as evidence.
