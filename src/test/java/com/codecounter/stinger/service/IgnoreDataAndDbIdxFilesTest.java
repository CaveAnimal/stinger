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
class IgnoreDataAndDbIdxFilesTest {

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @Test
    void filesWithIdxAndDbExtensionsAreIgnored(@TempDir Path tmpDir) throws IOException {
        Path project = tmpDir.resolve("project");
        Files.createDirectories(project);

        // create files that should be ignored by extension
        Files.writeString(project.resolve("thesystem.idx"), "index data");
        Files.writeString(project.resolve("local.db"), "sqlite data");
        // IntelliJ module files should also be ignored
        Files.writeString(project.resolve("module.iml"), "<module/>\n");

        // create a normal file that should be included
        Files.writeString(project.resolve("Included.java"), "public class Included {}\n");

        System.setProperty("stinger.results.dir", tmpDir.resolve("code_counter_results").toString());
        try {
            AnalysisResult result = fileAnalysisService.analyzeDirectory(project.toString());

            assertEquals(1, result.getTotalFiles(), "Only the non-ignored file should be counted");

            Path saved = Path.of(result.getResultsPath());
            List<String> totalFiles = Files.readAllLines(saved.resolve("total_files.txt"));
            assertFalse(totalFiles.stream().anyMatch(s -> s.endsWith(".idx")), "*.idx files must not be present in saved totals");
            assertFalse(totalFiles.stream().anyMatch(s -> s.endsWith(".db")), "*.db files must not be present in saved totals");
            assertFalse(totalFiles.stream().anyMatch(s -> s.endsWith(".iml")), "*.iml files must not be present in saved totals");
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }

    @Test
    void directoriesNamedDataAreIgnored(@TempDir Path tmpDir) throws IOException {
        Path project = tmpDir.resolve("project");
        Files.createDirectories(project);

        // create a directory named data with files that should be ignored
        Path ignored = project.resolve("data/subdir");
        Files.createDirectories(ignored);
        Files.writeString(ignored.resolve("Hidden.java"), "public class Hidden {}\n");

        // create a file that should be included
        Files.writeString(project.resolve("Included.java"), "public class Included {}\n");

        System.setProperty("stinger.results.dir", tmpDir.resolve("code_counter_results").toString());
        try {
            AnalysisResult result = fileAnalysisService.analyzeDirectory(project.toString());

            assertEquals(1, result.getTotalFiles(), "Files under data/ must be ignored");

            Path saved = Path.of(result.getResultsPath());
            List<String> totalFiles = Files.readAllLines(saved.resolve("total_files.txt"));
            assertFalse(totalFiles.stream().anyMatch(s -> s.contains("/data/") || s.contains("\\data\\")), "data/ must not be present in saved totals");
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }
}
