package com.example.gradproj.EduNest.service.storage;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * Centralized Supabase Storage (S3) service interface - [ADD-03]/EB-83.
 */
public interface SupabaseStorageService {

    /**
     * Checks if S3 storage is enabled and valid credentials are configured.
     */
    boolean isConfigured();

    /**
     * Gets public bucket name (edunest-public).
     */
    String getPublicBucket();

    /**
     * Gets private bucket name (edunest-private).
     */
    String getPrivateBucket();

    /**
     * Uploads a public asset (mentorship covers, student/mentor/admin avatars, chatroom images).
     *
     * @param folder category folder (must belong to UploadPolicy.PUBLIC_FOLDERS)
     * @param filename sanitized filename
     * @param content file bytes
     * @param contentType MIME type
     * @return full public URL to fetch the object directly
     */
    String uploadPublic(String folder, String filename, byte[] content, String contentType);

    /**
     * Constructs the public CDN URL for an object stored in the public bucket.
     */
    String getPublicUrl(String folder, String filename);

    /**
     * Deletes a public asset by folder and filename.
     */
    void deletePublic(String folder, String filename);

    /**
     * Deletes a public asset by full public URL or relative path.
     */
    void deletePublicByUrl(String publicUrl);

    /**
     * Uploads a private attachment or submission file into the private bucket.
     *
     * @param folder category folder (must belong to UploadPolicy.PRIVATE_FOLDERS)
     * @param filename sanitized filename
     * @param content file bytes
     * @param contentType MIME type
     * @return normalized object key in private bucket
     */
    String uploadPrivate(String folder, String filename, byte[] content, String contentType);

    /**
     * Streams a private object directly from S3.
     * The caller MUST close the returned stream.
     *
     * @param objectKey object key or stored path
     * @return S3 ResponseInputStream
     */
    ResponseInputStream<GetObjectResponse> downloadPrivate(String objectKey);

    /**
     * Deletes a private object by its object key.
     */
    void deletePrivate(String objectKey);

    /**
     * Deletes a private object by folder and filename.
     */
    void deletePrivate(String folder, String filename);

    /**
     * Safely normalizes path or key string to S3 object key (strips leading slashes, "uploads/", etc.).
     */
    static String normalizeKey(String path) {
        if (path == null) return null;
        String clean = path.replace('\\', '/').trim();
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        if (clean.startsWith("uploads/")) {
            clean = clean.substring("uploads/".length());
        }
        return clean;
    }
}
