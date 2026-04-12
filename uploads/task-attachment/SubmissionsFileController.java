package com.example.gradproj.EduNest.controller.tasks;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/uploads")
@Tag(name = "Submission Files", description = "View or download uploaded files")
@Slf4j
public class SubmissionsFileController {

    @Value("${app.upload.base-dir:uploads}")
    private String baseDir;

    @GetMapping("/file")
    @Operation(summary = "View or download uploaded file by full path")
    public ResponseEntity<Resource> serveFile(
            @RequestParam String filePath,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        try {
            Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
            Path requestedPath = Paths.get(filePath).toAbsolutePath().normalize();

            if (!requestedPath.startsWith(basePath)) {
                log.warn("Access denied outside upload directory: {}", filePath);
                return ResponseEntity.badRequest().build();
            }

            Resource resource = getResource(requestedPath);
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = Files.probeContentType(requestedPath);
            MediaType mediaType = parseMediaType(contentType);

            ContentDisposition contentDisposition = download
                    ? ContentDisposition.attachment().filename(resource.getFilename()).build()
                    : ContentDisposition.inline().filename(resource.getFilename()).build();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                    .body(resource);

        } catch (Exception e) {
            log.error("Error serving file: {}", filePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Resource getResource(Path filePath) throws MalformedURLException {
        return new UrlResource(filePath.toUri());
    }

    private MediaType parseMediaType(String contentType) {
        try {
            return contentType != null
                    ? MediaType.parseMediaType(contentType)
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}