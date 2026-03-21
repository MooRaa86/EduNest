package com.example.gradproj.EduNest.dto.tasks.response;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskStatisticsDTO {
    private TaskStatus status;
    private String taskTitle;
    private int totalStudents;
    private int totalSubmissions;
    private int pendingReview;
    private LocalDateTime createdAt;
    private LocalDateTime deadLine;
    private int totalPoints;

    private PageResponse<TaskSubmissionResponse> taskSubmissionResponsePageResponse;
}
