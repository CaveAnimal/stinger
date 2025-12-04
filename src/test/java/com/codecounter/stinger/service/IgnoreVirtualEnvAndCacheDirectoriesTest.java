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
class IgnoreVirtualEnvAndCacheDirectoriesTest {

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @Test
    void virtualenvDirsAreIgnored(@TempDir Path tmpDir) throws IOException {
        Path project = tmpDir.resolve("project");
        Files.createDirectories(project);

        // a few directories that should be ignored
        Files.createDirectories(project.resolve(".venv/Lib/site-packages"));
        Files.createDirectories(project.resolve("venv/lib"));
        Files.createDirectories(project.resolve("venv2/lib"));
        Files.createDirectories(project.resolve("env/lib"));
        Files.createDirectories(project.resolve("__pycache__"));
        Files.createDirectories(project.resolve(".venv2/lib"));
        Files.createDirectories(project.resolve("node_modules/some_module"));

        // create files inside those directories that should be ignored
        Files.writeString(project.resolve(".venv/Lib/site-packages/hidden.py"), "print('x')");
        Files.writeString(project.resolve("venv/lib/hidden2.py"), "print('y')");
        Files.writeString(project.resolve("venv2/lib/hidden2b.py"), "print('y2')");
        Files.writeString(project.resolve("env/lib/hidden3.py"), "print('z')");
        Files.writeString(project.resolve("__pycache__/cached.pyc"), "binary");
        Files.writeString(project.resolve(".venv2/lib/hiddenv2.py"), "print('v2')");
        Files.writeString(project.resolve("node_modules/some_module/index.js"), "console.log('x')");

        // create a visible file that should be included
        Files.writeString(project.resolve("Included.java"), "public class Included {}\n");

        System.setProperty("stinger.results.dir", tmpDir.resolve("code_counter_results").toString());
        try {
            AnalysisResult result = fileAnalysisService.analyzeDirectory(project.toString());

            // Only the one included file should be counted
            assertEquals(1, result.getTotalFiles(), "Files under virtualenv/cache folders must be ignored");

            Path saved = Path.of(result.getResultsPath());
            List<String> totalFiles = Files.readAllLines(saved.resolve("total_files.txt"));
            assertFalse(totalFiles.stream().anyMatch(s -> s.contains(".venv") || s.contains("venv") || s.contains("venv2") || s.contains(".venv2") || s.contains("env") || s.contains("__pycache__") || s.contains("node_modules")));
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }
}
