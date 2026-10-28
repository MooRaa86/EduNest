# Phase 06 — Tasks

Format canon: [`templates/TASK-TEMPLATE.md`](../../templates/TASK-TEMPLATE.md); anchors are short link targets (`tasks.md#p6-nn`). Infra tasks carry a **Rollback consideration** field per template.

<a id="p6-01"></a>
### P6-01 — Environment & infrastructure matrix (D-22 execution)

**Priority:** P0 — every infra task and the whole launch path hang on this decision made real; it's the only P0 in an infra phase because nothing else here is decidable without it.  **Size:** M
**Owner role:** DevOps Engineer (author) + PO (approval)   **Reviewers:** Eng Lead
**Issues:** DEP-07 (env definition half), D-22   **Domain:** deployment
**Components:** `ops/environments.md` (dev/test/staging/prod matrix), infra inventory doc, stakeholder approval record

**Context:** Today's reality: local Dockerfile, no compose, no infra (audit DEP). The matrix defines each environment's: host, MySQL provisioning + credentials flow, TLS/domain, backups target, upload storage, and who owns what.
**Evidence:** DECISIONS D-22 block; [`mds/deployment/environment-configuration.md`](../../../mds/deployment/environment-configuration.md).
**Expected outcome:** a ratification-ready matrix + chosen infra shape recorded against D-22 (recommended default: single VM + Docker Compose + off-site DB dumps) with explicit "revisit plan" triggers.
**Approach:** draft → review → PO approval meeting; record decision note in DECISIONS file (status flip) with matrix link.
**FE / API impact:** none.  **Environment:** hosting account access (PO-provided).
**Rollback consideration:** doc-only — rollback = revert to previous shape doc; no infra touched yet.
**Depends on:** D-22; P5-04 list.  **Blocks:** P6-02..09.
**Tests:** none (decision artifact).
**Docs:** environments matrix (new ops doc), DECISIONS D-22 record; ISSUE-MAPPING DEP-07.

<a id="p6-02"></a>
### P6-02 — Staging provisioning + backup policy

**Priority:** P1 — "doesn't even exist today"; staging must exist and mirror prod (R6) before any validation work.  **Size:** M
**Owner role:** DevOps Engineer   **Reviewers:** Eng Lead + PO (cost)
**Issues:** DEP-08 (policy half), DEP-03 (staging storage)   **Domain:** deployment
**Components:** host provisioning script (per D-22 shape), MySQL 8 instance, TLS/domain setup, `ops/backups/backup.sh`-level script + schedule, storage dir/volume

**Evidence:** D-22 matrix; ISSUES DEP-08; [`mds/deployment/environment-configuration.md`](../../../mds/deployment/environment-configuration.md).
**Expected outcome:** staging reachable at a domain w/ HTTPS; MySQL provisioned (same major version as pick for prod); backup script scheduled (daily, retention N per matrix), restore procedure documented (execution = P3-07 rehearsal + P8-05 drills).
**Approach:** config-as-script (not by hand); secrets into the P6-03 store; DNS/TLS from dependency register.
**Rollback consideration:** restore-from-backup is the documented recovery; staging redeployable from scratch script.
**Depends on:** D-22; P6-01.  **Blocks:** P7-01.
**Tests:** script runs on a disposable VM first (log artifact); restore check = backup → fresh instance boot.
**Docs:** ops/environments.md update; ISSUE-MAPPING DEP-08/DEP-03.

<a id="p6-03"></a>
### P6-03 — Secrets management implementation

**Priority:** P1 — historically the fallback JWT secret made secrets a *source-code fact*; now the store must exist before anyone provisions real values. **‼ Chained after P1-01: rotating a real secret before the code honors it breaks login (R1).**  **Size:** M
**Owner role:** DevOps Engineer   **Reviewers:** Security Reviewer
**Issues:** DEP-05 (infra half)   **Domain:** deployment / authentication-security
**Components:** secret store per D-22 (env-file w/ restricted perms is the accepted baseline; vault optional per scale), injection into containers, inventory per P5-04

