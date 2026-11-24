package com.example.gradproj.EduNest.entity.chat;

import com.example.gradproj.EduNest.entity.users.UserEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    @Builder.Default
    private UUID publicId = UUID.randomUUID();

    @PrePersist
    protected void ensurePublicId() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID();
        }
    }

    @ManyToOne
    private Conversation conversation;

    @ManyToOne
    private UserEntity sender;

    @Size(max = 10000, message = "Content must be less than 10000 characters")
    private String content;

    private LocalDateTime sentAt;

    @Builder.Default
    private boolean seen  = false;

    @Builder.Default
    private boolean updated = false;
}