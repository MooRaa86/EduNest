# EB-5 / P0-07 — ZIP Allowlist Endpoint-Level Verification Verdict (FILE-04)

**Task:** EB-5 ([P0-07] V-05: Zip allowlist looseness test, FILE-04)
**Branch:** `test/p0-07-zip-spoof-endpoint-verdict`
**Base:** `origin/main` @ `5c8c822` ("chore(p2-03): mark P2-03 delivered post-merge (0326b8b, EB-37) + SEL-14/TST-01 rows")
**Date:** 2026-10-29
**Nature:** verification-only evidence task — NO production upload validation was changed. Validation hardening remains owned by EB-29; public/private policy by EB-28; Supabase Storage mechanics by EB-83.

## 1. Question under investigation

EB-4 (PR #20, `c637e4a`) confirmed that `FileStorageService.saveFile` accepts non-ZIP bytes detected by Apache Tika as `application/octet-stream` when the extension is `.zip` — but only at the **service boundary**, calling `saveFile` directly. The endpoint-level question — do the actual task/project **attachment** and **submission** HTTP upload flows accept the same spoofed payload through real controllers, authorization, and service chains — was explicitly left to EB-5 (`EB-4-UPLOAD-MATRIX.md`, "FILE-04 … Task/project HTTP controllers and EB-5's focused verdict remain unverified").

## 2. Root confirmations in source (same commit as the test run)

- `FileStorageService.ALLOWED_FILE_TYPES["zip"]` includes `application/octet-stream` (`FileStorageService.java:62-66`).
- `validateMimeTypeCompatibility` passes when the Tika-detected MIME `startsWith` any allowed value for the extension (`FileStorageService.java:229-254`); the declared request Content-Type is never consulted for storage acceptance.
- Endpoint call sites feeding `saveFile`:
  - task attachment: `TaskServiceImpl.updateTask` → `TaskServiceImpl.java:137` (via `PATCH /api/v1/task/{id}`)
  - project attachment: `ProjectServiceImpl.updateProject` (via `PATCH /api/v1/project/{id}`)
  - task submission: `TaskSubmissionServiceImpl.submit` → `TaskSubmissionServiceImpl.java:126` (via `POST /api/v1/task-submission/{taskId}`)
  - project submission: `ProjectSubmissionServiceImpl.submit` (via `POST /api/v1/project/{projectId}/submissions`)

## 3. Test environment and isolation (mandatory)

- Harness: `src/test/java/com/example/gradproj/EduNest/testing/UploadZipEndpointHarness.java` (named outside default Surefire patterns so normal repo test runs can never trigger it).
- Runs: three disposable workspaces outside the repository, `C:\Users\Dell\AppData\Local\Temp\opencode\eb5-zip-endpoint-20261029-a` (screen run) and `...-b` (logged canonical run) with the original harness, and `...-c` (corrected canonical run, 2026-10-29, same day) with the corrected harness whose authorized probes cover both the genuine-ZIP control and the spoofed artifact per surface. Each workspace was a fresh copy of `.mvn`, `src`, `mvnw`, `mvnw.cmd`, `pom.xml` with a `.eb5-isolated-workspace` marker holding the workspace's own absolute path. Runs a and b produced identical verdicts; run c reproduced the same upload verdicts and added the corrected retrieval evidence (§6).
- Isolation guard: the harness refuses to boot unless (a) the working directory is under `…\Temp\opencode`, (b) the marker exists and self-describingly names the CWD, and (c) neither `uploads/` nor `target/test-uploads/` pre-exists. The repository working directory was never used for a run; each owned workspace was verified deleted after its observations were recorded here.
- Real Spring Boot context: H2 in-memory (MySQL mode, D-27 / P2-01 infra), test profile (`EDUNEST_UNSAFE_DEV` chain, `app.upload.base-dir=target/test-uploads`), `@AutoConfigureMockMvc` against the real security filter chain. Repository beans under test (`WeekRepository`, `MentorShipRepository`, `TaskRepository`, `TaskSubmissionRepository`, `EnrollmentRepository`, `ProjectRepository`, `ProjectSubmissionRepository`, `StudentRepository`, `MentorRepository`, `NotificationService`) are Mockito mocks; `FileStorageService`, `FileAccessService`, both upload service chains, all controllers, and the security chain are REAL.
- No Docker, no cloud services, no production databases or user files. All fixtures are synthetic and inert.

## 4. Endpoints under test (actual routes and multipart contracts)

| Surface | Method | Endpoint | Multipart field | Role gate (@PreAuthorize) | Storage destination convention |
|---|---|---|---|---|---|
| task-attachment | PATCH | `/api/v1/task/55` (`TaskController.patch`, `req` optional) | `file` | `hasRole('MENTOR')` | `task-attachment/task-{mentorshipId}-{mentorshipId}-{uuid}.{ext}` |
| project-attachment | PATCH | `/api/v1/project/21` (`ProjectController.patch`, `req` optional) | `file` | `hasRole('MENTOR')` | `project-attachment/project-{mentorshipId}-{mentorId}-{uuid}.{ext}` |
| task-submission | POST | `/api/v1/task-submission/11` | `file` (+optional `fileUrl`) | `hasRole('STUDENT')` | `submissions/task-{taskId}-{studentId}-{uuid}.{ext}` |
| project-submission | POST | `/api/v1/project/21/submissions` | `file` (+optional `fileUrl`) | `hasRole('STUDENT')` | `submissions/project-{projectId}-{studentId}-{uuid}.{ext}` |

Each endpoint request passed through the application's real `@PreAuthorize` method security with synthetic session-based principals (`ROLE_MENTOR` for attachment surfaces, `ROLE_STUDENT` for submission surfaces) and fixture-backed ownership preconditions (published task/project, enrolled student/mentor-owned mentorship).

## 5. Payload fixtures (synthetic)

| Code | Name | Filename | Declared MIME | Actual bytes | Tika detection (stream, as `FileStorageService.detectMimeType` sees it) | SHA-256 (measured on stored bytes) |
|---|---|---|---|---|---|---|
| Z | zip-valid | fixture.zip | application/zip | genuine ZIP (generated `ZipOutputStream`, one text entry; hashes vary slightly per run because ZIP stores timestamps) | application/zip | run b: `3ea2ba845043cddb1401d50c7b75ed9cafa67f549f8c69c7b10ddb2d1bae8f05` |
| Z' | zip-declared-octet | fixture.zip | application/octet-stream | same genuine ZIP | application/zip | as above |
| G | binary-as-zip-octet | fixture.zip | application/octet-stream | inert binary `00 01 02 03 04 05` | application/octet-stream | `17e88db187afd62c16e5debf3e6527cd006bc012bc90b51a810cd80c2d511f43` |
| G' | binary-as-zip-declared-zip | fixture.zip | application/zip | same binary | application/octet-stream | same as above |
| H | html-as-zip | fixture.zip | application/octet-stream | inert HTML | text/html | — (rejected, never stored) |
| X | blocked-extension | fixture.exe | application/octet-stream | same binary | application/octet-stream | — (rejected) |
| E | empty | fixture.zip | application/octet-stream | 0 bytes | — | — (rejected) |

Exactly the same non-ZIP fixture `G` (same SHA-256) that EB-4 confirmed accepted at the service boundary was reused here.

## 6. Results — run b (canonical; runs a and c independently reproducible, identical verdicts)

Legend: ACCEPTED = HTTP 2xx **and** response reports a stored path **and** exactly one new file appeared on disk with matching hash.

### task-attachment — `PATCH /api/v1/task/55`
| Payload | HTTP | Result | Stored path (workspace-relative) | Hash |
|---|---|---|---|---|
| zip-valid | 200 | ACCEPTED | `target/test-uploads/task-attachment/task-701-701-964c254d-….zip` | `3ea2ba84…` |
| zip-declared-octet | 200 | ACCEPTED | `…/task-701-701-5b4838ad-….zip` | `3ea2ba84…` |
| **binary-as-zip-octet** | **200** | **ACCEPTED (FAIL — evidence)** | `…/task-701-701-f6911cd2-….zip` | `17e88db1…` |
| **binary-as-zip-declared-zip** | **200** | **ACCEPTED (FAIL — evidence)** | `…/task-701-701-3ecf67dc-….zip` | `17e88db1…` |
| html-as-zip | 409 | REJECTED | none | — |
| blocked-extension | 409 | REJECTED | none | — |
| empty | 409 | REJECTED | none | — |

### project-attachment — `PATCH /api/v1/project/21`
| Payload | HTTP | Result |
|---|---|---|
| zip-valid | 200 | ACCEPTED (`…/project-attachment/project-701-101-fad9f5a6-….zip`) |
| zip-declared-octet | 200 | ACCEPTED |
| **binary-as-zip-octet** | **200** | **ACCEPTED (FAIL — evidence)** (`…/project-701-101-49521315-….zip`, hash `17e88db1…`) |
| **binary-as-zip-declared-zip** | **200** | **ACCEPTED (FAIL — evidence)** |
| html-as-zip / blocked-extension / empty | 409 ×3 | REJECTED |

### task-submission — `POST /api/v1/task-submission/11`
| Payload | HTTP | Result |
|---|---|---|
| zip-valid | 200 | ACCEPTED (`…/submissions/task-11-102-b9169d87-….zip`) |
| zip-declared-octet | 200 | ACCEPTED |
| **binary-as-zip-octet** | **200** | **ACCEPTED (FAIL — evidence)** (`…/submissions/task-11-102-be3c0545-….zip`, hash `17e88db1…`) |
| **binary-as-zip-declared-zip** | **200** | **ACCEPTED (FAIL — evidence)** |
| html-as-zip | 409 | REJECTED |
| blocked-extension | 409 | REJECTED |
| empty | 400 | REJECTED (business rule "Either file upload or file URL must be provided") |

### project-submission — `POST /api/v1/project/21/submissions`
Same pattern: zip-valid and zip-declared-octet ACCEPTED 200 (`…/submissions/project-21-102-….zip`); **binary-as-zip-octet and binary-as-zip-declared-zip ACCEPTED 200** (`hash 17e88db1…`); html-as-zip and blocked-extension REJECTED 409; empty REJECTED 400.

### Serving observations (retrievability of accepted artifacts — corrected run c)

Generic `GET /files?filePath=<stored>`: **200** for every accepted artifact in every run, `Content-Type: application/x-zip-compressed` (via `Files.probeContentType`), `Content-Disposition: attachment; filename="<stored name>"`, `X-Content-Type-Options: nosniff`, `Content-Security-Policy: default-src 'none'`. In run c the served bytes of **every** stored artifact were byte-compared against the stored file with `MessageDigest.isEqual` — **verified identical** (`fetchHashMatch=verified`) for all accepted payloads, including the spoofed instances.

Authorized endpoints (run c, `FileAccessService` ownership projections fixture-driven), **8 probes in total — two per surface**: one against the surface's genuine-ZIP **control** artifact (`kind=control`) and one against its first accepted **spoofed** artifact (`kind=spoof`, payload binary-as-zip-octet), each fetched by the artifact's owner identity (mentor for attachments, student for submissions). All 8 returned **HTTP 200**, `application/x-zip-compressed`, attachment disposition, and the returned bytes were SHA-256-compared against the stored artifact — **byte match verified** in all 8 cases:

| Surface | Authorized endpoint | Control (genuine ZIP) | Spoof (binary-as-zip-octet) |
|---|---|---|---|
| task-attachment | `GET /files/tasks/55/attachment` | 200, 163 bytes, hash verified | 200, **6 bytes** (= `00 01 02 03 04 05`), hash verified |
| project-attachment | `GET /files/projects/21/attachment` | 200, 163 bytes, hash verified | 200, **6 bytes**, hash verified |
| task-submission | `GET /files/task-submissions/91` | 200, 163 bytes, hash verified | 200, **6 bytes**, hash verified |
| project-submission | `GET /files/project-submissions/97` | 200, 163 bytes, hash verified | 200, **6 bytes**, hash verified |

The **6-byte** responses are decisive: the authorized endpoints returned exactly the original inert binary payload back to the owner, byte-for-byte — the spoofed artifact was genuinely stored, not merely path-recorded. (The 163-byte control is the genuine ZIP fixture of that run; run c's hashes matched its own stored artifacts because ZIP timestamps make genuine-ZIP hashes vary per run.)

### Harness integrity controls (what distinguishes wiring failure from validation verdicts)
- Per-surface synthetic control (zip-valid) asserted HTTP 200 + stored + single artifact **before** any verdict is treated as valid; all four surfaces passed the control.
- Rejection controls observed live: HTML-as-zip (Tika `text/html` → blocked MIME list) and `.exe` (extension blocklist/whitelist) were rejected through every endpoint — proving the harness records genuine rejections, not an "always accept" artifact.
- Response/disk agreement assertion: response-reported stored path was cross-checked against a pre/post diff of the storage root; every accepted request produced exactly one new file under the disposable root and every rejected request produced none.
- Storage containment assertion: every created file was verified to remain under `target/test-uploads` inside the disposable workspace.
- Run totals: 4 surfaces × 7 payloads = **28 upload observations**, **8 authorized retrieval probes (4 control + 4 spoof, run c)**, plus generic-reader GETs with byte-equality assertions on every stored artifact. `Tests run: 1, Failures: 0, Errors: 0` per run (a, b, and c). Rejection mapping observed: attachment surfaces return **HTTP 409** (generic `SecurityException`/`IllegalArgumentException` handler) and submissions return **409** for MIME/extension rejections and **400** (business rule) for the empty payload — the statuses are evidence, not asserted security guarantees.

### Accuracy correction (2026-10-29, pre-commit review)

An earlier draft of this report and the earlier harness version ran the dedicated authorized probes **only on the first accepted artifact of each surface** (in the a/b runs, that position was occupied by the genuine-ZIP control), then stated that spoofed artifacts were "retrievable via the dedicated authorized file endpoints." That claim extrapolated from the control artifact and was **not itself directly evidenced**. The harness was corrected before commit: authorized probes now run twice per surface (control + first accepted spoof) with SHA-256 byte comparison against the stored artifact, and run c was executed to produce the corrected evidence above. The upload-acceptance findings (the actual FILE-04 verdict) were never based on the extrapolated claim and are unchanged across runs a, b, and c.

## 7. Verdict

**FILE-04: CONFIRMED — endpoint level.**

Through all four real HTTP upload endpoints (task attachment, project attachment, task submission, project submission), an inert non-ZIP payload detected by Tika as `application/octet-stream` and named `*.zip` was:

1. **accepted** with HTTP 200;
2. **persisted** to real storage under the `.zip` naming convention;
3. **retrievable** through both the generic `/files?filePath=` reader and the dedicated authorized file endpoints (`/files/tasks/{id}/attachment`, `/files/projects/{id}/attachment`, `/files/task-submissions/{id}`, `/files/project-submissions/{id}`), served as `application/x-zip-compressed` with attachment disposition — with the returned bytes **SHA-256-verified in run c against the exact stored payload on every surface** (the spoofed artifacts were returned byte-for-byte as the 6-byte inert binary they were uploaded as; 8 authorized probes: 4 control + 4 spoof).

This upgrades EB-4's service-boundary finding to a full data-flow confirmation: the ZIP allowlist's `application/octet-stream` entry (`FileStorageService.java:62-66`) means an arbitrary binary with a `.zip` extension passes upload validation, is stored as a task/project submission artifact, and is downloadable. The declared request Content-Type is never enforced (payloads declared `application/zip` with octet-stream bytes behaved identically).

Scope of confirmation: local-disk `FileStorageService` upload flows as currently deployed. It does **not** include Supabase Storage mechanics (EB-83) or public/private policy (EB-28).

## 8. Test-environment limitations (uncertainty carried forward)

- The test profile boots the suppressive `EDUNEST_UNSAFE_DEV` security chain (permitAll URL rules); `@PreAuthorize` method gates were exercised with synthetic roles, but **real production authentication/authorization was not verified** and remains the province of the security-review process.
- Repository beans involved in enrollment/mentorship/task/project lookup were mocked with fixture ownership values; the fixtures are authorization **plumbing**, not evidence about production ownership correctness.
- `MockMultipartFile` and MockMvc do not prove servlet-container multipart limits, proxy behavior, chunked uploads, or transfer-level attacks.
- The generic `GET /files?filePath=` reader carries its own traversal/mime quirks (out of EB-5 scope; relate to EB-27/EB-28).
- Rejection HTTP codes (409/400) reflect the app's exception mapping, not a security policy statement.
- InvalidBinary zip-content attacks (ZIP bombs, nested jars, CRLF/format confusion) were not tested — FILE-04's scope is the allowlist entry itself.
- H2 was used; the test did not persist submission rows to a target database (persisted-path-authorization behavior is asserted at the file/service level only).

## 9. Handoffs

- **EB-29 (P1-22)** owns validation hardening: remove/replace the `application/octet-stream` entry of the ZIP allowlist and re-verify with this same harness pattern.
- **EB-28 (P1-21/FILE-01)** owns public/private exposure policy — untouched here.
- **EB-83 (ADD-03)** owns Supabase Storage mechanics — re-run this verification against the storage substitute before closing the P0 file-family rows.
- **EB-27 (P1-20)** owns the generic file endpoint guard — the generic `GET /files?filePath=` reader deserves its own hardening/verification.

Harness source: `src/test/java/com/example/gradproj/EduNest/testing/UploadZipEndpointHarness.java`.
Reproduction recipe (from a fresh owned disposable workspace only): write `.eb5-isolated-workspace` marker containing the workspace's absolute path, then run `.\mvnw.cmd -B test "-Dtest=UploadZipEndpointHarness" "-DskipTests=false" "-Dmaven.test.failure.ignore=false"` and collect the `EB5*` stdout lines. Never run from the repository working directory.
