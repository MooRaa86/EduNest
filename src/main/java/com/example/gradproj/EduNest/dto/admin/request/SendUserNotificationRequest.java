package com.example.gradproj.EduNest.dto.admin.request;

import lombok.Data;
import java.util.UUID;

@Data
public class SendUserNotificationRequest {
    private UUID userId;
    private String title;
    private String content;
}
