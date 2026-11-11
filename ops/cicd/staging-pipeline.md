# Staging CI/CD Pipeline Contract (P6-04 / EB-75)

> Status: implemented locally on `feat/p6-04-cicd-staging`; **no real deployment has been authorized or executed yet.** Automatic staging deployment is **disabled by default** and held behind the activation gate (section 5) plus the manual prerequisites in section 4.
>
> Source of truth: D-22 (hosting = Railway + Supabase, ratified 2026-11-06 in EB-72), D-25 (uploads = Supabase Storage; no local volume), the P6-04 row in `plan/phases/phase-06-environment-and-deployment-automation/tasks.md`, and the staging runbooks from EB-73 (`ops/staging/`).

## 1. Dependency chain

```
PR to main            tests.yml (PR run)          merge gate only, no artifacts
merge to main         tests.yml (push run)        full mvnw package suite + EB-44/EB-52 IT gates
       | success
       v
docker-build.yml      workflow_run on tests       builds + pushes immutable GHCR image
       | success (commit identity + staleness verified)
       v
deploy-staging.yml    workflow_run on image       ACTIVATION GATE -> verify -> migration gate -> Railway deploy -> smoke -> evidence
       | success
       v
staging updated       evidence artifact           audit record (never committed to main)
```

- **tests.yml is unchanged** and remains the only merge gate (JDK 21, full `mvnw package`, EB-44 `Uniqueness*IT` + EB-52 Postgres gates, advisory SpotBugs).
- **`docker-build.yml` (reworked):** fires on `workflow_run` of a *successful, completed* `tests` run with `head_branch == main`. A commit whose test run failed never produces an image. Older runs are rejected (see section 3).
- **`deploy-staging.yml` (new):** first job is the **activation gate**; if automatic deployment is not activated, the run stops there with all downstream jobs skipped and nothing external is touched. When activated, it runs verify -> migration gate -> Railway deploy -> smoke -> evidence, or on manual `workflow_dispatch` with an explicitly digest-pinned image ref + commit SHA (both paths run the full gate, migration, smoke chain).
- **`deploy-production.yml` (new, inert):** dispatch-only skeleton behind the `production` GitHub Environment and a repo variable `PROD_PROMOTION_ENABLED`. It deploys nothing; the real promotion logic belongs to the separately authorized production rollout task (P7-06).

## 2. Gate invariants (G1-G8)

Shared logic lives in `scripts/cicd/deploy-gate.sh` and is unit-tested locally by `scripts/cicd/test-deploy-gate.sh` (40 cases, all passing):

| Gate | Invariant | Enforced by |
|---|---|---|
| G1 | Only successful, completed triggering runs proceed; failed/cancelled/incomplete runs never build or deploy | `gate_verify_trigger` |
| G2 | Only `main` may build/deploy; PR refs and feature branches never reach staging | `gate_verify_trigger` (head_branch check) |
| G3 | Tested commit == built commit == deployed image (workflow_run head SHA == default-branch HEAD; image-identity artifact commit == verified commit) | `gate_verify_trigger`, `gate_commit_match` |
| G4 | Every required secret/variable must be present and non-empty; missing configuration **blocks** the deployment instead of silently continuing | `gate_require_config` preflights |
| G5 | The migration target must never reference the production Supabase project ref (`ciogysvcykzxrjuskxmk`, recorded in `ops/staging/provision-staging.md` section 0 — not a secret), anywhere in the URL | `gate_guard_migration_url` (coarse) |
| G6 | Image refs must be digest-pinned (`@sha256:...`); `latest` and mutable tags are not deployable | `gate_guard_image_ref` |
| G7 | Automatic staging deployment is inert until the repository variable `STAGING_AUTO_DEPLOY_ENABLED` is exactly `true`; skipping requires no approval (checked via a non-secret repo variable in a job with no environment and no secrets) | `gate_activation` / `activation` job |
| G8 | The migration target must **positively** match the approved staging Supabase project identity (allowlist: pooler username `postgres.<staging-ref>` or direct host `db.<staging-ref>.supabase.co`); URL/credentials are parsed but never printed | `gate_staging_db_identity` |

Workflow-level protections:

