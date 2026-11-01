package com.example.gradproj.EduNest.service.mentorShip;

import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.service.fileSotageService.UploadContentValidator;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;


/**
 * Image saves for covers, avatars and chat-room images.
 *
 * EB-29 [P1-22] hardened: the actual content is proven by the shared
 * {@link UploadContentValidator} (magic bytes + structural checks, per-surface
 * allowlists, size limits) before any byte is written; the stored filename
 * extension is derived from the detected format instead of the client filename
 * (FILE-02/FILE-03/MSH-06). Legitimate uploads keep their URL contract:
 * {@code /uploads/{folder}/{id}-EduNest-{uuid}.{ext}}.
 */
@Slf4j
@Service
public class ImageStorageService {

    /** Applies to covers and avatar surfaces; callers historically enforced 5MB. */
    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final Path baseUploadDir;
    private final UploadContentValidator contentValidator;

    public ImageStorageService(@Value("${app.upload.base-dir:uploads}") String baseUploadDir,
                               UploadContentValidator contentValidator) {
        this.baseUploadDir = Paths.get(baseUploadDir);
        this.contentValidator = contentValidator;
    }

    public String saveCoverImage(Long mentorshipId, MultipartFile file) {
        String folderName = "mentorship-cover-images";
        return store(folderName, mentorshipId, file, UploadContentValidator.IMAGE_FORMATS);
    }

    public void deleteOldCoverImage(String oldImageUrl) {
        try {
            if (oldImageUrl != null && !oldImageUrl.isBlank()) {

                String fileName =
                        Paths.get(oldImageUrl).getFileName().toString();

                Path oldFilePath =
                        baseUploadDir.resolve("mentorship-cover-images").resolve(fileName);

                Files.deleteIfExists(oldFilePath);
            }
        } catch (IOException e) {
            log.warn("Failed to delete old cover image: {}", oldImageUrl, e);
        }
    }

    /////////////////////////////////////////////
    public String saveImage(String folderName, Long id, MultipartFile file) {
        Set<String> allowedFormats = "chatroom".equals(folderName)
                ? UploadContentValidator.CHAT_IMAGE_FORMATS
                : UploadContentValidator.IMAGE_FORMATS;
        return store(folderName, id, file, allowedFormats);
    }

    private String store(String folderName, Long id, MultipartFile file, Set<String> allowedFormats) {
        String detectedExtension = contentValidator.validateImageUpload(
                file, allowedFormats, MAX_IMAGE_BYTES);

        try {
            Path uploadPath = baseUploadDir.resolve(folderName);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileName =
                    id + "-EduNest-" + UUID.randomUUID() + "." + detectedExtension;

            Path filePath = uploadPath.resolve(fileName);

            Files.copy(
                    file.getInputStream(),
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return "/uploads/" + folderName + "/" + fileName;

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
                        baseUploadDir.resolve(folderName)
                                .resolve(fileName);

                Files.deleteIfExists(filePath);
            }

        } catch (IOException e) {
            throw new globalLogicEx("Failed to delete image");
        }
    }

}
