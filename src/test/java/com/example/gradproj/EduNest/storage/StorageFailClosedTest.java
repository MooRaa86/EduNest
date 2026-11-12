package com.example.gradproj.EduNest.storage;

import com.example.gradproj.EduNest.config.transaction.AfterCommitExecutor;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.service.fileSotageService.FileStorageService;
import com.example.gradproj.EduNest.service.fileSotageService.UploadContentValidator;
import com.example.gradproj.EduNest.service.storage.SupabaseStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * EB-89 / Omar's fail-closed storage doctrine (2026-11-12): a failing Supabase S3
 * call during an upload MUST abort the operation with an exception - it must NEVER
 * silently fall back to saving under the local upload tree. Containers are
 * stateless: a local write in production is silent data loss on the next restart.
 *
 * The contract pinned here (all environments, no Docker - D-27):
 *   prod + storage configured  -> S3 only; ANY S3 failure (S3Exception, SDK
 *                                 transport failure) aborts; the upload tree
 *                                 stays EMPTY (the no-fallback invariant);
 *   prod + storage unconfigured -> hard fail (fail-closed boot of saveFile);
 *   dev  + storage configured   -> S3-only too: configuration selects the
 *                                 backend, never the environment softening;
 *   dev  + storage unconfigured -> local disk (the legitimate dev path).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EB-89: fail-closed storage contract - S3 failure never falls back to local disk")
class StorageFailClosedTest {

    @Mock
    private SupabaseStorageService storage;

    @Mock
    private UploadContentValidator contentValidator;

    @TempDir
    Path uploadRoot;

    private final MockMultipartFile file = new MockMultipartFile("file", "fixture.txt",
            "text/plain", "synthetic upload content".getBytes(StandardCharsets.UTF_8));

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService(contentValidator, storage, new MockEnvironment(), new AfterCommitExecutor());
        ReflectionTestUtils.setField(service, "baseDir", uploadRoot.toString());
        service.init();
    }

    private MockEnvironment env(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        // the environment instance the service consults is the one handed to the constructor
        service = new FileStorageService(contentValidator, storage, environment, new AfterCommitExecutor());
        ReflectionTestUtils.setField(service, "baseDir", uploadRoot.toString());
        service.init();
        return environment;
    }

    private void assertUploadTreeEmpty() throws IOException {
        try (Stream<Path> walk = Files.walk(uploadRoot)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            assertThat(files).as("local upload tree must contain ZERO files - a silent local "
                    + "fallback in production is data loss on the next restart").isEmpty();
        }
    }

    @Test
    @DisplayName("prod + configured: S3Exception aborts the upload and writes NOTHING locally")
    void prodS3FailureAbortsAndWritesNothing() throws IOException {
        env("prod");
        when(storage.isConfigured()).thenReturn(true);
        when(storage.uploadPrivate(anyString(), anyString(), any(byte[].class), anyString()))
                .thenThrow(S3Exception.builder().message("service unavailable").statusCode(503).build());

        // the real impl converts S3Exception -> globalLogicEx (pinned in SupabaseStorageServiceTest);
        // at this contract level what matters is ABORT + an empty local tree.
        assertThatThrownBy(() -> service.saveFile("submissions", "fixture", 1L, 2L, file))
                .isInstanceOf(RuntimeException.class);

        assertUploadTreeEmpty();
    }

    @Test
    @DisplayName("prod + configured: S3 transport failure (unreachable/timeout) aborts identically")
    void prodS3TransportFailureAborts() throws IOException {
        env("prod");
        when(storage.isConfigured()).thenReturn(true);
        when(storage.uploadPrivate(anyString(), anyString(), any(byte[].class), anyString()))
                .thenThrow(SdkClientException.create("Unable to execute HTTP request: Connection refused"));

        assertThatThrownBy(() -> service.saveFile("submissions", "fixture", 1L, 2L, file))
                .isInstanceOf(RuntimeException.class);

        assertUploadTreeEmpty();
    }

    @Test
    @DisplayName("prod + unconfigured: hard fail before any storage IO (fail-closed guard)")
    void prodUnconfiguredHardFails() throws IOException {
        env("prod");
        when(storage.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.saveFile("submissions", "fixture", 1L, 2L, file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Local storage fallback is prohibited in production");

        assertUploadTreeEmpty();
    }

    @Test
    @DisplayName("dev + configured: S3 failure aborts too - configuration selects the backend, not the profile")
    void devConfiguredStillS3Only() throws IOException {
        env("dev");
        when(storage.isConfigured()).thenReturn(true);
        when(storage.uploadPrivate(anyString(), anyString(), any(byte[].class), anyString()))
                .thenThrow(S3Exception.builder().message("boom").statusCode(500).build());

        assertThatThrownBy(() -> service.saveFile("submissions", "fixture", 1L, 2L, file))
                .isInstanceOf(RuntimeException.class);

        assertUploadTreeEmpty();
    }

    @Test
    @DisplayName("dev + unconfigured: local disk is the legitimate path (regression sanity)")
    void devUnconfiguredUsesLocalDisk() throws IOException {
        env("dev");
        when(storage.isConfigured()).thenReturn(false);

        String storedPath = service.saveFile("submissions", "fixture", 1L, 2L, file);

        assertThat(storedPath).isNotBlank();
        assertThat(Files.exists(uploadRoot.resolve(storedPath.replace('\\', '/'))))
                .as("dev + unconfigured must still write to the local tree (the dev store)")
                .isTrue();
    }
}
