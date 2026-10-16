# Phase 00 — Planning Baseline, Decision Round 1 & Finding Verification

## 1. Objective

Make execution safe to start: verify every suspected audit finding (⚠️ rows of [`mds/ISSUES.md`](../../../mds/ISSUES.md)) so no implementation builds on an unverified assumption, secure **Round-1 decisions** (the five that gate the critical path: D-14 chat membership, D-05 open endpoints, D-07 upload policy, D-01 session model, D-22 hosting), and ratify the working agreements (DoR/DoD, branching, review rules) so the team executes this plan without inventing process mid-flight.

## 2. Scope

- **In scope:** runtime/POC verification of suspected findings; decision workshops; working-agreement ratification; establishing the FE contract channel (assumption A-6); intent check on the unused Agora SDK (assumption A-8); a clean-DB deployment rehearsal to validate commission/first-admin boot behavior.
- **Out of scope:** any production-code change (fixes live in their phases); full decision resolution for D-08..D-21 (they have until their gating tasks — Phase 0 round targets only the critical-path blockers).

## 3. Entry criteria

1. This plan reviewed by PO + Eng Lead; priorities understood ([`EXECUTION-STRATEGY §2`](../../EXECUTION-STRATEGY.md#2-priority-model)).
2. Team roles assigned *by role* (people assigned at board level — not invented here).
3. [`RISKS-AND-DEPENDENCIES.md`](../../RISKS-AND-DEPENDENCIES.md) circulating; assumption register A-1..A-10 assigned verify owners where applicable.

## 4. Task index

| Task | Title | Pri | Size | Domain | Gate |
|---|---|---|---|---|---|
| [P0-01](tasks.md#p0-01) | Ratify working agreements + baseline facts | P1 | S | process | none |
| [P0-02](tasks.md#p0-02) | Decision workshop round 1 (5 gating decisions) | P1 | M | process | none → feeds P1 |
| [P0-03](tasks.md#p0-03) | V-01: FE channel + STOMP binding (CHAT-08) | P1 | S | websocket | none |
| [P0-04](tasks.md#p0-04) | V-02: contact-reply HTML-injection POC (SEL-15) | P1 | S | auth/notifications | none |
| [P0-05](tasks.md#p0-05) | V-03: explore `LIKE` wildcard POC (MSH-12) | P1 | S | learning | none |
| [P0-06](tasks.md#p0-06) | V-04: upload type/extension runtime matrix | P1 | M | file-storage | none |
| [P0-07](tasks.md#p0-07) | V-05: zip allowlist looseness test (FILE-04) | P1 | XS | file-storage | none |
| [P0-08](tasks.md#p0-08) | V-06: question re-parent cross-mentor POC (QZ-06) | P1 | S | learning | none |
| [P0-09](tasks.md#p0-09) | V-08: fresh-DB deployment rehearsal (MSH-05) | P1 | S | deployment/database | none |

## 5. Execution order & dependencies

- P0-01 first (everything references it). P0-02 next (its outputs unlock Phase 1 scheduling).
- V-01..V-08 are independent — parallelizable *only if* separate operators exist (they need running app instances; one dev machine can run them sequentially in ~ a day).
- P0-06 produces the evidence matrix that P1-22 consumes; P0-09 validates the P3-05 assumption (commission seeding) and gives Phase 1 a fail-fast test fixture (boot banner with profile check).

## 6. Exit criteria

1. Every suspected row in the mapping ([`ISSUE-MAPPING.md`](../../ISSUE-MAPPING.md)) has a verification outcome (confirmed-with-evidence / refuted) recorded; `mds/ISSUES.md` untouched, sync note added per [`TEAM-WORKFLOW §6`](../../TEAM-WORKFLOW.md#6-sync-rules-with-the-audit-registry).
2. D-01, D-05, D-07, D-14, D-22 have recorded outcomes in [`DECISIONS-REQUIRED.md`](../../DECISIONS-REQUIRED.md) (decided, or defaulted-with-PO-signature, or re-scoped).
3. Working agreements ratified (P0-01 artifacts linked).
4. Full details incl. sign-off: [`acceptance-criteria.md`](acceptance-criteria.md).

## 7. Risks specific to this phase

Verification POCs themselves touch running systems — run them against local/dev instances only, never staging-equivalents that others use ([RISKS R-none: they are read-only or disposable-DB tasks]).

## 8. Definition of done (phase level)

All exit criteria above verified with evidence in [`acceptance-criteria.md §2–§5`](acceptance-criteria.md); Phase 1 boards populated; blocked items carried with reasons.
