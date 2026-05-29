package com.example.gradproj.EduNest.entity.notification;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Notification extends BaseEntity {

    private String title;

    @Size(max = 700, message = "Notification content must not exceed 700 characters")
    private String content;

    @Enumerated(EnumType.STRING)
    private NotificationType type;
}
