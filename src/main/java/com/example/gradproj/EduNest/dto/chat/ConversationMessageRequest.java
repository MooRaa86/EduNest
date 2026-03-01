package com.example.gradproj.EduNest.dto.chat;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ConversationMessageRequest {

    private String recipientEmail;
    private String content;
}