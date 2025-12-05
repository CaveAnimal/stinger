package com.codecounter.stinger.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.codecounter.stinger.model.AnalysisResult;
import com.codecounter.stinger.model.FileNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

@Service
public class FileAnalysisService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileAnalysisService.class);
    
    private static final Set<String> CODE_EXTENSIONS = new HashSet<>(Arrays.asList(
        "java", "js", "ts", "py", "cpp", "cc", "c", "h", "cs", "go", "rb", "php", 
        "swift", "kt", "rs", "scala", "sh", "bash", "ps1", "sql", "html", 
        "css", "jsx", "tsx", "vue", "xml", "json", "yaml", "yml", "properties"
    ));
    private static final Set<String> DOC_EXTENSIONS = new HashSet<>(Arrays.asList(
        "md", "txt", "rst", "adoc", "pdf", "doc", "docx", "proto"
    ));

    // directories to ignore entirely (case-insensitive)
    private static final Set<String> IGNORED_DIR_NAMES = new HashSet<>(Arrays.asList(
        "target", ".github", ".idea", ".vscode", "code_counter_results", "data",
        // typical virtualenv / Python cache folders and site-packages
        ".venv", "venv", "env", "__pycache__", "site-packages"
    ));
    
    // other commonly ignored folders
    static {
        IGNORED_DIR_NAMES.add(".husky");
        IGNORED_DIR_NAMES.add(".pytest_cache");
        IGNORED_DIR_NAMES.add("chroma");
        // keep chroma_data ignored too (was previously removed accidentally)
        IGNORED_DIR_NAMES.add("chroma_data");
        IGNORED_DIR_NAMES.add("lucene-indices");
        IGNORED_DIR_NAMES.add(".cache");
        IGNORED_DIR_NAMES.add("models");
        // ignore assistant-proceed-extension workspace/tooling folder
        IGNORED_DIR_NAMES.add("assistant-proceed-extension");
        // user-requested directories to ignore
        IGNORED_DIR_NAMES.add("benchmarks");
        IGNORED_DIR_NAMES.add("test-data-indexmgr");
        IGNORED_DIR_NAMES.add("embedding_service");
    }

    // some directories are better matched by pattern/prefix than exact name
    private static final String[] IGNORED_DIR_PREFIXES = new String[] {
        ".venv", // .venv, .venv2, .venv-old
        "venv",  // venv, venv2
    };

    private static final Set<String> IGNORED_DIR_EXACT = new HashSet<>(Arrays.asList(
        "node_modules"
    ));

    private boolean isIgnoredDirectoryName(String rawName) {
        if (rawName == null) return false;
        String name = rawName.toLowerCase();

        if (IGNORED_DIR_NAMES.contains(name)) return true;
        if (IGNORED_DIR_EXACT.contains(name)) return true;

        for (String prefix : IGNORED_DIR_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }

        // allow user to add additional ignore patterns via system property
        // comma-separated patterns, supports exact names or suffix '*' wildcard, e.g. 'build,temp*,node_modules'
        String extra = System.getProperty("stinger.ignore.dirs", "");
        if (extra != null && !extra.isBlank()) {
            for (String raw : extra.split(",")) {
                String p = raw.trim().toLowerCase();
                if (p.isEmpty()) continue;
                if (p.endsWith("*")) {
                    String pref = p.substring(0, p.length() - 1);
                    if (name.startsWith(pref)) return true;
                } else {
                    if (name.equals(p)) return true;
                }
            }
        }

        return false;
    }

    // file extensions to ignore completely (case-insensitive)
    private static final Set<String> IGNORED_FILE_EXTENSIONS = new HashSet<>(Arrays.asList(
        "idx", "db", "iml", "log", "bak", "bat", "ttf",
        // common image file extensions to exclude from analysis
        "png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "ico", "tif", "tiff", "heic", "avif", "eps", "psd"
    ));

    // specific file names to ignore (case-insensitive)
    private static final Set<String> IGNORED_FILE_NAMES = new HashSet<>(Arrays.asList(
        ".gitignore",
        ".gitkeep",
        ".env.example",
        "chroma_config.env",
        ".eslintrc.cjs",
        ".eslintrc.cjs",
        ".prettierignore",
        "org.junit.jupiter.api.extension.Extension",
        // some services file names are the fully-qualified class name, store lowercase form too so matching is case-insensitive
        "org.junit.jupiter.api.extension.extension",
        // exclude some platform/test helper files and common UI font assets by exact name
        "setup_llm.bat",
        "start_llm_service.bat",
        "start_llm_service_mistral.bat",
        "view_llm_logs.bat",
        "codicon-dcmgc-ay.ttf"
    ));

    // configurable base results dir. Tests may set system / spring property `stinger.results.dir`
    // to point to a temporary location so tests do not write into the repo's ./code_counter_results/ folder.
    private final String resultsDirProperty;

    public FileAnalysisService(@Value("${stinger.results.dir:code_counter_results}") String resultsDirProperty) {
        this.resultsDirProperty = resultsDirProperty;
    }

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

    /**
     * Save detailed analysis lists (folders, all files, code files, documents, other files)
    * to the ./code_counter_results/<sanitized-root>/<YYYY_MM_DD>_alpha/ directory under application working directory.
     * Returns the path to the created results folder.
     */
    public Path saveAnalysisResults(String dirPath) throws IOException {
        Path root = Paths.get(dirPath);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IOException("Invalid directory path: " + dirPath);
        }

        // produce collections while walking
        Set<String> visited = new HashSet<>();
        List<String> folders = new ArrayList<>();
        List<String> allFiles = new ArrayList<>();
        List<String> codeFiles = new ArrayList<>();
        List<String> docFiles = new ArrayList<>();
        List<String> otherFiles = new ArrayList<>();

        // walk with protection against loops
        long walkStart = System.currentTimeMillis();
        walkAndCollect(root.toFile(), visited, folders, allFiles, codeFiles, docFiles, otherFiles);
        long walkElapsed = System.currentTimeMillis() - walkStart;
        logger.debug("walkAndCollect finished for {} — folders={}, totalFiles={}, codeFiles={}, docFiles={}, otherFiles={}, elapsed={}ms", dirPath, folders.size(), allFiles.size(), codeFiles.size(), docFiles.size(), otherFiles.size(), walkElapsed);

        // Build results directory structure
        // Respect configurable results dir (may be set by tests via a Spring property or runtime System property)
        String runtimeOverride = System.getProperty("stinger.results.dir");
        Path resultsRoot = runtimeOverride != null && !runtimeOverride.isEmpty() ? Paths.get(runtimeOverride) : Paths.get(resultsDirProperty);
        if (!resultsRoot.isAbsolute()) {
            Path appRoot = Paths.get(new File(".").getCanonicalPath());
            resultsRoot = appRoot.resolve(resultsDirProperty);
        }
        Files.createDirectories(resultsRoot);

        // Use last folder name from path (not the complete path), then sanitize
        Path inputPath = Paths.get(dirPath);
        String lastName = inputPath.getFileName() != null ? inputPath.getFileName().toString() : inputPath.toString();
        String sanitized = sanitizePathForFolder(lastName);
        Path rootFolder = resultsRoot.resolve(sanitized);
        Files.createDirectories(rootFolder);

        // date folder with alpha suffix
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
        String alpha = nextAlphaSuffix(rootFolder, datePrefix); // now always returns a non-empty alpha (e.g., 'a')
        Path outFolder = rootFolder.resolve(datePrefix + "_" + alpha);
        Files.createDirectories(outFolder);

        // produce output files
        long writeStart = System.currentTimeMillis();
        writeList(outFolder.resolve("folders.txt"), folders);
        writeList(outFolder.resolve("total_files.txt"), allFiles);
        writeList(outFolder.resolve("code_files.txt"), codeFiles);
        writeList(outFolder.resolve("document_files.txt"), docFiles);
        writeList(outFolder.resolve("other_files.txt"), otherFiles);

        // Also write a small meta file
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("root", dirPath);
        meta.put("createdAt", LocalDate.now().toString());
        meta.put("folders", String.valueOf(folders.size()));
        meta.put("totalFiles", String.valueOf(allFiles.size()));
        meta.put("codeFiles", String.valueOf(codeFiles.size()));
        meta.put("docFiles", String.valueOf(docFiles.size()));
        meta.put("otherFiles", String.valueOf(otherFiles.size()));
        writeKeyValue(outFolder.resolve("summary.txt"), meta);

        long writeElapsed = System.currentTimeMillis() - writeStart;
        logger.info("Saved analysis results to {} (files={}, elapsedMs={})", outFolder.toString(), allFiles.size(), writeElapsed);
        return outFolder;
    }

    private void writeList(Path p, List<String> items) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            for (String s : items) {
                w.write(s);
                w.newLine();
            }
        }
    }

    private void writeKeyValue(Path p, Map<String, String> map) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                w.write(e.getKey() + ": " + e.getValue());
                w.newLine();
            }
        }
    }

    private String sanitizePathForFolder(String path) {
        String sanitized = path.replaceAll("[\\\\/:*?\"<>|]", "_");
        // shorten if too long
        if (sanitized.length() > 120) sanitized = sanitized.substring(0, 120);
        return sanitized;
    }

    private String nextAlphaSuffix(Path rootFolder, String datePrefix) throws IOException {
        // find existing directories that start with datePrefix
        // if folder doesn't exist yet, first suffix should be 'a'
        if (!Files.exists(rootFolder)) return "a";

        // collect suffixes for entries matching either 'datePrefix' or 'datePrefix_<suffix>'
        List<String> suffixes = Files.list(rootFolder)
                .filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .filter(n -> n.equals(datePrefix) || n.startsWith(datePrefix + "_"))
                .map(n -> {
                    if (n.equals(datePrefix)) return ""; // base folder with no suffix
                    return n.substring((datePrefix + "_").length());
                })
                .sorted()
                .collect(Collectors.toList());

        if (suffixes.isEmpty()) return "a";

        // if the most recent entry has no suffix (i.e. datePrefix present but no suffix), next should be 'a'
        String last = suffixes.get(suffixes.size() - 1);
        if (last == null || last.isEmpty()) return "a";
        return incrementAlpha(last);
    }

    private String incrementAlpha(String s) {
        // treat string as base-26 with 'a'..'z'
        StringBuilder sb = new StringBuilder(s);
        int pos = sb.length() - 1;
        while (pos >= 0) {
            char c = sb.charAt(pos);
            if (c == 'z') {
                sb.setCharAt(pos, 'a');
                pos--;
            } else {
                sb.setCharAt(pos, (char) (c + 1));
                return sb.toString();
            }
        }
        // all z -> prepend 'a'
        return "a" + sb.toString();
    }

    private void walkAndCollect(File directory,
                                Set<String> visited,
                                List<String> folders,
                                List<String> allFiles,
                                List<String> codeFiles,
                                List<String> docFiles,
                                List<String> otherFiles) {
        // tracking time for per-directory operations (if needed)
        try {
            String canonical = directory.getCanonicalPath();
            if (visited.contains(canonical)) return;
            visited.add(canonical);
            folders.add(canonical);
        } catch (IOException e) {
            // fallback
            folders.add(directory.getAbsolutePath());
        }

        File[] files = directory.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isHidden()) continue;
            try {
                String fcanon = f.getCanonicalPath();
                if (visited.contains(fcanon)) continue;
                if (f.isDirectory()) {
                    if (isIgnoredDirectoryName(f.getName())) {
                        logger.debug("Skipping nested ignored directory: {}", f.getAbsolutePath());
                        continue;
                    }
                    walkAndCollect(f, visited, folders, allFiles, codeFiles, docFiles, otherFiles);
                } else {
                    // skip files with ignored extensions (e.g. .idx .db)
                    String _ext = getFileExtension(f.getName());
                    if (_ext != null && IGNORED_FILE_EXTENSIONS.contains(_ext)) {
                        logger.debug("Skipping ignored file by extension: {}", f.getAbsolutePath());
                        continue;
                    }
                    // ignore certain well-known filenames (e.g. .gitignore/.gitkeep/.env.example)
                    if (isIgnoredFileName(f.getName())) {
                        logger.debug("Skipping ignored filename: {}", f.getAbsolutePath());
                        continue;
                    }
                    visited.add(fcanon);
                    allFiles.add(fcanon);
                    String ext = getFileExtension(f.getName());
                    String type = classifyFile(ext);
                    switch (type) {
                        case "code": codeFiles.add(fcanon); break;
                        case "document": docFiles.add(fcanon); break;
                        default: otherFiles.add(fcanon); break;
                    }
                }
            } catch (IOException ioe) {
                // if canonicalization fails, still process
                String absolute = f.getAbsolutePath();
                if (!visited.contains(absolute)) {
                    if (f.isDirectory()) {
                        walkAndCollect(f, visited, folders, allFiles, codeFiles, docFiles, otherFiles);
                    } else {
                        // ignore .gitignore file in fallback path too
                        visited.add(absolute);
                        if (f.getName() != null && isIgnoredFileName(f.getName())) {
                            logger.debug("Skipping ignored filename (fallback): {}", absolute);
                            continue;
                        }
                        allFiles.add(absolute);
                        String ext = getFileExtension(f.getName());
                        String type = classifyFile(ext);
                        if ("code".equals(type)) codeFiles.add(absolute);
                        else if ("document".equals(type)) docFiles.add(absolute);
                        else otherFiles.add(absolute);
                    }
                }
            }
        }
    }

    public AnalysisResult analyzeDirectory(String dirPath) throws IOException {
        AnalysisResult result = new AnalysisResult();
        result.setPath(dirPath);

        Path path = Paths.get(dirPath);
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            logger.warn("analyzeDirectory called with invalid path: {}", dirPath);
            throw new IOException("Invalid directory path: " + dirPath);
        }

        logger.info("Starting analysis of directory: {}", dirPath);
        long startTime = System.currentTimeMillis();

        // Use a visited set to prevent infinite recursion when there are symlink loops
        Set<String> visited = new HashSet<>();
        analyzeRecursively(path.toFile(), result, visited);

        // Save results automatically into code_counter_results/<last-folder>/YYYY_MM_DD_alpha
        try {
            Path saved = saveAnalysisResults(dirPath);
            logger.info("Auto-saved analysis results to {}", saved);
            // expose saved path in result for callers
            result.setResultsPath(saved.toString());
        } catch (Exception e) {
            // include stacktrace to help diagnose saving issues when they occur outside streaming
            logger.warn("Failed to auto-save analysis results for {}: {}", dirPath, e.getMessage(), e);
        }

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Finished analysis of directory: {} — files={}, folders={}, codeFiles={}, lines={}, methods={} (elapsed={}ms)",
                dirPath, result.getTotalFiles(), result.getTotalFolders(), result.getTotalCodeFiles(), result.getTotalLines(), result.getTotalMethods(), elapsed);

        return result;
    }

    /**
     * Analyze directory and stream progress events to the provided SseEmitter.
     * Emits events named "start", "directory", "file", "result", and "error".
     */
    public void analyzeDirectoryStream(String dirPath, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) throws IOException {
        Path path = Paths.get(dirPath);
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            logger.warn("analyzeDirectoryStream called with invalid path: {}", dirPath);
            try { emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("error").data("Invalid directory path: " + dirPath)); } catch (Exception ex) {}
            throw new IOException("Invalid directory path: " + dirPath);
        }

        AnalysisResult result = new AnalysisResult();
        result.setPath(dirPath);

        Set<String> visited = new HashSet<>();
        try {
            // send a start event — if this fails the client likely disconnected and we abort
            safeSend(emitter, "start", "Start:" + dirPath, true);

            analyzeRecursivelyStream(path.toFile(), result, visited, emitter);

            // send a compact summary result (avoid sending the full object for large runs)
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("path", result.getPath());
            summary.put("totalFolders", result.getTotalFolders());
            summary.put("totalFiles", result.getTotalFiles());
            summary.put("totalCodeFiles", result.getTotalCodeFiles());
            summary.put("totalDocFiles", result.getTotalDocFiles());
            summary.put("totalLines", result.getTotalLines());
            summary.put("totalMethods", result.getTotalMethods());
            boolean sentResult = safeSend(emitter, "result", summary, false);
            if (sentResult) logger.info("Sent result summary to client for {}", dirPath); else logger.debug("Result summary not delivered to client for {} (likely disconnected)", dirPath);

            // Auto-save results and notify client
            try {
                Path saved = saveAnalysisResults(dirPath);
                logger.info("Auto-saved analysis results to {} during stream", saved);
                // notify client with saved info and a concise summary so the client can display quickly
                Map<String,Object> savedInfo = new LinkedHashMap<>();
                savedInfo.put("resultsPath", saved.toString());
                savedInfo.putAll(summary);
                boolean sentSaved = safeSend(emitter, "saved", savedInfo, false);
                if (sentSaved) logger.info("Notified client of saved results for {}", dirPath); else logger.debug("Client did not receive 'saved' event for {} (likely disconnected)", dirPath);
            } catch (Exception e) {
                // include stack trace for save problems — this is an application-level issue
                logger.warn("Failed to auto-save analysis results for {} during stream: {}", dirPath, e.getMessage(), e);
            }
        } catch (IOException ioe) {
            logger.error("Error while streaming analysis for {}: {}", dirPath, ioe.getMessage());
            try { emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("error").data(ioe.getMessage())); } catch (Exception ex) {}
            throw ioe;
        } catch (Exception e) {
            logger.error("Unexpected error during stream analysis: {}", e.getMessage());
            try { emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("error").data(e.getMessage())); } catch (Exception ex) {}
            throw new IOException(e);
        }
    }

    private void analyzeRecursivelyStream(File directory, AnalysisResult result, Set<String> visited, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) throws IOException {
        long dirStart = System.currentTimeMillis();
        try {
            String realPath = directory.getCanonicalPath();
            if (visited.contains(realPath)) {
                logger.debug("Skipping already visited directory (cycle detected): {}", realPath);
                return;
            }
            visited.add(realPath);
            logger.debug("Streaming analyze directory: {}", realPath);
        } catch (IOException e) {
            logger.warn("Could not resolve canonical path for {}: {}", directory.getPath(), e.getMessage());
        }

        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isHidden()) continue;
            // skip any known-named files (e.g. .gitignore/.gitkeep/.env.example)
            if (file.isFile() && isIgnoredFileName(file.getName())) {
                logger.debug("Skipping ignored filename during streaming: {}", file.getAbsolutePath());
                continue;
            }

            if (file.isDirectory()) {
                result.setTotalFolders(result.getTotalFolders() + 1);
                // send directory notification to client — abort if client disconnected during control events
                safeSend(emitter, "directory", file.getAbsolutePath(), true);
                // skip descending into any 'target' directories - they are usually build outputs
                if (file.isDirectory() && isIgnoredDirectoryName(file.getName())) {
                    logger.debug("Skipping ignored subdir during streaming analysis: {}", file.getAbsolutePath());
                    continue;
                }
                analyzeRecursivelyStream(file, result, visited, emitter);
            } else {
                // skip files with ignored extensions (e.g. .idx, .db)
                String fileExt = getFileExtension(file.getName());
                if (fileExt != null && IGNORED_FILE_EXTENSIONS.contains(fileExt)) {
                    logger.debug("Skipping ignored file by extension during stream analysis: {}", file.getAbsolutePath());
                    continue;
                }
                result.setTotalFiles(result.getTotalFiles() + 1);
                String extension = getFileExtension(file.getName());
                String fileType = classifyFile(extension);

                    if ("code".equals(fileType)) {
                    result.setTotalCodeFiles(result.getTotalCodeFiles() + 1);
                        // send file event; abort if sender cannot accept (client disconnected)
                        safeSend(emitter, "file", file.getAbsolutePath(), true);
                    analyzeCodeFile(file, extension, result);
                } else if ("document".equals(fileType)) {
                    result.setTotalDocFiles(result.getTotalDocFiles() + 1);
                        // documents: notify client (abort on failure)
                        safeSend(emitter, "file", file.getAbsolutePath(), true);
                }
            }
        }
        long dirElapsed = System.currentTimeMillis() - dirStart;
        if (dirElapsed > 2000) {
            logger.info("Long streaming processing for directory {} ({}ms) — consider profiling large dirs", directory.getAbsolutePath(), dirElapsed);
        } else {
            logger.trace("Finished streaming directory {} in {}ms", directory.getAbsolutePath(), dirElapsed);
        }
    }

    private void analyzeRecursively(File directory, AnalysisResult result, Set<String> visited) {
        long dirStart = System.currentTimeMillis();
        try {
            String realPath = directory.getCanonicalPath();
            if (visited.contains(realPath)) {
                logger.debug("Skipping already visited directory (cycle detected): {}", realPath);
                return;
            }
            visited.add(realPath);
            logger.debug("Analyzing directory: {}", realPath);
        } catch (IOException e) {
            logger.warn("Could not resolve canonical path for {}: {}", directory.getPath(), e.getMessage());
        }

        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isHidden()) continue;
            
            try {
                String filePath = file.getCanonicalPath();
                // Skip files already counted (protect against hardlinks / symlinks pointing to same file)
                if (visited.contains(filePath)) {
                    logger.trace("Skipping already visited file/dir (duplicate): {}", filePath);
                    continue;
                }
            } catch (IOException e) {
                // if canonicalization fails, fall back to absolute path check
                logger.debug("Failed to get canonical path for {}: {}", file.getPath(), e.getMessage());
            }

            if (file.isDirectory()) {
                result.setTotalFolders(result.getTotalFolders() + 1);
                try {
                    try {
                        // attempt to canonicalize to detect possible IO problems early
                        file.getCanonicalPath();
                    } catch (IOException ioe) {
                        logger.debug("Failed to canonicalize directory {}: {}", file.getAbsolutePath(), ioe.getMessage());
                    }
                    logger.trace("Recursing into directory: {}", file.getAbsolutePath());
                    // skip known ignored directories entirely (target, .github, .idea, .vscode)
                    if (isIgnoredDirectoryName(file.getName())) {
                        logger.debug("Skipping nested 'target' directory during analysis: {}", file.getAbsolutePath());
                        continue;
                    }
                    analyzeRecursively(file, result, visited);
                } catch (StackOverflowError so) {
                    logger.error("Stack overflow while recursing into directory {} — possible cycle", file.getAbsolutePath());
                }
                } else {
                    // ignore well-known filenames that should not be counted
                    if (isIgnoredFileName(file.getName())) {
                        logger.debug("Skipping ignored filename during analysis: {}", file.getAbsolutePath());
                        continue;
                    }
                    // ignore files with ignored extensions (e.g. .idx, .db)
                    String fext2 = getFileExtension(file.getName());
                    if (fext2 != null && IGNORED_FILE_EXTENSIONS.contains(fext2)) {
                        logger.debug("Skipping ignored file by extension during analysis: {}", file.getAbsolutePath());
                        continue;
                    }
                result.setTotalFiles(result.getTotalFiles() + 1);
                try {
                    visited.add(file.getCanonicalPath());
                } catch (IOException e) {
                    // ignore
                }
                
                String extension = getFileExtension(file.getName());
                String fileType = classifyFile(extension);
                
                if ("code".equals(fileType)) {
                    result.setTotalCodeFiles(result.getTotalCodeFiles() + 1);
                    logger.trace("Analyzing code file: {} (ext={})", file.getAbsolutePath(), extension);
                    analyzeCodeFile(file, extension, result);
                } else if ("document".equals(fileType)) {
                    result.setTotalDocFiles(result.getTotalDocFiles() + 1);
                }
            }
        }
        long dirElapsed = System.currentTimeMillis() - dirStart;
        if (dirElapsed > 2000) {
            logger.info("Long analysis for directory {} took {}ms — files/folders: files={}, folders={}", directory.getAbsolutePath(), dirElapsed, result.getTotalFiles(), result.getTotalFolders());
        } else {
            logger.trace("Analyzed directory {} in {}ms", directory.getAbsolutePath(), dirElapsed);
        }
    }

    private void analyzeCodeFile(File file, String extension, AnalysisResult result) {
        long fileStart = System.currentTimeMillis();
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
            long fileElapsed = System.currentTimeMillis() - fileStart;
            if (fileElapsed > 500) {
                logger.info("Slow analyzeCodeFile for {} ({}ms) — lines={}, ext={}", file.getAbsolutePath(), fileElapsed, lineCount, extension);
            } else {
                logger.trace("analyzeCodeFile {} completed in {}ms", file.getAbsolutePath(), fileElapsed);
            }
        } catch (java.nio.charset.MalformedInputException mie) {
            // binary or undecodable file — skip method counting and lines for this file
            logger.debug("Skipping file due to malformed encoding (likely binary): {} — {}", file.getPath(), mie.getMessage());
        } catch (Exception e) {
            logger.warn("Failed to analyze file: {} - {}", file.getPath(), e.getMessage());
        }
    }

    private long countLines(File file) throws IOException {
        try (Stream<String> lines = Files.lines(file.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            return lines.count();
        } catch (java.nio.charset.MalformedInputException mie) {
            logger.debug("countLines: Malformed input when reading {} — treating as 0 lines", file.getPath());
            return 0L;
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
            List<String> lines = Files.readAllLines(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
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
        } catch (java.nio.charset.MalformedInputException mie) {
            logger.debug("estimateMethodCount: Malformed input while reading {} — skipping method estimation", file.getPath());
            return 0;
        } catch (IOException ioe) {
            logger.debug("estimateMethodCount: IO error reading {} — {}", file.getPath(), ioe.getMessage());
            return 0;
        } catch (Exception e) {
            logger.debug("estimateMethodCount: unexpected error for {} — {}", file.getPath(), e.getMessage());
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

    private boolean isIgnoredFileName(String rawName) {
        if (rawName == null) return false;
        String name = rawName.toLowerCase();

        // check explicit names first
        if (IGNORED_FILE_NAMES.contains(name)) return true;

        // pattern: files that begin with 'test' and end with '.csv' should be ignored
        if (name.startsWith("test") && name.endsWith(".csv")) return true;
        // also ignore CSVs that start with 'sweep' or 'live' (e.g. sweep_2025.csv, live-results.csv)
        if ((name.startsWith("sweep") || name.startsWith("live")) && name.endsWith(".csv")) return true;

        return false;
    }


    private String classifyFile(String extension) {
        if (CODE_EXTENSIONS.contains(extension)) return "code";
        if (DOC_EXTENSIONS.contains(extension)) return "document";
        return "other";
    }

    /**
     * Helper to send SSE events safely. If abortOnFailure is true, IO/IllegalState exceptions
     * are propagated to abort the stream. If false, failures are treated as client disconnects
     * and logged at DEBUG level so auto-save notification doesn't spam WARN when the client dropped.
     */
    private boolean safeSend(org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter, String eventName, Object data, boolean abortOnFailure) throws IOException {
        if (emitter == null) return false;
        try {
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name(eventName).data(data));
            logger.trace("SSE event '{}' sent for data={} (abortOnFailure={})", eventName, data, abortOnFailure);
            return true;
        } catch (IOException | IllegalStateException sendEx) {
            // these are typically because the client disconnected — treat as debug unless requested to abort
            logger.debug("Emitter send failure for event '{}' data='{}': {} (abort={})", eventName, data, sendEx.getMessage(), abortOnFailure);
            if (abortOnFailure) {
                throw sendEx instanceof IOException ? (IOException) sendEx : new IOException(sendEx);
            }
            return false;
        } catch (Exception ex) {
            // unexpected — log and optionally abort
            logger.warn("Unexpected error sending SSE event '{}' data='{}': {}", eventName, data, ex.getMessage());
            if (abortOnFailure) throw new IOException(ex);
            return false;
        }
    }
}
