package com.caveanimal.stinger.service;

import com.caveanimal.stinger.model.AnalysisResult;
import com.caveanimal.stinger.model.FileNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class FileAnalysisService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileAnalysisService.class);
    
    private static final Set<String> CODE_EXTENSIONS = new HashSet<>(Arrays.asList(
        "java", "js", "ts", "py", "cpp", "c", "h", "cs", "go", "rb", "php", 
        "swift", "kt", "rs", "scala", "sh", "bash", "ps1", "sql", "html", 
        "css", "jsx", "tsx", "vue", "xml", "json", "yaml", "yml"
    ));
    
    private static final Set<String> DOC_EXTENSIONS = new HashSet<>(Arrays.asList(
        "md", "txt", "rst", "adoc", "pdf", "doc", "docx"
    ));

    public List<FileNode> listDirectory(String dirPath) throws IOException {
        File dir = new File(dirPath);
        
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("Invalid directory path: " + dirPath);
        }
        
        List<FileNode> nodes = new ArrayList<>();
        File[] files = dir.listFiles();
        
        if (files != null) {
            for (File file : files) {
                if (!file.isHidden()) {
                    FileNode node = new FileNode(
                        file.getName(),
                        file.getAbsolutePath(),
                        file.isDirectory()
                    );
                    
                    if (!file.isDirectory()) {
                        String extension = getFileExtension(file.getName());
                        node.setType(classifyFile(extension));
                        node.setSize(file.length());
                    }
                    
                    nodes.add(node);
                }
            }
        }
        
        return nodes;
    }

    public AnalysisResult analyzeDirectory(String dirPath) throws IOException {
        AnalysisResult result = new AnalysisResult();
        result.setPath(dirPath);
        
        Path path = Paths.get(dirPath);
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new IOException("Invalid directory path: " + dirPath);
        }
        
        analyzeRecursively(path.toFile(), result);
        
        return result;
    }

    private void analyzeRecursively(File directory, AnalysisResult result) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isHidden()) continue;
            
            if (file.isDirectory()) {
                result.setTotalFolders(result.getTotalFolders() + 1);
                analyzeRecursively(file, result);
            } else {
                result.setTotalFiles(result.getTotalFiles() + 1);
                
                String extension = getFileExtension(file.getName());
                String fileType = classifyFile(extension);
                
                if ("code".equals(fileType)) {
                    result.setTotalCodeFiles(result.getTotalCodeFiles() + 1);
                    analyzeCodeFile(file, extension, result);
                } else if ("document".equals(fileType)) {
                    result.setTotalDocFiles(result.getTotalDocFiles() + 1);
                }
            }
        }
    }

    private void analyzeCodeFile(File file, String extension, AnalysisResult result) {
        try {
            long lineCount = countLines(file);
            result.setTotalLines(result.getTotalLines() + lineCount);
            
            if ("java".equals(extension)) {
                int methodCount = countJavaMethods(file);
                result.setTotalMethods(result.getTotalMethods() + methodCount);
            } else {
                int methodCount = estimateMethodCount(file, extension);
                result.setTotalMethods(result.getTotalMethods() + methodCount);
            }
        } catch (Exception e) {
            logger.warn("Failed to analyze file: {} - {}", file.getPath(), e.getMessage());
        }
    }

    private long countLines(File file) throws IOException {
        try (Stream<String> lines = Files.lines(file.toPath())) {
            return lines.count();
        }
    }

    private int countJavaMethods(File file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElse(null);
            if (cu != null) {
                return cu.findAll(MethodDeclaration.class).size();
            }
        } catch (Exception e) {
            logger.debug("Failed to parse Java file {}, falling back to estimation: {}", file.getPath(), e.getMessage());
        }
        return 0;
    }

    private int estimateMethodCount(File file, String extension) {
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            int methodCount = 0;
            
            for (String line : lines) {
                String trimmed = line.trim();
                
                if (extension.equals("js") || extension.equals("ts") || extension.equals("jsx") || extension.equals("tsx")) {
                    if (trimmed.startsWith("function ") || trimmed.contains(" function(") || 
                        trimmed.matches(".*\\w+\\s*\\([^)]*\\)\\s*\\{.*") ||
                        trimmed.matches(".*=>\\s*\\{.*")) {
                        methodCount++;
                    }
                } else if (extension.equals("py")) {
                    if (trimmed.startsWith("def ")) {
                        methodCount++;
                    }
                } else if (extension.equals("cpp") || extension.equals("c") || extension.equals("cs")) {
                    if (trimmed.matches(".*\\w+\\s+\\w+\\s*\\([^)]*\\)\\s*\\{.*") && 
                        !trimmed.startsWith("//") && !trimmed.startsWith("/*")) {
                        methodCount++;
                    }
                }
            }
            
            return methodCount;
        } catch (Exception e) {
            return 0;
        }
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    private String classifyFile(String extension) {
        if (CODE_EXTENSIONS.contains(extension)) {
            return "code";
        } else if (DOC_EXTENSIONS.contains(extension)) {
            return "document";
        }
        return "other";
    }
}
