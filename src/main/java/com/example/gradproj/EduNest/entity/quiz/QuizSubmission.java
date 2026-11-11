package com.example.gradproj.EduNest.entity.quiz;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.users.Student;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "quizSubmission" ,
        uniqueConstraints = { @UniqueConstraint(columnNames = {"student_id", "quiz_id"})}
)
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class QuizSubmission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false )
    private Quiz quiz;

    @Min(value = 0, message = "Score cannot be negative")
    private Double score;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    /**
     * EB-53 / D-08 — true when the submission landed after the attempt window
     * (within the configured grace). Nullable: legacy rows are never backfilled.
     */
    private Boolean isLate;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StudentAnswer> answers;
}
