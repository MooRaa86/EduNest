# Risks & Dependencies

## 1. Suspected findings pending verification (never silently confirmed)

| Finding | Issue | Evidence status | Verification task |
|---|---|---|---|
| ~~STOMP `String content` binding ambiguity~~ | CHAT-08 | ✅ RESOLVED by EB-1 (2026-10-29): DTO contract shipped — FE must send `{"content": "..."}` objects; raw strings rejected, nothing persists | P0-03 (closed) |
| Contact-reply HTML injection | SEL-15 | ⚠️ needs runtime POC | P0-04 |
| `LIKE '%kw%'` wildcard abuse | MSH-12 | ⚠️ needs runtime/load POC | P0-05 |
| Cover-image filename extension trust | FILE-03 | EB-4 confirmed arbitrary extension; traversal remained inconclusive ([matrix](phases/phase-00-planning-baseline/EB-4-UPLOAD-MATRIX.md)) | P0-06; hardening P1-22 |
| Zip MIME allowlist looseness (`octet-stream`) | FILE-04 | Confirmed on four upload surfaces in isolated EB-5 runs; see [verdict and limitations](phases/phase-00-planning-baseline/EB-5-ZIP-VERDICT.md) | P0-06/P0-07; hardening P1-22 |
| Question re-parenting ownership | QZ-06 | ⚠️ needs cross-mentor POC | P0-08 |
| Fresh-DB commission + first-admin boot behavior | MSH-05 | ⚠️ needs clean-DB rehearsal | P0-09 |
| WS transport/backend-header contract with FE | WS-01 (CHAT-08's payload half RESOLVED by EB-1: DTO contract shipped; raw JWT header quirk remains) | unknown FE client | Payload half closed; header half tracked in EB-12 |

These drive Phase 0 only; their tasks feed results into the sync process ([`TEAM-WORKFLOW §6`](TEAM-WORKFLOW.md#6-sync-rules-with-the-audit-registry)).

## 2. Top risks

| # | Risk | Affects | Likelihood/Impact | Mitigation |
|---|---|---|---|---|
| R1 | **Secret-rotation ordering bug**: provisioning a real `JWT_SECRET` (P6-03) *before* SEL-01 is fixed makes login/role-parse fail (hardcoded default in `LoginServiceI`) | P1-01 → P6-03 ordering | certain / severe-login-outage | Explicit dependency: P6-03 blocked by P1-01; runbook asserts order; test rotates a secret in CI (P2-03) |
| R2 | **Frontend integration unknowns**: no FE repo exists in this repository; upload-policy (D-07), answer-key (P1-18), error-shape (P1-05/D-26), token-body (P1-04) changes can break a deployed FE invisibly | P1-04/05/18/21, P1-20 | medium–high | Transitional flags where feasible (header+body during migration), FE-liaison channel verification in Phase 0 (P0-03), staging E2E with FE (P7-02) |
| R3 | **Flyway baseline drift**: entities edited during Phases 1–2 while baseline migration is captured → schema mismatch at validation time | P3-01 | medium / medium | Baseline capture happens *after* Phase 1 content-guard PRs merge; `ddl-auto=validate` CI smoke; freeze on entity edits between baseline & application |
| R4 | **WS semantics change breaks chat UX** (membership rule redesign) | P1-12 (D-14) | medium / high-feature | Decision D-14 with FE-ready default; staged rollout behind feature-agnostic behavior; WS integration tests P2-05 |
| R5 | **`@Transactional`/refactor regressions via OSIV reliance** (quiz/homepage reads) | P3-03, P4-03, P4-11 | medium / medium | No-OSIV test profile built first (P3-06 prep), fetch-join before logic moves, D-24 gates disabling |
| R6 | **Staging/dev/prod drift** (different MySQL minor, timezones, `ddl-auto` leftovers) | P6/P7 | medium / medium | Environments doc from P6-01 pins versions; config matrix reviewed at P7-05 gate |
| R7 | **Email deliverability** (sender domain reputation, rate caps on SMTP provider) breaks OTP flows at launch | P7-06/P8 | medium / high-user-blocking | Deliverability verification in staging (P7-02 includes OTP e-mail E2E), sender warm-up note in runbook |
| R8 | **Git history purge coordination** (D-19) momentarily breaks contributor clones | P6-06 | low / low | Announce window, clone-refresh instructions in report template |
| R9 | **In-memory broker/single-node ceiling** mis-measured post-launch (vertical scaling only) | P8 capacity checks | low / medium | Monitoring on WS sessions + broker lag; scale decision documented for post-launch phase |
| R10 | **"Documentation claims shipped" regression**: README/books diverge again after fixes | P5-05, P7-08 | medium / low | DOC-01 task updates claims at release time; release-notes checklist requires claims reconciliation |
| R11 | **Agora SDK (planning-phase discovery)**: unused dependency `io.agora:authentication:2.1.3` in `pom.xml`; unknown provenance/intent | P1-26, D-16 | low | Removal bundled into P1-26's dead-dep audit; D-16 records whether Agora was an abandoned alternative (verify with team before deleting if unsure) |
| R12 | **Rate-limiter minimalism under attack load** (in-app fixed window vs infra-level) | P1-08/P5-03 | low–medium | Documented upgrade path (bucket4j/gateway) at P5-03; monitoring alert on throttle saturation (P8-01) |
| R13 | **Postgres/Supabase migration risk** (added 2026-10-25): MySQL→Postgres dialect/type conversion (AUTO_INCREMENT→identity, case sensitivity, dialect SQL), Supabase pooler limits, and baseline drift if schema freeze (R3) is violated during migration | EB-81 → EB-82 → P3-01 chain | medium / high | Conversion dry-run on a staging copy first (EB-82 DoD); keep the MySQL dump as cutover fallback; `flyway validate` in CI; do not edit entities between freeze and baseline |

## 3. External dependency register

| Dependency | Needed by | Owner role | Notes |
|---|---|---|---|
| Frontend team / repository contract channel | P0-03, D-04/05/07, P1-05/16/18/21, P7-02 | PO (liaison) | Not in this repo; assumes separate FE codebase — **assumption A-6** |
| Supabase Postgres projects (staging + prod) + Supabase Storage buckets | EB-81 addendum, P6-02, P7-05 | PO + Infra | Replaces "MySQL provisioning" (superseded 2026-10-25); connection pooling (Supavisor), IPv4/IPv6 note, service keys via secret env |
| SMTP deliverable sender account | P7-02, launch | DevOps | MAIL_* env; DKIM/SPF alignment owned outside app |
| Container registry + CI runner availability | P2-07, P6-04 | DevOps | GitHub Actions assumed (repo is GitHub-hosted: `origin/.../github` paths observed) |
| DNS + TLS certificates | P6-02, P7-05 | DevOps | D-22 decides ownership |
| Backups tooling + offsite storage | P3-07, P6-02, P8-05 | DevOps | mysqldump + uploads rsync baseline accepted by default |
| Decision D-22 (hosting) | all infra | PO + Infra | **Single hardest external blocker** |
| Zoom account + Server-to-Server OAuth app (meeting creation / attendance read APIs) | P4-17 (Zoom re-scope), [ADD-05], P7-02 | PO + DevOps | D-16 outcome 2026-10-25: Zoom replaces Jitsi; credentials + API rate limits via env store |

**Dependency reconciliation (2026-10-29):** EB-81 requires provisioned Supabase staging and production projects; provisioning and access are not verified in this repository. EB-82 depends on EB-81 and the Phase 1 entity freeze; EB-43 additionally waits on EB-82 and a green CI/frozen baseline. EB-83 depends on the Supabase project and EB-81 key environment and must retain EB-29 validation. PO/Infra should confirm provisioning and access; the team should complete the dependency chain before migration/baseline work. These are delivery blockers, not evidence of an existing deployment.

## 4. Migration & rollback limitations (declared honestly)

1. **Schema rollback ≠ data recovery:** Flyway `undo` is not used; rollback = restore pre-migration dump + redeploy prior image version. Post-deploy-write loss is accepted & documented in the runbook ([P7-06](phases/phase-07-staging-and-release/tasks.md#p7-06)).
2. **Upload data rollback:** files are outside DB; restore = storage-level (volume/object-store snapshot). Until P6-05, container replacement loses uploads — documented interim risk (FILE-07).
3. **Rollback triggers** (defined at P7-06): health check failing >2 min, error-rate threshold breach, or critical smoke failure → automated runbook entry; manual call for data-related failures.
4. **Backward compatibility of app/schema:** migrations are additive first (unique constraints use dedupe_cleanup migrations); no column drops without a two-step pattern documented in P3-02.

## 5. Known technical ceilings (carried forward, not solved here)

- In-memory STOMP simple broker → single instance only (horizontal scale would require external broker — post-launch decision; ARCH-004).
- OSIV retained until D-24; performance program P4-11 is the prerequisite.
- `qty` of manual verification for infra tasks: despite CI, DNS/TLS/SMTP/backup *reality checks* remain human-verifiable per P7 acceptance checklists.

## 6. Planning assumptions register

| # | Assumption | Verification of assumption |
|---|---|---|
| A-1 | Team = small (≤ ~6 engineers across roles) | Workflow sized for it; ceremonies scale |
| A-2 | GitHub as git host | `origin/HEAD → origin/main` observed |
| A-3 | GitHub Actions available for CI | P2-07 verifies on first run |
| A-4 | ~~MySQL 8 backend is final~~ **SUPERSEDED 2026-10-25:** target DB = **Supabase (PostgreSQL)** — see D-22 outcome; migration via [ADD-01]/[ADD-02] (Jira EB-81/EB-82) must precede the Flyway baseline (P3-01) | Decision record in DECISIONS-REQUIRED.md |
| A-5 | The graduation books/README remain "root exceptions" (per repo conventions) | Prior documentation-phase decision; DOC-01 corrects claims at P5-05 |
| A-6 | Frontend exists as a separate codebase/team | **Unverified** — P0-03's first step is establishing the FE contract channel; if the app is frontend-less today, D-07 and related tasks adjust scope (documented on those tasks) |
| A-7 | No production users/traffic yet | Supported by absence of CI/infra; P7 gates real launch |
| A-8 | `io.agora` dependency is an abandoned experiment | 2026-10-25: **moot — D-16 chose Zoom**, so Agora was never a chosen path; removal confirmed for P1-26's dead-dep audit (no further team consultation required for the removal itself) |
| A-9 | Spring Boot devtools excluded from fat jars by default | Documented property of `spring-boot-maven-plugin`; re-verify at P6-05 image inspection |
| A-10 | ISSUES.md severity labels (audit) reflect *production* impact | Defended in audit; if team disagrees, disagreements resolve via priority mapping (§ EXECUTION-STRATEGY 2), not issue re-labeling |
