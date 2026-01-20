package com.example.gradproj.EduNest.dto.quizdto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudentAnswerDTO {

    @NotNull(message = "Quiz Submission ID is required")
    private Long submissionId;

    @NotNull(message = "Question ID is required")
    private Long questionId;

    @NotBlank(message = "Answer cannot be blank")
    @Size(max = 100)
    private String selectedAnswer;
}
