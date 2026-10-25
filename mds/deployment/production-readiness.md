# Production Readiness — Gap Checklist

State at end of the documentation phase. Every row links to its issue ID in [../ISSUES.md](../ISSUES.md). "Stop-the-line" rows must be resolved (or consciously risk-accepted with sign-off) before real users gain access.

## 1. Before ANY real traffic (blockers)

| # | Gap | Domain doc | Issue |
|---|---|---|---|
| B1 | JWT secret hardcoded + login-role parse breaks when env secret set | [../authentication-security/MDS.md](../authentication-security/MDS.md) | SEL-01 |
| B2 | Dev profile (`!prod`) exposes **every** endpoint — one wrong run away | [../authentication-security/security-configuration.md](../authentication-security/security-configuration.md) | SEL-02 / ARCH-001 |
| B3 | WebSocket rooms: no subscribe/send authorization (eavesdrop + spoof messages) | [../websocket/connection-lifecycle.md](../websocket/connection-lifecycle.md) | WS-01 |
| B4 | Public `/uploads/**` incl. student submissions; `/files?filePath=` ownership bypass | [../file-storage/MDS.md](../file-storage/MDS.md) | FILE-01 / LEARN-03 |
| B5 | Quiz answer key readable pre-submission (grades trivially gamed) | [../learning-content/quizzes-workflow.md](../learning-content/quizzes-workflow.md) | LEARN-01 |
| B6 | Lectures/task-list of any mentorship readable by any authenticated user | [../mentorship-flow/weeks-and-lectures.md](../mentorship-flow/weeks-and-lectures.md) | MSH-02 / G4–G6 |
| B7 | Identity: no OTP rate limiting; enumeration via error shapes; reset step needs no secret | [../authentication-security/security-risks.md](../authentication-security/security-risks.md) | SEL-03/04/05 |
| B8 | Zero automated tests; no CI (`.github/` empty) | [../testing/MDS.md](../testing/MDS.md) · [../deployment/MDS.md](../deployment/MDS.md) | TST-01 / DEP-01 |

## 2. Before sustainability (hardening sprint)

| # | Gap | Issue |
|---|---|---|
| H1 | Health checks/actuator + basic monitoring/armored logging; remove `System.out` | DEP-02 |
| H2 | Schema migrations (Flyway) + retire `ddl-auto=update` + `show-sql` | DB-01/02, DEP-04 |
| H3 | Uploads externalization (volume/object storage) + access-control policy for the public static mount | DEP-03, FILE-* |
| H4 | Uniqueness constraints (enrollments, reviews, conversations) + constraint-violation UX | DB-04 |
| H5 | Notification pipeline: gate `/send`, afterCommit pushes, admin broadcast targeting | NOTIF-01/03/04 |
| H6 | Rate limiting (login/OTP/notifications), lockout policy | SEL-03 |
| H7 | Admin password-hash/registration leaks; notification phishing endpoint | SEL-08, SEL-10 |
| H8 | Error-contract unification (3× 401 shapes; catch-all 409) | [../api/error-responses.md](../api/error-responses.md) |

## 3. Before scale/feature promises (product-parity)

| # | Gap | Issue |
|---|---|---|
| P1 | Docs promise PDF certificates & JWT-guarded Jitsi — absent | CERT-01, LS-01, DOC-01 |
| P2 | `passPoints`/unlock semantics unverifiable in code → implement or delete | LEARN-08 |
| P3 | Lecture-completion tracking (currently always `completed=true`) | weeks doc open question |
| P4 | Payment gateway (enrollment is free-claim) | mentorship doc open question |
| P5 | Email channel for learning notifications, admin broadcast semantics | NOTIF-* open questions |
| P6 | Roadmap feature: empty skeleton — implement or delete | ARCH-007 |

## 4. Pre-flight checklist (executable once fixes land)

1. [ ] `JWT_SECRET` set at runtime; role parse verified (`/login-api` with new secret).
2. [ ] `prod` profile asserted at boot (fail-fast if not active); dev chain disabled or clearly gated.
3. [ ] WebSocket membership guards enabled + integration-tested.
4. [ ] Uploads: token-checked access or folder-split policy merged; `/files?filePath=` removed or gated.
5. [ ] `/api/v1/my-answers` key scoping fixed; `/api/v1/notifications/send` admin-gated.
6. [ ] Unique constraints migrated; enrollment double-submit returns clean 400.
7. [ ] Commission seeded; first-admin created; smoke journey scripted (register → verify → login → mentorship create → publish → enroll → week → submit → grade → points → certificate).
8. [ ] CI pipeline builds images **with tests**; health checks wired; log config shipped.
9. [ ] Secrets/env matrix reviewed (§ [environment-configuration.md](environment-configuration.md)); danger of stray `!prod` runs eliminated.
10. [ ] Books/README claims reconciled (DOC-01) to match the shipped product.

## 5. Suggested stabilization phase order (dependency-aware)

1. **Bundled security hardening** (B1–B5) — these are mostly orthogonal, code-local fixes.
2. **CI + test scaffold** (DEP-01/TST-*) — needed to *prove* the hardening, run in parallel with (1).
3. **Data-model constraints + migrations** (H4, DB-01/02/04).
4. **Notification/transaction projection cleanup** (NOTIF-03, DB-05, push-before-commit).
5. **Ops hardening** (H1/H3/H6), then **ops-parity features** (P-block) driven by product decisions.

## 6. Decided target environment (2026-10-25) — planning note (NOT yet implemented)

Everything above describes **current reality** (verified against the code). The following is a **decided target**, recorded in the execution plan — do not confuse it with what runs today:

- **Application:** containerized (current Dockerfile shape kept, hardened per plan P6-05).
- **Database:** **Supabase (PostgreSQL)** — supersedes MySQL 8 (plan assumption A-4). Current code is still MySQL; migration tasks: [ADD-01] adopt PG driver/config, [ADD-02] schema/data conversion → then the Flyway baseline (plan P3-01) is captured **on Postgres**.
- **File storage:** **Supabase Storage (S3)** with bucket security policies + server-side ownership enforcement — replaces the local-disk `uploads/` mount and closes FILE-01/FILE-07 mechanically (interval risk note in FILE-07 row remains until the integration lands).
- **Live-video: Zoom replaces Jitsi** (D-16, 2026-10-25): meetings created via the Zoom API with Zoom-native access control (passcode / require-authentication) instead of public `meet.jit.si` URLs; provisioning addendum [ADD-05].
- **Git uploads cleanup:** still required (FILE-06/DEP-06), independent of the new storage target.

Evidence/traceability: `plan/DECISIONS-REQUIRED.md` (D-05/07/14/22/25 outcome lines), `plan/ROADMAP.md §8`, and the Jira execution log (project "Edunest Backend", tasks EB-1..EB-84).
