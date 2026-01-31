package com.example.gradproj.EduNest.entity.quizentity;

import com.example.gradproj.EduNest.entity.users.Student;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

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
@Builder
public class QuizSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false )
    private Quiz quiz;

    @Min(value = 0, message = "Score cannot be negative")
    private Integer score;

    @NotNull(message = "Submission date must not be null")
    private LocalDateTime submittedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StudentAnswer> answers;
}
