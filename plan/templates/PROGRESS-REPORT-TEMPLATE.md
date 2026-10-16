# Progress Report Template

Two uses: (a) **weekly report** (owner: Engineering Lead), (b) **phase-exit report** (owner: Engineering Lead + sign-off reviewers). Do not invent dates/capacity — report measured deltas only.

---

## A. Weekly report (fill per week)

```markdown
# EduNest Weekly Report — week of <factual date range>

## 1. Movement this week
| Task | From → To | Notes |
| (e.g., P1-12) | In Progress → In Review | PR #…, 2 security-review comments resolved |

## 2. Verification & test evidence
- CI status: <green/red per branch, run link>
- Tests added: <count by type — unit/API/WS/E2E>
- Phase 0 verification results: <finding → confirmed/refuted + evidence + ISSUES-MAPPING row updated>

## 3. Decisions
- Advanced this week: <D-xx → Decided(on default/a/b)> from DECISIONS-REQUIRED.md
- Waiting: <list + who owes them + what they gate>

## 4. Blocked (must list every one — TEAM-WORKFLOW §8)
| Task | Reason | Unblocking owner | Next action |
| … | … | … | … |

## 5. Risks updated
- <R# from RISKS-AND-DEPENDENCIES.md if likelihood/impact changed + note>

## 6. Traceability sync state
- ISSUE-MAPPING.md rows updated: <issue IDs → new status>
- mds/ISSUES.md additions: <new defect IDs discovered, or "none">

## 7. Next-week intent (tasks per workstream A–F)
```

## B. Phase-exit report (fill at sign-off)

```markdown
# Phase <NN> Exit Report — <phase name>

## 1. Exit criteria status
<link each criterion in acceptance-criteria.md to its evidence>

## 2. Tasks: final state
| Done | Implemented-await-verification | Deferred by decision | Blocked |
| n | n | n (D-xx) | n (reason) |

## 3. Test/CI evidence
<suite list + results + coverage notes; no fabricated numbers>

## 4. Docs & traceability
- mds/ updates: <files>
- ISSUE-MAPPING rows: <verified list / deferrals>

## 5. Carry-overs into next phase(s) (must not be silent)
<task → next phase/file + owner role>

## 6. Sign-off
| Role | Name | Date | Result |
| Product Owner | — execution-time — | | |
| Engineering Lead | | | |
| Security Reviewer (when phase touched security) | | | |
| QA Engineer | | | |
| DevOps Engineer (when phase touched infra) | | | |
```

**Integrity rules:** every claimed completion carries an evidence artifact link; "verified" without evidence is reported as *Implemented-await-verification*; weekly reports never declare a phase complete (only the exit report + sign-off does).
