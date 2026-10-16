# Phase 08 — Tasks

Format canon: [`templates/TASK-TEMPLATE.md`](../../templates/TASK-TEMPLATE.md); anchors are short link targets (`tasks.md#p8-nn`).

<a id="p8-01"></a>
### P8-01 — Monitoring & alerting full coverage

**Priority:** P1 — without alerts, the first sign of the audit's failure classes is a user complaint.  **Size:** M
**Owner role:** DevOps Engineer   **Reviewers:** Eng Lead
**Issues:** DEP-02 full half; production-readiness monitoring rows   **Domain:** deployment
**Components:** metrics/monitor stack per D-22 (any: cloud metrics + dashboards acceptable), alert rules: auth-failure spikes (4xx on `/login-api`), 403 anomaly (IDOR probes gone wrong / sudden load), 5xx rate, health check failures, DB connection/disk, email failures (P4-13 rows), WS connection count drops, uploads disk capacity

**Evidence:** P5-01/02/03 artifacts; D-22 stack choice.
**Expected outcome:** each signal class has a rule + receiver; threshold values documented (facts from one week's baselines, not invented); alert test (deliberate trip) recorded once.
**Depends on:** entry criteria.  **Blocks:** P8-04 gate.
**Tests:** test-alert artifacts per class.
**Docs:** `ops/monitoring/alerts.md` (new); ISSUE-MAPPING DEP-02 closure.

<a id="p8-02"></a>
### P8-02 — Centralized logging + error tracking

**Priority:** P1 — logs exist (P5-02) but scatter across host; error tracking (e.g., Sentry compatible or chosen) gives the triage workflow its intake.  **Size:** M
**Owner role:** DevOps Engineer   **Reviewers:** Eng Lead
**Issues:** DEP-02 logging half   **Domain:** deployment
**Components:** log shipper (P5-02 pattern consumed), error-tracker SDK/config (accept env-injected DSN), retention policy per D-22 matrix

**Evidence:** P5-02 log shape; D-22 matrix.
**Expected outcome:** searchable store for prod logs w/ retention; error events appear with request IDs; severity defaults mapped.
**Depends on:** P5-02.  **Blocks:** P8-06.
**Tests:** error-event test with injected failure (artifacts).
**Docs:** ops/runbooks/log-search.md short guide.

<a id="p8-03"></a>
### P8-03 — Incident response plan + severities

**Priority:** P1 — "who does what when prod breaks" answered *before* it breaks.  **Size:** S
**Owner role:** Eng Lead   **Reviewers:** PO + DevOps
**Issues:** operations hardening rows   **Domain:** process / deployment
**Components:** `ops/incident-response.md` (severity defs SEV-1..4 w/ concrete criteria, response roles (rotation at assignment-time), escalation path incl. comms w/ PO, status-light market note, postmortem template)

**Evidence:** RISKS document failure classes; P7 artifacts.
**Expected outcome:** one-page plan covering all SEVs, roles unambiguous, escalation for SEV-1 includes comms expectations; template provable by one walkthrough exercise (recorded).
**Depends on:** —  **Blocks:** P8-06.
**Tests:** walkthrough artifact.
**Docs:** ops docs; linked from TEAM-WORKFLOW.

<a id="p8-04"></a>
### P8-04 — Observation period & stabilization gate

**Priority:** P2 — define the exit criteria for "we're stable" (duration: *not invented* — PO decides duration baseline with recorded fact-gathering criteria; the gate is fact-based regardless).  **Size:** S
**Owner role:** Eng Lead + PO   **Reviewers:** —
**Issues:** post-launch stabilization rows   **Domain:** process
**Components:** `ops/stabilization-gate.md` (criteria: no SEV-1/2 for ≥ observation period, alert noise rate agreed, error-tracker rate trend flat/down, first drill done, triage queue zeroed of launch residues)

**Evidence:** P8-01/02/05 artifacts.
**Expected outcome:** gate criteria list; when met (at any time), record exit with evidence; if not met, blockers w/ owners.
**Depends on:** P8-01/02.  **Blocks:** formal "post-launch" completion.
**Tests:** gate evidence review session.

<a id="p8-05"></a>
### P8-05 — Backup verification & recovery drill

**Priority:** P1 — a schedule that's never proven restores nothing.  **Size:** M
**Owner role:** DevOps Engineer   **Reviewers:** Eng Lead
**Issues:** DEP-08 (drill half)   **Domain:** deployment / database
**Components:** restore drill: full DB restore to scratch VM + uploads restore test + app boot + data verification queries; cadence doc + retention verification; "recovery time" measured each drill

**Evidence:** P6-02 backups; P3-07 rehearsal; P7-01 measured restore.
**Expected outcome:** drill executed + artifacts; per-drill measured time-to-restore recorded (no promises, facts); cadence + responsibilities set; failure-case (corrupt backup) handling documented.
**Depends on:** P6-02; P8-01 preferred (monitoring catch).  **Blocks:** P8-04 gate.
**Tests:** drill artifacts.
**Docs:** ops/runbooks/recovery-drill.md.

<a id="p8-06"></a>
### P8-06 — Post-launch triage & hotfix workflow

**Priority:** P1 — post-launch defect intake needs a fixed path; hotfix lane runs the *full* required checks (EXECUTION-STRATEGY §7).  **Size:** S
**Owner role:** Eng Lead + QA   **Reviewers:** PO
**Issues:** post-launch triage rows   **Domain:** process / testing
**Components:** `ops/runbooks/triage-hotfix.md`: intake sources (error tracker P8-02, user reports, alerts), severity→SLA-free priority mapping (roles not hours), new-ISSUES-row creation step (canonical registry law), hotfix branch rules (from `main`, full CI, merge-back)

**Evidence:** TEAM-WORKFLOW + ISSUE-MAPPING rules; EXECUTION-STRATEGY §7.
**Expected outcome:** documented workflow; first real triage exercise run (any defect → row → task) recorded.
**Depends on:** P8-02; P8-03.  **Blocks:** —
**Tests:** n/a (walkthrough artifact).
**Docs:** ops doc; ISSUE-MAPPING sync procedure reaffirmed.

<a id="p8-07"></a>
### P8-07 — Dependency & vulnerability management cadence

**Priority:** P2 — audits flagged outdated parents (Spring 3.5.x line) with known CVEs; cadence beats emergency patching.  **Size:** S
**Owner role:** Backend Developer + DevOps   **Reviewers:** Eng Lead
**Issues:** TST-04 (cadence half), security-maintenance rows   **Domain:** testing / deployment
**Components:** monthly cadence doc: OWASP report review (P2-08), Next/patch levels policy (within-major only), upgrade windows, emergency-CVE path (hotfix procedure reuse), baseline *registry check* (Spring Boot parent current minor)

**Evidence:** P2-08 scan; ISSUES DEP/TST rows.
**Expected outcome:** cadence doc + first review cycle run (findings → tasks/ISSUES rows).
**Depends on:** P2-08.  **Blocks:** —
**Tests:** first-cycle report artifact.

<a id="p8-08"></a>
### P8-08 — Operational maintenance calendar

**Priority:** P2 — recurring obligations (drills, reviews, capacity checks, log retention hygiene, cert/DNS renewals, cost review) on one calendar with owner roles.  **Size:** S
**Owner role:** DevOps Engineer   **Reviewers:** Eng Lead
**Issues:** ops hardening rows   **Domain:** deployment
**Components:** `ops/maintenance-calendar.md` (recurring items + owners roles + artifacts expected), D-25 revisit note (object storage), capacity watch (P8-01 feeds), log retention checks

**Evidence:** phases 6–8 artifacts.
**Expected outcome:** calendar with recurring items, each with clear artifact + owner role; first cycle scheduled (not dated here — recorded at assignment time).
**Depends on:** P8-05 calendar anchors.  **Blocks:** —
**Tests:** none.
**Docs:** ops doc; RISKS D-25 note cross-link.
