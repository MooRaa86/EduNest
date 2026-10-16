# EduNest — Engineering Documentation Index

**Generated:** Oct 2026 (documentation & analysis phase). Every claim in this tree was verified by reading the current source; issues carry evidence (`mds/ISSUES.md`). This directory is the single source of truth for Markdown engineering documentation.

---

## 1. What EduNest is

EduNest is a **mentor-led structured learning platform** (Java 21 · Spring Boot 3.5.7 · MySQL 8 · Spring Security/JWT · STOMP/SockJS · Jitsi Meet): mentors build week-by-week curricula (lectures, quizzes, tasks, projects, live sessions), students enroll into cohorts, earn points/badges/ranks, chat in real time, and finish with ranked certificates tied to a leaderboard. Roles: **STUDENT · MENTOR · ADMIN** (seeded by `config/RolesSeeder`).

Architecture in one paragraph: stateless JWT auth (prod profile only — see the warning below) → `@PreAuthorize` + service-level ownership checks → hand-rolled services writing through Spring Data repositories (43 entity classes, `ddl-auto=update`) → real-time pushes over an in-memory simple broker → local-disk file storage (`uploads/`, publicly served) → SMTP for account flows. There is **no payment gateway** (enrollments are free claims with a commission *snapshot*), **no CI/CD**, and **one scheduled job** (OTP cleanup).

> ⚠️ **Read this first:** the `!prod` security profile exposes **every endpoint** with `permitAll()`, and the JWT signing falls back to a **hardcoded secret**. If you run the app locally, set `SPRING_PROFILES_ACTIVE=prod` — [details](authentication-security/security-risks.md).

## 2. Documentation map

Each domain has its own directory with a primary **`MDS.md`**; supporting docs live beside it.

