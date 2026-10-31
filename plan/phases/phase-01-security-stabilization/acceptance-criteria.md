# Phase 01 — Acceptance Criteria

## 1. Entry verification

- [ ] Phase 0 exit sign-off recorded (D-14/D-05/D-07 defaults + verification verdicts).
- [ ] Security-config steward named; workstream A owner confirmed.

## 2. Per-task acceptance

| Task | Core criterion | Evidence (execution-time) | Verified by |
|---|---|---|---|
| P1-01 | App fails to boot without `JWT_SECRET`; role parse works with rotated secret | startup log + unit/IT evidence | Security Reviewer |
| P1-02 | No `prod` → fail-fast unless explicit unsafe flag; permissive chain unavailable by default | boot test matrix (prod/none/unsafe) | Security Reviewer |
| P1-03 | Stale-token header no longer breaks public endpoints; secured endpoints still 401 | IT probe results | Backend Dev |
| P1-04 | Login returns header (body retained only while FE depends on it); ProviderManager erases credentials | contract test + code evidence | Backend Dev |
| P1-05 | Unexpected exceptions → 500 generic; 403 handler exists; three 401 shapes unified | error-contract IT set | Backend Dev |
| P1-06 | OTP verify scoped by type; cross-flow interference impossible (fixture test) | Local H2 results below; verification pending | Security Reviewer — pending |
| P1-07 | Reset: expired rows rejected; row consumed on success | Merged to `main` via PR #30, `52452a0`, 2026-10-29 (Team Lead-approved); Linux/JDK 21 CI 187/187, no skips ([run 40966140701](https://github.com/MooRaa86/EduNest/actions/runs/40966140701)); Jira EB-14 = Done. Target-DB verification is a pre-deployment follow-up | Backend Dev — merged |
| P1-08 | Thresholds lock out (403/429-style response); enumeration-neutral login errors | rate-limit IT + constants doc | Security Reviewer |
| P1-09 | `GET /api/v1/admin` has no password fields; register echoes nothing sensitive; bootstrap one-shot + audited text; last-admin delete guard; `deleteAdmin` transactional (pre-fixed bare 500) | `AdminBootstrapProbesTest` (7 probes) + full suite pending (branch `feat/p1-09-admin-bootstrap-protection`, EB-16) — alternative to `@PreAuthorize` restore accepted by Security Reviewer: route-level `hasRole("ADMIN")` already covers the list endpoint; the DTO field removal makes the annotation moot | Security Reviewer |
| P1-10 | Missing-role/ownership annotations present; createRoom validates mentorship ownership | role-matrix IT | Security Reviewer |
| P1-11 | Notifications `/send` and platform mail `/send` admin-only (or removed per decision); docs updated | 403 probe + endpoints.md diff | Eng Lead |
| P1-12 | Unauthorized SUBSCRIBE/SEND rejected for foreign rooms; membership semantics per D-14; dead WS code removed | WS IT suite (P2-05) run | Security Reviewer |
| P1-13 | History/edit blocked for non-participants; edit events carry server-derived recipient | WS/API IT | Security Reviewer |
| P1-14 | Lectures/tasks detail+filters gate on ownership/enrollment; DRAFT hidden inside them | matrix IT | Backend Dev |
| P1-15 | DRAFT invisible (404) for non-owner everywhere incl. public overview | anon/authed probes | Backend Dev |
| P1-16 | Leaderboard 403 for outsiders; public reviews contain no emails | captures | PO (privacy) |
| P1-17 | `/homepage/progress/{id}` 403 without enrollment | probe | Backend Dev |
| P1-18 | `correctAnswer` absent pre-submission; present only for correct mentor/student contexts | captures + FE note | Backend Dev |
| P1-19 | Foreign-quiz re-parent rejected; award listings scoped | cross-mentor IT | Security Reviewer |
| P1-20 | `GET /files?filePath=` ownership-checked; traversal still blocked | probes | Backend Dev |
| P1-21 | Anonymous fetch of `submissions/**` denied; public asset folders intact; FE note recorded | integration matrix | Security Reviewer |
| P1-22 | Magic-byte detection on image surfaces (JDK decode-backed); per-surface allowlists; zip central-directory parse; rejected uploads leave no file/`.tmp`/metadata remnants | `UploadValidationHardeningProbeTest` (28 probes) + full suite 221 green on PR branch ([PR #33](https://github.com/MooRaa86/EduNest/pull/33), merge + CI pending) | Security Reviewer |
| P1-23 | Unlisted origin denied (preflight, credentialed) | probe | Backend Dev |
| P1-24 | Injection payload renders escaped in email (or P0-04 refuted → task unneeded) | email capture | Backend Dev |
| P1-25 | Malformed login/verify/resend payloads → 400 field-aware | EB-32: `AuthRequestValidationTest` and OTP/rate-limit/login regression suites; CI, review and merge pending | Backend Dev — verification pending |
| P1-26 | JavaFX+Agora removed; unused code paths deleted; build green | diff + build log | Eng Lead |
| P1-27 (cond.) | Refresh/invalidation implemented per D-01 or formally carried with decision record | IT + decision link | Eng Lead |

## 3. Phase-level checks

**P1-06 / EB-13 local evidence (2026-10-28; implemented-await-verification):** Before the fix, `OtpTypeIsolationIntegrationTest` ran 9 tests with 4 failures (wrong-purpose registration/reset accepted and unrelated OTPs deleted by resends) and 1 error (untyped lookup returned multiple rows). After the fix and security-focused review, `mvnw.cmd "-Dtest=OtpTypeIsolationIntegrationTest" test` passed 24/24; `mvnw.cmd -B package "-DskipTests=false" "-Dmaven.test.failure.ignore=false"` passed 38/38, zero skipped. Test source: `src/test/java/com/example/gradproj/EduNest/testing/OtpTypeIsolationIntegrationTest.java`; local Surefire output is under `target/surefire-reports` and is not a durable CI artifact. The H2 test checks the account lock, not target-database concurrency behavior. Security Review, merge and verification remain pending; Jira EB-13 is Done. (Update 2026-10-30: CI and merge completed — see the merged EB-14 evidence context below; Security Review sign-off remains outstanding.) EB-14's caller-bound reset authorization was subsequently resolved by the merged one-time-credential implementation (PR #30, `52452a0` — see P1-07 row). `RESET_PENDING` target schema compatibility and security/deployment approval for legacy OTP reissue and mixed-version rollout/rollback are required before deployment.

- [ ] P2-03 authorization regression suite runs green on `main` (or, if suite lands after some tasks, each culprit's tests already green at merge).
- [ ] G-register closure: G1–G15 all confirmed closed (mapping note per row in `ISSUE-MAPPING.md`).
- [ ] `mds/` docs updated: `security-configuration.md`, `authorization-rules.md`, `error-responses.md`, `endpoints.md`, `file-storage/MDS.md`, `websocket/*` reflect new behavior (links on tasks).
- [ ] No P0 security issue remains open (`ISSUE-MAPPING.md` P0 rows verified or Deferred-by-decision w/ D-01 only as *additive* hardening — the exploitable core closed).
- [ ] FE-impacting tasks carry documented coordination notes (P1-04/05/18/21).

## 4. Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| Product Owner | — | — | — |
| Engineering Lead | — | — | — |
| Security Reviewer | — | — | — |
| QA Engineer | — | — | — |

## 5. Carry-overs

- SEL-13 (latent WS token-in-URL) — remains DEF by design (revisit only if D-14 re-opens the handshake).
- TD-01 naming refactor — deferred; not this phase.
- Any FE-dependent transitional flags (P1-04 body token, P1-21 dual-serving period) — schedule cleanup at P7/P8; each carries the decision/owner note.
