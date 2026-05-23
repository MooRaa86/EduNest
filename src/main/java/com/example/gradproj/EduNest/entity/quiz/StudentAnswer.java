package com.example.gradproj.EduNest.entity.quiz;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
        name = "student_answer",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"submission_id", "question_id"})}
)
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StudentAnswer extends BaseEntity {

    @NotNull(message = "Quiz submission must not be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private QuizSubmission submission;

    @NotNull(message = "Question must not be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @NotBlank(message = "Answer must not be blank")
    private String selectedAnswer;
}
