package com.example.gradproj.EduNest.service.mentorShip;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class PdfStorageService {

    private static final String UPLOAD_DIR = "uploads/mentorship-pdf-documents";

    public String savePdfFile(Long mentorshipId, MultipartFile file) {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalName = file.getOriginalFilename();

            if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
                throw new RuntimeException("Invalid PDF file");
            }

            String fileName =
                    mentorshipId + "-EduNest-PDF-" + UUID.randomUUID() + ".pdf";

            Path filePath = uploadPath.resolve(fileName);

            Files.copy(
                    file.getInputStream(),
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return "/uploads/mentorship-pdf-documents/" + fileName;

        } catch (IOException e) {
            throw new RuntimeException("Failed to store PDF file", e);
        }
    }

    public void deleteOldPdf(String oldPdfUrl) {
        try {
            if (oldPdfUrl != null && !oldPdfUrl.isBlank()) {

                String fileName =
                        Paths.get(oldPdfUrl).getFileName().toString();

                Path oldFilePath =
                        Paths.get(UPLOAD_DIR).resolve(fileName);

                Files.deleteIfExists(oldFilePath);
            }
        } catch (IOException e) {
            System.err.println("Failed to delete old PDF file");
        }
    }
}