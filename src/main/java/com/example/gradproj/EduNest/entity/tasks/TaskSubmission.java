package com.example.gradproj.EduNest.entity.tasks;

import com.example.gradproj.EduNest.entity.BaseEntity;
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
             @UniqueConstraint(columnNames = {"task_id","student_id","attempt_no"})
)
public class TaskSubmission extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "task_id",nullable = false)
    private Task task;

    @Column(name = "student_id")
    private Long studentId;
    @Column(name = "attempt_no")
    private Integer attemptNo;
    @Column(name = "file_url")
    private String fileUrl;
    //الدرجه الي المدرس دخلها مش النهائيه لسه هنحسب بيها الدرجه النهائيه بعد حساب هل التاسك متاخر ولا لا
    @Column(name = "raw_score")
    private Integer rawScore;
    // الدرجة بعد خصم التأخير (السيستم يحسبها)
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
    @Column(name = "graded_By")
    private String gradedBy;
    @Column(name = "is_late")
    private Boolean isLate=false;


}
