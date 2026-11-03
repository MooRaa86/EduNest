package com.example.gradproj.EduNest.controller.file;

import com.example.gradproj.EduNest.service.fileSotageService.FileAccessService;
import com.example.gradproj.EduNest.service.storage.SupabaseStorageService;
import com.example.gradproj.EduNest.utils.FileResponseBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/files")
@Tag(
        name = "Files",
        description = "Secure file access"
)
@Slf4j
@RequiredArgsConstructor
public class FileController {

    @Value("${app.upload.base-dir:uploads}")
    private String baseDir;

    private Path resolvedBaseDir;

    private final FileAccessService fileAccessService;
    private final SupabaseStorageService supabaseStorageService;

    @PostConstruct
    public void init() {
        this.resolvedBaseDir = Path.of(baseDir).toAbsolutePath().normalize();
        log.info("Resolved upload directory: {}", resolvedBaseDir);
    }

    @GetMapping(
            value = "/task-submissions/{id}",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    @Operation(
            summary = "View task submission",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "File returned successfully",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                    schema = @Schema(
                                            type = "string",
                                            format = "binary"
                                    )
                            )
                    )
            }
    )
    public ResponseEntity<Resource> serveTaskSubmission(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        String filePath = fileAccessService.authorizeTaskSubmission(id);
        return resolveAndServeFile(filePath, download);
    }

    @GetMapping(
            value = "/tasks/{id}/attachment",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    @Operation(summary = "View task attachment")
    public ResponseEntity<Resource> serveTaskAttachment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        String filePath = fileAccessService.authorizeTask(id);
        return resolveAndServeFile(filePath, download);
    }

    @GetMapping(
            value = "/project-submissions/{id}",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    @Operation(summary = "View project submission")
    public ResponseEntity<Resource> serveProjectSubmission(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        String filePath = fileAccessService.authorizeProjectSubmission(id);
        return resolveAndServeFile(filePath, download);
    }

    @GetMapping(
            value = "/projects/{id}/attachment",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    @Operation(summary = "View project attachment")
    public ResponseEntity<Resource> serveProjectAttachment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        String filePath = fileAccessService.authorizeProject(id);
        return resolveAndServeFile(filePath, download);
    }

    @GetMapping(produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> serveFile(
            @RequestParam String filePath,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        return resolveAndServeFile(fileAccessService.authorizeGeneric(filePath), download);
    }

    private ResponseEntity<Resource> resolveAndServeFile(String filePath, boolean download) {
        try {
            if (filePath == null || filePath.isBlank()) {
                return badRequest();
            }

            if (filePath.contains("\0")) {
                log.warn("Null byte detected in path: {}", filePath);
                return badRequest();
            }

            Path storedPath = Path.of(filePath.replace("\\", "/"));
            Path configuredRoot = Path.of(baseDir.replace("\\", "/")).normalize();
            Path targetPath;
            if (storedPath.isAbsolute()) {
                targetPath = storedPath.normalize();
            } else {
                Path normalizedStoredPath = storedPath.normalize();
                if (!configuredRoot.isAbsolute() && normalizedStoredPath.startsWith(configuredRoot)) {
                    normalizedStoredPath = configuredRoot.relativize(normalizedStoredPath);
                }
                targetPath = resolvedBaseDir.resolve(normalizedStoredPath).normalize();
            }

            log.debug("Original DB path: {}", filePath);
            log.debug("Resolved target path: {}", targetPath);

            if (!targetPath.startsWith(resolvedBaseDir)) {
                log.warn("Path traversal blocked: {}", targetPath);
                return badRequest();
            }

            if (Files.isDirectory(targetPath)) {
                log.warn("Directory access blocked: {}", targetPath);
                return badRequest();
            }

            if (!Files.exists(targetPath) || !Files.isReadable(targetPath)) {
                if (supabaseStorageService.isConfigured()) {
                    String s3Key = SupabaseStorageService.normalizeKey(filePath);
                    log.debug("Attempting to serve file from S3: bucket={}, key={}", supabaseStorageService.getPrivateBucket(), s3Key);
                    try {
                        ResponseInputStream<GetObjectResponse> s3Stream = supabaseStorageService.downloadPrivate(s3Key);
                        GetObjectResponse s3Meta = s3Stream.response();

                        String fileName = Path.of(s3Key).getFileName().toString();
                        String contentType = s3Meta.contentType();
                        if (contentType == null || contentType.isBlank()) {
                            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
                        }

                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.parseMediaType(contentType));
                        if (s3Meta.contentLength() != null) {
                            headers.setContentLength(s3Meta.contentLength());
                        }
                        headers.setContentDisposition(
                                ContentDisposition.builder(download ? "attachment" : "inline")
                                        .filename(fileName)
                                        .build()
                        );

                        return ResponseEntity.ok()
                                .headers(headers)
                                .body(new InputStreamResource(s3Stream));

                    } catch (NoSuchKeyException e) {
                        log.warn("S3 object not found for key: {}", s3Key);
                        return ResponseEntity.notFound().build();
                    } catch (S3Exception e) {
                        if (e.statusCode() == 404) {
                            log.warn("S3 object not found (404) for key: {}", s3Key);
                            return ResponseEntity.notFound().build();
                        }
                        log.error("S3 error serving file: {}", s3Key, e);
                        return ResponseEntity.internalServerError().build();
                    }
                }

                log.warn("File not found or unreadable: {}", targetPath);
                return ResponseEntity.notFound().build();
            }

            Path realRoot = resolvedBaseDir.toRealPath();
            Path realTarget = targetPath.toRealPath();
            if (!realTarget.startsWith(realRoot)) {
                log.warn("File resolves outside upload directory: {}", targetPath);
                return badRequest();
            }

            return FileResponseBuilder.buildResponse(realTarget, download);

        } catch (Exception e) {
            log.error("Error serving file: {}", filePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity<Resource> badRequest() {
        return ResponseEntity.badRequest().build();
    }
}
