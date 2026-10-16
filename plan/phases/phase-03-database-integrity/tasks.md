# Phase 03 — Tasks

Format canon: [`templates/TASK-TEMPLATE.md`](../../templates/TASK-TEMPLATE.md); anchors are short link targets (`tasks.md#p3-nn`).

<a id="p3-01"></a>
### P3-01 — Flyway baseline + retire `ddl-auto=update` + prod hygiene

**Priority:** P1 — `ddl-auto=update` in a prod profile is the single largest data-integrity risk that compounds silently (DB-01/DEP-04).  **Size:** L
**Owner role:** Backend Developer (with DevOps review)   **Reviewers:** Eng Lead + Security Reviewer (config)
**Issues:** DB-01 (+DB-02, DEP-04)   **Domain:** database
**Components:** `pom.xml` (`flyway-core`), `application-prod.properties`, `db/migration/V1__baseline.sql` (generated from validated schema), `script.sql` disposition

**Context:** Today entities define the schema implicitly; prod depends on Hibernate auto-DDL. The baseline migration replaces the *unknown* starting state with a known, diff-able one.
**Evidence:** ISSUES DB-01/DB-02; [`mds/database/MDS.md`](../../../mds/database/MDS.md), [`entity-relationships.md`](../../../mds/database/entity-relationships.md) (43-entity catalog).
**Expected outcome:** fresh V1 baseline mirrors current entity model (validated hibernate-export diff = zero); prod `spring.jpa.hibernate.ddl-auto=validate` + Flyway on; dev stays `update` for iteration (documented convention: schema changes ship as migrations first); `show-sql: false` in prod; `script.sql` either imported as reference or deleted (decision recorded).
**Approach:** export schema via `Hibernate` UUID export (test profile, in-memory MySQL container) → diff against squeezed prod dump → V1; wire `spring.flyway.*` (locations, baseline-on-migrate false since V1 covers); naming convention note `V<seq>__` with reservation table in migration README (TEAM-WORKFLOW guard).
**FE / API impact:** none.  **Environment access:** prod-shaped DB dump (staging copy when P6-02 exists; until then a seeded local copy).
**Depends on:** Phase 1 frozen entity set; CI green.  **Blocks:** P3-02, P3-07, P6-04 (migrations run in pipeline).
**Tests:** IT: context boots with migrations applied to fresh container (`validate` passes); property hygiene test (script asserts prod profile values).
**Acceptance criteria:**
- [ ] V1 diff = zero vs entity export.
- [ ] Boot test green on `validate`.
- [ ] `script.sql` disposition recorded.
**DoD:** convention documented in migration README.  **Docs:** database/MDS.md; ISSUE-MAPPING DB-01/02, DEP-04.

<a id="p3-02"></a>
### P3-02 — Uniqueness constraints with dedupe + conflict UX

**Priority:** P1 — three join tables admit duplicates today: enrollments (MSH-04), reviews (one-student-one-review), conversations (CHAT-05) — each a source of double-spend-like business confusion.  **Size:** L
**Owner role:** Backend Developer   **Reviewers:** Eng Lead + QA
**Issues:** DB-04 (+MSH-04, CHAT-05)   **Domain:** database
**Components:** `V2__dedupe_cleanup.sql`, `V3__unique_constraints.sql` (dedupe + DDL separated deliberately), service get-or-create hardening, `GlobalExceptionHandler` duplicate-key mapping (exists)

**Context:** Constraints require clean data first — dedupe migration picks deterministic winners (per-face rules documented in the migration file comments; winners: earliest row for enrollment/review, canonical ordered pair for conversation).
**Evidence:** ISSUES DB-04/MSH-04/CHAT-05; [`transactions-consistency.md`](../../../mds/database/transactions-consistency.md).
**Expected outcome:** unique indexes on the three tables; service create paths catch duplicate-key violations and translate to friendly 409 (existing handler maps "key name already exists" — verify key-name UX or alias joins); conversation getOrCreate handles the race (constraint violation → re-fetch existing row).
**Approach:** two migrations + post-constraint service touch-up; reconciliation script artifact printed via Flyway logs; add regression test for double-enroll race (two threads).
**FE / API impact:** double-enroll attempts now receive clear 409 (previously silent duplicates) — FE messaging already tolerates duplicates (verify with FE note, non-blocking).
**Depends on:** P3-01.  **Blocks:** P2-04 (enrollment row).
**Tests:** IT: duplicate-enroll rejection texts; concurrent creation race test; review/conversation duplicates impossible.
**Docs:** entity-relationships.md constraint matrix; ISSUE-MAPPING DB-04/MSH-04/CHAT-05.

<a id="p3-03"></a>
### P3-03 — Transactional coverage + file-op atomicity

**Priority:** P1 — deletes without `@Transactional` (week deletion), session-state changes not atomic, and file replace deletes old *before* DB commit (FILE-05: window of jeopardy).  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Eng Lead
**Issues:** DB-05 (+FILE-05)   **Domain:** database / file-storage
**Components:** `deleteWeek` path, `LiveSessionsService` state transitions, `FileStorageService`/`ImageStorageService` replace flows (upload-new → update-ref → after-commit delete-old)

