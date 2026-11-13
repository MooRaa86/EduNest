# Production Activation Readiness & Cutover Assessment

**Project:** EduNest Backend (`EB`)  
**Document ID:** `PRODUCTION-ACTIVATION-READINESS`  
**Reference Issues:** `EB-115` (P6-04 / P7-06), `EB-77` (P6-06), `EB-80` (P6-09), `EB-113` (P6-10)  
**Author:** Omar Medhat (Tech Lead)  
**Date:** 2026-11-14  
**Target Cutover:** Automated Gated Production Pipeline (GitHub Actions $\to$ GHCR $\to$ Railway + Supabase Flyway)  
**Overall Verdict:** **NO-GO FOR IMMEDIATE CUTOVER** | **GO FOR MERGING PR #130 & STAGING PREREQUISITES**

---

## 1. Executive Summary & Objective

This document establishes the authoritative readiness assessment, infrastructure checklist, and step-by-step cutover runbook for transitioning the EduNest backend from the legacy push-triggered Railway build flow to the enterprise **6-stage gated production release pipeline** (`deploy-production.yml` / EB-115).

### Why a Controlled Cutover is Mandatory:
Currently, Railway automatically builds and deploys from Git source within ~2 seconds of any commit landing on the `production` branch. While functional, this legacy flow has two critical architectural vulnerabilities:
1. **Bypasses Database Migration Sequencing:** Railway deploys application code before database migrations are executed. Because production runs `spring.jpa.hibernate.ddl-auto=validate` and `spring.flyway.enabled=false`, any code release requiring schema additions risks failing at boot before an operator can manually migrate.
2. **Lacks Immutable Artifact Traceability:** Railway rebuilds from Git rather than pulling immutable, cryptographically digest-pinned OCI container images (`ghcr.io/...@sha256:...`) validated in CI.

The new pipeline resolves both issues by enforcing that tested commit == built commit == migrated schema == deployed container. However, activating it requires strict sequencing to avoid service outages or duplicate deployments.

---

## 2. Current Production State (Live Forensics)

All items verified via live probes on 2026-11-14:

| Component | Current Live State | Verification Source / Evidence |
| :--- | :--- | :--- |
| **Hosting Platform** | Railway PaaS | Project `terrific-amazement`, Service `EduNest`, 1/1 replica, Region `iad` |
| **Live Production URL** | `https://backend.edunest.dpdns.org` | DNS resolves, TLS valid |
| **Liveness / Readiness** | **UP** (`{"status":"UP","groups":["liveness","readiness"]}`) | HTTP GET `/actuator/health` (verified 2026-11-14) |
| **Process Security** | Protected (HTTP 401 Unauthorized) | HTTP GET `/actuator/info` (requires `ROLE_ADMIN` per EB-67) |
| **Serving Branch** | `production` | Tracks `git@github.com:MooRaa86/EduNest.git` |
| **Serving Commit** | `2b44daaf5e8202dfae2217da8e7febbdeb1e7d6b` | PR #121 + EB-108 (Stripe payments) + EB-80 (runbooks) |
| **Active Deployment Flow** | Railway Auto Deploy (**ACTIVE**) | Automatically rebuilds and deploys on push to `production` |
| **Post-Deploy Verification** | **ACTIVE & GREEN** | `.github/workflows/verify-deploy.yml` (Run `#43337990309` passed) |
| **Database Schema** | PostgreSQL 17 on Supabase | Project Ref: `ciogysvcykzxrjuskxmk` |
| **Current Schema Version** | `V1`–`V12`, `V14` (`payment_transactions`) | `ddl-auto=validate` booted cleanly in live deployment |
| **Branch Rulesets** | None active (`[]`) | `gh api repos/.../rules/branches/production` returns empty |

---

## 3. GitHub Environment & Configuration Checklist

The new deployment workflow (`deploy-production.yml`) executes under the GitHub Environment named **`production`**. 

### A. Environment Entity Status:
- [x] **Create GitHub Environment `production`**:
  - *Current Status:* **CREATED** (Environment ID `27252909928`).
  - *Protection Rules:*
    - **Required Reviewers:** Omar Medhat (User ID `113546087`, Tech Lead / Project Owner).
    - **Deployment Branches:** Restricted exclusively to `production` branch (Policy ID `66643771`).
    - **Prevent Self-Review:** Enabled.

### B. Required Secrets (Environment-Scoped under `production`):
> **SECURITY MANDATE:** Secret values must NEVER be committed to Git, logged, or printed. Names only.

