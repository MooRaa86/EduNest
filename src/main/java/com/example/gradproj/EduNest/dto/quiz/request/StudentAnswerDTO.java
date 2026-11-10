package com.example.gradproj.EduNest.dto.quiz.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentAnswerDTO {

    @NotNull(message = "Question ID is required")
    private UUID questionId;

    @NotBlank(message = "Answer cannot be blank")
    @Size(max = 100)
    private String selectedAnswer;
}
