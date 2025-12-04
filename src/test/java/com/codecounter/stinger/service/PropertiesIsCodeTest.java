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
class PropertiesIsCodeTest {

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @Test
    void propertiesFilesCountAsCode(@TempDir Path tmpDir) throws IOException {
        Path project = tmpDir.resolve("project");
        Files.createDirectories(project);

        // properties file that should be counted as code
        Files.writeString(project.resolve("app.properties"), "some.value=1\n");

        System.setProperty("stinger.results.dir", tmpDir.resolve("results").toString());
        try {
            AnalysisResult result = fileAnalysisService.analyzeDirectory(project.toString());

            assertEquals(1, result.getTotalFiles(), "properties should be counted as code files");
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }
}
