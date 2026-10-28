# EB-4 / P0-06 — Upload verification evidence

**Status:** evidence collected locally; Security Review pending. No hardening or storage migration is claimed. **Run:** 2026-10-28, Windows, Java 23.0.2, H2 in-memory, test profile, Tika 2.9.2. Baseline: `origin/main` commit `f123006f0c930984f8e7f9efc0e46a9fa406d86b` at task-branch creation. Harness: `src/test/java/com/example/gradproj/EduNest/testing/UploadMatrixHarness.java`.

**Isolation:** the copied project and test JVM ran with actual working directory `C:\Users\Dell\AppData\Local\Temp\opencode\eb4-upload-matrix-20261028-b`. The harness checked its workspace marker and a nonexistent `uploads` root before Spring started and printed the canonical root. Image writes were below that workspace's `uploads/`; file-service writes were below `target/test-uploads/` there. The repository upload directory was not used. The two owned disposable workspaces were removed after recording the observations here; paths in this report identify the run, not currently available files. Re-run only in a fresh owned disposable workspace: the harness intentionally refuses normal repository execution and a preexisting upload root.

**Method and limits:** image requests went through MockMvc, actual controllers, actual service/ownership checks and actual image storage with synthetic principals and mocked repositories/entities. H2 booted but persistence and real database ownership were not tested. The test profile uses the unsafe development chain; this is not evidence of production authentication or authorization. Static GETs were made independently via MockMvc. Container multipart parser limits, proxy/browser behavior, traversal payloads, real replacement/deletion failure windows, and admin avatar are untested. `MockMultipartFile` does not prove the servlet multipart size limit. Attachment/ZIP comparisons call the actual `FileStorageService.saveFile` directly using each real destination convention, then GET `/files`; they are **not** actual task/project submission or attachment HTTP calls. Their HTTP upload result and ownership checks are inconclusive.

**Fixture codes:** `J` = generated valid 2x2 JPEG, SHA-256 `9551d6570ec0d4522b0f49cd1e6e5a4cd32c6a47176e51c8d9952ebdada6a330`; `H` = inert HTML bytes, SHA-256 `877edf1e0f16979c80f93a88f184dd872f6d53f1414d06dddb80ff99761ef0ae`; `G` = bytes `00 01 02 03 04 05`, SHA-256 `17e88db187afd62c16e5debf3e6527cd006bc012bc90b51a810cd80c2d511f43`; `Z` = generated genuine ZIP, SHA-256 `a6f1d4e831e5db99d8c8296d72f0e6d4779b5841473b07bc04c6343d63879ef8`. Hashes in the tables refer to measured stored bytes, not a separately hashed GET body. Empty fixtures wrote no file.

**Security verdict convention:** PASS means valid JPEG accepted or empty input rejected; FAIL means a misleading extension, mismatched MIME, HTML/garbage bytes or deceptive double extension was accepted under the conservative expected policy. For valid JPEG declared PNG, FAIL describes mismatch acceptance under that policy; accepting after verified normalization could also be a safe design, but no such normalization was observed. A passing harness means the observations completed, not that security expectations passed.

## Image matrix: four endpoints × seven cases

Endpoint key: cover = `POST /api/v1/mentorship/101/change-cover-image`; student = `PATCH /student/profile/image`; mentor = `PATCH /mentor/profile/image`; chat = `PUT /api/v1/chat-room/103/image`. For every stored row, upload HTTP **200**, file exists, a separate static GET returned **200**, `X-Content-Type-Options: nosniff`, and no `Content-Disposition` or CSP header was observed. Returned content type is listed below. Every empty case returned HTTP **400**, stored no file, and GET/headers/hash were not applicable. Paths below are relative to the disposable workspace; UUIDs are the actual run's filenames.

