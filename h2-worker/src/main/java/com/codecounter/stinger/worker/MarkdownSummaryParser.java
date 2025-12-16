package com.codecounter.stinger.worker;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MarkdownSummaryParser {

    private MarkdownSummaryParser() {
    }

    public static ObjectNode toSummaryJson(String fullPath, String elementType, String name, String fileType, String granularity, String markdown) {
        Map<String, String> sections = splitSections(markdown);

        String exec = sections.getOrDefault("1", "").trim();
        String tech = sections.getOrDefault("2", "").trim();
        String deps = sections.getOrDefault("3", "").trim();
        String conceptsRaw = sections.getOrDefault("4", "");
        String flow = sections.getOrDefault("5", "").trim();
        String uniqueRaw = sections.getOrDefault("6", "");

        List<String> keyConcepts = parseBullets(conceptsRaw, 50);
        List<String> uniqueTerms = parseBullets(uniqueRaw, 100);

        ObjectNode root = JsonUtil.mapper().createObjectNode();
        root.put("full_path", fullPath);
        root.put("element_type", elementType);
        root.put("name", name);

        ObjectNode summary = root.putObject("summary");
        summary.put("executive_summary", exec);
        summary.put("technical_breakdown", tech);

        ObjectNode depsObj = summary.putObject("dependencies_and_interactions");
        depsObj.putArray("imports");
        depsObj.putArray("calls_to");
        depsObj.putArray("called_by");
        depsObj.putArray("uses");
        depsObj.putArray("data_sources");

        ArrayNode concepts = summary.putArray("key_concepts");
        for (String c : keyConcepts) {
            concepts.add(c);
        }

        summary.put("dataflow", flow);

        ArrayNode unique = summary.putArray("unique_code_words");
        for (String u : uniqueTerms) {
            unique.add(u);
        }

        summary.put("summary_markdown", markdown == null ? "" : markdown.trim());

        ObjectNode metadata = root.putObject("metadata");
        metadata.put("granularity", granularity == null ? "unknown" : granularity);
        metadata.put("file_type", fileType == null ? "unknown" : fileType);

        // Optional: stash raw deps section text in metadata for debugging.
        if (!deps.isBlank()) {
            metadata.put("deps_text", deps);
        }

        return root;
    }

    private static Map<String, String> splitSections(String markdown) {
        Map<String, StringBuilder> tmp = new HashMap<>();
        String current = null;

        if (markdown == null) {
            return Map.of();
        }

        String[] lines = markdown.replace("\r", "").split("\n");
        for (String line : lines) {
            String t = line.trim();
            String section = sectionKey(t);
            if (section != null) {
                current = section;
                tmp.computeIfAbsent(current, k -> new StringBuilder());
                continue;
            }
            if (current == null) {
                continue;
            }
            tmp.get(current).append(line).append('\n');
        }

        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, StringBuilder> e : tmp.entrySet()) {
            out.put(e.getKey(), e.getValue().toString());
        }
        return out;
    }

    private static String sectionKey(String trimmedLine) {
        // Expected headings like: "### 1. Executive Summary" etc.
        if (!trimmedLine.startsWith("###")) {
            return null;
        }
        String rest = trimmedLine.substring(3).trim();
        if (rest.length() >= 2 && Character.isDigit(rest.charAt(0)) && rest.charAt(1) == '.') {
            return String.valueOf(rest.charAt(0));
        }
        if (rest.length() >= 2 && Character.isDigit(rest.charAt(0)) && rest.startsWith("0.")) {
            return "0";
        }
        return null;
    }

    private static List<String> parseBullets(String text, int maxItems) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }

        String[] lines = text.replace("\r", "").split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("- ")) {
                String v = t.substring(2).trim();
                if (!v.isBlank()) {
                    out.add(v);
                }
            }
            if (out.size() >= maxItems) {
                break;
            }
        }
        return out;
    }
}
