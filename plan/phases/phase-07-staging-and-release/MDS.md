# Phase 07 — Staging Validation & Production Release

## 1. Objective

Prove the whole system on a production-shaped environment, then *execute* the production release as a rehearsed runbook with explicit approving owners — and verify what production actually does afterwards. The go/no-go checklist (§4 here and in [`acceptance-criteria.md`](acceptance-criteria.md)) is the launch gate: **no evidence = no deploy**.

## 2. Scope

- **In scope:** first staging deploy incl. migration + backup-restore rehearsal; E2E business, security, and observability validation on staging; readiness review; production runbook execution; post-deploy verification; release comms/sign-off.
- **Out of scope:** *ongoing* operations (Phase 8 builds on the released state).

## 3. Entry criteria

1. Phase 6 complete: staging exists, pipeline deploys, rollback automation tested, secrets injected.
2. Phase 5 complete: operable app (health/logging/limits) + claims parity.
3. All blocking decisions closed or defaulted with notes ([`DECISIONS-REQUIRED.md`](../../DECISIONS-REQUIRED.md) has no `Open` on launch-path items).

## 4. Task index

| Task | Title | Pri | Size |
|---|---|---|---|
| [P7-01](tasks.md#p7-01) | Staging deploy: migrations + backup-restore rehearsal | P1 | M |
| [P7-02](tasks.md#p7-02) | E2E regression (business flows incl. FE where applicable) | P1 | L |
| [P7-03](tasks.md#p7-03) | Security validation on staging | P1 | M |
| [P7-04](tasks.md#p7-04) | Observability validation (health/logs/limits/email/WS/files) | P2 | M |
| [P7-05](tasks.md#p7-05) | Production readiness review & go/no-go | P1 | S |
| [P7-06](tasks.md#p7-06) | Production release execution (runbook) | P1 | M |
| [P7-07](tasks.md#p7-07) | Post-deployment production verification | P1 | S |
| [P7-08](tasks.md#p7-08) | Release communication & sign-off record | P2 | S |

## 5. Execution order & dependencies

P7-01 → {02,03,04 parallel} → 05 (gate) → 06 → 07 → 08. If 02–04 surface release-blockers, loop back through owners; the gate may re-run multiple times — it is *completed* once, on final record.

## 6. Exit criteria

Live, verified production release + signed go/no-go + communicated release + post-deploy evidence. All details incl. the full verifiable checklist: [`acceptance-criteria.md`](acceptance-criteria.md).

## 7. Risks

- R7 (email deliverability), R2 (FE integration unknowns) surface here on real infrastructure — each has mitigation slots in P7-02.
- Rollback during release = restore pre-migration backup + previous tag (R-rollback limits declared in [`RISKS §4`](../../RISKS-AND-DEPENDENCIES.md#4-migration--rollback-limitations-declared-honestly)).

## 8. Definition of done (phase level)

Production URL verified w/ P7-07 evidence; go/no-go signed; Phase 8 tasks ready to start.