- [ ] **`PROD_FLYWAY_URL`**:
  - Direct or transaction-pooler JDBC connection string targeting Supabase production.
  - Must strictly enforce `sslmode=verify-full`.
  - Accepted host patterns: `db.ciogysvcykzxrjuskxmk.supabase.co:5432` or `*.pooler.supabase.com:6543`.
- [ ] **`PROD_FLYWAY_USER`**:
  - Direct: `postgres`.
  - Pooler: `postgres.ciogysvcykzxrjuskxmk`.
- [ ] **`PROD_FLYWAY_PASSWORD`**:
  - Production database role password.
- [ ] **`RAILWAY_TOKEN`**:
  - Production-scoped Railway project token (must NOT be a personal user token; must only have access to project `terrific-amazement`).

### C. Required Variables (Repository-Scoped under Actions $\to$ Variables):
- [x] **`PROD_AUTO_DEPLOY_ENABLED`**: Set to `false` (fail-closed default, **CONFIGURED**).
- [x] **`PROD_MANUAL_DEPLOY_ENABLED`**: Set to `false` (fail-closed default, **CONFIGURED**).
- [x] **`PROD_DB_PROJECT_REF`**: `ciogysvcykzxrjuskxmk` (**CONFIGURED**).
- [x] **`RAILWAY_PROD_PROJECT_ID`**: `0958db82-35db-44a6-9bf3-5ef0f04e138a` (**CONFIGURED**).
- [x] **`RAILWAY_PROD_SERVICE_ID`**: `bc38f0a7-6c4f-4df1-aad3-eb56e4693448` (**CONFIGURED**).
- [x] **`RAILWAY_PROD_ENVIRONMENT_ID`**: `034521d6-7979-47dc-859e-ac39ee849f03` (**CONFIGURED**).
- [x] **`PROD_BASE_URL`**: `https://backend.edunest.dpdns.org` (**CONFIGURED**).
- [ ] **`PROD_BACKUP_CONFIRMED_SHA`**: Empty string initially (populated per release containing pending migrations).
- [ ] **`PROD_COMPATIBILITY_CONFIRMED_SHA`**: Empty string initially (populated per release containing pending migrations).

---

## 4. Railway Service Configuration Checklist

Before pipeline cutover, Railway settings must be audited in the dashboard:

- [ ] **Auto-Deploy Cutover State**:
  - *Current:* **ON** (tracks `production` branch).
  - *Target during cutover:* **OFF**.
  - *Why:* If Auto-Deploy remains ON, merging to `production` triggers Railway immediately, creating a race condition with GitHub Actions and bypassing the Flyway migration gate.
- [ ] **Runtime Environment Variables**:
  - Confirmed: Railway variables store remains the primary runtime secrets store (`DATABASE_URL`, `DATABASE_NAME`, `DATABASE_PASSWORD`, `JWT_SECRET`, `SPRING_PROFILES_ACTIVE=prod`).
- [ ] **Restart Policy**:
  - `ON_FAILURE` (max 10 retries) preserved.
- [ ] **Container Image Support**:
  - Service must allow deployment via CLI command: `railway service source connect --image <digest-pinned-ref>` and `railway redeploy --from-source`.
  - GHCR registry credentials configured if image is private.

---

## 5. Supabase & Flyway Safety Assessment

### A. Current Migration Baseline
The repository currently maintains 13 versioned SQL migrations in `src/main/resources/db/migration/`:
- `V1__initial_schema.sql`
- `V2__mentorship_explore_trigram_index.sql`
- `V3__uniqueness_constraints_and_dedupe.sql`
- `V4__seed_default_commission.sql`
- `V5__refresh_token_sessions.sql`
- `V6__add_public_uuid_identifiers.sql`
- `V7__certificate_issuance_snapshot.sql`
- `V8__failed_email_attempts.sql`
- `V9__badge_and_total_points_versions.sql`
- `V10__unique_session_attendance_result.sql`
- `V11__completion_jobs_and_snapshots.sql`
- `V12__quiz_close_at_and_submission_is_late.sql`
- `V14__payment_transactions.sql` (EB-108 Stripe payments)

*(Note: V13 is an internal in-memory test sentinel used in `FlywayDisabledStartupPostgresTest.java` to verify that startup never applies pending migrations; it is deliberately omitted from production migration scripts).*

### B. Live Database Schema Audit:
- **Observation:** `PaymentTransaction` entity (introduced in EB-108) is running on the live Railway production instance (`2b44daaf`). Because `spring.jpa.hibernate.ddl-auto=validate` is active, the application successfully validated `payment_transactions` at boot.
- **Mandatory Preflight Before Any Automated Migration:**
  An operator must run the read-only inspection query from an authorized environment to verify the exact history table:
  ```powershell
  .\mvnw.cmd -B -Pdb-migrate flyway:info
  ```
  Ensure all migrations `V1`–`V14` report status `Success` with zero `Failed` or `Pending` rows.
