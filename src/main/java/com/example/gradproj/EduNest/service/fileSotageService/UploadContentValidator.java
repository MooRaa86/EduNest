package com.example.gradproj.EduNest.service.fileSotageService;

import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.CRC32;

/**
 * EB-29 [P1-22] — shared, reusable upload content validation (file-storage domain).
 *
 * Single source of truth for what a submitted file's BYTES actually are:
 * <ul>
 *   <li>formats are proven by magic bytes plus structural checks — never by
 *       {@code MultipartFile.getContentType()} and never by the original
 *       filename extension. JPEG and PNG payloads additionally must decode
 *       fully through the standard JDK image reader (header-passing garbage
 *       and truncated bodies reject); WebP has no JDK decoder, so it is
 *       verified structurally (RIFF container + WEBP form + real VP8/VP8L/VP8X
 *       subtype + declared-size consistency);</li>
 *   <li>per-surface allowlists are enforced by the caller (covers/avatars accept
 *       jpg/png/webp, chat images jpg/png); the canonical stored extension is
 *       derived from the detected format, so a stored file never diverges from
 *       its type (FILE-02/FILE-03);</li>
 *   <li>the generic {@code application/octet-stream} is definitionally not a
 *       ZIP verdict here — ZIP archives are additionally re-opened and their
 *       central directory parsed so truncated or disguised archives reject
 *       (FILE-04);</li>
 *   <li>validation happens before any bytes land in persistent storage and
 *       before any metadata is written, and throws {@code globalLogicEx} so the
 *       EB-12 unified error envelope answers 400 {@code ERR_BUSINESS_RULE}
 *       with authored, FE-safe text.</li>
 * </ul>
 *
 * Kept infrastructure-free on purpose (no new dependencies, no storage
 * abstraction) so the Supabase Storage integration (EB-83) can call the same
 * validation against byte streams.
 */
@Service
public class UploadContentValidator {

    /** Canonical extension returned by {@link #validateImageUpload}. */
    public static final String FORMAT_JPG = "jpg";
    public static final String FORMAT_PNG = "png";
    public static final String FORMAT_WEBP = "webp";

    /** Covers: mentorship-cover-images. Avatars: student/mentor/admin. Per plan P1-22. */
    public static final Set<String> IMAGE_FORMATS = Set.of(FORMAT_JPG, FORMAT_PNG, FORMAT_WEBP);
    /** Chat-room images are deliberately restricted to jpg/png (plan P1-22). */
    public static final Set<String> CHAT_IMAGE_FORMATS = Set.of(FORMAT_JPG, FORMAT_PNG);

    /**
     * Canonical image MIME types browsers emit for each detected format. A
     * declared content type that is absent, blank or {@code multipart/form-data}
     * style octet-stream is ignored (content decides); anything else must match
     * the detected format's canonical set, so executable-style declarations
     * (e.g. {@code image/x-msdownload}) or cross-format spoofing are rejected.
     */
    private static final Map<String, Set<String>> DECLARED_MIME_BY_FORMAT = Map.of(
            FORMAT_JPG, Set.of("image/jpeg", "image/jpg", "image/pjpeg"),
            FORMAT_PNG, Set.of("image/png", "image/apng"),
            FORMAT_WEBP, Set.of("image/webp"));

    private static final List<String> JPEG_SIGNED_WEBP_SUBTYPES =
            List.of("VP8 ", "VP8X", "VP8L");

    /** JPEG header structure is walked until the entropy (SOS) segment; conformant encoders emit it well inside 1 MiB. */
    private static final int JPEG_HEADER_SCAN_LIMIT = 1024 * 1024;