- **workflow_run privilege safety:** no artifacts from *user* runs are executed. The image workflow builds from a fresh checkout of the verified commit; the deploy workflow only downloads the `image-identity` artifact produced by our own trusted image workflow and proves its commit matches the verified trigger SHA. Nothing from a fork or PR run can influence these workflows.
- **Stale-run protection:** both workflows block whenever the triggering run's `head_sha` differs from default-branch HEAD at event time — an older successful run that completes after a newer main commit was pushed can never produce an image or overwrite a newer staging deployment. Concurrency (`staging-deploy`, `cancel-in-progress: false`) additionally serializes deployments.
- **Least privilege:** image workflow = `contents: read, packages: write`; deploy workflow = `contents: read, actions: read, packages: read`; production skeleton = `contents: read, actions: read`.
- **Secret hygiene:** secrets are masked (`::add-mask::`) before any step output, never echoed, never passed through job outputs, never written to artifacts. See section 4 for scoping rules.
- **Traceability:** commit SHA -> GHCR OCI label `org.opencontainers.image.revision` -> `image-identity` artifact -> digest-pinned deploy -> deployment evidence artifact, all in one chain.

## 3. SHA chain (workflow_run semantics)

GitHub evaluates `workflow_run` against the default branch, and in that context `github.sha` is **default-branch HEAD at event time — not the commit the triggering run tested**. The pipeline therefore distinguishes explicitly:

| Identity | Source | Where enforced |
|---|---|---|
| Workflow definition revision | the workflow file as read from `main` | GitHub `workflow_run` semantics |
| Tested commit | `github.event.workflow_run.head_sha` | G1-G3 in `docker-build.yml` |
| Built commit | same value: `head_sha` (checkout ref, tags, OCI `revision` label) | G3 + `image-identity.txt` |
| Image digest | `sha256:...` resolved after push | digest-pinned deploy |
| Deployed commit | `head_sha` proven equal to `image-identity.txt` commit **and** to default-branch HEAD at deploy time | G3 + stale-run guard in `deploy-staging.yml` |

`docker-build.yml` checks out `github.event.workflow_run.head_sha` (the tested commit) — never `github.sha`. `deploy-staging.yml` checks out `head_sha` on the workflow_run path and the default branch on the dispatch path (gate scripts source).

## 4. Required GitHub configuration (manual prerequisites — PO action)

**Repository variables** (non-secret; readable by every job, so the activation gate check needs no approval):

| Name | Meaning | Source |
|---|---|---|
| `STAGING_AUTO_DEPLOY_ENABLED` | activation flag; must be set to exactly `true` by the Project Owner to enable automatic deployment. Absent/false = disabled | PO decision |
| `STAGING_DB_PROJECT_REF` | approved staging Supabase project ref (identity allowlist for G8) | Supabase staging project |
| `RAILWAY_STAGING_PROJECT_ID` | Railway staging project ID | Railway dashboard |
| `RAILWAY_STAGING_SERVICE_ID` | Railway staging service ID | Railway dashboard |
| `RAILWAY_STAGING_ENVIRONMENT_ID` | Railway staging environment ID | Railway dashboard |
| `STAGING_BASE_URL` | staging public URL | Railway domain from the runbook section 2 |

**GitHub Environment `staging` secrets** (environment-scoped — only jobs attached to the environment can read them, and every deploy path requires any environment protection the PO configures, e.g. required reviewers):

| Secret | Meaning |
|---|---|
| `RAILWAY_TOKEN` | Railway project token scoped to the staging environment only (rotatable per `ops/secrets/rotation.md`) |
| `STAGING_FLYWAY_URL` | PostgreSQL **JDBC** URL of the staging Supabase database (pooler `:6543`, `sslmode=require`) |
| `STAGING_FLYWAY_USER` | migration-capable DB role for staging |
| `STAGING_FLYWAY_PASSWORD` | password for that role |

Scoping rules enforced by the workflow design: the `verify-and-prepare` job has **no** `environment:` and reads **no** secrets; all secrets are read only in jobs attached to `environment: staging`; missing values fail closed (G4).

**Also create the `production` environment** (empty, reviewers = Project Owner) so the inert production skeleton resolves its environment; it activates nothing.

**Railway-side prerequisite (one-time):** the Railway staging service must have **Registry Credentials** configured for private GHCR pulls — a GitHub PAT with `read:packages` entered in the service Settings under Source > Registry Credentials. Private registry deployments require a Railway **Pro plan**. This credential is stored encrypted by Railway and never handled by this pipeline.

