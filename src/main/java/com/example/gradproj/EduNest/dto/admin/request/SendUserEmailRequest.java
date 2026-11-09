package com.example.gradproj.EduNest.dto.admin.request;

import lombok.Data;
import java.util.UUID;

@Data
public class SendUserEmailRequest {
    private UUID userId;
    private String subject;
    private String message;
}
