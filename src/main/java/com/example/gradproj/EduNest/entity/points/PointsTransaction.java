package com.example.gradproj.EduNest.entity.points;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.quizentity.QuizSubmission;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.points.PointsReason;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
        name = "points_transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_points_task_submission", columnNames = {"task_submission_id"}),
                @UniqueConstraint(name = "uk_points_quiz_submission", columnNames = {"quiz_submission_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PointsTransaction  extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    private Integer points;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointsReason reason;

    @Column(columnDefinition = "TEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_submission_id")
    private TaskSubmission taskSubmission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_submission_id")
    private QuizSubmission quizSubmission;

}
