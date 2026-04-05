package com.example.gradproj.EduNest.dto.projects.response;

import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectWithSubmissionResponse {

    private Long projectId;
    private String title;
    private String brief;
    private String descriptionUrl;
    private Integer points;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;
    private String goal;

    private Long submissionId;
    private SubmissionStatus submissionStatus;
    private Integer score;
    private Integer totalPoints;
    private String fileUrl;
    private String uploadedFilePath;
    private String feedback;

    private Long mentorId;
    private String mentorName;
    private String mentorPhoto;
}
