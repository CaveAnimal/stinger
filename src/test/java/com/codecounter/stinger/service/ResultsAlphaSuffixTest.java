package com.codecounter.stinger.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ResultsAlphaSuffixTest {

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @Test
    void secondSaveUsesNextAlpha(@TempDir Path tmpDir) throws IOException {
        // prepare analysis directory
        Path project = tmpDir.resolve("stngr-001");
        Files.createDirectories(project);
        Files.writeString(project.resolve("A.java"), "public class A{}\n");

        // configure test-local results root
        Path resultsRoot = tmpDir.resolve("code_counter_results");
        System.setProperty("stinger.results.dir", resultsRoot.toString());

        try {
            // create first results folder as if an earlier run created it: datePrefix + _a
            String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
            Path rootFolder = resultsRoot.resolve("stngr-001");
            Files.createDirectories(rootFolder.resolve(datePrefix + "_a"));

            // run saveAnalysisResults: the new folder should use _b
            Path out = fileAnalysisService.saveAnalysisResults(project.toString());
            String outName = out.getFileName().toString();
            assertTrue(outName.equals(datePrefix + "_b"), "Expected new results folder to use _b but was: " + outName);
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }
}
