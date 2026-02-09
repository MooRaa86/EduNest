package com.example.gradproj.EduNest.dto.projects.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProjectResponse {
    private Long id;
    private String title;
    private String goal;
//    private String difficulty;
    private String brief;
    private String descriptionUrl;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;
    private Integer points;
    private String status;
    private Long mentorshipId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
