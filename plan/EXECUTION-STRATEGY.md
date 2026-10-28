# Execution Strategy

How the plan converts the audit into safe, reviewable, deferrable engineering work — written for implementers, not auditors.

## 1. Principles

1. **Stabilize, don't rebuild.** The Spring Boot 3.5.7 layered architecture (controllers → services → repositories, single JWT filter chain, in-memory STOMP broker, local-disk storage) is preserved. Tasks extend/patch existing classes cited in `mds/` docs.
2. **Fix closed to plan.** Every production-code change must trace to a task ID and (through it) to a canonical issue ID in `mds/ISSUES.md`. Drive-by fixes without a task are rejected in review.
3. **Tests ride with fixes.** A security P0 task is not Done unless a test fails without the fix (setUp reproduces the gap; then passes). Until CI exists (P2-07), merge gate = reviewer-run test command pasted in the PR.
4. **Verification before confirmation.** Suspected findings (⚠️ in `mds/ISSUES.md`) get Phase 0 verification tasks; results are recorded via the sync process (`TEAM-WORKFLOW §6`) — never assumed.
5. **Product decisions are minimized and time-boxed by dependency, not by date.** Where evidence mandates a default (documented per decision), work proceeds on the default; overrides re-open the task, not the phase.
6. **Backwards compatibility by default.** API-visible changes that the (separate) frontend consumes get a decision, a migration note, or a transitional flag — see each task's "Frontend impact" field.
7. **Everything reversible.** Migrations ship with before/after dumps + restore runbook (P3-01/07); deploys ship with rollback triggers (P7-06); each risky file op becomes transactional-afterCommit (P3-03).

## 2. Priority model

| Priority | Meaning | Rule of thumb | Count |
|---|---|---|---|
| **P0** | Exploitable security hole or correctness break; blocks any real-user traffic | Severity Critical, or High + trivially exploitable + no product dependency | 13 |
| **P1** | Required before a *safe* launch; or a decision-dependent item whose default is already adopted | High severity, or prerequisite task for the launch path | 50 |
| **P2** | Hardening, reliability, product correctness the platform should ship with, but not exploit-class | Medium severity, performance, DX | 32 |
| **P3** | Worth doing when convenient; deferred without embarrassment | Low severity/tech-debt with no launch impact | 2 |

**Severity → priority mapping exceptions (deliberate):**
- `CERT-01` (High, integration missing: PDF certificates) and `LS-01` (High, undocumented public room reality) are **P2/decision-gated** — feature-parity, not exploitable gaps. Mis-claiming them P0 would ignore the "launch blocker" definition (§ production-readiness B-table in [`mds/deployment/production-readiness.md`](../mds/deployment/production-readiness.md)).
- `DB-01` (High: `ddl-auto=update`) is **P1 not P0**: it is a slow-burn risk; fixing it **before** Phase 2 CI exists would ship an unauditable production schema change.
- `SEL-03` (Critical: no throttling) is delivered in two stages — minimal in-app lockout is **P0 (P1-08)**; the platform-wide limiter rollout is **P2 (P5-03)**. Splitting keeps the exploitable half fast.
- `TST-01` (High: no tests) is the CI phase's raison d'être — its *execution priority* is driven by the merge-gate need (P2-03/P2-07 = P0/P1), not by the issue's standalone severity.

**Why it matters:** every task's `tasks.md` entry states its priority; disagreements are resolved by reclassifying *once* in a review, then updating `ISSUE-MAPPING.md`.

## 3. Task granularity policy

- One task = one reviewable PR-sized unit (target: ≤ ~400 changed lines, excluding migrations/tests).
- Bundles exist only where components are inseparable (e.g., P1-13's three conversation IDORs share `ConversationService`; P1-26's hygiene items share nothing but "delete dead things").
- No nano-tasks: acceptance criteria annotate *sub-steps* inside a task instead of manufacturing task overhead.
- Any task exceeding L in estimate **must** show a decomposition path (e.g., P1-12 → interceptor + repository + service + tests listed as PR sequence).

