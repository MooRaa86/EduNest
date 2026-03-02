package com.example.gradproj.EduNest.service.mentorShip;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class PdfStorageService {

    @Value("${app.upload.pdf-dir:uploads/mentorship-pdf-documents}")
    private String uploadDir;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String ALLOWED_MIME_TYPE = "application/pdf";

    public String savePdfFile(Long mentorshipId, MultipartFile file) {
        validateFile(file);

        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String fileName = mentorshipId + "-" + UUID.randomUUID() + ".pdf";
            Path filePath = uploadPath.resolve(fileName).normalize();

            if (!filePath.startsWith(uploadPath)) {
                throw new SecurityException("Invalid file path");
            }

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/mentorship-pdf-documents/" + fileName;

        } catch (IOException e) {
            log.error("Failed to store PDF file for mentorship: {}", mentorshipId, e);
            throw new RuntimeException("Failed to store PDF file", e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 10MB limit");
        }

        String contentType = file.getContentType();
        if (!ALLOWED_MIME_TYPE.equals(contentType)) {
            throw new IllegalArgumentException("Only PDF files are allowed");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Invalid PDF file");
        }
    }

    public void deleteOldPdf(String oldPdfUrl) {
        if (oldPdfUrl == null || oldPdfUrl.isBlank()) {
            return;
        }

        try {
            String fileName = Paths.get(oldPdfUrl).getFileName().toString();
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = uploadPath.resolve(fileName).normalize();

            if (!filePath.startsWith(uploadPath)) {
                log.warn("Attempted path traversal: {}", oldPdfUrl);
                return;
            }

            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to delete PDF file: {}", oldPdfUrl, e);
        }
    }
}