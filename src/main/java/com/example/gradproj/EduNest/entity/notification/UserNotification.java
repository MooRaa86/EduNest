package com.example.gradproj.EduNest.entity.notification;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"user_id","notification_id"}
                )
        }
        , indexes = {
        @Index(name = "idx_notifications_user_id", columnList = "user_id")
}
)
public class UserNotification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id")
    private Notification notification;

    @Builder.Default
    @Column(name = "is_read")
    private boolean isRead = false;

    @Column(name = "certificate_issuance_id")
    private UUID certificateIssuanceId;

    @Column(name = "certificate_recipient_email")
    private String certificateRecipientEmail;
}
