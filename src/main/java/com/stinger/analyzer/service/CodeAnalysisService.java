package com.stinger.analyzer.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.stinger.analyzer.model.FileAnalysis;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class CodeAnalysisService {
    
    private final JavaParser javaParser;
    
    public CodeAnalysisService() {
        this.javaParser = new JavaParser();
    }
    
    public FileAnalysis analyzeFile(File file) {
        String fileName = file.getName();
        String filePath = file.getAbsolutePath();
        String extension = getFileExtension(fileName);
        
        FileAnalysis analysis = new FileAnalysis(fileName, filePath, extension);
        
        try {
            // Count lines
            int lineCount = countLines(file.toPath());
            analysis.setLineCount(lineCount);
            
            // Count methods for supported languages
            if ("java".equalsIgnoreCase(extension)) {
                int methodCount = countJavaMethods(file);
                analysis.setMethodCount(methodCount);
            } else {
                // For other languages, use a simple heuristic
                int methodCount = estimateMethodCount(file, extension);
                analysis.setMethodCount(methodCount);
            }
            
            analysis.setAnalyzed(true);
        } catch (Exception e) {
            analysis.setAnalyzed(false);
            analysis.setError(e.getMessage());
        }
        
        return analysis;
    }
    
    private int countLines(Path filePath) throws IOException {
        List<String> lines = Files.readAllLines(filePath);
        return lines.size();
    }
    
    private int countJavaMethods(File file) {
        try {
            CompilationUnit cu = javaParser.parse(file).getResult().orElse(null);
            if (cu != null) {
                return cu.findAll(MethodDeclaration.class).size();
            }
        } catch (Exception e) {
            // If parsing fails, fall back to estimation
        }
        return 0;
    }
    
    private int estimateMethodCount(File file, String extension) {
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            int methodCount = 0;
            
            // Simple heuristic based on language
            for (String line : lines) {
                String trimmed = line.trim();
                
                if (extension != null) {
                    switch (extension.toLowerCase()) {
                        case "py":
                            if (trimmed.startsWith("def ")) {
                                methodCount++;
                            }
                            break;
                        case "js", "ts", "jsx", "tsx":
                            if (trimmed.contains("function ") || 
                                trimmed.matches(".*\\w+\\s*[:=]\\s*\\([^)]*\\)\\s*=>.*") ||
                                trimmed.matches(".*\\w+\\s*\\([^)]*\\)\\s*\\{.*")) {
                                methodCount++;
                            }
                            break;
                        case "cpp", "c", "cs":
                            if (trimmed.matches(".*\\w+\\s+\\w+\\s*\\([^)]*\\)\\s*\\{?.*") &&
                                !trimmed.startsWith("//") && !trimmed.startsWith("/*")) {
                                methodCount++;
                            }
                            break;
                        case "go":
                            if (trimmed.startsWith("func ")) {
                                methodCount++;
                            }
                            break;
                        case "rb":
                            if (trimmed.startsWith("def ")) {
                                methodCount++;
                            }
                            break;
                        case "php":
                            if (trimmed.contains("function ")) {
                                methodCount++;
                            }
                            break;
                    }
                }
            }
            
            return methodCount;
        } catch (IOException e) {
            return 0;
        }
    }
    
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        
        return null;
    }
}
