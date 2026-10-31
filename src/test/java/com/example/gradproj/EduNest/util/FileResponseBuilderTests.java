package com.example.gradproj.EduNest.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Post-audit hardening coverage for the single-handle file response (EB-27 / P1-20). */
class FileResponseBuilderTests {

    private static final byte[] BYTES = "hardened single-handle stream content".getBytes(StandardCharsets.UTF_8);

    @TempDir Path dir;

    private static void assumeSymlinkCreationSupported() {
        try {
            Path probe = Files.createTempFile("probe-", ".txt");
            Path link = probe.resolveSibling("probe-link-" + System.nanoTime());
            Files.createSymbolicLink(link, probe);
            Files.deleteIfExists(link);
            Files.deleteIfExists(probe);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            assumeTrue(false, "Symbolic link creation unavailable: " + ex.getMessage());
        }
    }

    @Test
    void streamsBytesAndDerivesContentLengthFromTheOpenedHandleOnly() throws IOException {
        Path target = dir.resolve("authorized.txt");
        Files.write(target, BYTES);
        Path handleSource = dir.resolve("handle-source.txt");
        Files.write(handleSource, "bytes visible only through the opened handle".getBytes(StandardCharsets.UTF_8));

        try (SeekableByteChannel handle = Files.newByteChannel(handleSource);
             MockedStatic<Files> files = mockStatic(Files.class)) {
            long handleLength = handle.size();
            files.when(() -> Files.probeContentType(any(Path.class))).thenReturn("text/plain");
            files.when(() -> Files.newByteChannel(any(Path.class), anySet(), any(java.nio.file.attribute.FileAttribute[].class)))
                    .thenReturn(handle);

            ResponseEntity<Resource> response = FileResponseBuilder.buildResponse(target, false);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getHeaders().getContentLength()).isEqualTo(handleLength);
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
            assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getHeaders().getFirst("Content-Security-Policy")).isEqualTo("default-src 'none'");
            try (InputStream body = response.getBody().getInputStream()) {
                assertThat(body.readAllBytes()).isEqualTo("bytes visible only through the opened handle".getBytes(StandardCharsets.UTF_8));
            }
            files.verify(() -> Files.size(any(Path.class)), never());
            files.verify(() -> Files.newInputStream(any(Path.class), any(OpenOption[].class)), never());
        }
    }

    @Test
    void closesTheOpenedHandleWhenAttributeOrResponseConstructionFails() throws IOException {
        Path target = dir.resolve("authorized.txt");
        Files.write(target, BYTES);
        SeekableByteChannel failing = mock(SeekableByteChannel.class);
        when(failing.size()).thenThrow(new IOException("attributes changed while sizing"));
        try (MockedStatic<Files> files = mockStatic(Files.class)) {
            files.when(() -> Files.probeContentType(any(Path.class))).thenReturn("text/plain");
            files.when(() -> Files.newByteChannel(any(Path.class), anySet(), any(java.nio.file.attribute.FileAttribute[].class)))
                    .thenReturn(failing);

            assertThatThrownBy(() -> FileResponseBuilder.buildResponse(target, false))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("attributes changed");
            verify(failing).close();
        }
    }

    @Test
    void rejectsSymlinkSwappedInAtTheFinalPathComponent() throws IOException {
        assumeSymlinkCreationSupported();
        Path outside = dir.getParent().resolve("outside-" + System.nanoTime() + ".txt");
        Files.write(outside, "outside-secret".getBytes(StandardCharsets.UTF_8));
        Path swapped = dir.resolve("authorized-swap.txt");
        try {
            Files.createSymbolicLink(swapped, outside);
            assertThatThrownBy(() -> FileResponseBuilder.buildResponse(swapped, false))
                    .isInstanceOf(IOException.class);
        } finally {
            try (var walk = Files.walk(dir, 1)) {
                walk.forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void missingFileFailsBeforeAnyResourceIsBuilt() {
        assertThatThrownBy(() -> FileResponseBuilder.buildResponse(dir.resolve("missing.txt"), false))
                .isInstanceOf(IOException.class);
    }

    @Test
    void responseClosesTheHandleAfterTheBodyIsFullyConsumed() throws IOException {
        boolean linux = System.getProperty("os.name").toLowerCase().contains("linux");
        assumeTrue(linux, "open-fd audit is only performed on Linux CI");

        Path target = dir.resolve("authorized.txt");
        Files.write(target, BYTES);

        ResponseEntity<Resource> response = FileResponseBuilder.buildResponse(target, true);
        try (InputStream body = response.getBody().getInputStream()) {
            assertThat(body.readAllBytes()).isEqualTo(BYTES);
        }

        try (var fds = Files.list(Path.of("/proc/self/fd"))) {
            List<String> open = fds.map(p -> {
                try { return Files.readSymbolicLink(p).toString(); } catch (IOException e) { return ""; }
            }).filter(s -> s.equals(target.toString())).toList();
            assertThat(open).isEmpty();
        }
    }
}
