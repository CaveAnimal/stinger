package com.codecounter.stinger.worker;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class BottomUpProcessor {
    private final PromptBundle prompts;
    private final LlamaClient llama;
    private final String model;
    private final H2Store store;
    private final long appId;

    public BottomUpProcessor(PromptBundle prompts, LlamaClient llama, String model, H2Store store, long appId) {
        this.prompts = prompts;
        this.llama = llama;
        this.model = model;
        this.store = store;
        this.appId = appId;
    }

    public void process(ResultsReader results, Integer maxFiles, Integer maxFolders, boolean skipMethods) throws Exception {
        Path root = results.rootPath();

        List<Path> allFiles = new ArrayList<>();
        allFiles.addAll(results.codeFiles());
        allFiles.addAll(results.docFiles());

        if (maxFiles != null && maxFiles >= 0 && allFiles.size() > maxFiles) {
            allFiles = allFiles.subList(0, maxFiles);
        }

        int processedMethods = 0;
        int processedFiles = 0;
        int processedFolders = 0;

        for (Path absFile : allFiles) {
            boolean isDoc = results.docFiles().contains(absFile);
            String fileType = isDoc ? "document" : "code";

            String rel = toRelativeStable(root, absFile);

            if (!skipMethods && fileType.equals("code") && absFile.toString().toLowerCase().endsWith(".java")) {
                List<JavaMethodExtractor.ExtractedMethod> methods = JavaMethodExtractor.extractMethods(absFile);
                for (JavaMethodExtractor.ExtractedMethod m : methods) {
                    String methodKey = rel + "#" + m.stableId();
                    if (store.nodeExists(appId, methodKey, "method")) {
                        continue;
                    }

                    ObjectNode input = JsonUtil.mapper().createObjectNode();
                    input.put("full_path", absFile.toString() + "#" + m.stableId());
                    input.put("element_type", "method");
                    input.put("name", m.displayName());
                    input.put("file_type", "code");
                    input.put("content", truncate(m.code(), 6000));

                        String md = llama.summarizeNodeMarkdown(model, prompts, input);
                        ObjectNode summaryJson = MarkdownSummaryParser.toSummaryJson(
                            input.get("full_path").asText(),
                            "method",
                            m.displayName(),
                            "code",
                            "Method",
                            md
                        );
                        PersistedFields fields = PersistedFields.from(summaryJson);

                    store.upsertNode(appId, methodKey, "method", m.displayName(), "Method", "code",
                            summaryJson,
                            fields.summaryMarkdown(),
                            fields.executiveSummary(),
                            fields.technicalBreakdown(),
                            fields.dataflow());

                    processedMethods++;
                }
            }

            if (store.nodeExists(appId, rel, "file")) {
                continue;
            }

            List<Map<String, String>> methodSummaries = store.getMethodSummariesForFile(appId, rel + "#");

            ObjectNode input = JsonUtil.mapper().createObjectNode();
            input.put("full_path", absFile.toString());
            input.put("element_type", "file");
            input.put("name", absFile.getFileName().toString());
            input.put("file_type", fileType);

            if (!methodSummaries.isEmpty()) {
                ArrayNode ms = input.putArray("method_summaries");
                for (Map<String, String> s : methodSummaries) {
                    ObjectNode o = ms.addObject();
                    o.put("name", s.getOrDefault("name", ""));
                    o.put("executive_summary", truncate(s.getOrDefault("executive_summary", ""), 800));
                    o.put("summary_markdown", truncate(s.getOrDefault("summary_markdown", ""), 2000));
                }
            } else {
                input.put("content", truncate(safeRead(absFile), 8000));
            }

            String md = llama.summarizeNodeMarkdown(model, prompts, input);
            ObjectNode summaryJson = MarkdownSummaryParser.toSummaryJson(
                    input.get("full_path").asText(),
                    "file",
                    absFile.getFileName().toString(),
                    fileType,
                    "File",
                    md
            );
            PersistedFields fields = PersistedFields.from(summaryJson);

            store.upsertNode(appId, rel, "file", absFile.getFileName().toString(), "File", fileType,
                    summaryJson,
                    fields.summaryMarkdown(),
                    fields.executiveSummary(),
                    fields.technicalBreakdown(),
                    fields.dataflow());

            processedFiles++;
        }

        List<String> folderRels = new ArrayList<>();
        for (Path absFolder : results.folders()) {
            folderRels.add(toRelativeStable(root, absFolder));
        }

        folderRels.sort(Comparator.comparingInt(BottomUpProcessor::depthOf).reversed());

        if (maxFolders != null && maxFolders >= 0 && folderRels.size() > maxFolders) {
            folderRels = folderRels.subList(0, maxFolders);
        }

        for (String folderRel : folderRels) {
            if (folderRel.isBlank()) {
                continue;
            }
            if (store.nodeExists(appId, folderRel, "folder")) {
                continue;
            }

            int parentDepth = depthOf(folderRel);
            String prefix = folderRel.endsWith("/") ? folderRel : folderRel + "/";

            List<Map<String, String>> childFiles = new ArrayList<>();
            List<Map<String, String>> childFolders = new ArrayList<>();

            for (Map<String, String> node : store.getNodesByPrefix(appId, prefix)) {
                String p = node.getOrDefault("full_path", "");
                String et = node.getOrDefault("element_type", "");
                int d = depthOf(p);
                if (d != parentDepth + 1) {
                    continue;
                }
                if ("file".equals(et)) {
                    childFiles.add(node);
                } else if ("folder".equals(et)) {
                    childFolders.add(node);
                }
            }

            ObjectNode input = JsonUtil.mapper().createObjectNode();
            input.put("full_path", root.resolve(folderRel).toString());
            input.put("element_type", "folder");
            input.put("name", Path.of(folderRel).getFileName() == null ? folderRel : Path.of(folderRel).getFileName().toString());

            ArrayNode filesArr = input.putArray("file_summaries");
            for (Map<String, String> f : childFiles) {
                ObjectNode o = filesArr.addObject();
                o.put("full_path", f.getOrDefault("full_path", ""));
                o.put("name", f.getOrDefault("name", ""));
                o.put("summary_markdown", truncate(f.getOrDefault("summary_markdown", ""), 2000));
            }

            ArrayNode foldersArr = input.putArray("subfolder_summaries");
            for (Map<String, String> f : childFolders) {
                ObjectNode o = foldersArr.addObject();
                o.put("full_path", f.getOrDefault("full_path", ""));
                o.put("name", f.getOrDefault("name", ""));
                o.put("summary_markdown", truncate(f.getOrDefault("summary_markdown", ""), 2000));
            }

                String md = llama.summarizeNodeMarkdown(model, prompts, input);
                ObjectNode summaryJson = MarkdownSummaryParser.toSummaryJson(
                    input.get("full_path").asText(),
                    "folder",
                    input.get("name").asText(),
                    "n/a",
                    "Folder",
                    md
                );
                PersistedFields fields = PersistedFields.from(summaryJson);

            store.upsertNode(appId, folderRel, "folder", input.get("name").asText(), "Folder", "n/a",
                    summaryJson,
                    fields.summaryMarkdown(),
                    fields.executiveSummary(),
                    fields.technicalBreakdown(),
                    fields.dataflow());

            processedFolders++;
        }

        store.putProcessingState(appId, "worker.processed.methods", String.valueOf(processedMethods));
        store.putProcessingState(appId, "worker.processed.files", String.valueOf(processedFiles));
        store.putProcessingState(appId, "worker.processed.folders", String.valueOf(processedFolders));
        store.putProcessingState(appId, "worker.lastRunAt", Instant.now().toString());
    }

    private static int depthOf(String rel) {
        if (rel == null || rel.isBlank()) {
            return 0;
        }
        String p = rel.replace('\\', '/');
        int depth = 0;
        for (int i = 0; i < p.length(); i++) {
            if (p.charAt(i) == '/') {
                depth++;
            }
        }
        return depth;
    }

    private static String toRelativeStable(Path root, Path absolute) {
        Path abs = absolute.toAbsolutePath().normalize();
        Path r = root.toAbsolutePath().normalize();
        try {
            if (abs.startsWith(r)) {
                String rel = r.relativize(abs).toString();
                return rel.replace('\\', '/');
            }
        } catch (Exception ignored) {
        }
        return abs.toString().replace('\\', '/');
    }

    private static String safeRead(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String truncate(String s, int maxChars) {
        if (s == null) {
            return null;
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "\n...<truncated>...";
    }

    private record PersistedFields(String summaryMarkdown, String executiveSummary, String technicalBreakdown, String dataflow) {
        static PersistedFields from(ObjectNode node) {
            ObjectNode summary = (ObjectNode) node.path("summary");
            String md = summary.path("summary_markdown").asText(null);
            String exec = summary.path("executive_summary").asText(null);
            String tech = summary.path("technical_breakdown").asText(null);
            String df = summary.path("dataflow").asText(null);
            return new PersistedFields(md, exec, tech, df);
        }
    }
}
