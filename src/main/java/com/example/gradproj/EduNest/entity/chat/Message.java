package com.example.gradproj.EduNest.entity.chat;

import com.example.gradproj.EduNest.entity.users.UserEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
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

    @Size(max = 10000, message = "Content must be less than 10000 characters")
    private String content;

    private LocalDateTime sentAt;

    @Builder.Default
    private boolean seen  = false;

    @Builder.Default
    private boolean updated = false;
}