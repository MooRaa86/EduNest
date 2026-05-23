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

import jakarta.annotation.PostConstruct;
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

    private Path resolvedBaseDir;

    @PostConstruct
    public void init() {
        this.resolvedBaseDir = Paths.get(baseDir).toAbsolutePath().normalize();
        log.info("File serving base directory: {}", resolvedBaseDir);
    }

    @GetMapping(produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(
            summary = "View or download file by relative path",
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
            @RequestParam String filePath,  // Relative path: task-attachment/task-1-1-xxx.pdf
            @RequestParam(defaultValue = "false") boolean download
    ) {
        try {
            // 1. Validate input
            if (filePath == null || filePath.isBlank()) {
                log.warn("Empty file path requested");
                return ResponseEntity.badRequest().build();
            }

            // 2. Prevent null byte injection
            if (filePath.contains("\0")) {
                log.warn("Null byte injection attempt: {}", filePath);
                return ResponseEntity.badRequest().build();
            }

            // 3. Strip any leading "uploads/" or "/" (handle old paths)
            String cleanPath = filePath;
            if (cleanPath.startsWith(baseDir + "/") || cleanPath.startsWith(baseDir + "\\")) {
                cleanPath = cleanPath.substring(baseDir.length() + 1);
            }
            // Also handle absolute paths that happen to contain baseDir
            if (cleanPath.contains(baseDir)) {
                cleanPath = cleanPath.substring(cleanPath.indexOf(baseDir) + baseDir.length());
                cleanPath = cleanPath.replaceFirst("^[\\\\/]", "");
            }
            // Remove leading slash
            cleanPath = cleanPath.replaceFirst("^[\\\\/]", "");

            log.debug("Resolved file path: {} -> {}", filePath, cleanPath);

            // 4. Resolve against base directory (CRITICAL: use baseDir.resolve, NOT Paths.get)
            Path targetPath = resolvedBaseDir.resolve(cleanPath).normalize();

            // 5. Path Traversal Check: ensure resolved path is still within baseDir
            if (!targetPath.startsWith(resolvedBaseDir)) {
                log.warn("Path traversal blocked: {} -> resolved to {}", filePath, targetPath);
                return ResponseEntity.badRequest().build();
            }

            // 6. Prevent directory listing
            if (Files.isDirectory(targetPath)) {
                log.warn("Directory access blocked: {}", targetPath);
                return ResponseEntity.badRequest().build();
            }

            // 7. Load and verify resource
            Resource resource = getResource(targetPath);
            if (!resource.exists() || !resource.isReadable()) {
                log.warn("File not found or not readable: {}", targetPath);
                return ResponseEntity.notFound().build();
            }

            // 8. Determine content type safely
            String contentType = Files.probeContentType(targetPath);
            MediaType mediaType = parseMediaType(contentType);

            // 9. Build response with security headers
            ContentDisposition contentDisposition = download
                    ? ContentDisposition.attachment().filename(resource.getFilename()).build()
                    : ContentDisposition.inline().filename(resource.getFilename()).build();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'")
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("Malformed URL for file: {}", filePath, e);
            return ResponseEntity.badRequest().build();
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