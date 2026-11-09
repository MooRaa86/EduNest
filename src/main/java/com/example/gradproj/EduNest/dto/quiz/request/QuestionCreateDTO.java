package com.example.gradproj.EduNest.dto.quiz.request;

import com.example.gradproj.EduNest.enums.quiz.AnswerChoices;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionCreateDTO {

    @NotNull(message = "Quiz ID is required")
    private UUID quizId;

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

    public static class QuestionCreateDTOBuilder {
        public QuestionCreateDTOBuilder quizId(Long id) {
            this.quizId = id != null ? new UUID(0, id) : null;
            return this;
        }

        public QuestionCreateDTOBuilder quizId(UUID id) {
            this.quizId = id;
            return this;
        }
    }
}
