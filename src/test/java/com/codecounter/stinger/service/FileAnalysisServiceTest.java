package com.codecounter.stinger.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

        // avoid saving into repo code_counter_results/ during tests
        String resultsDir = tempDir.resolve("code_counter_results").toString();
        System.setProperty("stinger.results.dir", resultsDir);
        try {
            AnalysisResult result = fileAnalysisService.analyzeDirectory(tempDir.toString());

        assertEquals(1, result.getTotalFolders());
        assertEquals(2, result.getTotalFiles());
        assertEquals(1, result.getTotalCodeFiles());
        assertEquals(1, result.getTotalDocFiles());
            assertTrue(result.getTotalLines() > 0);
            // new: validate the per-category line totals are present
            assertTrue(result.getTotalCodeLines() > 0, "expected code file line count > 0");
            assertTrue(result.getTotalDocLines() > 0, "expected document file line count > 0");
            assertTrue(result.getTotalMethods() >= 2);
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }

    @Test
    void testProtoClassifiedAndDockerIgnored(@TempDir Path tempDir) throws IOException {
        // Create a small Java file
        Path javaFile = tempDir.resolve("Main.java");
        Files.writeString(javaFile, "public class Main {}\n");

        // Create a .proto file (should be treated as document)
        Path protoFile = tempDir.resolve("schema.proto");
        Files.writeString(protoFile, "syntax = \"proto3\";\nmessage Test {}\n");

        // Create Docker-related files which should be ignored by analysis
        Files.writeString(tempDir.resolve("Dockerfile"), "FROM openjdk:17\n");
        Files.writeString(tempDir.resolve("docker-compose.yml"), "version: '3'\nservices:\n  app:\n    image: example\n");

        // Ensure analyzer saves into temp results dir
        String resultsDir = tempDir.resolve("code_counter_results").toString();
        System.setProperty("stinger.results.dir", resultsDir);
        try {
            AnalysisResult result = fileAnalysisService.analyzeDirectory(tempDir.toString());

            // Dockerfile and docker-compose.yml should be ignored -> only java and proto counted
            assertEquals(2, result.getTotalFiles());
            assertEquals(1, result.getTotalCodeFiles());
            assertEquals(1, result.getTotalDocFiles());
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

    @Test
    void testPersistentSummaryUpdate(@TempDir Path tempDir) throws IOException {
        // Setup project dir
        Path projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("Main.java"), "public class Main {}");

        // Setup results dir
        Path resultsDir = tempDir.resolve("results");
        System.setProperty("stinger.results.dir", resultsDir.toString());

        // Pre-create persistent summary.txt
        Path projectResultsDir = resultsDir.resolve("project");
        Files.createDirectories(projectResultsDir);
        Path summaryFile = projectResultsDir.resolve("summary.txt");
        Files.writeString(summaryFile, "existingKey: existingValue\ntotalFiles: 999\n");

        try {
            fileAnalysisService.analyzeDirectory(projectDir.toString());

            // Verify summary.txt
            List<String> lines = Files.readAllLines(summaryFile);
            
            // Should contain existing key
            assertTrue(lines.stream().anyMatch(l -> l.contains("existingKey: existingValue")));
            
            // Should contain updated totalFiles (1 file)
            assertTrue(lines.stream().anyMatch(l -> l.equals("totalFiles: 1")));
            
            // Should NOT contain old totalFiles
            assertTrue(lines.stream().noneMatch(l -> l.equals("totalFiles: 999")));

        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }

    @Test
    void testSummaryContainsTotals(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, "public class Test { void m() {} }");

        String resultsDir = tempDir.resolve("results").toString();
        System.setProperty("stinger.results.dir", resultsDir);
        try {
            AnalysisResult result = fileAnalysisService.analyzeDirectory(tempDir.toString());
            
            // Find the summary.txt
            // result.getResultsPath() should point to the output folder
            Path outFolder = Paths.get(result.getResultsPath());
            Path summary = outFolder.resolve("summary.txt");
            
            assertTrue(Files.exists(summary));
            List<String> lines = Files.readAllLines(summary);
            
            assertTrue(lines.stream().anyMatch(l -> l.startsWith("totalMethods:")), "summary.txt should contain totalMethods");
            assertTrue(lines.stream().anyMatch(l -> l.startsWith("totalCodeLines:")), "summary.txt should contain totalCodeLines");
            
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }
}
