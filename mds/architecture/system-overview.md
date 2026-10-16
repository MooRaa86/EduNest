# System Overview

Companion document to [MDS.md](MDS.md). Everything here is verified against the current source tree.

## 1. Technology Stack (as built)

| Layer | Technology | Version | Configuration source |
|---|---|---|---|
| Language | Java | 21 (wrappers/JDK 24 on build host) | `pom.xml` `<java.version>21</java.version>` |
| Framework | Spring Boot | 3.5.7 (parent 3.5.7) | `pom.xml` |
| Security | Spring Security 6, jjwt | 6.5.6 (via Boot), 0.12.5 | `config/security/**`, `service/jwt/JwtService` |
| ORM | Spring Data JPA / Hibernate | 6.6.33.Final | `spring.jpa.*` properties |
| Database | MySQL (mysql-connector-j) | 8.0.33 | `${DATABASE_URL}` env |
| Real-time | Spring WebSocket (STOMP + SockJS, simple broker) | via Boot 3.5.7 | `config/webSocket/WebSocketConfig` |
| Mail | Spring Mail (SMTP STARTTLS) | via Boot | `${MAIL_*}` env |
| Validation | Jakarta Validation + custom validators | via Boot | `dto/**`, `annotation/`, `validation/` |
| File typing | Apache Tika | 2.9.2 | `service/fileSotageService/FileStorageService` |
| PDF | iText7 | 7.2.5 | only `service/admin/BadgePdfGeneratorService` |
| OpenAPI | springdoc-openapi-starter-webmvc-ui | 2.7.0 | `config/swagger/OpenApiConfig` |
| Templates | Thymeleaf (email HTML) | via Boot | `resources/templates/emails/*.html` (10 templates) |
| Build | Maven (wrapper `mvnw`, Maven 3.9.11) | – | `pom.xml`, `.mvn/` |
| Container | Docker, eclipse-temurin-21-jre runtime image | – | `Dockerfile` |
| Lombok | 1.18.42 (annotation-processor path) | – | `pom.xml` build plugins |

Dependencies of note (leftovers / unused): `org.openjfx:javafx-base:20` (no JavaFX usage found in code — flagged as dead dependency in `pom.xml:33-37`), `spring-boot-devtools` (runtime/optional only; excluded from boot jar by the Maven plugin excludes).

## 2. Package Map (verified counts)

- `entity/` — **43 entity classes** (including `BaseEntity`, `Roles`; `chat/Message`, `chat/Conversation`, `chat/ChatRoomMember`, `skill/StudentSkill` are plain classes extending nothing; `Roles` also has no audit columns).
- `controller/` — **33 `@RestController` classes** + 2 STOMP controllers.
- `service/` — 28 sub-packages; implementation classes commonly named `*ServiceI` (impl) with sibling interface `*Service` (naming is inverted in several spots, e.g. `service/auth/LoginService` is the interface, `LoginServiceI` the implementation; `service/mentorShip/mentorShipServiceI` is the impl).
- `repository/` — Spring Data interfaces + many **projection interfaces** under `<domain>/projection(s)` for read models (dashboard stats, leaderboards, admin lists).
- `dto/` — per-domain request/response sub-packages incl. `request/`, `response/` splits.
- `exception/` — `GlobalExceptionHandler` (`@RestControllerAdvice`), `exception/ErrorResponse`, domain exception packages (`registerExceptions`, `authHandling`, `jwt`, `globalLogicException`), plus `WebSocketExceptionHandler` (`@MessageExceptionHandler` → `/queue/errors`).
- `config/` — `security/ProjectSecurityConfig` (`!prod`), `security/ProjectSecurityProdconfig` (`prod`), `auth/EduNestAuthenticationProvider` + `EduNestUserDetailsService`, `webSocket/*` (4 classes), `swagger/OpenApiConfig`, `WebConfig` (static uploads serving), `RolesSeeder`.
- `filters/` — `JwtTokenValidatorFilter` (registered in prod chain), `JwtTokenGeneratorFilter` (registered but effectively dead — see below).
- `auditing/` — JPA auditing wiring; auditor = current principal email, else `SYSTEM`.
- `features/roadmap/**` — **empty package skeleton** (no Java files, no references — verified by glob/grep).

## 3. HTTP Request Lifecycle (prod profile)

1. **Tomcat embed** receives the request (`spring-boot-starter-tomcat`, no custom server config; port 8080 from Dockerfile `EXPOSE`).
2. **CORS filter** — inline `CorsConfigurationSource` in `ProjectSecurityProdconfig#getCorsConfiguration`-equivalent (anonymous inner class): `allowedOriginPatterns("*")` + `allowCredentials(true)` + `exposedHeaders=["Authorization"]`.
3. **`JwtTokenValidatorFilter`** (`filters/JwtTokenValidatorFilter`) — registered `addFilterBefore(..., BasicAuthenticationFilter.class)`:
   - Reads `Authorization` header (`Constants.JWT_HEADER`).
   - `shouldNotFilter` returns true **only for `/login-api`** (line 56) → an invalid/expired token returns 401 JSON even if the endpoint is otherwise public-bearing (e.g. `/api/v1/register/**` with stale `Authorization`).
   - `JwtService.validateToken` checks signature+expiry, then `UserRepository.isUserEnabledAndNotDeleted(username)`; admins are exempt from enabled/deleted checks.
   - On success sets `UsernamePasswordAuthenticationToken(username, null, authorities-from-claim)`; **authorities come from the token claims** (role changes require re-login; tokens valid 7 days).
