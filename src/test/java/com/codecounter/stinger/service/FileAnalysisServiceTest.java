package com.codecounter.stinger.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.codecounter.stinger.model.AnalysisResult;
import com.codecounter.stinger.model.FileNode;

@SpringBootTest
class FileAnalysisServiceTest {

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @Test
    void testListDirectory(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("test.java"));
        Files.createFile(tempDir.resolve("README.md"));
        Files.createDirectory(tempDir.resolve("subdir"));

        List<FileNode> nodes = fileAnalysisService.listDirectory(tempDir.toString());

        assertEquals(3, nodes.size());
        assertTrue(nodes.stream().anyMatch(n -> n.getName().equals("test.java")));
        assertTrue(nodes.stream().anyMatch(n -> n.getName().equals("README.md")));
        assertTrue(nodes.stream().anyMatch(n -> n.getName().equals("subdir") && n.isDirectory()));
    }

    @Test
    void testAnalyzeDirectory(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, """
            public class Test {
                public void method1() {
                    System.out.println("Hello");
                }
                
                public void method2() {
                    System.out.println("World");
                }
            }
            """);

        Path subdir = Files.createDirectory(tempDir.resolve("subdir"));
        Files.writeString(subdir.resolve("README.md"), "# Documentation\n\nSome content");

        // avoid saving into repo results/ during tests
        String resultsDir = tempDir.resolve("results").toString();
        System.setProperty("stinger.results.dir", resultsDir);
        try {
            AnalysisResult result = fileAnalysisService.analyzeDirectory(tempDir.toString());

        assertEquals(1, result.getTotalFolders());
        assertEquals(2, result.getTotalFiles());
        assertEquals(1, result.getTotalCodeFiles());
        assertEquals(1, result.getTotalDocFiles());
        assertTrue(result.getTotalLines() > 0);
            assertTrue(result.getTotalMethods() >= 2);
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }

    @Test
    void testInvalidDirectory() {
        assertThrows(IOException.class, () -> {
            fileAnalysisService.listDirectory("/nonexistent/path/that/does/not/exist");
        });
    }
}
