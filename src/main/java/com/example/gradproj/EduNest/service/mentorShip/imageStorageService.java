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
public class imageStorageService {

    private static final String UPLOAD_DIR = "uploads/mentorship-cover-images";

    public String saveCoverImage(Long mentorshipId, MultipartFile file) {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalName = file.getOriginalFilename();
            if (originalName == null || !originalName.contains(".")) {
                throw new RuntimeException("Invalid image file");
            }

            String extension =
                    originalName.substring(originalName.lastIndexOf("."));

            String fileName =
                    mentorshipId + "-EduNest-" + UUID.randomUUID() + extension;

            Path filePath = uploadPath.resolve(fileName);

            Files.copy(
                    file.getInputStream(),
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return "/uploads/mentorship-cover-images/" + fileName;

        } catch (IOException e) {
            throw new RuntimeException("Failed to store cover image", e);
        }
    }

    public void deleteOldCoverImage(String oldImageUrl) {
        try {
            if (oldImageUrl != null && !oldImageUrl.isBlank()) {

                String fileName =
                        Paths.get(oldImageUrl).getFileName().toString();

                Path oldFilePath =
                        Paths.get(UPLOAD_DIR).resolve(fileName);

                Files.deleteIfExists(oldFilePath);
            }
        } catch (IOException e) {
            System.err.println("Failed to delete old cover image");
        }
    }
}