## 4. Technical guardrails

1. **No new architecture:** no microservices, no message broker (the in-memory broker stays until D-22 scale needs appear — documented ceiling, [`mds/deployment/production-readiness.md`](../mds/deployment/production-readiness.md) ARCH-004).
2. **Dependency additions must be justified per task:** expected adds — `flyway-core` (P3-01), `spring-boot-starter-test` extensions + Testcontainers/`spring-boot-testcontainers` (P2-01), `spring-boot-starter-actuator` (P5-01), optionally `spring-boot-starter-oauth2-resource-server` *only if* D-01 picks refresh-tokens (P1-27). Rate limiting (P1-08/P5-03) ships **dependency-free** (fixed-window filter over `ConcurrentHashMap`) with a documented upgrade path (bucket4j) — decided deliberately to keep P0 footprint auditable.
3. **Database:** Flyway becomes the only schema authority in prod; entities may evolve only alongside a migration (P3-01 policy). No destructive migration without backup + rehearsal evidence (P3-07).
4. **OSIV:** stays ON until the fetch-join program (P4-11) completes and D-24 approves disabling; no "quick" `spring.jpa.open-in-view=false` flips (will fail read paths — [`mds/database/transactions-consistency.md`](../mds/database/transactions-consistency.md)).
5. **WebSocket:** authorization added *inside* `JwtChannelInterceptor`/services; the dead handshake interceptor stays dead unless D-14 produces a ticket-flow requirement (SEL-13 note).
6. **Files:** `uploads/` remains CWD-relative disk storage until P6-05/P6-07; the object-storage abstraction is additive, not a rewrite of `FileStorageService`.
7. **Java version/workflow:** branch from the latest `origin/main` and target protected `main` via PR; direct pushes are prohibited ([`TEAM-WORKFLOW.md` §3](TEAM-WORKFLOW.md#3-branching-and-pr-model)). Wrapper `mvnw.cmd` builds stay.

## 5. Estimation method

T-shirt sizes with explicitly recorded assumptions:

| Size | Meaning (assumption) |
|---|---|
| XS | 1 file, no API/contract change, simple tests |
| S | 1–3 files or 1 contract-visible change; unit + 1 integration test |
| M | Cross-class change (≥4 files) or state migration; multiple integration tests; PR review by 2 roles |
| L | Architectural touchpoint (security chain, WS layer, Flyway baseline, E2E suite); planned multi-PR landing; assumes test infra exists |
| XL | Reserved for decision-gated feature work only (e.g., PDF certificates if D-11 approves implementation; object storage migration) |

Anything estimated without knowing a required artifact (e.g., "database dump tooling availability") carries an assumption bullet; unknowns convert to a P0-phase verification task rather than inflating a later estimate.

## 6. Deferral policy

A task may be deferred **only** by one of:
1. A recorded decision in [`DECISIONS-REQUIRED.md`](DECISIONS-REQUIRED.md) marked *Deferred by decision*, or
2. A dependency that does not exist (e.g., no frontend repo contract channel) — recorded as `Blocked` with reason/owner/next-action.

Deferred Never-Means-Gone: each deferral row in [`ROADMAP.md` §5](ROADMAP.md#5-conditional-and-deferred-work-explicit-not-forgotten) states its re-open trigger.

## 7. Anti-goals (from the audit's failure patterns)

- No "big bang" security PR. The 10 P0 items in Phase 1 land as 10 PRs.
- No testless infra change: pipeline tasks (P6-04/08) include verification commands as acceptance criteria.
- No production deploy outside the P7-06 runbook; no "temporary" prod hotfix path bypassing CI (the hotfix lane defined in [P8-06](phases/phase-08-post-launch-operations/tasks.md#p8-06) still runs the full required checks).
- No documenting of unverified behavior as fact anywhere in `mds/` — verification results flow through the sync process with evidence links.
