# WebSocket & Real-Time Chat — Primary Domain Document

## 1. Overview

Two real-time subsystems share one STOMP/SockJS endpoint:

1. **Group chat rooms** (`ChatRoom`/`ChatMessage`) — cohort rooms inside a mentorship, timeline persisted and broadcast to a shared topic.
2. **Private 1:1 conversations** (`Conversation`/`Message`) — persisted per-pair history pushed to the two participants' user queues.

Plus **notifications** push (see [../notifications/MDS.md](../notifications/MDS.md)) and an errors queue for STOMP failures.

**Business value:** in-platform communication replacing external chat tools per cohort; real-time delivery of notifications.

**Scope:** `config/webSocket/**`, `controller/chat/**`, `service/chat/**`, `entity/chat/**`, `repository/chat/**`, `dto/chat/**`, plus server-side push producers (`service/notification/NotificationService`).

## 2. Current Implementation — surface

- **Endpoint:** single STOMP endpoint **`/ws`** with `withSockJS()` and `setAllowedOriginPatterns("*")` (`config/webSocket/WebSocketConfig:34-38`).
- **Broker:** simple in-memory broker on `/topic`, `/queue`; user destination prefix `/user`; app prefix `/app`.
- **Inbound interceptors:** only `JwtChannelInterceptor` — the sole WS auth/gate path since EB-19 deleted the (never-registered) `JwtHandshakeInterceptor`. **Consequence: only the native WebSocket transport can authenticate CONNECT** — SockJS XHR fallbacks cannot set native STOMP headers.
- **Authorization (P1-12 / EB-19):** `service/chat/ChatAuthorization` centralizes the D-14 rules; `JwtChannelInterceptor` applies them per frame — SUBSCRIBE `/topic/room/{id}` (READ: enrollment proxy or owning mentor), SEND `/app/chat/{roomId}` (WRITE: explicit room member or owning mentor), CONNECT (JWT). Violations are dropped with an explicit STOMP ERROR frame; `ChatMessageService#saveMessage` re-checks WRITE before persisting.
- **Client SEND destinations:**
  - `/app/chat/{roomId}` → `ChatWebSocketController.sendMessage` (raw `String content` bound, no `@Payload` DTO).
  - `/app/chat.private` → `ConversationWebSocketControllers.sendPrivate` (`ConversationMessageRequest`).
- **Broadcast destinations (all producers verified):**

| Destination | Producer | Payload |
|---|---|---|
| `/topic/room/{roomId}` | `ChatWebSocketController` (new), `chatRestControllers#editMessage/#deleteMessage` | `ChatMessageResponse` / `{type:"DELETE", messageId}` |
| `/user/{email}/queue/messages` | `ConversationWebSocketControllers#sendPrivate`, `ConversationRestControllers#edit/#delete` | `ConversationMessageResponse` / delete event |
| `/user/{email}/queue/notifications` | `NotificationService#sendToUserByEmail/#sendToMentorshipStudents/#sendToAdmin` (single newest admin) | `NotificationDto` / `AdminNotificationResponse` |
| `/user/{email}/queue/errors` | `ChatMessageService#saveMessage` failure path; `WebSocketExceptionHandler` (`@MessageExceptionHandler`) | plain string |

- Client subscriptions used: `/topic/room/{roomId}`, `/user/queue/messages`, `/user/queue/notifications`, `/user/queue/errors`.

## 3. REST companion endpoints

Base `/api/v1/chat-room` (`controller/chat/chatRestControllers`):
`POST /create/{mentorshipId}` (mentor), `GET /{mentorshipId}` (rooms of a mentorship; enrollment-gated), `GET /{roomId}/messages` (cursor-paged `?beforeId&size`), `GET /{roomId}/members`, `GET /mentorship/{mentorshipId}/rooms-with-status` (**STUDENT gate only, no enrollment check**), `POST /{roomId}/join`, `GET /my-rooms`, `PUT /{roomId}/image` (mentor+creator), `GET /mentor/mentorships`, `DELETE /{roomId}` (mentor+creator, DRAFT/any status), `PATCH/DELETE /{roomId}/messages/{messageId}` (sender-only).

Base `/api/v1/conversation` (`controller/chat/ConversationRestControllers`):
`GET /{conversationId}/messages` (**no ownership check**), `PATCH /messages/{messageId}` (**ownership check commented out**), `DELETE /messages/{messageId}` (sender-only), `GET /all` (safe, filtered by current user).

## 4. Workflows → detail docs

- Connection/auth lifecycle incl. **which checks are commented out**: [connection-lifecycle.md](connection-lifecycle.md)
- Message flows (group room + private), persistence shapes, read-state reality: [messaging-flow.md](messaging-flow.md)
- Notification push pipeline: [../notifications/MDS.md](../notifications/MDS.md)

