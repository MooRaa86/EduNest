# Phase 02 — Acceptance Criteria

## 1. Entry verification

- [ ] Phase 1 P0 tasks verified or in final review (their tests target merged behavior).
- [ ] Test suite plan accepted from [`mds/testing/MDS.md` §3](../../../mds/testing/MDS.md).
- [ ] Docker available on CI runner (first P2-01 run proves it).

## 2. Per-task acceptance

| Task | Core criterion | Evidence (execution-time) | Verified by |
|---|---|---|---|
| P2-01 | No-local-DB test run green; bases + fixtures exist | `mvnw test` log + files | QA |
| P2-02 | All fixed math branches have table tests; known-wrong branches documented | suite files | Backend Lead |
| P2-03 | G1–G15 probes + security matrix green; red-run note exists | suite run + matrix file | Security Reviewer |
| P2-04 | 6 business-flow suites green (or rows explicitly dependency-marked) | suite run | QA |
| P2-05 | WS auth matrix + error-frame contract green | suite run | WS implementer |
| P2-06 | Contract suites green; API docs reconciled | suite run | Eng Lead |
| P2-07 | CI green on `develop`; **red run** proven; branch protection on | run links + protection config | Eng Lead |
| P2-08 | Scan advisory green triaged; suppressions justified | first report | Eng Lead |

## 3. Phase-level checks

- [ ] `.github/workflows/tests.yml` contains no `skipTests`, `ignoreTestFailures`, or "continue-on-error: true" on the critical path.
- [ ] Test inventory matches [`mds/testing/MDS.md` §3](../../../mds/testing/MDS.md) priorities ≥1 (each priority item has at least a suite file).
- [ ] Phase 1 sign-off was able to cite P2-03 artifacts as evidence.
- [ ] No test asserts *invented* behavior (every test cites an issue ID/doc section in a comment).
- [ ] Flaky-failure log: any suite proven flaky twice gets a documented quarantine task (not deletion).

## 4. Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| QA Engineer | — | — | — |
| Engineering Lead | — | — | — |
| Security Reviewer | — | — | — |
| DevOps Engineer | — | — | — |

## 5. Carry-overs

- Suites depending on Phase 3/4 fixes stay dependency-marked until landing (tracked in `ISSUE-MAPPING.md` rows for the corresponding fixes).
- `TST-04` tuning (strict static analysis gating) deferred to P8-07 cadence — recorded here and there.
