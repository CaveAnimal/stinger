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
        // General / Web
        "java", "js", "ts", "py", "cpp", "cc", "c", "h", "hpp", "cs", "go", "rb", "php", 
        "swift", "kt", "kts", "rs", "scala", "sh", "bash", "zsh", "ps1", "sql", "html", 
        "css", "jsx", "tsx", "vue", "xml", "json", "yaml", "yml", "properties", "toml", "ini",
        // Build / Config
        "gradle", "groovy", "bzl", "bazel", "cmake", "make", "mk", "dockerfile", "vagrantfile", "jenkinsfile",
        "conf", "cfg", "def", "map", "ld",
        // Scripting / Other
        "pl", "pm", "tcl", "lua", "r", "m", "mm", "dart", "elm", "erl", "hrl", "ex", "exs",
        "fs", "fsx", "fsi", "hs", "lhs", "ml", "mli", "clj", "cljs", "edn", "lisp", "el", "scm",
        "vb", "vbs", "asm", "s", "sol", "vy", "tf", "hcl", "json5"
    ));
    private static final Set<String> DOC_EXTENSIONS = new HashSet<>(Arrays.asList(
        "md", "markdown", "txt", "text", "rst", "adoc", "asciidoc", "pdf", "doc", "docx", 
        "rtf", "odt", "tex", "bib", "ppt", "pptx", "xls", "xlsx", "csv", "tsv", 
        "license", "notice", "contributing", "readme", "changelog", "authors", "owners", "codeowners",
        "proto", "graphql"
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
        // docker-related folders should generally be ignored (often large and infra-related)
        IGNORED_DIR_NAMES.add("docker");
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
        // Metadata / IDE / System
        "idx", "db", "iml", "log", "bak", "bat", "tmp", "temp", "swp", "ds_store", "desktop",
        // Images / Media
        "png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "ico", "tif", "tiff", "heic", "avif", "eps", "psd",
        "mp3", "wav", "mp4", "avi", "mov", "flv", "wmv", "mkv", "webm",
        // Fonts
        "ttf", "otf", "woff", "woff2", "eot",
        // Archives / Binaries
        "zip", "tar", "gz", "bz2", "xz", "7z", "rar", "jar", "war", "ear", "iso", "img",
        "exe", "dll", "so", "dylib", "bin", "o", "obj", "a", "lib", "class", "pyc", "pyd",
        // Data / Models (TensorFlow/ML)
        "pb", "pbtxt", "tflite", "ckpt", "h5", "hdf5", "keras", "onnx", "pt", "pth", "npy", "npz", "pkl", "joblib",
        "parquet", "avro", "orc", "mindrecord", "tfrecord",
        // Lock files
        "lock"
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

    // common Docker filenames and compose files are infra; treat as ignored by default
    static {
        IGNORED_FILE_NAMES.add("dockerfile");
        IGNORED_FILE_NAMES.add("docker-compose.yml");
        IGNORED_FILE_NAMES.add("docker-compose.yaml");
    }

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

    private Path getPersistentSummaryPath(String dirPath) throws IOException {
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
        return rootFolder.resolve("summary.txt");
    }

    /**
     * Save detailed analysis lists (folders, all files, code files, documents, other files)
    * to the ./code_counter_results/<sanitized-root>/<YYYY_MM_DD>_alpha/ directory under application working directory.
     * Returns the path to the created results folder.
     */
    public Path saveAnalysisResults(String dirPath) throws IOException {
        return saveAnalysisResults(dirPath, null);
    }

    public Path saveAnalysisResults(String dirPath, AnalysisResult analysisResult) throws IOException {
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

        Path persistentSummary = getPersistentSummaryPath(dirPath);
        Path rootFolder = persistentSummary.getParent();
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
        
        long codeLines = 0L;
        long docLines = 0L;

        if (analysisResult != null) {
            codeLines = analysisResult.getTotalCodeLines();
            docLines = analysisResult.getTotalDocLines();
            meta.put("totalMethods", String.valueOf(analysisResult.getTotalMethods()));
        } else {
            // compute total lines per category so summary includes code/doc line counts
            for (String p : codeFiles) {
                try {
                    codeLines += countLines(new File(p));
                } catch (Exception e) {
                    logger.debug("Failed to count lines for code file {}: {}", p, e.getMessage());
                }
            }
            for (String p : docFiles) {
                try {
                    docLines += countLines(new File(p));
                } catch (Exception e) {
                    logger.debug("Failed to count lines for doc file {}: {}", p, e.getMessage());
                }
            }
        }

        meta.put("totalCodeLines", String.valueOf(codeLines));
        meta.put("totalDocLines", String.valueOf(docLines));
        meta.put("totalLines", String.valueOf(codeLines + docLines));
        writeKeyValue(outFolder.resolve("summary.txt"), meta);

        // Update persistent summary in the application folder
        try {
            Map<String, String> persistentMap = new LinkedHashMap<>();
            if (Files.exists(persistentSummary)) {
                persistentMap = readKeyValue(persistentSummary);
            }
            // Merge current meta into persistent map (overwriting existing keys with new values)
            persistentMap.putAll(meta);
            writeKeyValue(persistentSummary, persistentMap);
        } catch (Exception e) {
            logger.warn("Failed to update persistent summary file: {}", e.getMessage());
        }

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

    private Map<String, String> readKeyValue(Path p) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        for (String line : lines) {
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                map.put(parts[0].trim(), parts[1].trim());
            }
        }
        return map;
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
                    String ext = getFileExtension(f.getName());
                    String type = classifyFile(ext);
                    
                    if ("code".equals(type)) {
                        allFiles.add(fcanon);
                        codeFiles.add(fcanon);
                    } else if ("document".equals(type)) {
                        allFiles.add(fcanon);
                        docFiles.add(fcanon);
                    }
                    // else: ignore completely (do not add to allFiles or otherFiles)
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
                        
                        String ext = getFileExtension(f.getName());
                        String type = classifyFile(ext);
                        if ("code".equals(type)) {
                            allFiles.add(absolute);
                            codeFiles.add(absolute);
                        } else if ("document".equals(type)) {
                            allFiles.add(absolute);
                            docFiles.add(absolute);
                        }
                        // else ignore
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
            Path saved = saveAnalysisResults(dirPath, result);
            logger.info("Auto-saved analysis results to {}", saved);
            // expose saved path in result for callers
            result.setResultsPath(saved.toString());
            // attempt to read the auto-saved summary and copy computed line totals back into the AnalysisResult
            Path summary = saved.resolve("summary.txt");
            if (Files.exists(summary)) {
                try {
                    List<String> lines = Files.readAllLines(summary, StandardCharsets.UTF_8);
                    for (String l : lines) {
                        if (l.startsWith("totalCodeLines:")) {
                            String v = l.substring(l.indexOf(':') + 1).trim();
                            try { result.setTotalCodeLines(Long.parseLong(v)); } catch (NumberFormatException nfe) {}
                        } else if (l.startsWith("totalDocLines:")) {
                            String v = l.substring(l.indexOf(':') + 1).trim();
                            try { result.setTotalDocLines(Long.parseLong(v)); } catch (NumberFormatException nfe) {}
                        }
                    }
                    // keep totalLines consistent
                    result.setTotalLines(result.getTotalCodeLines() + result.getTotalDocLines());
                } catch (Exception e) {
                    logger.debug("Failed to read summary.txt from saved results {}: {}", summary.toString(), e.getMessage());
                }
            }
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

            // Attempt to read totals from persistent summary.txt instead of computing them
            AnalysisResult totals = new AnalysisResult();
            try {
                Path persistentSummary = getPersistentSummaryPath(dirPath);
                if (Files.exists(persistentSummary)) {
                    Map<String, String> summaryMap = readKeyValue(persistentSummary);
                    totals.setTotalFolders(parseIntSafe(summaryMap.get("folders")));
                    totals.setTotalFiles(parseIntSafe(summaryMap.get("totalFiles")));
                    totals.setTotalCodeFiles(parseIntSafe(summaryMap.get("codeFiles")));
                    totals.setTotalDocFiles(parseIntSafe(summaryMap.get("docFiles")));
                    totals.setTotalOtherFiles(parseIntSafe(summaryMap.get("otherFiles")));
                    totals.setTotalLines(parseLongSafe(summaryMap.get("totalLines")));
                    totals.setTotalCodeLines(parseLongSafe(summaryMap.get("totalCodeLines")));
                    totals.setTotalDocLines(parseLongSafe(summaryMap.get("totalDocLines")));
                    totals.setTotalMethods(parseIntSafe(summaryMap.get("totalMethods")));
                    logger.info("Loaded totals from persistent summary: {}", persistentSummary);
                } else {
                    logger.info("No persistent summary found at {}, percentages will be unavailable.", persistentSummary);
                }
            } catch (Exception e) {
                logger.warn("Failed to load persistent summary: {}", e.getMessage());
            }

            Map<String, Object> totalsSummary = new LinkedHashMap<>();
            totalsSummary.put("path", totals.getPath() == null ? dirPath : totals.getPath());
            totalsSummary.put("totalFolders", totals.getTotalFolders());
            totalsSummary.put("totalFiles", totals.getTotalFiles());
            totalsSummary.put("totalCodeFiles", totals.getTotalCodeFiles());
            totalsSummary.put("totalDocFiles", totals.getTotalDocFiles());
            totalsSummary.put("totalOtherFiles", totals.getTotalOtherFiles());
            totalsSummary.put("totalLines", totals.getTotalLines());
            totalsSummary.put("totalCodeLines", totals.getTotalCodeLines());
            totalsSummary.put("totalDocLines", totals.getTotalDocLines());
            totalsSummary.put("totalMethods", totals.getTotalMethods());
            // best-effort send totals so UI can draw progress bars with known totals
            safeSend(emitter, "totals", totalsSummary, false);

            analyzeRecursivelyStream(path.toFile(), result, visited, totals, emitter);

            // send a compact summary result (avoid sending the full object for large runs)
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("path", result.getPath());
            summary.put("totalFolders", result.getTotalFolders());
            summary.put("totalFiles", result.getTotalFiles());
            summary.put("totalCodeFiles", result.getTotalCodeFiles());
            summary.put("totalDocFiles", result.getTotalDocFiles());
            summary.put("totalOtherFiles", result.getTotalOtherFiles());
            summary.put("totalLines", result.getTotalLines());
            summary.put("totalCodeLines", result.getTotalCodeLines());
            summary.put("totalDocLines", result.getTotalDocLines());
            summary.put("totalMethods", result.getTotalMethods());
            boolean sentResult = safeSend(emitter, "result", summary, false);
            if (sentResult) logger.info("Sent result summary to client for {}", dirPath); else logger.debug("Result summary not delivered to client for {} (likely disconnected)", dirPath);

            // Auto-save results and notify client
            try {
                Path saved = saveAnalysisResults(dirPath, result);
                logger.info("Auto-saved analysis results to {} during stream", saved);
                // notify client with saved info and a concise summary so the client can display quickly
                Map<String,Object> savedInfo = new LinkedHashMap<>();
                savedInfo.put("resultsPath", saved.toString());
                savedInfo.putAll(summary);
                // include a compact list of saved file paths (first N entries) so the client
                // can display saved items under File Explorer without needing to show the full path
                try {
                    final int MAX_SAVED_FILES = 120; // cap to keep the payload small
                    List<String> savedFiles = new ArrayList<>();
                    Path codeList = saved.resolve("code_files.txt");
                    Path docList = saved.resolve("document_files.txt");
                    Path otherList = saved.resolve("other_files.txt");
                    // helper to read up to n lines from file
                    java.util.function.BiConsumer<Path,Integer> addLines = (p, n) -> {
                        if (savedFiles.size() >= MAX_SAVED_FILES) return;
                        try {
                            if (Files.exists(p)) {
                                try (java.util.stream.Stream<String> s = Files.lines(p, java.nio.charset.StandardCharsets.UTF_8)) {
                                    s.limit(n).forEach(line -> {
                                        if (savedFiles.size() < MAX_SAVED_FILES) savedFiles.add(line);
                                    });
                                }
                            }
                        } catch (Exception ex) {
                            logger.debug("Failed to read saved list {}: {}", p, ex.getMessage());
                        }
                    };
                    // collect some code files first, then docs, then others
                    addLines.accept(codeList, 80);
                    addLines.accept(docList, 40);
                    addLines.accept(otherList, 20);
                    if (!savedFiles.isEmpty()) savedInfo.put("savedFiles", savedFiles);
                } catch (Exception ex) {
                    logger.debug("Failed to prepare saved file list for client: {}", ex.getMessage());
                }
                boolean sentSaved = safeSend(emitter, "saved", savedInfo, false);
                if (sentSaved) {
                    logger.info("Notified client of saved results for {}", dirPath);
                    // best-effort: send a final 'done' event so the client can close deterministically even
                    // if the controller-level 'done' isn't observed (some clients may close earlier)
                    safeSend(emitter, "done", "saved", false);
                } else {
                    logger.debug("Client did not receive 'saved' event for {} (likely disconnected)", dirPath);
                }
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

    private void analyzeRecursivelyStream(File directory, AnalysisResult result, Set<String> visited, AnalysisResult totals, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) throws IOException {
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
                // processed folder count (used by live progress)
                result.setTotalFolders(result.getTotalFolders() + 1);
                
                // Update totals if we've exceeded the initial estimate
                if (result.getTotalFolders() > totals.getTotalFolders()) {
                    totals.setTotalFolders(result.getTotalFolders());
                }

                // send directory notification to client — abort if client disconnected during control events
                safeSend(emitter, "directory", file.getAbsolutePath(), true);
                // send folder progress (processed / total)
                Map<String,Object> folderProg = new LinkedHashMap<>();
                folderProg.put("processedFolders", result.getTotalFolders());
                folderProg.put("totalFolders", totals.getTotalFolders());
                if (totals.getTotalFolders() > 0) folderProg.put("percent", (int)((result.getTotalFolders()*100L)/totals.getTotalFolders())); else folderProg.put("percent", 0);
                safeSend(emitter, "folderProgress", folderProg, false);
                // skip descending into any 'target' directories - they are usually build outputs
                if (file.isDirectory() && isIgnoredDirectoryName(file.getName())) {
                    logger.debug("Skipping ignored subdir during streaming analysis: {}", file.getAbsolutePath());
                    continue;
                }
                analyzeRecursivelyStream(file, result, visited, totals, emitter);
            } else {
                // skip files with ignored extensions (e.g. .idx, .db)
                String fileExt = getFileExtension(file.getName());
                if (fileExt != null && IGNORED_FILE_EXTENSIONS.contains(fileExt)) {
                    logger.debug("Skipping ignored file by extension during stream analysis: {}", file.getAbsolutePath());
                    continue;
                }
                String extension = getFileExtension(file.getName());
                String fileType = classifyFile(extension);
                
                if ("code".equals(fileType)) {
                    result.setTotalFiles(result.getTotalFiles() + 1);
                    result.setTotalCodeFiles(result.getTotalCodeFiles() + 1);
                    
                    // Update totals if we've exceeded the initial estimate
                    if (result.getTotalFiles() > totals.getTotalFiles()) totals.setTotalFiles(result.getTotalFiles());
                    if (result.getTotalCodeFiles() > totals.getTotalCodeFiles()) totals.setTotalCodeFiles(result.getTotalCodeFiles());

                    // send file event; abort if sender cannot accept (client disconnected)
                    safeSend(emitter, "file", file.getAbsolutePath(), true);
                    // analyze and stream per-file stats and progress
                    analyzeCodeFile(file, extension, result, emitter, totals);
                } else if ("document".equals(fileType)) {
                    result.setTotalFiles(result.getTotalFiles() + 1);
                    result.setTotalDocFiles(result.getTotalDocFiles() + 1);
                    
                    // Update totals if we've exceeded the initial estimate
                    if (result.getTotalFiles() > totals.getTotalFiles()) totals.setTotalFiles(result.getTotalFiles());
                    if (result.getTotalDocFiles() > totals.getTotalDocFiles()) totals.setTotalDocFiles(result.getTotalDocFiles());

                    try {
                        long docLines = countLines(file);
                        result.setTotalDocLines(result.getTotalDocLines() + docLines);
                        result.setTotalLines(result.getTotalLines() + docLines);
                        
                        // Update line totals
                        if (result.getTotalDocLines() > totals.getTotalDocLines()) totals.setTotalDocLines(result.getTotalDocLines());
                        if (result.getTotalLines() > totals.getTotalLines()) totals.setTotalLines(result.getTotalLines());
                    } catch (IOException ioe) {
                        logger.debug("Failed to count lines for document {}: {}", file.getAbsolutePath(), ioe.getMessage());
                    }
                    // documents: notify client (abort on failure)
                    safeSend(emitter, "file", file.getAbsolutePath(), true);
                    // send per-file stats for documents (lines and processed counts)
                    Map<String,Object> docStats = new LinkedHashMap<>();
                    docStats.put("path", file.getAbsolutePath());
                    docStats.put("type", "document");
                    // doc lines is accumulated in result.getTotalDocLines() — send this file's lines instead
                    try {
                        long docLines = countLines(file);
                        docStats.put("lines", docLines);
                    } catch (IOException ioe) {
                        docStats.put("lines", 0);
                    }
                    docStats.put("processedDocFiles", result.getTotalDocFiles());
                    docStats.put("totalDocFiles", totals.getTotalDocFiles());
                    if (totals.getTotalDocFiles() > 0) docStats.put("percentDocFiles", (int)((result.getTotalDocFiles()*100L)/totals.getTotalDocFiles())); else docStats.put("percentDocFiles", 0);
                    // include doc-line running totals so the client can render processed/total counters live
                    docStats.put("processedDocLines", result.getTotalDocLines());
                    docStats.put("totalDocLines", totals.getTotalDocLines());
                    if (totals.getTotalDocLines() > 0) docStats.put("percentDocLines", (int)((result.getTotalDocLines()*100L)/totals.getTotalDocLines())); else docStats.put("percentDocLines", 0);
                    safeSend(emitter, "file-stats", docStats, false);
                }
                // else ignore (do not increment totalFiles)
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
                String extension = getFileExtension(file.getName());
                String fileType = classifyFile(extension);
                
                if ("code".equals(fileType)) {
                    result.setTotalFiles(result.getTotalFiles() + 1);
                    result.setTotalCodeFiles(result.getTotalCodeFiles() + 1);
                    logger.trace("Analyzing code file: {} (ext={})", file.getAbsolutePath(), extension);
                    analyzeCodeFile(file, extension, result);
                } else if ("document".equals(fileType)) {
                    result.setTotalFiles(result.getTotalFiles() + 1);
                    result.setTotalDocFiles(result.getTotalDocFiles() + 1);
                    try {
                        long lines = countLines(file);
                        result.setTotalDocLines(result.getTotalDocLines() + lines);
                        result.setTotalLines(result.getTotalLines() + lines);
                    } catch (Exception e) {
                        logger.debug("Failed to count lines for doc file {}: {}", file.getAbsolutePath(), e.getMessage());
                    }
                }
                // else ignore (do not increment totalFiles)
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
            // track code-specific and total line counts
            result.setTotalCodeLines(result.getTotalCodeLines() + lineCount);
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

    /**
     * Streaming-aware analyzeCodeFile overload: updates result and emits per-file stats and progress events
     */
    private void analyzeCodeFile(File file, String extension, AnalysisResult result, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter, AnalysisResult totals) {
        long fileStart = System.currentTimeMillis();
        try {
            long lineCount = countLines(file);
            // track code-specific and total line counts
            result.setTotalCodeLines(result.getTotalCodeLines() + lineCount);
            result.setTotalLines(result.getTotalLines() + lineCount);

            // Update totals if we've exceeded the initial estimate
            if (result.getTotalCodeLines() > totals.getTotalCodeLines()) totals.setTotalCodeLines(result.getTotalCodeLines());
            if (result.getTotalLines() > totals.getTotalLines()) totals.setTotalLines(result.getTotalLines());

            int methodCount;
            if ("java".equals(extension)) {
                // For Java we can stream individual method names as we discover them
                try {
                    JavaParser parser = new JavaParser();
                    CompilationUnit cu = parser.parse(file).getResult().orElse(null);
                    if (cu != null) {
                        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
                        methodCount = methods.size();
                        for (MethodDeclaration md : methods) {
                            // increment processed methods and emit the current method name
                            result.setTotalMethods(result.getTotalMethods() + 1);
                            
                            // Update totals if we've exceeded the initial estimate
                            if (result.getTotalMethods() > totals.getTotalMethods()) totals.setTotalMethods(result.getTotalMethods());

                            safeSend(emitter, "method", md.getNameAsString(), false);
                            // also send a small method progress update
                            Map<String,Object> mprog = new LinkedHashMap<>();
                            mprog.put("processedMethods", result.getTotalMethods());
                            mprog.put("totalMethods", totals.getTotalMethods());
                            if (totals.getTotalMethods() > 0) mprog.put("percentMethods", (int)((result.getTotalMethods()*100L)/totals.getTotalMethods())); else mprog.put("percentMethods", 0);
                            safeSend(emitter, "methodProgress", mprog, false);
                        }
                    } else {
                        methodCount = 0;
                    }
                } catch (Exception e) {
                    logger.debug("Streaming method names failed for {}: {}", file.getAbsolutePath(), e.getMessage());
                    methodCount = countJavaMethods(file);
                    result.setTotalMethods(result.getTotalMethods() + methodCount);
                    if (result.getTotalMethods() > totals.getTotalMethods()) totals.setTotalMethods(result.getTotalMethods());
                }
            } else {
                methodCount = estimateMethodCount(file, extension);
                result.setTotalMethods(result.getTotalMethods() + methodCount);
                if (result.getTotalMethods() > totals.getTotalMethods()) totals.setTotalMethods(result.getTotalMethods());
            }

            // emit file-level stats for the client UI to show lines and methods for this file
            Map<String,Object> fileStats = new LinkedHashMap<>();
            fileStats.put("path", file.getAbsolutePath());
            fileStats.put("type", "code");
            fileStats.put("ext", extension);
            fileStats.put("lines", lineCount);
            fileStats.put("methods", methodCount);
            fileStats.put("processedCodeFiles", result.getTotalCodeFiles());
            fileStats.put("totalCodeFiles", totals.getTotalCodeFiles());
            if (totals.getTotalCodeFiles() > 0) fileStats.put("percentCodeFiles", (int)((result.getTotalCodeFiles()*100L)/totals.getTotalCodeFiles())); else fileStats.put("percentCodeFiles", 0);
            if (totals.getTotalCodeLines() > 0) fileStats.put("percentCodeLines", (int)((result.getTotalCodeLines()*100L)/totals.getTotalCodeLines())); else fileStats.put("percentCodeLines", 0);
            fileStats.put("processedMethods", result.getTotalMethods());
            fileStats.put("totalMethods", totals.getTotalMethods());
            if (totals.getTotalMethods() > 0) fileStats.put("percentMethods", (int)((result.getTotalMethods()*100L)/totals.getTotalMethods())); else fileStats.put("percentMethods", 0);

            safeSend(emitter, "file-stats", fileStats, false);

            // emit an overall 'progress' summary to let the UI update multiple bars in one go
            Map<String,Object> progress = new LinkedHashMap<>();
            progress.put("processedCodeFiles", result.getTotalCodeFiles());
            progress.put("totalCodeFiles", totals.getTotalCodeFiles());
            progress.put("processedCodeLines", result.getTotalCodeLines());
            progress.put("totalCodeLines", totals.getTotalCodeLines());
            progress.put("processedMethods", result.getTotalMethods());
            progress.put("totalMethods", totals.getTotalMethods());
            progress.put("processedFiles", result.getTotalFiles());
            progress.put("totalFiles", totals.getTotalFiles());
            progress.put("processedLines", result.getTotalLines());
            progress.put("totalLines", totals.getTotalLines());
            // include document counts in compact progress packet so UI can update doc bars
            progress.put("processedDocFiles", result.getTotalDocFiles());
            progress.put("totalDocFiles", totals.getTotalDocFiles());
            progress.put("processedDocLines", result.getTotalDocLines());
            progress.put("totalDocLines", totals.getTotalDocLines());
            safeSend(emitter, "progress", progress, false);

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

    /**
     * Compute totals for a directory tree. This is a pre-scan to provide totals for streaming progress.
     */
    private int parseIntSafe(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseLongSafe(String s) {
        if (s == null) return 0L;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private AnalysisResult computeTotals(File directory, Set<String> visited) throws IOException {
        AnalysisResult totals = new AnalysisResult();
        if (directory == null || !directory.exists()) return totals;

        try {
            String realPath = directory.getCanonicalPath();
            if (visited.contains(realPath)) return totals;
            visited.add(realPath);
        } catch (IOException e) {
            logger.debug("computeTotals: failed to canonicalize {}: {}", directory.getPath(), e.getMessage());
        }

        File[] files = directory.listFiles();
        if (files == null) return totals;

        for (File f : files) {
            if (f.isHidden()) continue;
            if (f.isFile() && isIgnoredFileName(f.getName())) continue;

            if (f.isDirectory()) {
                totals.setTotalFolders(totals.getTotalFolders() + 1);
                if (isIgnoredDirectoryName(f.getName())) continue;
                AnalysisResult childTotals = computeTotals(f, visited);
                totals.setTotalFolders(totals.getTotalFolders() + childTotals.getTotalFolders());
                totals.setTotalFiles(totals.getTotalFiles() + childTotals.getTotalFiles());
                totals.setTotalCodeFiles(totals.getTotalCodeFiles() + childTotals.getTotalCodeFiles());
                totals.setTotalDocFiles(totals.getTotalDocFiles() + childTotals.getTotalDocFiles());
                totals.setTotalLines(totals.getTotalLines() + childTotals.getTotalLines());
                totals.setTotalCodeLines(totals.getTotalCodeLines() + childTotals.getTotalCodeLines());
                totals.setTotalDocLines(totals.getTotalDocLines() + childTotals.getTotalDocLines());
                totals.setTotalMethods(totals.getTotalMethods() + childTotals.getTotalMethods());
            } else {
                String ext = getFileExtension(f.getName());
                if (ext != null && IGNORED_FILE_EXTENSIONS.contains(ext)) continue;
                String type = classifyFile(ext);
                if ("code".equals(type)) {
                    totals.setTotalFiles(totals.getTotalFiles() + 1);
                    totals.setTotalCodeFiles(totals.getTotalCodeFiles() + 1);
                    try {
                        long lines = countLines(f);
                        totals.setTotalCodeLines(totals.getTotalCodeLines() + lines);
                        totals.setTotalLines(totals.getTotalLines() + lines);
                    } catch (IOException ioe) {
                        logger.debug("computeTotals: failed to count lines for {}: {}", f.getAbsolutePath(), ioe.getMessage());
                    }
                    if ("java".equals(ext)) totals.setTotalMethods(totals.getTotalMethods() + countJavaMethods(f)); else totals.setTotalMethods(totals.getTotalMethods() + estimateMethodCount(f, ext));
                } else if ("document".equals(type)) {
                    totals.setTotalFiles(totals.getTotalFiles() + 1);
                    totals.setTotalDocFiles(totals.getTotalDocFiles() + 1);
                    try {
                        long docLines = countLines(f);
                        totals.setTotalDocLines(totals.getTotalDocLines() + docLines);
                        totals.setTotalLines(totals.getTotalLines() + docLines);
                    } catch (IOException ioe) {
                        logger.debug("computeTotals: failed to count lines for doc {}: {}", f.getAbsolutePath(), ioe.getMessage());
                    }
                }
            }
        }

        return totals;
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

        // Dockerfile variants: name may be 'Dockerfile' or 'Dockerfile.<variant>'
        if (name.startsWith("dockerfile")) return true;
        // docker-compose.* files (yaml/yml) – ignore these orchestration files
        if (name.startsWith("docker-compose")) return true;

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
