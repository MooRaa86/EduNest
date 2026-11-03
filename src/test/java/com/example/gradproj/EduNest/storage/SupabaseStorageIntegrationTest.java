package com.example.gradproj.EduNest.storage;

import com.example.gradproj.EduNest.config.storage.SupabaseStorageProperties;
import com.example.gradproj.EduNest.controller.file.FileController;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.service.fileSotageService.FileAccessService;
import com.example.gradproj.EduNest.service.fileSotageService.FileStorageService;
import com.example.gradproj.EduNest.service.fileSotageService.UploadContentValidator;
import com.example.gradproj.EduNest.service.mentorShip.ImageStorageService;
import com.example.gradproj.EduNest.service.storage.SupabaseStorageService;
import com.example.gradproj.EduNest.service.storage.SupabaseStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("EB-83: Supabase Storage Integration & Contract Tests")
class SupabaseStorageIntegrationTest {

    private S3Client s3Client;
    private SupabaseStorageProperties properties;
    private SupabaseStorageService supabaseStorageService;
    private UploadContentValidator contentValidator;
    private FileStorageService fileStorageService;
    private ImageStorageService imageStorageService;
    private FileAccessService fileAccessService;
    private FileController fileController;

    @TempDir
    Path tempUploadDir;

    // Standard valid 1x1 PNG fixture (EB-29 compliant)
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

    // Valid minimal PDF fixture
    private static final byte[] VALID_PDF = "%PDF-1.4\n1 0 obj\n<<\n>>\nendobj\ntrailer\n<<\n>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);

        properties = new SupabaseStorageProperties();
        properties.setEndpoint("https://ciogysvcykzxrjuskxmk.storage.supabase.co/storage/v1/s3");
        properties.setRegion("eu-west-1");
        properties.setAccessKey("test-key");
        properties.setSecretKey("test-secret");
        properties.setPublicBucket("edunest-public");
        properties.setPrivateBucket("edunest-private");
        properties.setEnabled(true);
        properties.setSupabaseUrl("https://ciogysvcykzxrjuskxmk.supabase.co");

        supabaseStorageService = new SupabaseStorageServiceImpl(s3Client, properties);
        contentValidator = new UploadContentValidator();

        fileStorageService = new FileStorageService(contentValidator, supabaseStorageService);
        // Set baseDir to tempUploadDir for isolation
        org.springframework.test.util.ReflectionTestUtils.setField(fileStorageService, "baseDir", tempUploadDir.toString());
        fileStorageService.init();

        imageStorageService = new ImageStorageService(tempUploadDir.toString(), contentValidator, supabaseStorageService);

