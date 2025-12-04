package com.codecounter.stinger.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import com.codecounter.stinger.service.FileAnalysisService;

@SpringBootTest
class FileExplorerControllerPathTest {

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @Autowired
    private FileExplorerController fileExplorerController;

    @Test
    void testCurrentPathPreservesBackslashes(@TempDir Path tempDir) throws IOException {
        // Create a subdirectory
        Path subdir = tempDir.resolve("subdir");
        File dirFile = subdir.toFile();
        dirFile.mkdir();

        // Use Windows-style path
        String winPath = subdir.toString().replace("/", "\\");
        ResponseEntity<?> response = fileExplorerController.listDirectory(winPath);
        assertEquals(200, response.getStatusCodeValue());
        Map<?,?> body = (Map<?,?>) response.getBody();
        assertNotNull(body);
        assertEquals(winPath, body.get("path"));
    }
}
