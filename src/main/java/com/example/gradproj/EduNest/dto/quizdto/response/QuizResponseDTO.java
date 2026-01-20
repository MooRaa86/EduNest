package com.example.gradproj.EduNest.dto.quizdto.response;
import com.example.gradproj.EduNest.enums.QuizStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class QuizResponseDTO {

    @NotNull
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String title;

    @NotNull
    private Integer durationMinutes;

    @Min(0)
    private Integer totalPoints;

    private QuizStatus status;

    private LocalDate deadline;

    private int submissions;
    private double averageScore;
}