    /**
     * Proves the submitted bytes are a structurally sound, decodable image of
     * one of the allowed formats and returns the canonical extension to store
     * ("jpg" | "png"). "webp" is identified structurally (no JDK decoder);
     * throws {@code globalLogicEx} (400 ERR_BUSINESS_RULE) for empty,
     * oversized, unknown, unsupported, corrupt, truncated or otherwise
     * ambiguous uploads.
     */
    public String validateImageUpload(MultipartFile file, Set<String> allowedFormats, long maxBytes) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new globalLogicEx("No image file was provided.");
        }
        if (file.getSize() > maxBytes) {
            throw new globalLogicEx(
                    String.format("Image exceeds the upload size limit of %d MB.", (maxBytes + 1024 * 1024 - 1) / (1024 * 1024)));
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new globalLogicEx("The image file could not be read.");
        }
        if (content == null || content.length == 0) {
            throw new globalLogicEx("No image file was provided.");
        }
        String format = detectImageFormat(content);

        String declaredContentType = file.getContentType();
        if (declaredContentType != null && !declaredContentType.isBlank()
                && !"application/octet-stream".equalsIgnoreCase(declaredContentType)
                && !DECLARED_MIME_BY_FORMAT.getOrDefault(format, Set.of())
                        .contains(declaredContentType.toLowerCase(Locale.ROOT))) {
            throw new globalLogicEx(
                    "The declared content type does not match the submitted image content.");
        }

        if (!allowedFormats.contains(format)) {
            throw new globalLogicEx("Unsupported image format. Allowed formats: " + String.join(", ", allowedFormats.stream().sorted().toList()) + ".");
        }
        return format;
    }

    /**
     * Structurally confirms an already-accepted ZIP payload by re-opening the
     * written temp file and parsing its central directory. A file that merely
     * starts with {@code PK} magic but lacks a complete central directory —
     * truncated, corrupt, or arbitrary bytes named {@code .zip} — is rejected
     * as {@code globalLogicEx}.
     */
    public void verifyZipIntegrity(Path candidate) {
        try (ZipFile ignored = new ZipFile(candidate.toFile())) {
            // constructor order: the central directory is parsed on open
        } catch (ZipException e) {
            throw new globalLogicEx("File content is not a valid ZIP archive.");
        } catch (IOException e) {
            if (e.getCause() instanceof ZipException) {
                throw new globalLogicEx("File content is not a valid ZIP archive.");
            }
            throw new RuntimeException("Failed to validate file content", e);
        }
    }

    /**
     * Detects jpg/png/webp from magic bytes plus structural checks, or throws
     * for anything unknown, corrupt or truncated. Header-only trust
     * (content-type, filename extension) never plays a role.
     */
    public String detectImageFormat(byte[] content) {
        if (content == null || content.length == 0) {
            throw new globalLogicEx("No image file was provided.");
        }
        if (isStructurallyValidJpeg(content)) {
            return FORMAT_JPG;
        }
        if (isStructurallyValidPng(content)) {
            return FORMAT_PNG;
        }
        if (isStructurallyValidWebp(content)) {
            return FORMAT_WEBP;
        }
        throw new globalLogicEx("The uploaded file is not a supported image or its content is corrupt.");
    }

    /**
     * JPEG: requires the FF D8 FF start marker, walks the marker stream
     * (segment lengths must be consistent) until the SOF/entropy (SOS)
     * segment or an EOI, and finally requires the standard JDK decoder
     * ({@code javax.imageio} — no external dependency) to fully decode the
     * payload. Header-passing garbage and truncated bodies fail at decode;
     * exotic encodings the JDK cannot decode (e.g. CMYK JPEGs from design
     * tools) are conservatively rejected too — browsers and standard encoders
     * emit RGB/progressive JPEGs the decoder handles.
     */
    private boolean isStructurallyValidJpeg(byte[] d) {
        if (d.length < 4
                || (d[0] & 0xFF) != 0xFF || (d[1] & 0xFF) != 0xD8 || (d[2] & 0xFF) != 0xFF) {
            return false;
        }
        if (!jpegMarkerWalkReachesSos(d)) {
            return false;
        }
        return decodesAsImage(d);
    }

    /**
     * PNG: signature plus a complete, CRC-verified IHDR chunk (always the
     * first chunk, always 13 bytes), then a full decode by the standard JDK
     * PNG reader — corrupt IDAT streams or truncated bodies fail the decode.
     */
    private boolean isStructurallyValidPng(byte[] d) {
        if (d.length < 33) return false;
        for (int i = 0; i < 8; i++) {
            if ((d[i] & 0xFF) != (PNG_SIGNATURE[i] & 0xFF)) return false;
        }
        long chunkLength = unsignedBigEndian(d, 8);
        if (chunkLength != 13 || d[12] != 'I' || d[13] != 'H' || d[14] != 'D' || d[15] != 'R') {
            return false;
        }
        CRC32 crc = new CRC32();
        crc.update(d, 12, 17); // "IHDR" + 13 IHDR bytes
        if (unsignedBigEndian(d, 29) != crc.getValue()) {
            return false;
        }
        return decodesAsImage(d);
    }

    private boolean decodesAsImage(byte[] d) {
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(d));
            return decoded != null && decoded.getWidth() > 0 && decoded.getHeight() > 0;
        } catch (Exception decodeFailure) {
            return false;
        }
    }

    private boolean jpegMarkerWalkReachesSos(byte[] d) {
        int limit = Math.min(d.length, JPEG_HEADER_SCAN_LIMIT);
        int i = 2;
        int markers = 0;
        while (i < limit && markers++ < 512) {
            while (i < limit && (d[i] & 0xFF) == 0xFF) i++; // fill bytes before marker
            if (i >= limit) return false;
            int marker = d[i] & 0xFF;
            if (marker == 0xD9) return true;  // EOI — header complete
            if (marker == 0xDA) return true;  // SOS — entropy data, header complete
            if (marker == 0x00 || marker == 0xFF) return false;
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i++;
                continue;
            }
            if (i + 2 >= limit) return false;
            int segmentLength = ((d[i + 1] & 0xFF) << 8) | (d[i + 2] & 0xFF);
            if (segmentLength < 2) return false;
            i += 1 + segmentLength;
        }
        return false;
    }

    /**
     * WebP: RIFF container carrying a WEBP form with an actual VP8/VP8L/VP8X
     * bitstream subtype, and the declared RIFF size must not exceed the
     * submitted bytes (catches truncation).
     */
    private boolean isStructurallyValidWebp(byte[] d) {
        if (d.length < 16) return false;
        if (d[0] != 'R' || d[1] != 'I' || d[2] != 'F' || d[3] != 'F'
                || d[8] != 'W' || d[9] != 'E' || d[10] != 'B' || d[11] != 'P') {
            return false;
        }
        String subtype = new String(d, 12, 4, StandardCharsets.US_ASCII);
        if (!JPEG_SIGNED_WEBP_SUBTYPES.contains(subtype)) return false;
        long declaredRiffSize = unsignedLittleEndian(d, 4);
        return declaredRiffSize + 8 <= d.length;
    }

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private long unsignedBigEndian(byte[] d, int offset) {
        long v = 0;
        for (int i = 0; i < 4; i++) v = (v << 8) | (d[offset + i] & 0xFFL);
        return v;
    }

    private long unsignedLittleEndian(byte[] d, int offset) {
        long v = 0;
        for (int i = 3; i >= 0; i--) v = (v << 8) | (d[offset + i] & 0xFFL);
        return v;
    }
}