**Evidence:** ISSUES DB-05/FILE-05; [`transactions-consistency.md §2`](../../../mds/database/transactions-consistency.md).
**Expected outcome:** multi-step mutations atomic; orphan-file risk reduced (old files removed only after DB success, via `TransactionSynchronizationManager` afterCommit hook); no partial states on failure.
**Approach:** annotate missing classes/paths, keep transaction scopes *minimal* (no new mega-transactions — related reads not wrapped); file-order swap pattern documented for reuse in P4-03.
**Depends on:** P3-01 (tests rely on baseline).  **Blocks:** P4-02/03 (patterns reused).
**Tests:** IT: rollback paths leave no orphan state (week delete mid-failure compile? simulate by injecting exception between files), file replace: failure keeps old reference + file.
**Docs:** transactions-consistency.md; ISSUE-MAPPING DB-05/FILE-05.

<a id="p3-04"></a>
### P3-04 — Soft-delete read policy

**Priority:** P2 — deleted users/mentorships still appear in some public reads while vanishing from others — inconsistency depends on the joined entity (DB-03, USR open Q).  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Eng Lead + PO
**Issues:** DB-03 (D-18 default: filter public surfaces)   **Domain:** database / users-profiles
**Components:** leaderboard/review/other public queries, repos with `deleted=false` predicates (or `@Where` where entity-wide desirable)

**Evidence:** ISSUES DB-03; [`entity-relationships.md` soft-delete section](../../../mds/database/entity-relationships.md).
**Expected outcome:** public read surfaces exclude soft-deleted users/mentorships (leaderboards, reviews, chat participant listing); admin surfaces unaffected; behavior documented per surface.
**Approach:** per-surface predicates (not entity `@Where` — avoids surprise filtering on admin joins); FE-visible only where UI could show ghosts (no contract change).
**Depends on:** D-18.  **Blocks:** —
**Tests:** IT per surface with a deleted-user fixture.
**Docs:** entity-relationships.md; ISSUE-MAPPING DB-03.

<a id="p3-05"></a>
### P3-05 — Commission default seeding

**Priority:** P2 — fresh DB has no commission row → everything enroll-based fails until an admin manually intervenes (MSH-05, P0-09 evidence).  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** DevOps + PO (value confirm)
**Issues:** MSH-05 (D-17 default: 10%)   **Domain:** database
**Components:** `V4__seed_commission.sql` (idempotent insert-if-absent w/ env-override property), service null-guard improvement

**Evidence:** ISSUES MSH-05; [`enrollment-and-pricing.md`](../../../mds/mentorship-flow/enrollment-and-pricing.md).
**Expected outcome:** fresh deployment enrolls without manual admin step; admin can override via panel as before; value documented.
**Approach:** migration `INSERT ... SELECT ... WHERE NOT EXISTS`; default rate via Flyway placeholder bound to `app.commission.default` env; service error message retained for true misconfigurations.
**Depends on:** P3-01; D-17.  **Blocks:** P6-09 (deployment docs mention).
**Tests:** fresh-container test enrolls successfully; override env respected.
**Docs:** database/MDS.md + environment-configuration.md; ISSUE-MAPPING MSH-05.

<a id="p3-06"></a>
### P3-06 — OSIV assessment & policy note

**Priority:** P3 — DB-06 is a *documented decision point*, not a quick fix; the fetch-join program is P4-11.  **Size:** S
**Owner role:** Eng Lead   **Reviewers:** Backend Lead
**Issues:** DB-06 (D-24 deferral default)   **Domain:** database
**Components:** `mds/database/transactions-consistency.md` (policy note), test-profile toggle plan, P4-11 acceptance hook

**Evidence:** ISSUES DB-06; [`mds/database/transactions-consistency.md`](../../../mds/database/transactions-consistency.md), [`mds/deployment/production-readiness.md`](../../../mds/deployment/production-readiness.md).
**Expected outcome:** a no-OSIV test profile *prepared* (not enabled) with list of known-affected reads; D-24 recorded: disable only after P4-11 green.
**Approach:** inventory of lazy usages relying on OSIV (grep + reading), categories into safe/to-fix/document; note appended to DB doc + linked from P4-11.
**Depends on:** —  **Blocks:** P4-11 (uses inventory).
**Tests:** none yet (the profile stays off).  **Docs:** database docs + ISSUE-MAPPING DB-06.

<a id="p3-07"></a>
### P3-07 — Migration rehearsal (apply → verify → rollback on copy)

**Priority:** P1 — migrations meeting production-shaped data for the first time must be *rehearsed*, not premiered (DEP-08's backup half is used here).  **Size:** M
**Owner role:** DevOps Engineer   **Reviewers:** Eng Lead + QA
**Issues:** DEP-08's rehearsal half; guards DB-01 (rollback-safe)   **Domain:** database / deployment
**Components:** rehearsal script (dump → restore copy → run migrations → smoke queries → restore-rollback), artifact log

**Evidence:** ISSUES DEP-08; [`mds/database/MDS.md`](../../../mds/database/MDS.md); RISKS §4 limits.
**Expected outcome:** a documented, repeatable rehearsal run: baseline + dedupe + constraints apply cleanly to a realistic dataset; rollback = restore pre-migration dump (procedure validated); leftover-backups file hygiene documented.
**Approach:** script under `ops/rehearsals/` (repo tooling for operators), run against local production copy; timing + issues logged; run again after P4 data-touching tasks (P7-01 full-depth).
**Depends on:** P3-01/02.  **Blocks:** P7-01 (consumes artifacts).
**Tests:** the rehearsal itself is the evidence; artifact log linked.
**Docs:** deployment/environment-configuration.md (backup commands); ISSUE-MAPPING DEP-08.
