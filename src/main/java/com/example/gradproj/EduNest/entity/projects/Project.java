package com.example.gradproj.EduNest.entity.projects;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.mentorship.mentorShipE;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Project extends BaseEntity {
        @Column(name = "project_title",nullable = false)
        private String title;
        @Lob
        @Column(name = "project_description",nullable = false)
        private  String description;

        @Column(name = "project_points",nullable = false)
        private Integer points;

        @Column(name = "project_pass_points",nullable = false)
        private Integer passPoints;

        @Column(name="project_estimated_minutes",nullable = false)
        private Integer estimatedMinutes;

        @Column(name = "project_status",nullable = false)
        @Enumerated(EnumType.STRING)
        private ProjectStatus status=ProjectStatus.DRAFT;

        @Column(name = "project_due_at",nullable = false)
        private LocalDateTime dueAt;

        @Column(name = "project_attachment_url")
        private String attachmentUrl;

        @OneToMany(mappedBy = "project", cascade = CascadeType.REMOVE, orphanRemoval = true)
        private List<ProjectSubmission> submissions = new ArrayList<>();


        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "mentorship_id", nullable = false)
        private mentorShipE mentorship;

    }
