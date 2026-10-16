# Phase 05 — Acceptance Criteria

## 1. Entry verification

- [ ] P4 core merged; suites green.
- [ ] D-11/D-16/D-21 closed (decided or defaults PO-confirmed).

## 2. Per-task acceptance

| Task | Core criterion | Evidence (execution-time) | Verified by |
|---|---|---|---|
| P5-01 | Health/readiness endpoints live with minimal exposure | curl captures + config | DevOps |
| P5-02 | Prod logs pattern-complete with requestId; no println | log capture + CI grep job | Eng Lead |
| P5-03 | Public-route budget table documented; tripping IT green | IT run | Security Reviewer |
| P5-04 | `.env.example` authoritative; CI guard test green | file + CI run | DevOps |
| P5-05 | Root books/README claims reconciled per decisions; roadmap state | diff + PO note | PO |

## 3. Phase-level checks

- [ ] Actuator exposure reviewed *as a security surface* (signed off by Security Reviewer in P5-01 row).
- [ ] Health payload contains no internal detail in prod (no show-details).
- [ ] Logback config committed once (no dev/prod divergence beyond intended levels).
- [ ] All P1-26-contained prints unchanged behaviorally (logger placeholders only).

## 4. Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| DevOps Engineer | — | — | — |
| Engineering Lead | — | — | — |
| Security Reviewer | — | — | — |
| Product Owner | — | — | — |

## 5. Carry-overs

- P8-01/02 adopt this config as baseline (alert thresholds wired there).
- P7-06 runbook slots actuator endpoints into health checks (dependency noted there).
