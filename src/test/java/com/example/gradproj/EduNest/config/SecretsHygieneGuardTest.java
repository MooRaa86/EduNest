package com.example.gradproj.EduNest.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * P6-03 / EB-74 [DEP-05] — secrets-hygiene guard.
 *
 * Keeps the secrets posture honest in CI (store = Railway dashboard per the D-22
 * amendment; values never in git/image/logs):
 *   1. The Dockerfile bakes no secret-bearing ENV values (only the profile flag).
 *   2. `.gitignore` covers env files, PEM keys, and the rotation scratch dir.
 *   3. Secret-classified bindings in `application*.properties` are
 *      `${ENV_VAR}` placeholders only — a literal assignment fails the build.
 *   4. Nothing secret-shaped is tracked by git (`.env*`, `*.pem`, scratch values).
 *
 * D-27-clean: plain file parsing + one `git ls-files` call, no Docker, no Spring context.
 */
class SecretsHygieneGuardTest {

    private static final Path DOCKERFILE = Path.of("Dockerfile");
    private static final Path GITIGNORE = Path.of(".gitignore");
    private static final List<Path> PROPERTIES_FILES = List.of(
            Path.of("src/main/resources/application.properties"),
            Path.of("src/main/resources/application-prod.properties"));

    /** Property keys whose value must come from the environment (P5-04 secret classification). */
    private static final List<String> SECRET_BINDINGS = List.of(
            "spring.datasource.password",
            "spring.mail.password",
            "app.zoom.client-secret",
            "supabase.storage.access-key",
            "supabase.storage.secret-key");

    /** The only ENV declaration allowed to be baked into the image. */
    private static final Pattern DOCKERFILE_ENV_LINE = Pattern.compile("^ENV\\s+([A-Za-z_][A-Za-z0-9_]*)=(.*)$");
    private static final String ALLOWED_BAKED_ENV_KEY = "SPRING_PROFILES_ACTIVE";

    /** A resolved value must be exactly a ${VAR} or ${VAR:default} placeholder. */
    private static final Pattern PLACEHOLDER_ONLY = Pattern.compile("^\\$\\{[A-Z][A-Z0-9_]*(:.*)?}$");

    private static final List<String> REQUIRED_IGNORE_PATTERNS = List.of(
            ".env", ".env.*", "!.env.example", "*.pem", "ops/secrets/local/");

    private static final String LIVE_SECRET_SHAPE =
            "(sk_live_[A-Za-z0-9]{8,}|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{20,}"
                    + "|-----BEGIN [A-Z ]*PRIVATE KEY-----)";

    @Test
    @DisplayName("Dockerfile bakes no secret-bearing ENV values")
    void dockerfileBakesNoSecrets() throws IOException {
        assertTrue(Files.exists(DOCKERFILE), "Dockerfile must exist (image-bake audit)");
        List<String> lines = Files.readAllLines(DOCKERFILE, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            var matcher = DOCKERFILE_ENV_LINE.matcher(lines.get(i).trim());
            if (!matcher.matches()) {
                continue;
            }
            String key = matcher.group(1);
            String value = matcher.group(2);
            assertTrue(ALLOWED_BAKED_ENV_KEY.equals(key),
                    "Dockerfile line " + (i + 1) + " bakes ENV " + key
                            + " - only SPRING_PROFILES_ACTIVE may be baked; secrets are injected at runtime");
            assertTrue(!Pattern.compile(LIVE_SECRET_SHAPE).matcher(value).find(),
                    "Dockerfile line " + (i + 1) + " bakes a live-secret-shaped value");
        }
    }

    @Test
    @DisplayName(".gitignore covers env files, PEM keys, and the rotation scratch dir")
    void gitignoreCoversSecretFiles() throws IOException {
        List<String> lines = Files.readAllLines(GITIGNORE, StandardCharsets.UTF_8);
        for (String required : REQUIRED_IGNORE_PATTERNS) {
            assertTrue(lines.contains(required),
                    ".gitignore must contain the ignore rule '" + required + "' (P6-03 posture)");
        }
    }

    @Test
    @DisplayName("Secret-classified property bindings are ${VAR} placeholders only")
    void secretBindingsArePlaceholdersOnly() throws IOException {
        for (Path file : PROPERTIES_FILES) {
            assertTrue(Files.exists(file), "expected properties file " + file);
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq);
                if (!SECRET_BINDINGS.contains(key)) {
                    continue;
                }
                String value = trimmed.substring(eq + 1).trim();
                assertTrue(PLACEHOLDER_ONLY.matcher(value).matches(),
                        "secret binding '" + key + "' in " + file + " must resolve from an environment"
                                + " placeholder (${VAR}), never a literal - found: " + value);
            }
        }
    }

    @Test
    @DisplayName("git tracks no secret artifacts (.env*, *.pem, rotation scratch)")
    void gitTracksNoSecretArtifacts() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("git", "ls-files");
        Process process;
        try {
            process = pb.start();
        } catch (IOException gitUnavailable) {
            // Environment without git (rare) cannot prove this probe - skip rather than fail.
            assumeTrue(false, "git unavailable; tracked-file probe skipped");
            return;
        }
        List<String> tracked;
        try (Stream<String> stream = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).lines()) {
            tracked = stream.toList();
        }
        assertTrue(!tracked.isEmpty(), "git ls-files returned nothing; probe could not run");

        List<String> offenders = new ArrayList<>();
        for (String trackedPath : tracked) {
            String name = Path.of(trackedPath.replace('\\', '/')).getFileName().toString();
            boolean envArtifact = name.equals(".env") || (name.startsWith(".env.")
                    && !name.equals(".env.example"));
            boolean pemArtifact = name.endsWith(".pem");
            boolean scratch = trackedPath.replace('\\', '/').startsWith("ops/secrets/local/");
            if (envArtifact || pemArtifact || scratch) {
                offenders.add(trackedPath);
            }
        }
        assertTrue(offenders.isEmpty(),
                "secret artifacts tracked by git: " + offenders + " - remove and rely on ignored local files");
    }
}
