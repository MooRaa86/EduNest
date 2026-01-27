package com.example.gradproj.EduNest.controller.quizcontroller;


import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizCreateDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizDashboardDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizStatisticsDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizUpdateDto;
import com.example.gradproj.EduNest.dto.quizdto.response.QuizResponseDTO;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.service.quizservice.quiz.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/quiz")
public class QuizController {

    private final QuizService quizService;

    @PostMapping
    public ResponseEntity<SimpleResponse> createQuiz(@Valid  @RequestBody QuizCreateDTO quizCreateDTO) {
        QuizResponseDTO  quizResponseDTO = quizService.createQuiz(quizCreateDTO);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","Quiz created successfully");
        simpleResponse.addMessage("Quiz Details",quizResponseDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(simpleResponse);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SimpleResponse> updateQuiz(@Valid @RequestBody QuizUpdateDto quizUpdateDto , @PathVariable Long id) {
        QuizResponseDTO quizResponseDTO = quizService.updateQuiz(id,quizUpdateDto);
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
            @RequestParam(required = false) String quizName,
            @RequestParam(required = false) QuizStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "4") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<QuizResponseDTO> response =
                quizService.getQuizzes(quizName, status, pageable);

        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Quizzes retrieved successfully");
        simpleResponse.addMessage("Quizzes", response);

        return ResponseEntity.ok(simpleResponse);
    }

    @GetMapping("/dashboard/{mentorshipId}")
    public ResponseEntity<SimpleResponse> getDashboard(@PathVariable  Long mentorshipId) {
        QuizDashboardDTO quizDashboardDTO=quizService.getQuizDashboard(mentorshipId);
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

    @PostMapping("/change-status/{id}")
    public ResponseEntity<SimpleResponse>changeQuizStatus(@PathVariable Long id, @RequestParam QuizStatus status) {
        quizService.changeStatus(id, status);        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","Quiz status changed successfully");
        simpleResponse.addMessage("Quiz Status",status);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

}
