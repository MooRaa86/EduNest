# EB-7 / P0-09 — V-08 Fresh-Database Deployment Rehearsal (MSH-05 + boot evidence)

**Task:** EB-7 ([P0-09] V-08: Fresh-DB deployment rehearsal, MSH-05 + boot evidence)
**Branch:** `feat/p0-09-fresh-db-rehearsal`
**Base:** `origin/main` @ `917df99d12f4197fa220ec6bfe0cd8a455bf0565` ("P1-11: Delete test push and platform mail endpoints per D-05 (EB-18) (#48)", 2026-11-01)
**Date:** 2026-11-01
**Nature:** rehearsal / verification-only evidence task. **`src/main` is zero-touch** — no production file was added, changed, or deleted on this branch; the final-audit `git diff` against the merge base contains exactly the four files inventoried in this document. The MSH-05 defect is proved, not fixed: per D-17 the default-commission-seed decision is still open and its fix is owned by Phase 3 (P3-05).

## 1. Question under investigation

V-08 asks: does a **freshly created** EduNest database — the exact state a first deploy boots into — serve prime-time traffic correctly? Two concrete scenarios were rehearsed against a virgin schema:

1. **Scenario A (MSH-05, defect):** the `commission` table has never been populated (no seed exists yet). A student tries to enroll in an ACTIVE mentorship on day one. Expected today: hard rejection. (The executed fixture used the `AuthFixture` default price 0.0 — the rate lookup throws before any price math matters, so the observed rejection covers even zero-price enrollments.)
2. **Scenario B (success path):** the commission rate is set to 10% via a direct repository fixture insert (the commission write path is only unit-covered in `MentorCommissionCalcTest`; its HTTP surface was not exercised here). A student enrolls in a 200-money mentorship. Expected today: enrollment succeeds and the money split is computed correctly.

Both scenarios are governed by:

- **D-27:** H2 in-memory only (MySQL compatibility mode), **no Docker / no Testcontainers** on P0 infrastructure.
- **D-17:** a default 10% commission seed via migration is still an OPEN decision, deferred to P3-05. **This task must not pre-decide it** — so no seeder was added to `src/main`.

## 2. Test artifact

`src/test/java/com/example/gradproj/EduNest/testing/FreshDbEnrollmentRehearsalTest.java` (new file, test-only) — a standalone prod-chain Spring Boot test (`@SpringBootTest(MOCK)` + `@AutoConfigureMockMvc` + `@ActiveProfiles({"prod","test"})`), deliberately **not** extending any existing base class, because the fresh-database property requires its own `@DynamicPropertySource`:

- It overrides `spring.datasource.url` with `jdbc:h2:mem:edunest-eb7-freshrehearsal-<UUID>;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1`.
- The UUID is generated once per class load. No other test context — in this JVM, a past run, or a future run — can share the same database name, so when boot log says "Added connection conn0: url=jdbc:h2:mem:edunest-eb7-freshrehearsal-…", that database did not exist one line earlier.
- The test profile (`src/test/resources/application-test.properties`) is untouched and shared-test config is unaffected; the shared `jdbc:h2:mem:edunest` databases used by the rest of the suite keep working (all 409 suite tests stayed green, §4 run B).

Principals and mentorship fixtures are seeded with the existing `AuthFixture` probe infrastructure (`AuthFixture.student(...)`, `AuthFixture.mentorship(mentorEmail, Status.ACTIVE)`, real JWTs signed with the test-profile secret) — no duplicated user-provisioning logic.

## 3. What "fresh database" means here (mechanism, honestly stated)

