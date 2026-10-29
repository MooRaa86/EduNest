package com.example.gradproj.EduNest.security.probes;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class TypedDownloadAbsoluteRootTest extends TypedDownloadSecurityCases {
    private static final Path ROOT = createRoot();
    private static Path createRoot() {
        try { return Files.createTempDirectory("edunest-typed-absolute-").toAbsolutePath(); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
    @DynamicPropertySource static void root(DynamicPropertyRegistry properties) {
        properties.add("app.upload.base-dir", ROOT::toString);
    }
}
