# Deploy Staging Runbook (P6-09 / EB-80 — DEP-07 docs half)

> **Status banner (2026-11-12):** Staging is **NOT PROVISIONED**. This runbook documents the *provisioned-when-it-exists* procedure and the *current* fallback. Verified evidence for the not-provisioned claim (three independent probes, 2026-11-12):
> 1. Railway (linked project): exactly **one service** (`EduNest`, production) and **one environment** (`production`) — no staging project or service is accessible.
> 2. `staging-backend.edunest.dpdns.org` — **DNS NXDOMAIN** (the custom domain documented in [`../staging/provision-staging.md`](../staging/provision-staging.md) §2.3 was never registered).
> 3. GitHub repository `MooRaa86/EduNest`: **0 Actions variables, 0 Actions secrets** — none of the pipeline prerequisites (`STAGING_AUTO_DEPLOY_ENABLED`, `STAGING_MANUAL_DEPLOY_ENABLED`, `STAGING_DB_PROJECT_REF`, Railway staging IDs, `RAILWAY_TOKEN`, `STAGING_FLYWAY_*`) exist, so both `deploy-staging.yml` paths are provably inert at the activation gate.
>
> Until the owner provisions staging per [`../staging/provision-staging.md`](../staging/provision-staging.md), the only authorized way to exercise a `main` build outside production is the manual Railway "Deploy" fallback in §6, path C — which itself requires the staging service to exist first.
>
> Ratified sources of truth: [`../environments.md`](../environments.md) (D-22 topology), [`../cicd/staging-pipeline.md`](../cicd/staging-pipeline.md) (EB-75 deploy contract), [`../staging/provision-staging.md`](../staging/provision-staging.md) (EB-73 bootstrap), [`../secrets/`](../secrets/README.md) (EB-74), [`rollback-triggers.md`](rollback-triggers.md) (EB-79).

---

## 1. Preconditions and access requirements

| Requirement | Holder | Where it lives | Status 2026-11-12 |
|---|---|---|---|
| Railway staging project + service | Repo admin (Omar) | Railway dashboard | **Missing — provision per provision-staging §2** |
| Supabase staging project (DB + storage) | Repo admin | Supabase dashboard + staging Railway vars | **Existence unverified — [VERIFY-OWNER].** EB-73 closed with the *runbook* delivered; its closure record states "actual staging project creation = Omar's console action following the runbook", which is not recorded anywhere verifiable. Confirm or provision per provision-staging §1 |
| Railway Registry Credentials (GHCR `read:packages` PAT) | Repo admin | Staging service Settings → Source | **Missing; private pulls require Railway Pro** |
| GitHub Environment `staging` (secrets + required reviewers) | Repo admin | Repo Settings → Environments | **Not created** |
| GitHub repo variables (`STAGING_AUTO_DEPLOY_ENABLED`, `STAGING_MANUAL_DEPLOY_ENABLED`, `STAGING_DB_PROJECT_REF`, `RAILWAY_STAGING_*`, `STAGING_BASE_URL`) | Project Owner | Repo Settings → Secrets and variables → Actions | **Not set (0 variables)** |
| GitHub Environment secrets (`RAILWAY_TOKEN`, `STAGING_FLYWAY_URL/USER/PASSWORD`) | Project Owner | Environment `staging` | **Not set (0 secrets)** |
| `gh` CLI with repo access (trigger `workflow_dispatch`, read runs) | Operator | Local tooling | Available |
| Railway CLI ≥ 4.x | Operator | `railway` (link writes local config only) | Available |

Permissions model: the operator (Hazem, DevOps) may run verifications, smoke scripts, and workflow triggers; **all staging configuration** (Railway/Supabase/GitHub settings and the two activation flags) is a Project-Owner action, because setting `STAGING_AUTO_DEPLOY_ENABLED=true` is the explicit authorization act (staging-pipeline §5).

## 2. Isolation rules (staging Railway/Supabase separation)

Verbatim obligations (from provision-staging §0 and staging-pipeline §7):

