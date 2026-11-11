package com.example.gradproj.EduNest.entity.quiz;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "quiz", indexes = {
        @Index(name = "idx_quiz_week_id", columnList = "week_id")
})
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Quiz extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "week_id",nullable = false)
    private Week week;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private Integer durationMinutes;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private QuizStatus status = QuizStatus.DRAFT;

    /**
     * EB-53 / D-08 — absolute close deadline. Nullable: legacy PUBLISHED quizzes
     * (closeAt == null) keep their manual-close-only behavior and are never
     * auto-closed or backfilled.
     */
    private LocalDateTime closeAt;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true ,fetch = FetchType.LAZY)
    private List<Question> questions;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<QuizSubmission> submissions;
}
