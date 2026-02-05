package com.example.gradproj.EduNest.dto.quizdto.request;


import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizUpdateDto {

//    private Long mentorshipId;

    @Size(max = 100, message = "Title can't exceed 100 characters")
    private String title;

    @Size(max = 500, message = "Description can't exceed 500 characters")
    private String description;

    @Min(value = 1, message = "Duration must be at least 1 minute")
    private Integer durationMinutes;

    private QuizStatus status = QuizStatus.DRAFT;
}
