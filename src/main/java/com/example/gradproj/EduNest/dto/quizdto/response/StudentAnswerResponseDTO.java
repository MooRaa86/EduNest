package com.example.gradproj.EduNest.dto.quizdto.response;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StudentAnswerResponseDTO {

    @NotNull
    private Long id;

    @NotNull
    private Long submissionId;

    @NotNull
    private Long questionId;

    @NotBlank
    private String selectedAnswer;
}
