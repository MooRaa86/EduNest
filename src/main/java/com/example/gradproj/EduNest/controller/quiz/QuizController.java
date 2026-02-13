package com.example.gradproj.EduNest.controller.quiz;


import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.quiz.request.QuizCreateDTO;
import com.example.gradproj.EduNest.dto.quiz.request.QuizDashboardDTO;
import com.example.gradproj.EduNest.dto.quiz.request.QuizStatisticsDTO;
import com.example.gradproj.EduNest.dto.quiz.request.QuizUpdateDto;
import com.example.gradproj.EduNest.dto.quiz.response.QuizResponseDTO;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.service.quiz.quiz.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(
        name = "Quiz",
        description = "APIs for managing quizzes (create, update, delete, filter, dashboard, statistics)"
)
public class QuizController {

    private final QuizService quizService;

    @Operation(
            summary = "Create quiz",
            description = "Create a new quiz for a mentorship"
    )
    @PostMapping
    public ResponseEntity<SimpleResponse> createQuiz(@Valid @RequestBody QuizCreateDTO quizCreateDTO) {
        QuizResponseDTO quizResponseDTO = quizService.createQuiz(quizCreateDTO);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Quiz created successfully");
        simpleResponse.addMessage("Quiz Details", quizResponseDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(simpleResponse);
    }

    @Operation(
            summary = "Update quiz",
            description = "Update quiz details using quiz ID"
    )
    @PatchMapping("/{id}")
    public ResponseEntity<SimpleResponse> updateQuiz(@Valid @RequestBody QuizUpdateDto quizUpdateDto, @PathVariable Long id) {
        QuizResponseDTO quizResponseDTO = quizService.updateQuiz(id, quizUpdateDto);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Quiz updated successfully");
        simpleResponse.addMessage("Quiz Details", quizResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @Operation(
            summary = "Delete quiz",
            description = "Delete a quiz using quiz ID"
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<SimpleResponse> deleteQuiz(@PathVariable Long id) {
        quizService.deleteQuiz(id);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Quiz deleted successfully");
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @Operation(
            summary = "Get quiz details",
            description = "Retrieve quiz details by quiz ID"
    )
    @GetMapping("/{id}")
    public ResponseEntity<SimpleResponse> getQuiz(@PathVariable Long id) {
        QuizResponseDTO quizResponseDTO = quizService.getQuizDetails(id);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Quiz retrieved successfully");
        simpleResponse.addMessage("Quiz Details", quizResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @Operation(
            summary = "Filter quizzes",
            description = "Filter quizzes by name and status with pagination for a mentorship"
    )
    @GetMapping("/filter/{mentorshipId}")
    public ResponseEntity<SimpleResponse> filterQuizzes(
            @RequestParam(required = false) String quizName,
            @RequestParam(required = false) QuizStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "4") int size,
            @PathVariable Long mentorshipId
    ) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<QuizResponseDTO> response =
                quizService.getQuizzes(quizName, status, mentorshipId, pageable);

        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Quizzes retrieved successfully");
        simpleResponse.addMessage("Quizzes", response);

        return ResponseEntity.ok(simpleResponse);
    }

    @Operation(
            summary = "Quiz dashboard",
            description = "Retrieve quiz dashboard data for a mentorship"
    )
    @GetMapping("/dashboard/{mentorshipId}")
    public ResponseEntity<SimpleResponse> getDashboard(@PathVariable Long mentorshipId) {
        QuizDashboardDTO quizDashboardDTO = quizService.getQuizDashboard(mentorshipId);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Dashboard retrieved successfully");
        simpleResponse.addMessage("Dashboard Details", quizDashboardDTO);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @Operation(
            summary = "Quiz statistics",
            description = "Retrieve statistics for a specific quiz"
    )
    @GetMapping("/statistics/{id}")
    public ResponseEntity<SimpleResponse> getStatistics(@PathVariable Long id) {
        QuizStatisticsDTO quizStatisticsDTO = quizService.getQuizStatistics(id);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Statistics retrieved successfully");
        simpleResponse.addMessage("Statistics", quizStatisticsDTO);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @Operation(
            summary = "Change quiz status",
            description = "Change quiz status (DRAFT, PUBLISHED, CLOSED)"
    )
    @PostMapping("/change-status/{id}")
    public ResponseEntity<SimpleResponse> changeQuizStatus(@PathVariable Long id, @RequestParam QuizStatus status) {
        quizService.changeStatus(id, status);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Quiz status changed successfully");
        simpleResponse.addMessage("Quiz Status", status);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

}
