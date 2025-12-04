package com.codecounter.stinger.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.codecounter.stinger.model.AnalysisResult;

@SpringBootTest
class BinaryFileHandlingTest {

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @Test
    void analyzeDirectoryDoesNotBreakOnBinaryFile(@TempDir Path tmpDir) throws IOException {
        Path project = tmpDir.resolve("project");
        Files.createDirectories(project);

        // create a small 'binary' file with malformed single-byte sequence (invalid UTF-8)
        byte[] bad = new byte[] {(byte)0xC3};
        Files.write(project.resolve("weird.bin"), bad);

        // create a normal file that should be included
        Files.writeString(project.resolve("Included.java"), "public class Included {}\n");

        System.setProperty("stinger.results.dir", tmpDir.resolve("code_counter_results").toString());
        try {
            // should not throw even though file has malformed encoding
            AnalysisResult result = assertDoesNotThrow(() -> fileAnalysisService.analyzeDirectory(project.toString()));
            // both files should be counted (binary included as an 'other' file)
            assertEquals(2, result.getTotalFiles());
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }
}
