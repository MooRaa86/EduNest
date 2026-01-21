package com.example.gradproj.EduNest.dto.quizdto.request;

import com.example.gradproj.EduNest.entity.quizentity.StudentAnswer;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QuizSubmissionDTO {

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotNull(message = "Quiz ID is required")
    private Long quizId;

    @NotNull
    private List<StudentAnswerDTO> answers;
}
