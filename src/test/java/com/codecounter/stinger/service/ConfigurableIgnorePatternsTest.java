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
class ConfigurableIgnorePatternsTest {

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @Test
    void customIgnorePatternsApplied(@TempDir Path tmpDir) throws IOException {
        // ensure system property is respected at runtime
        System.setProperty("stinger.ignore.dirs", "my_ignore, prefix*");

        Path project = tmpDir.resolve("project");
        Files.createDirectories(project);

        // things that should be ignored due to the property
        Files.createDirectories(project.resolve("my_ignore/subdir"));
        Files.createDirectories(project.resolve("prefixX/subdir"));

        // a file that should still be processed
        Files.writeString(project.resolve("Good.java"), "public class Good {}\n");

        try {
            AnalysisResult result = fileAnalysisService.analyzeDirectory(project.toString());
            assertEquals(1, result.getTotalFiles());
        } finally {
            System.clearProperty("stinger.ignore.dirs");
        }
    }
}
