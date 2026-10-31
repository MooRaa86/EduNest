package com.example.gradproj.EduNest.util;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

public class FileResponseBuilder {

    public static ResponseEntity<Resource> buildResponse(Path filePath, boolean download) throws IOException {
        String contentType = Files.probeContentType(filePath);
        MediaType mediaType = parseMediaType(contentType);

        boolean safeInline = mediaType.getType().equalsIgnoreCase("image")
                || mediaType.equals(MediaType.APPLICATION_PDF)
                || mediaType.equals(MediaType.TEXT_PLAIN);

        ContentDisposition disposition = (!download && safeInline)
                ? ContentDisposition.inline().filename(filePath.getFileName().toString()).build()
                : ContentDisposition.attachment().filename(filePath.getFileName().toString()).build();

        // P1-20 hardening (post-TOCTOU audit): the authorized file is opened
        // once and Content-Length is read from that same open handle instead of
        // a second pathname-based Files.size() call that could describe a
        // different object. NOFOLLOW_LINKS rejects a symlink/junction swapped
        // in at the final path component after validation (defense-in-depth
        // only: it does NOT protect against parent-directory or reparse-point
        // races in between, which remain the host filesystem's responsibility).
        // On failure before return the handle is closed; on success it is kept
        // open for streaming and closed by the response writer.
        SeekableByteChannel channel = null;
        try {
            channel = Files.newByteChannel(filePath, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
            long contentLength = channel.size();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(contentLength)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'")
                    .body(new InputStreamResource(Channels.newInputStream(channel)));
        } catch (IOException | RuntimeException ex) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                }
            }
            throw ex;
        }
    }

    private static MediaType parseMediaType(String contentType) {
        try {
            return contentType != null 
                    ? MediaType.parseMediaType(contentType)
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
