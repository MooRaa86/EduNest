package com.example.gradproj.EduNest.controller.quiz;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.quiz.request.QuizSubmissionDTO;
import com.example.gradproj.EduNest.dto.quiz.request.StudentAnswerDTO;
import com.example.gradproj.EduNest.dto.quiz.response.QuizSubmissionResponseDTO;
import com.example.gradproj.EduNest.service.quiz.submission.QuizSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1")
@Tag(
        name = "Quiz Submissions",
        description = "APIs for submitting quizzes and retrieving quiz submissions and answers"
)
public class QuizSubmissionController {
    private final QuizSubmissionService submissionService;

    @Operation(
            summary = "Submit quiz answers",
            description = "Submit student answers for a specific quiz and calculate the score"
    )
    @PostMapping("/submit-quiz-answer/{quizId}")
    public ResponseEntity<SimpleResponse> submitQuizAnswers(@Valid @RequestBody QuizSubmissionDTO quizSubmissionDTO, @PathVariable Long quizId) {
        QuizSubmissionResponseDTO quizSubmissionResponseDTO = submissionService.submitQuizAnswers(quizSubmissionDTO, quizId);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Quiz submitted successfully");
        simpleResponse.addMessage("Score", quizSubmissionResponseDTO.getScore().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(simpleResponse);
    }

    @Operation(
            summary = "Get student answers",
            description = "Retrieve all answers submitted by a specific student for a quiz"
    )
    @GetMapping("/answer/{studentId}/{quizId}")
    public ResponseEntity<SimpleResponse> getStudentAnswers(
            @PathVariable Long studentId,
            @PathVariable Long quizId) {

        List<StudentAnswerDTO> allAnswers = submissionService.getStudentAnswers(studentId, quizId);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "All answers");
        simpleResponse.addMessage("Answers", allAnswers);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @Operation(
            summary = "Get quiz submissions",
            description = "Retrieve all submissions for a specific quiz with pagination"
    )
    @GetMapping("/submissions/quiz/{quizId}")
    public ResponseEntity<SimpleResponse> getAllSubmissionsByQuiz(
            @PathVariable Long quizId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<QuizSubmissionResponseDTO> quizSubmissionResponseDTOS =
                submissionService.getAllSubmissionsByQuiz(quizId, page, size);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "All Submissions of quiz");
        simpleResponse.addMessage("Submissions", quizSubmissionResponseDTOS);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @Operation(
            summary = "Get student submissions",
            description = "Retrieve all quiz submissions made by a specific student with pagination"
    )
    @GetMapping("/submissions/student/{studentId}")
    public ResponseEntity<SimpleResponse> getAllSubmissionsByStudent(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<QuizSubmissionResponseDTO> quizSubmissionResponseDTOS =
                submissionService.getAllSubmissionsByStudent(studentId, page, size);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "All Submissions of student");
        simpleResponse.addMessage("Submissions", quizSubmissionResponseDTOS);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);

    }

}
