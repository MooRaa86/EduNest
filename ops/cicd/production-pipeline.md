# EB-115 Production Deployment Pipeline Specification

**Status: IMPLEMENTED IN PR #129 — NOT ACTIVATED (INERT).**  
Automatic production deployment is disabled by default via repository variable `PROD_AUTO_DEPLOY_ENABLED=false`. No production secrets are accessed, no migrations are executed, and no deployments occur until explicitly authorized by the Project Owner.

---

## 1. Architecture Overview

EB-115 replaces the overengineered Python deployment framework with the repository's proven, minimal pipeline architecture mirroring `deploy-staging.yml`:

```
Push to 'production' branch
  │
  ▼
[Stage 1: Activation Gate] ───────────► (Disabled: exit 0 cleanly; no secrets loaded)
  │ (Enabled)
  ▼
[Stage 2: Verify & Prepare Image] ────► Release SHA freshness check, Maven package,
  │                                     PostgreSQL ITs, GHCR build/push, digest pinning
  ▼
[Stage 3: Disposable Rehearsal] ──────► Testcontainers PostgreSQL migration rehearsal
  │
  ▼
[Stage 4: Production Migration Gate] ──► Environment: production
  │                                     DB identity guard (ciogysvcykzxrjuskxmk, sslmode=verify-full)
  │                                     Backup & backward compatibility verification
  │                                     Flyway: info -> validate -> migrate -> validate -> info
  ▼
[Stage 5: Railway Deploy] ─────────────► Environment: production
  │                                     Railway CLI v4: service source connect + redeploy
  │                                     Authoritative deployment ID captured & polled
  ▼
[Stage 6: Smoke & Evidence] ──────────► Invoke-DeployVerification.ps1 health & commit match
                                        Sanitized evidence artifact uploaded
```

---

## 2. Hard Invariants Preserved

1. **Spring Boot Invariants:**
   - `spring.flyway.enabled=false` — application startup never executes database migrations.
   - `spring.jpa.hibernate.ddl-auto=validate` — strict schema validation fails fast at boot if the database schema does not match JPA entities.
2. **Release Branch Topology:**
   - Production deployments trigger strictly from pushes to the `production` branch (which receive merged release PRs from `main`).
   - `main` remains dedicated to staging deployments (`deploy-staging.yml`).
3. **Target Database Identity:**
   - Enforced by `deploy-gate.sh production-db-identity`.
   - Positively verifies the authorized production Supabase project ref (`ciogysvcykzxrjuskxmk`).
   - Requires exact `sslmode=verify-full` (rejects `sslmode=require` and all weaker modes).
   - Rejects staging references (`STAGING_DB_PROJECT_REF`) and unknown targets.
   - Rejects embedded credentials, duplicate parameters, and ambiguous multi-host URLs.
   - Enforces user/host coupling:
     - Direct: host `db.ciogysvcykzxrjuskxmk.supabase.co`, db_user `postgres`.
     - Pooler: host `*.pooler.supabase.com`, db_user `postgres.ciogysvcykzxrjuskxmk`.
   - Credentials are never logged, printed, or passed through step outputs.
4. **Digest-Pinned Immutability:**
   - Images are referenced strictly as `ghcr.io/mooraa86/edunest-backend@sha256:...`.
   - The OCI revision label `org.opencontainers.image.revision` is inspected after build to prove it matches the release commit SHA.
5. **Fail-Closed Migration Sequencing:**
   - Forward-only: no `clean`, no `repair`, no `undo`, no auto-baseline.
   - If no pending migrations exist, `flyway:migrate` is cleanly skipped.
   - If pending migrations exist:
     - `PROD_BACKUP_CONFIRMED_SHA` must match the release SHA (auditable pre-migration backup confirmation per runbook §3).
     - `PROD_COMPATIBILITY_CONFIRMED_SHA` must match the release SHA (verified backward compatibility with the currently serving deployment).
   - A failed or blocked migration halts the pipeline before Railway deployment.
6. **Authoritative Railway Deployment Correlation:**
   - Uses official Railway CLI v4 (`service source connect --image` + `redeploy --from-source --yes --json`).
   - Captures the exact `DEPLOY_ID` from the CLI response payload.
   - Polls `railway deployment list --json` until that specific `DEPLOY_ID` reaches a terminal status (`SUCCESS|DEPLOYED|ACTIVE`).
   - Rejects inferring deployment identity from the latest service health.

---

## 3. Operational Prerequisites for Pipeline Activation

Before setting `PROD_AUTO_DEPLOY_ENABLED=true`:

1. **Railway Auto-Deploy Cutover:**
   - In Railway Dashboard $\to$ Project `terrific-amazement` $\to$ Service `EduNest` $\to$ Settings:
   - Toggle **Auto Deploy to OFF** for the `production` branch.
   - *Rationale:* While Railway Auto-Deploy is enabled, any push to `production` causes Railway to build and deploy from git source immediately, bypassing GitHub Actions and the Flyway migration gate.
2. **GitHub Environment `production` Configuration:**
   - Ensure the following secrets are configured under GitHub Environment `production`:
     - `PROD_FLYWAY_URL`
     - `PROD_FLYWAY_USER`
     - `PROD_FLYWAY_PASSWORD`
     - `RAILWAY_TOKEN` (Production-scoped token)
   - Ensure the following variables are configured:
     - `PROD_DB_PROJECT_REF` = `ciogysvcykzxrjuskxmk`
     - `RAILWAY_PROD_PROJECT_ID` = `0958db82-35db-44a6-9bf3-5ef0f04e138a`
     - `RAILWAY_PROD_SERVICE_ID` = `bc38f0a7-6c4f-4df1-aad3-eb56e4693448`
     - `RAILWAY_PROD_ENVIRONMENT_ID` = `034521d6-7979-47dc-859e-ac39ee849f03`
     - `PROD_BASE_URL` = `https://backend.edunest.dpdns.org`
3. **Database Reconciliation (EB-114 V14 History):**
   - Confirm production database schema state and baseline integrity before first automated migration.
