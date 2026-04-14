package com.example.gradproj.EduNest.controller.tasks;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/files")
@Tag(name = "Files", description = "View or download files")
@Slf4j
public class FileController {

    @Value("${app.upload.base-dir:uploads}")
    private String baseDir;

    @GetMapping(produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(
            summary = "View or download file by full path",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "File returned successfully",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                    schema = @Schema(type = "string", format = "binary")
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Invalid file path", content = @Content),
                    @ApiResponse(responseCode = "404", description = "File not found", content = @Content),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
            }
    )
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