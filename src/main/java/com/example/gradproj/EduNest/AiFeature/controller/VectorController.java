package com.example.gradproj.EduNest.AiFeature.controller;

import com.example.gradproj.EduNest.AiFeature.dto.RoadmapRequest;
import com.example.gradproj.EduNest.AiFeature.service.VectorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vector")
@RequiredArgsConstructor
@Tag(name = "Vector API", description = "APIs for storing and searching vector data")
public class VectorController {

    private final VectorService vectorService;

    @PostMapping("/store")
    public String store(@RequestBody String content) {
        vectorService.store(content);
        return "Stored successfully ✅";
    }

    @GetMapping("/search")
    public List<String> search(@RequestParam String query) {
        List<Document> results = vectorService.search(query);

        return results.stream()
                .map(Document::getText)
                .toList();
    }

    @PostMapping("/roadmap")
    public String addRoadmap(@RequestBody RoadmapRequest request) {
        vectorService.storeRoadmap(request.getTitle(), request.getContent());
        return "Roadmap stored ✅";
    }

    @GetMapping("/roadmap")
    public List<String> getRoadmap(@RequestParam String query) {

        List<Document> results = vectorService.searchRoadmaps(query);

        return results.stream()
                .map(Document::getText)
                .toList();
    }
}