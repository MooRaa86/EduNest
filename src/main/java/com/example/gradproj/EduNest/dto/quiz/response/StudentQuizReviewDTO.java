package com.example.gradproj.EduNest.dto.quiz.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentQuizReviewDTO {
    private UUID questionId;
    private String text;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;

    /**
     * P1-18 / LEARN-01: populated only once the attempt is genuinely submitted
     * (QuizSubmission#score set). Pre-submission the service leaves it null and
     * this annotation omits the key from the JSON entirely, so review payloads
     * cannot leak the answer key before grading.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String correctAnswer;
    private String selectedAnswer;
}