        fileAccessService = mock(FileAccessService.class);
        fileController = new FileController(fileAccessService, supabaseStorageService);
        org.springframework.test.util.ReflectionTestUtils.setField(fileController, "baseDir", tempUploadDir.toString());
        fileController.init();
    }

    @Test
    @DisplayName("ImageStorageService saves cover image to S3 public bucket when enabled")
    void testImageStorageService_S3PublicUpload() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", "image/png", VALID_PNG
        );

        String resultUrl = imageStorageService.saveCoverImage(10L, file);

        assertThat(resultUrl).startsWith("https://ciogysvcykzxrjuskxmk.supabase.co/storage/v1/object/public/edunest-public/mentorship-cover-images/10-EduNest-");
        assertThat(resultUrl).endsWith(".png");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("edunest-public");
        assertThat(captor.getValue().key()).startsWith("mentorship-cover-images/10-EduNest-");
    }

    @Test
    @DisplayName("ImageStorageService deletes old cover image from S3 public bucket when URL is provided")
    void testImageStorageService_DeleteOldPublicImage() {
        String publicUrl = "https://ciogysvcykzxrjuskxmk.supabase.co/storage/v1/object/public/edunest-public/mentorship-cover-images/10-EduNest-uuid.png";
        imageStorageService.deleteOldCoverImage(publicUrl);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("edunest-public");
        assertThat(captor.getValue().key()).isEqualTo("mentorship-cover-images/10-EduNest-uuid.png");
    }

    @Test
    @DisplayName("FileStorageService saves PDF submission to S3 private bucket when enabled")
    void testFileStorageService_S3PrivateUpload() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        MockMultipartFile file = new MockMultipartFile(
                "file", "submission.pdf", "application/pdf", VALID_PDF
        );

        String storedPath = fileStorageService.saveFile("submissions", "task-submission", 1L, 2L, file);

        assertThat(storedPath).contains("submissions/task-submission-1-2-");
        assertThat(storedPath).endsWith(".pdf");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("edunest-private");
        assertThat(captor.getValue().key()).startsWith("submissions/task-submission-1-2-");
    }

    @Test
    @DisplayName("FileStorageService verifies ZIP integrity before S3 upload and uploads valid ZIP")
    void testFileStorageService_S3ZipUpload() throws IOException {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("code.txt");
            zos.putNextEntry(entry);
            zos.write("System.out.println(\"hello\");".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] validZipBytes = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
                "file", "archive.zip", "application/zip", validZipBytes
        );

        String storedPath = fileStorageService.saveFile("submissions", "project-submission", 5L, 6L, file);

        assertThat(storedPath).contains("submissions/project-submission-5-6-");
        assertThat(storedPath).endsWith(".zip");

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("FileStorageService rejects corrupt ZIP before S3 upload")
    void testFileStorageService_RejectsCorruptZip() {
        // Corrupt ZIP with just PK header
        byte[] corruptZip = new byte[]{'P', 'K', 0x03, 0x04, 0x00, 0x00, 0x00, 0x00};

        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.zip", "application/zip", corruptZip
        );

        assertThatThrownBy(() -> fileStorageService.saveFile("submissions", "project-submission", 5L, 6L, file))
                .isInstanceOf(globalLogicEx.class);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("FileController streams private file from S3 when local file does not exist")
    void testFileController_ServeFromS3() {
        String dbPath = "uploads/submissions/task-sub-1-2.pdf";
        when(fileAccessService.authorizeTaskSubmission(100L)).thenReturn(dbPath);

        GetObjectResponse meta = GetObjectResponse.builder()
                .contentType("application/pdf")
                .contentLength(100L)
                .build();
        ByteArrayInputStream bais = new ByteArrayInputStream("dummy pdf stream".getBytes(StandardCharsets.UTF_8));
        ResponseInputStream<GetObjectResponse> s3Stream = new ResponseInputStream<>(meta, AbortableInputStream.create(bais));

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(s3Stream);

        ResponseEntity<Resource> response = fileController.serveTaskSubmission(100L, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(100L);
        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("inline");
        assertThat(response.getBody()).isNotNull();

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("edunest-private");
        assertThat(captor.getValue().key()).isEqualTo("submissions/task-sub-1-2.pdf");
    }

    @Test
    @DisplayName("FileController returns 404 when S3 throws NoSuchKeyException")
    void testFileController_S3NoSuchKey() {
        String dbPath = "uploads/submissions/missing.pdf";
        when(fileAccessService.authorizeTaskSubmission(101L)).thenReturn(dbPath);

        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Key not found").build());

        ResponseEntity<Resource> response = fileController.serveTaskSubmission(101L, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("FileController preserves local file serving when file exists on local disk (backward compatibility)")
    void testFileController_ServeLocalDiskFallback() throws IOException {
        Path subFolder = tempUploadDir.resolve("submissions");
        Files.createDirectories(subFolder);
        Path localFile = subFolder.resolve("local-test.pdf");
        Files.writeString(localFile, "local file content");

        String dbPath = tempUploadDir.relativize(localFile).toString().replace('\\', '/');
        when(fileAccessService.authorizeTaskSubmission(102L)).thenReturn(dbPath);

        ResponseEntity<Resource> response = fileController.serveTaskSubmission(102L, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");

        // s3Client should NOT be called because the file existed on local disk
        verifyNoInteractions(s3Client);
    }
}
