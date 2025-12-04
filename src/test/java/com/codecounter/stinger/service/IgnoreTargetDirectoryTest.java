package com.codecounter.stinger.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.codecounter.stinger.model.AnalysisResult;

@SpringBootTest
class IgnoreTargetDirectoryTest {

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @Test
    void targetDirectoryIgnored(@TempDir Path tmpDir) throws IOException {
        Path project = tmpDir.resolve("project");
        Files.createDirectories(project);

        // directories that should be ignored
        Files.createDirectories(project.resolve("target/dir"));

        // file that should be included
        Files.writeString(project.resolve("Included.java"), "public class Included {}\n");

        System.setProperty("stinger.results.dir", tmpDir.resolve("code_counter_results").toString());
        try {
            AnalysisResult result = fileAnalysisService.analyzeDirectory(project.toString());

            assertEquals(1, result.getTotalFiles(), "Only the non-target file should be counted");

            Path saved = Path.of(result.getResultsPath());
            List<String> totalFiles = Files.readAllLines(saved.resolve("total_files.txt"));
            assertFalse(totalFiles.stream().anyMatch(s -> s.contains("target")), "target/ must not be present in saved totals");
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }
}
