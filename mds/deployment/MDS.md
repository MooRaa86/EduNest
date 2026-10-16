# Deployment & Operations — Primary Domain Document

## 1. Overview

A single Spring Boot fat-jar served in Docker (prod profile forced), MySQL and SMTP as external dependencies, file uploads on the container's local filesystem. **There is no CI/CD** (`.github/` is empty — verified), no health checks, no observability, and no separation of artifacts from runtime data.

Supporting docs: [environment-configuration.md](environment-configuration.md) (env vars & profiles matrix) · [production-readiness.md](production-readiness.md) (gap checklist & priorities).

## 2. Current Implementation

### 2.1 Artifacts & build

| Item | Value (verified) |
|---|---|
| `pom.xml` | Java 21, Boot 3.5.7 parent; springdoc 2.7.0, jjwt 0.12.5, Tika 2.9.2, iText7 7.2.5, mysql-connector-j 8.0.33, javafx-base 20 (unused), Thymeleaf |
| Build command | `.\mvnw.cmd clean package -DskipTests` (wrapper = Maven 3.9.11) |
| Output | `target/EduNest-0.0.1-SNAPSHOT.jar` |
| Docker | multi-stage: `maven:3.9-eclipse-temurin-21` (`mvn dependency:go-offline`, `mvn clean package -DskipTests`) → `eclipse-temurin:21-jre`; `mkdir -p uploads`; `EXPOSE 8080`; **`ENV SPRING_PROFILES_ACTIVE=prod`**; plain `ENTRYPOINT ["java","-jar","app.jar"]` |

### 2.2 Reality of orchestration

- **No `docker-compose.yml`** committed; MySQL connectivity is expected from env vars (`DATABASE_URL` is a full JDBC URL or host? — handled by naming convention, see [environment-configuration.md](environment-configuration.md)).
- **No volume mounts:** `uploads/` lives inside the container layer → **redeploy wipes all uploaded files** unless operators mount storage manually (FILE-07 / DEP-03).
- `.dockerignore` excludes target/IDE noise (and `uploads/` was previously listed for the build context — images therefore start with an empty uploads tree).
- **CI/CD: none.** `.github/` contains no workflows (verified by glob) — no build/test/image pipeline beyond local `docker build`.
- **Health/observability: none.** No actuator dependency, no `HEALTHCHECK`, no logback config (default console logs; 4 × `System.out.println` debug leftovers [../architecture/system-overview.md](../architecture/system-overview.md#4-runtime-characteristics--configuration)).
- `server.forward-headers-strategy=framework` — okay behind a proxy as long as the proxy sets forwarded headers.

### 2.3 Runtime entry points

| Surface | Exposure |
|---|---|
| HTTP API | container port 8080 |
| Swagger UI | `/swagger-ui/**` — public **in prod** |
| Static uploads | `/uploads/**` — public (ToDo comment in code) |
| STOMP | `/ws` (SockJS) — permitAll at HTTP layer |

## 3. Known Operational Risks (IDs in ../ISSUES.md)

- **DEP-01 (High):** no CI/CD at all — no automated build/test/image/deploy; regressions reach prod unverified (also TST-03: `-DskipTests`).
- **DEP-02 (High):** no health/actuator endpoints; orchestrators can't gate readiness/liveness.
- **DEP-03 (High):** uploads not externalized (volume/bind/managed storage) — data loss on container replacement + single-node file server assumptions.
- **DEP-04 (Medium):** `ddl-auto=update` + `show-sql=true` in prod (schema churn, noisy logs) — DB-01.
- **DEP-05 (Medium):** JWT secret not wired in deployment config (no env var declared anywhere; hardcoded fallback applies — SEL-01).
- **DEP-06 (Medium):** committed real user files under `uploads/` in git (privacy + repo bloat).
- **DEP-07 (Medium):** no staging/prod config split beyond profile properties; secret management story absent (DB/mail creds via env — fine; JWT secret excluded).
- **DEP-08 (Low):** no backup/restore plan documented; DB-side lifecycle policies (expiry jobs) exist only for OTP.

## 4. Deployment runbook (as-is, minimal)

1. Provision MySQL 8 + SMTP credentials.
2. `docker build -t edunest .` → run with env vars: `DATABASE_URL`, `DATABASE_NAME`, `DATABASE_PASSWORD`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` (+ `JWT_SECRET` **if you want a non-committed secret** — note `LoginServiceI` bug SEL-01 requires the code fix before that env var works).
3. First boot: `ddl-auto=update` creates schema; seed roles auto (`RolesSeeder`); **set commission rate** via admin `PATCH /admin/commission` before enrollments can work (MSH-05).
4. Create the first admin via public `POST /api/v1/admin/register` (single-admin guard).
5. Mount a volume for `/app/uploads` if intending persistence (currently absent by default).

## 5. Open Questions

- Target platform: single VM + nginx? PaaS with managed MySQL? Kubernetes? (Decides health checks, volumes, scaling of the in-memory STOMP broker — [../architecture/MDS.md](../architecture/MDS.md) Q1.)
- Is horizontal scale anticipated? The simple broker + local file storage are single-node bound.
- Who owns secret distribution (CI secrets vs .env vs vault)?

## 6. Related Documentation

[environment-configuration.md](environment-configuration.md) · [production-readiness.md](production-readiness.md) · [../database/MDS.md](../database/MDS.md) (schema evolution) · [../file-storage/MDS.md](../file-storage/MDS.md) (storage) · [../ISSUES.md](../ISSUES.md)
