package com.example.gradproj.EduNest.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class ChatMessageResponse {
    UUID id;
    String message;
    String senderName;
    String senderEmail;
    String senderProfileImageUrl;
    UUID roomId;
    LocalDateTime time;
}
