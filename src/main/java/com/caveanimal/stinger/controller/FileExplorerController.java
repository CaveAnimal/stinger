package com.caveanimal.stinger.controller;

import com.caveanimal.stinger.model.AnalysisResult;
import com.caveanimal.stinger.model.FileNode;
import com.caveanimal.stinger.service.FileAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FileExplorerController {

    @Autowired
    private FileAnalysisService fileAnalysisService;

    @GetMapping("/list")
    public ResponseEntity<?> listDirectory(@RequestParam String path) {
        try {
            List<FileNode> nodes = fileAnalysisService.listDirectory(path);
            Map<String, Object> response = new HashMap<>();
            response.put("path", path);
            response.put("files", nodes);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeDirectory(@RequestBody Map<String, String> request) {
        try {
            String path = request.get("path");
            AnalysisResult result = fileAnalysisService.analyzeDirectory(path);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
