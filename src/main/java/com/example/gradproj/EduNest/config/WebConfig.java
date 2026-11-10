package com.example.gradproj.EduNest.config;

import com.example.gradproj.EduNest.config.file.UploadPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Path;

/**
 * P1-21 / EB-28 — explicit public-allowlist static serving for uploads.
 *
 * Before (FILE-01 defect): ONE broad handler "/uploads/**" → "file:uploads/"
 * served the entire uploads tree — including submissions/, task-attachment/,
 * project-attachment/ — with no security awareness in the MVC handler itself
 * (the security chain is a separate protection layer).
 *
 * Now (fail-closed, generated from UploadPolicy — the single policy source):
 *  - one handler per ANONYMOUS-PUBLIC category only; public URLs are unchanged
 *    (repair issue for FE: none of the public shapes changed).
 *  - private/unknown/admin paths have NO resource handler at all: with no
 *    broad upload fallback, they are not served by these handlers — defense
 *    in depth; the configured default resource locations must also be safe.
 *
 * The base dir honors app.upload.base-dir (same property FileStorageService and
 * FileController and ImageStorageService use). The default remains uploads/.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.base-dir:uploads}")
    private String baseDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        for (String folder : UploadPolicy.PUBLIC_FOLDERS) {
            registry.addResourceHandler("/uploads/" + folder + "/**")
                    .addResourceLocations(baseLocation(folder));
        }
    }

    private String baseLocation(String folder) {
        String location = Path.of(baseDir, folder).toAbsolutePath().normalize().toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