4. **DispatcherServlet** → controller → `@PreAuthorize("hasRole(...)")` (`@EnableMethodSecurity` on both configs).
5. **Service layer** performs object-level authorization via `SecurityService` (`service/security/SecurityService`), `AuthorizationRepository` existence queries, or `CurrentUserProvider` (`security/CurrentUserProvider`, `@RequestScope` bean — see risks).
6. **Persistence** via repositories; **side effects**: `EmailService.sendEmail` (`@Async`), `NotificationService` (persist + STOMP push), file writes under `uploads/`.
7. **Errors**: `exception/GlobalExceptionHandler` → JSON `ErrorResponse`/`Map`; security entry point/denied handler produce their own JSON shapes; the WS `@MessageExceptionHandler` produces a plain string on `/queue/errors`. Contract detail: [../api/error-responses.md](../api/error-responses.md).

Dev profile (`!prod`): same servlet, but `ProjectSecurityConfig` chain — everything `permitAll()` (`.anyRequest().permitAll()` line 53), headers/CSRF/httpBasic/formLogin enabled, CORS `*` (no credentials), no JWT filters. **Local default run = fully open API.**

## 4. Runtime Characteristics & Configuration

| Aspect | Value (verified) |
|---|---|
| Profiles | default + `prod` (`spring.config.activate.on-profile=prod`); dev chain = `@Profile("!prod")` |
| Session | STATELESS in prod only |
| CSRF | disabled in both profiles |
| httpBasic / formLogin | disabled in prod; **enabled** in default profile |
| Multipart cap | 10MB (`spring.servlet.multipart.max-*`) |
| Uploads directory | relative CWD `uploads/` (`app.upload.base-dir` default) — repo also ships committed files under `uploads/` |
| Static serving | `config/WebConfig` maps `/uploads/**` → `file:uploads/`; prod properties add `file:uploads/` to static locations |
| Scheduling | `@EnableScheduling`; jobs: OTP cleanup hourly (`OtpCleanupService.removeExpiredOtps`) |
| Async | `@EnableAsync` (mail, notification fan-outs) |
| DDL | `hibernate.ddl-auto=update` + `show-sql=true` in **both** profiles |
| Jackson | `fail-on-unknown-properties=true` in prod only |
| Health checks | **none** (no actuator dependency, no Docker `HEALTHCHECK`) |
| Logging | default Spring console logging; **no logback config**; 4× `System.out.println` (OtpCleanupService, QuizSubmissionServiceImpl:97, JwtChannelInterceptor:153, RolesSeeder:24) |

## 5. Cross-cutting Conventions

- **Response envelope:** many controllers return `dto/SimpleResponse` (map-like) with 200/201; others return typed DTOs or `PageResponse<T>` for paging. No uniform envelope is enforced — documented in [../api/MDS.md](../api/MDS.md).
- **Business error signal:** `exception/globalLogicException/globalLogicEx` reused as the generic 400 "logic" exception across domains.
- **Entity auditing:** `BaseEntity` (`id`, `createdAt`, `updatedAt`, `createdBy`, `updatedBy`) for almost everything; exceptions listed in [MDS.md §3.4](MDS.md#34-persistence-pipeline).
- **ID semantics:** sequential IDs exist across most tables (paths embed user IDs for admin badges/profiles), making enumeration feasible where object-level checks are missing (see [../ISSUES.md](../ISSUES.md) IDOR group).
- **Ownership-check pattern:** `SecurityService` + `AuthorizationRepository` native/JPQL existence queries (`isMentorOwnWeek`, `isMentorOwnLiveSession`, `isStudentEnrolledByLiveSessionId`, `isUserMemberOfChatRoom`, ...). Coverage is documented per domain; known gaps are raised in the domain docs and [../ISSUES.md](../ISSUES.md).

## 6. Build & Run Overview (operational sketch)

- Build: `mvnw clean package` (wrappers present; Maven pulls deps from central; **tests exist but Docker builds pass `-DskipTests`**).
- Run: `java -jar target/EduNest-0.0.1-SNAPSHOT.jar` with `spring.profiles.active=prod` (default from Dockerfile env) and database + mail env vars set; creates `uploads/` on demand.
- Required env vars: `DATABASE_URL`, `DATABASE_NAME`, `DATABASE_PASSWORD`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, optionally `JWT_SECRET` (falls back to a **hardcoded** value if unset — critical risk SEL-01). Full deployment detail: [../deployment/MDS.md](../deployment/MDS.md).
- API docs: `/swagger-ui/**` and `/v3/api-docs/**` are permitAll in dev **and prod** (`ProjectSecurityProdconfig` matchers).

## 7. Non-Goals / Not Implemented (verified absence)

- **Roadmap feature:** empty skeleton (`features/roadmap/**`), no endpoints/entities.
- **Payment gateway integration:** none exists — "payments" are revenue *reporting* over `Enrollment` price/profit snapshots.
- **Search engine integration:** external search (Elasticsearch/Meilisearch/etc.) is only a *plan* (see `mds/database/search_indexing_plan.md`); current search is `LIKE` queries over `MentorShip`.
- **Refresh tokens / logout blacklisting:** absent by design.
- **Rate limiting / brute-force protection:** absent (no bucket4j/resilience deps).
- **Health/observability endpoints:** absent.
