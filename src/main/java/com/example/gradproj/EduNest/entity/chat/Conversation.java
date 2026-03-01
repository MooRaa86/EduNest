package com.example.gradproj.EduNest.entity.chat;

import com.example.gradproj.EduNest.entity.users.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@Builder
@AllArgsConstructor @NoArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // participant 1
    @ManyToOne
    private UserEntity user1;

    // participant 2
    @ManyToOne
    private UserEntity user2;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true )
    private List<Message> messages;
}
