package com.example.gradproj.EduNest.dto.quiz.request;


import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

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

    /**
     * EB-53 / D-08 — absolute close deadline, editable while DRAFT. Required
     * (and in the future) when this update transitions the quiz to PUBLISHED.
     */
    private LocalDateTime closeAt;

    @Builder.Default
    private QuizStatus status = QuizStatus.DRAFT;
}