**No credential or ID exists in this repository.** Nothing is committed; automatic deploys cannot start until the activation flag is set by the PO — and every step still fails closed on missing configuration (G4).

## 5. Explicit deployment activation gate (G7)

- The `activation` job is the first job of `deploy-staging.yml`. It reads only the non-secret repository variable `STAGING_AUTO_DEPLOY_ENABLED` — no checkout, no secrets, no environment — so **skipping activation requires no approval**.
- Absent, `false`, wrong casing, or any other value → output `enabled=false`; all downstream jobs are skipped (`if: needs.activation.outputs.enabled == 'true'`). **No migration runs, no deployment runs, nothing external is touched.** The run completes as a clean skip, not a red failure.
- Setting the flag to `true` is the PO's explicit activation act; the flag itself is part of the documented authorization (recommended: also enable required reviewers on the `staging` environment).
- **Manual dispatch is exempt from the flag** but requires explicit operator inputs (full commit SHA + digest-pinned image ref) and is still gated by the `staging` environment protection and the full verification chain (G1-G6, migration gate, smoke).
- CI image building (docker-build.yml) continues independently of the activation flag.

## 6. Railway staging integration (verified CLI v4 mechanism)

`railway deploy --image` **does not exist** — in Railway CLI v4, `railway deploy` provisions *templates* (verified via `railway deploy --help` and `railway service --help` on v4, plus `docs.railway.com/cli/service` and `docs.railway.com/guides/private-container-registry`). The verified image-source update mechanism is:

```
railway service source connect --image <digest-pinned-ref> \
  --project <P> --service <S> --environment <E> --json     # set/update image source
railway redeploy --from-source --yes --json \
  --project <P> --service <S> --environment <E>            # pull + deploy the configured source
```

- `railway service source connect --image ...` sets/updates the service's Docker image source; `railway redeploy --from-source` pulls and deploys **the configured source** ("Pull and deploy the latest commit or image from the configured source, instead of redeploying the existing deployment" — CLI help). Railway does **not** redeploy on image push by itself (docs: "Pushing a new image does not redeploy your service on its own"), so the explicit `--from-source` redeploy makes the sequence deterministic.
- The image ref is **digest-pinned** (`ghcr.io/...@sha256:...`). Railway docs show tag-form image refs; digest refs are standard Docker references pulled via normal registry auth — the first authorized deploy must be observed to confirm pull behavior. If a digest ref were ever rejected, the deploy step fails and **nothing deploys** — there is no fallback to `railway up` (rebuild from source) or to an unpinned image, by design.
- Deploy service keeps its variables (runbook section 2), so application secrets are never handled by the pipeline. GHCR pull credentials are the one-time Registry Credentials setting (section 4).
- The Railway token must be a project token scoped to the staging environment; production project access must not be granted to it.
- Deployment status: observed best-effort via `railway deployment list --json` (newest deployments, IDs/statuses; CLI is not pinned to a documented JSON schema, so crash detection is best-effort). The **authoritative success signal is the HTTP smoke verification** — the workflow cannot report success before the smoke job succeeds, and a nonzero `railway` exit fails the deploy job outright.

## 7. Database migration safety contract

The existing **operator-controlled Flyway model** (EB-43) is preserved exactly; the pipeline acts as the authorized operator for staging only.

- Exact sequence (verbatim from `src/main/resources/db/migration/README.md` §3.1.1), executed on a fresh checkout of the verified commit:
  1. `flyway:info` — record current state
  2. `flyway:validate` with `-Dflyway.ignoreMigrationPatterns=*:pending` — applied history is intact
  3. `flyway:migrate` — apply pending migrations (V4 placeholder default commission rate 10.0 supplied by the profile)
  4. `flyway:validate` (strict) — all applied, none pending
  5. `flyway:info` — final state for the evidence trail
