# Domain: WebSocket — Implementation Guide

Canonical audit sources: [`mds/websocket/MDS.md`](../../../mds/websocket/MDS.md), [`messaging-flow.md`](../../../mds/websocket/messaging-flow.md), [`connection-lifecycle.md`](../../../mds/websocket/connection-lifecycle.md), conversation flows under [`mds/users-profiles/profiles.md`](../../../mds/users-profiles/profiles.md).

## 1. Purpose & components owned

STOMP messaging over `JwtChannelInterceptor` (token auth at CONNECT), group room chat (`/topic/room/{id}`, `/app/chat/{id}`), direct-message conversations (`/app/chat.private` events), edit/delete notifications, keep-alives. Components: `config/webSocket/**`, `service/ChatMessageService`, `service/ConversationService`, WS controllers, membership repositories.

## 2. Review checklist (mandatory for any PR here)

- [ ] Frame-level authz: **CONNECT (existing), SUBSCRIBE, SEND** all validated; destinations parsed from actual frame, not client-declared metadata.
- [ ] Server derives recipients/rooms from persisted rows (client payload never trusted — CHAT-04 invariant).
- [ ] Persistence/authorization ordering: authorize first, persist second; failure → explicit ERROR frame (CHAT-01).
- [ ] No `System.out`; no commented-out authorization left "temporarily".
- [ ] WS suite ([P2-05](../../phases/phase-02-testing-and-ci/tasks.md#p2-05) — auth matrix + error-frame contract) updated.

## 3. Implementation requirements (per canonical issue)

1. Room authorization with membership semantics — **WS-01/TD-08(G13), D-14 decision**, tasks [P1-12](../../phases/phase-01-security-stabilization/tasks.md#p1-12): membership rule (write = member or mentor-owner; read = additionally enrollment-proxy per default), membership query added, dead interceptor code deleted (WS-02).
2. Conversation IDOR trio — **CHAT-02/03/04**, [P1-13](../../phases/phase-01-security-stabilization/tasks.md#p1-13): participant-only history, sender-only edit, server-derived event recipient; `recipientEmail` field tolerated-but-ignored (contract-preserving).
3. Reliability: save-failure error frame, correct-room broadcasts, payload binding — **CHAT-01/06/08**, [P4-06](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-06); V-01 ([P0-03](../../phases/phase-00-planning-baseline/tasks.md#p0-03)) fixes the String-binding question with FE first.
4. Read receipts / unread counts — **CHAT-07/D-13**, conditional [P4-07](../../phases/phase-04-business-logic-and-notifications/tasks.md#p4-07) (default deferral documented).

## 4. Technical constraints & anti-goals

- Keep the in-memory simple broker (single-node ceiling R9 is documented, revisit post-launch only via D-22-scale review).
- Do not "fix" by disabling message acknowledgments or moving authz to client-side middleware.
- The handshake-interceptor revival only via D-14 re-open (SEL-13 stays dormant otherwise).

## 5. Definition of done (domain)

WS rows (WS-01..03, CHAT-01..08, TD-08) on [`ISSUE-MAPPING.md`](../../ISSUE-MAPPING.md) resolve to Verified (or documented deferral); `messaging-flow.md` updated to describe validated destinations + error-frame contract; P2-05 suite green.
