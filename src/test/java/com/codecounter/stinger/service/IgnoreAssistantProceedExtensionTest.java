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
class IgnoreAssistantProceedExtensionTest {

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @Test
    void assistantProceedExtensionFolderIgnored(@TempDir Path tmpDir) throws IOException {
        Path project = tmpDir.resolve("project");
        Files.createDirectories(project);

        // create assistant-proceed-extension folder with a file inside
        Files.createDirectories(project.resolve("assistant-proceed-extension/subdir"));
        Files.writeString(project.resolve("assistant-proceed-extension/subdir/hidden.txt"), "secret");

        // a visible file
        Files.writeString(project.resolve("Included.java"), "public class Included {}\n");

        System.setProperty("stinger.results.dir", tmpDir.resolve("code_counter_results").toString());
        try {
            AnalysisResult result = fileAnalysisService.analyzeDirectory(project.toString());
            assertEquals(1, result.getTotalFiles(), "assistant-proceed-extension must be ignored");
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }
}
