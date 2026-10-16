# Phase 03 — Acceptance Criteria

## 1. Entry verification

- [ ] Phase 1 content guards merged (R3 freeze respected).
- [ ] CI green on `develop`.
- [ ] D-17/D-18 decided or PO-confirmed defaults.

## 2. Per-task acceptance

| Task | Core criterion | Evidence (execution-time) | Verified by |
|---|---|---|---|
| P3-01 | V1 diff vs entities = zero; prod `validate` boots green; `show-sql` off in prod | diff artifact + boot test | Eng Lead |
| P3-02 | Three unique constraints live; dedupe winners documented; conflict 409 UX | migration files + IT run | Eng Lead |
| P3-03 | No orphan state on rollback-path ITs; file replace keeps old file on failure | suite run | Backend Lead |
| P3-04 | Deleted users absent from public reads; admin unaffected | IT run | PO + Eng Lead |
| P3-05 | Fresh-DB enrollment succeeds without manual admin step | container test | DevOps |
| P3-06 | Policy note exists; no-OSIV delta list produced | doc artifact | Backend Lead |
| P3-07 | Rehearsal log complete incl. rollback restore proof | rehearsal artifact | DevOps |

## 3. Phase-level checks

- [ ] No entity change lands without a migration (spot-check `git log` of entities vs migration history).
- [ ] `application-prod.properties` has no `update`/`create`; `script.sql` disposition recorded (kept-as-reference or deleted, with rationale).
- [ ] All three new unique constraints verified *in CI* (fresh container) **and** in rehearsal copy.
- [ ] `mds/database/*` updated (MDS, entity-relationships, transactions-consistency) to reflect new reality.

## 4. Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| Engineering Lead | — | — | — |
| Backend Developer (migrations author) | — | — | — |
| DevOps Engineer | — | — | — |
| QA Engineer | — | — | — |

## 5. Carry-overs

- Batch-write optimization (DB-07/LS-04) → P4-12.
- OSIV disable → D-24 gate at P4-11 exit; until then OSIV stays on in all profiles (risk R5).
- Any dedupe conflicts hitting business-meaningful duplicates → new ISSUES rows + PO decision note.