- The app has **no Flyway/Liquibase** and no SQL seed scripts anywhere (`pom.xml` has no migration dependency; no `src/main/resources/db` directory exists). The production schema is created by Hibernate DDL the same way the rehearsal schema is — `ddl-auto` (`create-drop` under test). So the rehearsal exercises the same schema-creation mechanism a real first deploy would use.
- No test resource creates data (`src/test/resources` contains only `application-test.properties`; there is no `data.sql` / `schema.sql`).
- The pristine-state assertions in order-1 run **before any code in this class has written anything**: commission, enrollment, total_points, students, mentors, admins, mentorships are all asserted **0 rows** at boot. This is boot state, not post-`deleteAll` state.
- One honest correction to earlier planning notes: the `RolesSeeder` CommandLineRunner **does** run under `@SpringBootTest` here — boot log prints "Role created: ADMIN / MENTOR / STUDENT" before the first test method executes. The tests therefore do not assert a role count; they observe it without depending on it.

## 4. Executed results (all numbers below are from real runs on 2026-11-01)

Environment: Windows 11, local Maven wrapper in the isolated worktree `D:\EduNest\wt-eb7-rehearsal`, local JDK 23 compiling with `release 21` (CI uses Temurin 21; first CI run still pending at report time).

### Run A — class only (`mvnw test -Dtest=FreshDbEnrollmentRehearsalTest`, 15:43+02:00)

First execution failed once, by design of the process (no placeholder was ever used):

- `freshDatabaseBootsTheAppWithEveryBusinessTableEmpty` expected the anonymous denial of `GET /admin/commission` to be **403 ERR_FORBIDDEN** and it actually was **401**, with this real response body:
  `{"timestamp":"2026-11-01T15:43:01.786…","status":401,"error":"Unauthorized","errorCode":"ERR_UNAUTHENTICATED","message":"Authentication required. Please log in.","path":"/admin/commission"}`.
  Cause: the JWT validator filter rejects headerless requests outright — nothing reaches `@PreAuthorize` for anonymous callers. The earlier hypothesis (advice-mapped 403) was **wrong**; the test was corrected to the observed application behavior, never the other way around.

Final Run A result: **Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS (29.6 s)**, boot log proving the unique fresh DB: `HikariPool-1 - Added connection conn0: url=jdbc:h2:mem:edunest-eb7-freshrehearsal-57687c4bec3641bf96bedcdd13a3e3fa`.

### Run B — full gate (`mvnw test`, 15:46+02:00)

**Tests run: 409, Failures: 0, Errors: 0, Skipped: 2 — BUILD SUCCESS (01:55 min).**

- The 2 skipped are the pre-existing `FileResponseBuilderTests` skips, present on `main` before this task.
- The rehearsal class ran again inside the suite and printed a **differently-UUIDed** fresh DB (`…freshrehearsal-b1ab6d7606634581898636f3d62516e3`), demonstrating the per-run freshness mechanism end to end.
- No EB-90-style Windows teardown failures were observed in this run.

## 5. Scenario A — MSH-05 defect reproduced on a virgin schema (P0 evidence)

Method (order 2 of the class): assert commission/enrollment/total_points/students/mentors/mentorships all still 0 → seed one student + one ACTIVE mentorship (AuthFixture default price 0.0) → assert commission is still 0 (no seeder exists) → `POST /api/v1/mentorship/…/join` with a real student JWT.

Observed:

- **HTTP 400**, body shape `errorCode = ERR_BUSINESS_RULE`, `message = "Commission rate not set"` (advice-mapped from `globalLogicEx`, per the EB-12/G-register error contract).
- Zero side effects: after the request, `enrollment` = 0, `total_points` = 0, `commission` = 0 rows. The failure happens before any enrollment write (`mentorShipServiceI.joinMentorship` computes `priceAfterDiscount` then calls `commissionService.calculatePlatformCut`, which throws on the empty `commission` table).
- Conclusion: **on a fresh deployment, student enrollment is completely unavailable until an admin manually sets a commission rate.** This is exactly the MSH-05 production defect, reproduced on the cleanest possible schema. It stays OPEN — the fix (seed decision) is D-17 / P3-05, and `plan/ISSUE-MAPPING.md` continues to mark MSH-05 as verification-first / Planned pending that decision.

