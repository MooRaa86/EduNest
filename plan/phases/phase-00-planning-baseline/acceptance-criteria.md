# Phase 00 — Acceptance Criteria

## 1. Entry verification

- [ ] Plan accepted (README walked through; no open dispute on the phase model) — evidence: minuted walkthrough note from P0-01.
- [ ] Roles assigned (P0-01 artifact).

## 2. Per-task acceptance

| Task | Key criterion | Evidence (filled at execution) | Verified by role |
|---|---|---|---|
| P0-01 | Working agreements ratified; A-6/A-8 resolved | — | Eng Lead |
| P0-02 | 5 gating decisions recorded | — | PO |
| P0-03 | CHAT-08 verdict recorded with payload captures | — | QA |
| P0-04 | SEL-15 verdict recorded | — | Security Reviewer |
| P0-05 | MSH-12 scan-abuse evidence recorded | — | Backend Dev |
| P0-06 | Upload matrix complete (4 surfaces × ≥5 payloads) | [Local evidence: 4 × 7 image cases; ZIP service contrast; traversal and endpoint ZIP cases pending](EB-4-UPLOAD-MATRIX.md) | Security Reviewer — pending |
| P0-07 | Zip-spoof verdict recorded | — | Security Reviewer |
| P0-08 | QZ-06 verdict recorded (two-mentor fixture) | **Recorded 2026-11-01 (EB-26): CONFIRMED** — the pre-fix G15 pin probe (`QuestionReparentProbeTest`) drove mentor A's PATCH with a foreign `quizId` and got 200 with the question re-pointed into mentor B's DRAFT quiz; code audit matched (`QuestionServiceImp#updateQuestion` validated only source ownership + target DRAFT status). Verdict annotation added to [P1-19 mapping](../../../plan/ISSUE-MAPPING.md); guard shipped same ticket | Security Reviewer (Omar) — verdict recorded; guard merged |
| P0-09 | Fresh-DB rehearsal log incl. permissive-chain observation | [Executed 2026-11-01: fresh-UUID H2 boot; MSH-05 reproduced (400 ERR_BUSINESS_RULE, zero writes); 10% commission success, 200 → 20 split; full suite 409 run / 0 fail; SEL-02 git evidence + boot matrix 5/5](EB-7-FRESH-DB-REHEARSAL.md) | DevOps — evidence recorded; MSH-05 fix pending D-17 (P3-05) |

## 3. Phase-level checks

- [ ] `ISSUE-MAPPING.md` rows for SEL-15, MSH-12, MSH-05, FILE-03, FILE-04, CHAT-08, QZ-06 annotated with verdicts + evidence links.
- [ ] `mds/ISSUES.md` untouched except the single sync-pointer note (per [`TEAM-WORKFLOW §6`](../../TEAM-WORKFLOW.md#6-sync-rules-with-the-audit-registry)).
- [ ] No production source file modified during this phase (verification POCs ran against dev instances only) — evidence: git diff summary excluding `plan/`+`mds/`.
- [ ] Phase 1 board populated with decisions available (or defaults confirmed per `DECISIONS-REQUIRED.md`).

## 4. Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| Product Owner | — | — | — |
| Engineering Lead | — | — | — |
| Security Reviewer | — | — | — |
| QA Engineer | — | — | — |

*(Names/dates are filled at execution time from real sign-off events.)*

## 5. Carry-overs

List (with reason + owner role) any verification refuted finding that removes work from a later task, and any Round-1 decision left as default-adopted rather than fully decided.
