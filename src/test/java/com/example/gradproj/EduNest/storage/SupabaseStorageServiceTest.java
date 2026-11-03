package com.example.gradproj.EduNest.storage;

import com.example.gradproj.EduNest.config.storage.SupabaseStorageConfig;
import com.example.gradproj.EduNest.config.storage.SupabaseStorageProperties;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.service.storage.SupabaseStorageService;
import com.example.gradproj.EduNest.service.storage.SupabaseStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EB-83: Supabase Storage Service Unit Tests")
class SupabaseStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private SupabaseStorageProperties properties;
    private SupabaseStorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        properties = new SupabaseStorageProperties();
        properties.setEndpoint("https://ciogysvcykzxrjuskxmk.storage.supabase.co/storage/v1/s3");
        properties.setRegion("eu-west-1");
        properties.setAccessKey("test-access-key");
        properties.setSecretKey("test-secret-key");
        properties.setPublicBucket("edunest-public");
        properties.setPrivateBucket("edunest-private");
        properties.setEnabled(true);
        properties.setSupabaseUrl("https://ciogysvcykzxrjuskxmk.supabase.co");

        storageService = new SupabaseStorageServiceImpl(s3Client, properties);
    }

    @Test
    @DisplayName("Configuration status is true when enabled with valid credentials")
    void testIsConfigured_True() {
        assertThat(storageService.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("Configuration status is false when disabled or missing credentials")
    void testIsConfigured_False() {
        properties.setEnabled(false);
        assertThat(storageService.isConfigured()).isFalse();

        properties.setEnabled(true);
        properties.setAccessKey("");
        assertThat(storageService.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("Upload public asset routes to edunest-public bucket and returns public CDN URL")
    void testUploadPublic_Success() {
        byte[] content = "test image bytes".getBytes(StandardCharsets.UTF_8);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String url = storageService.uploadPublic("mentorship-cover-images", "cover-1.jpg", content, "image/jpeg");

        assertThat(url).isEqualTo("https://ciogysvcykzxrjuskxmk.supabase.co/storage/v1/object/public/edunest-public/mentorship-cover-images/cover-1.jpg");

        ArgumentCaptor<PutObjectRequest> reqCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(reqCaptor.capture(), any(RequestBody.class));
        PutObjectRequest captured = reqCaptor.getValue();
        assertThat(captured.bucket()).isEqualTo("edunest-public");
        assertThat(captured.key()).isEqualTo("mentorship-cover-images/cover-1.jpg");
        assertThat(captured.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("Upload public asset rejects private folder category")
    void testUploadPublic_RejectsPrivateFolder() {
        byte[] content = "test bytes".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storageService.uploadPublic("submissions", "sub-1.pdf", content, "application/pdf"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not permitted in the public bucket");

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Upload public asset rejects path traversal in filename")
    void testUploadPublic_RejectsPathTraversal() {
        byte[] content = "test bytes".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storageService.uploadPublic("mentorship-cover-images", "../evil.jpg", content, "image/jpeg"))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Upload private file routes to edunest-private bucket and returns object key")
    void testUploadPrivate_Success() {
        byte[] content = "test pdf bytes".getBytes(StandardCharsets.UTF_8);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = storageService.uploadPrivate("submissions", "task-sub-123.pdf", content, "application/pdf");

        assertThat(key).isEqualTo("submissions/task-sub-123.pdf");

        ArgumentCaptor<PutObjectRequest> reqCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(reqCaptor.capture(), any(RequestBody.class));
        PutObjectRequest captured = reqCaptor.getValue();
        assertThat(captured.bucket()).isEqualTo("edunest-private");
        assertThat(captured.key()).isEqualTo("submissions/task-sub-123.pdf");
        assertThat(captured.contentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("Upload private file rejects public folder category")
    void testUploadPrivate_RejectsPublicFolder() {
        byte[] content = "test bytes".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storageService.uploadPrivate("student", "avatar.jpg", content, "image/jpeg"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not permitted in the private bucket");

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Download private file streams from edunest-private bucket with proper key normalization")
    void testDownloadPrivate_Success() throws IOException {
        GetObjectResponse meta = GetObjectResponse.builder()
                .contentType("application/pdf")
                .contentLength(1024L)
                .build();
        ByteArrayInputStream bais = new ByteArrayInputStream("stream content".getBytes(StandardCharsets.UTF_8));
        ResponseInputStream<GetObjectResponse> mockStream = new ResponseInputStream<>(meta, AbortableInputStream.create(bais));

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(mockStream);

        // Test with path containing leading slash and uploads/ prefix
        ResponseInputStream<GetObjectResponse> resultStream = storageService.downloadPrivate("/uploads/submissions/task-sub-123.pdf");

        assertThat(resultStream).isNotNull();
        assertThat(resultStream.response().contentType()).isEqualTo("application/pdf");
        assertThat(resultStream.response().contentLength()).isEqualTo(1024L);

        ArgumentCaptor<GetObjectRequest> reqCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(reqCaptor.capture());
        assertThat(reqCaptor.getValue().bucket()).isEqualTo("edunest-private");
        assertThat(reqCaptor.getValue().key()).isEqualTo("submissions/task-sub-123.pdf");

        resultStream.close();
    }

    @Test
    @DisplayName("Download private file rejects non-private folder or path traversal")
    void testDownloadPrivate_Rejection() {
        assertThatThrownBy(() -> storageService.downloadPrivate("student/avatar.jpg"))
                .isInstanceOf(SecurityException.class);

        assertThatThrownBy(() -> storageService.downloadPrivate("submissions/../secret.txt"))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Delete private file invokes S3 delete on edunest-private bucket")
    void testDeletePrivate_Success() {
        storageService.deletePrivate("submissions/task-sub-123.pdf");

        ArgumentCaptor<DeleteObjectRequest> reqCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(reqCaptor.capture());
        assertThat(reqCaptor.getValue().bucket()).isEqualTo("edunest-private");
        assertThat(reqCaptor.getValue().key()).isEqualTo("submissions/task-sub-123.pdf");
    }

    @Test
    @DisplayName("Delete public by URL parses public bucket key and deletes from edunest-public")
    void testDeletePublicByUrl_Success() {
        String publicUrl = "https://ciogysvcykzxrjuskxmk.supabase.co/storage/v1/object/public/edunest-public/mentor/avatar-42.jpg";
        storageService.deletePublicByUrl(publicUrl);

        ArgumentCaptor<DeleteObjectRequest> reqCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(reqCaptor.capture());
        assertThat(reqCaptor.getValue().bucket()).isEqualTo("edunest-public");
        assertThat(reqCaptor.getValue().key()).isEqualTo("mentor/avatar-42.jpg");
    }

    @Test
    @DisplayName("S3Exception during upload is converted to user-safe globalLogicEx")
    void testUpload_S3ExceptionHandled() {
        byte[] content = "test bytes".getBytes(StandardCharsets.UTF_8);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("Access Denied").statusCode(403).build());

        assertThatThrownBy(() -> storageService.uploadPrivate("submissions", "sub.pdf", content, "application/pdf"))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("Failed to upload file to storage");
    }

    @Test
    @DisplayName("Fail-closed: production configuration without S3 credentials throws IllegalStateException")
    void testFailClosed_ProductionStartup() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        SupabaseStorageProperties prodProps = new SupabaseStorageProperties();
        prodProps.setEnabled(true);
        prodProps.setAccessKey("");
        prodProps.setSecretKey("");

        SupabaseStorageConfig config = new SupabaseStorageConfig(prodProps, prodEnv);

        assertThatThrownBy(config::s3Client)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAIL-CLOSED");
    }

    @Test
    @DisplayName("Key normalization helper safely cleans paths")
    void testNormalizeKey() {
        assertThat(SupabaseStorageService.normalizeKey("submissions/task.pdf")).isEqualTo("submissions/task.pdf");
        assertThat(SupabaseStorageService.normalizeKey("/submissions/task.pdf")).isEqualTo("submissions/task.pdf");
        assertThat(SupabaseStorageService.normalizeKey("uploads/submissions/task.pdf")).isEqualTo("submissions/task.pdf");
        assertThat(SupabaseStorageService.normalizeKey("/uploads/submissions/task.pdf")).isEqualTo("submissions/task.pdf");
        assertThat(SupabaseStorageService.normalizeKey("uploads\\task-attachment\\file.docx")).isEqualTo("task-attachment/file.docx");
        assertThat(SupabaseStorageService.normalizeKey(null)).isNull();
    }
}
