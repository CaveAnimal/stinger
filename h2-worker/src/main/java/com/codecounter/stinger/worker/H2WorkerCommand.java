package com.codecounter.stinger.worker;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.time.Instant;

@Command(
        name = "stinger-h2-worker",
        mixinStandardHelpOptions = true,
        description = "Persists bottom-up llama-service node summaries into per-app/run H2."
)
public final class H2WorkerCommand implements Runnable {

    @Option(names = {"--savedResultsDir"}, required = true, description = "Path to code_counter_results/<app>/<run>/ folder")
    private Path savedResultsDir;

    @Option(names = {"--baseDir"}, defaultValue = "code_summary_results", description = "Base output directory (default: ${DEFAULT-VALUE})")
    private Path baseDir;

    @Option(names = {"--llmBaseUrl"}, defaultValue = "http://localhost:8080", description = "llama.cpp OpenAI-compatible base URL (default: ${DEFAULT-VALUE})")
    private String llmBaseUrl;

    @Option(names = {"--model"}, description = "Model id to use (defaults to first from /v1/models)")
    private String model;

    @Option(names = {"--promptsDir"}, defaultValue = "work/prompts", description = "Directory containing SummaryDefinition2.md and Bottom-UpCodebaseAnalysis2.md")
    private Path promptsDir;

    @Option(names = {"--maxFiles"}, description = "Limit number of files processed (for smoke tests)")
    private Integer maxFiles;

    @Option(names = {"--maxFolders"}, description = "Limit number of folders processed (for smoke tests)")
    private Integer maxFolders;

    @Option(names = {"--skipMethods"}, defaultValue = "false", description = "Skip method node extraction/summaries")
    private boolean skipMethods;

    @Option(names = {"--timeoutSeconds"}, defaultValue = "120", description = "HTTP timeout seconds (default: ${DEFAULT-VALUE})")
    private long timeoutSeconds;

    @Override
    public void run() {
        try {
            PromptBundle prompts = PromptBundle.load(promptsDir);
            LlamaClient llama = new LlamaClient(llmBaseUrl, timeoutSeconds);
            String resolvedModel = llama.resolveModel(model);

            ResultsReader results = ResultsReader.load(savedResultsDir, null);
            WorkerInputs inputs = WorkerInputs.fromSavedResultsDir(savedResultsDir, baseDir).withRootPath(results.rootPath());

            try (H2Store store = H2Store.open(inputs)) {
                store.ensureSchema();

                long appId = store.upsertApplication(inputs.applicationName(), inputs.runFolder(), inputs.rootPath().toString());
                store.putProcessingState(appId, "worker.startedAt", Instant.now().toString());
                store.putProcessingState(appId, "worker.llmBaseUrl", llmBaseUrl);
                store.putProcessingState(appId, "worker.model", resolvedModel);

                BottomUpProcessor processor = new BottomUpProcessor(prompts, llama, resolvedModel, store, appId);
                processor.process(results, maxFiles, maxFolders, skipMethods);

                store.putProcessingState(appId, "worker.finishedAt", Instant.now().toString());

                long nodeCount = store.countNodesForApplication(appId);
                long appCount = store.countApplications();
                System.out.println("H2 worker complete. applications=" + appCount + ", nodes(for app)=" + nodeCount);
            }
        } catch (Exception e) {
            System.err.println("Worker failed: " + e.getMessage());
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
    }
}
