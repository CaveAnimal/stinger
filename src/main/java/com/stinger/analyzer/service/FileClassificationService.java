package com.stinger.analyzer.service;

import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class FileClassificationService {
    
    private static final Set<String> CODE_EXTENSIONS = Set.of(
        "java", "py", "js", "ts", "jsx", "tsx", "cpp", "c", "h", "hpp",
        "cs", "go", "rb", "php", "swift", "kt", "rs", "scala", "groovy",
        "sh", "bash", "ps1", "sql", "r", "m", "dart", "lua", "perl", "pl"
    );
    
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
        "md", "txt", "pdf", "doc", "docx", "odt", "rtf",
        "html", "htm", "xml", "json", "yaml", "yml", "toml",
        "csv", "xls", "xlsx", "ppt", "pptx"
    );
    
    public String classifyFile(String fileName) {
        String extension = getFileExtension(fileName);
        
        if (extension == null || extension.isEmpty()) {
            return "other";
        }
        
        String lowerExtension = extension.toLowerCase();
        
        if (CODE_EXTENSIONS.contains(lowerExtension)) {
            return "code";
        } else if (DOCUMENT_EXTENSIONS.contains(lowerExtension)) {
            return "document";
        } else {
            return "other";
        }
    }
    
    public boolean isCodeFile(String fileName) {
        return "code".equals(classifyFile(fileName));
    }
    
    public boolean isDocumentFile(String fileName) {
        return "document".equals(classifyFile(fileName));
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
