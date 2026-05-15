package com.example.gradproj.EduNest.dto.admin.request;

import lombok.Data;

@Data
public class SendUserEmailRequest {
    private Long userId;
    private String subject;
    private String message;
}
