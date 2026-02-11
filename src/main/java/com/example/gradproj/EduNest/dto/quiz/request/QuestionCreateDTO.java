package com.example.gradproj.EduNest.dto.quiz.request;

import com.example.gradproj.EduNest.enums.quiz.AnswerChoices;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionCreateDTO {

    @NotNull(message = "Quiz ID is required")
    private Long quizId;

    @NotBlank(message = "Question text is required")
    @Size(max = 500, message = "Question text can't exceed 500 characters")
    private String text;

    @Min(value = 0, message = "Points can't be negative")
    private Integer points;

    @NotNull(message = "Correct answer is required")
    private AnswerChoices correctAnswer;

    @NotBlank(message = "Option A is required")
    @Size(max = 100)
    private String optionA;

    @NotBlank(message = "Option B is required")
    @Size(max = 100)
    private String optionB;

    @NotBlank(message = "Option C is required")
    @Size(max = 100)
    private String optionC;

    @NotBlank(message = "Option D is required")
    @Size(max = 100)
    private String optionD;
}
