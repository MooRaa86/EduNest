package com.example.gradproj.EduNest.dto.quiz.response;

import com.example.gradproj.EduNest.enums.quiz.AnswerChoices;
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
