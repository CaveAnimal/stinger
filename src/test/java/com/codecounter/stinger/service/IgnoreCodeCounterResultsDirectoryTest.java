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
class IgnoreCodeCounterResultsDirectoryTest {

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @Test
    void codeCounterResultsFolderIsIgnored(@TempDir Path tmpDir) throws IOException {
        Path project = tmpDir.resolve("project");
        Files.createDirectories(project);

        // place a file that should be included
        Files.writeString(project.resolve("Included.java"), "public class Included {}\n");

        // create a nested code_counter_results folder that should be ignored
        Path ignored = project.resolve("code_counter_results/subdir");
        Files.createDirectories(ignored);
        Files.writeString(ignored.resolve("Hidden.java"), "public class Hidden {}\n");

        // force tests to write their results into the temp dir
        System.setProperty("stinger.results.dir", tmpDir.resolve("code_counter_results").toString());
        try {
            AnalysisResult result = fileAnalysisService.analyzeDirectory(project.toString());

            // Only the one included file should be counted
            assertEquals(1, result.getTotalFiles(), "Files under code_counter_results must be ignored");

            Path saved = Path.of(result.getResultsPath());
            List<String> totalFiles = Files.readAllLines(saved.resolve("total_files.txt"));
            assertFalse(totalFiles.stream().anyMatch(s -> s.contains("code_counter_results")), "code_counter_results must not be present in saved totals");
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }
}
