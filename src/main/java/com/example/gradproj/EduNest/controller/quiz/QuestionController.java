package com.example.gradproj.EduNest.controller.quiz;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.quiz.request.QuestionCreateDTO;
import com.example.gradproj.EduNest.dto.quiz.request.QuestionUpdateDto;
import com.example.gradproj.EduNest.dto.quiz.response.QuestionResponseDTO;
import com.example.gradproj.EduNest.service.quiz.question.QuestionService;
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
@RequestMapping("api/v1/question")
@Tag(
        name = "Quiz Questions",
        description = "APIs for managing quiz questions (create, update, delete, fetch)"
)
public class QuestionController {

    private final QuestionService questionService;

    @Operation(
            summary = "Add new question",
            description = "Create a new question and attach it to a quiz"
    )
    @PostMapping
    public ResponseEntity<SimpleResponse> addQuestion(@Valid @RequestBody QuestionCreateDTO questionCreateDTO){
        QuestionResponseDTO questionResponseDTO=questionService.createQuestion(questionCreateDTO);
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message","Question added successfully");
        simpleResponse.addMessage("Question Details",questionResponseDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(simpleResponse);
    }

    @Operation(
            summary = "Update question",
            description = "Update an existing question using question ID"
    )
    @PatchMapping("/{id}")
    public ResponseEntity<SimpleResponse> updateQuestion(@Valid @RequestBody QuestionUpdateDto questionUpdateDto, @PathVariable Long id){
        QuestionResponseDTO  questionResponseDTO=questionService.updateQuestion(id, questionUpdateDto );
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message","Question updated successfully");
        simpleResponse.addMessage("Question Details",questionResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @Operation(
            summary = "Delete question",
            description = "Delete a question from a quiz using quiz ID and question ID"
    )
    @DeleteMapping("/{quizId}/{questionId}")
    public ResponseEntity<SimpleResponse> deleteQuestion(@PathVariable Long quizId,@PathVariable Long questionId){
        questionService.deleteQuestion(quizId,questionId);
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message","Question deleted successfully");
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }


    @Operation(
            summary = "Get question by ID",
            description = "Retrieve question details using question ID"
    )
    @GetMapping("/{id}")
    public ResponseEntity<SimpleResponse> getQuestionById(@PathVariable Long id){
        QuestionResponseDTO questionResponseDTO=questionService.getQuestionById(id);
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message","Question retrieved successfully");
        simpleResponse.addMessage("Question Details",questionResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @Operation(
            summary = "Get questions by quiz ID",
            description = "Retrieve all questions that belong to a specific quiz"
    )
    @GetMapping("/fetch/{quizId}")
    public  ResponseEntity<SimpleResponse> getQuestionByQuizId(@PathVariable Long quizId){
        List<QuestionResponseDTO>AllQuestions=questionService.getQuestionsByQuizId(quizId);
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message","Questions retrieved successfully");
        simpleResponse.addMessage("Quiz Questions",AllQuestions);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }


}
