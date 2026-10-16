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

Admin broadcast: **none** — no `/topic` broadcast exists; admin notifications target exactly one stored admin (the newest).

### 2.3 Push mechanics & transaction interplay

- Class-level `@Transactional` on the service; `convertAndSendToUser` executes **before the caller's commit** → rollback could leave already-delivered "ghost" notifications (inconsistent with `AdminBadgeService`, which defers the email post-commit but still pushes STOMP pre-commit).
- Fan-out (`sendToMentorshipStudents`) is `@Async` — a single shared notification row + `saveAll` relations, then per-student pushes.

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

Zero tests. Recommended: unread math including mark-one/mark-all interplay; ownership checks on mark/delete; fan-out rollback ghosting behavior (post-fix).

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
