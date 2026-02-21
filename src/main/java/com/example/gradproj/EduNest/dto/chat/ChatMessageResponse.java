package com.example.gradproj.EduNest.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Builder
@Setter @Getter @AllArgsConstructor
public class ChatMessageResponse {
    String message;
    String senderName;
    String senderEmail;
    Long roomId;
    LocalDateTime time;
}
