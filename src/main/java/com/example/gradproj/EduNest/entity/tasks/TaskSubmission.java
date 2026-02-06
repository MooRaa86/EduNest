package com.example.gradproj.EduNest.entity.tasks;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "task_submission",
        uniqueConstraints =
             @UniqueConstraint(columnNames = {"task_id","student_id"})
)
public class TaskSubmission extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "task_id",nullable = false)
    private Task task;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "raw_score")
    private Integer rawScore;

    @Column(name = "final_score")
    private Integer finalScore;
    @Column(name = "task_feedback")
    @Lob
    private String feedBack;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_status")
    private SubmissionStatus status=SubmissionStatus.SUBMITTED;

    @Column(name= "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    @Column(name = "is_late")
    private Boolean isLate=false;

    @Column(name = "points_applied")
    private Integer pointsApplied;



    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;


}
