# Phase 06 — Environments & Deployment Automation

## 1. Objective

From "a Dockerfile on one laptop" to **repeatable, gated deployments**: decision D-22 picks the hosting shape; staging exists, mirrors prod closely; CI/CD extends the P2-07 workflow to build/push/deploy with tag traceability; the container carries persistent storage; secrets never sit in git.

## 2. Scope

- **In scope:** environment matrix + provisioning (staging first), secrets management, deploy pipeline stages + gates, Docker/runtime hardening incl. uploads volume (FILE-07/DEP-03), repo hygiene + history purge (FILE-06/DEP-06 per D-19), conditional object storage (D-25), deployment verification automation, runbook draft.
- **Out of scope:** app-level readiness features (Phase 5), staging *validation* content (Phase 7 runs it), monitoring stacks (Phase 8 — but alert *receivers* provisioned cheaply here if chosen).

## 3. Entry criteria

1. **D-22 hosting decision** (the single earliest critical-path item in [`DECISIONS-REQUIRED.md`](../../DECISIONS-REQUIRED.md)).
2. P5-01 actuator endpoints exist (healthchecks depend on them); P5-04 `.env.example` exists.
3. CI (P2-07) green & protected — pipelines build on it rather than replacing it.
4. Flyway migrations stable (P3-01/07) — deploy logic sequences them.

## 4. Task index

| Task | Title | Pri | Size | Decision gate |
|---|---|---|---|---|
| [P6-01](tasks.md#p6-01) | Environment & infrastructure matrix (D-22 execution) | P0 | M | **D-22** |
| [P6-02](tasks.md#p6-02) | Staging provisioning + backup policy | P1 | M | D-22 |
| [P6-03](tasks.md#p6-03) | Secrets management implementation | P1 | M | — (chained after P1-01!) |
| [P6-04](tasks.md#p6-04) | CI/CD stages to staging with gates & tags | P1 | L | D-22 |
| [P6-05](tasks.md#p6-05) | Docker/runtime hardening + persistent volume | P1 | M | D-25 default |
| [P6-06](tasks.md#p6-06) | Repo hygiene + uploads history purge | P2 | M | D-19 |
| [P6-07](tasks.md#p6-07) | Object storage for uploads (conditional) | P2 | L | D-25 |
| [P6-08](tasks.md#p6-08) | Deployment verification & failure handling | P2 | M | — |
| [P6-09](tasks.md#p6-09) | Runbook draft & environment config matrix | P2 | S | — |

## 5. Execution order & dependencies

P6-01 (decision made in Phase 0, *executed* here) → 02 → 03/04/05 parallel → 06/08/09. P6-07 only if D-25 selects it; otherwise the D-25 deferral note lives in this phase's carry-overs.

## 6. Exit criteria

1. `git tag` + pipeline run deploys a known image to staging without human config hunting.
2. Backups exist, scheduled, and the *procedure* (not yet the drill — P8-05 drills) works.
3. Secrets live in a store; git carries none.
4. Details: [`acceptance-criteria.md`](acceptance-criteria.md).

## 7. Risks

See [`RISKS-AND-DEPENDENCIES.md` §3/§4](../../RISKS-AND-DEPENDENCIES.md#3-external-dependency-register): DNS/TLS/registry access, secret-rotation ordering (R1), staging-drift (R6).

## 8. Definition of done (phase level)

Acceptance signed; P7-01 can execute purely from this phase's artifacts.
