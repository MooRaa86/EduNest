package com.example.gradproj.EduNest.entity.quizentity;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.mentorship.Mentorship;
import com.example.gradproj.EduNest.enums.QuizStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "quiz")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz extends BaseEntity {

    @ManyToOne
    @JoinColumn(name="mentorship_id")
    private Mentorship mentorship;

    @NotBlank(message = "Quiz title cannot be blank")
    @Size(max = 100, message = "Quiz title cannot exceed 100 characters")
    private String title;

    @NotNull
    private Integer durationMinutes;

    @Min(0)
    private Integer totalPoints;

    @Enumerated(EnumType.STRING)
    private QuizStatus status = QuizStatus.DRAFT;

    private LocalDate deadline;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Question> questions;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL)
    private List<QuizSubmission> submissions;
}
