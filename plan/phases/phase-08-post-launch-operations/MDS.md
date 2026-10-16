# Phase 08 — Post-Launch Stabilization & Operations

## 1. Objective

Turn the released system into a *maintained* one: monitoring/alerting that watches the signals the audit cared about (auth failures, 403 storms, 5xx, DB, email, WS, files), incident response with real severities, backup drills that prove recovery (not just existence), a triage workflow for post-launch defects, and an operations calendar — closing the launch→run transition cleanly.

## 2. Scope

- **In scope:** monitoring/alerting stack, centralized logging + error tracking, incident response plan, observation period gates, backup/recovery drills, triage + hotfix workflow, dependency/vulnerability cadence, ops maintenance calendar.
- **Out of scope:** feature work (that's backlog/roadmap territory post-stabilization), performance tuning beyond alert-driven fixes.

## 3. Entry criteria

1. P7-08 complete: production released and verified.
2. P5-02 log shape + P5-03 rate-limit budgets in place (alerts consume these).

## 4. Task index

| Task | Title | Pri | Size |
|---|---|---|---|
| [P8-01](tasks.md#p8-01) | Monitoring & alerting full coverage | P1 | M |
| [P8-02](tasks.md#p8-02) | Centralized logging + error tracking | P1 | M |
| [P8-03](tasks.md#p8-03) | Incident response plan + severities | P1 | S |
| [P8-04](tasks.md#p8-04) | Observation period & stabilization gate | P2 | S |
| [P8-05](tasks.md#p8-05) | Backup verification & recovery drill | P1 | M |
| [P8-06](tasks.md#p8-06) | Post-launch triage & hotfix workflow | P1 | S |
| [P8-07](tasks.md#p8-07) | Dependency & vulnerability management cadence | P2 | S |
| [P8-08](tasks.md#p8-08) | Operational maintenance calendar | P2 | S |

## 5. Execution order & dependencies

01/02 (stack) + 03/06 (plans) parallel after entry; 05 drill after 01 (uses alerting for degraded-restore detection); 04/07/08 settle into steady cadence.

## 6. Exit criteria

Stabilization gate (P8-04) passed per evidence; runbooks + plans live and reviewed at least once; ops calendar published. Details: [`acceptance-criteria.md`](acceptance-criteria.md).

## 7. Risks

Alert fatigue (starting threshold over-tuning) — P8-01 documents the "too chatty" feedback loop explicitly; single-node ceiling (R9) shows up in monitoring capacity planning.

## 8. Definition of done (phase level)

All tasks verified; the system is *in steady operations*: someone on-call-ish (role rotation), alerts tested once, first drill done, triage running.
