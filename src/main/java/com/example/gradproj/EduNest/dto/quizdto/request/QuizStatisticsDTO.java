package com.example.gradproj.EduNest.dto.quizdto.request;

import com.example.gradproj.EduNest.enums.QuizStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class QuizStatisticsDTO {
    private QuizStatus status;
    private int totalStudents;
    private int totalSubmissions;
    private double averageScore;
    private LocalDate deadline;
}
