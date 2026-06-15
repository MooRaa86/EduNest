package com.example.gradproj.EduNest.entity.projects;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
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
@Table(name = "projects", indexes = {
        @Index(name = "idx_project_week_id", columnList = "week_id")
})
@Setter @Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Project extends BaseEntity {

        @Column(name = "project_title", nullable = false, length = 150)
        private String title;

        @Lob
        @Size(max = 10000,message = "Brief must be less than 10000 characters")
        @Column(name = "project_brief", nullable = false)
        private String brief;

        @Column(name = "project_goal", nullable = false, length = 255)
        private String goal;

        @Column(name = "project_description_url", length = 1000)
        private String descriptionUrl;

        @Column(name = "uploaded_attachment_path", length = 500)
        private String uploadedAttachmentPath;

        @Column(name = "project_start_at", nullable = false)
        private LocalDateTime startAt;

        @Column(name = "project_end_at", nullable = false)
        private LocalDateTime endAt;

        @Column(name = "project_points", nullable = false)
        private Integer points;

        @Enumerated(EnumType.STRING)
        @Column(name = "project_status", nullable = false)
        private ProjectStatus status = ProjectStatus.DRAFT;

        @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<ProjectSubmission> submissions = new ArrayList<>();


        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "week_id",nullable = false)
        private Week week;

}
