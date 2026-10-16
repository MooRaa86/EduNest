# Phase 03 — Database Integrity & Schema Evolution

## 1. Objective

Replace the invisible, drift-prone `ddl-auto=update` contract with **versioned Flyway migrations**, add the three missing uniqueness constraints (enrollment/review/conversation) with data-dedupe first, and fix transactional/race holes — so that P4's business logic lands on a schema the team can reason about, rehearse, and roll back.

## 2. Scope

- **In scope:** Flyway baseline + prod `ddl-auto=validate`; `exclude show-sql` in prod (DEP-04); constraint migrations with dedupe scripts; conflict UX (409s); multi-step transaction fixes (DB-05, FILE-05); soft-delete read policy (D-18); commission seeding (D-17); OSIV assessment (D-24); migration rehearsal.
- **Out of scope:** batch/N+1 performance work (P4-11/12), product-logic fixes (P4-*), infra backups provisioning (P6-02 — but the *rehearsal* here uses the backup procedure).

## 3. Entry criteria

1. Phase 1 content-guard tasks merged (see R3: no entity churn while baseline is "fresh").
2. Phase 2 CI green (migrations verified in CI before any human runs them against data).
3. D-17 (commission value) + D-18 (soft-delete) decided or defaulted.

## 4. Task index

| Task | Title | Pri | Size |
|---|---|---|---|
| [P3-01](tasks.md#p3-01) | Flyway baseline + retire `ddl-auto=update` + hygiene | P1 | L |
| [P3-02](tasks.md#p3-02) | Uniqueness constraints with dedupe + conflict UX | P1 | L |
| [P3-03](tasks.md#p3-03) | Transactional coverage + file-op atomicity | P1 | M |
| [P3-04](tasks.md#p3-04) | Soft-delete read policy | P2 | M |
| [P3-05](tasks.md#p3-05) | Commission default seeding | P2 | S |
| [P3-06](tasks.md#p3-06) | OSIV assessment & policy note | P3 | S |
| [P3-07](tasks.md#p3-07) | Migration rehearsal (apply→verify→rollback on copy) | P1 | M |

## 5. Execution order & dependencies

1 → 2 (constraints live in the Flyway world 1 creates) → 7 (rehearsal exercises 1+2 against a copied dataset). 3 independent of 2. 4/5/6 parallel small tasks. **P3-07 is the launch-blocking rehearsal artifact for P7-01.**

## 6. Exit criteria

1. Prod config: `ddl-auto: validate`, Flyway enabled — schema changes impossible without a migration file.
2. Three uniqueness constraints live with dedupe evidence; conflict paths user-friendly (not 500s).
3. Rehearsal log proves migration + rollback procedure works against real-shaped data.
4. Details + sign-off: [`acceptance-criteria.md`](acceptance-criteria.md).

## 7. Risks

R3 (baseline drift — entities frozen while capture happens). R-data: in-flight production-like data may hide constraint violations found only by the dedupe step (mitigated: dedupe-then-constrain two-migration pattern; deterministic winner selection documented).

## 8. Definition of done (phase level)

All tasks verified; P6-02 can consume the rehearsal artifacts; [`mds/database/MDS.md`](../../../mds/database/MDS.md) + `entity-relationships.md` updated with constraint matrix.
