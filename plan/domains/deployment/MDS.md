# Domain: Deployment (build, config, container, environments, release) — Implementation Guide

Canonical audit sources: [`mds/deployment/MDS.md`](../../../mds/deployment/MDS.md), [`mds/deployment/environment-configuration.md`](../../../mds/deployment/environment-configuration.md), [`mds/deployment/production-readiness.md`](../../../mds/deployment/production-readiness.md). Ground facts: `.github/` empty, Dockerfile builds with `-DskipTests`/prod/current-no-volume, `.env.example` absent.

## 1. Purpose & components owned

`Dockerfile`/compose, CI workflow + pipeline stages ([P2-07](../../phases/phase-02-testing-and-ci/tasks.md#p2-07) → [P6-04](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-04)), logging/actuator surfaces, env/secrets management, uploads storage strategy ([P6-05](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-05) / conditional [P6-07](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-07)), git hygiene ([P6-06](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-06)), runbooks + release execution ([P7-06](../../phases/phase-07-staging-and-release/tasks.md#p7-06)), ops cadence (Phase 8).

## 2. Review checklist (mandatory for any PR here)

- [ ] CI: required checks enumerate `mvnw verify` with **no** `-DskipTests` anywhere in invoked arguments (audited rule).
- [ ] Image: non-root, healthcheck uses actuator (P5-01), no devtools (A-9 verified).
- [ ] Config: any new property joins `.env.example` (guard test green) with required/optional/dev-only flag.
- [ ] Secrets: none in git/image/logs; injection via store pattern (P6-03).
- [ ] Docs: runbooks updated whenever deploy behavior changes (P6-09 owns the file; PR must touch it).

## 3. Implementation requirements (per canonical issue)

| Issue | Work | Task |
|---|---|---|
| DEP-01 | CI → staged pipeline w/ gates & tags | [P2-07](../../phases/phase-02-testing-and-ci/tasks.md#p2-07) + [P6-04](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-04) |
| DEP-02 | Actuator + logging baseline | [P5-01](../../phases/phase-05-production-readiness/tasks.md#p5-01) + [P5-02](../../phases/phase-05-production-readiness/tasks.md#p5-02) |
| DEP-03 + FILE-07 | Volume persistent + object-storage option (D-25) | [P6-05](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-05) + [P6-07](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-07) |
| DEP-04 | `ddl-auto` retirement (with DB domain) | [P3-01](../../phases/phase-03-database-integrity/tasks.md#p3-01) |
| DEP-05 | Secrets: code fail-fast → provisioning, *ordered* (R1) | [P1-01](../../phases/phase-01-security-stabilization/tasks.md#p1-01) + [P6-03](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-03) |
| DEP-06 + FILE-06 | History purge (D-19) | [P6-06](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-06) |
| DEP-07 | Env matrix + `.env.example` + runbooks | [P5-04](../../phases/phase-05-production-readiness/tasks.md#p5-04) + [P6-01](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-01) / [P6-09](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-09) |
| DEP-08 | Backup policy + drills + rehearsal | [P6-02](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-02) + [P3-07](../../phases/phase-03-database-integrity/tasks.md#p3-07) + [P8-05](../../phases/phase-08-post-launch-operations/tasks.md#p8-05) |
| DOC-01/02 + TD-06 | Claims parity & roadmap disposition (D-11/16/21) | [P5-05](../../phases/phase-05-production-readiness/tasks.md#p5-05) |

*(Full links resolvable via `ISSUE-MAPPING.md` — deployment rows column.)*

## 4. Specific implementation rules (ordered dependencies!)

1. **R1 rule:** P6-03 (real secrets) only after P1-01 merges — rotating `JWT_SECRET` before the code honors it breaks every session.
2. Migration-first deploys: pipeline runs Flyway before app swap; prod `ddl-auto=validate`.
3. Staging mirrors prod per R6 (same MySQL major, same config matrix, same upload volume shape).
4. Manual steps live in runbooks: nothing was ever "manually fixed on prod" untraced — runbooks *are* the record.
5. Failure posture: deploys fail closed ([P6-08](../../phases/phase-06-environment-and-deployment-automation/tasks.md#p6-08) verify step), rollback triggers pre-defined at execution time ([P7-06](../../phases/phase-07-staging-and-release/tasks.md#p7-06)), not planning prose.

## 5. Definition of done (domain)

Phase 6 exit + Phase 8 steady-state criteria met; runbooks current after every deploy change; `production-readiness.md` B-table items each map to a verified task in [`ISSUE-MAPPING.md`](../../ISSUE-MAPPING.md).
