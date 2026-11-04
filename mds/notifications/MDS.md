# Notifications — Primary Domain Document

## 1. Overview

The in-app notification system: shared notification rows fan out to user relations, pushed live over STOMP user queues. **Only real-time channel — no email/SMS/push-notification channel for notifications** (emails exist separately for account ops and admin flows).

## 2. Current Implementation

### 2.1 Entities
- `entity/notification/Notification` — `title`, `content` (`@Size(max=700)`), `type` (`NotificationType`: ANNOUNCEMENT, QUIZ, SESSION, TASK, PROJECT, SUPPORT, BADGE, CERTIFICATE, LIVE_SESSION, MENTORSHIP, REVIEW).
- `entity/notification/UserNotification` — user FK + notification FK; `is_read` boolean default false; unique (user_id, notification_id); index `idx_notifications_user_id`. Read-state lives on the relation.
- `entity/notification/AdminNotification` — `title`, `content`, `type`, `admin_id`.
- `repository/notification/*` — paged read-with-notification queries, unread queries/counts, `@Modifying` mark-read/delete-all operations.

### 2.2 Service API and all callers (verified by grep, 46 call sites)

| Method | Behavior | Callers |
|---|---|---|
| `sendToUserByEmail(email,title,content,type)` | persist `Notification` + relation, STOMP push → `/user/{email}/queue/notifications` (runs **inside caller transactions**) | Task submission/grade, project submission/grade, quiz submission (both sides), badge award (mentor + admin), certificate per-student, contact-admin reply attempt, live-session attendance results, mentorship enroll/rate, dashboard reader, admin→user SUPPORT message |
| `sendToMentorshipStudents(mentorshipId,...)` `@Transactional @Async` | one `Notification` row + relations, loop-push to each student's queue | task publish/update/close x3, quiz x4, project x3, session x5 (scheduled/updated/cancelled/started/ended), mentorship completed |
| `sendToAdmin(title,...)` | persists `AdminNotification` bound to **`adminRepository.findTopByOrderByCreatedAtDesc()` — newest admin only**; pushes to that one admin's queue | mentorship published/completed (from mentor flows), admin test-send endpoint |
| read-side (`getUserNotifications`, `getUnreadCount`, `getUnread…`, `markAllAsRead`, `markOneAsRead`, `deleteNotification`, `deleteAll…`) | relation-scoped CRUD | `NotificationController` (`/api/v1/notifications/**`), `DashboardService` |
| admin read-side (`getAdminNotifications`, deletes) | single-admin row management | `AdminNotificationController`, `Dashboard` (admin) |

Admin broadcast: **Broadcast to all admins (EB-50 / D-12)** — `sendToAdmin` retrieves all active `Admin` accounts via `adminRepo.findAll()`, creates individual `AdminNotification` rows, and pushes to each admin's user queue.

### 2.3 Push mechanics & transaction interplay

- **FIXED (EB-50 / P4-01):** `convertAndSendToUser` executes **after transaction commit** via `sendAfterCommit` (`TransactionSynchronizationManager.registerSynchronization`). If a transaction rolls back, STOMP messages are never sent, completely eliminating ghost notifications.
- Fan-out (`sendToMentorshipStudents`) is `@Async` — a single shared notification row + `saveAll` relations, then per-student pushes queued after commit.

### 2.4 Contact-admin reply EMAIL — template merge (SEL-15 — FIXED, EB-31 / P1-24)

- **FIXED 2026-11-02 (EB-31 / P1-24):** `ContactMessageService#sendAdminReply` now runs **both** merge slots through `HtmlUtils.htmlEscape` at merge time — the same pattern as the badge flow (`AdminBadgeService#sendBadgeAwardEmail`). Hostile `{{name}}` / `{{reply}}` values render inert in the HTML email.
- History: `{{name}}` was **attacker-controlled** (`POST /api/v1/contact/save-message` is PUBLIC per PublicRoutesPolicy; `ContactMessageRequestDto.name` validates `@NotBlank` only), and both slots were merged with plain `String.replace` — the first ADMIN reply sent the payload as live HTML to the visitor's mailbox.
- **V-02 POC (P0-04/EB-2) CONFIRMED 2026-11-01** — prod-chain probes `ContactReplyInjectionPocTest` (2) captured the finished email at the `EmailService` boundary (sentinel inbox): hostile `<img src=x onerror=...>` and `<script>...` survived **verbatim**. The same probes were **flipped by EB-31** to the escaped contract: payload now arrives `&lt;img ...&gt;` / `&lt;script&gt;` and never verbatim.

## 3. Interfaces & Contracts

Base `/api/v1/notifications` (class `isAuthenticated()`):
- `GET /` (paged), `GET /unread` (paged), `GET /unread/count`
- `PATCH /mark-all-read`, `PATCH /{id}/mark-read` (ownership-checked via `SecurityService.isUserOwnNotification`)
- `DELETE /delete/{id}` (ownership-checked), `DELETE /delete-for-user`
- **`POST /api/v1/notifications/send` ← `{userEmail,title,content,type}`** — *any authenticated user can address any notification to any email* (PRODUCT/SECURITY issue NOTIF-01; likely a testing leftover).

Admin: `/api/v1/admin/notifications` — list/delete/`DELETE /delete-all`/`POST /send` (ADMIN), each `@PreAuthorize`.

## 4. Error Handling & Edge Cases

- `sendToUserByEmail` resolves id by email; unknown email → `UsernameNotFoundException` → 404 (contact-us reply flow **fails for guest senders** — NOTIF-02).
- Unread-count live updates rely on client refetch (no push after mark-read).
- Fan-out to large cohorts = N pushes + N relation inserts in one transaction (long fan-out latency + ghosting risk).

## 5. Testing

First probes landed 2026-11-01 (EB-2 / P0-04): `ContactReplyInjectionPocTest` (2, prod-chain, `@Tag("critical")`) covers the contact-admin **reply email** path — public save → ADMIN reply → real `admin-reply.html` merge, captured at the `EmailService` boundary; SEL-15 verdict CONFIRMED (raw `{{name}}`/`{{reply}}` merge). **Flipped 2026-11-02 (EB-31 / P1-24):** the two probes now assert the escaped contract — payload arrives `&lt;`-escaped and never verbatim.

Still zero tests for the in-app notification core. Recommended: unread math including mark-one/mark-all interplay; ownership checks on mark/delete; fan-out rollback ghosting behavior (post-fix).

## 6. Known Issues

- **NOTIF-01 (High):** open send endpoint (phishing vector) — see [../ISSUES.md](../ISSUES.md).
- **NOTIF-02 (Medium):** contact-notification endpoint errors for unregistered sender emails (404 UX break).
- **NOTIF-03 (Medium):** push-before-commit ghosting pattern (inconsistent with the admin-badge flow's afterCommit email).
- **NOTIF-04 (Medium):** single-admin targeting (`findTopByOrderByCreatedAtDesc`).
- **NOTIF-05 (Low):** unused `NotificationProjection`; no email/push channel; unbounded page sizes.

## 7. Open Questions

- Should announcement-style admin messages reach *all* admins and/or appear in a public topic?
- Is email delivery of learning-event notifications desired (currently only account ops email)?
- Confirm intent for `/send` (remove vs admin-gate).

## 8. Related Documentation

[../websocket/messaging-flow.md](../websocket/messaging-flow.md) · [../ISSUES.md](../ISSUES.md) · [../api/endpoints.md](../api/endpoints.md)
