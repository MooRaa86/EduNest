package com.example.gradproj.EduNest.entity.chat;

import com.example.gradproj.EduNest.entity.users.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    @Builder.Default
    private UUID publicId = UUID.randomUUID();

    @PrePersist
    protected void ensurePublicId() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID();
        }
    }


    // participant 1 — [EB-44][P3-02] non-null invariant (V3 adds SET NOT NULL)
    @ManyToOne(optional = false)
    private UserEntity user1;

    // participant 2 — [EB-44][P3-02] non-null invariant (V3 adds SET NOT NULL)
    @ManyToOne(optional = false)
    private UserEntity user2;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true )
    private List<Message> messages;
}
