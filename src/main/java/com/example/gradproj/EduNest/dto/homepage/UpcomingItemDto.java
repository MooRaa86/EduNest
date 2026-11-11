package com.example.gradproj.EduNest.dto.homepage;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpcomingItemDto {
    private UUID id;
    private String title;
    private String type; // SESSION, TASK, PROJECT
    private LocalDateTime dueDate;
    private UUID mentorshipId;
    private String mentorshipTitle;
    private UUID weekId;
    private String weekTitle;
    private Integer points;
}
