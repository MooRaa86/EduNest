# Phase 05 — Tasks

Format canon: [`templates/TASK-TEMPLATE.md`](../../templates/TASK-TEMPLATE.md); anchors are short link targets (`tasks.md#p5-nn`).

<a id="p5-01"></a>
### P5-01 — Actuator: health/readiness/info with minimal exposure

**Priority:** P1 — no health endpoints exist; the runbook (P7-06/07) and Docker healthchecks have nothing to query.  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** DevOps + Security Reviewer
**Issues:** DEP-02   **Domain:** deployment
**Components:** `pom.xml` (`spring-boot-starter-actuator`), `application-prod.properties` (exposure rules), chain rule for `/actuator/**`

**Evidence:** ISSUES DEP-02; [`mds/deployment/MDS.md`](../../../mds/deployment/MDS.md).
**Expected outcome:** `health` (probe-friendly) and `readiness`/`liveness` groups enabled; info minimal; everything else disabled or ADMIN-gated; health stays queryable anonymous (documented; no `show-details`).
**Approach:** minimal-exposure list (`health,info` only); DB/disk health indicators inherited; explicit docs note (what shows in health).
**Depends on:** —  **Blocks:** P6-05 (Docker healthcheck), P7-07 (post-deploy verification).
**Tests:** IT: `/actuator/health` 200 anonymous w/o details; restricted endpoints hidden; protected ones per chain.
**Docs:** deployment/MDS.md; ISSUE-MAPPING DEP-02.

<a id="p5-02"></a>
### P5-02 — Logging config: logback, MDC request IDs, println purge

**Priority:** P1 — no logback config, request tracing absent, `System.out` prints in interceptors (TD-05 remainder of P1-26).  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** DevOps + Eng Lead
**Issues:** DEP-02, TD-05   **Domain:** deployment
**Components:** `logback-spring.xml` (patterns, levels, rolling), MDC filter (request ID), `filters/*` + WS interceptor print cleanups

**Evidence:** ISSUES DEP-02/TD-05; [`mds/architecture/system-overview.md`](../../../mds/architecture/system-overview.md) filter-chain map.
**Expected outcome:** every prod log line carries timestamp/level/thread/requestId; exceptions log with requestId consumer coverage consistent w/ P1-05 error ref-id; files roll (size+retention properties); zero `System.out.println` in src (grep-proven).
**Approach:** start from standard Spring logback pattern (`traceId` = MDC); wire default filter `@Order(HIGHEST)`; keep pattern documented for P8-02 parsers.
**Depends on:** P1-05 (refId contract).  **Blocks:** P8-01/02 alerting/log centralization consumes shape.
**Tests:** IT: log capture asserts pattern contains requestId; grep assertion in test? (build-time check in CI script easier).
**Docs:** deployment/MDS.md; ISSUE-MAPPING DEP-02/TD-05.

<a id="p5-03"></a>
### P5-03 — Rate-limit rollout beyond auth surfaces