- **Fail-Closed DB Identity Guard (`deploy-gate.sh production-db-identity`):**
  - Positively matches ref `ciogysvcykzxrjuskxmk`.
  - Rejects any URL missing `sslmode=verify-full`.
  - Rejects credentials containing staging references or multi-host ambiguity.

---

## 6. Pipeline Verification Results (PR #129 & PR #130)

### A. PR #130 (`EB-77` / `P6-06` — Repository Hygiene Gate):
- **Status:** **MERGED INTO `main`** (`49d0fb4b787d3349a4f17d430df5203441e61ae3`).
- **Checks:**
  - `scripts/cicd/test-repo-hygiene-gate.sh`: **14/14 unit tests passed**.
  - GitHub Actions `tests.yml` (Run `#43479910175`): **SUCCESS**.

### B. PR #129 (`EB-115` — Production Deployment Pipeline):
- **Status:** **APPROVED & MERGED INTO `main`** (`5108b748ad28b34a54253868269e8463c234da0c`).
- **Review:** Approved by Tech Lead (Omar Medhat) per review ID `5899079109`.
- **Checks:**
  - `scripts/cicd/test-deploy-gate.sh`: **97/97 unit tests passed**.
  - GitHub Actions `EB-115 production security checks`: **SUCCESS**.
- **Current Posture:** Fails closed by default; `PROD_AUTO_DEPLOY_ENABLED` and `PROD_MANUAL_DEPLOY_ENABLED` are set to `false`.

---

## 7. Cutover Execution Runbook (Step-by-Step)

When Project Owner authorizes production cutover, execute strictly in this order:

```
[Phase 1: Merge PRs] ────────► Merge PR #130, then merge PR #129 into main
        │
[Phase 2: GitHub Prep] ──────► Create Environment 'production' + Secrets + Variables
        │
[Phase 3: Preflight Audit] ──► Read-only flyway:info on Supabase (confirm V1-V14 clean)
        │
[Phase 4: Railway Cutover] ──► Railway Dashboard: Toggle Auto Deploy to OFF
        │
[Phase 5: First Release] ────► Cut release PR (main -> production) & merge to production
        │
[Phase 6: Manual Trigger] ───► Set PROD_MANUAL_DEPLOY_ENABLED=true -> workflow_dispatch
        │
[Phase 7: Verification] ─────► Automated Smoke + Commit Identity Match + Manual Spot-Check
        │
[Phase 8: Full Auto] ────────► Set PROD_AUTO_DEPLOY_ENABLED=true
```

### Detailed Phase Instructions:

1. **Phase 1 — Merge PRs into `main`**:
   - Merge PR #130 (`feat/eb-77-repo-hygiene-uploads-guard`) into `main`.
   - Merge PR #129 (`feat/eb-115-production-deploy-pipeline`) into `main`.
2. **Phase 2 — Configure GitHub Settings**:
   - Create GitHub Environment `production` with required reviewers (`Omar Medhat`).
   - Populate `PROD_FLYWAY_URL`, `PROD_FLYWAY_USER`, `PROD_FLYWAY_PASSWORD`, `RAILWAY_TOKEN`.
   - Populate repository variables listed in Section 3C (keeping `PROD_AUTO_DEPLOY_ENABLED=false`).
3. **Phase 3 — Database Preflight**:
   - Run `.\mvnw.cmd -B -Pdb-migrate flyway:info`. Confirm clean schema state.
   - Confirm latest Tier 1 Supabase automated backup exists. Take Tier 2 offline dump via `ops/backups/Invoke-Backup.ps1`.
4. **Phase 4 — Railway Setting Change**:
   - Open Railway Project `terrific-amazement` $\to$ Service `EduNest` $\to$ Settings.
   - Set **Auto Deploy = OFF**.
5. **Phase 5 — Promote to `production` branch**:
   - Open Release PR from `main` to `production`.
   - Verify both required PR checks pass: `Build + test + package (JDK 21)` and `smoke`.
   - Merge Release PR into `production`.
   - *(Note: Because Auto Deploy is OFF and PROD_AUTO_DEPLOY_ENABLED=false, no deployment occurs yet).*