| Domain | Entry | Key supporting docs |
|---|---|---|
| Architecture (cross-cutting) | [architecture/MDS.md](architecture/MDS.md) | [system-overview.md](architecture/system-overview.md) — stack, pipelines, packages, conventions |
| Authentication & security | [authentication-security/MDS.md](authentication-security/MDS.md) | [authentication-flow.md](authentication-security/authentication-flow.md) · [authorization-rules.md](authentication-security/authorization-rules.md) (URL matrix + IDOR G#) · [security-configuration.md](authentication-security/security-configuration.md) · [security-risks.md](authentication-security/security-risks.md) · [legacy-audits/](authentication-security/legacy-audits/) (preserved team audits) |
| Mentorship lifecycle | [mentorship-flow/MDS.md](mentorship-flow/MDS.md) | [mentorship-lifecycle.md](mentorship-flow/mentorship-lifecycle.md) · [enrollment-and-pricing.md](mentorship-flow/enrollment-and-pricing.md) · [weeks-and-lectures.md](mentorship-flow/weeks-and-lectures.md) · [explore-and-homepage.md](mentorship-flow/explore-and-homepage.md) |
| Learning content | [learning-content/MDS.md](learning-content/MDS.md) | [tasks-workflow.md](learning-content/tasks-workflow.md) · [projects-workflow.md](learning-content/projects-workflow.md) · [quizzes-workflow.md](learning-content/quizzes-workflow.md) · [my-learning-and-achievements.md](learning-content/my-learning-and-achievements.md) |
| WebSocket & chat | [websocket/MDS.md](websocket/MDS.md) | [connection-lifecycle.md](websocket/connection-lifecycle.md) · [messaging-flow.md](websocket/messaging-flow.md) |
| Users & profiles | [users-profiles/MDS.md](users-profiles/MDS.md) | [profiles.md](users-profiles/profiles.md) |
| Gamification | [gamification/MDS.md](gamification/MDS.md) | points award sites table inside [learning-content/my-learning-and-achievements.md](learning-content/my-learning-and-achievements.md) |
| Live sessions | [live-sessions/MDS.md](live-sessions/MDS.md) | (attendance math + Jitsi reality inside) |
| Certificates | [certificates/MDS.md](certificates/MDS.md) | (issuance flow + doc-parity gap inside) |
| Notifications | [notifications/MDS.md](notifications/MDS.md) | (call-site map + push mechanics inside) |
| File storage | [file-storage/MDS.md](file-storage/MDS.md) | (two-service contrast + serving rules inside) |
| Integrations | [integrations/MDS.md](integrations/MDS.md) | [external-services.md](integrations/external-services.md) (template/config tables) |
| Database | [database/MDS.md](database/MDS.md) | [entity-relationships.md](database/entity-relationships.md) · [transactions-consistency.md](database/transactions-consistency.md) · [indexing.md](database/indexing.md) · preserved analyses: [DATABASE_OPTIMIZATION.md](database/DATABASE_OPTIMIZATION.md), [database_indexing_recommendations.md](database/database_indexing_recommendations.md), [search_indexing_plan.md](database/search_indexing_plan.md) |
| API | [api/MDS.md](api/MDS.md) | [endpoints.md](api/endpoints.md) (full route-verified inventory) · [error-responses.md](api/error-responses.md) |
| Testing | [testing/MDS.md](testing/MDS.md) | (current state + prioritized suite plan) |
| Deployment | [deployment/MDS.md](deployment/MDS.md) | [environment-configuration.md](deployment/environment-configuration.md) · [production-readiness.md](deployment/production-readiness.md) (checklist + phase order) |
| **Issue inventory** | [ISSUES.md](ISSUES.md) | ~98 tracked issues with IDs, evidence, severity, and resolution pointers |

## 3. Navigation guide

- *"How does X actually work?"* → open the domain's `MDS.md`, then its workflow docs (they include file paths, method names, and citations).
- *"What's broken / risky?"* → [ISSUES.md](ISSUES.md) (by domain ID families like `SEL-*`, `WS-*`, `MSH-*`), each domain's §"Known Issues", and the condensed register in [authentication-security/security-risks.md](authentication-security/security-risks.md).
- *"What do we call this endpoint?"* → [api/endpoints.md](api/endpoints.md); error shapes: [api/error-responses.md](api/error-responses.md).
- *"What must we fix before launch?"* → [deployment/production-readiness.md](deployment/production-readiness.md) (blockers B1–B8, hardening H1–H8, and the suggested phase order).
- *"Which tests should we write first?"* → [testing/MDS.md §3](testing/MDS.md#3-requirements-the-suite-must-eventually-cover-from-this-documentations-risk-register).

### Recommended reading order (new teammate)

1. [architecture/system-overview.md](architecture/system-overview.md) — stack & request/WebSocket pipelines
2. [authentication-security/MDS.md](authentication-security/MDS.md) + [security-configuration.md](authentication-security/security-configuration.md) — the security posture (and why `prod` matters)
3. [api/endpoints.md](api/endpoints.md) — the surface (skim by domain)
4. [mentorship-flow/MDS.md](mentorship-flow/MDS.md) — the core product loop
5. [learning-content/MDS.md](learning-content/MDS.md) + [gamification/MDS.md](gamification/MDS.md) — the engagement engine
6. [websocket/MDS.md](websocket/MDS.md) + [notifications/MDS.md](notifications/MDS.md) — real-time behavior
7. [database/entity-relationships.md](database/entity-relationships.md) + [ISSUES.md](ISSUES.md) — the model and its debt
8. [deployment/production-readiness.md](deployment/production-readiness.md) — where the team is heading

## 4. Documentation status

**Current state:** all discovered domains documented — 16 domain directories holding 43 documents, plus 4 preserved legacy audits, the root-level [ISSUES.md](ISSUES.md) issue inventory, and this README. Content distinguishes *implemented* vs *claimed-but-absent* behavior; suspected (unverified) findings are marked ⚠️.

**Known gaps / open questions:**

- Frontend contract assumptions await confirmation (STOMP payload shape `CHAT-08`, SockJS transport choice, unread-badge behavior).
- A few `dto` field-level validations were not exhaustively enumerated (schemas are consistent in style; the endpoints doc captures the contractual DTOs where audits verified them).
- The `pom.xml` dependency tree was inspected at the module level, not per-artifact CVE audit (`OWASP dependency-check` recommended in the stabilization phase).
- Runtime-only behaviors (threading races, SockJS fallback transports) are marked ⚠️ suspected and need a running environment to confirm.

**Products of this phase (deliverables):** the 43-document tree above; the consolidated issue inventory ([ISSUES.md](ISSUES.md)); this README as the entry point; legacy audits preserved under [authentication-security/legacy-audits/](authentication-security/legacy-audits/) and [database/](database/).

## 5. Outside this directory (documented exceptions)

- `README.md` (repo root) — project landing page, kept at root by GitHub convention. ⚠️ It (and the three graduation "book" files `EduNest_Project_Documentation*.md`, `FINAL_EDUNEST_DOCUMENTATION.md`) contains marketing/claim-level statements that **overstate** the implementation (e.g., Jitsi JWT protection, PDF certificates). Treat them as historical deliverables; [ISSUES.md DOC-01/DOC-02](ISSUES.md#16-documentation-doc) tracks the parity debt.
- `uploads/` committed real files and `scripts/script.sql` (inert) are code-repo artifacts, not documentation — tracked as FILE-06 / DB-02.

---

*How this tree was produced: parallel read-only audits of six source areas (auth, chat/WS, mentorship, learning, users/gamification, admin/infra) followed by targeted verification of load-bearing claims (JWT secret handling, exception contract, Dockerfile/CI, route map, template usage). Nothing outside `mds/` was modified.*
