package com.codecounter.stinger.service.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class LlamaClient {
    private final String baseUrl;
    private final HttpClient http;
    private final Duration timeout;

    public LlamaClient(String baseUrl, long timeoutSeconds) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    public String resolveModel(String preferred) throws IOException, InterruptedException {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }

        URI uri = URI.create(baseUrl + "/v1/models");
        HttpRequest req = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .GET()
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Failed to list models: HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode json = JsonUtil.mapper().readTree(resp.body());
        JsonNode data = json.get("data");
        if (data instanceof ArrayNode array && array.size() > 0) {
            JsonNode first = array.get(0);
            JsonNode id = first.get("id");
            if (id != null && !id.asText().isBlank()) {
                return id.asText();
            }
        }
        throw new IOException("No models returned from /v1/models");
    }

    public String summarizeNodeMarkdown(String model, PromptBundle prompts, ObjectNode nodeInput) throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + "/v1/chat/completions");

        ObjectNode body = JsonUtil.mapper().createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("max_tokens", 2048);

        ArrayNode messages = body.putArray("messages");

        // Some llama.cpp server templates reject the "system" role.
        // To remain compatible, embed all instructions into a single user message.
        String combined = prompts.summaryDefinitionPrompt()
            + "\n\nInput JSON:\n"
            + nodeInput
            + "\n\nOutput requirements:\n"
            + "- Return ONLY the Markdown summary (no JSON, no code fences).\n"
            + "- Use the exact section headings and order from the prompt.\n"
            + "- Keep it concise; prefer bullets.";

        messages.addObject().put("role", "user").put("content", combined);

        HttpRequest req = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("LLM call failed: HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode respJson = JsonUtil.mapper().readTree(resp.body());
        JsonNode content = respJson.at("/choices/0/message/content");
        if (content.isMissingNode()) {
            throw new IOException("LLM response missing choices[0].message.content: " + resp.body());
        }

        return content.asText();
    }
}
