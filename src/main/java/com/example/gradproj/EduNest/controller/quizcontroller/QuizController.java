package com.example.gradproj.EduNest.controller.quizcontroller;


import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizDashboardDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizStatisticsDTO;
import com.example.gradproj.EduNest.dto.quizdto.response.QuizResponseDTO;
import com.example.gradproj.EduNest.enums.QuizStatus;
import com.example.gradproj.EduNest.service.quizservice.quiz.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/quiz")
public class QuizController {

    private final QuizService quizService;

    @PostMapping
    public ResponseEntity<SimpleResponse> createQuiz(@Valid  @RequestBody QuizDTO quizdto) {
        QuizResponseDTO  quizResponseDTO = quizService.createQuiz(quizdto);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","Quiz created successfully");
        simpleResponse.addMessage("Quiz Details",quizResponseDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(simpleResponse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SimpleResponse> updateQuiz(@Valid @RequestBody QuizDTO quizdto , @PathVariable Long id) {
        QuizResponseDTO quizResponseDTO = quizService.updateQuiz(id,quizdto);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","Quiz updated successfully");
        simpleResponse.addMessage("Quiz Details",quizResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SimpleResponse> deleteQuiz(@PathVariable Long id) {
        quizService.deleteQuiz(id);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","Quiz deleted successfully");
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SimpleResponse> getQuiz(@PathVariable Long id) {
        QuizResponseDTO quizResponseDTO=quizService.getQuizDetails(id);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","Quiz retrieved successfully");
        simpleResponse.addMessage("Quiz Details",quizResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @GetMapping("/filter")
    public ResponseEntity<SimpleResponse> filterQuizzes(
            @RequestParam String quizName,
            @RequestParam QuizStatus status,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate deadline,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "4") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<QuizResponseDTO> response =
                quizService.getQuizzes(quizName, status, deadline, pageable);

        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Quizzes retrieved successfully");
        simpleResponse.addMessage("Quizzes", response);

        return ResponseEntity.ok(simpleResponse);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<SimpleResponse> getDashboard() {
        QuizDashboardDTO quizDashboardDTO=quizService.getQuizDashboard();
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","Dashboard retrieved successfully");
        simpleResponse.addMessage("Dashboard Details",quizDashboardDTO);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @GetMapping("/statistics/{id}")
    public ResponseEntity<SimpleResponse> getStatistics(@PathVariable Long id) {
        QuizStatisticsDTO quizStatisticsDTO=quizService.getQuizStatistics(id);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","Statistics retrieved successfully");
        simpleResponse.addMessage("Statistics", quizStatisticsDTO);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @PostMapping("/publish/{id}")
    public ResponseEntity<SimpleResponse> publishQuiz(@PathVariable Long id) {
        quizService.publishQuiz(id);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","Quiz published successfully");
          return  ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @PostMapping("/close/{id}")
    public ResponseEntity<SimpleResponse> closeQuiz(@PathVariable Long id) {
        quizService.closeQuiz(id);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","Quiz Closed successfully");
        return  ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }
}
