package com.stinger.analyzer.service;

import com.stinger.analyzer.model.AnalysisResult;
import com.stinger.analyzer.model.FileAnalysis;
import com.stinger.analyzer.model.FileNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class FileSystemService {
    
    @Autowired
    private FileClassificationService classificationService;
    
    @Autowired
    private CodeAnalysisService codeAnalysisService;
    
    public List<FileNode> listDirectory(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        
        if (!directory.exists()) {
            throw new IOException("Directory does not exist: " + directoryPath);
        }
        
        if (!directory.isDirectory()) {
            throw new IOException("Path is not a directory: " + directoryPath);
        }
        
        List<FileNode> nodes = new ArrayList<>();
        File[] files = directory.listFiles();
        
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> {
                // Directories first, then files
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });
            
            for (File file : files) {
                // Skip hidden files
                if (file.getName().startsWith(".")) {
                    continue;
                }
                
                FileNode node = new FileNode(
                    file.getName(),
                    file.getAbsolutePath(),
                    file.isDirectory()
                );
                
                if (!file.isDirectory()) {
                    node.setType(classificationService.classifyFile(file.getName()));
                    node.setSize(file.length());
                }
                
                nodes.add(node);
            }
        }
        
        return nodes;
    }
    
    public FileNode buildFileTree(String directoryPath, int maxDepth) throws IOException {
        File rootFile = new File(directoryPath);
        
        if (!rootFile.exists()) {
            throw new IOException("Path does not exist: " + directoryPath);
        }
        
        return buildFileTreeRecursive(rootFile, maxDepth, 0);
    }
    
    private FileNode buildFileTreeRecursive(File file, int maxDepth, int currentDepth) {
        FileNode node = new FileNode(
            file.getName(),
            file.getAbsolutePath(),
            file.isDirectory()
        );
        
        if (!file.isDirectory()) {
            node.setType(classificationService.classifyFile(file.getName()));
            node.setSize(file.length());
            return node;
        }
        
        // Don't go deeper than maxDepth
        if (currentDepth >= maxDepth) {
            return node;
        }
        
        File[] children = file.listFiles();
        if (children != null) {
            Arrays.sort(children, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });
            
            for (File child : children) {
                // Skip hidden files
                if (child.getName().startsWith(".")) {
                    continue;
                }
                
                FileNode childNode = buildFileTreeRecursive(child, maxDepth, currentDepth + 1);
                node.addChild(childNode);
            }
        }
        
        return node;
    }
    
    public AnalysisResult analyzeDirectory(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        
        if (!directory.exists()) {
            throw new IOException("Path does not exist: " + directoryPath);
        }
        
        if (!directory.isDirectory()) {
            throw new IOException("Path is not a directory: " + directoryPath);
        }
        
        AnalysisResult result = new AnalysisResult(directoryPath);
        analyzeDirectoryRecursive(directory, result);
        return result;
    }
    
    private void analyzeDirectoryRecursive(File directory, AnalysisResult result) {
        File[] files = directory.listFiles();
        
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            // Skip hidden files
            if (file.getName().startsWith(".")) {
                continue;
            }
            
            if (file.isDirectory()) {
                result.incrementFolders();
                analyzeDirectoryRecursive(file, result);
            } else {
                result.incrementFiles();
                
                String fileType = classificationService.classifyFile(file.getName());
                
                switch (fileType) {
                    case "code":
                        result.incrementCodeFiles();
                        // Analyze code files
                        try {
                            FileAnalysis analysis = codeAnalysisService.analyzeFile(file);
                            if (analysis.isAnalyzed()) {
                                result.addMethods(analysis.getMethodCount());
                                result.addLines(analysis.getLineCount());
                            }
                        } catch (Exception e) {
                            // Skip files that can't be analyzed
                        }
                        break;
                    case "document":
                        result.incrementDocumentFiles();
                        break;
                    default:
                        result.incrementOtherFiles();
                        break;
                }
            }
        }
    }
}
