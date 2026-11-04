package com.example.gradproj.EduNest.service.mentorShip;

import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.service.fileSotageService.UploadContentValidator;
import com.example.gradproj.EduNest.service.fileSotageService.TransactionFileActions;
import com.example.gradproj.EduNest.service.storage.SupabaseStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Image saves for covers, avatars and chat-room images - [ADD-03]/EB-83.
 *
 * EB-29 [P1-22] hardened: validated via {@link UploadContentValidator}.
 * Supports Supabase S3 public storage bucket (edunest-public) with fail-closed production protection
 * and automated transaction rollback cleanup.
 */
@Slf4j
@Service
public class ImageStorageService {

    /** Applies to covers and avatar surfaces; callers historically enforced 5MB. */
    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final Path baseUploadDir;
    private final UploadContentValidator contentValidator;
    private final SupabaseStorageService supabaseStorageService;
    private final Environment environment;

    public ImageStorageService(@Value("${app.upload.base-dir:uploads}") String baseUploadDir,
                               UploadContentValidator contentValidator,
                               SupabaseStorageService supabaseStorageService,
                               Environment environment) {
        this.baseUploadDir = Paths.get(baseUploadDir);
        this.contentValidator = contentValidator;
        this.supabaseStorageService = supabaseStorageService;
        this.environment = environment;
    }

    private boolean isProduction() {
        if (environment == null) return false;
        List<String> active = Arrays.asList(environment.getActiveProfiles());
        return active.contains("prod") && !active.contains("test");
    }

    public String saveCoverImage(Long mentorshipId, MultipartFile file) {
        String folderName = "mentorship-cover-images";
        return store(folderName, mentorshipId, file, UploadContentValidator.IMAGE_FORMATS);
    }

    public void deleteOldCoverImage(String oldImageUrl) {
        if (oldImageUrl == null || oldImageUrl.isBlank()) {
            return;
        }
        TransactionFileActions.deleteAfterCommit(() -> deleteOldCoverImageNow(oldImageUrl));
    }

    private void deleteOldCoverImageNow(String oldImageUrl) {
        if (supabaseStorageService.isConfigured() && (oldImageUrl.startsWith("http://") || oldImageUrl.startsWith("https://"))) {
            supabaseStorageService.deletePublicByUrl(oldImageUrl);
            return;
        }
        try {
            String fileName = Paths.get(oldImageUrl).getFileName().toString();
            Path oldFilePath = baseUploadDir.resolve("mentorship-cover-images").resolve(fileName);
            Files.deleteIfExists(oldFilePath);
        } catch (IOException e) {
            log.warn("Failed to delete old cover image: {}", oldImageUrl, e);
        }
    }

    public String saveImage(String folderName, Long id, MultipartFile file) {
        Set<String> allowedFormats = "chatroom".equals(folderName)
                ? UploadContentValidator.CHAT_IMAGE_FORMATS
                : UploadContentValidator.IMAGE_FORMATS;
        return store(folderName, id, file, allowedFormats);
    }

    private String store(String folderName, Long id, MultipartFile file, Set<String> allowedFormats) {
        String detectedExtension = contentValidator.validateImageUpload(
                file, allowedFormats, MAX_IMAGE_BYTES);

        String fileName = id + "-EduNest-" + UUID.randomUUID() + "." + detectedExtension;

        // Fail-closed guard: production must never silently fall back to local disk
        if (isProduction() && !supabaseStorageService.isConfigured()) {
            log.error("FAIL-CLOSED: Production image upload blocked - Supabase S3 storage is not configured!");
            throw new IllegalStateException("Production image storage is unconfigured. Local storage fallback is prohibited in production.");
        }

        if (supabaseStorageService.isConfigured()) {
            try {
                String contentType = switch (detectedExtension.toLowerCase()) {
                    case "png" -> "image/png";
                    case "webp" -> "image/webp";
                    default -> "image/jpeg";
                };
                String publicUrl = supabaseStorageService.uploadPublic(folderName, fileName, file.getBytes(), contentType);

                // Register compensating transaction rollback hook to delete orphaned S3 image if DB tx rolls back
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status == STATUS_ROLLED_BACK) {
                                log.warn("Transaction rolled back - executing compensating cleanup of uploaded S3 image: {}", publicUrl);
                                try {
                                    supabaseStorageService.deletePublic(folderName, fileName);
                                } catch (Exception ex) {
                                    log.error("Failed to clean up orphaned S3 image after rollback: {}", publicUrl, ex);
                                }
                            }
                        }
                    });
                }

                return publicUrl;
            } catch (IOException e) {
                log.error("Failed to read image bytes for S3 upload: {}", fileName, e);
                throw new globalLogicEx("Failed to store image");
            }
        }

        try {
            Path uploadPath = baseUploadDir.resolve(folderName);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(fileName);
            Files.copy(
                    file.getInputStream(),
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            TransactionFileActions.deleteOnRollback(() -> {
                try {
                    Files.deleteIfExists(filePath);
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to clean up rolled-back image", ex);
                }
            });

            return "/uploads/" + folderName + "/" + fileName;

        } catch (IOException e) {
            throw new globalLogicEx("Failed to store image");
        }
    }

    public void deleteImage(String folderName, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        TransactionFileActions.deleteAfterCommit(() -> deleteImageNow(folderName, imageUrl));
    }

    private void deleteImageNow(String folderName, String imageUrl) {
        if (supabaseStorageService.isConfigured() && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
            supabaseStorageService.deletePublicByUrl(imageUrl);
            return;
        }
        try {
            String fileName = Paths.get(imageUrl).getFileName().toString();
            Path filePath = baseUploadDir.resolve(folderName).resolve(fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new globalLogicEx("Failed to delete image");
        }
    }
}