| Surface | Payload: filename / declared MIME / actual | Stored path | Stored hash | GET MIME | Verdict |
|---|---|---|---|---|---|
| cover | valid `photo.jpg` / image/jpeg / J | `uploads/mentorship-cover-images/101-EduNest-9a42f57f-a018-424a-8fed-4cb49252bb90.jpg` | J | image/jpeg | PASS |
| cover | wrong `photo.exe` / image/jpeg / J | `uploads/mentorship-cover-images/101-EduNest-8f51773f-99b3-4756-9817-50dd08985797.exe` | J | application/x-msdownload | FAIL |
| cover | spoof `photo.jpg` / image/png / J | `uploads/mentorship-cover-images/101-EduNest-6f5a288e-8eda-4fbd-afbe-0f84a30b198a.jpg` | J | image/jpeg | FAIL |
| cover | HTML `photo.jpg` / image/jpeg / H | `uploads/mentorship-cover-images/101-EduNest-481eee48-cd66-46e8-b436-88358e016752.jpg` | H | image/jpeg | FAIL |
| cover | double `photo.html.jpg` / image/jpeg / H | `uploads/mentorship-cover-images/101-EduNest-19e54bbc-536c-4972-be3f-ebe94b895f7f.jpg` | H | image/jpeg | FAIL |
| cover | garbage `photo.png` / image/png / G | `uploads/mentorship-cover-images/101-EduNest-628d710c-78be-4470-839c-f04e31008806.png` | G | image/png | FAIL |
| cover | empty `photo.jpg` / image/jpeg / empty | — | — | — | PASS |
| student | valid `photo.jpg` / image/jpeg / J | `uploads/student/102-EduNest-dd9fc4ba-3603-4c5b-90e6-463853e39e3b.jpg` | J | image/jpeg | PASS |
| student | wrong `photo.exe` / image/jpeg / J | `uploads/student/102-EduNest-34c65b42-0416-4c17-9adb-3010dcc71a2e.exe` | J | application/x-msdownload | FAIL |
| student | spoof `photo.jpg` / image/png / J | `uploads/student/102-EduNest-52c7a557-7280-41ca-99e8-e29b8510fe50.jpg` | J | image/jpeg | FAIL |
| student | HTML `photo.jpg` / image/jpeg / H | `uploads/student/102-EduNest-2287fb5f-d75c-488e-894e-209b0979058b.jpg` | H | image/jpeg | FAIL |
| student | double `photo.html.jpg` / image/jpeg / H | `uploads/student/102-EduNest-e1807034-3ed2-4dab-8dc7-94b820bdaf4a.jpg` | H | image/jpeg | FAIL |
| student | garbage `photo.png` / image/png / G | `uploads/student/102-EduNest-35188f70-35e5-488e-98ef-b9f26bae3765.png` | G | image/png | FAIL |
| student | empty `photo.jpg` / image/jpeg / empty | — | — | — | PASS |
| mentor | valid `photo.jpg` / image/jpeg / J | `uploads/mentor/101-EduNest-be36f46f-054e-414e-b013-6e16af4313f9.jpg` | J | image/jpeg | PASS |
| mentor | wrong `photo.exe` / image/jpeg / J | `uploads/mentor/101-EduNest-ac67734e-4d67-470c-8706-a229157a0edf.exe` | J | application/x-msdownload | FAIL |
| mentor | spoof `photo.jpg` / image/png / J | `uploads/mentor/101-EduNest-80ec9a6c-5d3e-4925-b0d3-bf77528369c3.jpg` | J | image/jpeg | FAIL |
| mentor | HTML `photo.jpg` / image/jpeg / H | `uploads/mentor/101-EduNest-a58943e4-8522-480a-be0a-dc1c894512e3.jpg` | H | image/jpeg | FAIL |
| mentor | double `photo.html.jpg` / image/jpeg / H | `uploads/mentor/101-EduNest-900f0b53-22a7-4686-a264-fd3b73e5bbd7.jpg` | H | image/jpeg | FAIL |
| mentor | garbage `photo.png` / image/png / G | `uploads/mentor/101-EduNest-0e5297fd-e4e8-451a-a635-d42ccc6cf29b.png` | G | image/png | FAIL |
| mentor | empty `photo.jpg` / image/jpeg / empty | — | — | — | PASS |
| chat | valid `photo.jpg` / image/jpeg / J | `uploads/chatroom/103-EduNest-b3898e34-0b05-4883-9e56-b657a00b7186.jpg` | J | image/jpeg | PASS |
| chat | wrong `photo.exe` / image/jpeg / J | `uploads/chatroom/103-EduNest-6719e560-2e16-42b3-a468-3ac2dd3c94f3.exe` | J | application/x-msdownload | FAIL |
| chat | spoof `photo.jpg` / image/png / J | `uploads/chatroom/103-EduNest-1a625230-6eed-4706-87e4-3e48af9eef3e.jpg` | J | image/jpeg | FAIL |
| chat | HTML `photo.jpg` / image/jpeg / H | `uploads/chatroom/103-EduNest-cf239afc-d659-4d6c-a88a-8f0d56f107d1.jpg` | H | image/jpeg | FAIL |
| chat | double `photo.html.jpg` / image/jpeg / H | `uploads/chatroom/103-EduNest-c01abd36-72f5-4edd-8207-87ca45d38f96.jpg` | H | image/jpeg | FAIL |
| chat | garbage `photo.png` / image/png / G | `uploads/chatroom/103-EduNest-2555efcf-eadf-4b18-9834-feab05852309.png` | G | image/png | FAIL |
| chat | empty `photo.jpg` / image/jpeg / empty | — | — | — | PASS |

