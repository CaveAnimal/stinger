package com.codecounter.stinger.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtil() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static JsonNode parseLenientJsonObject(String text) throws JsonProcessingException {
        String trimmed = text == null ? "" : text.trim();

        // Remove common fenced blocks
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence >= 0) {
                trimmed = trimmed.substring(0, lastFence);
            }
            trimmed = trimmed.trim();
        }

        trimmed = extractFirstJsonObject(trimmed);
        trimmed = sanitizeJsonLikeString(trimmed);
        return MAPPER.readTree(trimmed);
    }

    private static String extractFirstJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return text;
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }

        // Best effort: return from first '{' onward.
        return text.substring(start);
    }

    /**
     * Some local LLMs emit "JSON" that contains raw newlines inside quoted strings,
     * which is invalid JSON. This rewrites those control characters as escapes.
     */
    private static String sanitizeJsonLikeString(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                    out.append(c);
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    out.append(c);
                    continue;
                }
                if (c == '"') {
                    inString = false;
                    out.append(c);
                    continue;
                }

                if (c == '\n') {
                    out.append("\\n");
                    continue;
                }
                if (c == '\r') {
                    out.append("\\r");
                    continue;
                }
                if (c == '\t') {
                    out.append("\\t");
                    continue;
                }

                out.append(c);
                continue;
            }

            if (c == '"') {
                inString = true;
                out.append(c);
                continue;
            }

            out.append(c);
        }

        return out.toString();
    }
}
