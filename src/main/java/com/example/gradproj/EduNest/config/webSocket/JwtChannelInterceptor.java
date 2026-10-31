package com.example.gradproj.EduNest.config.webSocket;

import com.example.gradproj.EduNest.service.chat.ChatAuthorization;
import com.example.gradproj.EduNest.service.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP frame gate (P1-12 / EB-19).
 *
 * Authorization contract (D-14 semantics, Engineering Lead-approved):
 * <ul>
 *   <li><b>CONNECT</b> — valid JWT in the raw {@code Authorization} native
 *       header; principal is rebuilt from the token claims on every frame
 *       batch. This is the only WS auth path: the old (never-registered)
 *       {@code JwtHandshakeInterceptor} was removed with EB-19, so SockJS
 *       fallback transports cannot authenticate — the FE must use the native
 *       WebSocket transport (documented in mds/websocket/MDS.md §6).</li>
 *   <li><b>SUBSCRIBE {@code /topic/room/{roomId}}</b> — READ rule via
 *       {@link ChatAuthorization#canReadRoom}; violations are dropped with an
 *       explicit STOMP ERROR frame.</li>
 *   <li><b>SEND {@code /app/chat/{roomId}}</b> — WRITE rule via
 *       {@link ChatAuthorization#canWriteRoom}; same ERROR-frame contract.
 *       {@code /app/chat.private} only requires an authenticated user; the
 *       sender↔recipient pair is validated in ConversationService.</li>
 * </ul>
 *
 * All checks run per frame (stateless); every non-room destination that
 * requires no membership passes untouched, so notifications and user queues
 * keep working unchanged for compliant clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final String FORBIDDEN_ROOM =
            "Forbidden: you are not a member of this room";

    private final JwtService jwtService;
    private final ChatAuthorization chatAuthorization;

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
            case SUBSCRIBE -> handleSubscribe(accessor);
            case SEND -> handleSend(accessor);
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
            throw new MessagingException("Invalid or missing authentication token");
        }

        String email = jwtService.extractUserEmail(token);
        String fullName = jwtService.getFullName(token);

        accessor.setUser(new ChatPrincipal(email, fullName));
    }

    // ===================== SUBSCRIBE =====================

    private void handleSubscribe(StompHeaderAccessor accessor) {

        if (accessor.getUser() == null) {
            throw new MessagingException("Unauthorized subscribe");
        }

        String destination = accessor.getDestination();

        if (destination == null) return;

        // READ rule — cohort room topic
        if (destination.startsWith("/topic/room/")) {

            Long roomId = extractRoomId(destination);
            String email = accessor.getUser().getName();

            if (!chatAuthorization.canReadRoom(roomId, email)) {
                throw new MessagingException(FORBIDDEN_ROOM);
            }
        }
    }

    // ===================== SEND =====================

    private void handleSend(StompHeaderAccessor accessor) {

        if (accessor.getUser() == null) {
            throw new MessagingException("Unauthorized send");
        }

        String destination = accessor.getDestination();

        if (destination == null) return;

        // WRITE rule — room chat (note: /app/chat.private does NOT match this
        // prefix; it only needs the authenticated user checked above, with the
        // sender↔recipient pair validated in ConversationService)
        if (destination.startsWith("/app/chat/")) {

            Long roomId = extractRoomId(destination);
            String email = accessor.getUser().getName();

            if (!chatAuthorization.canWriteRoom(roomId, email)) {
                throw new MessagingException(FORBIDDEN_ROOM);
            }
        }
    }

    // ===================== HELPER =====================

    private Long extractRoomId(String destination) {
        try {
            return Long.parseLong(
                    destination.substring(
                            destination.lastIndexOf("/") + 1
                    )
            );
        } catch (Exception e) {
            throw new MessagingException("Invalid destination format");
        }
    }
}
