package com.example.gradproj.EduNest.dto.projects.response;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectStatisticsDTO {
    private ProjectStatus status;
    private String projectTitle;
    private int totalStudents;
    private int totalSubmissions;
    private int pendingReview;
    private String brief;
    private String attachmentUrl;
    private String uploadedAttachmentPath;
    private String goal;
    private LocalDateTime createdAt;
    private LocalDateTime deadLine;
    private int totalPoints;

    private PageResponse<ProjectSubmissionResponse> taskSubmissionResponsePageResponse;
}
