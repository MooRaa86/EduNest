# Decisions Required

26 open decisions. Each states the question, options, impact, blocked tasks, recommended default (only where the audit evidence supports one), and the latest reasonable decision point expressed **by task/phase dependency, never by calendar**.

> Security-adjacent defaults: work proceeds on the documented default without waiting, per [`EXECUTION-STRATEGY.md` §1.5](EXECUTION-STRATEGY.md#1-principles) — an override re-opens the mapped task but not the phase.

## Index

| ID | Decision | Owner role | Decide-by (latest) | Blocked work |
|---|---|---|---|---|
| D-01 | JWT session invalidation model | PO + Eng Lead | P1-27 unblocks | P1-27, P2-03 (test shape) |
| D-02 | DRAFT mentorship visibility | PO | Before P1-15 | P1-15 |
| D-03 | Confirm confinement of unowned content | PO | Before P1-14 (formality) | P1-14 |
| D-04 | Public-surface privacy (emails on reviews/leaderboards) | PO + FE liaison | Before P1-16 | P1-16 |
| D-05 | Disposition of open push/mail endpoints | PO | ✅ Decided 2026-10-25 (delete) | P1-11 (re-scoped: delete) |
| D-06 | Fate of generic `GET /files?filePath=` | Eng Lead + FE liaison | Before P1-20 | P1-20 |
| D-07 | Upload exposure policy & FE link changes | PO + FE liaison | ✅ Decided 2026-10-25 (Supabase Storage) | P1-21 (re-scoped), P7-02 |
| D-08 | Late-submission & quiz close policy | PO | Before P4-04 | P4-04 |
| D-09 | Points reconciliation & completion freeze rules | PO | Before P4-02 | P4-02 |
| D-10 | `passPoints`/unlock: implement or retire | PO | Before P4-15 (may be deferred) | P4-15 (conditional) |
| D-11 | Certificate PDF feature: implement or claim-update | PO | Before P5-05 (deferral allowed) | P5-05 content, optional feature |
| D-12 | Admin notification broadcast model | PO + Eng Lead | Before P4-01 slice | P4-01 |
| D-13 | Chat read receipts scope | PO (deferral allowed) | Before P4-07 | P4-07 (conditional) |
| D-14 | Chat membership semantics | PO + Eng Lead | ✅ Decided 2026-10-25 (fix approved) | P1-12 (unblocked) |
| D-15 | Live-session rules: attendance constants + auto-close | PO | Before P4-08 | P4-08 |
| D-16 | Live-video platform (Jitsi public vs self-host JWT vs Agora) | PO + Infra | ✅ Decided 2026-10-25 (Zoom) | P4-17 (re-scoped: Zoom integration) |
| D-17 | Fresh-deployment default commission rate | PO | Before P3-05 | P3-05 |
| D-18 | Soft-deleted users' visibility on public reads | PO | Before P3-04 | P3-04 |
| D-19 | Git history cleanup approval for committed uploads | Eng Lead + PO | Before P6-06 execution | P6-06 |
| D-20 | API envelope/path standardization scope | Eng Lead + FE liaison | Before P4-16 (deferral expected) | P4-16 (conditional) |
| D-21 | `features/roadmap/**` fate | PO | Before P5-05 | doc sub-item |
| D-22 | Hosting & environments (staging/prod target, DNS/TLS) | PO + Infra + Eng Lead | ✅ Decided 2026-10-25 (container + Supabase) | P6-02+ §all infra; P7-05+ (unblocked) |
| D-23 | CORS allowed-origins values | PO + FE liaison | Before P1-23 | P1-23 |
| D-24 | OSIV disable timing | Eng Lead | Before P3-06 (deferral OK) | P3-06, P4-11 |
| D-25 | Upload storage: volume vs object storage | PO + Infra | ✅ Decided 2026-10-25 (Supabase Storage S3) | P6-07 → superseded by [ADD-03] |
| D-26 | Error-body disclosure level | Eng Lead + FE liaison | Before P1-05 | P1-05 |
| D-07a | *(sub-decision inside D-07)* whether FE fetches public assets by URL or via authenticated endpoints | FE liaison | With D-07 | FE contract change |

### D-01 — JWT session invalidation model
- **Question:** What replaces the 7-day, non-revocable token (SEL-06)?
- **Options:** (a) short-lived access (≤30 min) + refresh endpoint at `/login-api/refresh`; (b) keep 7-day tokens + server-side `lastInvalidatedAt` checked by the validator filter; (c) status quo (rejected by audit).
- **Impact:** (a) best posture + biggest FE change (refresh flow); (b) smallest change, revokes on next request, slight DB cost per request; (c) cannot logout.
- **Blocked:** P1-27, P2-03.
- **Default:** (a) with a transitional period allowing 7-day tokens to be marked short (evidence: validator already re-checks DB per request — (b) is the low-risk fallback if FE integration stalls).
- **Owner:** PO + Eng Lead. **Decide-by:** before P1-27 (Phase 1).
- **Outcome (decided 2026-10-31, user-approved):** **(a) narrowed — stateless JWT stands for Sprint 1.** No refresh tokens and no server-side revocation model this sprint. Logout invalidation is purely client-side token erasure (the erasure contract already enforced by EB-11: credentials wiped post-auth, token served in `Authorization` header). **Refresh tokens + real session/revocation model move to the Sprint 2 backlog** — `P1-27` is formally **carried with this decision record** (per its acceptance criterion: "implemented per D-01 or formally carried with decision record"), so `EB-34` is unblocked to scope/codify, not to build refresh. Fallback if the audit or FE forces earlier revocation: option (b) (`lastInvalidatedAt` validator check) may be adopted only via a new decision line.

### D-02 — DRAFT mentorship visibility
- **Question:** Is DRAFT exposure acceptable "coming soon" behavior (MSH-01)?
- **Options:** (a) hard-restrict DRAFT to owner+ADMIN everywhere; (b) restricted detail + public teaser via overview; (c) keep public.
- **Impact:** (a) closes leak + breaks "browse drafts" UX if intended; (b) needs designed teaser surface; (c) unreleased pricing/content leaks (current behavior).
- **Default:** (a) — audit found no intentional surface for DRAFT browsing.
- **Decide-by:** before P1-15.

### D-03 — Confinement of unowned content (G4/G5/G6)
- **Question:** Confirm lectures/task-lists/details are for enrolled students + owning mentors only.
- **Options:** (a) enforce enrollment-based access (recommended); (b) allow logged-in preview (broad leak) — not recommended.
- **Default:** (a). Decide-by: before P1-14; formal confirmation only.
- **Outcome (decided 2026-10-31, user-approved):** **(a) confirmed.** Course content (lectures, task lists, details) is readable exclusively by: **(1) enrolled students of that course, (2) the owning mentor, (3) ADMIN.** Everyone else is denied. Confirmation covers preview surfaces too (no logged-in browsing of content). Unblocks `P1-14` (Jira **EB-21**, High — first security mover).

### D-04 — Public-surface privacy
- **Question:** Which fields stay public on reviews/leaderboards/public profiles (MSH-03/09, USR-02)? Emails exposed today.
- **Options:** (a) display names only, no emails/photos beyond needed; (b) pseudonyms; (c) status quo.
- **Impact:** (a) smallest code change + real privacy win; FE must render names.
- **Default:** (a). Decide-by: before P1-16.
- **Outcome (decided 2026-10-31, user-approved):** **(a) display names only.** Public surfaces (reviews, leaderboard, public profiles) expose the user's **display name and points only** — no emails, no phone, no other personal profile data. FE renders names instead of emails. Unblocks `P1-16` (Jira **EB-23**, Leaderboard Privacy).
- **Amendment — Ruler B (2026-10-31, Engineering Lead-approved): supersedes the leaderboard-row rule above for the leaderboard surface only.** The leaderboard is NOT public: it is accessible only to students enrolled in the mentorship, the owning mentor, and ADMIN. Because enrollment authorization already protects access, the approved student leaderboard row contract is `studentFullName`, `studentEmail`, `studentProfileImageUrl`, `rank`, `totalPoints`; `studentLevel` and badge fields remain excluded. Public reviews keep the earlier display-name-only rule (no reviewer email or profile image).

### D-05 — Open push/mail endpoints
- **Question:** `POST /api/v1/notifications/send` (any→any), `POST /admin/.../send-notification`, and public `POST /send` platform mailbox (SEL-10/NOTIF-01, MAIL-01) — admin-gate, restrict, or delete?
- **Options:** (a) admin-gate (keep tooling); (b) delete outright; (c) keep public (unacceptable).
- **Impact:** (a) preserves ops tooling + closes phishing vector; (b) cleanest but loses messaging utility.
- **Default:** (a). Decide-by: before P1-11.
- **Outcome (decided 2026-10-25, user-approved):** **(b)-shaped — DELETE.** The endpoints are test leftovers. Execution: inventory all test-only APIs, confirm with FE/ops that none are load-bearing, then delete with removal evidence. `P1-11` re-scoped from "gate" to "delete" (Jira EB-18); a follow-up addendum task **[ADD-04]** (Jira EB-84) covers the broader test-endpoint inventory. No admin-gate.

### D-06 — Generic file endpoint fate
- **Question:** Keep `GET /files?filePath=` guarded via `FileAccessService` allowlist, or retire it entirely (does FE depend on it)?
- **Default:** guard-first (retire when FE confirms non-use). Decide-by: before P1-20.
- **Outcome (decided 2026-10-31, user-approved):** **Guard-first confirmed as the standing rule.** The endpoint stays under the `FileAccessService` allowlist. The already-merged temporary protection counts as **partial progress, not task closure** — `P1-20` (Jira **EB-27**) remains open until FE confirms the endpoint is unused, at which point it is retired outright.

### D-07 — Upload exposure policy (P0-critical for FE)
- **Question:** Which `uploads/**` folders remain publicly URL-fetchable and which become authorization-checked (submissions must be private)?
- **Options:** (a) public: covers/profiles/chatroom images; private: submissions — served via ownership-checked `/files/**`; (b) everything authenticated (FE rework of asset loading); (c) status quo (public submissions — unacceptable).
- **Impact:** (a) moderate FE change for submission links only; (b) large FE change.
- **Default:** (a). Decide-by: before P1-21; FE confirmation documented on the task.
- **Outcome (decided 2026-10-25, user-approved):** uploads move to **Supabase Storage (S3)** with bucket security policies + server-side ownership enforcement. The security intent of (a) is preserved: public assets (covers/avatars/chatroom images) via bucket-URL policy; submissions private (owner + mentor) via signed URLs or an authenticated proxy. **[ADD-03]** (Jira EB-83) implements the storage integration; `P1-21` (EB-28) re-scoped to submission-access policy enforcement + FE contract note. Local-disk serving code is retired.

### D-08 — Late submissions & quiz window
- **Question:** Define late policy (LEARN-05): accept-with-flag? reject? penalty?; when do quizzes hard-close (LEARN-10)?
- **Default (evidence-backed):** reject late *first* submissions is harsh; accept-with-penalty config `late.penalty.perMinute` default adopt existing −1pt/min rule; quizzes auto-close via scheduler at `durationMinutes` + grace. Decide-by: before P4-04.

### D-09 — Points reconciliation & freeze rules
- **Question:** Can mentors edit task/project/quiz points after grading (LEARN-06)? Badge point edits after mentorship COMPLETED (CERT-04)? Bulk-update clamp policy (GAME-03)?
- **Options per axis:** allow+recalculate vs lock-after-first-grade; freeze badge edits on completed; clamp ≥0 always.
- **Default:** lock content points after any grade exists (regrade flows recompute explicitly); freeze badge edits on COMPLETED; clamp everywhere. Decide-by: before P4-02.

### D-10 — passPoints/unlock
- **Question:** Implement checkpoint unlock semantics (feature) or remove `passPoints` + fix claims?
- **Default:** defer as feature; remove the dead field's implication from claims (P6-05 docs) if unapproved. Decide-by: before P4-15.

### D-11 — Certificates PDF
- **Question:** Implement iText-rendered PDF certificates (feature) or correct README/books claims?
- **Impact:** implementation is XL (layout, file storage, email attach, FE download) vs doc-only.
- **Default:** correct claims now; revisit feature post-launch. Decide-by: before P5-05.

### D-12 — Admin notification broadcast
- **Question:** Admin pushes to newest admin only (NOTIF-04) — broadcast to all admins or a fixed role assignment?
- **Default:** broadcast to all `Roles.ADMIN` accounts. Decide-by: before P4-01.

### D-13 — Chat receipts
- **Question:** Build unread/read-stat model for chat (CHAT-07) in this cycle?
- **Default:** defer to post-launch unless FE sprint has room (clearly medium feature). Decide-by: before P4-07.

### D-14 — Chat membership semantics *(decide early — gates P0 task P1-12)*
- **Question:** Who may subscribe/post in `chat-room`: enrollment-proxy (any enrolled student) or explicit `chat_room_members` only?
- **Impact:** defines the interceptor rule + repository membership query; affects every cohort user.
- **Default:** membership = true members ∪ mentor-owner ∪ enrollment-proxy READ-only? — recommendation: **write: member or mentor-owner; read: member/mentor/enrolled (enrollment-proxy)**. Decide-by: before P1-12 (Phase 0 round).
- **Outcome (decided 2026-10-25, user-approved):** **FIX approved** (status quo rejected). WebSocket security is committed: room authorization + dead-code removal in `P1-12` (Jira EB-19), with access-control hardening hooks added to the WS path. Access rules follow the plan default above unless the team revises during implementation.

### D-15 — Live-session rules
- **Question:** Approve attendance math constants (verify ≥75%→pass, snapshot 5-min grid, +5 bonus, absence→0) and auto-close scheduler (LS-02/03)?
- **Default:** constants into config properties (values unchanged), scheduler closes ENDED-state after actualEnd + grace. Decide-by: before P4-08.

### D-16 — Live-video platform
- **Question:** Keep public `meet.jit.si` rooms (update docs), self-host Jitsi with JWT-gated rooms, or evaluate Agora SDK (dependency present but **unused** — planning-phase discovery, see [RISKS §6](RISKS-AND-DEPENDENCIES.md#6-planning-assumptions-register))?
- **Impact:** self-host is infra XL; keep-public is doc-truth work.
- **Default:** keep public + truthful docs this cycle. Decide-by: before P4-17.
- **Outcome (decided 2026-10-25, user-approved):** **new option (d) — adopt Zoom** for live sessions/meetings. Meetings are created/owned through the Zoom API (recommended: Server-to-Server OAuth app); access control uses Zoom-native require-authentication/passcode instead of hand-distributed public `meet.jit.si` URLs — narrowing LS-01's open-room exposure at the source. `P4-17` re-scoped to **[P4-17] Zoom meeting integration** (Jira EB-66); addendum **[ADD-05]** (Jira EB-85) provisions the Zoom app/credentials/env. Attendance flows (LS-02..05) unchanged. The unused Agora dependency (A-8/R11) is confirmed removable in P1-26's dead-dep audit.

### D-17 — Default commission rate
- **Question:** What value should a fresh deployment seed (MSH-05)? (Current: unset → enrollments fail until admin sets.)
- **Default:** seed 10% via migration, overridable by admins. Decide-by: before P3-05.

### D-18 — Soft-deleted users on public reads
- **Question:** Filter deleted users from leaderboards/reviews/chat participants (DB-03)?
- **Default:** filter on public surfaces; admin surfaces keep full visibility. Decide-by: before P3-04.

### D-19 — Git history cleanup
- **Question:** Approve history purge of committed `uploads/**` user files (FILE-06)?
- **Impact:** force-push coordination for all contributors; permissions.
- **Default:** yes (privacy). Decide-by: before P6-06 execution.

### D-20 — API envelope standardization
- **Question:** Unify `SimpleResponse` vs typed DTOs vs `PageResponse` (API-01) and normalize legacy path styles (API-03) now or later?
- **Default:** defer both (breaking-change cost vs zero user value mid-stabilization). Decide-by: before P4-16.

### D-21 — Roadmap module
- **Question:** `features/roadmap/**` empty skeleton — implement or delete (TD-06)?
- **Default:** delete the skeleton (dead code). Decide-by: before P5-05.

### D-22 — Hosting & environments *(earliest infra gate)*
- **Question:** Target platform for staging+prod (single VM + nginx? PaaS w/ managed MySQL? K8s?), domains, TLS provisioning, and DB hosting responsibility (MSH/DEP evidence: no compose/infra exists).
- **Impact:** decides P6-02..09 shape, backup tooling, monitoring stack, single-node constraints.
- **Default:** smallest viable: single VM + Docker Compose + managed backups, revisit scale later. Decide-by: before P6-01 (blocks critical path!).
- **Outcome (decided 2026-10-25, user-approved):** **(a)-superseded.** Target: application = container; database = **Supabase (PostgreSQL)**; file storage = **Supabase Storage (S3)** with bucket policies. Staging = same shape (separate Supabase project/keys). Consequences: assumption A-4 (MySQL 8 final) **superseded** — migration tasks **[ADD-01]** (adopt Postgres; Jira EB-81) and **[ADD-02]** (MySQL→Postgres schema/data migration; Jira EB-82) precede `P3-01`'s Flyway baseline, which must be captured on Postgres. Infra tasks P6-02/03/05 re-scoped accordingly (uploads volume no longer required; secrets split into Supabase-managed DB creds + container env for app secrets).

### D-23 — CORS origins
- **Question:** Which origins whitelist in prod (SEL-11)?
- **Default:** FE + admin origin(s) the team provides; credentials stays `true`, wildcard never combined with credentials. Decide-by: before P1-23.
- **Outcome (decided 2026-10-31, user-approved):** **Public — no origin allowlist for now** ("make it public, no problem"). Implementation note: auth travels in the `Authorization` header (post-EB-11, no cookies/credentials), so credentialed CORS is not required; wildcard (`*`) is permitted **without** `allowCredentials=true`. Safety note on record: the moment any cookie/session-based request appears, wildcard must be dropped and this decision revisited. Unblocks `P1-23` (Jira **EB-30**) with scope: permissive open policy, no allowlist.

### D-24 — OSIV disable timing
- **Question:** When to disable Open-Session-In-View (DB-06)?
- **Default:** after P4-11 fetch-join program completes + no-OSIV test profile achieves green. Decide-by: before P3-06.

### D-25 — Upload storage
- **Question:** Docker volume now, object storage now, or abstract-then-migrate (FILE-07/DEP-03)?
- **Default:** volume now + abstraction task post-launch. Decide-by: before P6-07.
- **Outcome (decided 2026-10-25, user-approved):** **object storage now** — Supabase Storage (S3) is the chosen design and `P6-07`'s condition is resolved; the task is superseded by **[ADD-03]** (Jira EB-83), which includes retention/deletion policy and migration of any existing files. A Docker volume for uploads is no longer needed (scope removed from `P6-05`).

### D-26 — Error-body disclosure
- **Question:** Confirm generic bodies for unexpected errors (SEL-14 fix) — FE impact on error UX?
- **Default:** generic 500 body + keep field-validation detail map. Decide-by: before P1-05.

### D-27 — Test DB engine (no-Docker policy)
- **Question:** Testcontainers MySQL or H2 for the test profile (P2-01)?
- **Default (plan original):** Testcontainers MySQL ("native SQL rules out H2" — rationale written before verification).
- **Outcome (decided 2026-10-28, user-approved):** **H2 in-memory only; Docker is NOT allowed** (team no-Docker policy per Omar). The blocking rationale was re-verified and found overstated: exactly **one** special query exists (`TotalPointsRepository` rank list, `ROW_NUMBER() OVER`), and H2 2.x supports it — proven by the EB-35 smoke probe. Mitigations already in place: H2 `MODE=MySQL` compatibility, the window-function smoke test, and the rank query gets real-data fixtures in P2-02; if some future corner case fights H2, fallback = rewrite that single query in portable JPQL (never reintroduce Docker). CI note: EB-41 needs no special runner because tests are fully self-contained.
- **Application to EB-7 (ruled 2026-10-31):** the EB-7 rehearsal request for an isolated Docker/MySQL environment is **denied** under this decision. The fresh-database rehearsal runs on **H2 in-memory (MySQL-compat mode)**: scripted schema recreate + seeded-dump reset between rehearsal steps; "old security behavior before the fix" is evidenced via git-history diffs (`before` refs checked out in the rehearsal run) + rehearsal logs, all inside `mvnw test`. No Docker, ever.
