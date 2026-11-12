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

1. Owner reviews the diff, confirms both checks green, merges (merge commit). Do **not** squash into `production` without checking that Railway commit tracking stays meaningful (each production push = one deployment).
2. **The push to `production` is the deployment trigger.** Railway Auto Deploy fires on it (observed: deployment created within seconds of PR #121's merge).

## 3. Operator-controlled Flyway migrations (separate, before deploy — never inside it)

Production runs `spring.jpa.hibernate.ddl-auto=validate` and `spring.flyway.enabled=false` in `application-prod.properties` — **the app never migrates its own database at boot** (EB-43). Schema changes are applied by an authorized operator **before the new image is deployed**.

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

### 3.3 Operator sequence on the production database (README §3.1.1, exact)

Set `FLYWAY_URL` / `FLYWAY_USER` / `FLYWAY_PASSWORD` for the **production** target (from the owner's secret store; values are never echoed, logged, or committed), then:

```powershell
.\mvnw.cmd -B -Pdb-migrate flyway:info
.\mvnw.cmd -B -Pdb-migrate '-Dflyway.ignoreMigrationPatterns=*:pending' flyway:validate
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

Record in the ticket thread: **deployment ID**, **deployed commit SHA**, and start time. Railway keeps deployment history — the rollback target (§6) is chosen from this list.

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
| Health poll exhausts budget (boot crash) | Read failed-deployment logs → **Railway → Deployments → Redeploy the previous deployment** (one click; Railway restores image + variables) → re-run §5 verification with the rolled-back commit |
| Version match fails (wrong build deployed — queued-push race) | Not a rollback trigger; re-verify with the actually-deployed commit as `-ExpectedCommit`, redeploy the intended commit if genuinely wrong |
| Smoke/WS fail while health is UP | One investigation cycle; roll back only if it persists |
| **Boot crash after a migration ran** | **Database is NOT rolled back.** Forward-only rule. Paths: (a) roll forward with a `V{N+1}` script (app stays down until the fix migrates clean, or the previous image is redeployed *if* it still validates against the new schema), or (b) if data itself is damaged, the restore decision table in [`../backups/backup-policy.md`](../backups/backup-policy.md) — **full production restore = downtime window + PO sign-off** |
| Boot crash after a config/secret change | Revert the variable per [`../secrets/rotation.md`](../secrets/rotation.md) rollback lane; the app boots fail-fast on bad secrets |

**Rollback limitations after schema migration (explicit):** container rollback restores *code*, not *schema*. Redeploying an older image is safe only while its Hibernate validation (`ddl-auto=validate`) still passes against the migrated schema — check the prior image's expected migrations before choosing it. Data recovery after a bad migration goes through the Tier-2 restore procedure (disposable target first, verify, then act), never through schema rollback. The next release must include a corrective forward migration regardless of which path was taken.

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
