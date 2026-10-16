# EduNest — Implementation & Delivery Plan (Entry Point)

> **Status: PLANNING ONLY.** Nothing in `plan/` has been implemented. No acceptance criterion in this plan is met yet. This plan was produced from the completed audit (`mds/`) in Oct 2026; every task traces to a canonical `mds/ISSUES.md` issue ID or to an explicitly declared product/ops requirement.
>
> **What this plan is not:** it contains no code, no new issue registry, and no invented IDs. It turns the audit into *dependency-aware, reviewable execution work*.

## 1. How the three documentation layers relate

| Layer | Location | Answers |
|---|---|---|
| **Audit / system truth** | [`mds/`](../mds/README.md) | How EduNest works today; what is implemented vs claimed; canonical issue inventory [`mds/ISSUES.md`](../mds/ISSUES.md) (~98 issues, IDs like `SEL-01`, `WS-01`, `DB-04`) |
| **Plan (this directory)** | `plan/` | What we will do about it, in what order, by whom, with what acceptance criteria |
| **Templates** | [`plan/templates/`](templates/TASK-TEMPLATE.md) | Field canon for new tasks, phases, and progress reports |

`mds/ISSUES.md` remains the **single source of defect truth**. The mapping from issue → task lives in [`ISSUE-MAPPING.md`](ISSUE-MAPPING.md); no one ever edits problems into the phases directly.

## 2. Start here, by role

- **Product Owner** → [`DECISIONS-REQUIRED.md`](DECISIONS-REQUIRED.md) (26 decisions; first 5 unblock the critical path) and [`ROADMAP.md`](ROADMAP.md) §1.
- **Engineering Lead / Architect** → [`EXECUTION-STRATEGY.md`](EXECUTION-STRATEGY.md), [`ROADMAP.md`](ROADMAP.md) (dependency graph, critical path), [`RISKS-AND-DEPENDENCIES.md`](RISKS-AND-DEPENDENCIES.md).
- **Developers** → your domain doc under [`domains/`](domains/authentication-security/MDS.md), then your assigned task in `phases/<phase>/tasks.md`, then the phase acceptance file.
- **QA Engineer** → [`domains/testing/MDS.md`](domains/testing/MDS.md) + phase acceptance criteria + [`mds/testing/MDS.md`](../mds/testing/MDS.md) §3 (suite priorities).
- **DevOps Engineer** → [`domains/deployment/MDS.md`](domains/deployment/MDS.md) + Phases 6–8.
- **Anyone new** → read [`mds/README.md`](../mds/README.md) reading order first; this plan assumes it.

## 3. Structure

```
plan/
├── README.md              ← you are here
├── ROADMAP.md             ← phases, dependency graph, critical path, release lifecycle
├── EXECUTION-STRATEGY.md  ← prioritization model, policies, guardrails
├── TEAM-WORKFLOW.md       ← how work flows: boards, branches, PRs, reviews, sync rules
├── DECISIONS-REQUIRED.md  ← D-01..D-26 product/ops decisions with blocked-task lists
├── RISKS-AND-DEPENDENCIES.md
├── ISSUE-MAPPING.md       ← traceability: every ISSUES.md row → task / verification / deferral
├── phases/
│   ├── phase-00-planning-baseline/        (9 tasks)
│   ├── phase-01-security-stabilization/   (27 tasks)
│   ├── phase-02-testing-and-ci/           (8 tasks)
│   ├── phase-03-database-integrity/       (7 tasks)
│   ├── phase-04-business-logic-and-notifications/ (17 tasks)
│   ├── phase-05-production-readiness/     (5 tasks)
│   ├── phase-06-environment-and-deployment-automation/ (9 tasks)
│   ├── phase-07-staging-and-release/      (8 tasks)
│   └── phase-08-post-launch-operations/   (8 tasks)
├── domains/  (authentication-security · websocket · file-storage · learning-content ·
│              database · testing · notifications · deployment — one MDS.md each)
└── templates/ (TASK-TEMPLATE.md · PHASE-TEMPLATE.md · PROGRESS-REPORT-TEMPLATE.md)
```

Each phase directory = the execution unit: `MDS.md` (objective, entry/exit criteria) → `tasks.md` (the executable tasks, template-formatted) → `acceptance-criteria.md` (verifiable exit checklist + sign-off table). Domain directories = the *how-to* constraints for whoever works in that area, regardless of phase.

## 4. Conventions used everywhere in `plan/`

- **Task IDs:** `P<phase>-<NN>` (e.g., `P1-12`). Deliberately different in shape from issue IDs (`SEL-01`, `WS-01`, …) so nothing is confused with the audit inventory.
- **Priorities:** P0 = critical security/correctness, do first. P1 = prerequisite for any safe deployment. P2 = important hardening/product work. P3 = can slip. Rules and severity→priority mapping: [`EXECUTION-STRATEGY.md` §2](EXECUTION-STRATEGY.md#2-priority-model).
- **Effort sizes:** XS/S/M/L/XL with stated assumptions — no person-days, no dates, no invented capacity. Date/people assignment happens in the team's tracker using [`TEAM-WORKFLOW.md`](TEAM-WORKFLOW.md).
- **Statuses (tasks):** `Planned → Ready → In Progress → In Review → Testing → Done`, plus `Blocked` (always with reason/owner/next action) and `Deferred by decision` (only via a decision from [`DECISIONS-REQUIRED.md`](DECISIONS-REQUIRED.md)). Full model: [`TEAM-WORKFLOW.md` §2](TEAM-WORKFLOW.md#2-execution-model).
- **Issue status sync:** per [`TEAM-WORKFLOW.md` §6](TEAM-WORKFLOW.md#6-sync-rules-with-the-audit-registry): `mds/ISSUES.md` statuses change only after *implementation + verification evidence*, via the documented sync step — never merely because a task was planned.
- **Links:** relative links throughout; `mds/` references use `../mds/...` (from `plan/` root files) or `../../../mds/...` (from files two levels deep). If a link breaks, fix it — they are part of the definition of done.

## 5. Ground rules inherited from the audit

1. Spring Boot 3.5.7 / Java 21 layered architecture is **preserved** — no rewrites, no service extraction (see [`EXECUTION-STRATEGY.md` §4](EXECUTION-STRATEGY.md#4-technical-guardrails)).
2. Security fixes are not held hostage by product debates: every decision in [`DECISIONS-REQUIRED.md`](DECISIONS-REQUIRED.md) declares whether security work can proceed on the documented default.
3. Nothing may be declared "done" or "production-ready" without verification evidence (CI run, test suite result, or staging checklist entry). [`Go/no-go checklist`](phases/phase-07-staging-and-release/acceptance-criteria.md#4-production-gono-go-checklist-verifiable-exit-criteria).
4. Suspected audit findings are verification tasks first (Phase 0) — never silently converted into confirmed requirements.
