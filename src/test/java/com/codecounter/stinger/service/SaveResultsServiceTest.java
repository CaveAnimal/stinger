package com.codecounter.stinger.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.codecounter.stinger.model.AnalysisResult;

@SpringBootTest
class SaveResultsServiceTest {

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @Test
    void saveResultsCreatesFiles(@TempDir Path tmpDir) throws IOException {
        // Create structure
        Path sub = tmpDir.resolve("subdir");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("Test.java"), "public class Test { void m(){} }");
        Files.writeString(tmpDir.resolve("README.md"), "# test");
        Files.writeString(tmpDir.resolve("data.bin"), "bin");

        // avoid writing into repo code_counter_results/ during tests
        String resultsDir = tmpDir.resolve("code_counter_results").toString();
        System.setProperty("stinger.results.dir", resultsDir);
        try {
            Path out = fileAnalysisService.saveAnalysisResults(tmpDir.toString());
            assertTrue(Files.exists(out));
            
            assertTrue(Files.exists(out.resolve("folders.txt")));
            assertTrue(Files.exists(out.resolve("total_files.txt")));
            assertTrue(Files.exists(out.resolve("code_files.txt")));
            assertTrue(Files.exists(out.resolve("document_files.txt")));
            assertTrue(Files.exists(out.resolve("other_files.txt")));

        List<String> code = Files.readAllLines(out.resolve("code_files.txt"));
        assertTrue(code.stream().anyMatch(s -> s.endsWith("Test.java")));

        List<String> docs = Files.readAllLines(out.resolve("document_files.txt"));
        assertTrue(docs.stream().anyMatch(s -> s.endsWith("README.md")));

        List<String> other = Files.readAllLines(out.resolve("other_files.txt"));
        // data.bin is now ignored by extension, and "other" files are ignored by policy
        assertTrue(other.isEmpty());

        // summary.txt should include total lines metadata for code and documents and standard keys
        List<String> summaryLines = Files.readAllLines(out.resolve("summary.txt"));
        List<String> requiredKeys = List.of(
            "root:", "createdAt:", "folders:", "totalFiles:", "codeFiles:", "docFiles:", "otherFiles:",
            "totalCodeLines:", "totalDocLines:", "totalLines:"
        );

        for (String key : requiredKeys) {
            assertTrue(summaryLines.stream().anyMatch(s -> s.startsWith(key)), "summary.txt must contain key '" + key + "'");
        }

            // Also verify analyzeDirectory returns a resultsPath
            AnalysisResult result = fileAnalysisService.analyzeDirectory(tmpDir.toString());
            assertNotNull(result.getResultsPath());
            assertTrue(Files.exists(Path.of(result.getResultsPath())));
        } finally {
            System.clearProperty("stinger.results.dir");
        }
    }
}
