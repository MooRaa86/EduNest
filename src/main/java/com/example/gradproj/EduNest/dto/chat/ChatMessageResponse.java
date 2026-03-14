package com.example.gradproj.EduNest.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Builder
@Setter @Getter @AllArgsConstructor
public class ChatMessageResponse {
    long id;
    String message;
    String senderName;
    String senderEmail;
    String senderProfileImageUrl;
    Long roomId;
    LocalDateTime time;
}
