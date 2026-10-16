# Task Template (canonical field set)

Every task in `phases/<phase>/tasks.md` must present **all** fields below (compact inline format is acceptable; omitted-only-when-truly-not-applicable *must be stated*, e.g., "FE impact: none").

```markdown
### P<phase>-<NN> — <Action-oriented title>

**Priority:** P0–P3, with one-line justification   **Size:** XS/S/M/L/XL (+ assumptions for M+)
**Owner role:** <Backend Developer | QA Engineer | DevOps Engineer | Security Reviewer | Product Owner | Engineering Lead>
**Reviewers required:** <Code ≥1 (+ Security Reviewer when trigger list applies)>
**Issues:** <canonical mds/ISSUES.md IDs, "(+related)" for aliases>   **Domain:** <plan domain>
**Components:** <exact source paths, e.g., `src/main/java/.../JwtService`, `application-prod.properties`>

**Context / business value:** 2–4 sentences — why this work matters to EduNest, not generic theory.
**Evidence:** links to mds docs (relative `../../../mds/...`) + source line refs from ISSUES.md. Suspected items are marked ⚠️ with their verification task.
**Expected outcome (measurable):** observable end-state ("`X` returns 403 for user Y in role Z", "CI fails…").
**Proposed approach:** concrete steps (3–6 bullets), preserving architecture guardrails ([EXECUTION-STRATEGY §4](../EXECUTION-STRATEGY.md#4-technical-guardrails)).
**Frontend / API impact:** contract-visible change or "none". If contract-visible: transitional flag/decision link.
**Environment access needed:** e.g., CI runner, staging DB, SMTP account — or "none".
**Product decisions involved:** link D-xx or "none".
**Depends on:** task/decision IDs.   **Blocks:** task IDs (or "—").
**Required tests / regression scenarios:** exact new/updated tests incl. the "fails-without-fix" security rule.
**Acceptance criteria:**
- [ ] <objective, verifiable statement — each gets evidence at sign-off>
- [ ] …
**Definition of done:** per [TEAM-WORKFLOW §5](../TEAM-WORKFLOW.md#5-definition-of-ready--definition-of-done) + any task-specific artifact.
**Deliverables / docs to update:** which mds files, ISSUE-MAPPING rows, runbooks.
**Status:** Planned · **Evidence links:** — (filled at execution)
```

### Rules

1. No invented names, dates, or hours. People/dates are assigned at execution time.
2. A task that cannot state a measurable outcome is not Ready — split or clarify first.
3. Conditional tasks are marked `(conditional)` and carry: decision ID, default path, and re-open trigger.
4. Fix tasks touching security/invariant surfaces must cite the invariant they restore (domain doc review checklist).
5. Verification tasks (Phase 0 pattern) use the same template; their "Expected outcome" is *an evidence record*, not a code change.
6. Infra/deployment tasks include the rollback consideration field explicitly.