**Evidence:** D-22 matrix; ISSUES DEP-05; P5-04 `.env.example`.
**Expected outcome:** staging/prod containers receive secrets via injection (no values in git/image layers/compose defaults); rotation procedure documented (stable + app restart); JWT_SECRET generation record.
**Approach:** baseline: directory `ops/secrets/` templates + `docker secret`/env-file pattern set by script; document rotation + who-holds-what.
**Rollback consideration:** fallback = previous env file (kept versioned in secret store, not git).
**Depends on:** P1-01 (order!), P6-01.  **Blocks:** P7-05.
**Tests:** secret-injection smoke: staging container boots with no env baked into image; log shows healthy startup.
**Docs:** environment-configuration.md §secrets; ISSUE-MAPPING DEP-05.

<a id="p6-04"></a>
### P6-04 — CI/CD stages to staging with gates & tags

**Priority:** P1 — one command from tag → tested image → staging; prod stays manual gated (P7).  **Size:** L
**Owner role:** DevOps Engineer   **Reviewers:** Eng Lead + Security Reviewer (pipeline)
**Issues:** DEP-01 (pipeline half)   **Domain:** deployment
**Components:** extend `.github/workflows/` (`build.yml`: compile+test+scan+image; `deploy-staging.yml`: on tag/manual), registry (per D-22; GHCR acceptable), tag scheme `git describe`/short-SHA

