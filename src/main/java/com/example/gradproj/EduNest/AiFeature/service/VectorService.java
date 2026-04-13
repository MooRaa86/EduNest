package com.example.gradproj.EduNest.AiFeature.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VectorService {

    private final VectorStore vectorStore;

    public void store(String content) {
        vectorStore.add(List.of(new Document(content)));
    }

    public List<Document> search(String query) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(10)
                        .build()
        );
    }

    public void storeRoadmap(String title, String content) {

        Document doc = new Document(content);
        doc.getMetadata().put("type", "roadmap");
        doc.getMetadata().put("title", title);

        vectorStore.add(List.of(doc));
    }

    public List<Document> searchRoadmaps(String query) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(1)
                        .filterExpression("type == 'roadmap'")
                        .build()
        );
    }
}
