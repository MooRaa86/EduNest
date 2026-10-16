# Security Configuration (profiles, filters, CORS, roles)

Verified against `src/main/java/com/example/gradproj/EduNest/config/security/**` and `filters/**`.

## 1. Two SecurityFilterChain configurations

| Aspect | `ProjectSecurityConfig` (`@Profile("!prod")`) | `ProjectSecurityProdconfig` (`@Profile("prod")`) |
|---|---|---|
| Session policy | default (IF_REQUIRED, sessions allowed) | `STATELESS` |
| CSRF | disabled | disabled |
| httpBasic | **enabled** (`Customizer.withDefaults()`) | disabled |
| formLogin | **enabled** | disabled |
| JWT filters | none | `JwtTokenValidatorFilter` before `BasicAuthenticationFilter`; `JwtTokenGeneratorFilter` after (generator is dead code — see below) |
| Rule behavior | explicit matchers `permitAll()` **and `.anyRequest().permitAll()`** (line 53) | public/admin/authenticated matchers + `.anyRequest().authenticated()` (line 117) |
| CORS | `*` origins/methods/headers, `allowCredentials=false` | `*` origin patterns, `*` methods/headers, **`allowCredentials=true`**, exposes `Authorization`, maxAge 3600 |
| Exception handling | none custom | `EduNestAuthenticationEntryPoint` (401 JSON + header `EduNest-error-reason`) and `EduNestAccessDeniedHandler` (403 JSON + header `EduNest-denied-reason`) |
| Auth manager | `ProviderManager(EduNestAuthenticationProvider)`, `eraseCredentialsAfterAuthentication=false` | same |
| Method security | `@EnableMethodSecurity` | `@EnableMethodSecurity` |

**Operational consequence:** Spring Boot's active-profile default is *no* `prod` → a plain `mvn spring-boot:run` or IDE run uses the wide-open chain. The Docker image sets `SPRING_PROFILES_ACTIVE=prod` (`Dockerfile` line 13).

## 2. Prod URL rule map (`ProjectSecurityProdconfig.filterChain`)

### permitAll (lines 69–97)
```
/swagger-ui/**, /v3/api-docs/**, /swagger-resources/**
/api/auth/**, /api/v1/register/**, /login-api, /forget-password/**
/api/v1/admin/register
/ws/**
/uploads/mentorship-cover-images/**, /uploads/profile-images/**,
/uploads/student-profile/**, /uploads/mentor-profile/**,
/uploads/mentor/**, /uploads/student/**, /uploads/chatroom/**,
/uploads/**                     ← catch-all, duplicated; code comment L88:
                                  "//ToDo public uploads until front make the security on it"
/api/v1/contact/save-message
/api/v1/student/mentorships/*/overview, /api/v1/student/mentorships/*/reviews
/api/v1/mentorship/explore, /api/v1/mentorship/categories
/api/v1/profile/mentor/**
```

### hasRole("ADMIN") (lines 99–107)
```
/api/v1/admin/**, /admin/**, /api/admin/**, /api/users/*/badges
/api/v1/contact/all-messages, /api/v1/contact/message/**, /api/v1/contact/messages/**
```

### authenticated (lines 109–114)
```
/settings/**, /api/v1/notifications/**, /student/profile/**, /api/v1/file/**
```

### anyRequest().authenticated()
Everything else — protected from anonymous, but **not role-restricted** (role checks only via `@PreAuthorize` or service checks; see [authorization-rules.md](authorization-rules.md)).

Notable consequences:
- Swagger UI + OpenAPI docs are public **in prod**.
- `/uploads/**` fully public (all folders; including `submissions/` — student work).
- `/lectures/**`, `/api/v1/task/**`, `/api/v1/project/**`, `/api/v1/quiz/**` etc. are `authenticated()` — anonymity blocked, but any logged-in user passes.

## 3. JWT filter mechanics

- **Validator** (`filters/JwtTokenValidatorFilter`): runs in prod; skips only `/login-api`; sets `Authentication(username, null, authorities-from-claims)`; re-checks `enabled`/`deleted` from DB (except admin); writes its own 401 JSON body (`"Invalid JWT token"` or the deactivated message) — a **third JSON error shape** alongside `GlobalExceptionHandler` and the entry point.
- **Generator** (`filters/JwtTokenGeneratorFilter`): registered, but `shouldNotFilter` exempts everything except `/test-login` — a path **no controller exposes**. Effectively unreachable; login happens in `service/auth/LoginServiceI` instead.

## 4. Roles & seeding

- `config/RolesSeeder` (CommandLineRunner): inserts `ADMIN`, `MENTOR`, `STUDENT` into `roles` if missing; `System.out.println` on registration.
- Role constants referenced as `SystemUtils.STUDENT/MENTOR/ADMIN` in services; authority strings are `ROLE_*` from JWT claims.

## 5. CORS summary

- Dev: `allowedOriginPatterns(["*"])`, methods/headers `*`, credentials **false**.
- Prod: `allowedOriginPatterns(["*"])` (reflection of any origin), `allowCredentials` **true** — flagged as dangerous-but-ineffective-while-beta (no cookies; bearer header auth). Recommend restricting origins before adding cookie auth. Full risk entry: [security-risks.md](security-risks.md#cors).

## 6. Error handlers (security layer)

- `exception/authHandling/EduNestAuthenticationEntryPoint` — 401 JSON `{timestamp,status,error,message,path}` + header `EduNest-error-reason: Authentication failed`.
- `exception/authHandling/EduNestAccessDeniedHandler` — 403 JSON, header `EduNest-denied-reason: Authorization failed`.
- Only wired in the **prod** chain (`ProjectSecurityProdconfig` lines 119–122).
- HTTP-401-vs-403 JSON shape differences vs `GlobalExceptionHandler` are consolidated in [../api/error-responses.md](../api/error-responses.md).

## 7. Custom validators

| Validator | Applied to | Behavior |
|---|---|---|
| `annotation/FieldsValueMatch` + `FieldsValueMatchValidator` | `dto/register/RegisterRequestDto` (`password` vs `confirmPassword`) | BeanWrapper comparison, error at `fieldMatch` |
| `validation/PasswordMatches` + `PasswordMatchesValidator` (class-level) | `dto/auth/RegisterRequest` (legacy, otherwise unused) | plain equals |
| None | `ChangePasswordRequest`, reset DTO | manual equals in `SettingsService.changePassword` / DTO constraints |

Password length min 8 on register/reset DTOs; **no complexity rules** anywhere.
