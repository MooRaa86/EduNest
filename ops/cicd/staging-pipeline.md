# Staging CI/CD Pipeline Contract (P6-04 / EB-75)

> Status: implemented locally on `feat/p6-04-cicd-staging`; **no real deployment has been authorized or executed yet.** The first deploy is held behind the manual prerequisites in section 4 and an explicit Project Owner authorization.
>
> Source of truth: D-22 (hosting = Railway + Supabase, ratified 2026-11-06 in EB-72), D-25 (uploads = Supabase Storage; no local volume), the P6-04 row in `plan/phases/phase-06-environment-and-deployment-automation/tasks.md`, and the staging runbooks from EB-73 (`ops/staging/`).

## 1. Dependency chain

```
PR to main            tests.yml (PR run)        merge gate only, no artifacts
merge to main         tests.yml (push run)      full mvnw package suite + EB-44/EB-52 IT gates
       | success
       v
docker-build.yml      workflow_run on tests     builds + pushes immutable GHCR image
       | success (commit identity verified)
       v
deploy-staging.yml    workflow_run on image     verify -> migration gate -> Railway deploy -> smoke -> evidence
       | success
       v
staging updated       evidence artifact         audit record (never committed to main)
```

- **tests.yml is unchanged** and remains the only merge gate (JDK 21, full `mvnw package`, EB-44 `Uniqueness*IT` + EB-52 Postgres gates, advisory SpotBugs).
- **`docker-build.yml` (reworked):** no longer fires on raw `push`. It fires on `workflow_run` of a *successful, completed* `tests` run with `head_branch == main`. A commit whose test run failed never produces an image.
- **`deploy-staging.yml` (new):** fires on `workflow_run` of a successful image build on main, or on manual `workflow_dispatch` with an explicitly digest-pinned image ref + commit SHA (both paths run the full gate, migration, smoke chain).
- **`deploy-production.yml` (new, inert):** dispatch-only skeleton behind the `production` GitHub Environment and a repo variable `PROD_PROMOTION_ENABLED`. It deploys nothing; the real promotion logic belongs to the separately authorized production rollout task (P7-06).

## 2. Gate invariants (G1-G6)

Shared logic lives in `scripts/cicd/deploy-gate.sh` and is unit-tested locally by `scripts/cicd/test-deploy-gate.sh` (22 cases, all passing):

| Gate | Invariant | Enforced by |
|---|---|---|
| G1 | Only successful, completed triggering runs proceed; failed/cancelled/incomplete runs never build or deploy | `gate_verify_trigger` (workflow_run conclusion/status checks) |
| G2 | Only `main` may build/deploy; PR refs and feature branches never reach staging | `gate_verify_trigger` (head_branch check) |
| G3 | Tested commit == built commit == deployed image (workflow_run head SHA == default-branch HEAD; image-identity artifact commit == verified commit) | `gate_verify_trigger`, `gate_commit_match` |
| G4 | Every required secret/variable must be present and non-empty; missing configuration **blocks** the deployment instead of silently continuing | `gate_require_config` preflights in all deploy jobs |
| G5 | The migration target must be a PostgreSQL JDBC URL that does **not** reference the production Supabase project ref (`ciogysvcykzxrjuskxmk`, recorded in `ops/staging/provision-staging.md` section 0 — not a secret) | `gate_guard_migration_url` |
| G6 | Image refs must be digest-pinned (`@sha256:...`); `latest` and mutable SHA tags are not deployable | `gate_guard_image_ref` |

Workflow-level protections:

