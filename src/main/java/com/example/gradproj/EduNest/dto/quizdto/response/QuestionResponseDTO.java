package com.example.gradproj.EduNest.dto.quizdto.response;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionResponseDTO {

    @NotNull
    private Long id;

    @NotBlank
    @Size(max = 500)
    private String text;

    @Min(0)
    private int points;

    @Min(1)
    private int orderNumber;

    @NotBlank
    @Size(max = 100)
    private String correctAnswer;

    @NotBlank
    @Size(max = 100)
    private String optionA;

    @NotBlank
    @Size(max = 100)
    private String optionB;

    @NotBlank
    @Size(max = 100)
    private String optionC;

    @NotBlank
    @Size(max = 100)
    private String optionD;
}
