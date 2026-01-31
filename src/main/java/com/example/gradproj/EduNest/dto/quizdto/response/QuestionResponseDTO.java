package com.example.gradproj.EduNest.dto.quizdto.response;

import com.example.gradproj.EduNest.enums.quiz.AnswerChoices;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionResponseDTO {

    private Long id;

    private String text;

    private int points;

    private AnswerChoices correctAnswer;

    private String optionA;

    private String optionB;

    private String optionC;

    private String optionD;
}
