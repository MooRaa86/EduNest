package com.example.gradproj.EduNest.config.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * AWS SDK Java v2 S3Client configuration for Supabase S3 storage - [ADD-03]/EB-83.
 * Configured with path-style addressing and environment-based credentials.
 * Fail-closed in production if credentials are missing.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class SupabaseStorageConfig {

    private final SupabaseStorageProperties properties;
    private final Environment environment;

    @Bean
    public S3Client s3Client() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean isProd = activeProfiles.contains("prod") && !activeProfiles.contains("test");

        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();

        if (isProd && properties.isEnabled()) {
            if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
                throw new IllegalStateException(
                        "FAIL-CLOSED: Supabase S3 credentials (SUPABASE_S3_ACCESS_KEY / SUPABASE_S3_SECRET_KEY) " +
                        "must be provided when storage is enabled in the production profile!"
                );
            }
        }

        String resolvedAccess = (accessKey != null && !accessKey.isBlank()) ? accessKey : "mock-access-key";
        String resolvedSecret = (secretKey != null && !secretKey.isBlank()) ? secretKey : "mock-secret-key";

        log.info("Initializing Supabase S3Client: endpoint={}, region={}, pathStyle=true, enabled={}",
                properties.getEndpoint(), properties.getRegion(), properties.isEnabled());

        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(resolvedAccess, resolvedSecret)
                        )
                )
                .forcePathStyle(true)
                .build();
    }
}
