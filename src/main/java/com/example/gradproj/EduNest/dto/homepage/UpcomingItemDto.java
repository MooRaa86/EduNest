package com.example.gradproj.EduNest.dto.homepage;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpcomingItemDto {
    private Long id;
    private String title;
    private String type; // SESSION, TASK, PROJECT
    private LocalDateTime dueDate;
    private Long mentorshipId;
    private String mentorshipTitle;
    private Long weekId;
    private String weekTitle;
    private Integer points;
}
