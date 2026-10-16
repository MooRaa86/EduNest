# Phase 01 — Security Stabilization

## 1. Objective

Every exploitable security hole identified in the audit (7 Critical + High exposure set) is closed **with a failing-test-first proof**, and the app refuses to run in a state weaker than production-grade by default. This is the launch-critical phase: nothing else ships before its P0 row verifies.

## 2. Scope

- **In scope:** JWT lifecycle & secret handling; security-chain fail-fast; public-route calibration; OTP/reset flows; throttling/lockout; admin exposure; method-security & object-level authorization (G-register); WebSocket room authorization; conversation IDORs; answer-key leak; upload exposure & validation policy; error contract; hygiene死去-code dep.
- **Out of scope:** notification *pipeline semantics* (Phase 4), DB constraints (Phase 3), rate-limit rollout beyond auth surfaces (Phase 5), Infra: secrets *provisioning* (Phase 6 — chained after P1-01, see R1).

## 3. Entry criteria

1. Phase 0 exit: D-14, D-05, D-07 defaults confirmed; verification verdicts recorded for proposed-suspected (SEL-15, QZ-06, FILE-03/04) rows.
2. Workstreams A (Security code) & B (Tests) staffed; security-config steward named ([`TEAM-WORKFLOW §7`](../../TEAM-WORKFLOW.md#7-conflict-avoidance-rules-for-shared-files)).
3. current-repo working tree clean on relevant files; scoped baseline established by P2-01 task branch for tests behind fixes becoming mandatory.

## 4. Task index

| Task | Title | Pri | Size | Decision gate |
|---|---|---|---|---|
| [P1-01](tasks.md#p1-01) | JWT secret: env-only + role-parse fix | P0 | S | — |
| [P1-02](tasks.md#p1-02) | Fail-fast security profile (kill dev-wide-open default) | P0 | M | — |
| [P1-03](tasks.md#p1-03) | JWT filter skips public routes | P1 | S | — |
| [P1-04](tasks.md#p1-04) | Token from header only; erase credentials | P1 | S | D-01 transitional |
| [P1-05](tasks.md#p1-05) | Error contract: catch-all→500, explicit 403, unified shapes | P1 | M | D-26 (default adopted) |
| [P1-06](tasks.md#p1-06) | OTP type isolation & verification scoping | P0 | M | — |
| [P1-07](tasks.md#p1-07) | Password reset: single-use, expiry-bound | P1 | S | — |
| [P1-08](tasks.md#p1-08) | Auth throttling + lockout + enumeration fix | P0 | M | — (P5-03 extends) |
| [P1-09](tasks.md#p1-09) | Admin credential exposure + guarded bootstrap | P0 | S | — |
| [P1-10](tasks.md#p1-10) | Method-security & ownership sweep (admin/dashboard/room-create) | P1 | M | — |
| [P1-11](tasks.md#p1-11) | Gate open push/mail endpoints | P0 | S | D-05 (default: gate) |
| [P1-12](tasks.md#p1-12) | WebSocket room authorization (sub/send) + dead-code removal | P0 | L | D-14 |
| [P1-13](tasks.md#p1-13) | Conversation IDOR trio + server-derived events | P0 | M | — |
| [P1-14](tasks.md#p1-14) | Content-read guards (lectures/tasks/get-tasks/detail) | P1 | M | D-03 (confirm) |
| [P1-15](tasks.md#p1-15) | DRAFT mentorship confinement | P1 | S | D-02 |
| [P1-16](tasks.md#p1-16) | Leaderboard guard + reviews/emails privacy | P1 | S | D-04 |
| [P1-17](tasks.md#p1-17) | Homepage progress endpoint enrollment check | P1 | XS | — |
| [P1-18](tasks.md#p1-18) | Quiz answer-key scoping | P0 | M | FE note |
| [P1-19](tasks.md#p1-19) | Question re-parent + badge/award read scoping | P1 | S | V-06 input |
| [P1-20](tasks.md#p1-20) | Generic file endpoint guard | P1 | S | D-06 |
| [P1-21](tasks.md#p1-21) | Upload exposure policy (public/private split) | P0 | M | D-07 |
| [P1-22](tasks.md#p1-22) | Upload validation hardening (Tika magic bytes) | P1 | M | P0-06 matrix |
| [P1-23](tasks.md#p1-23) | CORS allowlist | P2 | S | D-23 |
| [P1-24](tasks.md#p1-24) | Contact-reply escaping (SEL-15) | P2 | S | P0-04 result |
| [P1-25](tasks.md#p1-25) | Bean validation coverage (login/verify/resend) | P2 | S | — |
| [P1-26](tasks.md#p1-26) | Dead-dependency & dead-code hygiene | P2 | S | A-8 record |
| [P1-27](tasks.md#p1-27) | Session invalidation & refresh (conditional) | P1 | L | D-01 |

## 5. Execution order & dependencies

- **Immediately parallel-safe:** P1-01, P1-02, P1-06, P1-08 (auth core) alongside P1-13 (conversations) and P1-18 (quiz) — disjoint files.
- **Sequential islands:** P1-21 needs D-07 + P0-06 matrix; consumes the same chain files as P1-02 (steward sequencing); lands as **one** config PR each.
- **Last:** P1-26 (hygiene) — because it deletes code that P1-12/13 may reuse (dead-code lines referenced in ISSUES TD-03 live in files being fixed).
- P1-27 runs when D-01 is decided; ifulla decision slips, it carries to Phase 2 boundary explicitly (never silently).

## 6. Exit criteria

Full matrix in [`acceptance-criteria.md`](acceptance-criteria.md), but the headline:
1. All 10 P0 tasks *Done with tests*; G1–G15 probe suite (P2-03 section) denies every unauthorized probe.
2. Prod boot fails fast unless hardened configuration present; secrets externalized per design.
3. No FE-visible contract change lacks its coordination note (P1-04/05/18/21).

## 7. Risks specific to this phase

- Shared-file churn (security chain, WebConfig, prod config) → steward model + PR-sequencing (R4, R5 mitigations in [`RISKS §2`](../../RISKS-AND-DEPENDENCIES.md#2-top-risks)).
- FE-breaking changes discovered mid-phase → transitional flags per task, FE-liaison confirmation tracked in task rows.

## 8. Definition of done (phase-level)

All task rows `Verified` (or explicitly Deferred/Blocked with carry-over); P2-03 authorization suite green on `develop`; sign-off table complete in acceptance file.
