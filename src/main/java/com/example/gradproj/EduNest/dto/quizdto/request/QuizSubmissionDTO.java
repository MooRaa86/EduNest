package com.example.gradproj.EduNest.dto.quizdto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QuizSubmissionDTO {

//    @NotNull(message = "Student ID is required")
//    private Long studentId;

//    private Long quizId;

    @NotNull
    private List<StudentAnswerDTO> answers;
}
