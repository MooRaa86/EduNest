# Staging Provisioning Runbook (P6-02 / EB-73)

> Config-as-script: this runbook IS the provisioning definition — whoever clicks the console buttons follows it top to bottom. Target shape per D-22: staging mirrors production (Railway container + Supabase PostgreSQL + Supabase Storage), fully isolated, **no production data, no production secrets**.
>
> Deploy source for staging: as of **P6-04 / EB-75**, deploys are wired through the staging-on-merge pipeline (`deploy-staging.yml`: activation gate -> tests gate -> digest-pinned GHCR image -> migration gate -> Railway deploy -> smoke -> evidence artifact; contract in [`../cicd/staging-pipeline.md`](../cicd/staging-pipeline.md)). **Automatic deployment is disabled by default** — it activates only when the Project Owner sets the repository variable `STAGING_AUTO_DEPLOY_ENABLED=true` (explicit authorization) and the GitHub Environment `staging` + Railway Registry Credentials (GHCR `read:packages` PAT) are provisioned; until then keep using the manual "Deploy" from `main` in the Railway staging service (decision 2026-11-11, Omar-approved).

## 0. Isolation rules (read first)

1. Staging gets its **own Supabase project** — separate from the production project (`ciogysvcykzxrjuskxmk`). Never point staging at production data.
2. Staging secrets are **distinct values** held in the staging Railway service (same store topology as [EB-74](../secrets/README.md): Railway dashboard = env store; Supabase = DB/storage creds). Never reuse production secret values.
3. `EDUNEST_UNSAFE_DEV` must never exist in staging (SEL-02): staging runs `SPRING_PROFILES_ACTIVE=prod` — the **hardened chain** — pointed at staging infrastructure. "Mirroring prod" means the security chain, not the data.

## 1. Supabase staging project (database + storage)

1. supabase.com → **New project** → name `edunest-staging`, region = same as prod (`eu-west-1`) unless cost/latency says otherwise.
2. Record into the staging Railway variables (step 2), never into git:
   - `DATABASE_URL` — Project Settings → Database → Connection string (pooler `:6543` for app runtime, `sslmode=require`)
   - `DATABASE_NAME` / `DATABASE_PASSWORD` (the project DB password)
   - `SUPABASE_S3_ENDPOINT` / `SUPABASE_S3_REGION` / `SUPABASE_S3_ACCESS_KEY` / `SUPABASE_S3_SECRET_KEY` (Storage → S3 access keys)
   - `SUPABASE_URL` (project URL)
3. Create the schema: run the authoritative reconciliation flow used by [ADD-02] (`tools/migration/`) against the staging DB, or let Flyway run on first boot — the app boots with `spring.flyway.enabled=false` + `ddl-auto=validate` by default, so the **[ADD-02] schema must be applied before first boot** (same requirement as production; the migration rehearsal in `ops/rehearsals/` validates the DDL).
4. Storage → create the two buckets with the same names as prod: `edunest-public` and `edunest-private` (policies per the [ADD-03]/EB-83 design — public read on public bucket only; private bucket never public).

## 2. Railway staging service (container)

1. Railway → **New service** in a dedicated staging project (or a second environment of the existing project — dedicated project preferred for full isolation) → deploy source becomes the P6-04 pipeline once activated (currently manual "Deploy" from `main`; the service was provisioned via Deploy-from-repo, but EB-75 deploys digest-pinned GHCR images via `railway service source connect --image` + `railway redeploy --from-source` — the service keeps its variables either way). One-time prerequisite for GHCR pulls: service Settings → Source → Registry Credentials → GitHub PAT with `read:packages` (private registry deploys require a Railway Pro plan).
2. Variables — the full staging set (from the [`ops/environments.md`](../environments.md) matrix + [EB-74 inventory](../secrets/secrets-inventory.md)):
   - `SPRING_PROFILES_ACTIVE=prod`
   - `PORT` — leave unset (Railway injects)
   - `DATABASE_URL`, `DATABASE_NAME`, `DATABASE_PASSWORD` — staging values from step 1
   - `JWT_SECRET` — **generate fresh** via [`ops/secrets/new-jwt-secret.ps1`](../secrets/new-jwt-secret.ps1) (staging tokens must not verify against prod's key)
   - `MAIL_HOST/PORT/USERNAME/PASSWORD` — staging mail identity (a dedicated Gmail/app-password, or prod SMTP credentials only if policy allows shared sending identity; distinct app-password recommended)
   - `SUPABASE_S3_*`, `SUPABASE_URL`, `SUPABASE_STORAGE_PUBLIC_BUCKET=edunest-public`, `SUPABASE_STORAGE_PRIVATE_BUCKET=edunest-private` — staging values
   - `ZOOM_*` — leave integration OFF (`ZOOM_ENABLED=false`) in staging unless a live-meeting test needs it; if enabled, use a test Zoom account
   - Do **not** set `EDUNEST_UNSAFE_DEV` in staging
3. Generate a domain: Railway → Settings → Networking → custom domain `staging-backend.edunest.dpdns.org` (DNS is managed where `dpdns.org` is hosted — add the CNAME to the Railway-generated target).
4. Deploy once and watch the log: `JwtSecretProvider` accepts the secret, Flyway validation passes (schema present), no fail-closed storage warnings.

## 3. Staging smoke (acceptance)

Run after every (re)provisioning and after any notable `main` change deployed manually:

```powershell
powershell -ExecutionPolicy Bypass -File ops/staging/Invoke-StagingSmoke.ps1 -BaseUrl "https://staging-backend.edunest.dpdns.org"
```

Checks: health UP, Swagger reachable, STOMP handshake info OK. Exit code 0 = staging healthy.

## 4. Who holds what (staging)

| Asset | Holder | Where |
|---|---|---|
| Railway staging project | Repo admin (Omar) | Railway dashboard |
| Supabase staging project + creds | Repo admin (Omar) | Supabase dashboard + staging Railway vars |
| Staging JWT_SECRET provenance | Repo admin | [`ops/secrets/JWT-GENERATION-RECORD.md`](../secrets/JWT-GENERATION-RECORD.md) — record the staging secret as a separate row |

## 5. Teardown / rebuild

Staging is disposable by design: the runbook above is the rebuild-from-scratch script. Tearing down = delete the Railway service + Supabase project; re-provisioning = follow sections 1–3. Nothing in git references staging secrets, so teardown leaks nothing.
