package com.codecounter.stinger.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.codecounter.stinger.model.AnalysisResult;

@SpringBootTest
class H2StorageHookTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("stinger.h2.enabled", () -> "true");
        registry.add("stinger.h2.base-dir", () -> tempDir.resolve("code_summary_results").toString());
        // Avoid calling a real llama.cpp server during tests.
        registry.add("stinger.h2.max-files", () -> "0");
        registry.add("stinger.h2.max-folders", () -> "0");
        registry.add("stinger.h2.skip-methods", () -> "true");
    }

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @Test
    void createsH2OutputFoldersWhenEnabled() throws IOException {
        // Arrange: a tiny project
        Path projectDir = tempDir.resolve("my-app");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("Main.java"), "public class Main { void m() {} }\n");

        // Ensure analyzer saves into a temp results dir
        Path resultsDir = tempDir.resolve("code_counter_results");
        System.setProperty("stinger.results.dir", resultsDir.toString());
        Path summaryBaseDir = tempDir.resolve("code_summary_results");

        try {
            // Act
            AnalysisResult result = fileAnalysisService.analyzeDirectory(projectDir.toString());

            // Assert: H2StorageService infers app/run from the saved results path
            Path saved = Path.of(result.getResultsPath());
            String appName = saved.getParent().getFileName().toString();
            String runName = saved.getFileName().toString();

            Path outRoot = summaryBaseDir.resolve(appName).resolve(runName);
            assertTrue(Files.isDirectory(outRoot.resolve("h2")), "expected h2 directory");
            assertTrue(Files.isDirectory(outRoot.resolve("processing")), "expected processing directory");
            assertTrue(Files.exists(outRoot.resolve("h2").resolve("stinger.mv.db")), "expected H2 DB file");
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }
}
