# Phase 00 — Tasks

Format canon: [`templates/TASK-TEMPLATE.md`](../../templates/TASK-TEMPLATE.md). The `<a id="p0-nn">` anchors below each heading are short link targets (`tasks.md#p0-nn`).

<a id="p0-01"></a>
### P0-01 — Ratify working agreements & establish baseline facts

**Priority:** P1 — every later ceremony points at these agreements; skipping produces ad-hoc process conflicts mid-Phase-1.  **Size:** S
**Owner role:** Engineering Lead   **Reviewers:** Product Owner
**Issues:** — (working agreements; baseline fact-checks against assumption register A-1..A-8)   **Domain:** process/testing
**Components:** root `agreements` notes referenced from this plan (no source changes)

**Context:** The audit revealed a per-person-branch workflow and zero CI/agency-of-process. Tasks ahead assume structure ([`TEAM-WORKFLOW.md`](../../TEAM-WORKFLOW.md)) — it must be ratified by the same people who will follow it.
**Evidence:** [`TEAM-WORKFLOW.md §3`](../../TEAM-WORKFLOW.md#3-branching-and-pr-model) (observed `main`/`develop`/name-branch reality), [`RISKS §6`](../../RISKS-AND-DEPENDENCIES.md#6-planning-assumptions-register).
**Expected outcome:** every team member can state the status model, DoR/DoD, and branch naming without reading this plan.
**Approach:**
1. Walkthrough session; ratify or amend the workflow doc (amendments recorded in-plan, not silently).
2. Verify assumption A-6: identify the FE codebase/channel or record "no FE team exists yet — changes needing FE coordination are blocked posture".
3. Verify A-8 (Agora SDK intent — ask the contributor(s), record answer).
4. Assign the six workstream owners (roles only).
**Frontend / API impact:** none.  **Environment access:** git hosting admin (branch protection deferred to P2-07).  **Decisions:** none.
**Depends on:** plan acceptance.  **Blocks:** workstreams assignment; indirectly all Phase 1 scheduling.
**Tests / regressions:** n/a (process).
**Acceptance criteria:**
- [ ] Ratified workflow doc link recorded here.
- [ ] A-6 answer recorded (FE exists / channel defined / "none yet").
- [ ] A-8 answer recorded (Agora keep/remove intent).
- [ ] Workstream owners (roles) named.
**DoD:** this file updated with links; report posted.
**Docs:** no source files.

---

<a id="p0-02"></a>
### P0-02 — Decision workshop round 1

**Priority:** P1 — gates the critical path (D-14→P1-12 P0, D-05→P1-11 P0, D-07→P1-21 P0, D-01→P1-27, D-22→P6 entire).  **Size:** M — 5 decisions with real alternatives + impact analysis.
**Owner role:** Product Owner (facilitator)   **Reviewers:** Eng Lead, Security Reviewer (for D-05/D-07/D-14 defaults)
**Issues:** indirectly feeds SEL-06, SEL-10/NOTIF-01, MAIL-01, WS-01/TD-08, FILE-01 & infra cluster.   **Domain:** process
**Components:** none (decision record only).

**Context:** 26 decisions exist ([`DECISIONS-REQUIRED.md`](../../DECISIONS-REQUIRED.md)); security work proceeds on documented defaults, but these five either *shape* security implementation or unlock infra — resolving them early multiplies execution certainty.
**Evidence:** per-decision blocks (D-01, D-05, D-07, D-14, D-22).
**Expected outcome:** each of the five recorded as `Decided on option X` or `Default confirmed by PO (name)` with rationale.
**Approach:** facilitated review of the five blocks (~60 min total if prepared); record in the decision file; note any decision that *defaults-then-overrides* and what re-open triggers it.
**Frontend / API impact:** decisions may commit FE changes (D-07).  **Environment access:** none.  **Decisions:** D-01, D-05, D-07, D-14, D-22.
**Depends on:** P0-01.  **Blocks:** P1-11/12/21/27 scheduling; P6-02+.
**Tests:** n/a.
**Acceptance criteria:**
- [ ] Five decision rows updated with outcome + approver role.
- [ ] Any default-adopted decisions annotated "PO confirmed default" — otherwise they cannot unblock P0 tasks.
**DoD:** decision file updated with outcomes; affected task rows already reference the decision IDs (no separate mapping edits needed).
**Docs:** `DECISIONS-REQUIRED.md` only.

---

<a id="p0-03"></a>
### P0-03 — V-01: Frontend contract channel & STOMP payload binding (CHAT-08)

**Priority:** P1 — unblocks FE-sensitive security changes (P1-21 policy, WS semantics) and closes CHAT-08's ambiguity before chat fixes.  **Size:** S
**Owner role:** QA Engineer + FE liaison   **Reviewers:** WebSocket implementer (P1-12 owner)
**Issues:** CHAT-08 (⚠️ suspected); supports WS-01 coordination.   **Domain:** websocket
**Components:** `ChatWebSocketController`, running app + any FE chat client.

**Context:** `mds/websocket/messaging-flow.md` flags that `String content` STOMP binding may persist raw JSON as message text if an FE sends object payloads.
**Evidence:** [`mds/websocket/messaging-flow.md`](../../../mds/websocket/messaging-flow.md); CHAT-08 row in ISSUES.
**Expected outcome:** an evidence note — (a) FE sends plain strings (CHAT-08 stays Low/technically moot) or (b) FE sends objects (CHAT-08 confirmed; P1-06/P4-06 gains a contract fix).
**Approach:** connect with FE/lib client (SockJS + STOMP as documented), SEND to `/app/chat/{room}` with object payload, inspect persisted `ChatMessage.content`.
**FE impact:** contract clarification only.  **Environment:** dev instance.  **Decisions:** none.
**Depends on:** P0-01 (FE channel).  **Blocks:** P4-06 scope clarity.
**Tests:** n/a (observation task).  Deliverable: evidence note appended to the P4-06 task + ISSUE-MAPPING status.
**Acceptance criteria:**
- [x] PayloadBinding note recorded with request/response captures.
- [x] ISSUE-MAPPING row for CHAT-08 annotated.
**DoD:** per workflow + evidence link above.

**Evidence note (V-01, 2026-10-29) — CHAT-08 ⚠️ → mechanism CONFIRMED → FIXED in the same PR (Omar's decision):**

Probed with `src/test/java/com/example/gradproj/EduNest/websocket/probe/V01PayloadBindingProbeTest.java` (`@Tag("critical")`, prod-chain H2 run — drives the real inbound STOMP chain: `JwtChannelInterceptor` → conversion → `@MessageMapping("/chat/{roomId}")` → `ChatMessageService.saveMessage` → persistence).

**Before the fix (audit observation):** `String content` bound the STOMP body as an opaque string — plain text, JSON string literals, and even an object JSON `{"content":"..."}` were all persisted **verbatim** (quotes and JSON included). CHAT-08's suspected mechanism was CONFIRMED server-side; severity stayed Low (display fidelity — no injection vector at the binding layer).

**Decision (2026-10-29): do NOT wait for P4-06** — the contract fix shipped immediately in this PR: `@Payload ChatMessageRequest` (DTO: `content` field, `@NotBlank`) replaces the raw `String`; `ChatMessageService.saveMessage` rejects blank/missing content (`globalLogicEx` → sender's `/queue/errors`).

**After the fix (observed, same probe):**

| `SEND /app/chat/{roomId}` body | Result |
|---|---|
| `hello...` (plain text) | **rejected** — nothing persists |
| `"hello..."` (JSON string literal) | **rejected** — nothing persists |
| `{"content":"pure text from FE object"}` | **deserialized → PURE text persisted** |
| `{"content":"   "}` (blank) | **rejected** — service guard, nothing persists |

**FE impact (mandatory contract change):** room-chat SEND bodies must now be JSON objects `{"content": "..."}` (see `dto/chat/ChatMessageRequest`). Raw-string sends no longer persist anything. The API stays open for reply/attachment fields without re-breaking the contract.

---

<a id="p0-04"></a>
### P0-04 — V-02: Contact-reply HTML-injection POC (SEL-15)

**Priority:** P1 — turns a suspected medium into fact before P1-24 sizes blind.  **Size:** S
**Owner role:** Security Reviewer   **Reviewers:** —
**Issues:** SEL-15 (⚠️).   **Domain:** authentication-security / notifications
**Components:** `ContactMessageService` (admin-reply template merge), SMTP sink (mailhog or sentinel inbox).

**Context:** `ContactMessageService:111` merges unescaped values into `admin-reply.html` (contrast badge flow's `HtmlUtils`); if fields carry `<script>`, injected email HTML executes in mail clients.
**Evidence:** ISSUES SEL-15; [`mds/notifications/MDS.md`](../../../mds/notifications/MDS.md) template-usage map.
**Expected outcome:** evidence note — reproducible injection or refutation.
**Approach:** craft contact message with `<img src=x onerror=...>`-style payload; trigger admin reply; inspect received HTML.
**FE impact:** none.  **Environment:** dev instance + disposable inbox.  **Decisions:** none.
**Depends on:** dev instance.  **Blocks:** P1-24 approach.
**Acceptance criteria:** evidence note + ISSUE-MAPPING SEL-15 annotation (confirmed/refuted).
**DoD:** as above.

---

<a id="p0-05"></a>
### P0-05 — V-03: Explore keyword wildcard POC (MSH-12)

**Priority:** P1 — informs whether the perf fix is escape-only (S) or needs full-text approach ahead of scale.  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** —
**Issues:** MSH-12 (⚠️).   **Domain:** learning / mentorship-flow
**Components:** `GET /api/v1/mentorship/explore`, `MentorShipRepository.searchMentorShips`.

**Context:** keyword flows to a `LIKE '%…%'` filter without escaping; `%`/`_` characters make every row a match (cost) and alter semantics.
**Evidence:** ISSUES MSH-12; [`mds/mentorship-flow/explore-and-homepage.md`](../../../mds/mentorship-flow/explore-and-homepage.md).
**Expected outcome:** measured row-scan effect + a canonical abusive-payload list to escape in P4-10.
**Approach:** explore with `%`-only and `_`-only keywords on a seeded dev DB; note result-set sizes/latencies.
**FE impact:** none.  **Environment:** dev DB.  **Decisions:** none.
**Depends on:** —.  **Blocks:** P4-10 sizing.
**Acceptance criteria:** evidence note + mapping annotation.

---

<a id="p0-06"></a>
### P0-06 — V-04: Upload type/extension runtime matrix (FILE-02/03/04, MSH-06)

**Priority:** P1 — P1-22's design directly consumes this matrix.  **Size:** M (multi-surface: cover image, student/mentor avatar, chatroom image; extension edge cases)
**Owner role:** Security Reviewer + Backend Developer   **Reviewers:** —
**Issues:** FILE-02, FILE-03 (⚠️), FILE-04 (⚠️), MSH-06.   **Domain:** file-storage
**Components:** `ImageStorageService`, `FileStorageService` (contrast), all image endpoints.

**Context:** image upload paths trust the `Content-Type` header (no Tika, no magic bytes); cover extension is taken from the multipart filename.
**Evidence:** [`mds/file-storage/MDS.md`](../../../mds/file-storage/MDS.md); ISSUES rows.
**Expected outcome:** a test matrix artifact: surface × payload (valid, mislabeled jpg→html, double-extension, zip-as-octet-stream) × stored result × served-content behavior.
**Approach:** scripted curl/REST runs against dev; catalog each outcome; include zero-byte/garbage frames.
**FE impact:** none.  **Environment:** dev.  **Decisions:** none.
**Depends on:** —.  **Blocks:** P1-22.
**Acceptance criteria:**
- [ ] Matrix artifact §, all 4 surfaces × ≥5 payloads.
- [ ] CONFIRM/REFUTE verdicts for FILE-02, FILE-03, FILE-04, MSH-06 rows in mapping.
**DoD:** matrix committed as evaluation record (under `plan/phases/phase-00/` evaluation note or issue-mapping annotation).

---

<a id="p0-07"></a>
### P0-07 — V-05: Zip allowlist looseness test (FILE-04)

**Priority:** P1 — small scoped follow-up to P0-06 (kept separate so its refutation doesn't reopen the matrix).  **Size:** XS
**Owner role:** Security Reviewer   **Reviewers:** —
**Issues:** FILE-04 (⚠️).   **Domain:** file-storage
**Components:** `FileStorageService` MIME map; task/project submit endpoints.
**Context:** Tika match loosens for `application/octet-stream` zip; extension spoof may pass.
**Evidence:** ISSUES FILE-04; contrast code path in [`mds/file-storage/MDS.md`](../../../mds/file-storage/MDS.md).
**Expected outcome:** verdict + stored/serve behavior on zip-spoof vectors.
**Approach:** single-purpose test cases; reuse matrix harness.
**Acceptance criteria:** evidence + mapping annotation.  **Depends on:** P0-06 harness.

**Reconciliation (2026-10-29):** Evidence and mapping annotation merged via [PR #22](https://github.com/MooRaa86/EduNest/pull/22), `626bb889461ea0b447c758720025811da39668eb`; [JDK 21 CI](https://github.com/MooRaa86/EduNest/actions/runs/40872915701) passed. [The verdict](EB-5-ZIP-VERDICT.md) confirms the spoof in isolated runs, with production-authorization limitations. Jira EB-5 remains **In Progress**: its handoff requires Security Reviewer acceptance and EB-29 hardening re-verification before closure. FILE-04 is not fixed by this verification.

---

<a id="p0-08"></a>
### P0-08 — V-06: Question re-parent cross-mentor POC (QZ-06)

**Priority:** P1 — small but security-relevant (QZ-06 ⚠️ suspected IDOR-adjacent).  **Size:** S
**Owner role:** Security Reviewer   **Reviewers:** —
**Issues:** QZ-06; relates to G15.   **Domain:** learning
**Components:** `QuestionServiceImp#updateQuestion`, two mentor accounts + their quizzes (fixture).
**Context:** `updateQuestion` validates the *source* question's ownership only; payload may reassign `quiz` to another mentor's quiz.
**Evidence:** [`mds/learning-content/quizzes-workflow.md`](../../../mds/learning-content/quizzes-workflow.md); ISSUES QZ-06.
**Expected outcome:** confirmation POC: mentor B moves own question into mentor A's quiz (or refutation).
**Approach:** two-mentor fixture; PATCH question with foreign `quizId`; assert result.
**Acceptance criteria:** verdict note + mapping annotation.  **Depends on:** —.  **Blocks:** P1-19 exact-scope.

---

<a id="p0-09"></a>
### P0-09 — V-08: Fresh-DB deployment rehearsal (MSH-05 + boot evidence)

**Priority:** P1 — validates two assumptions the plan depends on (commission seeding need; fail-fast visibility).  **Size:** S
**Owner role:** DevOps Engineer + Backend Developer   **Reviewers:** —
**Issues:** MSH-05; supports SEL-02 (fail-fast design input); AGORA-check assist for A-8/R11.
**Domain:** deployment/database.   **Components:** empty-schemed local MySQL, app boot, `POST /api/v1/admin/register`, enrollment attempt.
**Context:** Fresh deployments fail enrollment with `Commission rate not set` ([ISSUES MSH-05]); also validates that today's boot has *no* profile/secret guard — the pain P1-02 fixes (before-fix evidence).
**Evidence:** [`mds/mentorship-flow/enrollment-and-pricing.md`](../../../mds/mentorship-flow/enrollment-and-pricing.md); ISSUES MSH-05.
**Expected outcome:** rehearsal log: boot w/o `prod` (permissive chain — observed, secure-none), boot w/ prod, join attempt → error text, admin register OK.
**Approach:** disposable dockerized MySQL + empty DB; scripted calls.
**Acceptance criteria:** log artifact + mapping annotations (MSH-05 confirmed; SEL-02 "before" evidence captured for P1-02's regression proof).  **Depends on:** docker locally.  **Blocks:** P3-05 sizing certainty.