## ZIP / attachment contrast — service-level, not HTTP upload

For each destination, the harness called `saveFile` directly; no upload HTTP status exists. Genuine `fixture.zip` declared `application/zip` was detected as `application/zip`, stored and fetched with GET `/files` 200 (**PASS**). The same ZIP named `fixture.pdf` declared `application/pdf` was detected as `application/zip` and rejected with `SecurityException`, no file/GET (**PASS**). Binary G named `fixture.zip`, declared `application/octet-stream`, was detected as `application/octet-stream`, stored and fetched (**FAIL** against the expected rejection of non-ZIP bytes). Accepted GETs returned `application/x-zip-compressed`, attachment disposition naming the stored file, `nosniff`, and `Content-Security-Policy: default-src 'none'`. All paths are sandbox-relative; the following hashes are measured on stored bytes.

| Destination | ZIP stored path / hash Z | Non-ZIP stored path / hash G |
|---|---|---|
| task submission convention | `target/test-uploads/submissions/task-10-20-478ddafa-0c4c-4d0b-9837-55560d9addf3.zip` | `target/test-uploads/submissions/task-10-20-d1af2b4e-ad3c-445e-bb7c-12f2672a704c.zip` |
| project submission convention | `target/test-uploads/submissions/project-10-20-1596b45b-0472-4bec-be1b-6dcc9036fadd.zip` | `target/test-uploads/submissions/project-10-20-af5bee43-9ec4-4c34-8104-1a481be34bd8.zip` |
| task attachment convention | `target/test-uploads/task-attachment/task-10-20-4392e505-d9de-4040-8728-480302276f82.zip` | `target/test-uploads/task-attachment/task-10-20-8c588890-3647-4e6c-9544-32611ee52f3a.zip` |
| project attachment convention | `target/test-uploads/project-attachment/project-10-20-e518fc83-e65d-4b1d-8938-255807dad967.zip` | `target/test-uploads/project-attachment/project-10-20-78274fcb-0dcf-4275-a8f4-de4e4329d51f.zip` |

## Finding verdicts and handoffs

- **FILE-02 CONFIRMED in this isolated run:** all four image endpoints accepted HTML/garbage under declared image MIME and stored the original bytes; static GET returned 200. No claim is made about browser execution.
- **MSH-06 CONFIRMED for header/extension/content validation:** cover accepted JPEG with `.exe` and HTML with `.jpg`; replacement failure-window behavior was not exercised.
- **FILE-03 PARTIAL / traversal INCONCLUSIVE:** arbitrary final extension accepted and raw suffix behavior is source-confirmed; separators/traversal were not executed. Do not mark traversal confirmed or refuted.
- **FILE-04 CONFIRMED at storage-service boundary:** non-ZIP G detected by Tika as `application/octet-stream` was accepted as ZIP in all four destination conventions. Task/project HTTP controllers and EB-5's focused verdict remain unverified.
- **EB-28:** public/private access policy is separate; unsafe test-chain GETs do not establish production authorization. **EB-29:** implement image/ZIP validation hardening and rerun; no application fixes were made here. **EB-5:** perform its separate endpoint-level ZIP spoof study.

## Commands and result

From the copied, marker-guarded disposable working directory: `.\mvnw.cmd "-Dtest=UploadMatrixHarness" test` — 1 harness test, 0 JUnit failures, 28 image observations + 12 service-level ZIP observations. Its FAIL verdicts are expected vulnerability observations, not infrastructure failures. `.\mvnw.cmd -B test "-DskipTests=false" "-Dmaven.test.failure.ignore=false"` — 14 existing tests passed, 0 skipped. The explicitly invoked harness is intentionally named outside default Surefire test patterns so a normal repository test run cannot write to the repository's `uploads/`; it is not counted in the 14. No Docker or cloud database was used.
