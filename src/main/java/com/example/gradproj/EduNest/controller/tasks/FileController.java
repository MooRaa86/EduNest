package com.example.gradproj.EduNest.controller.tasks;

import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.service.file.FileAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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

    @PostConstruct
    public void init() {

        this.resolvedBaseDir =
                Path.of(baseDir)
                        .toAbsolutePath()
                        .normalize();

        log.info(
                "Resolved upload directory: {}",
                resolvedBaseDir
        );
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
            @RequestParam(defaultValue = "false")
            boolean download
    ) {

        TaskSubmission submission =
                fileAccessService
                        .authorizeTaskSubmission(id);

        return resolveAndServeFile(
                submission.getUploadedFilePath(),
                download
        );
    }

    @GetMapping(
            value = "/tasks/{id}/attachment",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    @Operation(summary = "View task attachment")
    public ResponseEntity<Resource> serveTaskAttachment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false")
            boolean download
    ) {

        Task task =
                fileAccessService
                        .authorizeTask(id);

        return resolveAndServeFile(
                task.getUploadedAttachmentPath(),
                download
        );
    }

    @GetMapping(
            value = "/project-submissions/{id}",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    @Operation(summary = "View project submission")
    public ResponseEntity<Resource> serveProjectSubmission(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false")
            boolean download
    ) {

        ProjectSubmission submission =
                fileAccessService
                        .authorizeProjectSubmission(id);

        return resolveAndServeFile(
                submission.getUploadedFilePath(),
                download
        );
    }

    @GetMapping(
            value = "/projects/{id}/attachment",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    @Operation(summary = "View project attachment")
    public ResponseEntity<Resource> serveProjectAttachment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false")
            boolean download
    ) {

        Project project =
                fileAccessService
                        .authorizeProject(id);

        return resolveAndServeFile(
                project.getUploadedAttachmentPath(),
                download
        );
    }

    private ResponseEntity<Resource> resolveAndServeFile(
            String filePath,
            boolean download
    ) {

        try {

            if (filePath == null
                    || filePath.isBlank()) {

                return badRequest();
            }

            if (filePath.contains("\0")) {

                log.warn(
                        "Null byte detected in path: {}",
                        filePath
                );

                return badRequest();
            }

            String cleanPath =
                    filePath.replace("\\", "/")
                            .replaceFirst(
                                    "^[\\\\/]+",
                                    ""
                            );

            /*
             * Prevent uploads/uploads duplication
             */
            if (cleanPath.startsWith(baseDir + "/")) {

                cleanPath =
                        cleanPath.substring(
                                baseDir.length() + 1
                        );
            }

            Path targetPath =
                    resolvedBaseDir
                            .resolve(cleanPath)
                            .normalize();

            log.info(
                    "Original DB path: {}",
                    filePath
            );

            log.info(
                    "Resolved target path: {}",
                    targetPath
            );

            log.info(
                    "File exists: {}",
                    Files.exists(targetPath)
            );

            if (!targetPath.startsWith(
                    resolvedBaseDir
            )) {

                log.warn(
                        "Path traversal blocked: {}",
                        targetPath
                );

                return badRequest();
            }

            if (Files.isDirectory(targetPath)) {

                log.warn(
                        "Directory access blocked: {}",
                        targetPath
                );

                return badRequest();
            }

            Resource resource =
                    new UrlResource(
                            targetPath.toUri()
                    );

            if (!resource.exists()
                    || !resource.isReadable()) {

                log.warn(
                        "File not found or unreadable: {}",
                        targetPath
                );

                return ResponseEntity
                        .notFound()
                        .build();
            }

            String contentType =
                    Files.probeContentType(
                            targetPath
                    );

            MediaType mediaType =
                    parseMediaType(contentType);

            boolean safeInline =
                    mediaType.getType()
                            .equalsIgnoreCase("image")
                            || mediaType.equals(
                            MediaType.APPLICATION_PDF
                    )
                            || mediaType.equals(
                            MediaType.TEXT_PLAIN
                    );

            ContentDisposition disposition =
                    (!download && safeInline)
                            ? ContentDisposition
                              .inline()
                              .filename(
                                      resource.getFilename()
                              )
                              .build()
                            : ContentDisposition
                              .attachment()
                              .filename(
                                      resource.getFilename()
                              )
                              .build();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            disposition.toString()
                    )
                    .header(
                            HttpHeaders.CACHE_CONTROL,
                            "no-store"
                    )
                    .header(
                            "X-Content-Type-Options",
                            "nosniff"
                    )
                    .header(
                            "Content-Security-Policy",
                            "default-src 'none'"
                    )
                    .body(resource);

        } catch (MalformedURLException e) {

            log.error(
                    "Malformed file URL: {}",
                    filePath,
                    e
            );

            return badRequest();

        } catch (Exception e) {

            log.error(
                    "Error serving file: {}",
                    filePath,
                    e
            );

            return ResponseEntity
                    .internalServerError()
                    .build();
        }
    }

    private MediaType parseMediaType(
            String contentType
    ) {

        try {

            return contentType != null
                    ? MediaType.parseMediaType(
                    contentType
            )
                    : MediaType.APPLICATION_OCTET_STREAM;

        } catch (Exception e) {

            return MediaType
                    .APPLICATION_OCTET_STREAM;
        }
    }

    private ResponseEntity<Resource> badRequest() {

        return ResponseEntity
                .badRequest()
                .build();
    }
}