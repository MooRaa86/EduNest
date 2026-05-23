package com.example.gradproj.EduNest.util;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileResponseBuilder {

    public static ResponseEntity<Resource> buildResponse(Path filePath, boolean download) throws IOException {
        String contentType = Files.probeContentType(filePath);
        MediaType mediaType = parseMediaType(contentType);
        
        boolean safeInline = mediaType.getType().equalsIgnoreCase("image")
                || mediaType.equals(MediaType.APPLICATION_PDF)
                || mediaType.equals(MediaType.TEXT_PLAIN);

        ContentDisposition disposition = (!download && safeInline)
                ? ContentDisposition.inline().filename(filePath.getFileName().toString()).build()
                : ContentDisposition.attachment().filename(filePath.getFileName().toString()).build();

        InputStream inputStream = Files.newInputStream(filePath);
        InputStreamResource resource = new InputStreamResource(inputStream);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(Files.size(filePath))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .body(resource);
    }

    private static MediaType parseMediaType(String contentType) {
        try {
            return contentType != null 
                    ? MediaType.parseMediaType(contentType)
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
