# Environment Configuration

Complete configuration surface (verified against `application.properties`, `application-prod.properties`, `Dockerfile`, and the code that reads env values).

## 1. Required environment variables

| Variable | Used by | Notes |
|---|---|---|
| `DATABASE_URL` | `spring.datasource.url` | Full JDBC URL expected by the naming convention (e.g. `jdbc:mysql://host:3306/db`) |
| `DATABASE_NAME` | `spring.datasource.username` | (name holds the *user*) |
| `DATABASE_PASSWORD` | `spring.datasource.password` | – |
| `MAIL_HOST`, `MAIL_PORT` | `spring.mail.*` | SMTP |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | `spring.mail.*` | SMTP auth creds |
| `JWT_SECRET` (optional today) | `JwtService.getSecretKey` (`env.getProperty("JWT_SECRET", default)`) | **Not declared in Dockerfile/properties** — if unset, signing uses the hardcoded fallback (`jxgEQe…`). If set to a non-default value, login role-parsing breaks (SEL-01 code bug) until fixed. Minimum 32 bytes (`hmacShaKeyFor` throws otherwise; no startup validation) |
| `SPRING_PROFILES_ACTIVE` | profile selection | Defaulted to `prod` in the Dockerfile; absence locally runs the **wide-open `!prod` chain** (SEL-02) |

## 2. Properties matrix (dev/default vs prod profile)

| Setting | default (`application.properties`) | `prod` (`application-prod.properties`) |
|---|---|---|
| DDL | `ddl-auto=update` | `ddl-auto=update` (same) |
| SQL logging | `show-sql=true`, `format_sql=true` | same (**prod-noisy**, DEP-04) |
| Jackson unknown fields | default (ignored) | `fail-on-unknown-properties=true` (contract drift — API-05) |
| Static resources | – | `static-locations = classpath:/static/, file:uploads/` |
| Multipart caps | 10MB file / 10MB request | same |
| Mail | same `${MAIL_*}` set | same |
| Forward headers | `framework` | `framework` |
| springdoc UI | `doc-expansion=none`, alpha tag sort | inherits (no override block) |

Additional prod-profile-only hardcoding: none for JWT (see §1).

## 3. Filesystem layout expectations

| Path | Creator | Persisted how |
|---|---|---|
| `uploads/` (CWD-relative; `${app.upload.base-dir:uploads}`) | `FileStorageService#@PostConstruct` + Dockerfile `mkdir -p uploads` | **container-local** (no volume — DEP-03) |
| `uploads/mentorship-cover-images/{id}-EduNest-{uuid}.{ext}` | `ImageStorageService` | same |
| `uploads/{student|mentor|admin|chatroom}/…` | `ImageStorageService.saveImage` | same |
| `uploads/task-attachment|project-attachment|submissions/…` | `FileStorageService` | same |

`config/WebConfig` serves `/uploads/**` → `file:uploads/` (public in both profiles).

## 4. Profile-activation pitfalls

1. **Default (no profile) = dev chain:** `anyRequest().permitAll()` + httpBasic + formLogin enabled. IDE runs and CLI `spring-boot:run` without `-Dspring.profiles.active=prod` are **not** production-safe (SEL-02 / ARCH-001).
2. The Dockerfile always sets `prod`; when running locally, set the env var yourself to exercise the hardened chain — `SPRING_PROFILES_ACTIVE=prod` (watch for typos: a misspelled variable silently degrades to the open dev chain).
3. The `prod` profile also flips static-locations (adds `file:uploads/`); earlier team docs recorded only the file-serving diff, but the Jackson strictness delta is prod-only too (see matrix).

## 5. Local runbook (JDK note)

- JDK: `pom` targets 21; wrapper historically ran JDK 24 on this workstation; IDE run config uses `C:\Program Files\Java\jdk-21`. Align builds on 21.
- Commands: `.\mvnw.cmd dependency:resolve -U` (first-time), `.\mvnw.cmd clean compile -DskipTests`, `.\mvnw.cmd spring-boot:run` with env vars (or set `prod` profile to test the hardened chain).
- Grace period: `EduNestApplicationTests` needs DB+mail env; run `.\mvnw.cmd test` only with env resolved.

## 6. Configuration debt (IDs in ../ISSUES.md)

- DEP-05: JWT secret handling (above).
- DEP-07: no compose/k8s manifest tracked; no `.env.example`.
- SEL-01/SEL-02 cross-references; DB-01 (`ddl-auto`), DEP-04 (show-sql), API-05 (Jackson strictness delta).
