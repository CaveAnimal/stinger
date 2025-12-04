package com.caveanimal.stinger.controller;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
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

    // Executor for background streaming tasks. Bounded so we don't spawn unlimited threads.
    private final ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));

    // Keep SSE connections alive for up to 10 minutes for long running analyses
    private static final long SSE_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);

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
        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // Submit to executor instead of creating ad-hoc threads
        executor.submit(() -> {
            try {
                // send starting event (best-effort)
                safeSend(emitter, SseEmitter.event().name("start").data("Starting analysis of: " + path));

                // run analysis — the service will now throw if sending fails and the emitter is closed
                fileAnalysisService.analyzeDirectoryStream(path, emitter);

                safeSend(emitter, SseEmitter.event().name("done").data("Analysis complete"));
                emitter.complete();
            } catch (Exception e) {
                logger.error("Streaming analysis failed for {}: {}", path, e.getMessage());
                // best-effort notify client
                safeSend(emitter, SseEmitter.event().name("error").data(e.getMessage()));
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    logger.debug("Failed to completeWithError on emitter: {}", ex.getMessage());
                }
            }
        });

        return emitter;
    }

    private boolean safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException e) {
            // emitter likely closed; treat as transient and stop attempts
            logger.debug("Emitter failed to send event — skipping further sends: {}", e.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void shutdownExecutor() {
        try {
            executor.shutdownNow();
        } catch (Exception e) {
            logger.debug("Error shutting down executor: {}", e.getMessage());
        }
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