## 6. Scenario B — 10% commission success path with exact money split (P0 evidence)

Method (order 3 of the class): new student + new mentorship, price set to 200.0 (discount 0), one fixture row `Commission(rate=10.0)` (test-only data, NOT a production seed), then `POST /join` again.

Observed:

- **HTTP 200**, body `$.apiResponse.message == "Mentorship joined successfully"` (`SimpleResponse` envelope, as the FE consumes it).
- Exactly one `enrollment` row: `price == 200.0` (±1e-9), `platformProfit == 20.0` (±1e-9 → 10% of 200), `joinedAt` set, and ownership verified through the repository's `existsByMentorShip_IdAndStudent_Id` (detached-lazy safe; mentor name/email equals the fixture mentor).
- Exactly one `total_points` row for that (student, mentorship) pair with `totalPoints == 0` — the leaderboard baseline written by the join flow.
- Implied mentor payout `200 − 20 = 180`, consistent with `MentorCommissionCalcTest` unit coverage (12/12 green in the same suite run).

## 7. SEL-02 boot-evidence extraction (git-history ruling honored, no invented runtime)

Ruling applied: SEL-02 is fixed and locked on `origin/main`; this task provides (a) the verbatim git evidence of what the historical defect was and (b) fresh runtime evidence from the current executable boot matrix — without attempting to boot old code.

- **Historical evidence (verbatim from git, visited this session):** pre-fix tree `2722160^` contained a permissive default chain exposed via `@Profile("!prod")` plus a separate `ProjectSecurityProdconfig` for the prod chain; the post-fix state introduced by `af50e58` unified both into `ProjectSecurityConfig` with explicit secure fallback (unsafe-dev mode explicitly refused while `prod` is active — confirmed again in this run's boot log: `EDUNEST_UNSAFE_DEV=true is ignored while the 'prod' profile is active — the secure JWT chain is in force`).
- **Runtime evidence (executed today, run B):** `SecurityProfileBootMatrixTest` — **5/5 tests green**; security probes suites (AdminBootstrapProbesTest, typed-dedicated-download enforcement, UploadValidationHardeningProbeTest, etc.) all green in the same run.

## 8. Side observations (non-blocking, for the planning file)

- Anonymous denial for missing JWT is uniformly **401 `ERR_UNAUTHENTICATED`** at the entry point (before any `@PreAuthorize` evaluation) — worth keeping in mind when consumers of the ApiError contract plan FE flows.
- `/v3/api-docs` answers 200 on a fresh prod-chain context (permitAll as configured) — used as the harmless "app is alive against the virgin DB" check in order 1.

## 9. Definition-of-Done accounting

| DoD item | Status |
|---|---|
| Truly fresh H2 schema and initial state (no Docker, D-27) | DONE (§3) — unique-UUID H2 per class load, pristine counts asserted before any write |
| MSH-05 defect reproduced (P0 evidence) | DONE (§5) — 400 `ERR_BUSINESS_RULE`, zero persistence side effects |
| 10% fixture commission enrollment succeeds with correct split | DONE (§6) — 200; price 200 → platform profit 20.0 ±1e-9; TotalPoints 0 baseline row |
| Full Maven test suite passes (Elisha gate) | DONE (§4 run B) — 409 run / 0 failed / 0 errored / 2 skipped (pre-existing), exit 0 |
| New test file adds rehearsal-only coverage, prod untouched | DONE (§2; final-audit `git diff` contains exactly the four files inventoried here, none from `src/main`) |
| Evidence doc linking to EB-7/MSH-05 with live reflections | DONE — this document |

**Open items left for others (intentionally):** D-17 decision + MSH-05 fix (P3-05); CI confirmation of the 409-test gate on JDK 21 (first push of this branch); local runs used JDK 23 with `release 21` compile.
