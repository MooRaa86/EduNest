package com.example.gradproj.EduNest.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * EB-76 / P6-05 (A-9): the packaged executable jar must not ship
 * spring-boot-devtools (devtools restarts/classloader tricks are a runtime
 * hardening hazard). The exclusion comes from spring-boot-maven-plugin's
 * default {@code excludeDevtools=true} — this test is the regression guard.
 *
 * <p>Runtime wiring note: the surefire (test) phase runs before repackage, so a
 * jar may not exist yet when this suite runs as part of {@code mvnw test} — in
 * that case the test is skipped (assumption), and the authoritative check runs
 * in the docker smoke workflow against the freshly packaged jar.</p>
 */
class DevtoolsAbsenceTest {

    private static final String DEVTOOLS_PREFIX = "BOOT-INF/lib/spring-boot-devtools";

    @Test
    void packagedExecutableJarExcludesDevtools() throws IOException {
        Path jar = findPackagedJar();
        assumeTrue(jar != null,
                "no packaged executable jar under target/ yet — run 'mvnw package' (checked authoritatively by docker smoke)");
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            boolean devtoolsShipped = jarFile.stream()
                    .anyMatch(entry -> entry.getName().startsWith(DEVTOOLS_PREFIX));
            assertFalse(devtoolsShipped, "spring-boot-devtools must be excluded from the packaged jar (A-9)");
            assertTrue(jarFile.stream().anyMatch(e -> e.getName().startsWith("BOOT-INF/classes/")),
                    "packaged jar does not look like a Spring Boot executable jar");
        }
    }

    @Test
    void dockerignoreKeepsLocalStateAndSecretsOutOfTheImageContext() throws IOException {
        Path dockerignore = Paths.get(".dockerignore");
        assumeTrue(Files.exists(dockerignore), ".dockerignore not present in the working directory");
        String rules = Files.readString(dockerignore);
        for (String required : new String[] {"uploads/", ".env", "target/", "docs/", "plan/", "ops/", "tools/", "mds/", ".github/"}) {
            assertTrue(rules.contains(required), ".dockerignore must exclude " + required);
        }
    }

    private Path findPackagedJar() throws IOException {
        Path target = Paths.get("target");
        if (!Files.isDirectory(target)) {
            return null;
        }
        try (Stream<Path> jars = Files.list(target)) {
            return jars
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().endsWith(".original"))
                    .findFirst()
                    .orElse(null);
        }
    }
}
