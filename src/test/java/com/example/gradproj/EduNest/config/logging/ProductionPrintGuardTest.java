package com.example.gradproj.EduNest.config.logging;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionPrintGuardTest {
    @Test
    void productionSourcesHaveNoRawConsolePrinting() throws Exception {
        var prohibited = Pattern.compile("System\\s*\\.\\s*(?:out|err)\\s*\\.\\s*(?:print|println|printf)\\s*\\(|\\.\\s*printStackTrace\\s*\\(");
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertTrue(prohibited.matcher(Files.readString(path)).results().findAny().isEmpty(), "Raw print in " + path);
            }
        }
    }
}
