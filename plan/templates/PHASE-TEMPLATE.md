# Phase Document Template

Each phase directory contains exactly three files. Keep them linked cross-wise:

## 1. `MDS.md` — the charter

Required sections:

```markdown
# Phase <NN> — <Name>

## 1. Objective
<the one thing this phase achieves; measurable>

## 2. Scope
In scope / explicitly out of scope (with pointers where moved work lives).

## 3. Entry criteria
<verifiable preconditions — decisions, prior phase exits, environment access>

## 4. Task index
| Task | Title | Priority | Size | Domain | Decision gate |
… (one row per task in tasks.md; no duplication of their details)

## 5. Execution order & dependencies
<what must be sequential inside the phase, what may parallel; reference critical path>

## 6. Exit criteria
<point to acceptance-criteria.md summary; list the artifacts>

## 7. Risks specific to this phase
<link to RISKS-AND-DEPENDENCIES.md rows + mitigations>

## 8. Definition of done (phase level)
<all exit criteria verified; sign-off table complete; traceability rows Verified/Deferred>
```

## 2. `tasks.md` — the work

- One `###` subsection per task, in field-canon format ([TASK-TEMPLATE.md](TASK-TEMPLATE.md)).
- Ordered by recommended execution sequence (not alphabetically).
- Every task's status stays `Planned` until DoR moves it — statuses live on the board AND in [ISSUE-MAPPING.md](../ISSUE-MAPPING.md).

## 3. `acceptance-criteria.md` — the gate

Required sections:

```markdown
# Phase <NN> — Acceptance Criteria
## 1. Entry verification  (prove entry criteria were truly met)
## 2. Per-task acceptance (table: Task | Criterion | Evidence | Verified by role)
## 3. Phase-level checks (integration concerns, cross-task regressions, docs in sync)
## 4. Sign-off (table: Role | Name (filled at execution) | Date (fact, not plan) | Result)
## 5. Carry-overs (explicit list — anything exiting as Deferred/Blocked with reason+owner)
```

Rules: an unchecked criterion with no `Blocked`+reason blocks the phase; claims of "done" without evidence are rejected at sign-off ([TEAM-WORKFLOW §10](../TEAM-WORKFLOW.md#10-progress-reporting--phase-sign-off)).
