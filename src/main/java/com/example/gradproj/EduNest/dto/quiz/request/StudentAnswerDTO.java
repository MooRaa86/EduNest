package com.example.gradproj.EduNest.dto.quiz.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentAnswerDTO {

//    private Long submissionId;

    @NotNull(message = "Question ID is required")
    private Long questionId;

    @NotBlank(message = "Answer cannot be blank")
    @Size(max = 100)
    private String selectedAnswer;
}
