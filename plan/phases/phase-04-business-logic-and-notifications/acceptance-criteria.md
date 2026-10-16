# Phase 04 — Acceptance Criteria

## 1. Entry verification

- [ ] Phases 2–3 signed off (or their P0 subset + constraints live).
- [ ] D-08..D-15 closed (decided or default-confirmed).
- [ ] P2-02/04 suites present to absorb behavior changes.

## 2. Per-task acceptance

| Task | Core criterion | Evidence (execution-time) | Verified by |
|---|---|---|---|
| P4-01 | No pushes pre-commit; guest flow graceful; broadcast fan-out per D-12 | IT run | Eng Lead |
| P4-02 | Clamp ≥0; no lost updates; regrade idempotent; freeze rule enforced | IT run + code | Eng Lead |
| P4-03 | Completion atomic+idempotent; side effects postCommit | IT run | Eng Lead |
| P4-04 | Late policy per D-08; auto-close runs; post-close edits rejected | IT run | QA |
| P4-05 | Stats fixture tests prove counted truth; paged issuance; rank context | IT run | QA |
| P4-06 | Short-run failures reported to sender; correct-room broadcasts | WS IT | WS reviewer |
| P4-07 | *(Conditional)* implemented per D-13 or recorded deferral | — | Eng Lead |
| P4-08 | Constants externalized (identical values); sweep closes sessions; tx in place | IT run | QA |
| P4-09 | Page-size clamp matrix; invalid params handled; project status filters | IT run | QA |
| P4-10 | Homepage DB-paged with recorded numbers; keyword escaped; effective-price filter | before/after log | Eng Lead |
| P4-11 | no-OSIV profile green; N+1 inventory items resolved | run log | Backend Lead |
| P4-12 | Batch writes unchanged outcomes, fewer flushes | IT run | Backend Lead |
| P4-13 | Failed-email records + bounded retry + admin listing | IT run (fake SMTP) | DevOps |
| P4-14 | Profiles behave identically on unknown-property payloads | probe run | Eng Lead |
| P4-15 | *(Conditional)* implemented per D-10 or deferral recorded | — | PO + Eng Lead |
| P4-16 | *(Conditional)* decision outcome recorded; contract suites updated if implemented | — | FE liaison |
| P4-17 | *(Conditional)* hook or docs per D-16 | — | DevOps |

## 3. Phase-level checks

- [ ] `ISSUE-MAPPING.md`: every Phase-4-owned row is Verified or Deferred-with-decision.
- [ ] Product docs (`mds/learning-content|gamification|live-sessions|certificates|notifications|mentorship-flow`) reflect changed deliverable behavior.
- [ ] No stats endpoint still reports known-inflated numbers (fixture-based false-path check).
- [ ] Performance recordings honest (some tasks record *no improvement*: that's fine — record it).

## 4. Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| Engineering Lead | — | — | — |
| Backend Developer | — | — | — |
| QA Engineer | — | — | — |
| Product Owner | — | — | — |

## 5. Carry-overs

- P4-16 envelope work, if applied, becomes the API's fastest bleeding risk — retain contract tests re-run at P7-02.
- Any deferred conditionals (P4-07/15/16/17) appear in RISKS/DECISIONS with re-open triggers.