- **Migrations always run before the new image is deployed.** The deploy job `needs` the migration job; a failed or skipped migration makes the deployment job un-runnable.
- **Isolation is two-layered:** G5 (coarse) rejects any URL containing the production ref anywhere; G8 (identity) then **positively proves** the target is the approved staging Supabase project via the repository variable `STAGING_DB_PROJECT_REF` — pooler username `postgres.<staging-ref>` or direct host `db.<staging-ref>.supabase.co`. An unknown database that merely lacks the production ref is still refused. If identity cannot be proven, migration is blocked. URL/credential values are parsed but never printed; the staging identity was never tested against production or shared Supabase.
- **Never shared-Supabase-default:** staging has its own Supabase project per the isolation rules in `provision-staging.md` section 0.
- **No destructive initialization:** `clean-disabled=true` is permanent in all profiles; the pipeline never calls `flyway:clean`, never calls `flyway:repair` (not even for rollback), and never baselines automatically. EB-44 baseline/adoption rules are untouched; a fresh staging database is initialized by this same forward-only sequence from V1, per README §4.
- **Rollback limitations (documented honestly):** migrations are forward-only. Application rollback = redeploy the previous digest-pinned image via `workflow_dispatch` (Railway deployment rollbacks restore both image ref and variables per its docs); the previous image remains in GHCR keyed by its commit SHA. Database rollback is NOT automated: schema state after a failed migration must be resolved by an authorized operator following README §3.1.1/§6 (investigate, never auto-repair); data restoration uses `ops/backups/` (Tier 1/Tier 2 procedures from EB-73) if needed. This asymmetry is recorded in the deployment evidence artifact.

## 8. Staging verification

- Railway status watch (best-effort, pre-smoke) then rollout wait: poll `/actuator/health/liveness` up to 20 attempts / 30s.
- Then the EB-73 smoke script runs unchanged: `ops/staging/Invoke-StagingSmoke.ps1` (liveness UP, readiness UP, Swagger UI 200, STOMP `/ws/info` websocket advertised). A smoke failure fails the pipeline and marks the deployment unsuccessful.
- **Deployed identity:** the exact digest-pinned ref deployed is recorded; the application does not currently expose build-info at `/actuator/info` (adding it is an EB-76-adjacent change), so commit-level verification at runtime relies on the digest chain plus the deploy note.
- **Evidence:** `staging-deployment-evidence-<run-id>` artifact containing overall status, activation state, UTC timestamp, verified commit, digest-pinned image ref, tags, per-stage outcomes (migration / deploy / smoke), run URL, and the rollback note. Also echoed to the job summary. Deployment metadata is **never committed to protected main**.

## 9. Failure handling and bootstrap

| Failure | Effect |
|---|---|
| `STAGING_AUTO_DEPLOY_ENABLED` != `true` | automatic path stops at the activation gate; all downstream jobs skipped; **nothing external touched** |
| tests.yml red on main | no image, no deploy (workflow_run never fires) |
| image build fails | no image push; deploy workflow never fires |
| older run completes after newer main commit | stale-run guard blocks the build and any deploy |
| migration gate fails (identity, missing creds, or SQL failure) | deployment job un-runnable; staging stays on the previous image; evidence records FAILED migration stage |
| Railway deploy fails (source connect / redeploy nonzero exit) | status watch and smoke never run; evidence records FAILED deploy stage |
| Railway deployment crashed (best-effort detection) or smoke fails | deployment marked unsuccessful; evidence artifact still published for audit |
| missing configuration | G4 preflight blocks every affected job with an explicit message; nothing partially deploys |

Manual bootstrap recap (one-time): provision staging Railway service + Supabase staging project per `ops/staging/provision-staging.md`; configure Railway Registry Credentials for GHCR; create the GitHub Environment `staging` with section 4 secrets; set the repository variables including `STAGING_DB_PROJECT_REF`; **Project Owner explicitly authorizes activation by setting `STAGING_AUTO_DEPLOY_ENABLED=true`** (plus environment required-reviewers recommended). Until then every automatic deploy attempt is a clean no-op at the activation gate.

## 10. Production boundary (P7-06)

`deploy-production.yml` is dispatch-only, gated by the `production` GitHub Environment and the repo variable `PROD_PROMOTION_ENABLED`. With the variable unset it is a no-op that logs the boundary conditions. It contains **no** deploy logic: the production promotion workflow (migrations against prod DB under the same G5/G8-style guard, prod Railway service, prod smoke, evidence) is implemented and exercised by the separately authorized production rollout task. No production infrastructure is created or modified by EB-75.
