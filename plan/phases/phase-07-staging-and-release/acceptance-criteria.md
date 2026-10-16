# Phase 07 — Acceptance Criteria

## 1. Entry verification

- [ ] Phase 5 + Phase 6 sign-offs complete.
- [ ] No `Open` decision on launch-path items in [`DECISIONS-REQUIRED.md`](../../DECISIONS-REQUIRED.md).
- [ ] [`ISSUE-MAPPING.md`](../../ISSUE-MAPPING.md): no open P0 rows; deferrals documented.

## 2. Per-task acceptance

| Task | Core criterion | Evidence (execution-time) | Verified by |
|---|---|---|---|
| P7-01 | Staging live; migration log clean; restore rehearsal passed w/ measured time | artifacts | DevOps |
| P7-02 | All E2E flows pass (or FE-scope-note rows documented); OTP email received in real inbox | checklist artifact | QA |
| P7-03 | G-matrix denies on staging; scanner triage done; no secret leakage | reports + triage note | Security Reviewer |
| P7-04 | Signals demonstrated (health/log/limits/email/WS/file) | capture artifacts | DevOps |
| P7-05 | Checklist fully evidenced; blockers resolved or explicitly waivable *only by decision* | signed checklist | PO + all reviewers |
| P7-06 | Runbook executed end-to-end; tag recorded; rollback triggers pre-defined | execution log | DevOps + Eng Lead |
| P7-07 | Automated verify green + manual smoke captures | artifacts | DevOps |
| P7-08 | Release note published incl. known-issues; sign-off complete | note | PO |

## 3. Phase-level checks

- [ ] Release blockers policy enforced: zero unresolved Critical/High defects on the launch path ([`ISSUE-MAPPING.md`](../../ISSUE-MAPPING.md) filter).
- [ ] Backward compatibility statement between app version and schema documented (migration boundary rules from P3).
- [ ] Production config validated: env matrix values + secrets injection audit are in the P7-06 log, not assumed.
- [ ] Frontend (if exists per A-6) validated on staging (P7-02 note); if none exists, "frontend = none, backend-only launch" recorded explicitly.

## 4. Production go/no-go checklist (verifiable exit criteria)

Each row needs an evidence link. **Any ❌ = blocked**, never waived silently.

| # | Criterion | Evidence source | Status |
|---|---|---|---|
| 1 | All Critical security blockers resolved & verified | `ISSUE-MAPPING.md` P0 rows + P2-03 suite | ☐ |
| 2 | Mandatory tests + CI checks passing on release tag | P2-07 run of release tag | ☐ |
| 3 | No unresolved release-blocking defects | ISSUE-MAPPING Deferred/Blocked review | ☐ |
| 4 | Migration + recovery rehearsed (P3-07 + P7-01 artifacts) | rehearsal logs | ☐ |
| 5 | Production secrets & env config validated | P6-03/P7-06 log rows | ☐ |
| 6 | Health/log/alert signals operational (at least app-side, P7-04) | capture artifacts | ☐ |
| 7 | Backup + rollback validated to the extendable degree (restore measured; known limits documented) | P7-01 artifact + RISKS §4 | ☐ |
| 8 | Critical E2E user workflows pass in staging | P7-02 checklist | ☐ |
| 9 | Claims parity (books/README) recorded per decisions | P5-05 artifact | ☐ |
| 10 | Product Owner + authorized technical owner sign-off recorded | P7-05 signature rows | ☐ |

## 5. Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| Product Owner | — | — | — |
| Engineering Lead (technical owner) | — | — | — |
| Security Reviewer | — | — | — |
| DevOps Engineer | — | — | — |
| QA Engineer | — | — | — |

## 6. Carry-overs

- Known-issues list (from ISSUE-MAPPING deferrals) moves into P8-06 triage intake.
- First-week stabilization observation fields (P8-04) initialized from P7-07 captures.
