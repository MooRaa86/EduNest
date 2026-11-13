# Deploy Production Runbook (P6-09 / EB-80 — DEP-07 docs half)

> **Status banner (2026-11-12):** production is **ACTIVE and confirmed live**. Railway Auto Deploy tracks the `production` branch; latest verified state: service `EduNest` healthy (1/1 replica, region `iad`), deployment `01a69575` SUCCESS, commit `bef1153` (PR #121), health endpoints UP, startup logs clean.
>
> **Branch protection is NOT yet enforced.** Verified 2026-11-12: `gh api repos/MooRaa86/EduNest/rules/branches/production` → `[]` (no rulesets, no classic protection reachable by a non-admin). The EB-113 release gate (required checks `Build + test + package (JDK 21)` + `smoke`) is implemented and proven green on release PR #123, but the **`production-release-gate` ruleset awaits owner activation**. Until the owner activates it (and this runbook's §2 verification passes), merge discipline on `production` is **procedural, not enforced** — verify §2 before relying on any claim of enforcement.
>
> Ratified sources of truth: [`../environments.md`](../environments.md) (topology, D-22), [`../backups/backup-policy.md`](../backups/backup-policy.md) + [`../backups/restore-procedure.md`](../backups/restore-procedure.md) (EB-73), [`rollback-triggers.md`](rollback-triggers.md) (EB-79), [`../secrets/`](../secrets/README.md) (EB-74), `src/main/resources/db/migration/README.md` (EB-43/44).

---

## 1. Preconditions and access requirements

| Requirement | Holder | Status 2026-11-12 |
|---|---|---|
| `production` branch merge rights (owner/admin) | Repo admin (Omar) | Confirmed (all release PRs merged by owner) |
| Railway production service + `production` environment access | Repo admin | Confirmed — 1 service `EduNest`, env `production`, URL `https://backend.edunest.dpdns.org` |
| Supabase production project (`ciogysvcykzxrjuskxmk`) dashboard access | Repo admin | Confirmed (ref is public config, not a secret) |
| Operator verification tooling (`Invoke-DeployVerification.ps1`, health endpoints) | DevOps | Available |
| pg client tools for operator migrations (`pg_dump`, Flyway via `mvnw -Pdb-migrate`) | DevOps | Available |
| Ruleset activation (owner-only, EB-113) | Project Owner | **Pending** |

The release process (§2) is executed by the PO; verification (§5) and operator migrations (§3) by the DevOps operator; secrets changes (§6) by the PO.

## 2. Release path: release PR → `production` → Railway Auto Deploy

### 2.1 Required checks on the release PR

A release PR targets `production` (head = `main` or a release branch cut from it). Two GitHub checks run by `.github/workflows/tests.yml` (`pull_request: branches: [main, production]`) and `.github/workflows/docker-smoke.yml` (`pull_request: branches: [production]`):

- **`Build + test + package (JDK 21)`** — full `mvnw package` suite + EB-44/EB-52 migration gates (exact check name verified in `tests.yml`; do not paraphrase it in branch-protection config).
- **`smoke`** — Docker build + container smoke (exact job-key name verified in `docker-smoke.yml`).

### 2.2 Enforcement status — verify before relying on it

```powershell
gh api repos/MooRaa86/EduNest/rules/branches/production     # expect non-empty once the ruleset is active
```

Verified 2026-11-12: `[]` — **no enforcement active**. The delivered ruleset (EB-113: require PR + 1 approval + both checks above, block force-push/deletion, admin bypass "always", strict up-to-date recommended) becomes binding only when the owner activates it. A merged release PR before activation must therefore include, in its body, evidence that both checks passed (workflow-run links), plus an explicit reviewer approval.

### 2.3 Authorized review and merge

### 2.3 Authorized review, merge, and the unambiguous release sequence

Railway Auto Deploy fires on the push to `production` — **observed within ~2 seconds of the merge** (deployment created moments after PR #121's merge). There is no gap to "catch up" a forgotten migration afterward. The authoritative sequence:

1. Review the release diff **and the pending Flyway migrations** it contains (`flyway:info` against production, §3.4 — read-only).
2. Determine whether any database changes are required. **Releases with no schema changes skip §3 entirely** — do not imply or perform migrations that are not needed; only the §2 checks and verification apply.
3. If database changes are required: verify the new migrations are **backward-compatible with the currently deployed app** (the app that runs *now* must keep booting `ddl-auto=validate` against the post-migration schema, because the migration will land before the new code does).
4. Complete §3.1 backups and §3.2 migration rehearsal.
5. If required and safe, run the authorized migration and strict validation (§3.4) — **before merging the release PR**.
6. **If any pending migration is NOT backward-compatible with the currently deployed app: STOP.** A plain pre-merge migration is unsafe by definition; require an expand/contract migration pair (add-and-backfill now, contract in a later release after the new code ships) or an explicit coordinated-downtime plan approved by the PO before proceeding.
7. Confirm both required checks green (§2.1) and reviewer approval; if the ruleset is not yet active (§2.2), record the check-run evidence in the PR body.
8. Merge into `production` (merge commit).
9. Track the Railway Auto Deploy (§4): deployment ID + deployed commit SHA.
10. Run deployment verification (§5) against the merged commit.

> **Never merge the production release PR and then attempt to catch up with a required database migration.** The push itself deploys; by then it is too late.

Owner reviews the diff and confirms both checks green before merging. Do **not** squash into `production` without checking that Railway commit tracking stays meaningful (each production push = one deployment).

## 3. Operator-controlled Flyway migrations (separate, before deploy — never inside it)

Production runs `spring.jpa.hibernate.ddl-auto=validate` and `spring.flyway.enabled=false` in `application-prod.properties` — **the app never migrates its own database at boot** (EB-43). Schema changes are applied by an authorized operator **before the release PR is merged** (§2.3 step 5): once the push lands, the new image deploys within seconds, so "before deploy" in practice means "before merge".

### 3.1 Backup gate first (mandatory)

Per [`../backups/backup-policy.md`](../backups/backup-policy.md): before any production migration, confirm (a) the latest **Tier 1** Supabase snapshot (dashboard → Database → Backups) is current, and (b) take a fresh **Tier 2** offline dump:

```powershell
powershell -ExecutionPolicy Bypass -File ops/backups/Invoke-Backup.ps1   # pg_dump -Fc against DATABASE_URL; never print the URL
```

Verify the dump's size assertion (> 10 KB) and note its timestamp in the ticket thread.

### 3.2 Migration rehearsal (mandatory)

```powershell
.\mvnw.cmd test -Dtest=UniquenessPostgresMigrationIT
powershell -ExecutionPolicy Bypass -File ops/rehearsals/Invoke-MigrationRehearsal.ps1
```

### 3.3 Credential load + target-identity preflight (mandatory, before any manual migration)

A wrong-target migration is the worst-case failure of the manual path; no automated guard protects it on the production manual path. The sequence is **load → verify → use**: the same session variables that are verified here are the ones the migration commands consume — there is no circular dependency and no variable replacement between verification and execution. **Fail closed** on any mismatch, and do not proceed if the environment cannot be positively established. Credentials are parsed/read but **never** printed, echoed, or logged (no passwords, tokens, or full credential-bearing URLs).

1. **Record intent** in the ticket thread: "production migration of commit X against project ref `ciogysvcykzxrjuskxmk`" — before any command runs. No recorded intent → stop.
2. **Obtain credentials** from the correct authorized environment store: the production Railway service variables / the owner's secret store — never staging values, never the staging pipeline's stores.
3. **Load all three variables** into the current session explicitly, overwriting any inherited values (a stale `$env:FLYWAY_*` from a previous session — possibly staging — must never be trusted): set `FLYWAY_URL` / `FLYWAY_USER` / `FLYWAY_PASSWORD` from the store; values are never echoed, logged, or committed.
4. **Verify identity of the loaded variables** (read-only parsing in-session; do **not** print the URL):

| Check | How | Fail condition |
|---|---|---|
| Actual `FLYWAY_URL` host identity | Parse the loaded JDBC URL, extract the host. Accepted production host forms: `*.pooler.supabase.com` (pooler) or `db.<ref>.supabase.co` (direct) | Unrecognized host, or anything that is not a Supabase host → stop |
| Expected project ref | Host-derived ref must **positively** equal the production ref `ciogysvcykzxrjuskxmk` (public config) — a simple substring match anywhere in the URL is not sufficient; derive it from the host/username form | Ref mismatch, absent, or only present in an unrelated URL parameter → stop |
| Actual `FLYWAY_USER` identity | Must be `postgres.<ref>` (pooler) or `postgres` (direct), matching the host form | User/host form mismatch → stop |
| Staging crosscheck | If host or user derives to a **non-production** ref (i.e. the approved staging `STAGING_DB_PROJECT_REF` or any unknown ref), the credentials point at the wrong environment → **stop immediately**, discard the session values, and re-derive from the owner's store | Any non-production derivation → stop |
| Uncertainty | If any value cannot be positively derived or matched (obscure URL form, ambiguous ref) | Uncertainty counts as mismatch → stop |

5. **Freeze the verified variables.** From this point the exact loaded `FLYWAY_URL`/`FLYWAY_USER`/`FLYWAY_PASSWORD` are used for every migration command; do **not** replace, re-source, or re-derive them between identity verification and execution.
6. **Recheck immediately before the mutation step** (`flyway:migrate`, step 3 of §3.4): re-derive the host + username from the loaded variables and confirm the ref still positively equals `ciogysvcykzxrjuskxmk` (same checks as step 4). If the recheck fails or anything changed → stop.

This manual preflight is **not equivalent to a fully automated identity guard**: it relies on operator discipline, has no machine-auditable freshness re-check, and is not independently auditable (the pipeline's G5/G8 gates exist only on the staging pipeline path). The recorded ticket-thread intent plus this checklist is the documented compensating control until the owner ratifies a live DB-side identity marker.

### 3.4 Operator sequence on the production database (README §3.1.1, exact)

Use the exact variables loaded and identity-verified in §3.3 — **do not replace them between verification and execution**. The commands consume the same session variables that passed the preflight:

```powershell
.\mvnw.cmd -B -Pdb-migrate flyway:info
.\mvnw.cmd -B -Pdb-migrate '-Dflyway.ignoreMigrationPatterns=*:pending' flyway:validate
# >>> §3.3 step 6: recheck host/username identity against the production ref ciogysvcykzxrjuskxmk immediately here, before the mutation <<<
.\mvnw.cmd -B -Pdb-migrate flyway:migrate
.\mvnw.cmd -B -Pdb-migrate '-Dflyway.ignoreMigrationPatterns=' flyway:validate
.\mvnw.cmd -B -Pdb-migrate flyway:info
```

Non-negotiables: forward-only; `clean-disabled=true`; **never** `flyway:clean`, never `flyway:repair` (not even for rollback), never auto-baseline (`baseline-on-migrate=false`). Baseline/adoption of an existing schema follows the EB-44 baseline procedure only; a brand-new database initializes forward from V1 (README §4).

## 4. Deployment: Railway Auto Deploy from `production`

Railway builds the Docker image from the pushed `production` commit and rolls the single replica (`Dockerfile`, Java 21, port `${PORT:8080}`). Track the actual deployment:

```powershell
# Read-only; CLI is linked to the production project/environment
railway service list
railway deployment list
```

Record in the ticket thread: **deployment ID**, **deployed commit SHA**, and start time. Railway's deploy history is the rollback target source (§6), but history entries are eventually **Removed** (singleton-deploy model) and rollback availability is retention-limited — see §6.1 before assuming any target is restorable.

## 5. Verification and post-deployment validation

```powershell
powershell -ExecutionPolicy Bypass -File ops/runbooks/Invoke-DeployVerification.ps1 `
  -BaseUrl "https://backend.edunest.dpdns.org" -ExpectedCommit "<merged-commit-sha>"
```

Gates (EB-79): liveness UP → readiness UP → aggregate health within the hang-capable timeout → Swagger UI reachable → STOMP `/ws/info` advertised → **commit match** against `/actuator/info` `git.commit.id` (build-info; EB-79). Contract: **exit 0 only when every enabled gate passed**; exit 1 with a gate report otherwise. `verify-deploy.yml` runs the automated variant on every `production` push automatically.

Post-deployment validation: spot-check Swagger UI (`/swagger-ui/index.html`), API docs (`/v3/api-docs`), and the WebSocket handshake (`/ws/info`) — all read-only. Record the verification output in the ticket thread.

## 6. Failure detection, rollback, and rollback limitations

Full verdict→action map: [`rollback-triggers.md`](rollback-triggers.md). Production headlines:

| Failure | Action |
|---|---|
| Health poll exhausts budget (boot crash) | Read failed-deployment logs → **Railway → Deployments → Rollback to the previous deployment** (semantics and retention caveats: §6.1) → re-run §5 verification with the rolled-back commit |
| Version match fails (wrong build deployed — queued-push race) | Not a rollback trigger; re-verify with the actually-deployed commit as `-ExpectedCommit`, redeploy the intended commit if genuinely wrong |
| Smoke/WS fail while health is UP | One investigation cycle; roll back only if it persists |
| **Boot crash after a migration ran** | **Database is NOT rolled back.** Forward-only rule. Paths: (a) roll forward with a `V{N+1}` script (app stays down until the fix migrates clean, or the previous image is redeployed *if* it still validates against the new schema), or (b) if data itself is damaged, the restore decision table in [`../backups/backup-policy.md`](../backups/backup-policy.md) — **full production restore = downtime window + PO sign-off** |
| Boot crash after a config/secret change | Revert the variable per [`../secrets/rotation.md`](../secrets/rotation.md) rollback lane; the app boots fail-fast on bad secrets |

**Rollback limitations after schema migration (explicit):** container rollback restores *code*, not *schema*. Rolling back to an older image is safe only while its Hibernate validation (`ddl-auto=validate`) still passes against the migrated schema — check the prior image's expected migrations before choosing it. A database migration is **never** reversed by a container rollback. Data recovery after a bad migration goes through the Tier-2 restore procedure (disposable target first, verify, then act), never through schema rollback. The next release must include a corrective forward migration regardless of which path was taken.

### 6.1 Railway rollback semantics and retention (official docs)

Per Railway's official documentation ([Deployment actions](https://docs.railway.com/deployments/deployment-actions), [Plans → Image retention policy](https://docs.railway.com/pricing/plans#image-retention-policy)):

- **Rollback and Redeploy are different actions.** *Rollback* restores the selected deployment's Docker image **and its custom variables** — but only while the rollback artifact (retained image) is still available. *Redeploy* creates a new deployment from the selected deployment's code/config; for a removed deployment outside the retention window it **rebuilds the image from the original source code with the deployment's original variables** instead of restoring a retained image.
- **Image retention is plan-dependent** (images of removed deployments are retained only for the policy window): Free/Trial **24 h**, Hobby **72 h**, Pro **120 h**, Enterprise **360 h**. Deployments older than the policy cannot be rolled back at all — the Rollback option is not shown. **[VERIFY-OWNER]** Do not assume this project's current Railway plan tier; the operator confirms the effective window in the Railway dashboard before relying on any rollback target, and prefers the most recent successful deployment as the target.
- Railway's singleton-deploy model eventually **removes** older deployments; a removed deployment is still rollable within the retention window, beyond it only Redeploy (rebuild-from-source) remains possible. Because production deploys from a git source, a Redeploy rebuild remains feasible even beyond retention — but it is a *rebuild*, not a restore, and must be verified like any new deployment.
- **Verify after either action:** confirm the actually-deployed artifact identity — source SHA (Railway deployment's recorded commit) and the `/actuator/info` `git.commit.id` — via §5 verification, and review the effective variable set. Never assume the restored configuration is what you intended; prove it.
- **Secret-rotation check before restoring old variables:** rollback/redeploy restores the *historical* variable values. If any secret was rotated since the target deployment was created (rotation history: [`../secrets/rotation.md`](../secrets/rotation.md)), restoring old values may re-introduce a revoked or superseded credential — re-set rotated values immediately after the rollback and re-verify. Do not assume old credentials remain safe to restore.
- **A database migration is NOT reversed by a container rollback** (forward-only rule; the "boot crash after a migration ran" row above).

## 7. Environment configuration matrix (EB-80 approved scope, comment 10112)

Authoritative base: [`../environments.md`](../environments.md) (D-22 ratified). This matrix adds the operational detail the runbooks depend on; anything unverified is marked **[VERIFY-OWNER]** and is never invented. Credential values are never printed anywhere.

### 7.1 Railway identification

| Item | Production | Staging (planned) |
|---|---|---|
| Project | linked Railway project (`terrific-amazement`) | **[VERIFY-OWNER]** dedicated project preferred (provision-staging §2) |
| Environment | `production` (id `034521d6-7979-47dc-859e-ac39ee849f03`) | — |
| Service | `EduNest` (id `bc38f0a7-6c4f-4df1-aad3-eb56e4693448`), source repo `MooRaa86/EduNest` branch `production`, region `iad` | source becomes digest-pinned GHCR image once pipeline activates |
| Deploy trigger | Auto Deploy on `production` push | `deploy-staging.yml` after activation flags |
| Domain | `https://backend.edunest.dpdns.org` (managed TLS) | `staging-backend.edunest.dpdns.org` — **never registered (DNS NXDOMAIN)** [VERIFY-OWNER at provisioning] |

### 7.2 Supabase separation, pooling, buckets

| Item | Production (confirmed) | Staging (planned — provision-staging §1) |
|---|---|---|
| Project ref | `ciogysvcykzxrjuskxmk` (public ref, not a secret) | separate project, ref recorded into `STAGING_DB_PROJECT_REF` [VERIFY-OWNER] |
| Connection modes | Pooler `:6543` (transaction mode — app runtime + backup scripts) vs session/direct `:5432` (`db.<ref>.supabase.co`) — `sslmode=require` both | same modes; pipeline G8 accepts pooler (`FLYWAY_USER=postgres.<ref>`) or direct host form |
| JDBC username quirk | `DATABASE_NAME` holds the **username** (maps to `spring.datasource.username`); the pooler username form is `postgres.<ref>` — repo-documented in `mds/deployment/environment-configuration.md` §112 (ref = public config, value shape documented, actual secret values stay owner-side) | fresh values, never copied from prod |
| Storage buckets | `edunest-public` (public read only) / `edunest-private` (never public) — names from environments.md; policies per ADD-03/EB-83 | same names, staging project; **[VERIFY-OWNER]** confirm policies were created on the staging project |
| Snapshots | Tier 1 daily / 7-day retention; Tier 2 offline dumps (14 kept) | Tier 1 only; recovery = rebuild, not restore |

### 7.3 Secrets: required vs optional, ownership

Ownership model (EB-74): **Railway dashboard variables** are the runtime store; Supabase dashboard holds DB/storage credentials; GitHub holds none for the production path (verify-deploy needs no secrets — health probes only). Required at runtime: `SPRING_PROFILES_ACTIVE`, `DATABASE_URL`, `DATABASE_NAME`, `DATABASE_PASSWORD`, `JWT_SECRET` (≥ 32 bytes, fail-fast), `MAIL_HOST/PORT/USERNAME/PASSWORD`. Optional/feature-flagged: Zoom (`ZOOM_ENABLED=false` until provisioned), Supabase Storage (`SUPABASE_STORAGE_ENABLED` + `SUPABASE_*`), rate limiting, live-session flags — full catalog: environments.md §4 / `.env.example` (parity pinned by `EnvContractGuardTest` in CI). Rotation: [`../secrets/rotation.md`](../secrets/rotation.md). **Never** set `EDUNEST_UNSAFE_DEV` outside local dev (SEL-02 boot gate).

### 7.4 GitHub Actions environment configuration

| Path | Secrets/variables needed | Status 2026-11-12 |
|---|---|---|
| `verify-deploy.yml` (production push) | none (public health endpoints only) | active, confirmed green |
| `deploy-staging.yml` automatic | repo variables incl. `STAGING_AUTO_DEPLOY_ENABLED=true`; Environment `staging` secrets | **0 variables / 0 secrets — inert** |
| `deploy-staging.yml` manual | additionally `STAGING_MANUAL_DEPLOY_ENABLED=true` + provenance inputs | inert |
| `deploy-production.yml` (P7-06) | GitHub Environment `production` + repo variable `PROD_PROMOTION_ENABLED`; **contains no deploy logic** (dispatch-only skeleton) | unset — no-op |

Isolation rule: the future `staging` GitHub Environment must carry a **staging-scoped** `RAILWAY_TOKEN` that cannot touch the production project; production promotion (P7-06) must not reuse it.

### 7.5 Database-provider outage checklist

1. Confirm scope (Supabase status page / dashboard) — is it a project incident, regional incident, or this project only?
2. Health reflects DB: `/actuator/health` aggregate degrades; check app logs for JDBC failures (read-only diagnostics, no restarts that mask the signal).
3. Container-level restart is **pointless** during a provider outage — do not roll back the deployment.
4. Data plane: if the project is lost/unrecoverable → recreate per backup-policy vendor-incident row (recreate project → restore newest **Tier 2** dump via [`../backups/restore-procedure.md`](../backups/restore-procedure.md) → redeploy container → update Railway DB variables → verify).
5. Storage plane: DB snapshots exclude Storage objects (documented deferred scope) — file loss is not recoverable by Tier 1/2 today; state this honestly in the incident record.
6. After recovery: full §5 verification + incident record in the ticket thread.

## 8. Owners and source artifacts

| Concern | Owner role | Artifact |
|---|---|---|
| Release PRs, merge, ruleset activation | Project Owner (Omar) | EB-113 delivery; `rules/branches/production` |
| Railway service / variables / rollback clicks | Project Owner | Railway dashboard; [`../environments.md`](../environments.md) |
| Operator migrations & verification | DevOps | `db/migration/README.md` §3.1.1; [`Invoke-DeployVerification.ps1`](Invoke-DeployVerification.ps1) |
| Backup drills | DevOps (+ PO sign-off on full restore) | [`../backups/backup-policy.md`](../backups/backup-policy.md), [`../backups/restore-procedure.md`](../backups/restore-procedure.md) |
| Secret rotation | PO | [`../secrets/rotation.md`](../secrets/rotation.md) |
| Production rollout automation (finalizer) | P7-06/EB-97 | [`../cicd/staging-pipeline.md`](../cicd/staging-pipeline.md) §10; `deploy-production.yml` |
| Staging counterpart | DevOps | [`deploy-staging.md`](deploy-staging.md) |
