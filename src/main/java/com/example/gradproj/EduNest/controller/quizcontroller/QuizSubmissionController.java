package com.example.gradproj.EduNest.controller.quizcontroller;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizSubmissionDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.StudentAnswerDTO;
import com.example.gradproj.EduNest.dto.quizdto.response.QuizSubmissionResponseDTO;
import com.example.gradproj.EduNest.service.quizservice.submission.QuizSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1")
public class QuizSubmissionController
{
    private final QuizSubmissionService submissionService;

    @PostMapping("/submit-quiz-answer/{quizId}")
    public ResponseEntity<SimpleResponse>submitQuizAnswers(@Valid @RequestBody QuizSubmissionDTO  quizSubmissionDTO,@PathVariable Long quizId){
        QuizSubmissionResponseDTO quizSubmissionResponseDTO = submissionService.submitQuizAnswers(quizSubmissionDTO, quizId);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","Quiz submitted successfully");
        simpleResponse.addMessage("Score",quizSubmissionResponseDTO.getScore().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(simpleResponse);
    }

    @GetMapping("/answer/{studentId}/{quizId}")
    public ResponseEntity<SimpleResponse> getStudentAnswers(
            @PathVariable Long studentId,
            @PathVariable Long quizId) {

        List<StudentAnswerDTO> allAnswers = submissionService.getStudentAnswers(studentId, quizId);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","All answers");
        simpleResponse.addMessage("Answers",allAnswers);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @GetMapping("/submissions/quiz/{quizId}")
    public ResponseEntity<SimpleResponse> getAllSubmissionsByQuiz(
            @PathVariable Long quizId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<QuizSubmissionResponseDTO> quizSubmissionResponseDTOS =
                submissionService.getAllSubmissionsByQuiz(quizId, page, size);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","All Submissions of quiz");
        simpleResponse.addMessage("Submissions",quizSubmissionResponseDTOS);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @GetMapping("/submissions/student/{studentId}")
    public ResponseEntity<SimpleResponse> getAllSubmissionsByStudent(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<QuizSubmissionResponseDTO> quizSubmissionResponseDTOS =
                submissionService.getAllSubmissionsByStudent(studentId, page, size);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","All Submissions of student");
        simpleResponse.addMessage("Submissions",quizSubmissionResponseDTOS);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);

    }

}
