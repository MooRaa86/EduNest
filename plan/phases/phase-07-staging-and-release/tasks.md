# Phase 07 — Tasks

Format canon: [`templates/TASK-TEMPLATE.md`](../../templates/TASK-TEMPLATE.md); anchors are short link targets (`tasks.md#p7-nn`).

<a id="p7-01"></a>
### P7-01 — Staging deploy: migrations + backup-restore rehearsal

**Priority:** P1 — the *first time* the full pipeline meets staging reality: migrations against real-shaped data plus backup-restore proof.  **Size:** M
**Owner role:** DevOps Engineer   **Reviewers:** Eng Lead
**Issues:** execution of P6-02/03/04/05/08 artifacts; DEP-08 drill seed   **Domain:** deployment / database
**Components:** pipeline deploy job, Flyway migration run on staging DB, backup-then-restore rehearsal on staging, smoke endpoint set

**Evidence:** Phase 6 artifacts; P3-07 rehearsal log.
**Expected outcome:** staging serves stable identity flows; migration run logs clean; restore procedure executes (backup → scratch → boot → verify) with artifact; deploy metadata (tag/commit) recorded.
**Rollback consideration:** restore staging from backup after rehearsal; document time-to-restore measured (fact, no promise).
**Depends on:** Phase 6 exit.  **Blocks:** P7-02..04.
**Tests:** rehearsal artifacts themselves; smoke script green.
**Docs:** runbook updates from what was learned (P6-09 files).

<a id="p7-02"></a>
### P7-02 — E2E regression on staging (business flows)

**Priority:** P1 — the six business suites (P2-04) re-run against staging real-services (real SMTP, real WS, real files), plus FE-applicable checks (A-6 assumption governs scope).  **Size:** L
**Owner role:** QA Engineer + Backend Developer   **Reviewers:** Eng Lead + PO
**Issues:** launch validation (production-readiness.md §staging)   **Domain:** testing / cross-domain
**Components:** scripted E2E (script or manual checklist — *either*, but evidence mandatory): registration→verify→login; mentorship create→publish→enroll→week/task/project; quiz lifecycle & late policy per D-08; grading→points→badges→certificates; chat/WS pair flows; notifications; upload policy behaviors (public/private per D-07)

**Evidence:** P2-04 suites as templates; staging URL; FE decision notes (A-6/P0-03).
**Expected outcome:** checklist artifact with pass/fail per flow incl. measured email delivery confirmation (OTP lands in a real inbox), WS over real TLS/file retrieval over real volume; FE-signoff note if FE exists.
**Depends on:** P7-01.  **Blocks:** P7-05.
**Tests:** the E2E checklist *is* the test; every check needs its artifact link.
**Docs:** checklist file under `ops/validation/staging-e2e.md` (new, retained for future releases).

<a id="p7-03"></a>
### P7-03 — Security validation on staging

**Priority:** P1 — re-run the authorization/regression matrix where it depends on a real environment (uploads policy, CORS, rate limits, error shapes), plus an external-eyes pass (automated scanner baseline; not a pentest claim).  **Size:** M
**Owner role:** Security Reviewer + QA   **Reviewers:** Eng Lead
**Issues:** launch security gate (B-table items of [`mds/deployment/production-readiness.md`](../../../mds/deployment/production-readiness.md))
**Domain:** authentication-security
**Components:** G-matrix probes (P2-03) against staging; OWASP ZAP or similar baseline scan; rate-limit load spot-check; secret-injection audit (no leakage to responses/logs)

**Evidence:** P2-03 suite; staging environment.
**Expected outcome:** every G-row denies on staging; scanner findings triaged (blocker/defer w/ decision); error shapes pass; no secret material in any response/log capture.
**Depends on:** P7-01.  **Blocks:** P7-05.
**Tests:** artifact = probe/scanner reports + triage note.
**Docs:** ops/validation/staging-security.md (new); carry findings into ISSUES if real.

<a id="p7-04"></a>
### P7-04 — Observability validation on staging

**Priority:** P2 — health endpoints, log shape, rate-limit budget visibility, email failure capture (P4-13), WS connection counts, file-storage space checks: everything P8 would need *actually working*.  **Size:** M
**Owner role:** DevOps Engineer   **Reviewers:** Eng Lead
**Issues:** DEP-02 operational check   **Domain:** deployment / testing
**Components:** actuator probes, log sampling vs pattern, budget-table presence,升降 of `failed_email` rows if a failure is injected, WS metrics none (documented), disk space probe

