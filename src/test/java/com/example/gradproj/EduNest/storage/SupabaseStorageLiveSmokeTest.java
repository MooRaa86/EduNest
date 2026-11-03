package com.example.gradproj.EduNest.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in Live Supabase Storage (S3) Smoke Test - [ADD-03]/EB-83.
 *
 * SAFETY GUARD:
 * - Disabled by default. Enabled ONLY when explicitly requested with:
 *   -Dedunest.storage.live.test=true
 * - Operates ONLY on test-generated objects prefixed with "smoke-live-".
 * - Deletes ONLY its own generated test objects during cleanup.
 */
@DisplayName("EB-83: Live Supabase Storage Smoke Test (Opt-In)")
@EnabledIfSystemProperty(named = "edunest.storage.live.test", matches = "true")
class SupabaseStorageLiveSmokeTest {

    private S3Client s3Client;
    private String endpoint;
    private String region;
    private String publicBucket;
    private String privateBucket;
    private String supabaseUrl;

    private final List<String> createdPublicKeys = new ArrayList<>();
    private final List<String> createdPrivateKeys = new ArrayList<>();

    private static final byte[] VALID_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89,
            0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
            0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
            0x0D, 0x0A, 0x2D, (byte) 0xB4,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    private static final byte[] VALID_PDF = "%PDF-1.4\n1 0 obj\n<<\n>>\nendobj\ntrailer\n<<\n>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @BeforeEach
    void setUp() {
        String accessKey = System.getenv("SUPABASE_S3_ACCESS_KEY");
        String secretKey = System.getenv("SUPABASE_S3_SECRET_KEY");

        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Missing live credentials: SUPABASE_S3_ACCESS_KEY and SUPABASE_S3_SECRET_KEY must be set in environment!");
        }

        endpoint = System.getenv().getOrDefault("SUPABASE_S3_ENDPOINT", "https://ciogysvcykzxrjuskxmk.storage.supabase.co/storage/v1/s3");
        region = System.getenv().getOrDefault("SUPABASE_S3_REGION", "eu-west-1");
        publicBucket = System.getenv().getOrDefault("SUPABASE_STORAGE_PUBLIC_BUCKET", "edunest-public");
        privateBucket = System.getenv().getOrDefault("SUPABASE_STORAGE_PRIVATE_BUCKET", "edunest-private");
        supabaseUrl = System.getenv().getOrDefault("SUPABASE_URL", "https://ciogysvcykzxrjuskxmk.supabase.co");

        s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        // Strict cleanup of ONLY the test's own generated objects
        for (String key : createdPublicKeys) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder().bucket(publicBucket).key(key).build());
            } catch (Exception ignored) {}
        }
        for (String key : createdPrivateKeys) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder().bucket(privateBucket).key(key).build());
            } catch (Exception ignored) {}
        }
        if (s3Client != null) {
            s3Client.close();
        }
    }

    @Test
    @DisplayName("Live Public Object: upload, CDN read, verify byte equality, and cleanup")
    void testLivePublicUploadAndRead() throws IOException, InterruptedException {
        String key = "mentorship-cover-images/smoke-live-" + UUID.randomUUID() + ".png";
        createdPublicKeys.add(key);

        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(publicBucket)
                .key(key)
                .contentType("image/png")
                .build();
        s3Client.putObject(putReq, RequestBody.fromBytes(VALID_PNG));

        // Construct public CDN URL
        String publicUrl = supabaseUrl.replaceAll("/+$", "") + "/storage/v1/object/public/" + publicBucket + "/" + key;

        // Perform anonymous HTTP GET to verify public CDN exposure
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(publicUrl)).GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(VALID_PNG);
    }

    @Test
    @DisplayName("Live Private Object: upload, authenticated S3 read, verify byte equality, and cleanup")
    void testLivePrivateUploadAndAuthenticatedRead() throws IOException {
        String key = "submissions/smoke-live-" + UUID.randomUUID() + ".pdf";
        createdPrivateKeys.add(key);

        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(privateBucket)
                .key(key)
                .contentType("application/pdf")
                .build();
        s3Client.putObject(putReq, RequestBody.fromBytes(VALID_PDF));

        // Read back via authenticated S3 client
        GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(privateBucket)
                .key(key)
                .build();
        try (ResponseInputStream<GetObjectResponse> is = s3Client.getObject(getReq)) {
            byte[] fetched = is.readAllBytes();
            assertThat(fetched).isEqualTo(VALID_PDF);
            assertThat(is.response().contentType()).isEqualTo("application/pdf");
        }
    }

    @Test
    @DisplayName("Live Private Object: anonymous public CDN access is rejected (400/403/404)")
    void testLivePrivateAnonymousAccessRejected() throws IOException, InterruptedException {
        String key = "submissions/smoke-live-" + UUID.randomUUID() + ".pdf";
        createdPrivateKeys.add(key);

        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(privateBucket)
                .key(key)
                .contentType("application/pdf")
                .build();
        s3Client.putObject(putReq, RequestBody.fromBytes(VALID_PDF));

        // Attempt anonymous HTTP GET to private bucket URL
        String privateUrl = supabaseUrl.replaceAll("/+$", "") + "/storage/v1/object/public/" + privateBucket + "/" + key;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(privateUrl)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Must NOT be 200 OK
        assertThat(response.statusCode()).isIn(400, 403, 404);
    }
}
