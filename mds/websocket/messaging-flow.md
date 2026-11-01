# Chat Messaging Flows (group rooms & private conversations)

Verified message paths, persistence shapes, and the notification-push pipeline's chat integration.

## 1. Group room chat flow (`SEND /app/chat/{roomId}`)

1. **Client** sends an **JSON object** `{"content": "message text"}` to `/app/chat/{roomId}`. Handler: `controller/chat/ChatWebSocketController#sendMessage(@DestinationVariable Long roomId, @Payload ChatMessageRequest request, ChatPrincipal principal)` — bound via the DTO contract (P0-03 / EB-1, 2026-10-29). A raw string body or a string literal is **rejected by the payload converter** (nothing persists; the sender receives the error on `/queue/errors` via `WebSocketExceptionHandler`); a blank/missing `content` never persists (guard in `ChatMessageService#saveMessage`, `globalLogicEx`). The DTO (`dto/chat/ChatMessageRequest`) is the extension point for future reply/attachment fields. *(History: pre-2026-10-29 the handler bound a raw `String` and persisted every frame body VERBATIM — an object payload landed in the DB as literal JSON text; evidence + behavior matrix in `plan/phases/phase-00-planning-baseline/tasks.md#p0-03`.)*
2. **Persistence** — `service/chat/ChatMessageService#saveMessage`:
   - Checks `roomRepo.existsById(roomId)`. On miss: pushes `"/queue/errors"` to sender's user queue and returns `null` (**no exception** → silent failure contract).
   - **P1-12 / EB-19 — defense in depth:** re-checks the D-14 WRITE rule via `ChatAuthorization#canWriteRoom` (explicit `ChatRoomMember` row or the room's owning mentor). Unauthorized → error push + `null` + **no DB row**. The STOMP interceptor already gates SEND frames at the channel; this re-check guarantees no persistence path bypasses membership.
   - Loads `UserEntity` by `principal.email()`; saves `ChatMessage{content, chatRoom, sender, senderEmail, senderName}` — email+name denormalized per row (stale display names if changed later).
3. **Broadcast** — `convertAndSend("/topic/room/" + roomId, ChatMessageResponse)` to subscribed cohort members (members-only since P1-12 / EB-19, when SUBSCRIBE gained the READ gate).
4. **`ChatMessageResponse.time`** = `BaseEntity.createdAt` (DB-generated). No read/unread field exists on room messages at all.

### Edit / delete over REST (`controller/chat/chatRestControllers`)
- `PATCH /{roomId}/messages/{messageId}?content=` → `editMessage`: sender-email check (`msg.getSenderEmail().equals(senderEmail)`) else `globalLogicEx`; then broadcasts to `"/topic/room/" + roomId` — **the path-variable room, not the message's actual room** (`ChatMessageService` returns `messageMapper(msg, msg.getChatRoom().getId())`). Same for `delete` (`{type:"DELETE", messageId}`).
- `deleteMessage` uses `MessageRepository.deleteByIdAndSender`-style JPQL (sender-only hard delete).

## 2. Membership semantics — three different rules

| Surface | Query used | Meaning |
|---|---|---|
| REST history/members read | `AuthorizationRepository#isUserMemberOfChatRoom` (lines 82–93) | **mentorship enrollment OR the mentorship's mentor** (not explicit room join) |
| Room join (`POST /{roomId}/join`) | `EnrollmentRepository#isUserInRoomMentorship` (lines 178–191) | enrolled **student** only |
| STOMP **READ** (`SUBSCRIBE /topic/room/{id}`) | `ChatAuthorization#canReadRoom` → same `isUserMemberOfChatRoom` query | enrollment proxy (P1-12 / EB-19) |
| STOMP **WRITE** (`SEND /app/chat/{roomId}`) | `ChatAuthorization#canWriteRoom` → `MembersRepo.existsByChatRoom_IdAndUser_Email` OR `ChatRoomRepository#isRoomOwnedByMentor` | explicit room member **OR** the room's owning mentor (P1-12 / EB-19) |

Consequences (post EB-19; READ/WRITE unified per **D-14**): the three inconsistent semantics collapsed into two rules — an enrolled student who never joined room X may **read** X (enrollment proxy) but cannot **write** into it until they join; the owning mentor can read and write every room of their mentorships without joining. Still open (unchanged by EB-19): the `rooms-with-status` endpoint (`GET /{mid}/rooms-with-status`, `@PreAuthorize("hasRole('STUDENT')")` only, no enrollment check) **leaks all rooms + join state of any mentorship to any student** (`ChatRoomRepository#findRoomsWithJoinStatusByMentorship`).

## 3. Private conversation flow (`/app/chat.private` + `/api/v1/conversation/**`)

1. **Send** — `ConversationWebSocketControllers#sendPrivate` → `ConversationService#sendMessage`:
   - **P1-12 / EB-19 — pair validation:** `ChatAuthorization#validatePrivatePair` rejects a blank recipient and a self-send (`"You cannot send a private message to yourself"`) before any lookup.
   - Resolves both users by email.
   - **Get-or-create** `Conversation` via `conversationRepo.findBetweenUsers(emailA, emailB)` (ordered/unordered pair match) or `createConversation` — *not* transactional, **no unique constraint** on `(user1_id, user2_id)` → concurrent first messages can create duplicate conversations (CHAT-05).
   - Saves `entity/chat/Message{content, sender, sentAt = LocalDateTime.now(), seen = false}`.
   - Pushes `convertAndSendToUser(recipientEmail, "/queue/messages", msg)` **and** to sender; recipient is server-derived from the persisted conversation record (fixed by P1-13 / EB-20).
2. **Edit** — REST `PATCH /messages/{messageId}`: sets `content` + `updated=true`; sender ownership enforced (P1-13 / EB-20; strangers receive 403 Forbidden). Pushes edit events to caller + server-derived recipient queue.
3. **Delete** — `DELETE /messages/{messageId}`: sender ownership enforced (P1-13 / EB-20; strangers receive 403 Forbidden). Pushes delete event to caller + server-derived recipient queue.
4. **History** — `GET /{conversationId}/messages?beforeId&size` (cursor via `MessageRepository.findConversationMessages`): ownership enforced (P1-13 / EB-20; requires participant or ROLE_ADMIN, strangers receive 403 Forbidden without ID enumeration oracle).
5. **`/all`** — `ConversationListResponse` list filtered by `authentication.getName()` (safe).

## 4. Read-state reality

- Room messages: no read flag (nothing to flip).
- Private messages: `Message.seen` exists but is **never set to `true` anywhere** (grep verified) → **no read receipts in the entire chat system**; unread counts exist only for platform notifications ([../notifications/MDS.md](../notifications/MDS.md)).
- `Message.updated=true` with no timestamp column → edit time unknowable.

## 5. Cross-interactions

- `NotificationService` pushes ride the same broker (`/queue/notifications`) — see [../notifications/MDS.md](../notifications/MDS.md) for the persist-then-push-and-transaction-commit pattern.
- `WebSocketExceptionHandler` catches handler exceptions → `/queue/errors` plain `ex.getMessage()` (info-leak suspicion).
- **STOMP gate violations (P1-12 / EB-19, wire-repaired by P2-05 / EB-39):** the channel interceptor DROPS the violated SUBSCRIBE/SEND (no STOMP ERROR frame exists on the real wire — the async `clientInboundChannel` swallows interceptor exceptions) and pushes the authored plain-string error ("Forbidden: you are not a member of this room") to the violator's `/user/queue/errors`. CONNECT violations remain connection-level (session setup fails).

## 6. Testing recommendations

1. ~~Post-fix integration: room membership enforced on subscribe + send (out-of-room id must be rejected and must not persist).~~ **DONE (V-02, P1-12 / EB-19)** — `WSRoomAuthorizationProbesTest` (10 probes): joined member persists; enrolled-but-not-joined and foreign users rejected/no-persist on SEND; foreign SUBSCRIBE rejected; owning-mentor SEND persists; defense-in-depth direct `saveMessage` drop; CONNECT missing/garbage token rejected; private self-send rejected. V-01 re-seeded under the joined-member contract. **Socket-level re-proof (EB-39, 2026-11-01):** `WSAuthMatrixIntegrationTest` (9 real-socket probes) — CONNECT matrix, member broadcast round-trip, violation frames dropped + authored error on `/user/queue/errors`, no persistence on unauthorized SEND.
2. Conversation ownership: user B reading user A's conversation id → 403.
3. Duplicate-conversation race: two parallel first-messages produce one conversation (post unique-constraint fix).
4. ~~Contract: `String content` vs JSON payload on `/app/chat/{roomId}`.~~ **DONE (V-01, 2026-10-29)** — DTO contract shipped with validity probes: `src/test/java/com/example/gradproj/EduNest/websocket/probe/V01PayloadBindingProbeTest.java` (raw strings / string literals / blank contents rejected; object payload persists pure text).