- `workflow_run` privilege safety: no artifacts from *user* runs are executed. The image workflow builds from a fresh checkout of the verified commit; the deploy workflow only downloads the `image-identity` artifact produced by our own trusted image workflow and proves its commit matches the verified trigger SHA. Nothing from a fork or PR run can influence these workflows.
- Concurrency: `staging-deploy` group with `cancel-in-progress: false` — concurrent staging deployments are serialized (queued), never cancelled mid-deploy. Image builds are grouped per commit SHA.
- Least privilege: image workflow = `contents: read, packages: write`; deploy workflow = `contents: read, actions: read, packages: read`; production skeleton = `contents: read, actions: read`. Secrets are masked (`::add-mask::`) before any step output and never echoed or printed.
- Traceability: commit SHA -> GHCR OCI label `org.opencontainers.image.revision` -> `image-identity` artifact -> digest-pinned deploy -> deployment evidence artifact, all in one chain.

## 3. Image tagging and identity

- Tags published: `ghcr.io/<owner-lowercase>/edunest-backend:<full-commit-sha>` and `...:<git-describe>` (falls back to 7-char SHA when no tag exists). **`latest` is no longer published.**
- The manifest digest is resolved with `docker buildx imagetools inspect` and recorded in the `image-identity` artifact together with the commit SHA and both tags.
- Staging deploys pin the image by digest: `ghcr.io/.../edunest-backend@sha256:<digest>`. The digest is also written to the evidence artifact, so commit, image and deployment are reconstructable from any audit artifact.

## 4. Required GitHub configuration (manual prerequisites — PO action)

Create the GitHub Environment **`staging`** and set:

| Type | Name | Meaning | Source |
|---|---|---|---|
| secret | `RAILWAY_TOKEN` | Railway API token with access to the staging project only | Railway account tokens (rotatable per `ops/secrets/rotation.md`) |
| secret | `STAGING_FLYWAY_URL` | PostgreSQL **JDBC** URL of the staging Supabase database (pooler `:6543`, `sslmode=require`) | Supabase staging project (runbook `ops/staging/provision-staging.md` section 1) |
| secret | `STAGING_FLYWAY_USER` | migration-capable DB role for staging | Supabase staging project |
| secret | `STAGING_FLYWAY_PASSWORD` | password for that role | Supabase staging project |
| variable | `RAILWAY_STAGING_PROJECT_ID` | Railway staging project ID | Railway dashboard |
| variable | `RAILWAY_STAGING_SERVICE_ID` | Railway staging service ID (container) | Railway dashboard |
| variable | `RAILWAY_STAGING_ENVIRONMENT_ID` | Railway staging environment ID | Railway dashboard |
| variable | `STAGING_BASE_URL` | staging public URL (e.g. `https://staging-backend.edunest.dpdns.org`) | Railway domain from the runbook section 2 |

Environment protection: the PO may enable required reviewers on the `staging` environment; every deploy path (including manual dispatch) then requires that approval. Also create the **`production`** environment (empty, reviewers = Project Owner) so the inert production skeleton resolves its environment; it activates nothing.

**No credential or ID exists in this repository.** Nothing is committed; no deploy can start until these are configured — the preflight jobs fail fast with "required configuration ... is missing" (G4) by design.

## 5. Railway staging integration

- Mechanism: Railway CLI v4 (`@railway/cli`) `railway deploy --image <digest-pinned-ref> --project ... --service ... --environment ...` — this deploys a pre-built GHCR image into the existing Railway staging service; the service keeps its variables (per `provision-staging.md` section 2), so application secrets are never handled by the pipeline.
- The Railway token must be scoped to the staging project; production project access must not be granted to this token.
- The deploy step runs only after the migration gate succeeded; the smoke step runs only after the deploy step succeeded.

## 6. Database migration safety contract

The existing **operator-controlled Flyway model** (EB-43) is preserved exactly; the pipeline acts as the authorized operator for staging only.

- Exact sequence (verbatim from `src/main/resources/db/migration/README.md` §3.1.1), executed on a fresh checkout of the verified commit:
  1. `flyway:info` — record current state
  2. `flyway:validate` with `-Dflyway.ignoreMigrationPatterns=*:pending` — applied history is intact
  3. `flyway:migrate` — apply pending migrations (V4 placeholder default commission rate 10.0 supplied by the profile; per §3.1.1 an explicitly approved rate would be passed for a fresh database needing a different value)
  4. `flyway:validate` (strict) — all applied, none pending
  5. `flyway:info` — final state for the evidence trail