**Priority:** P2 — P1-08 covered auth/OTP; the public-but-unauthenticated endpoints that matter at scale now inherit the limiter (SEL-03's extension).  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** SEL-03 (second stage)   **Domain:** authentication-security
**Components:** `AuthRateLimitFilter` (renamed/generalized), route-specific budgets config, docs

**Evidence:** ISSUES SEL-03; [`mds/deployment/production-readiness.md`](../../../mds/deployment/production-readiness.md).
**Expected outcome:** explore/homepage/file endpoints carry sane defaults (documented table); limits disabled flag retained; monitoring hooks documented for P8-01 alerts.
**Approach:** extension of P1-08 filter with per-route config map; keep memory-bounded key eviction; upgrade path note (bucket4j) recorded in docs.
**Depends on:** P1-08.  **Blocks:** P8-01.
**Tests:** IT: budget tripping on public routes; disable flag respected.
**Docs:** security-configuration.md; ISSUE-MAPPING SEL-03.

<a id="p5-04"></a>
### P5-04 — Config/secrets inventory & `.env.example` [COMPLETED — PR #102 merged, EB-70 closed 2026-11-10]

**Priority:** P2 — Phase 6 infra needs the authoritative list of env vars, defaults, and secrets ("which value is secret?" — DEP-05/07 prep).  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** DevOps
**Issues:** DEP-05 (prep), DEP-07 (doc half)   **Domain:** deployment
**Components:** `.env.example` (all vars, no secrets — placeholders + descriptions), config matrix section in environment-configuration.md

**Evidence:** ISSUES DEP-05/07; [`mds/deployment/environment-configuration.md`](../../../mds/deployment/environment-configuration.md).
**Expected outcome:** single canonical var list (DB url/name/user/pass, JWT_SECRET, MAIL_*, APPUPLOAD base dir, CORS list, rate-limit toggle, commission default, unsafe-dev flag); each marked required/optional/dev-only-secret.
**Approach:** grep properties + `@Value`/`@ConfigurationProperties` sites; produce + cross-link doc; CI guard test that referenced vars exist in `.env.example` (simple shell test in CI).
**Depends on:** P1-01/02/23 (names settled).  **Blocks:** P6-01/03.
**Tests:** the guard test in CI.
**Docs:** environment-configuration.md §5 update; ISSUE-MAPPING DEP-07.

<a id="p5-05"></a>
### P5-05 — Docs parity & product claims corrections

**Priority:** P2 — the graduation books + root README claim PDF certificates, Agora, roadmap module & exact-7 features; release comms can't ship with known-false claims (DOC-01/02, TD-06/D-21, CERT-01/DOC via D-11, LS-01 doc outcome via D-16).  **Size:** M
**Owner role:** Eng Lead (docs)   **Reviewers:** PO
**Issues:** DOC-01, DOC-02, TD-06 (D-21), CERT-01 doc half (D-11), LS-01 doc half (D-16)
**Domain:** documentation / deployment
**Components:** root `README.md` + `FINAL_EDUNEST_DOCUMENTATION.md` + `EduNest_Project_Documentation*.md` claims sections, `mds/` linkage notes, roadmap skeleton deletion/keep

**Context:** Docs at root stay (documented exception, per audit convention) but their *claims* are corrected to observed reality; `mds/` remains the engineering truth. The historical default recommended deleting the roadmap skeleton; EB-71 found no `features/roadmap/**` files tracked on baseline `88736c1`, and does not attribute a deletion. Formal D-21 disposition remains pending.
**Evidence:** ISSUES DOC-01/02/TD-06/CERT-01/LS-01; [`mds/deployment/production-readiness.md`](../../../mds/deployment/production-readiness.md) claims list.
**Expected outcome:** every claim reconciled: implemented ↔ not-implemented (per decisions); no invented completion; roadmap module state per D-21; link to `mds/README.md` for engineering truth.
**Approach:** claims diff list (old→new), apply; keep edit small (tables where sensible); PO sign-off.
**Depends on:** D-11/D-16/D-21.  **Blocks:** P7-08 release notes; P7-05 checklist.
**Tests:** none (docs).
**Docs:** root books + mds docs; ISSUE-MAPPING rows updated.

**EB-71 work in progress:** [claim-by-claim evidence and remaining gaps](EB-71-CLAIMS-RECONCILIATION.md). D-11 and D-21 are not finalized; provider-side Zoom meeting authentication/passcode controls require separate review. Documentation reconciliation and PO sign-off remain pending.

<a id="p5-06"></a>
### P5-06 — Root Domain Gateway & Backend Architecture Showcase ('/')

**Priority:** P1 — root domain currently returns 404/Whitelabel; evaluators, recruiters, and clients need an informative gate to backend capabilities.  **Size:** M
**Owner role:** Tech Lead / Backend Architect (Omar Medhat)   **Reviewers:** Team Lead / Eng Reviewers
**Jira Issue:** EB-112   **Sprint:** EB Sprint 2 (`sprint-2`)
**Domain:** architecture / documentation / deployment
**Components:** `GatewayController.java`, `GatewayService.java`, `PublicRoutesPolicy.java`, `ProjectSecurityConfig.java`, `gateway.html` (pinned Mermaid.js)

**Evidence:** Jira [`EB-112`](https://mromarmedhat.atlassian.net/browse/EB-112); Task Blueprint [`task-gateway-root-path.md`](../../../brain/6dbd2042-22b4-4b9c-b6f7-d3e8c12d33d4/task-gateway-root-path.md).
**Expected outcome:** `GET /` serves an interactive, self-contained HTML showcase (for browser clients with pinned Mermaid.js visual architecture diagrams) and structured content-negotiated JSON (for API clients with `?audience=hr|business|technical`), backed by `/view`, `/diagrams`, `/status`, and `/api/v1/showcase/**` aliases. Read-only, pre-cached in memory (<15ms response), 0 DB writes, fail-closed security.
**Approach:** Dual-representation controller with in-memory caching and dynamic route reflection. Allowlisted in `PublicRoutesPolicy.java` as anonymous-public.
**Depends on:** P1-03 (PublicRoutesPolicy), P5-01 (health probe alignment).  **Blocks:** P7-07.
**Tests:** Unit (`GatewayServiceTest`), WebMvc integration (`GatewayControllerTest`), security probe (`GatewaySecurityProbeTest`).
**Docs:** root docs, Jira EB-112, ISSUE-MAPPING P5-06.

