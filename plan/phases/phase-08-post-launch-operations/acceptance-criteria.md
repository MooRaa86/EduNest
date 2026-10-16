# Phase 08 — Acceptance Criteria

## 1. Entry verification

- [ ] Production released (P7-08 done).
- [ ] Log/logback shape (P5-02) + rate-limit budgets (P5-03) live.

## 2. Per-task acceptance

| Task | Core criterion | Evidence (execution-time) | Verified by |
|---|---|---|---|
| P8-01 | Every signal class has rule + receiver; test-trip once | alert artifacts | Eng Lead |
| P8-02 | Centralized searchable logs + error events visible | capture artifacts | DevOps |
| P8-03 | IR plan walkthrough done (incl. SEV-1 path + comms) | walkthrough artifact | PO |
| P8-04 | Stabilization gate criteria list live; exit recorded when met (or blockers named) | gate record | Eng Lead |
| P8-05 | Restore drill executed with measured time-to-restore; corrupt-backup handling documented | drill artifacts | DevOps |
| P8-06 | Triage workflow walkthrough passed (real defect end-to-end) | artifact | Eng Lead |
| P8-07 | First weekly/monthly review cycle done w/ report | report | Backend Lead |
| P8-08 | Maintenance calendar published w/ owner roles | doc | DevOps |

## 3. Phase-level checks

- [ ] All alerts tested at least once (real trip, not theory).
- [ ] First *successful restore* documented — time measured.
- [ ] Triage produced ≥1 real audit-tracked defect record (canonical registry law respected).
- [ ] No invented duration: stabilization-gate duration is PO-set at assignment time with recorded, factual evidence criteria.

## 4. Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| DevOps Engineer | — | — | — |
| Engineering Lead | — | — | — |
| Product Owner | — | — | — |
| QA Engineer | — | — | — |

## 5. Carry-overs

- Sustained operations runs on P8-08 calendar (no new phases needed).
- Any P4 conditional deferrals in feature domains now respect the post-launch backlog process.
