package com.example.gradproj.EduNest.AiFeature.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiService {

    private final ChatClient chatClient;
    private final VectorService vectorService;

    public String ask (String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    public String askWithContext(String query) {

        var docs = vectorService.search(query);

        String context = docs.stream()
                .map(doc -> doc.getText())
                .reduce("", (a, b) -> a + "\n" + b);

        return chatClient.prompt()
                .system("You are an AI assistant. Answer only from the provided context.")
                .user("Context:\n" + context + "\n\nQuestion:\n" + query)
                .call()
                .content();
    }

}
