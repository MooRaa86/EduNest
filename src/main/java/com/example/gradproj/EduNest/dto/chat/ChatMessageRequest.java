package com.example.gradproj.EduNest.dto.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Room-messaging client contract (P0-03 / CHAT-08).
 *
 * Binding contract (decided 2026-10-29, in force since the EB-1 fix):
 * clients SEND a JSON object to {@code /app/chat/{roomId}}:
 * <pre> {"content": "message text"} </pre>
 * A bare raw-string body is NOT accepted (rejected by the payload converter);
 * a blank/missing content never persists (guarded in ChatMessageService).
 */
@Getter
@Setter
@Builder
public class ChatMessageRequest {

    @NotBlank(message = "Content must not be blank")
    private String content;
}
