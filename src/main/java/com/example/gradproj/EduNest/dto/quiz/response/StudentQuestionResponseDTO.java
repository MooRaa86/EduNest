package com.example.gradproj.EduNest.dto.quiz.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentQuestionResponseDTO {

    private Long id;

    private String text;

    private int points;

    private String optionA;

    private String optionB;

    private String optionC;

    private String optionD;
}
