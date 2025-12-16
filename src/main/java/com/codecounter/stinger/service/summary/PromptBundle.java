package com.codecounter.stinger.service.summary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record PromptBundle(String summaryDefinitionPrompt) {

    public static PromptBundle loadOrDefault(Path promptsDir) {
        if (promptsDir != null) {
            try {
                Path summaryDef = promptsDir.resolve("SummaryDefinition2.md");
                if (Files.exists(summaryDef)) {
                    // Ensure prompt exists, but keep runtime prompt compact for small context windows.
                    Files.readString(summaryDef, StandardCharsets.UTF_8);
                    return new PromptBundle(compactSummaryDefinition2());
                }
            } catch (IOException ignored) {
            }
        }
        return new PromptBundle(compactSummaryDefinition2());
    }

    private static String compactSummaryDefinition2() {
        return "You are the Recursive Analysis Engine for Stinger.\n" +
            "You summarize ONE node at a time and produce a Markdown summary that can be used to summarize parents.\n\n" +
            "Context keys you may be given: granularity=Method|File|Folder, file_type=code|document|other. If unknown, infer conservatively.\n\n" +
            "Your Markdown output MUST have these sections, in this exact order:\n" +
            "0. Metadata (Granularity, FileType, Name, Path)\n" +
            "1. Executive Summary\n" +
            "2. Technical / Content Breakdown\n" +
            "3. Dependencies & Interactions\n" +
            "4. Key Concepts\n" +
            "5. Data / Information Flow\n" +
            "6. Unique Terms\n\n" +
            "Strategy:\n" +
            "- Method+code: single responsibility, step-by-step flow, direct calls/deps, inputs/outputs, unique identifiers.\n" +
            "- Method+document: treat as a doc chunk; summarize what it communicates, structure/steps, referenced systems/commands/config, workflows.\n" +
            "- File+code: synthesize file responsibility across contained methods/classes; mention entry points, helpers, imports/frameworks, state, IO.\n" +
            "- File+document: summarize purpose/audience, section structure, commands/config/URLs mentioned, procedures and expected outcomes.\n" +
            "- Folder: MUST include both code and document perspectives. If one type is absent, explicitly say so.\n\n" +
            "Guardrails: be grounded in provided content; if truncated/ambiguous, say so.";
    }
}
