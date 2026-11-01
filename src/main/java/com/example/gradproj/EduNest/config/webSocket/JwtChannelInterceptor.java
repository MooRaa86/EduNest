package com.example.gradproj.EduNest.config.webSocket;

import com.example.gradproj.EduNest.service.chat.ChatAuthorization;
import com.example.gradproj.EduNest.service.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP frame gate (P1-12 / EB-19; wire-delivery contract repaired by
 * P2-05 / EB-39).
 *
 * Authorization contract (D-14 semantics, Engineering Lead-approved):
 * <ul>
 *   <li><b>CONNECT</b> — valid JWT in the raw {@code Authorization} native
 *       header; principal is rebuilt from the token claims on every frame
 *       batch. This is the only WS auth path: the old (never-registered)
 *       {@code JwtHandshakeInterceptor} was removed with EB-19, so SockJS
 *       fallback transports cannot authenticate — the FE must use the native
 *       WebSocket transport (documented in mds/websocket/MDS.md §6).
 *       Missing/invalid tokens fail the session setup (connection-level
 *       rejection, no principal exists yet).</li>
 *   <li><b>SUBSCRIBE {@code /topic/room/{roomId}}</b> — READ rule via
 *       {@link ChatAuthorization#canReadRoom}. Violations are DROPPED
 *       (preSend returns null) and the authored message
 *       ("Forbidden: you are not a member of this room") is pushed to the
 *       violating session's {@code /user/{email}/queue/errors}.</li>
 *   <li><b>SEND {@code /app/chat/{roomId}}</b> — WRITE rule via
 *       {@link ChatAuthorization#canWriteRoom}; same drop + /queue/errors
 *       contract. {@code /app/chat.private} only requires an authenticated
 *       user; the sender↔recipient pair is validated in ConversationService.</li>
 * </ul>
 *
 * Why /queue/errors instead of a raw STOMP ERROR frame: the client inbound
 * channel executes interceptor preSend on the async executor — a thrown
 * exception is only LOGGED there and never reaches the wire (proven by the
 * EB-39 socket matrix). Returning null + pushing to /queue/errors is the
 * deterministic error path every connected client already observes (the same
 * queue ChatMessageService and WebSocketExceptionHandler use). Probes assert
 * the authored text on /user/queue/errors.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final String FORBIDDEN_ROOM =
            "Forbidden: you are not a member of this room";

    private final JwtService jwtService;
    private final ChatAuthorization chatAuthorization;
    /**
     * Provider, not the bean itself: SimpMessagingTemplate is owned by the
     * broker configuration this interceptor is registered into — direct
     * injection would be a circular reference. Resolved lazily at call time
     * (after the context is up, so it is never null in practice).
     */
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();

        switch (command) {
            case CONNECT -> handleConnect(accessor);
            case SUBSCRIBE -> {
                if (!handleSubscribe(accessor)) return null;
            }
            case SEND -> {
                if (!handleSend(accessor)) return null;
            }
            case DISCONNECT -> {
                if (accessor.getUser() != null) {
                    log.debug("WS disconnected: {}", accessor.getUser().getName());
                }
            }
        }

        return message;
    }

    // ===================== CONNECT =====================

    private void handleConnect(StompHeaderAccessor accessor) {

        String token =
                accessor.getFirstNativeHeader("Authorization");

        if (token == null || !jwtService.isTokenValid(token)) {
            // No principal exists yet — no user queue to reach; fail the session.
            throw new MessagingException("Invalid or missing authentication token");
        }

        String email = jwtService.extractUserEmail(token);
        String fullName = jwtService.getFullName(token);

        accessor.setUser(new ChatPrincipal(email, fullName));
    }

    // ===================== SUBSCRIBE (READ rule) =====================

    /** Returns false when the frame must be dropped (error already queued). */
    private boolean handleSubscribe(StompHeaderAccessor accessor) {

        if (accessor.getUser() == null) {
            return false; // unauthenticated mid-stream — nothing to notify
        }

        String destination = accessor.getDestination();

        if (destination == null) return true;

        if (destination.startsWith("/topic/room/")) {
            Long roomId = extractRoomId(accessor);
            if (roomId == null) {
                return false; // "Invalid destination format" error already queued
            }
            String email = accessor.getUser().getName();

            if (!chatAuthorization.canReadRoom(roomId, email)) {
                errorToUser(email, FORBIDDEN_ROOM);
                return false;
            }
        }
        return true;
    }

    // ===================== SEND (WRITE rule) =====================

    private boolean handleSend(StompHeaderAccessor accessor) {

        if (accessor.getUser() == null) {
            return false;
        }

        String destination = accessor.getDestination();

        if (destination == null) return true;

        // WRITE rule — room chat (note: /app/chat.private does NOT match this
        // prefix; it only needs the authenticated user checked above, with the
        // sender↔recipient pair validated in ConversationService)
        if (destination.startsWith("/app/chat/")) {
            Long roomId = extractRoomId(accessor);
            if (roomId == null) {
                return false; // "Invalid destination format" error already queued
            }
            String email = accessor.getUser().getName();

            if (!chatAuthorization.canWriteRoom(roomId, email)) {
                errorToUser(email, FORBIDDEN_ROOM);
                return false;
            }
        }
        return true;
    }

    // ===================== HELPERS =====================

    private void errorToUser(String email, String text) {
        try {
            SimpMessagingTemplate template = messagingTemplateProvider.getIfAvailable();
            if (template == null) {
                log.debug("No messaging template available yet — auth error for {} not delivered", email);
                return;
            }
            template.convertAndSendToUser(email, "/queue/errors", text);
        } catch (Exception ex) {
            // the violating user may have vanished mid-frame — never fail the gate
            log.debug("Could not deliver auth error to {}: {}", email, ex.getMessage());
        }
    }

    /** Returns the room id, or {@code null} when the destination is not a room
     *  id (the authored error is already pushed to the violator's queue). */
    private Long extractRoomId(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        try {
            return Long.parseLong(
                    destination.substring(
                            destination.lastIndexOf("/") + 1
                    )
            );
        } catch (Exception e) {
            String email = accessor.getUser() != null ? accessor.getUser().getName() : null;
            if (email != null) errorToUser(email, "Invalid destination format");
            return null;
        }
    }
}

