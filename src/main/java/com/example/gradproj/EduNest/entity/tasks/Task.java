package com.example.gradproj.EduNest.entity.tasks;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tasks", indexes = {
        @Index(name = "idx_task_week_id", columnList = "week_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Task extends BaseEntity {
    @Column(name = "task_title",nullable = false)
    private String title;
    @Lob
    @Size(max = 10000,message = "Description must be less than 10000 characters")
    @Column(name = "task_description",nullable = false)
    private  String description;

    @Column(name = "task_points",nullable = false)
    private Integer points;

    @Column(name = "task_pass_points",nullable = false)
    private Integer passPoints;

    @Column(name="task_estimated_minutes",nullable = false)
    private Integer estimatedMinutes;

    @Column(name = "task_status",nullable = false)
    @Enumerated(EnumType.STRING)
    private TaskStatus status=TaskStatus.DRAFT;

    @Column(name = "task_due_at",nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "task_attachment_url")
    private String attachmentUrl;

    @Column(name = "uploaded_attachment_path")
    private String uploadedAttachmentPath;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TaskSubmission> submissions = new ArrayList<>();


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "week_id",nullable = false)
    private Week week;

}
