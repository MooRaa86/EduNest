package com.example.gradproj.EduNest.service.mentorShip;

import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;


@Service
public class ImageStorageService {

    private static final String UPLOAD_DIR = "uploads/mentorship-cover-images";
    private static final String BASE_UPLOAD_DIR = "uploads";

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

    /////////////////////////////////////////////
    public String saveImage(String folderName, Long id, MultipartFile file) {
        try {

            Path uploadPath = Paths.get(BASE_UPLOAD_DIR, folderName);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String contentType = file.getContentType();

            if (contentType == null ||
                    !(contentType.equals("image/png")
                            || contentType.equals("image/jpeg")
                            || contentType.equals("image/jpg"))) {

                throw new globalLogicEx("Only PNG and JPG images are allowed");
            }

            String originalName = file.getOriginalFilename();
            if (originalName == null || !originalName.contains(".")) {
                throw new globalLogicEx("Invalid image file");
            }

            if (file.getSize() > 5 * 1024 * 1024) {
                throw new globalLogicEx("Image size must be less than 5MB");
            }

            String extension =
                    originalName.substring(originalName.lastIndexOf("."));

            String fileName =
                    id + "-EduNest-" + UUID.randomUUID() + extension;

            Path filePath = uploadPath.resolve(fileName);

            Files.copy(
                    file.getInputStream(),
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return "/" + BASE_UPLOAD_DIR + "/" + folderName + "/" + fileName;

        } catch (IOException e) {
            throw new globalLogicEx("Failed to store image");
        }
    }

    public void deleteImage(String folderName, String imageUrl) {
        try {

            if (imageUrl != null && !imageUrl.isBlank()) {

                String fileName =
                        Paths.get(imageUrl).getFileName().toString();

                Path filePath =
                        Paths.get(BASE_UPLOAD_DIR, folderName)
                                .resolve(fileName);

                Files.deleteIfExists(filePath);
            }

        } catch (IOException e) {
            throw new globalLogicEx("Failed to delete image");
        }
    }

}