package com.stinger.analyzer.controller;

import com.stinger.analyzer.model.AnalysisResult;
import com.stinger.analyzer.model.FileNode;
import com.stinger.analyzer.service.FileSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FileSystemController {
    
    @Autowired
    private FileSystemService fileSystemService;
    
    @GetMapping("/list")
    public ResponseEntity<?> listDirectory(@RequestParam(required = false) String path) {
        try {
            if (path == null || path.isEmpty()) {
                path = System.getProperty("user.home");
            }
            
            List<FileNode> files = fileSystemService.listDirectory(path);
            
            Map<String, Object> response = new HashMap<>();
            response.put("path", path);
            response.put("files", files);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    @GetMapping("/tree")
    public ResponseEntity<?> getFileTree(
            @RequestParam(required = false) String path,
            @RequestParam(defaultValue = "3") int maxDepth) {
        try {
            if (path == null || path.isEmpty()) {
                path = System.getProperty("user.home");
            }
            
            FileNode tree = fileSystemService.buildFileTree(path, maxDepth);
            
            return ResponseEntity.ok(tree);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    @GetMapping("/analyze")
    public ResponseEntity<?> analyzeDirectory(@RequestParam(required = false) String path) {
        try {
            if (path == null || path.isEmpty()) {
                path = System.getProperty("user.home");
            }
            
            AnalysisResult result = fileSystemService.analyzeDirectory(path);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    @GetMapping("/roots")
    public ResponseEntity<?> getRoots() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("home", System.getProperty("user.home"));
            response.put("userDir", System.getProperty("user.dir"));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
