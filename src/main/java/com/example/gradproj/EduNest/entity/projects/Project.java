package com.example.gradproj.EduNest.entity.projects;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.mentorship.mentorShipE;
import com.example.gradproj.EduNest.enums.project.ProjectDifficultyLevel;
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

        @Column(name = "project_title", nullable = false, length = 150)
        private String title;

        @Lob
        @Column(name = "project_brief", nullable = false)
        private String brief;

        @Column(name = "project_goal", nullable = false, length = 255)
        private String goal;

//        @Enumerated(EnumType.STRING)
//        @Column(name = "project_difficulty", nullable = false)
//        private ProjectDifficultyLevel difficulty;

        @Column(name = "project_description_url", length = 1000)
        private String descriptionUrl;

        @Column(name = "project_start_at", nullable = false)
        private LocalDateTime startAt;

        @Column(name = "project_end_at", nullable = false)
        private LocalDateTime endAt;

        @Column(name = "project_points", nullable = false)
        private Integer points;

        @Enumerated(EnumType.STRING)
        @Column(name = "project_status", nullable = false)
        private ProjectStatus status = ProjectStatus.DRAFT;

        @OneToMany(mappedBy = "project", cascade = CascadeType.REMOVE, orphanRemoval = true)
        private List<ProjectSubmission> submissions = new ArrayList<>();

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "mentorship_id", nullable = false)
        private mentorShipE mentorship;
    }
