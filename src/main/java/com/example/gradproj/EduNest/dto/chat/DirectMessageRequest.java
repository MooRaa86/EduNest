package com.example.gradproj.EduNest.dto.chat;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DirectMessageRequest {

    private String receiverEmail;
    private String content;
}