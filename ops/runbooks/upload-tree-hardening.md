# Upload-tree hardening — deployment evidence (EB-89)

Ops ticket: **EB-89 — filesystem permission hardening evidence for the upload tree
(file-serving TOCTOU containment)**, required when EB-27/PR #40 shipped. The code
review accepted the residual validate→open TOCTOU window as **out-of-contract**
(exploitation requires local filesystem write access to the upload tree — HTTP
alone cannot obtain it) — **conditional on this written deployment evidence.**
**No Docker, ever (D-27):** every check below is a plain host/filesystem read.

## The fail-closed storage contract (Omar, 2026-11-12 — binding)

Production storage is **Supabase S3, fail-closed**:

- If Supabase S3 is down, unreachable, or fails for **any** reason during an
  upload, the application **immediately aborts**, throws, and returns a failure
  response (500/503) to the client.
- It must **NEVER** silently catch the error and fall back to saving locally
  (`/app/uploads`). Containers are **stateless** — a local fallback is silent
  data loss upon the next restart.
- `/app/uploads` hardening is for **local/dev and temporary processing only**;
  production is hard-wired to fail if S3 fails.

**Enforcement status (active, in code + CI):**

| Layer | Where | Status |
|---|---|---|
| Unconfigured-in-prod boot guard | `FileStorageService.saveFile` — `isProduction() && !isConfigured()` → `IllegalStateException` ("Local storage fallback is prohibited in production") | in code |
| Client-bean guard | `SupabaseStorageConfig.s3Client` — prod without credentials → `IllegalStateException` ("FAIL-CLOSED") | in code |
| S3 failure conversion | `SupabaseStorageServiceImpl.uploadPrivate` — `catch (S3Exception)` → rethrow as `globalLogicEx`; transport failures (`SdkClientException`) propagate | in code |
| **No-fallback invariant** | `StorageFailClosedTest` (5 probes): prod+configured+`S3Exception` / +`SdkClientException` / prod+unconfigured / dev+configured → **abort + upload tree stays EMPTY**; dev+unconfigured → local (legit) | CI-pinned |
| Backend-selection doctrine | configured ⇒ S3-only in **every** profile; the profile never softens a configured backend | pinned by the same test |

## Acceptance items → evidence

### 1. Upload tree writable ONLY by the app-service user
**Windows host:** run `ops/runbooks/Invoke-UploadTreeEvidence.ps1` (from the app
service account if possible) — Gate 1 prints the ACL owner and every writable
principal. PASS = no `Everyone` / `BUILTIN\Users` / `Authenticated Users` /
`ANONYMOUS` among the writable set; only the app-service identity + administrators.
**Linux host:** run `ops/runbooks/invoke-upload-tree-evidence.sh` — PASS = not
other-writable; group-writable only if the group is the app-service group alone.
**Container (Railway):** the EB-76 image runs as dedicated non-root
`edunest` (uid/gid 1001) and chowns **only** `/app/uploads` — the Dockerfile is
the standing evidence (checked out at the deployed commit).

### 2. 8.3 short-names disabled; no untrusted reparse points under the root
**Windows host:** the same script — Gate 2 runs `fsutil 8dot3name query <drive>`
(**requires an elevated shell**; if unelevated it reports UNVERIFIED — rerun as
Administrator). PASS = `8dot3name generation is disabled`. Gate 3 scans the whole
tree for junctions/symlinks/reparse points — PASS = none found.
**Linux:** 8.3 is a Windows concept (N/A); the script's Gate 3 scans for symlinks.
**Container:** EB-76 runs as a single-purpose non-root user with no untrusted
principals in the container — no reparse-point concern.

### 3. `app.upload.base-dir` pinned ABSOLUTE in prod
- The relative default (`uploads`) couples to the process CWD — pinned away in
  the deployed image: **`Dockerfile` now sets `ENV APP_UPLOAD_BASE_DIR=/app/uploads`**.
- **CI pin:** a new `EnvContractGuardTest` probe (6th) fails the build if the
  Dockerfile stops pinning the absolute var.

### 4. Reviewer sign-off (recorded 2026-11-12)

> **Tech Lead sign-off (Omar, 2026-11-12):** *"I have decided to waive the local
> Windows host evidence requirement. Since the production environment is fully
> secured via the Docker non-root user (`edunest`) and the strict S3 fail-closed
> logic, the local Windows ACLs are strictly a local development concern and not
> a priority for this sprint. Consider this message my official Tech Lead
> sign-off: The fail-closed storage contract is verified, and the container
> runtime hardening is accepted."*

**Disposition of the acceptance items under this sign-off:**

| Item | Disposition |
|---|---|
| 1. Upload tree writable only by app-service user | **WAIVED for the local Windows dev host** — production boundary is the container runtime: non-root `edunest` (uid/gid 1001) with only `/app/uploads` chowned (EB-76 Dockerfile). The collectors remain available for any future host-level audit. |
| 2. 8.3 / reparse-point evidence | **WAIVED with item 1** (same local-host scope; 8.3 is a Windows-host concept with no container analog). |
| 3. `app.upload.base-dir` pinned absolute in prod | **ACCEPTED** — Dockerfile `ENV APP_UPLOAD_BASE_DIR=/app/uploads`, double CI-pinned (`EnvContractGuardTest` + `SecretsHygieneGuardTest`). |
| 4. Reviewer sign-off | **RECORDED above** (Omar, 2026-11-12). |

The security-relevant contract lives outside the local host: **fail-closed
Supabase S3 in production** (verified by `StorageFailClosedTest`, 5 CI probes)
and the **container runtime hardening** (EB-76) — both accepted by this sign-off.
The ticket closed **Done** on this basis (Jira, 2026-11-12).

## Evidence-collection procedures (copy-paste)

```powershell
# Windows host (elevated for the 8.3 gate):
powershell -ExecutionPolicy Bypass -File ops/runbooks/Invoke-UploadTreeEvidence.ps1 `
    -BaseDir "<absolute upload root>"
# exit 0 = green; the script prints a paste-ready EVIDENCE BLOCK for EB-89.
```

```bash
# Linux host / staging VM (Hazem to verify on Linux, same pattern as the backup script):
./ops/runbooks/invoke-upload-tree-evidence.sh /absolute/upload/root
```

Fail-path contract: a broad-writable ACL, an enabled 8.3 volume, any reparse
point under the root, or a relative prod base-dir → **exit 1** with the failing
gates listed (fix, then re-run).

## Production reality check

Production is **fail-closed Supabase S3** (`FileStorageService` refuses local
fallback in prod); the local tree is the dev/local-store + temporary-processing
path. The evidence above therefore protects the environments where the local
tree is actually live (dev hosts, staging, and the container's `/app/uploads`
for temp processing), and the fail-closed contract protects production itself.