6. **Phase 6 — Execute Controlled First Release**:
   - In GitHub Repository Variables, set `PROD_MANUAL_DEPLOY_ENABLED = true`.
   - Go to GitHub Actions $\to$ **Deploy Production** $\to$ **Run workflow**.
   - Input: The 40-character commit SHA just merged into `production`.
   - Owner approves environment prompt in GitHub Actions.
   - Observe pipeline execution:
     - Stage 1: Activation Gate ($\checkmark$)
     - Stage 2: Verify Release & Push Digest-Pinned OCI Image ($\checkmark$)
     - Stage 3: Disposable Migration Rehearsal in PostgreSQL container ($\checkmark$)
     - Stage 4: Production DB Identity Guard & Flyway validate/migrate ($\checkmark$)
     - Stage 5: Railway CLI source connect & redeploy tracked deployment ($\checkmark$)
     - Stage 6: Post-deploy smoke & commit ID verification ($\checkmark$)
7. **Phase 7 — Post-Deploy Verification**:
   - Run local verification:
     ```powershell
     powershell -ExecutionPolicy Bypass -File ops/runbooks/Invoke-DeployVerification.ps1 `
       -BaseUrl "https://backend.edunest.dpdns.org" -ExpectedCommit "<deployed-sha>"
     ```
   - Spot-check public endpoints: `/actuator/health`, `/v3/api-docs`, `/swagger-ui/index.html`.
8. **Phase 8 — Enable Automatic Deployments**:
   - Once the manual run succeeds end-to-end, set repository variable `PROD_AUTO_DEPLOY_ENABLED = true`.
   - Subsequent release PRs merged to `production` will now trigger the full automated pipeline safely.

---

## 8. Rollback & Disaster Recovery Procedures

| Scenario | Trigger / Symptom | Recovery Procedure |
| :--- | :--- | :--- |
| **Pipeline Gate Failure (Stages 1–4)** | Gate check fails (freshness check, rehearsal, or identity guard) | **Zero Impact:** Railway and production database remain completely untouched. Fix issue on `main` and cut a new release commit. |
| **Container Boot Crash (Stage 5–6)** | Healthcheck fails or container exits during rollout | 1. Open Railway Dashboard $\to$ Deployments $\to$ Click previous deployment $\to$ **Rollback**.<br>2. Run `Invoke-DeployVerification.ps1` with prior commit SHA.<br>3. Verify service returns to `UP`. |
| **Database Migration Failure** | `flyway:migrate` fails mid-execution | **Database is NOT rolled back automatically** (forward-only rule).<br>1. If schema is backward-compatible: Redeploy previous working image.<br>2. If data damaged: Restore Tier 2 snapshot per `ops/backups/restore-procedure.md` with PO sign-off.<br>3. Prepare forward-fix migration `V{N+1}`. |
| **Emergency Reversion to Legacy Deploy** | Critical pipeline failure requiring immediate unblocking | 1. Set `PROD_AUTO_DEPLOY_ENABLED = false` in GitHub Variables.<br>2. Open Railway Dashboard $\to$ Settings $\to$ Toggle **Auto Deploy = ON**.<br>3. Railway resumes deploying directly from Git commits on `production`. |

---

## 9. Remaining Pre-Activation Gaps

The following items remain to be completed prior to cutover:

1. 🛑 **Environment Secrets Configuration**: `PROD_FLYWAY_URL`, `PROD_FLYWAY_USER`, `PROD_FLYWAY_PASSWORD`, `RAILWAY_TOKEN` must be populated inside the newly created GitHub `production` environment secrets.
2. 🛑 **Live DB Flyway History Audit**: Live `flyway:info` read-only check required to confirm V1–V14 state.
3. 🛑 **Railway Auto Deploy Toggle**: Railway service setting `Auto Deploy` needs to be set to OFF during the scheduled cutover window.

---

## 10. GO / NO-GO Recommendation

### Verdict: 🔴 **NO-GO FOR PRODUCTION CUTOVER AT THIS MOMENT**

#### Justification:
The pipeline code, security adapters, and CI test suites are thoroughly verified and ready. However, the external infrastructure prerequisites (GitHub Environment creation, secret provisioning, and Flyway database audit) have not yet been executed. Attempting to activate production automation without these prerequisites will result in an immediate fail-closed pipeline halt.

### Recommended Action Plan:
1. **Approve and Merge PR #130** into `main` (Repository Hygiene Gate).
2. **Approve and Merge PR #129** into `main` (Production Pipeline code).
3. **Execute Section 3 (GitHub Environment & Secrets Setup)** via GitHub UI.
4. **Execute Section 5 (Flyway Read-Only Audit)** via operator CLI.
5. **Re-evaluate to 🟢 GO** for scheduled cutover window.
