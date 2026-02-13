package com.example.gradproj.EduNest.entity.quizentity;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "quiz")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @Enumerated(EnumType.STRING)
    private QuizStatus status = QuizStatus.DRAFT;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true ,fetch = FetchType.LAZY)
    private List<Question> questions;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL,fetch = FetchType.LAZY)
    private List<QuizSubmission> submissions;
}
