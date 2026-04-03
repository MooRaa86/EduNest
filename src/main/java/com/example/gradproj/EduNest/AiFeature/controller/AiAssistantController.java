package com.example.gradproj.EduNest.AiFeature.controller;

import com.example.gradproj.EduNest.AiFeature.dto.UserPrompt;
import com.example.gradproj.EduNest.AiFeature.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Assistant API", description = "APIs for interacting with the AI assistant")
public class AiAssistantController {

    private final AiService aiService;

    @PostMapping("/ask")
    @Operation(summary = "Ask global ai (not specific domain)")
    public String askAi(UserPrompt userPrompt) {
        return aiService.ask(userPrompt.getPrompt());
    }

}
