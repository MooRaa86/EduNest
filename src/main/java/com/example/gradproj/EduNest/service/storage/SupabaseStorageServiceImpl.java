package com.example.gradproj.EduNest.service.storage;

import com.example.gradproj.EduNest.config.file.UploadPolicy;
import com.example.gradproj.EduNest.config.storage.SupabaseStorageProperties;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/**
 * Implementation of SupabaseStorageService using AWS SDK Java v2 - [ADD-03]/EB-83.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SupabaseStorageServiceImpl implements SupabaseStorageService {

    private final S3Client s3Client;
    private final SupabaseStorageProperties properties;

    @Override
    public boolean isConfigured() {
        return properties.isEnabled()
                && properties.getAccessKey() != null
                && !properties.getAccessKey().isBlank()
                && properties.getSecretKey() != null
                && !properties.getSecretKey().isBlank();
    }

    @Override
    public String getPublicBucket() {
        return properties.getPublicBucket();
    }

    @Override
    public String getPrivateBucket() {
        return properties.getPrivateBucket();
    }

    @Override
    public String uploadPublic(String folder, String filename, byte[] content, String contentType) {
        if (!UploadPolicy.PUBLIC_FOLDERS.contains(folder)) {
            log.warn("Security violation: Folder '{}' is not an allowed public category", folder);
            throw new SecurityException("Folder '" + folder + "' is not permitted in the public bucket");
        }
        if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("\0")) {
            throw new SecurityException("Invalid filename");
        }

        String key = folder + "/" + filename;
        log.info("Uploading public object to S3: bucket={}, key={}, size={}", properties.getPublicBucket(), key, content.length);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.getPublicBucket())
                    .key(key)
                    .contentType(contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream")
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(content));
            return getPublicUrl(folder, filename);
        } catch (S3Exception e) {
            log.error("Failed to upload public file to S3: bucket={}, key={}", properties.getPublicBucket(), key, e);
            throw new globalLogicEx("Failed to upload file to storage.");
        }
    }

    @Override
    public String getPublicUrl(String folder, String filename) {
        String base = properties.getSupabaseUrl();
        if (base == null || base.isBlank()) {
            base = "https://ciogysvcykzxrjuskxmk.supabase.co";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/storage/v1/object/public/" + properties.getPublicBucket() + "/" + folder + "/" + filename;
    }

    @Override
    public void deletePublic(String folder, String filename) {
        if (!UploadPolicy.PUBLIC_FOLDERS.contains(folder)) {
            throw new SecurityException("Folder '" + folder + "' is not permitted in public bucket");
        }
        if (filename == null || filename.isBlank() || filename.contains("..")) {
            throw new SecurityException("Invalid filename");
        }
        String key = folder + "/" + filename;
        deleteObject(properties.getPublicBucket(), key);
    }

    @Override
    public void deletePublicByUrl(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) return;
        String marker = "/object/public/" + properties.getPublicBucket() + "/";
        int idx = publicUrl.indexOf(marker);
        if (idx >= 0) {
            String key = publicUrl.substring(idx + marker.length());
            deleteObject(properties.getPublicBucket(), key);
        } else {
            String key = SupabaseStorageService.normalizeKey(publicUrl);
            if (key != null && key.contains("/")) {
                String folder = key.substring(0, key.indexOf('/'));
                if (UploadPolicy.PUBLIC_FOLDERS.contains(folder)) {
                    deleteObject(properties.getPublicBucket(), key);
                }
            }
        }
    }

    @Override
    public String uploadPrivate(String folder, String filename, byte[] content, String contentType) {
        if (!UploadPolicy.PRIVATE_FOLDERS.contains(folder)) {
            log.warn("Security violation: Folder '{}' is not an allowed private category", folder);
            throw new SecurityException("Folder '" + folder + "' is not permitted in the private bucket");
        }
        if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("\0")) {
            throw new SecurityException("Invalid filename");
        }

        String key = folder + "/" + filename;
        log.info("Uploading private object to S3: bucket={}, key={}, size={}", properties.getPrivateBucket(), key, content.length);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.getPrivateBucket())
                    .key(key)
                    .contentType(contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream")
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(content));
            return key;
        } catch (S3Exception e) {
            log.error("Failed to upload private file to S3: bucket={}, key={}", properties.getPrivateBucket(), key, e);
            throw new globalLogicEx("Failed to upload file to storage.");
        }
    }

    @Override
    public ResponseInputStream<GetObjectResponse> downloadPrivate(String objectKey) {
        String key = SupabaseStorageService.normalizeKey(objectKey);
        validatePrivateAccess(key);

        log.debug("Streaming private object from S3: bucket={}, key={}", properties.getPrivateBucket(), key);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.getPrivateBucket())
                .key(key)
                .build();

        return s3Client.getObject(request);
    }

    @Override
    public void deletePrivate(String objectKey) {
        String key = SupabaseStorageService.normalizeKey(objectKey);
        validatePrivateAccess(key);
        deleteObject(properties.getPrivateBucket(), key);
    }

    @Override
    public void deletePrivate(String folder, String filename) {
        if (!UploadPolicy.PRIVATE_FOLDERS.contains(folder)) {
            throw new SecurityException("Folder '" + folder + "' is not permitted in private bucket");
        }
        if (filename == null || filename.isBlank() || filename.contains("..")) {
            throw new SecurityException("Invalid filename");
        }
        String key = folder + "/" + filename;
        deleteObject(properties.getPrivateBucket(), key);
    }

    private void deleteObject(String bucket, String key) {
        if (key == null || key.isBlank() || key.contains("..") || key.contains("\0")) {
            log.warn("Invalid delete key attempt: {}", key);
            throw new SecurityException("Invalid object key for deletion");
        }
        try {
            log.info("Deleting S3 object: bucket={}, key={}", bucket, key);
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.deleteObject(request);
        } catch (S3Exception e) {
            log.warn("S3 delete failed for bucket={}, key={}: {}", bucket, key, e.getMessage());
        }
    }

    private void validatePrivateAccess(String key) {
        if (key == null || key.isBlank() || key.contains("..") || key.contains("\0")) {
            throw new SecurityException("Invalid object key");
        }
        int slash = key.indexOf('/');
        if (slash <= 0) {
            throw new SecurityException("Object key must contain a valid category folder");
        }
        String folder = key.substring(0, slash);
        if (!UploadPolicy.PRIVATE_FOLDERS.contains(folder)) {
            throw new SecurityException("Folder '" + folder + "' is not a recognized private storage category");
        }
    }
}
