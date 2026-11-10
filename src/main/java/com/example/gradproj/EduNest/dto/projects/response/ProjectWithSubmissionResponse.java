package com.example.gradproj.EduNest.dto.projects.response;

import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectWithSubmissionResponse {

    private UUID projectId;
    private String title;
    private String brief;
    private String descriptionUrl;
    private String attachmentPath;
    private Integer points;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;
    private String goal;

    private UUID submissionId;
    private SubmissionStatus submissionStatus;
    private Integer score;
    private Integer totalPoints;
    private String fileUrl;
    private String uploadedFilePath;
    private String feedback;

    private UUID mentorId;
    private String mentorName;
    private String mentorPhoto;
}
