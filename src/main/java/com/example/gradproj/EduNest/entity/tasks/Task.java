package com.example.gradproj.EduNest.entity.tasks;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Task extends BaseEntity {
    @Column(name = "task_title",nullable = false)
    private String title;
    @Lob
    @Column(name = "task_description",nullable = false)
    private  String description;
    @Column(name = "task_points",nullable = false)
    private Integer points;
    @Column(name = "task_pass_points",nullable = false)
    private Integer passPoints;
    @Column(name="task_estimated_minutes",nullable = false)
    private Integer estimatedMinutes;

    @Column(name = "task_max_attempts",nullable = false)
    @Builder.Default
    private Integer maxAttempts=3;
    @Column(name = "task_status",nullable = false)
    @Enumerated(EnumType.STRING)
    private TaskStatus status=TaskStatus.DRAFT;
    @Column(name = "task_due_at",nullable = false)
    private LocalDateTime dueAt;
    @Column(name = "task_attachment_url")
    private String attachmentUrl;


}
