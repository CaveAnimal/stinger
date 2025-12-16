package com.codecounter.stinger.service;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.codecounter.stinger.config.H2StorageProperties;
import com.codecounter.stinger.service.summary.BottomUpSummarizer;
import com.codecounter.stinger.service.summary.LlamaClient;
import com.codecounter.stinger.service.summary.PromptBundle;
import com.codecounter.stinger.service.summary.ScanResults;
import com.codecounter.stinger.service.summary.ScanResultsReader;
import com.codecounter.stinger.service.summary.SummaryH2Store;

@Service
public class H2StorageService {

    private static final Logger logger = LoggerFactory.getLogger(H2StorageService.class);

    private final H2StorageProperties props;

    public H2StorageService(H2StorageProperties props) {
        this.props = props;
    }

    /**
     * Hook invoked after code-counter results are saved. This method is intentionally lightweight:
     * it validates configuration, determines the per-app output folder, and ensures required
     * directories exist.
     */
    public void onCodeCounterResultsSaved(Path savedResultsDir) {
        if (savedResultsDir == null) return;
        if (!props.isEnabled()) {
            logger.debug("H2 storage disabled (stinger.h2.enabled=false); skipping for {}", savedResultsDir);
            return;
        }

        try {
            Path runFolder = savedResultsDir.getFileName();
            Path appFolder = savedResultsDir.getParent() != null ? savedResultsDir.getParent().getFileName() : null;
            if (runFolder == null || appFolder == null) {
                logger.warn("Cannot infer app/run from saved results path: {}", savedResultsDir);
                return;
            }

            String appName = appFolder.toString();
            String runName = runFolder.toString();

            Path base = resolveBaseDir(props.getBaseDir());
            Path outRoot = base.resolve(appName).resolve(runName);

            Path h2Dir = outRoot.resolve("h2");
            Files.createDirectories(h2Dir);
            Files.createDirectories(outRoot.resolve("processing"));
            if (props.isCreateMarkedSources()) {
                Files.createDirectories(outRoot.resolve("processing").resolve("marked_sources"));
            }

            Path dbPrefix = h2Dir.resolve("stinger");
            try (SummaryH2Store store = SummaryH2Store.open(h2Dir, dbPrefix)) {
                store.ensureSchema();
                long appId = store.upsertApplication(appName, runName, safeReadRootPath(savedResultsDir));
                store.putProcessingState(appId, "app.name", appName);
                store.putProcessingState(appId, "app.run", runName);
                store.putProcessingState(appId, "app.savedResultsDir", savedResultsDir.toAbsolutePath().normalize().toString());
                store.putProcessingState(appId, "worker.llmBaseUrl", props.getLlmBaseUrl());
                store.putProcessingState(appId, "worker.model", props.getLlmModel());
                store.putProcessingState(appId, "worker.maxFiles", String.valueOf(props.getMaxFiles()));
                store.putProcessingState(appId, "worker.maxFolders", String.valueOf(props.getMaxFolders()));
                store.putProcessingState(appId, "worker.skipMethods", String.valueOf(props.isSkipMethods()));
                store.putProcessingState(appId, "worker.timeoutSeconds", String.valueOf(props.getLlmTimeoutSeconds()));
                store.putProcessingState(appId, "worker.lastStartedAt", Instant.now().toString());

                logger.info("H2 storage prepared for app={} run={} at {} (mode={})", appName, runName, outRoot, props.getMode());

                // Fast no-op mode for tests / offline runs.
                if (props.getMaxFiles() == 0 && props.getMaxFolders() == 0 && props.isSkipMethods()) {
                    store.putProcessingState(appId, "worker.skipped", "true");
                    store.putProcessingState(appId, "worker.lastFinishedAt", Instant.now().toString());
                    return;
                }

                runSummaries(savedResultsDir, store, appId);
                store.putProcessingState(appId, "worker.nodes.count", String.valueOf(store.countNodesForApplication(appId)));
                store.putProcessingState(appId, "worker.lastFinishedAt", Instant.now().toString());
            }
        } catch (Exception e) {
            // Never fail the scan pipeline because H2 setup failed.
            logger.warn("H2 storage preparation failed for {}: {}", savedResultsDir, e.getMessage());
        }
    }

    private void runSummaries(Path savedResultsDir, SummaryH2Store store, long appId) {
        try {
            ScanResults results = ScanResultsReader.read(savedResultsDir);

            PromptBundle prompts = PromptBundle.loadOrDefault(Paths.get("work").resolve("prompts"));
            LlamaClient llama = new LlamaClient(props.getLlmBaseUrl(), props.getLlmTimeoutSeconds());
            String model = llama.resolveModel(props.getLlmModel());

            store.putProcessingState(appId, "worker.resolvedModel", model);

            BottomUpSummarizer summarizer = new BottomUpSummarizer(prompts, llama, model, store, appId);
            summarizer.process(results, props.getMaxFiles(), props.getMaxFolders(), props.isSkipMethods());
        } catch (Exception e) {
            // Never fail the scan pipeline because summarization failed.
            logger.warn("H2 summary persistence failed for {}: {}", savedResultsDir, e.getMessage());
        }
    }

    private String safeReadRootPath(Path savedResultsDir) {
        try {
            return ScanResultsReader.read(savedResultsDir).rootPath().toString();
        } catch (Exception e) {
            return "";
        }
    }

    private Path resolveBaseDir(String baseDir) {
        if (baseDir == null || baseDir.isBlank()) {
            return Paths.get("code_summary_results");
        }
        return Paths.get(baseDir);
    }
}