- **Migrations always run before the new image is deployed.** The deploy job `needs` the migration job; a failed or skipped migration makes the deployment job un-runnable.
- **Never production:** G5 rejects any URL containing the production Supabase project ref; the secrets are staging-scoped by name and environment.
- **Never shared-Supabase-default:** staging has its own Supabase project per the isolation rules in `provision-staging.md` section 0.
- **No destructive initialization:** `clean-disabled=true` is permanent in all profiles; the pipeline never calls `flyway:clean`, never calls `flyway:repair` (not even for rollback), and never baselines automatically. EB-44 baseline/adoption rules (`baseline-on-migrate=false` everywhere) are untouched; a fresh staging database is initialized by this same forward-only sequence from V1, per README §4.
- **Rollback limitations (documented honestly):** migrations are forward-only. Application rollback = redeploy the previous digest-pinned image via `workflow_dispatch` (the previous image remains in GHCR keyed by its commit SHA). Database rollback is NOT automated: schema state after a failed migration must be resolved by an authorized operator following README §3.1.1/§6 (investigate, never auto-repair); data restoration uses `ops/backups/` (Tier 1/Tier 2 procedures from EB-73) if needed. This asymmetry is recorded in the deployment evidence artifact.

## 7. Staging verification

- Rollout wait: poll `/actuator/health/liveness` up to 20 attempts / 30s (Railway deploy returns before the container finishes rolling).
- Then the EB-73 smoke script runs unchanged: `ops/staging/Invoke-StagingSmoke.ps1` (liveness UP, readiness UP, Swagger UI 200, STOMP `/ws/info` websocket advertised; aggregate health as soft signal). A smoke failure fails the pipeline and marks the deployment unsuccessful.
- **Deployed identity:** the exact digest-pinned ref deployed is recorded; the application does not currently expose build-info at `/actuator/info` (adding it is an EB-76-adjacent change), so commit-level verification at runtime relies on the digest chain plus the deploy note.
- **Evidence:** `staging-deployment-evidence-<run-id>` artifact containing overall status, UTC timestamp, verified commit, digest-pinned image ref, tags, per-stage outcomes (migration / deploy / smoke), run URL, and the rollback note. Also echoed to the job summary. Deployment metadata is **never committed to protected main**.

## 8. Failure handling and bootstrap

| Failure | Effect |
|---|---|
| tests.yml red on main | no image, no deploy (workflow_run never fires) |
| image build fails | no image push; deploy workflow never fires |
| migration gate fails (target guard, missing creds, or SQL failure) | deployment job un-runnable; staging stays on the previous image; evidence records FAILED migration stage |
| Railway deploy fails | smoke never runs; evidence records FAILED deploy stage |
| smoke fails (or staging never becomes live) | deployment marked unsuccessful; evidence artifact still published for audit |
| missing configuration | G4 preflight blocks every job with an explicit message; nothing partially deploys |

Manual bootstrap recap (one-time): provision staging Railway service + Supabase staging project per `ops/staging/provision-staging.md`; create the GitHub Environment `staging` with section 4 values; Project Owner authorizes the first deploy (required-reviewer approval on the environment is the recommended authorization mechanism). Until then every deploy attempt fails fast at preflight — intentional and safe.

## 9. Production boundary (P7-06)

`deploy-production.yml` is dispatch-only, gated by the `production` GitHub Environment and the repo variable `PROD_PROMOTION_ENABLED`. With the variable unset it is a no-op that logs the boundary conditions. It contains **no** deploy logic: the production promotion workflow (migrations against prod DB under the same G5-style guard, prod Railway service, prod smoke, evidence) is implemented and exercised by the separately authorized production rollout task. No production infrastructure is created or modified by EB-75.