## 5. Security (summary — details in §3 of lifecycle doc)

- STOMP CONNECT requires a valid JWT native header (**this is the only WS auth path** since EB-19 deleted the never-registered handshake interceptor); the handshake endpoint is `permitAll` and unauthenticated.
- **Since P1-12 / EB-19 room membership is enforced** (`service/chat/ChatAuthorization`, D-14 semantics): SUBSCRIBE runs the READ rule (enrollment proxy or owning mentor), SEND the WRITE rule (explicit member or owning mentor), with a documented STOMP ERROR-frame contract; `saveMessage` re-checks WRITE before persisting. Probe evidence: `WSRoomAuthorizationProbesTest` (10), V-01 re-seeded.

## 6. Error Handling

- STOMP authorization violations (P1-12 / EB-19) throw `MessagingException` in the inbound channel — Spring's protocol error handler converts them into an explicit **STOMP ERROR frame** carrying the authored message (`"Forbidden: you are not a member of this room"` / `"Invalid destination format"`). For compliant clients nothing changes.
- Service-side chat save failures push `/queue/errors` strings and return `null`.
- `exception/WebSocketExceptionHandler` (`@MessageExceptionHandler(Exception.class)`, `@SendToUser("/queue/errors")`) forwards authored business messages and sanitizes everything else.
- SockJS transports (XHR fallbacks) cannot set STOMP native headers on CONNECT — **resolved stance (EB-19): fallback transports are unsupported; the frontend must use the native WebSocket transport.** A future fallback would require a short-lived single-use ticket design (NOT the old query-param handshake, which leaks tokens).

## 7. Data Model

- `entity/chat/ChatRoom` (mentorship FK, name, creator, coverImage), `ChatRoomMember` (room+user, `joinedAt`), `ChatMessage` (content ≤10k, sender id + denormalized senderEmail/senderName, room FK; audit columns; **no read/seen flag**).
- `entity/chat/Conversation` (user1/user2 pair; **no unique constraint** on (user1,user2), no BaseEntity), `entity/chat/Message` (content, sender FK, seen flag **never set true**, sentAt, `updated` flag set true on edits **without timestamp**).

## 8. Testing

No tests exist for chat/WS (see [../testing/MDS.md](../testing/MDS.md)). Recommended priority: STOMP CONNECT auth, room membership guard, conversation ownership, duplicate-conversation race.

## 9. Known Issues (IDs defined in ../ISSUES.md)

- **WS-01 (Critical):** ~~no room authorization on subscribe/send → any authenticated user reads/writes any room~~ **FIXED (EB-19):** D-14 READ/WRITE rules enforced via `ChatAuthorization`; ERROR-frame contract; defense-in-depth in `saveMessage`.
- **CHAT-02/CHAT-03 (Critical/High):** conversation history IDOR; edit IDOR (commented check).
- **CHAT-04 (High):** client-controlled `recipientEmail` in REST edit/delete lets the caller choose which user queue receives events.
- **CHAT-05 (Medium):** duplicate-conversation race (get-or-create, no unique constraint, no transaction).
- **CHAT-06 (Medium):** REST edit/delete broadcast build `/topic/room/{pathVar}` not the message's real room.
- **CHAT-07 (Medium):** `Message.seen` never set; no unread count for chat.
- **CHAT-08 (Low):** raw `String content` binding (JSON-vs-plaintext ambiguity, needs frontend confirmation).
- **WS-02 (Low):** ~~handshake interceptor dead code; `connectedUsers` map commented out; debug prints~~ **FIXED (EB-19):** deleted the never-registered interceptor, removed remnants, SLF4J logging.
- **WS-03 (Low):** unbounded `size` params on message pagination.

## 10. Open Questions

- Which SockJS transport does the frontend use in production (native WebSocket or fallback)? If fallback: CONNECT cannot authenticate (no headers) → handshake interceptor must be re-enabled — with a token-in-URL risk that needs a designed alternative (short-lived single-use ticket, recommended).
- Should room chat support read receipts/unread counts now, or is per-room presence sufficient? (Decides whether to add read-state storage.)
- Is the double-broadcast model intentional: REST edit/delete events go to the *topic*, private edit/delete events go to *user queues* — confirm desired consistency.

## 11. Related Documentation

[connection-lifecycle.md](connection-lifecycle.md) · [messaging-flow.md](messaging-flow.md) · [../notifications/MDS.md](../notifications/MDS.md) · [../ISSUES.md](../ISSUES.md) (WS-*/CHAT-* IDs) · [../authentication-security/authorization-rules.md](../authentication-security/authorization-rules.md) (WS auth in the global matrix)