1. Staging gets its **own Supabase project**, separate from production (`ciogysvcykzxrjuskxmk`). Never point staging at production data.
2. Staging secrets are **distinct values** in the staging Railway store; never reuse production secret values (incl. a **fresh `JWT_SECRET`** from [`../secrets/new-jwt-secret.ps1`](../secrets/new-jwt-secret.ps1) — staging tokens must not verify against prod's key).
3. `EDUNEST_UNSAFE_DEV` must **never exist** in staging; staging runs `SPRING_PROFILES_ACTIVE=prod` (the hardened chain) against staging infrastructure.
4. Pipeline-level isolation (EB-75 gates): **G5** rejects any migration URL containing the production ref anywhere; **G8** positively proves the target matches `STAGING_DB_PROJECT_REF` via the actual `FLYWAY_USER` + URL host form (pooler: `postgres.<staging-ref>` + `*.pooler.supabase.com`; direct: `postgres` + `db.<staging-ref>.supabase.co`).
5. The Railway token used by the pipeline is scoped to the **staging** project only; it must not grant production access.

## 3. Environment and secret verification (no values printed)

Run these before every staging deploy. All commands fail closed; none print credential values.

```powershell
# GitHub side: variables/secrets must be present (names only are listed by these calls)
gh api repos/MooRaa86/EduNest/actions/variables
gh api repos/MooRaa86/EduNest/environments/staging --jq '{name, protection_preview}'

# Railway side (read-only): confirm the staging service exists and is linked to the expected project
railway project list
railway service list

# App side: confirm the staging URL answers and which commit is live
Invoke-RestMethod "https://<STAGING_BASE_URL>/actuator/info" | ConvertTo-Json -Depth 5
```

Expected state once provisioning is complete: the four repo variables exist with the staging Railway/Supabase IDs, the `staging` environment carries the three secrets, and `/actuator/info` exposes `git.commit.id` (build-info implemented by EB-79). **Any missing value blocks deployment (G4) by design — do not "fix" it by copying production values.**

Unknowns explicitly requiring owner verification (never invented here): staging Supabase project existence + ref, staging Railway project/service/environment IDs, staging SMTP identity choice (dedicated Gmail vs shared sender), Railway Pro plan availability for private GHCR pulls.

## 4. Backup prerequisites

Per [`../backups/backup-policy.md`](../backups/backup-policy.md): staging DB is **Tier 1 only** (Supabase native snapshots) and is **disposable by design** — the recovery path for staging is *rebuild* via provision-staging §1–5, **not** restore. Therefore:

- No operator backup gate is required before a staging migration (production-grade dump discipline does not apply to staging).
- Still confirm the staging Supabase project exists with daily snapshots enabled, so a failed rehearsal leaves a recovery point.
- Never run the Tier-2 production dump tooling (`Invoke-Backup.ps1`) against staging with a production `DATABASE_URL` — the script keys off the connection string and would produce misleading evidence; a staging dump, if ever wanted for a drill, is a separate decision.

## 5. Flyway migration preparation (operator-controlled, staging target only)

The app runs `spring.flyway.enabled=false` + `ddl-auto=validate` in every profile — **migrations are always run by an authorized operator before the image deploys** (EB-43 model; preserved by EB-75 §7). The staging DB must already have the `[ADD-02]` schema (provision-staging §1.3) before first boot.

### 5.0 Credential load + target-identity preflight (mandatory, before any manual migration)

A wrong-target migration is the worst-case failure of the manual path; the pipeline's G5/G8 gates do not protect it. The sequence is **load → verify → use**: the same session variables that are verified here are the ones the migration commands consume — there is no circular dependency and no variable replacement between verification and execution. **Fail closed** on any mismatch, and do not proceed if the environment cannot be positively established. Credentials are parsed/read but **never** printed, echoed, or logged (no passwords, tokens, or full credential-bearing URLs).

1. **Record intent** in the ticket thread: "staging migration of commit X against `STAGING_DB_PROJECT_REF=<ref>`" — before any command runs. No recorded intent → stop.
2. **Obtain credentials** from the correct authorized environment store: the staging Railway service variables / the owner's secret store — never production values, never the pipeline's production-adjacent stores.
3. **Load all three variables** into the current session explicitly, overwriting any inherited values (a stale `$env:FLYWAY_*` from a previous session — possibly production — must never be trusted):
   ```powershell
   $env:FLYWAY_URL    = "<staging JDBC url — pooler :6543, sslmode=require>"   # set by owner, kept in Railway/secret store
   $env:FLYWAY_USER   = "<staging migration role, e.g. postgres.<staging-ref>>"
   $env:FLYWAY_PASSWORD = "<staging role password>"
   ```
4. **Verify identity of the loaded variables** (read-only parsing in-session; do **not** print the URL):

| Check | How | Fail condition |
|---|---|---|
| Actual `FLYWAY_URL` host identity | Parse the loaded JDBC URL and extract the host. Host must be `*.pooler.supabase.com` or `db.<ref>.supabase.co` | Unrecognized host, or a URL that looks like anything but Supabase → stop |
| Expected project ref | Host-derived ref (pooler username `postgres.<ref>` suffix / `db.<ref>` host component) must **positively** match `STAGING_DB_PROJECT_REF` — the owner-approved allowlist value, not a guess | Ref mismatch, unknown, or absent `STAGING_DB_PROJECT_REF` → stop |
| Actual `FLYWAY_USER` identity | Must be `postgres.<staging-ref>` (pooler) or `postgres` (direct) — matching the host form | User/host form mismatch → stop |
| Production-crosscheck | If host or user derives to the **production** ref (`ciogysvcykzxrjuskxmk`, public config — also guard-listed in staging-pipeline G5), the credentials point at production → **stop immediately**, discard the session values, and re-derive from the owner's store | Any production-derivation → stop |
| Uncertainty | If any value cannot be positively derived or matched (obscure URL form, ambiguous ref) | Uncertainty counts as mismatch → stop |

5. **Freeze the verified variables.** From this point the exact loaded `FLYWAY_URL`/`FLYWAY_USER`/`FLYWAY_PASSWORD` are used for every migration command; do **not** replace, re-source, or re-derive them between identity verification and execution.
6. **Recheck immediately before the mutation step** (`flyway:migrate`, step 3 of §5.2): re-derive the host + username from the loaded variables and confirm they still positively match `STAGING_DB_PROJECT_REF` (same checks as step 4). If the recheck fails or anything changed → stop.

This manual preflight is **not equivalent to the automated identity guard** (G8): it depends on operator discipline and the same session's variables, has no machine-auditable freshness re-check, and is not independently auditable. Until a live DB-side marker is ratified (staging-pipeline §7 limitation), the manual path remains this check plus the recorded ticket-thread intent.

### 5.1 Rehearsal gate (mandatory, before touching any real target)

```powershell
.\mvnw.cmd test -Dtest=UniquenessPostgresMigrationIT   # disposable-PostgreSQL migration rehearsal (EB-44)
powershell -ExecutionPolicy Bypass -File ops/rehearsals/Invoke-MigrationRehearsal.ps1
```

### 5.2 Operator sequence on staging (exact commands from `src/main/resources/db/migration/README.md` §3.1.1)

Use the exact variables loaded and identity-verified in §5.0 — **do not replace them between verification and execution**. The commands consume the same session variables that passed the preflight:

```powershell
.\mvnw.cmd -B -Pdb-migrate flyway:info
.\mvnw.cmd -B -Pdb-migrate '-Dflyway.ignoreMigrationPatterns=*:pending' flyway:validate
# >>> §5.0 step 6: recheck host/username identity against STAGING_DB_PROJECT_REF immediately here, before the mutation <<<
.\mvnw.cmd -B -Pdb-migrate flyway:migrate
.\mvnw.cmd -B -Pdb-migrate '-Dflyway.ignoreMigrationPatterns=' flyway:validate
.\mvnw.cmd -B -Pdb-migrate flyway:info
```

Non-negotiables: `clean-disabled=true` is permanent; **never** `flyway:clean`, never `flyway:repair` (not even for rollback), never auto-baseline (`baseline-on-migrate=false`; a *fresh* database is initialized forward from V1 per README §4 — any adoption/baseline of an existing schema follows the EB-44 baseline procedure only). Migrations run **before** the new image deploys; in the pipeline path the deploy job `needs` the migration job, so a failed/skipped migration makes deployment un-runnable.

**Known documented limitation (staging-pipeline §7):** no live database-identity marker exists in the DB today; the identity gate is credential+host based (G5 coarse + G8 positive match). Ratifying a staging marker (e.g., marker row or dedicated database name) is a PO decision recorded for a future iteration.

## 6. Deployment procedures and activation gates

Three paths exist. **All automation is currently inert** (0 variables/0 secrets — see status banner); path C is the only live mechanism today and requires the staging service to exist.

### Path A — automatic pipeline (default once activated)

Triggered by `deploy-staging.yml` `workflow_run` on a successful `tests` run on `main` (checkout of `tests.yml` confirms `workflow_run` types `[completed]`, branches `[main]`):

1. `STAGING_AUTO_DEPLOY_ENABLED` must be exactly `true` (activation job; anything else = clean skip, nothing touched — G7).
2. Image build: `docker-build.yml` (workflow_run on tests) pushes a **digest-pinned** `ghcr.io/<owner>/edunest-backend@sha256:<64 hex>` image whose OCI `revision` label is the tested commit.
3. Chain: verify → migration gate (G5/G8 + freshness re-check) → `railway service source connect --image <digest-pinned-ref>` + `railway redeploy --from-source` → tracked-deployment polling → smoke → evidence artifact. Full contract and failure table: staging-pipeline §2, §6, §9.
4. Stale-run protection: a queued older run aborts at its own mutation checkpoint; newer `main` HEAD always wins.

### Path B — manual dispatch

`workflow_dispatch` of `deploy-staging.yml` with an explicit commit SHA + digest-pinned image ref. Requires `STAGING_MANUAL_DEPLOY_ENABLED=true` **and** the full provenance chain (40-hex SHA, approved GHCR prefix, commit in main history, approved-pipeline-run existence, OCI revision-label equality) and the `staging` environment protection. Enabling the manual flag never bypasses integrity gates (G7m).

### Path C — current fallback: manual Railway "Deploy" from `main` (Omar-approved, 2026-11-11)

Until the pipeline is activated (and only while the staging service exists). **Migration always precedes deployment on this path** — the app boots with `spring.flyway.enabled=false` (it never migrates its own database) and `spring.jpa.hibernate.ddl-auto=validate`. Deploying a release whose code expects not-yet-applied schema changes fails at boot (Hibernate validation rejects the mismatched schema); a release that happens to be schema-compatible with the current schema may boot without migrating, but the §5.2 sequence is still mandatory on this path either way, so schema state and deployed code never drift:

1. Verify prerequisites (§1), confirm the target identity per §5.0 preflight (staging — never production), and confirm the backup posture (§4).
2. Run the migration rehearsal gate (§5.1).
3. Run the authorized Flyway sequence (§5.2) against the verified staging database **if** `flyway:info` shows pending migrations; confirm the strict final `flyway:validate` passes (step 4 of the sequence).
4. Railway dashboard → staging service → Deploy → the intended `main` commit (confirm SHA).
5. Track the deployment and run §7 verification (smoke + commit identity).

This path has no digest pinning, no tracked-deployment gate, and no evidence artifact — it is the interim mechanism, replaced by paths A/B once the owner activates them.

**Rollback policy (all paths):** automatic deployments only ever advance to the newest verified commit; rollback is a **manual** redeploy of the previous digest-pinned image (paths A/B) or a previous Railway deployment (path C), superseded by any later automatic deployment — documented policy, never an accident. Railway action semantics and retention caveats: see §6.1.

### 6.1 Railway rollback semantics and retention (official docs)

Per Railway's official documentation ([Deployment actions](https://docs.railway.com/deployments/deployment-actions), [Plans → Image retention policy](https://docs.railway.com/pricing/plans#image-retention-policy)):

- **Rollback and Redeploy are different actions.** *Rollback* restores the selected deployment's Docker image **and its custom variables** — but only while the rollback artifact (retained image) is still available. *Redeploy* creates a new deployment from the selected deployment's code/config; for a removed deployment outside the retention window it **rebuilds the image from the original source code with the deployment's original variables** instead of restoring a retained image.
- **Image retention is plan-dependent** (Railway retains images of removed deployments only for the policy window): Free/Trial **24 h**, Hobby **72 h**, Pro **120 h**, Enterprise **360 h**. Deployments older than the policy cannot be rolled back at all — the Rollback option is not even shown. **[VERIFY-OWNER]** Do not assume this project's current Railway plan tier; the operator confirms the effective window in the dashboard before relying on any rollback.
- Railway's singleton-deploy model eventually **removes** older deployments; a removed deployment is still rollable within the retention window, beyond it only Redeploy is possible.
- **Verify after either action:** confirm the actually-deployed artifact identity — for paths A/B the digest-pinned image ref and `/actuator/info` `git.commit.id`; for source deploys the source SHA and the effective variable set — via §7 verification. Never assume the restored configuration is what you intended; prove it.
- **Secret-rotation check before restoring old variables:** rollback/redeploy restores the *historical* variable values. If any secret was rotated since the target deployment was created (rotation history: [`../secrets/rotation.md`](../secrets/rotation.md)), restoring old values may re-introduce a revoked or superseded credential — re-set rotated values immediately after the rollback and re-verify. Do not assume old credentials remain safe to restore.
- **A database migration is NOT reversed by a container rollback** (forward-only rule; §8 / `rollback-triggers.md`).
- Retention-immune alternative for paths A/B: manual `workflow_dispatch` of the previous **digest-pinned GHCR image** (the image persists in GHCR keyed by commit SHA, independent of Railway's retention window).

## 7. Smoke and health verification

```powershell
# EB-73 staging smoke (liveness UP, readiness UP, Swagger 200, STOMP /ws/info advertised)
powershell -ExecutionPolicy Bypass -File ops/staging/Invoke-StagingSmoke.ps1 -BaseUrl "https://<STAGING_BASE_URL>"

# EB-79 full verification, including deployed-commit identity
powershell -ExecutionPolicy Bypass -File ops/runbooks/Invoke-DeployVerification.ps1 `
  -BaseUrl "https://<STAGING_BASE_URL>" -ExpectedCommit "<tested-commit-sha>"
```

Rules (EB-75 §6 + EB-79): success is **never** inferred from existing health (health may be served by the previous deployment) — the tracked deployment must reach a terminal successful state first; a `/actuator/info` `git.commit.id` differing from the verified commit fails the run (absent value on a pre-EB-79 rollback image is recorded as "identity not exposed", not a hard fail on rollback). Every verification result goes in the ticket thread.

## 8. Failure detection and rollback

The verdict→action map lives in [`rollback-triggers.md`](rollback-triggers.md) (EB-79) and applies to staging unchanged. Headlines:

| Failure | Action |
|---|---|
| Health poll exhausts budget | Read failed-deployment logs; roll back to the previous deployment/image (Railway action semantics + retention caveats: §6.1); re-verify after rollback |
| Boot crash **after a migration ran** | DB is **NOT rolled back** (forward-only): roll forward with `V{N+1}`, or if data is damaged, follow the restore decision table in [`../backups/backup-policy.md`](../backups/backup-policy.md) — for staging, prefer **rebuild** (§ teardown) |
| Boot crash after a config/secret change | Revert the variable via the rotation lane (`../secrets/rotation.md`) |
| Pipeline gate failure (G1–G8) | Nothing external was touched; fix the configuration/identity problem and re-run |
| Deployment UNVERIFIED (id not captured / no terminal success) | Treat as failed; smoke never runs; evidence artifact still published |

Non-negotiables: one investigation cycle before container rollback when health is UP but smoke fails; verification runs after every rollback too; every rollback event gets a ticket-thread record.

## 9. Walkthrough record — SIMULATED (desk validation only, 2026-11-12)

**Authorization:** owner-approved desk walkthrough (2026-11-12). **This simulation does NOT satisfy a real staging deployment test and does not claim to.** The P6-09 acceptance item "walk-through dry-run on staging (recorded)" is recorded here as **simulated/deferred** until staging exists; the real drill belongs to P7-06/EB-97.

| Runbook step | Classification | Evidence basis |
|---|---|---|
| Status banner claims (not provisioned) | **Verified by evidence** | Railway CLI: 1 service/1 env; DNS NXDOMAIN for staging domain; `gh api .../actions/variables` and `/actions/secrets` → total_count 0 (2026-11-12). Supabase staging-project *existence* is additionally classified unverified: EB-73's closure comment assigns actual project creation to the owner's console action, not recorded — hence the [VERIFY-OWNER] mark, not a "missing" claim |
| §1 preconditions table | **Verified as gaps** | Same probes; `provision-staging.md` §4 ownership roles |
| §2 isolation rules | **Verified as text of ratified docs** | provision-staging §0; staging-pipeline G5/G8; no live Supabase access attempted |
| §3 verification commands | **Simulated syntax only** | `gh api` forms returned real data for the repo-level calls (0/0 result proves the flags exist); environment-scoped call untested (no `staging` environment exists); Railway CLI calls exercised read-only against the production-linked project |
| §4 backup prerequisites | **Verified as policy text** | backup-policy scope table (staging = Tier 1, rebuild-not-restore) |
| §5.1 rehearsal gate | **Verified by test suite** | `UniquenessPostgresMigrationIT` + rehearsal script are merged artifacts (P3-07/EB-44); not re-run in this session |
| §5.2 Flyway sequence | **Simulated (no DB touched)** | Commands copied verbatim from `db/migration/README.md` §3.1.1; `-Pdb-migrate` profile shape verified in `pom.xml` (`FLYWAY_URL/USER/PASSWORD` env wiring); **no command executed against any database** |
| §6 paths A/B activation gates | **Verified inert-by-config** | 0 variables/0 secrets ⇒ G7/G7m skip; `deploy-staging.yml` triggers confirmed (`workflow_run` on tests/`[main]`, `workflow_dispatch`) |
| §6 path C | **Not executable** | Requires the staging service, which does not exist |
| §7 smoke/verify commands | **Simulated against prod-reality only** | Script parameters verified from source (`-BaseUrl`, `-ExpectedCommit`); production health endpoints independently confirmed UP the same day |
| §8 rollback table | **Verified as text** | rollback-triggers.md; no rollback executed |

**Impossible without staging:** staging smoke, staging migration, staging deploy (all paths), staging rollback, tracked-deployment polling, evidence-artifact flow, G8 identity check against a real staging DB.

**Pending owner actions (in order):** provision Supabase staging project + Railway staging service (provision-staging §1–2); GHCR Registry Credentials (Pro plan); create GitHub Environment `staging` with required reviewers + section-1 secrets; set section-1 repo variables; then explicitly set `STAGING_AUTO_DEPLOY_ENABLED=true` and/or `STAGING_MANUAL_DEPLOY_ENABLED=true`.

**Risks requiring future validation:** first digest-pinned pull behavior on Railway (documented fail-closed); `redeploy`/`deployment list` JSON field shapes unpinned by docs (fail-closed on mismatch); pooler-vs-direct host form of the real staging ref; staging mail identity deliverability.

## 10. Owners and source artifacts

| Concern | Owner role | Artifact (single source of truth) |
|---|---|---|
| Topology & variable catalog | Tech Lead/PO (ratified D-22) | [`../environments.md`](../environments.md) |
| Staging provisioning steps | PO executes, runbook is the script | [`../staging/provision-staging.md`](../staging/provision-staging.md) (EB-73) |
| Pipeline contract & gates | DevOps | [`../cicd/staging-pipeline.md`](../cicd/staging-pipeline.md) (EB-75); `.github/workflows/deploy-staging.yml`, `docker-build.yml`; `scripts/cicd/deploy-gate.sh` |
| Secret ownership & rotation | PO + DevOps | [`../secrets/secrets-inventory.md`](../secrets/secrets-inventory.md), [`../secrets/rotation.md`](../secrets/rotation.md) (EB-74) |
| Backup scope | PO + DevOps | [`../backups/backup-policy.md`](../backups/backup-policy.md) (EB-73) |
| Migration authority | DevOps operator | `src/main/resources/db/migration/README.md` §3.1.1/§4 (EB-43/EB-44); `pom.xml` `db-migrate` profile |
| Verification tooling | DevOps | [`Invoke-DeployVerification.ps1`](Invoke-DeployVerification.ps1) (EB-79), [`../staging/Invoke-StagingSmoke.ps1`](../staging/Invoke-StagingSmoke.ps1) (EB-73) |
| Rollback authority | DevOps | [`rollback-triggers.md`](rollback-triggers.md) (EB-79) |
| Production rollout (finalizer) | P7-06 | [`deploy-prod.md`](deploy-prod.md) |
