package com.example.gradproj.EduNest.service.fileSotageService;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.upload.base-dir:uploads}")
    private String baseDir;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private static final Map<String, Set<String>> ALLOWED_FILE_TYPES = Map.of(
            "pdf",  Set.of("application/pdf"),
            "docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/x-tika-ooxml"
            ),
            "xlsx", Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/x-tika-ooxml"
            ),

            "pptx", Set.of(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/x-tika-ooxml"
            ),
            "txt", Set.of("text/plain"),

            "jpg", Set.of(
                    "image/jpeg"
            ),

            "jpeg", Set.of(
                    "image/jpeg"
            ),

            "png", Set.of(
                    "image/png"
            ),

            "gif", Set.of(
                    "image/gif"
            ),

            "zip", Set.of(
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream"
            )
    );

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "dll", "bat", "cmd", "sh", "php", "jsp", "asp", "aspx",
            "html", "htm", "js", "jar", "war", "py", "rb", "pl", "cgi"
    );


    private static final Set<String> BLOCKED_MIME_TYPES = Set.of(
            "application/x-msdownload",
            "application/x-executable",
            "application/x-sh",
            "application/x-httpd-php",
            "text/html",
            "application/javascript",
            "application/x-javascript"
    );

    private static final Pattern SAFE_FOLDER_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    private Path resolvedBaseDir;
    private final Tika tika = new Tika();

    @PostConstruct
    public void init() {
        this.resolvedBaseDir = Paths.get(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(resolvedBaseDir);
            log.info("File storage initialized at: {}", resolvedBaseDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create upload directory: " + baseDir, e);
        }
    }

    public String saveFile(String subFolder, String type, Long id, Long secondaryId, MultipartFile file) {
        validateFileStructure(file);
        validateFolderName(subFolder);
        validateTypePrefix(type);

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = extractAndValidateExtension(originalFilename);
        String detectedMimeType = detectMimeType(file);

        validateMimeTypeCompatibility(extension, detectedMimeType);
        validateNotBlockedType(extension, detectedMimeType);

        Path uploadPath = constructSecurePath(subFolder);
        String safeFileName = generateSafeFileName(type, id, secondaryId, extension);
        Path filePath = uploadPath.resolve(safeFileName).normalize();

        if (!filePath.startsWith(uploadPath)) {
            log.warn("Path traversal attempt blocked: {}", filePath);
            throw new SecurityException("Invalid file path detected");
        }

        saveFileAtomically(file, filePath);

        return Paths.get(baseDir)
                .resolve(subFolder)
                .resolve(filePath.getFileName().toString())
                .toString()
                .replace("\\", "/");
    }

    private void validateFileStructure(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty or missing");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    String.format("File size %d bytes exceeds limit of %d bytes (10MB)",
                            file.getSize(), MAX_FILE_SIZE));
        }
    }

    private void validateFolderName(String subFolder) {
        if (subFolder == null || subFolder.isBlank()) {
            throw new IllegalArgumentException("Subfolder cannot be empty");
        }
        if (!SAFE_FOLDER_PATTERN.matcher(subFolder).matches()) {
            throw new SecurityException("Invalid subfolder name: " + subFolder);
        }
    }

    private void validateTypePrefix(String type) {
        if (type == null || type.isBlank() || type.length() > 20) {
            throw new IllegalArgumentException("Invalid type prefix");
        }
        if (!SAFE_FOLDER_PATTERN.matcher(type).matches()) {
            throw new SecurityException("Invalid type prefix: " + type);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename is null");
        }

        if (filename.contains("\0")) {
            log.warn("Null byte injection attempt detected");
            throw new SecurityException("Filename contains invalid characters");
        }

        String basename = Paths.get(filename).getFileName().toString();

        basename = basename.replaceAll("[\\x00-\\x1f\\x7f]", "");

        basename = basename.replaceAll("\\.{2,}", ".");
        basename = basename.replace(" ", "_");
        if (basename.isBlank() || basename.equals(".") || basename.equals("..")) {
            throw new IllegalArgumentException("Invalid filename after sanitization");
        }

        if (!SAFE_FILENAME_PATTERN.matcher(basename).matches()) {
            log.warn("Invalid filename format detected: {}", basename);
            throw new SecurityException("Invalid filename format");
        }

        return basename;
    }

    private String extractAndValidateExtension(String filename) {
        int lastDot = filename.lastIndexOf(".");
        if (lastDot == -1 || lastDot == 0 || lastDot == filename.length() - 1) {
            throw new IllegalArgumentException("Filename must have a valid extension");
        }

        String extension = filename.substring(lastDot + 1).toLowerCase().trim();

        if (BLOCKED_EXTENSIONS.contains(extension)) {
            log.warn("Blocked dangerous extension attempt: {}", extension);
            throw new SecurityException("File type not allowed: ." + extension);
        }

        if (!ALLOWED_FILE_TYPES.containsKey(extension)) {
            throw new IllegalArgumentException(
                    "Extension ." + extension + " not in whitelist. Allowed: " + ALLOWED_FILE_TYPES.keySet());
        }

        return extension;
    }

    private String detectMimeType(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {

            String mimeType = tika.detect(is);

            if (mimeType == null || mimeType.isBlank()) {
                throw new IllegalArgumentException("Could not determine file type from content");
            }

            log.debug("Detected MIME type: {} for file: {}", mimeType, file.getOriginalFilename());
            return mimeType;

        } catch (IOException e) {
            log.error("Failed to read file for MIME detection", e);
            throw new RuntimeException("Failed to validate file content", e);
        }
    }

    private void validateMimeTypeCompatibility(String extension, String detectedMimeType) {

        Set<String> allowedMimes = ALLOWED_FILE_TYPES.get(extension);

        boolean valid = allowedMimes != null &&
                allowedMimes.stream()
                        .anyMatch(detectedMimeType::startsWith);

        if (!valid) {

            log.warn(
                    "MIME mismatch: extension .{} but detected {}. Possible spoofing attempt.",
                    extension,
                    detectedMimeType
            );

            throw new SecurityException(
                    String.format(
                            "File content does not match extension. Expected %s for .%s, got %s",
                            allowedMimes,
                            extension,
                            detectedMimeType
                    )
            );
        }
    }

    private void validateNotBlockedType(String extension, String mimeType) {
        if (BLOCKED_MIME_TYPES.contains(mimeType)) {
            log.warn("Blocked dangerous MIME type detected: {} (extension: {})", mimeType, extension);
            throw new SecurityException("File type blocked for security reasons");
        }
    }

    private Path constructSecurePath(String subFolder) {
        Path uploadPath = resolvedBaseDir.resolve(subFolder).normalize();


        if (!uploadPath.startsWith(resolvedBaseDir)) {
            throw new SecurityException("Subfolder path traversal detected");
        }

        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create upload subdirectory", e);
        }

        return uploadPath;
    }

    private String generateSafeFileName(String type, Long id, Long secondaryId, String extension) {

        return String.format("%s-%d-%d-%s.%s",
                type, id, secondaryId, UUID.randomUUID(), extension);
    }

    private void saveFileAtomically(MultipartFile file, Path targetPath) {
        if (Files.exists(targetPath)) {
            throw new IllegalStateException("File already exists (UUID collision)");
        }

        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");

        try {
            Files.copy(file.getInputStream(), tempPath);

            if (Files.size(tempPath) != file.getSize()) {
                Files.deleteIfExists(tempPath);
                throw new IOException("File size mismatch after write");
            }

            Files.move(tempPath, targetPath);

            try {
                Files.setPosixFilePermissions(targetPath,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException e) {
                log.debug("POSIX permissions not supported on this filesystem");
            }

        } catch (IOException e) {
            try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
            log.error("Failed to save file to {}", targetPath, e);
            throw new RuntimeException("Failed to save file", e);
        }
    }


    public void validateOnly(MultipartFile file) {
        validateFileStructure(file);
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = extractAndValidateExtension(originalFilename);
        String detectedMimeType = detectMimeType(file);
        validateMimeTypeCompatibility(extension, detectedMimeType);
        validateNotBlockedType(extension, detectedMimeType);
    }


    public void deleteFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            log.debug("Delete called with null or blank path, skipping");
            return;
        }
        String cleanPath = relativePath;
        if (cleanPath.startsWith(baseDir + "/") || cleanPath.startsWith(baseDir + "\\")) {
            cleanPath = cleanPath.substring(baseDir.length() + 1);
        }

        Path targetPath;
        try {
            targetPath = resolvedBaseDir.resolve(cleanPath).normalize();
        } catch (Exception e) {
            log.error("Invalid path format for deletion: {}", relativePath);
            throw new SecurityException("Invalid file path format");
        }

        if (!targetPath.startsWith(resolvedBaseDir)) {
            log.warn("Path traversal attempt in deleteFile blocked: {}", relativePath);
            throw new SecurityException("Invalid file path for deletion");
        }

        if (targetPath.equals(resolvedBaseDir)) {
            log.warn("Attempted to delete base directory blocked");
            throw new SecurityException("Cannot delete base directory");
        }

        try {
            boolean deleted = Files.deleteIfExists(targetPath);
            if (deleted) {
                log.info("Deleted file: {}", targetPath);
            } else {
                log.warn("File not found for deletion: {}", targetPath);
            }
        } catch (IOException e) {
            log.error("Failed to delete file: {}", targetPath, e);
            throw new RuntimeException("Failed to delete file: " + relativePath, e);
        }
    }
}