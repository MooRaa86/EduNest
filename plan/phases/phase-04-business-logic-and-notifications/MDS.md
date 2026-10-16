# Phase 04 — Business Logic, Transactions & Notifications

## 1. Objective

Make the *product behave as its rules claim*: points that can't be silently inflated or lost, notification pushes that only fire on committed work, statistics that match the data, and the performance/scale debt the audit measured — all while honoring the reserved decisions (D-08..D-13) whose defaults are confirmed in Phase 0.

## 2. Scope

- **In scope:** notification pipeline timing (NOTIF-02/03/04), completion flow (MSH-10), points ledger & game integrity (GAME-03/04/05, LEARN-06, CERT-04), submission/quiz policy (LEARN-05/10), stats/projections (LEARN-04/07/09, USR-03, CERT-02/03), chat reliability (CHAT-01/06/08), receipts (CHAT-07, conditional), live sessions (LS-02/03/05, LS-01 conditional JW), API ergonomics (API-02/05, WS-03, USR-01/04), explore/homepage perf (MSH-08/12/07), fetch-join program (DB-06 prep, MSH-08 deep), batching (DB-07, LS-04), email observability (INT-02), conditional feature work (LEARN-08, CERT-01 via docs, API-01/03, Jitsi JWT).
- **Out of scope:** anything the DB layer now owns (Phase 3 closed transactional primitives), release/infra (Phases 5–6).

## 3. Entry criteria

1. Phases 2–3 exits: CI green, constraints live (points/index fixes assume them).
2. Decision set D-08..D-15 resolved or defaults PO-confirmed (P0-02 covered the gating five; this phase's gates must also be closed by its own task list).
3. P2-02/04 suites in place to absorb behavior changes (tests get updated *with* fixes, one PR each).

## 4. Task index

| Task | Title | Pri | Size | Decision gate |
|---|---|---|---|---|
| [P4-01](tasks.md#p4-01) | Notification pipeline (afterCommit, guests, broadcast, cleanup) | P1 | M | D-12 |
| [P4-02](tasks.md#p4-02) | Points ledger consistency & game integrity | P1 | L | D-09 |
| [P4-03](tasks.md#p4-03) | Completion flow restructure | P1 | M | — |
| [P4-04](tasks.md#p4-04) | Late-submission & quiz close policy | P2 | M | D-08 |
| [P4-05](tasks.md#p4-05) | Stats, projections & certificate read fixes | P2 | M | — |
| [P4-06](tasks.md#p4-06) | Chat reliability fixes (save-fail, broadcast room, payload) | P2 | S | P0-03 |
| [P4-07](tasks.md#p4-07) | Chat receipts/unread (conditional) | P2 | M | D-13 |
| [P4-08](tasks.md#p4-08) | Live-session rules & auto-lifecycle | P2 | M | D-15 |
| [P4-09](tasks.md#p4-09) | API ergonomics: pagination caps & params | P2 | M | — |
| [P4-10](tasks.md#p4-10) | Explore/homepage perf & correctness | P2 | M | — (MSH-07 default) |
| [P4-11](tasks.md#p4-11) | Fetch-join / N+1 program | P2 | L | D-24 prep |
| [P4-12](tasks.md#p4-12) | Batch persistence loops | P2 | S | — |
| [P4-13](tasks.md#p4-13) | Email delivery observability | P2 | M | — |
| [P4-14](tasks.md#p4-14) | Jackson parity (API-05) | P3 | S | — |
| [P4-15](tasks.md#p4-15) | `passPoints`/unlock (conditional) | P2 | L | D-10 |
| [P4-16](tasks.md#p4-16) | Envelope/path standardization (conditional) | P2 | L | D-20 |
| [P4-17](tasks.md#p4-17) | Jitsi JWT config hook (conditional) | P2 | S | D-16 |

## 5. Execution order & dependencies

1. P4-01 → P4-02 → P4-03 (the "consistency trilogy" — notification hooks reuse each other).
2. Parallel island: P4-05/06/08/09/10/12/13/14 (disjoint files).
3. P4-11 last of the perf set (uses P3-06 inventory; touches the most query paths).
4. Conditionals (P4-07/15/16/17) run whenever their decisions close — or carry as documented deferrals.

## 6. Exit criteria

1. All business-logic *Confirmed* issues (`ISSUE-MAPPING.md` rows owned here) fixed with green tests, or explicitly deferred with decision.
2. No P0/P1 business-flagged issue remains open in [`ISSUE-MAPPING.md`](../../ISSUE-MAPPING.md).
3. Explore/homepage latency measured pre/post (recorded, not promised).

## 7. Risks

Behavior-change risks on statistics surfaces (FE expectations); receipts feature-scope creep (D-13 defers cleanly).

## 8. Definition of done (phase level)

Task rows verified; `mds/learning-content|gamification|live-sessions|certificates|notifications` docs updated for changed behavior; performance numbers recorded (honest before/after, no vanity).

