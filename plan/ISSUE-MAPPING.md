# Issue Mapping — Canonical Issues → Plan Tasks (Traceability Index)

Live tracker. Update statuses **in this file** per execution ([`TEAM-WORKFLOW §6`](TEAM-WORKFLOW.md#6-sync-rules-with-the-audit-registry)); `mds/ISSUES.md` rows stay untouched (they are audit truth). Statuses: `Planned → Ready → In Progress → Implemented-await-verification → Verified`, or `Deferred by decision` / `Blocked`.

**Disposition legend:** T = implementation task planned · V→T = verification task first, then implementation · D→T = decision-gated, proceeds on documented default · DEF = deferred by decision · RES = resolved during documentation phase.

| Issue | Sev | Task(s) | Phase | Domain | Disposition | Status |
|---|---|---|---|---|---|---|
| **SEL-01** | Crit | P1-01 | 1 | auth | T + D-22 ordering note | Planned |
| **SEL-02** | Crit | P1-02 | 1 | auth | T | Planned |
| **SEL-03** | Crit | P1-08 → P5-03 | 1,5 | auth | T (two-stage) | Planned |
| **SEL-04** | High | P1-07 | 1 | auth | T | Planned |
| **SEL-05** | High | P1-06 | 1 | auth | T | Planned |
| **SEL-06** | High | P1-27 (cond.) | 1 | auth | D→T (D-01) | Planned |
| **SEL-07** | Med | P1-25 | 1 | auth | T | Planned |
| **SEL-08** | High | P1-09 | 1 | auth | T (+API-04) | Planned |
| **SEL-09** | Med | P1-10 | 1 | auth | T | Planned |
| **SEL-10** | High | P1-11 | 1 | auth | D→T (D-05) | Planned |
| **SEL-11** | Med | P1-23 | 1 | auth | D→T (D-23) | Planned |
| **SEL-12** | Med | P1-04 | 1 | auth | T (+D-01 note) | Planned |
| **SEL-13** | Med | — | — | auth | DEF (latent design) | Deferred |
| **SEL-14** | High | P1-05 | 1 | api | D→T (D-26) | Planned |
| **SEL-15** | Med | P0-04 → P1-24 | 0,1 | auth | V→T | Planned |
| **SEL-16** | Med | P1-03 | 1 | auth | T | Planned |
| **WS-01** | Crit | P1-12 | 1 | ws | D→T (D-14) | Planned |
| **WS-02** | Low | P1-12 | 1 | ws | T | Planned |
| **WS-03** | Low | P4-09 | 4 | api | T | Planned |
| **CHAT-01** | Med | P4-06 | 4 | ws | T | Planned |
| **CHAT-02** | Crit | P1-13 | 1 | ws | T | Planned |
| **CHAT-03** | High | P1-13 | 1 | ws | T | Planned |
| **CHAT-04** | High | P1-13 | 1 | ws | T | Planned |
| **CHAT-05** | Med | P3-02 | 3 | database | T | Planned |
| **CHAT-06** | Low | P4-06 | 4 | ws | T | Planned |
| **CHAT-07** | Med | P4-07 (cond.) | 4 | ws | D→T (D-13) | Planned |
| **CHAT-08** | Low | P0-03 → (P4-06 if confirmed) | 0,4 | ws | V→T (cond.) | Planned |
| **MSH-01** | High | P1-15 | 1 | auth | D→T (D-02) | Planned |
| **MSH-02** | High | P1-14 | 1 | learning | D→T (D-03) | Planned |
| **MSH-03** | Med | P1-16 | 1 | auth | D→T (D-04) | Planned |
| **MSH-04** | Med | P3-02 | 3 | database | T | Planned |
| **MSH-05** | Med | P0-09 → P3-05 | 0,3 | database | V→T (D-17) | Planned |
| **MSH-06** | Med | P1-22 | 1 | file-storage | T | Planned |
| **MSH-07** | Low | P4-10 | 4 | learning | T (default: effective price) | Planned |
| **MSH-08** | Low | P4-10, P4-11 | 4 | learning | T | Planned |
| **MSH-09** | Med | P1-16 | 1 | auth | D→T (D-04) | Planned |
| **MSH-10** | Med | P4-03 | 4 | notifications | T | Planned |
| **MSH-11** | Med | P1-17 | 1 | auth | T | Planned |
| **MSH-12** | Med | P0-05 → P4-10 | 0,4 | learning | V→T | Planned |
| **LEARN-01** | Crit | P1-18 | 1 | learning | T | Planned |
| **LEARN-02** | High | P1-14 | 1 | learning | D→T (D-03) | Planned |
| **LEARN-03** | High | P1-20 | 1 | file-storage | D→T (D-06) | Planned |
| **LEARN-04** | Med | P4-05 | 4 | learning | T | Planned |
| **LEARN-05** | Med | P4-04 | 4 | learning | D→T (D-08) | Planned |
| **LEARN-06** | Med | P4-02 | 4 | gamification | D→T (D-09) | Planned |
| **LEARN-07** | Med | P4-05 | 4 | learning | T | Planned |
| **LEARN-08** | High | P4-15 (cond.) | 4 | learning | D→T (D-10), DEF ok | Planned |
| **LEARN-09** | Low | P4-05 | 4 | learning | T | Planned |
| **LEARN-10** | Med | P4-04 | 4 | learning | D→T (D-08) | Planned |
| **QZ-06** | Med | P0-08 → P1-19 | 0,1 | learning | V→T | Planned |
| **USR-01** | Med | P4-09 | 4 | users | T | Planned |
| **USR-02** | Low | P1-16 | 1 | auth | D→T (D-04) | Planned |
| **USR-03** | Low | P4-05 | 4 | users | T | Planned |
| **USR-04** | Low | P4-09 | 4 | users | T | Planned |
| **USR-05** | Med | P4-01 | 4 | notifications | = NOTIF-04 | Planned |
| **GAME-01** | High | P1-16 / P1-19 | 1 | gamification | = MSH-03+G11 | Planned |
| **GAME-02** | Med | P1-19 | 1 | gamification | T | Planned |
| **GAME-03** | Med | P4-02 | 4 | gamification | D→T (D-09) | Planned |
| **GAME-04** | Low | P4-02 | 4 | gamification | T | Planned |
| **GAME-05** | Low | P4-02 | 4 | gamification | T (doc-in-code) | Planned |
| **LS-01** | High | P4-17 (cond.), P5-05 | 4,5 | live-sess | D→T (D-16), DEF ok | Planned |
| **LS-02** | Med | P4-08 | 4 | live-sess | D→T (D-15) | Planned |
| **LS-03** | Med | P4-08 | 4 | live-sess | D→T (D-15) | Planned |
| **LS-04** | Low | P4-12 | 4 | database | T | Planned |
| **LS-05** | Low | P4-08 | 4 | live-sess | T | Planned |
| **CERT-01** | High | P5-05 (+optional feature) | 5 | certificates | D→T (D-11), DEF ok | Planned |
| **CERT-02** | Med | P4-05 | 4 | certificates | T | Planned |
| **CERT-03** | Low | P4-05 | 4 | certificates | T | Planned |
| **CERT-04** | Med | P4-02 | 4 | gamification | D→T (D-09) | Planned |
| **NOTIF-01** | High | P1-11 | 1 | notifications | D→T (D-05) | Planned |
| **NOTIF-02** | Med | P4-01 | 4 | notifications | T | Planned |
| **NOTIF-03** | Med | P4-01 | 4 | notifications | T | Planned |
| **NOTIF-04** | Med | P4-01 | 4 | notifications | D→T (D-12) | Planned |
| **NOTIF-05** | Low | P4-01 | 4 | notifications | T | Planned |
| **MAIL-01** | High | P1-11 | 1 | notifications | D→T (D-05) | Planned |
| **FILE-01** | Crit | P1-21 + P1-20 | 1 | file-storage | D→T (D-07/D-06) | Planned |
| **FILE-02** | High | P1-22 | 1 | file-storage | T | Planned |
| **FILE-03** | Med | P0-06 → P1-22 | 0,1 | file-storage | V→T | Planned |
| **FILE-04** | Med | P0-06/P0-07 → P1-22 | 0,1 | file-storage | V→T | Planned |
| **FILE-05** | Med | P3-03 | 3 | file-storage | T | Planned |
| **FILE-06** | Med | P6-06 | 6 | deployment | D→T (D-19) | Planned |
| **FILE-07** | Med | P6-05 (+P6-07 cond.) | 6 | deployment | T | Planned |
| **DB-01** | High | P3-01 | 3 | database | T (+DEP-04) | Planned |
| **DB-02** | Med | P3-01 | 3 | database | T | Planned |
| **DB-03** | Med | P3-04 | 3 | database | D→T (D-18) | Planned |
| **DB-04** | Med | P3-02 | 3 | database | T | Planned |
| **DB-05** | Med | P3-03 | 3 | database | T | Planned |
| **DB-06** | Low | P3-06 → P4-11 | 3,4 | database | T (+D-24) | Planned |
| **DB-07** | Low | P4-12 | 4 | database | T | Planned |
| **API-01** | Low | P4-16 (cond.) | 4 | api | D→T (D-20), DEF expected | Planned |
| **API-02** | Med | P4-09 | 4 | api | T | Planned |
| **API-03** | Low | P4-16 (cond.) | 4 | api | D→T (D-20), DEF expected | Planned |
| **API-04** | High | P1-09 | 1 | auth | = SEL-08 | Planned |
| **API-05** | Med | P4-14 | 4 | api | T | Planned |
| **INT-01** | Low | P1-26 | 1 | integrations | T (+Agora/R11) | Planned |
| **INT-02** | Med | P4-13 | 4 | integrations | T | Planned |
| **INT-03** | Low | P1-26 | 1 | integrations | T | Planned |
| **DEP-01** | High | P2-07 → P6-04 | 2,6 | deployment | T | Planned |
| **DEP-02** | High | P5-01, P5-02 | 5 | deployment | T | Planned |
| **DEP-03** | High | P6-05 | 6 | deployment | T | Planned |
| **DEP-04** | Med | P3-01 | 3 | database | = DB-01 part | Planned |
| **DEP-05** | Med | P1-01 → P6-03 | 1,6 | auth | = SEL-01 + infra | Planned |
| **DEP-06** | Med | P6-06 | 6 | deployment | = FILE-06 (D-19) | Planned |
| **DEP-07** | Med | P6-01, P6-03 | 6 | deployment | T | Planned |
| **DEP-08** | Low | P6-02, P8-05 | 6,8 | deployment | T | Planned |
| **TST-01** | High | P2-02/03/04/05 | 2 | testing | T | Planned |
| **TST-02** | Med | P2-01 | 2 | testing | T | Planned |
| **TST-03** | Med | P2-07 | 2 | testing | T | Planned |
| **TST-04** | Low | P2-08, P8-07 | 2,8 | testing | T | Planned |
| **DOC-01** | Med | P5-05 | 5 | deployment | T | Planned |
| **DOC-02** | Low | P5-05 | 5 | deployment | T | Planned |
| **DOC-03** | Low | — | — | docs | RES (mds reorganization) | Resolved |
| **TD-01** | Low | — | — | tech-debt | DEF (risk>value now) | Deferred |
| **TD-02** | Low | P1-26 | 1 | tech-debt | T | Planned |
| **TD-03** | Low | P1-26 (+P1-12 dead code) | 1 | tech-debt | T | Planned |
| **TD-04** | Low | P1-26 | 1 | tech-debt | = INT-01 | Planned |
| **TD-05** | Low | P1-26 (+P5-02) | 1,5 | tech-debt | T | Planned |
| **TD-06** | Low | P5-05 | 5 | tech-debt | D→T (D-21) | Planned |
| **TD-07** | Med | P1-10 | 1 | ws | = G12 | Planned |
| **TD-08** | Low | P1-12 | 1 | ws | = G13 (D-14) | Planned |
| **TD-09** | Low | P1-21 | 1 | file-storage | = G14 | Planned |

**Roll-up wrappers (not separate work):** `ARCH-001..007` resolve to their canonical rows (SEL-02, WS-01, SEL-01, DEP-02+broker, TD-01/02, DOC-01, TD-06 — see [`mds/ISSUES.md`](../mds/ISSUES.md) §18 cross-cutting rollups). `G1..G15` are the authorization-gap register; each row's canonical mapping lives in [`mds/ISSUES.md`](../mds/ISSUES.md) §1 — the plan implements canonical IDs (G-matrix rows are the same work: e.g., G4/G5→P1-14, G6→P1-14, G7→P1-16, G8→P1-15, G9→P1-20, G10→P1-11, G11→P1-19, G12→P1-10, G13→P1-12, G14→P1-21, G15→P1-19).

**Not tracked (by design):** `mds/ISSUES.md` §19 disconfirmed suspicions — no tasks; recorded as regression-source material for P2-03.

**Counts:** 97 unique issue rows mapped: 2 deferred (SEL-13, TD-01), 1 resolved (DOC-03), 7 verification-first (SEL-15, MSH-12, MSH-05, FILE-03, FILE-04, QZ-06, CHAT-08), 34 with decision linkage (default path defined for all), the remainder straight implementation tasks. No issue is unmapped.
