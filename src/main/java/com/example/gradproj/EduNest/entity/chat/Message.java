package com.example.gradproj.EduNest.entity.chat;

import com.example.gradproj.EduNest.entity.users.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    @ManyToOne
    private Conversation conversation;

    @ManyToOne
    private UserEntity sender;

    private String content;

    private LocalDateTime sentAt;

    @Builder.Default
    private boolean seen  = false;

    @Builder.Default
    private boolean updated = false;
}