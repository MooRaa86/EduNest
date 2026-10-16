# Domain: Database — Implementation Guide

Canonical audit sources: [`mds/database/MDS.md`](../../../mds/database/MDS.md), [`entity-relationships.md`](../../../mds/database/entity-relationships.md), [`transactions-consistency.md`](../../../mds/database/transactions-consistency.md) — all under `mds/database/`, plus preserved performance docs (`indexing.md`, `DATABASE_OPTIMIZATION.md`, legacy `search_indexing_plan.md`, `database_indexing_recommendations.md`).

*(Paths spelled out in the table below to avoid the shorthand.)*

## 1. Purpose & components owned

43-entity schema (see entity-relationships catalog), repository conventions, Flyway migrations (new!), transaction boundaries, unique constraints, soft-delete semantics, the `ddl-auto` contract, OSIV posture.

## 2. Review checklist (mandatory for any PR here)

- [ ] Schema change ⇒ migration file in same PR; entity ↔ migration diff = zero ([P3-01](../../phases/phase-03-database-integrity/tasks.md#p3-01) convention).
- [ ] New tables/constraints have fresh-DB container test + rehearsal inclusion.
- [ ] No destructive migration without two-step pattern + rehearsal artifact (R-DATA patrol: dedupe first).
- [ ] Multi-step mutations either `@Transactional` (minimal scope) or use afterCommit hooks — never cross DB+file+email atomics naively (P3-03/P4-01 patterns).
- [ ] Migration numbering follows the reservation convention (TEAM-WORKFLOW shared-file rule).

## 3. Implementation requirements (per canonical issue)

| Issue | Work | Task |
|---|---|---|
| DB-01 + DB-02 + DEP-04 | Flyway baseline, `validate` prod, show-sql off, `script.sql` disposition | [P3-01](../../phases/phase-03-database-integrity/tasks.md#p3-01) |
| DB-04 + MSH-04 + CHAT-05 | Dedupe → unique constraints (enrollment/reviews/conversations) + 409 UX | [P3-02](../../phases/phase-03-database-integrity/tasks.md#p3-02) |
| DB-05 + FILE-05 | Transaction coverage + file replace atomicity | [P3-03](../../phases/phase-03-database-integrity/tasks.md#p3-03) |
| DB-03 (D-18) | Soft-delete read policy per surface | [P3-04](../../phases/phase-03-database-integrity/tasks.md#p3-04) |
| MSH-05 (D-17) | Commission seed migration | [P3-05](../../phases/phase-03-database-integrity/tasks.md#p3-05) |
| DB-06 (D-24) | OSIV assessment → fetch-join program ([P4-11](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-11)) | [P3-06](../../phases/phase-03-database-integrity/tasks.md#p3-06) |
| DB-07 + LS-04 | Batch writes | [P4-12](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-12) |
| DEP-08 rehearsal half | Migration rehearsal artifacts | [P3-07](../../phases/phase-03-database-integrity/tasks.md#p3-07) |

## 4. Technical constraints & anti-goals

- No `undo` migrations — rollback = restore dump + redeploy (RISKS §4).
- No dependency on OSIV going forward *after* D-24 disable (new code expected to work in no-OSIV profile from P4-11).
- Legacy performance docs preserved as-is; supersede only in `MDS.md` cross-references (never deleted).

## 5. Definition of done (domain)

Rows owned here on [`ISSUE-MAPPING.md`](../../ISSUE-MAPPING.md) Verified; fresh-container + rehearsal tests green in CI; entity-relationships doc carries the final constraint/policy matrix.
