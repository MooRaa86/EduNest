package com.example.gradproj.EduNest.AiFeature.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiService {

    private final ChatClient chatClient;

    public String ask (String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}