**Evidence:** ISSUES DEP-01; P2-07 workflow foundation; D-22 matrix.
**Expected outcome:** PR to `main` → checks; merge to `main` → image push with SHA tag; deploy-staging job runs migrations then new image (image-name/tag recorded in staging metadata file); prod deploy = same workflow invoked manually with approval gate (GitHub Environment protection) — *executed* at P7-06.
**Approach:** reuse P2-07 workflow (extend, don't fork); artifact traceability via tag ↔ commit ↔ run links; failure path: deploy job aborts on any required-check red; migration step ordering documented (runbook consumes).
**Rollback consideration:** redeploy = previous tag re-run (tags immutable); documented at P6-09 runbook.
**Depends on:** P2-07, P3-01 (migrations), P6-02 (target host).  **Blocks:** P7-01/06.
**Tests:** pipeline green run + a deliberately-failing check blocks deploy (fail-path evidence).
**Docs:** deployment/MDS.md; ISSUE-MAPPING DEP-01.

<a id="p6-05"></a>
### P6-05 — Docker/runtime hardening + persistent volume

**Priority:** P1 — container has no volume today: any redeploy loses every uploaded file (FILE-07/DEP-03 Critical-adjacent for real data).  **Size:** M
**Owner role:** DevOps Engineer   **Reviewers:** Eng Lead + Security Reviewer
**Issues:** FILE-07, DEP-03 (+DEP-06 partial: uploads ignore already present)   **Domain:** deployment / file-storage
**Components:** `Dockerfile` (non-root user, hardened entrypoint), compose file (dev/staging parity), volume for uploads + XFS/ext4 named volume, healthcheck (actuator from P5-01), devtools-absence verification (A-9)

**Evidence:** ISSUES FILE-07/DEP-03; [`mds/deployment/MDS.md`](../../../mds/deployment/MDS.md); A-9 assumption.
**Expected outcome:** image runs as non-root; uploads volume survives redeploys (test: upload → redeploy → still there); healthcheck wired; expose-list minimal; A-9 verified (devtools not in jar).
**Approach:** adjust USER/CHOWN; compose `volumes` for the base-dir; compose files in `ops/` folder (dev + staging variants); `.dockerignore` review.
**Rollback consideration:** volume is additive; rollback = old image + same volume (managed migration not needed).
**Depends on:** P5-01, P6-02.  **Blocks:** P7-01.
**Tests:** image inspection (no devtools, non-root uid); volume-persistence IT documented w/ capture.
**Docs:** deployment/MDS.md; ISSUE-MAPPING FILE-07/DEP-03.

<a id="p6-06"></a>
### P6-06 — Repo hygiene + uploads history purge

**Priority:** P2 — 23+ real user files are in git history (FILE-06/DEP-06); even with `.gitignore` (`**/uploads/` already present) they persist in all clones. D-19 approves the forced-push moment.  **Size:** M
**Owner role:** DevOps Engineer   **Reviewers:** Eng Lead + PO (approval) + Security Reviewer
**Issues:** FILE-06, DEP-06 (D-19)   **Domain:** deployment / repo hygiene
**Components:** `git rm -r --cached uploads`, history rewrite via `git filter-repo` (or BFG), branch-protection interplay, clone-refresh comms, CI guard preventing re-commits (path check job optional), `test.txt` disposition

**Evidence:** ISSUES FILE-06/DEP-06; `.gitignore` lines 34–35 (already ignoring, but tracked files precede it); D-19 decision.
**Expected outcome:** no commit (in any branch) contains user uploads; contributors re-clone with documented commands; guard prevents accidental re-add.
**Approach:** announce window → re-clone instructions → filter-repo on all branches → force-push coordinated (protection lifted temporarily in the window) → guards re-established.
**Rollback consideration:** full backup bundle (bare clone archive) kept until next successful cycle; procedure reversible by restoring that bundle.
**Depends on:** D-19; CI (P2-07) for guard.  **Blocks:** —
**Tests:** post-purge clone verification (file list); CI guard triggers on re-add attempt (fail-path evidence).
**Docs:** environment/ops docs note; ISSUE-MAPPING FILE-06/DEP-06.

<a id="p6-07"></a>
### P6-07 — Object storage for uploads (conditional)

**Priority:** P2 — D-25 default is **volume first** (P6-05); this task executes object storage only if PO opts in (potentially post-launch).  **Size:** L
**Owner role:** DevOps Engineer + Backend Developer   **Reviewers:** Eng Lead
**Issues:** FILE-07 option (D-25)   **Domain:** deployment / file-storage
**Components:** storage abstraction adapter (S3-API compatible or chosen provider), config switch, migration script for existing volume files (optional)

**Evidence:** D-25 decision; ISSUES FILE-07; [`mds/file-storage/MDS.md`](../../../mds/file-storage/MDS.md).
**Expected outcome (if approved):** app reads/writes through a pluggable client configured per environment; existing volume data migrated or dual-read during transition.
**Rollback consideration:** adapter switch returns to volume mode (config-only).
**Depends on:** P6-05 (volume baseline); D-25 approval.  **Blocks:** —
**Tests:** adapter IT against localstack-ish container + prod-like config.

<a id="p6-08"></a>
### P6-08 — Deployment verification & failure handling

**Priority:** P2 — deploys need an *automatic* post-deploy check (health + smoke) instead of a human peeking, and a defined abort path.  **Size:** M
**Owner role:** DevOps Engineer   **Reviewers:** Eng Lead
**Issues:** DEP-01 (verification half)   **Domain:** deployment
**Components:** post-deploy job (health poll w/ timeout, tagged version match check, log sweep for boot errors, smoke endpoint set), failure → rollback trigger execution (documented at P6-09)

**Evidence:** ISSUES DEP-01; runbook needs (P7-06 input).
**Expected outcome:** deploy job verdict = verify step; on failure: automatic halt + rollback trigger checklist run (rollback = previous tag redeploy, DB untouched unless migration ran — pre/post-migration boundaries from P3-07 rehearsal artifacts).
**Rollback consideration:** the task *builds* the rollback automation; drill = P7-01.
**Depends on:** P6-04/05; P5-01.  **Blocks:** P7-06/07.
**Tests:** fail-path IT: inject failing smoke → job marks red → rollback trigger documented (dry-run log).

<a id="p6-09"></a>
### P6-09 — Runbook draft & environment config matrix

**Priority:** P2 — the P7-06 production runbook needs a draft to review *before* release day (not during it).  **Size:** S
**Owner role:** DevOps Engineer   **Reviewers:** Eng Lead + PO
**Issues:** DEP-07 (docs half), DEP-01   **Domain:** deployment
**Components:** `ops/runbooks/deploy-staging.md`, `ops/runbooks/deploy-prod.md` (draft to be finalized in P7), config matrix cross-linked matrix from P6-01

**Evidence:** ISSUES DEP-07; phases 6-7 artifacts so far.
**Expected outcome:** step-by-step runbooks exist: preconditions, backup, migrate, deploy, verify, rollback — each step annotated with which task provides the artifact.
**Depends on:** P6-01..08 outputs.  **Blocks:** P7-06 (finalizes + executes).
**Tests:** walk-through dry-run on staging (recorded).
**Docs:** runbooks (new), environment-configuration.md links.
