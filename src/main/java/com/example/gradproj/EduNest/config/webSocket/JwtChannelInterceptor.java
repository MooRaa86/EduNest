package com.example.gradproj.EduNest.config.webSocket;

import com.example.gradproj.EduNest.exception.jwt.InvalidJwtToken;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.service.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final ChatRoomRepository chatRoomRepository;
    private final ConcurrentHashMap<String, String> connectedUsers = new ConcurrentHashMap<>();

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
            case DISCONNECT -> handleDisconnect(accessor);
        }

        return message;
    }

    // ===================== CONNECT =====================

    private void handleConnect(StompHeaderAccessor accessor) {

        String token =
                accessor.getFirstNativeHeader("Authorization");

        if (token == null || !jwtService.isTokenValid(token)) {
            throw new InvalidJwtToken("Invalid JWT");
        }

        String email = jwtService.extractUserEmail(token);
        String fullName = jwtService.getFullName(token);

        if (connectedUsers.containsKey(email)) {
            throw new IllegalStateException("User already connected");
        }

        connectedUsers.put(email, accessor.getSessionId());
        accessor.setUser(new ChatPrincipal(email, fullName));
    }

    // ===================== SUBSCRIBE =====================

    private void handleSubscribe(StompHeaderAccessor accessor) {

        if (accessor.getUser() == null) {
            throw new UsernameNotFoundException(
                    "Unauthorized subscribe"
            );
        }

        String destination = accessor.getDestination();

        if (destination == null) return;

        // check rooms only
//        if (destination.startsWith("/topic/room/")) {
//
//            Long roomId = extractRoomId(destination);
//
//            String email =
//                    accessor.getUser().getName();
//
//            boolean allowed =
//                    chatRoomRepository.isUserInRoom(
//                            email,
//                            roomId
//                    );
//
//            if (!allowed) {
//                throw new IllegalArgumentException(
//                        "Forbidden subscription"
//                );
//            }
//        }
    }

    // ===================== SEND =====================

    private void handleSend(StompHeaderAccessor accessor) {

        if (accessor.getUser() == null) {
            throw new IllegalArgumentException(
                    "Unauthorized send"
            );
        }

        String destination = accessor.getDestination();

        if (destination == null) return;

        // check chat sending
        if (destination.startsWith("/app/chat/")) {

            Long roomId = extractRoomId(destination);

            String email =
                    accessor.getUser().getName();

//            boolean allowed =
//                    chatRoomRepository.isUserInRoom(
//                            email,
//                            roomId
//                    );
//
//            if (!allowed) {
//                throw new IllegalArgumentException(
//                        "Forbidden send"
//                );
//            }
        }
    }

    // ===================== DISCONNECT =====================

    private void handleDisconnect(
            StompHeaderAccessor accessor
    ) {

        if (accessor.getUser() != null) {
            String email = accessor.getUser().getName();
            connectedUsers.remove(email);
            System.out.println("Disconnected: " + email);
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
            throw new IllegalArgumentException(
                    "Invalid destination format"
            );
        }
    }
}