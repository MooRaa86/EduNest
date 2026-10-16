# Domain: Learning Content (weeks/tasks/projects/quizzes) — Implementation Guide

Canonical audit sources: [`mds/learning-content/MDS.md`](../../../mds/learning-content/MDS.md), [`mds/mentorship-flow/weeks-and-lectures.md`](../../../mds/mentorship-flow/weeks-and-lectures.md), [`mds/learning-content/tasks-workflow.md`](../../../mds/learning-content/tasks-workflow.md), [`mds/learning-content/projects-workflow.md`](../../../mds/learning-content/projects-workflow.md), [`mds/learning-content/quizzes-workflow.md`](../../../mds/learning-content/quizzes-workflow.md), [`mds/learning-content/my-learning-and-achievements.md`](../../../mds/learning-content/my-learning-and-achievements.md); authorization gaps tracked in [`mds/authentication-security/authorization-rules.md`](../../../mds/authentication-security/authorization-rules.md) G4–G6/G9/G15.

## 1. Purpose & components owned

Mentorship content: weeks & lectures, tasks/projects with attachments and submissions, quiz authoring + attempts/grading, my-learning achievements & progress. Components: `LectureService`, `TaskService/Controller`, `ProjectService`, `QuizService`, `QuestionService`, `QuizSubmissionService`, `MentorShip`-scoped content queries.

## 2. Review checklist (mandatory for any PR here)

- [ ] Reads gate on enrollment/ownership per the **ContentAccessGuard** pattern ([P1-14](../../phases/phase-01-security-stabilization/tasks.md#p1-14)).
- [ ] Answer-bearing payloads never leak pre-submission (`correctAnswer` discipline, [P1-18](../../phases/phase-01-security-stabilization/tasks.md#p1-18)).
- [ ] Question/int-content writes validate the *target* object's ownership (P1-19 pattern for cross-mentor re-parenting).
- [ ] Publish-state gates: locked questions/points after grade or close (D-09/D-08 semantics).
- [ ] Statistics endpoints report DB-truth (fixture tests exist, [P4-05](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-05)).

## 3. Implementation requirements (per canonical issue)

| Issue | Fix | Task |
|---|---|---|
| LEARN-01 answer-key leak (Critical) | Omit `correctAnswer` until a submitted attempt exists; mentor view ownership asserted | [P1-18](../../phases/phase-01-security-stabilization/tasks.md#p1-18) |
| MSH-02 / LEARN-02 (G4–G6) read gaps | Enrollment/ownership guards on lectures, task detail/filters, `.get-tasks` | [P1-14](../../phases/phase-01-security-stabilization/tasks.md#p1-14) |
| QZ-06 (G15, ⚠️V-06) re-parent | Target-quiz ownership guard after P0-08 verdict | [P1-19](../../phases/phase-01-security-stabilization/tasks.md#p1-19) / [P0-08](../../phases/phase-00-planning-baseline/tasks.md#p0-08) |
| LEARN-05/10 late + close (D-08) | Grace/penalty policy + scheduler + post-close locks | [P4-04](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-04) |
| LEARN-04/07/09 stats-projection truth | Submitted-only progress; REVIEW-status stats; score semantics | [P4-05](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-05) |
| LEARN-06/D-09 content edit reconciliation | Locked-after-grade default; explicit regrade rules | [P4-02](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-02) |
| LEARN-08/D-10 unlock feature | Conditional implementation or claim removal | [P4-15](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-15) |
| MSH-08/12/07 explore correctness | DB pagination, keyword escape, effective-price filter | [P4-10](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-10) |

## 4. Definition of done (domain)

Rows owned here in [`ISSUE-MAPPING.md`](../../ISSUE-MAPPING.md) Verified (or decision-deferred: LEARN-08); quizzes/tasks/projects docs updated to describe the *implemented* policy; no answer-key leakage provable by fixture test.