**Evidence:** P5-* artifacts; staging environment. (No monitoring stack yet — P8-01 owns stack setup; this task validates *app-side* signals.)
**Expected outcome:** each signal demonstrable with capture; gaps recorded as carry-overs (e.g., no dashboard yet — that's P8).
**Depends on:** P7-01.  **Blocks:** P7-05.
**Tests:** signal-capture artifacts.
**Docs:** ops/validation/staging-observability.md (new).

<a id="p7-05"></a>
### P7-05 — Production readiness review & go/no-go

**Priority:** P1 — the checklist gate; *must* be evidence-based; unresolved = blocked, not waived.  **Size:** S
**Owner role:** Eng Lead (facilitator)   **Reviewers:** PO + Security Reviewer + DevOps + QA (all required)
**Issues:** launch gate (production-readiness.md §exit + this plan §release criteria)   **Domain:** process
**Components:** go/no-go checklist (see [`acceptance-criteria.md` §4](acceptance-criteria.md)) walked live, sign-off record, blocker list

**Evidence:** P7-01..04 artifacts + Phase 1–5 sign-offs + [`ISSUE-MAPPING.md`](../../ISSUE-MAPPING.md) status column.
**Expected outcome:** all verifiable rows checked w/ evidence links; unmet rows listed as blockers w/ owners; explicit PO + technical-owner sign-off; approval recorded for production deploy.
**Depends on:** 02–04.  **Blocks:** P7-06.
**Tests:** none (gate).
**Docs:** signed checklist retained (ops/releases/…).

<a id="p7-06"></a>
### P7-06 — Production release execution (runbook)

**Priority:** P1 — the runbook *executed for real*: backups → migrations → deploy → verify → smoke → records.  **Size:** M
**Owner role:** DevOps Engineer + Eng Lead   **Reviewers:** PO witness (recorded)
**Issues:** release execution   **Domain:** deployment / release
**Components:** final `ops/runbooks/deploy-prod.md` (finalized from P6-09 draft + P7-01 lesson log), production secrets validated (P6-03 store), migration sequencing w/ backup immediately before, rollback triggers live

**Evidence:** P7-05 approval record; runbook files; P6-08 automation.
**Expected outcome:** executed runbook with per-step log linkage; release tag + deployment metadata recorded; rollback triggers defined *before* deploy (health >2min fail, error-rate breach, critical smoke fail) w/ owner roles; **database recovery limitation documented in-line** (rollback loses post-deploy writes — accepted before deploy).
**Rollback consideration:** previous-tag redeploy + DB restore from pre-deploy backup; procedure linked; time-to-restore measured at rehearsal.
**Depends on:** P7-05 (gate).  **Blocks:** P7-07/08.
**Tests:** the execution log is the evidence.
**Docs:** runbook final + release record (tag/date/approvers — factual, not planned).

<a id="p7-07"></a>
### P7-07 — Post-deployment production verification

**Priority:** P1 — within the release window: health, smoke, log sweep, WS email file checks — production version of P7-01 smoke but on the real deployment.  **Size:** S
**Owner role:** DevOps Engineer + QA   **Reviewers:** Eng Lead
**Issues:** post-deploy verification (P6-08 automation may automate parts)
**Domain:** deployment
**Components:** P6-08 verify job output + manual smoke set (login, enrollment, upload, WS, email)

**Evidence:** P6-08 automation.
**Expected outcome:** automated verdict green + manual spot checks with captures; any drift (config missed) → immediate rollback per P7-06 triggers.
**Depends on:** P7-06.  **Blocks:** P7-08.
**Tests:** artifacts.

<a id="p7-08"></a>
### P7-08 — Release communication & sign-off record

**Priority:** P2 — the team + stakeholders get a factual release note (what shipped, claims covered per P5-05), and the sign-off record completes.  **Size:** S
**Owner role:** Eng Lead   **Reviewers:** PO
**Issues:** release record; DOC-01 parity confirmation   **Domain:** process / documentation
**Components:** release note (from runbook record), sign-off table completion, known-issues list from [`ISSUE-MAPPING.md` Deferred/Blocked rows](../../ISSUE-MAPPING.md)

**Evidence:** P7-06/07 artifacts.
**Expected outcome:** published release note with known-issues section (honest); sign-off table complete in acceptance file.
**Depends on:** P7-07.  **Blocks:** —
**Tests:** none.  **Docs:** release record + books claims already corrected (P5-05).
