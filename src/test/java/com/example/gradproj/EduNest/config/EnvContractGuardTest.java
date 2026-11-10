package com.example.gradproj.EduNest.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P5-04 / EB-70 [DEP-05/DEP-07] — configuration-contract guard.
 *
 * Keeps the environment documentation honest in CI:
 *   1. Every `${ENV_VAR[:default]}` placeholder referenced by the shipped
 *      `application*.properties` files must exist in `.env.example`.
 *   2. Code-side resolvable variables (documented optional knobs and the
 *      SEL-02 dev-chain gate) must also exist in `.env.example`.
 *   3. Dormancy/feature-flag defaults are pinned so a silent default change
 *      breaks the build instead of flipping disabled behavior.
 *   4. No real-live secret shapes (style: sk_live / AKIA / ghp_ / PEM key material)
 *      may appear in `.env.example`.
 *
 * D-27-clean: plain file parsing, no Spring context, no Docker.
 */
class EnvContractGuardTest {

    private static final Path ENV_EXAMPLE = Path.of(".env.example");
    private static final List<Path> PROPERTIES_FILES = List.of(
            Path.of("src/main/resources/application.properties"),
            Path.of("src/main/resources/application-prod.properties"));

    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]+)(:[^}]*)?}");

    /** Variables resolvable from code (relaxed binding / @Value()) rather than a property file. */
    private static final List<String> DOCUMENTED_CODE_SIDE_VARS = List.of(
            "EDUNEST_UNSAFE_DEV",                       // SEL-02 boot gate (ProjectSecurityConfig)
            "APP_ZOOM_HOST_USER_ID",                    // EB-66 runtime
            "APP_ZOOM_MAX_MEETING_MINUTES",
            "APP_ZOOM_OAUTH_TOKEN_URL",
            "APP_RATE_LIMIT_ENABLED",                   // EB-15 / P1-08 knobs
            "APP_UPLOAD_BASE_DIR",
            "APP_AUTH_RESET_TOKEN_TTL",
            "APP_CERTIFICATE_ISSUANCE_BATCH_SIZE");

    /** Defaults that are contracts of the dormancy/rollout decisions and must not drift silently. */
    private static final Map<String, String> PINNED_DEFAULTS = Map.of(
            "ZOOM_ENABLED", "false",                                    // D-29: disabled until keys flip
            "SUPABASE_STORAGE_ENABLED", "false",                        // D-25 rollback lane default
            "SPRING_PROFILES_ACTIVE", "prod",                           // hardened by default
            "EDUNEST_UNSAFE_DEV", "false",                              // SEL-02
            "APP_LIVESESSION_ATTENDANCE_ENFORCED", "false",             // D-15 dormancy
            "APP_LIVESESSION_AUTO_CLOSE_ENABLED", "true");              // approved active sweep

    private static final Pattern LIVE_SECRET_SHAPE = Pattern.compile(
            "(sk_live_[A-Za-z0-9]{8,}|pk_live_[A-Za-z0-9]{8,}|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{20,}"
                    + "|xox[baprs]-[A-Za-z0-9-]{10,}|-----BEGIN [A-Z ]*PRIVATE KEY-----)");

    private static List<String> envExampleLines() throws IOException {
        assertThat(ENV_EXAMPLE.toAbsolutePath())
                .as("repo-root .env.example must exist (EB-70 contract)")
                .exists();
        return Files.readAllLines(ENV_EXAMPLE, StandardCharsets.UTF_8);
    }

    private static Map<String, List<String>> placeholdersPerFile() throws IOException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Path file : PROPERTIES_FILES) {
            assertThat(file.toAbsolutePath()).as("expected properties file %s", file).exists();
            List<String> found = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .flatMap(line -> {
                        Matcher matcher = ENV_PLACEHOLDER.matcher(line);
                        java.util.List<String> vars = new java.util.ArrayList<>();
                        while (matcher.find()) {
                            vars.add(matcher.group(1));
                        }
                        return vars.stream();
                    })
                    .toList();
            result.put(file.toString(), found);
        }
        return result;
    }

    @Test
    @DisplayName("Every ${VAR} placeholder in application*.properties exists in .env.example")
    void everyPropertiesPlaceholderIsDocumented() throws IOException {
        List<String> documented = envExampleLines();
        Map<String, List<String>> byFile = placeholdersPerFile();
        for (Map.Entry<String, List<String>> entry : byFile.entrySet()) {
            assertThat(entry.getValue())
                    .as("new env vars used in %s must be added to .env.example (P5-04 contract)", entry.getKey())
                    .allSatisfy(var -> assertThat(anyLineDocuments(documented, var)).isTrue());
        }
    }

    @Test
    @DisplayName("Code-side configurable variables (relaxed binding / @Value) are documented")
    void codeSideVarsAreDocumented() throws IOException {
        List<String> documented = envExampleLines();
        for (String var : DOCUMENTED_CODE_SIDE_VARS) {
            assertThat(anyLineDocuments(documented, var))
                    .as("%s must appear in .env.example", var).isTrue();
        }
    }

    @Test
    @DisplayName("Decision-critical defaults in .env.example match their recorded contracts")
    void pinnedDefaultsDoNotDrift() throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : envExampleLines()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                values.putIfAbsent(trimmed.substring(0, eq), trimmed.substring(eq + 1).trim());
            }
        }
        for (Map.Entry<String, String> pinned : PINNED_DEFAULTS.entrySet()) {
            assertThat(values.get(pinned.getKey()))
                    .as("%s example default must stay pinned to %s (decision contract)", pinned.getKey(), pinned.getValue())
                    .isEqualTo(pinned.getValue());
        }
    }

    @Test
    @DisplayName(".env.example contains placeholders only - no live secret shapes")
    void noLiveSecretMaterial() throws IOException {
        for (String line : envExampleLines()) {
            assertThat(LIVE_SECRET_SHAPE.matcher(line).find())
                    .as("live credential shape detected in .env.example (%s) - replace with placeholder", line)
                    .isFalse();
        }
    }

    private static boolean anyLineDocuments(List<String> lines, String variable) {
        Pattern pattern = Pattern.compile("^\\s*#*\\s*" + Pattern.quote(variable) + "\\s?=.*",
                Pattern.CASE_INSENSITIVE);
        return lines.stream().anyMatch(line -> pattern.matcher(line).matches());
    }
}
