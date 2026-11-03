package com.example.gradproj.EduNest.config.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Supabase Storage (S3) configuration properties - [ADD-03]/EB-83.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "supabase.storage")
public class SupabaseStorageProperties {

    /** Supabase S3 endpoint URL (path-style). */
    private String endpoint = "https://ciogysvcykzxrjuskxmk.storage.supabase.co/storage/v1/s3";

    /** AWS / Supabase Region. */
    private String region = "eu-west-1";

    /** S3 Access Key ID (backend-only). */
    private String accessKey;

    /** S3 Secret Access Key (backend-only). */
    private String secretKey;

    /** Public assets bucket name (covers, avatars, chatroom images). */
    private String publicBucket = "edunest-public";

    /** Private files bucket name (submissions, task/project attachments). */
    private String privateBucket = "edunest-private";

    /** Whether S3 storage operations are enabled. */
    private boolean enabled = false;

    /** Supabase project public URL for CDN asset fetching. */
    private String supabaseUrl = "https://ciogysvcykzxrjuskxmk.supabase.co";
}
