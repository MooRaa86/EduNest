package com.example.gradproj.EduNest.controller.quizcontroller;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.quizdto.request.QuestionCreateDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuestionUpdateDto;
import com.example.gradproj.EduNest.dto.quizdto.response.QuestionResponseDTO;
import com.example.gradproj.EduNest.service.quizservice.question.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/question")
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping
    public ResponseEntity<SimpleResponse> addQuestion(@Valid @RequestBody QuestionCreateDTO questionCreateDTO){
        QuestionResponseDTO questionResponseDTO=questionService.createQuestion(questionCreateDTO);
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message","Question added successfully");
        simpleResponse.addMessage("Question Details",questionResponseDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(simpleResponse);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SimpleResponse> updateQuestion(@Valid @RequestBody QuestionUpdateDto questionUpdateDto, @PathVariable Long id){
        QuestionResponseDTO  questionResponseDTO=questionService.updateQuestion(id, questionUpdateDto );
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message","Question updated successfully");
        simpleResponse.addMessage("Question Details",questionResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }


    @DeleteMapping("/{quizId}/{questionId}")
    public ResponseEntity<SimpleResponse> deleteQuestion(@PathVariable Long quizId,@PathVariable Long questionId){
        questionService.deleteQuestion(quizId,questionId);
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message","Question deleted successfully");
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }


    @GetMapping("/{id}")
    public ResponseEntity<SimpleResponse> getQuestionById(@PathVariable Long id){
        QuestionResponseDTO questionResponseDTO=questionService.getQuestionById(id);
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message","Question retrieved successfully");
        simpleResponse.addMessage("Question Details",questionResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }


    @GetMapping("/fetch/{quizId}")
    public  ResponseEntity<SimpleResponse> getQuestionByQuizId(@PathVariable Long quizId){
        List<QuestionResponseDTO>AllQuestions=questionService.getQuestionsByQuizId(quizId);
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message","Questions retrieved successfully");
        simpleResponse.addMessage("Quiz Questions",AllQuestions);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }


}
