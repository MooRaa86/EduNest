package com.example.gradproj.EduNest.entity.projects;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Table(name = "project_submission",
        uniqueConstraints =
        @UniqueConstraint(columnNames = {"project_id","student_id"})
)
public class ProjectSubmission extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "project_id",nullable = false)
    private Project project;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "uploaded_file_path")
    private String uploadedFilePath;

    @Column(name = "raw_score")
    private Integer rawScore;

    @Column(name = "final_score")
    private Integer finalScore;
    @Column(name = "project_feedback")
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
