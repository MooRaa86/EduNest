# Chat Messaging Flows (group rooms & private conversations)

Verified message paths, persistence shapes, and the notification-push pipeline's chat integration.

## 1. Group room chat flow (`SEND /app/chat/{roomId}`)

1. **Client** sends `content` to `/app/chat/{roomId}`. Handler: `controller/chat/ChatWebSocketController#sendMessage(@DestinationVariable Long roomId, String content, ChatPrincipal principal)` — content bound as **raw String** (no `@Payload`/DTO; JSON objects would be stored as literal JSON text — SUSPECTED, needs frontend confirmation).
2. **Persistence** — `service/chat/ChatMessageService#saveMessage` (lines 37–71):
   - Checks `roomRepo.existsById(roomId)` only. On miss: pushes `"/queue/errors"` to sender's user queue and returns `null` (**no exception** → silent failure contract).
   - Loads `UserEntity` by `principal.email()`; saves `ChatMessage{content, chatRoom, sender, senderEmail, senderName}` — email+name denormalized per row (stale display names if changed later).
3. **Broadcast** — `convertAndSend("/topic/room/" + roomId, ChatMessageResponse)` (controller lines 33–36) — everyone subscribed (regardless of membership, per [connection-lifecycle.md](connection-lifecycle.md)).
4. **`ChatMessageResponse.time`** = `BaseEntity.createdAt` (DB-generated). No read/unread field exists on room messages at all.

### Edit / delete over REST (`controller/chat/chatRestControllers`)
- `PATCH /{roomId}/messages/{messageId}?content=` → `editMessage`: sender-email check (`msg.getSenderEmail().equals(senderEmail)`) else `globalLogicEx`; then broadcasts to `"/topic/room/" + roomId` — **the path-variable room, not the message's actual room** (`ChatMessageService` returns `messageMapper(msg, msg.getChatRoom().getId())`). Same for `delete` (`{type:"DELETE", messageId}`).
- `deleteMessage` uses `MessageRepository.deleteByIdAndSender`-style JPQL (sender-only hard delete).

## 2. Membership semantics — three different rules

| Surface | Query used | Meaning |
|---|---|---|
| REST history/members read | `AuthorizationRepository#isUserMemberOfChatRoom` (lines 82–93) | **mentorship enrollment OR the mentorship's mentor** (not explicit room join) |
| Room join (`POST /{roomId}/join`) | `EnrollmentRepository#isUserInRoomMentorship` (lines 178–191) | enrolled **student** only |
| STOMP subscribe/send | *(none — commented out, unbuildable as written)* | none |

Consequences: a student enrolled in the mentorship but who never joined room X can read room X's history/members; conversely the `rooms-with-status` endpoint (`GET /{mid}/rooms-with-status`, `@PreAuthorize("hasRole('STUDENT')")` only, no enrollment check) **leaks all rooms + join state of any mentorship to any student** (`ChatRoomRepository#findRoomsWithJoinStatusByMentorship`).

## 3. Private conversation flow (`/app/chat.private` + `/api/v1/conversation/**`)

1. **Send** — `ConversationWebSocketControllers#sendPrivate` → `ConversationService#sendMessage` (lines 47–78):
   - Resolves both users by email.
   - **Get-or-create** `Conversation` via `conversationRepo.findBetweenUsers(emailA, emailB)` (ordered/unordered pair match) or `createConversation` — *not* transactional, **no unique constraint** on `(user1_id, user2_id)` → concurrent first messages can create duplicate conversations (CHAT-05).
   - Saves `entity/chat/Message{content, sender, sentAt = LocalDateTime.now(), seen = false}`.
   - Pushes `convertAndSendToUser(recipientEmail, "/queue/messages", msg)` **and** to sender (lines 34–45); the recipient queue is derived from the request's `recipientEmail` — client-controlled targeting (CHAT-04 pattern).
2. **Edit** — REST `PATCH /messages/{messageId}`: sets `content` + `updated=true`; **ownership check commented out** (lines 119–121) → any authenticated user can edit any message (CHAT-03). Pushes edit events to caller + client-supplied recipient queue.
3. **Delete** — `DELETE /messages/{messageId}`: `MessageRepository` JPQL delete guarded `m.sender.email = :senderEmail` (sender-only hard delete); pushes delete event to caller + client-supplied recipient queue.
4. **History** — `GET /{conversationId}/messages?beforeId&size` (cursor via `MessageRepository.findConversationMessages`): **no ownership check** (CHAT-02).
5. **`/all`** — `ConversationListResponse` list filtered by `authentication.getName()` (safe).

## 4. Read-state reality

- Room messages: no read flag (nothing to flip).
- Private messages: `Message.seen` exists but is **never set to `true` anywhere** (grep verified) → **no read receipts in the entire chat system**; unread counts exist only for platform notifications ([../notifications/MDS.md](../notifications/MDS.md)).
- `Message.updated=true` with no timestamp column → edit time unknowable.

## 5. Cross-interactions

- `NotificationService` pushes ride the same broker (`/queue/notifications`) — see [../notifications/MDS.md](../notifications/MDS.md) for the persist-then-push-and-transaction-commit pattern.
- `WebSocketExceptionHandler` catches handler exceptions → `/queue/errors` plain `ex.getMessage()` (info-leak suspicion).

## 6. Testing recommendations

1. Post-fix integration: room membership enforced on subscribe + send (out-of-room id must be rejected and must not persist).
2. Conversation ownership: user B reading user A's conversation id → 403.
3. Duplicate-conversation race: two parallel first-messages produce one conversation (post unique-constraint fix).
4. Contract: `String content` vs JSON payload on `/app/chat/{roomId}`.
