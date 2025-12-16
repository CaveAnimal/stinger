package com.codecounter.stinger.service.summary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScanResultsReader {

    private ScanResultsReader() {
    }

    public static ScanResults read(Path savedResultsDir) throws IOException {
        Path summary = savedResultsDir.resolve("summary.txt");
        if (!Files.exists(summary)) {
            throw new IOException("Missing summary.txt in " + savedResultsDir);
        }

        Map<String, String> meta = readKeyValue(summary);
        String root = meta.get("root");
        if (root == null || root.isBlank()) {
            throw new IOException("summary.txt missing 'root' key: " + summary);
        }
        Path rootPath = Path.of(root).toAbsolutePath().normalize();

        List<Path> code = readPathList(savedResultsDir.resolve("code_files.txt"));
        List<Path> docs = readPathList(savedResultsDir.resolve("document_files.txt"));
        List<Path> folders = readPathList(savedResultsDir.resolve("folders.txt"));

        return new ScanResults(savedResultsDir.toAbsolutePath().normalize(), rootPath, code, docs, folders);
    }

    private static List<Path> readPathList(Path file) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<Path> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (!t.isBlank()) {
                out.add(Path.of(t).toAbsolutePath().normalize());
            }
        }
        return out;
    }

    private static Map<String, String> readKeyValue(Path p) throws IOException {
        Map<String, String> map = new HashMap<>();
        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isBlank()) continue;
            int idx = t.indexOf(':');
            if (idx < 0) continue;
            String k = t.substring(0, idx).trim();
            String v = t.substring(idx + 1).trim();
            if (!k.isBlank()) map.put(k, v);
        }
        return map;
    }
}
