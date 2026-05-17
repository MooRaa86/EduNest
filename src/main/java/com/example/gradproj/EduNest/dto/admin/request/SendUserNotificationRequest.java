package com.example.gradproj.EduNest.dto.admin.request;

import lombok.Data;

@Data
public class SendUserNotificationRequest {
    private Long userId;
    private String title;
    private String content;
}
