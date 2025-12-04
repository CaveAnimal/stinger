package com.codecounter.stinger.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.codecounter.stinger.model.AnalysisResult;
import com.codecounter.stinger.model.FileNode;
import com.codecounter.stinger.service.FileAnalysisService;

@RestController
@RequestMapping("/api")
public class FileExplorerController {

    private static final Logger logger = LoggerFactory.getLogger(FileExplorerController.class);

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @GetMapping("/list")
    public ResponseEntity<?> listDirectory(@RequestParam String path) {
        logger.info("Received request to list directory: {}", path);
        try {
            List<FileNode> nodes = fileAnalysisService.listDirectory(path);
            Map<String, Object> response = new HashMap<>();
            response.put("path", path);
            response.put("files", nodes);
            logger.info("Returning {} files for path {}", nodes.size(), path);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("Error listing directory {}: {}", path, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeDirectory(@RequestBody Map<String, String> request) {
        String path = request.get("path");
        logger.info("Received request to analyze directory: {}", path);
        try {
            AnalysisResult result = fileAnalysisService.analyzeDirectory(path);
            logger.info("Analysis complete for path {}", path);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            logger.error("Error analyzing directory {}: {}", path, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/analyze-stream")
    public SseEmitter analyzeDirectoryStream(@RequestParam String path) {
        final SseEmitter emitter = new SseEmitter(0L); // no timeout

        // Perform analysis in background thread and stream progress events
        new Thread(() -> {
            try {
                // send starting event
                emitter.send(SseEmitter.event().name("start").data("Starting analysis of: " + path));
                fileAnalysisService.analyzeDirectoryStream(path, emitter);
                emitter.send(SseEmitter.event().name("done").data("Analysis complete"));
                emitter.complete();
            } catch (Exception e) {
                try {
                    logger.error("Streaming analysis failed for {}: {}", path, e.getMessage());
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (Exception ex) {
                    logger.error("Failed to send SSE error: {}", ex.getMessage());
                }
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    @PostMapping("/save-results")
    public ResponseEntity<?> saveResults(@RequestBody Map<String, String> request) {
        String path = request.get("path");
        try {
            Path out = fileAnalysisService.saveAnalysisResults(path);
            Map<String, String> resp = new HashMap<>();
            resp.put("resultsPath", out.toString());
            return ResponseEntity.ok(resp);
        } catch (IOException e) {
            logger.error("Failed to save results for {}: {}", path, e.getMessage());
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }
}
