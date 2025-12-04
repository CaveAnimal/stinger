package com.codecounter.stinger.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.codecounter.stinger.model.AnalysisResult;

@SpringBootTest
class IgnoreBakFilesTest {

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @Test
    void bakFilesAreIgnored(@TempDir Path tmpDir) throws IOException {
        Path project = tmpDir.resolve("project");
        Files.createDirectories(project);

        // file with .bak extension should be ignored
        Files.writeString(project.resolve("old.bak"), "backup");

        // a normal file that should be included
        Files.writeString(project.resolve("Included.java"), "public class Included {}\n");

        System.setProperty("stinger.results.dir", tmpDir.resolve("code_counter_results").toString());
        try {
            AnalysisResult result = fileAnalysisService.analyzeDirectory(project.toString());
            assertEquals(1, result.getTotalFiles(), ".bak files should be ignored during analysis");
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }
}
