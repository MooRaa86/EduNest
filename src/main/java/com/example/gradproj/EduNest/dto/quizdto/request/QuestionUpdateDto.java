package com.example.gradproj.EduNest.dto.quizdto.request;

import com.example.gradproj.EduNest.enums.quiz.AnswerChoices;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;


@Getter
@Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class QuestionUpdateDto {

    private Long quizId;

    @Size(max = 500, message = "Question text can't exceed 500 characters")
    private String text;

    @Min(value = 0, message = "Points can't be negative")
    private Integer points;

    private AnswerChoices correctAnswer;

    @Size(max = 100)
    private String optionA;

    @Size(max = 100)
    private String optionB;

    @Size(max = 100)
    private String optionC;

    @Size(max = 100)
    private String optionD;
}
