# External Services Detail

Companion tables to [MDS.md](MDS.md). All usages verified by grep on `src/main/java`.

## 1. SMTP configuration

| Setting | Source |
|---|---|
| `spring.mail.host/port/username/password` | `${MAIL_HOST}`, `${MAIL_PORT}`, `${MAIL_USERNAME}`, `${MAIL_PASSWORD}` env |
| `mail.smtp.auth` | true (both profiles) |
| `mail.smtp.starttls.enable` / `.required` | true / true (opportunistic→required STARTTLS) |

`EmailService` (service/register/EmailService): `@Async` `sendEmail(...)`, template loader `getEmailTemplate(name)` reads from `templates/emails/` (line 69), manual `String.replace` merges, `sendHtmlEmail` wraps `JavaMailSender` with try/catch (failures print, never rethrow).

## 2. Email template usage map

| Template file | Referenced by | Purpose |
|---|---|---|
| `otp-template.html` | `RegisterServiceImpl` (via `template` variable, line 67 path) | Registration verify OTP (6-digit, 2-min) |
| `forget-password-otp-template.html` | `RegisterServiceImpl.forgetPassword` | Reset OTP |
| `restore-account.html` | `RegisterServiceImpl.restoreAccount` (line 282) | Deleted-account restore OTP |
| `delete-account.html` | `SettingsService.deleteAccount` (line 143) | Account delete OTP |
| `change-email.html` | `SettingsService` (line 60) **and** `AdminProfileAndSettingsService` (line 117) | Change-email OTP (sent to the *new* address) |
| `badge-award.html` | `AdminBadgeService.awardBadgeToUser` (line 160) | Admin badge award + `BadgePdfGeneratorService` PDF attachment; values escaped via `HtmlUtils`; sent `afterCommit` |
| `admin-reply.html` | `ContactMessageService` (line 111) | Admin reply to a contact-us message |
| `admin-message.html` | `AdminUserDirectory` (line 198) | Admin → user direct message (also mirrored to admin-management endpoint `/api/v1/admin/users/send-email`) |
| `general-mail.html` | `PlatformMailController POST /send` (line 22) | Free-form platform email (`{{name}}`, `{{message}}`) — **public endpoint** |
| `certificate-template.html` | **no references** | Dead artifact (certificates have no email/PDF flow) |

## 3. Jitsi Meet

- `service/livesession/JitsiService#createRoomLink(sessionId)` → `https://meet.jit.si/EduNest_Session_{sessionId}_{UUID}`.
- No configuration properties (`meet.jit.si` hardcoded), no JWT claims, no signing library.
- Room distribution relies on `LiveSessionServiceImp#joinSession` (enrollment/mentor audience check) — the backend governs *when* the URL is revealed, it does not authenticate *room entry*.
- Verified absent: `jitsi` / `JWT` / `JAM` config keys in both properties files.

## 4. Java libraries with "integration" weight

| Library | Modules touched | Notes |
|---|---|---|
| jjwt 0.12.5 | `JwtService` (sign/verify), `LoginServiceI` (role parse) | HS256; `Keys.hmacShaKeyFor` (throws on <32-byte secrets — startup-unverified) |
| Apache Tika 2.9.2 | `FileStorageService` MIME sniffing | Submission/attachment path only |
| iText7 7.2.5 | `BadgePdfGeneratorService` only | Admin badge certificates; NOT student certificates |
| Thymeleaf (starter) | Not actually used by the mail path (manual replace) — starter present for possible future use |
| springdoc 2.7.0 | `/swagger-ui`, `/v3/api-docs`; `OpenApiConfig` metadata | Public in prod |
| mysql-connector-j 8.0.33 | Datasource | env-driven URL/user/password |

## 5. "Contact Us" subsystem note

Though an internal feature, its support email goes through the same SMTP/templates ([../api/endpoints.md](../api/endpoints.md) Contact Us). The reply path resolves the original sender by email via `userRepository` — **registered users only**: `POST /api/v1/contact/message/{id}/notification` throws `UsernameNotFoundException` (404) for guest senders (NOTIF-02), while `reply` sends the email directly — both flows documented in [../notifications/MDS.md](../notifications/MDS.md).
