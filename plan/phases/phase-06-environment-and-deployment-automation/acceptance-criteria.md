# Phase 06 — Acceptance Criteria

## 1. Entry verification

- [ ] D-22 decided (status flipped in [`DECISIONS-REQUIRED.md`](../../DECISIONS-REQUIRED.md)).
- [ ] P5-01/04 artifacts exist; P2-07 CI protected.
- [ ] P3-01/07 migration artifacts stable.

## 2. Per-task acceptance

| Task | Core criterion | Evidence (execution-time) | Verified by |
|---|---|---|---|
| P6-01 | Matrix ratified (PO approved) | doc + decision note | PO |
| P6-02 | Staging reachable w/ HTTPS; MySQL same major; backup runs scheduled | URL + script schedule + restore note | DevOps |
| P6-03 | No secrets in git/image; injection verified; rotation doc | store audit + smoke log | Security Reviewer |
| P6-04 | Tag→image→staging green pipeline; red check blocks; manual prod gate exists | run links | Eng Lead |
| P6-05 | Non-root image; volume persists across redeploy; devtools absent | inspection + capture | Security Reviewer |
| P6-06 | Uploads purged from all history; clone-refresh executed; guard live | clone check + guard run | Eng Lead |
| P6-07 | *(Conditional)* adapter live per D-25 or deferral recorded | — | Eng Lead |
| P6-08 | Automated verify step green; fail-path triggers documented rollback | fail-run log | Eng Lead |
| P6-09 | Runbook drafts complete & dry-run walked | artifact | DevOps |

## 3. Phase-level checks

- [ ] Staging mirrors prod per R6: MySQL version, schema state (validated), config matrix values reviewed.
- [ ] No secret material in git after P6-06 (registry/scan note).
- [ ] D-19/D-25 outcomes recorded; unconditional leftovers none.
- [ ] The pipeline fails closed: every deploy path tested with a red check at least once (evidence links).

## 4. Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| Product Owner | — | — | — |
| Engineering Lead | — | — | — |
| DevOps Engineer | — | — | — |
| Security Reviewer | — | — | — |

## 5. Carry-overs

- D-25 deferral (object storage) → note in P8-08 ops calendar as revisit item.
- Monitoring/alerting receivers chosen but *not configured* (P8-01/02 own that).
