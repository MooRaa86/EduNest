package com.example.gradproj.EduNest.dto.notification;

import com.example.gradproj.EduNest.enums.notification.NotificationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationSendRequest {

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    private NotificationType type;
}