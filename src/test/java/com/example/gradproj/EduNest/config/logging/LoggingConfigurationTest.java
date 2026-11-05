package com.example.gradproj.EduNest.config.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Runs logging initialization in isolated JVMs, never reconfiguring the test JVM. */
public class LoggingConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    static class ProbeConfiguration { }

    public static void main(String[] args) {
        try (var context = new SpringApplicationBuilder(ProbeConfiguration.class)
                .web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF).run()) {
            var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            var root = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            var file = root.getAppender("FILE");
            System.out.println("TEST_FILE_APPENDER=" + (file != null));
            if (file != null) {
                System.out.println("TEST_FILE_STARTED=" + file.isStarted() + " PATH=" + ((RollingFileAppender<?>) file).getFile());
                var policy = (SizeAndTimeBasedRollingPolicy<?>) ((RollingFileAppender<?>) file).getRollingPolicy();
                System.out.println("TEST_MAX_HISTORY=" + policy.getMaxHistory());
            }
            org.slf4j.MDC.put("requestId", "test-id");
            var logger = LoggerFactory.getLogger("eb68.test");
            logger.error("unsafe\nsecond\t\"quoted\"\\path", new IllegalStateException("exception-secret"));
            for (int i = 0; i < 500; i++) logger.info("rotation-{} {}", i, "x".repeat(200));
            org.slf4j.MDC.clear();
        }
    }

    @Test
    void consoleDefaultsEscapeMessagesAndDoNotCreateFile(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("disabled.log");
        String output = launch(path, false);
        assertTrue(output.contains("TEST_FILE_APPENDER=false"));
        assertFalse(Files.exists(path));
        String record = output.lines().filter(line -> line.contains("logger=eb68.test") && line.contains("unsafe"))
                .findFirst().orElseThrow();
        assertTrue(record.contains("requestId=test-id"));
        assertTrue(record.contains("unsafe\\nsecond\\t\\\"quoted\\\"\\\\path"));
        assertTrue(record.contains("exception=\"java.lang.IllegalStateException"));
        assertFalse(output.contains("exception-secret"));
        assertFalse(output.lines().anyMatch(line -> line.startsWith("second")));
    }

    @Test
    void optInFileAppenderRotatesAndHasConfiguredRetention(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("enabled.log");
        String output = launch(path, true);
        assertTrue(output.contains("TEST_FILE_APPENDER=true"));
        assertTrue(output.contains("TEST_MAX_HISTORY=7"));
        assertTrue(Files.exists(path));
        try (var entries = Files.list(directory)) {
            assertTrue(entries.anyMatch(p -> p.getFileName().toString().endsWith(".gz")), "size-based archive expected");
        }
        String xml = Files.readString(Path.of("src/main/resources/logback-spring.xml"));
        assertTrue(xml.contains("defaultValue=\"10MB\""));
        assertTrue(xml.contains("defaultValue=\"100MB\""));
        assertTrue(xml.contains("<totalSizeCap>${FILE_TOTAL_CAP}</totalSizeCap>"));
    }

    @Test
    void unavailableOptInFilePathFailsStartup(@TempDir Path directory) throws Exception {
        Path nonDirectory = directory.resolve("not-a-directory");
        Files.writeString(nonDirectory, "file");
        var result = runProbe(nonDirectory.resolve("app.log"), true);
        assertNotEquals(0, result.exitCode(), result.output().lines().filter(line -> line.contains("TEST_FILE") || line.contains("ERROR")).limit(10).toList().toString());
        assertTrue(result.output().contains("openFile("), result.output());
    }

    private static String launch(Path file, boolean enabled) throws Exception {
        var result = runProbe(file, enabled);
        assertEquals(0, result.exitCode(), result.output());
        return result.output();
    }

    private record ProbeResult(int exitCode, String output) { }

    private static ProbeResult runProbe(Path file, boolean enabled) throws Exception {
        String javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var command = new java.util.ArrayList<>(java.util.List.of(javaCommand, "-Dspring.devtools.restart.enabled=false",
                "-Dapp.logging.file.path=" + file.toAbsolutePath(), "-Dapp.logging.file.max-size=1KB"));
        if (enabled) command.add("-Dspring.profiles.active=logging-file");
        command.addAll(java.util.List.of("-cp", System.getProperty("surefire.test.class.path"), LoggingConfigurationTest.class.getName()));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            // Drain output concurrently; waiting first could deadlock on a full process pipe.
            var output = executor.submit(() -> new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(process.waitFor(Duration.ofSeconds(45).toMillis(), TimeUnit.MILLISECONDS), "logging probe timed out");
            String text = output.get(30, TimeUnit.SECONDS);
            return new ProbeResult(process.exitValue(), text);
        } finally {
            // Destroy before tearing down the executor: the drain task ends on EOF, and a hung
            // process would otherwise deadlock in ExecutorService.close before this finally ran.
            if (process.isAlive()) process.destroyForcibly();
            executor.shutdownNow();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                assertTrue(false, "drain task failed to terminate after probe cleanup");
            }
        }
    }
}
