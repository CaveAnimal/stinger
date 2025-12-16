package com.codecounter.stinger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stinger.h2")
public class H2StorageProperties {

    /**
     * When true, run the H2 persistence step after a successful code-counter scan.
     */
    private boolean enabled = false;

    /**
     * Storage isolation mode: perApp (preferred) or shared.
     */
    private String mode = "perApp";

    /**
     * Base directory where per-application H2 databases and artifacts are written.
     */
    private String baseDir = "code_summary_results";

    /**
     * When true, create processing/marked_sources and apply [x] line markers on processed files.
     */
    private boolean createMarkedSources = true;

    /**
     * When true, perform resume checks on startup using markers/state.
     */
    private boolean resumeCheck = true;

    /**
     * Base URL for llama.cpp OpenAI-compatible API (e.g., http://localhost:8080).
     */
    private String llmBaseUrl = "http://localhost:8080";

    /**
     * Optional model id. If blank, the summary runner may use a server default.
     */
    private String llmModel = "";

    /**
     * Maximum number of files to summarize. -1 means no limit.
     */
    private int maxFiles = -1;

    /**
     * Maximum number of folders to summarize. -1 means no limit.
     */
    private int maxFolders = -1;

    /**
     * Skip method-level summaries (defaults to true for speed).
     */
    private boolean skipMethods = true;

    /**
     * HTTP timeout in seconds for llama calls.
     */
    private long llmTimeoutSeconds = 120;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public boolean isCreateMarkedSources() {
        return createMarkedSources;
    }

    public void setCreateMarkedSources(boolean createMarkedSources) {
        this.createMarkedSources = createMarkedSources;
    }

    public boolean isResumeCheck() {
        return resumeCheck;
    }

    public void setResumeCheck(boolean resumeCheck) {
        this.resumeCheck = resumeCheck;
    }

    public String getLlmBaseUrl() {
        return llmBaseUrl;
    }

    public void setLlmBaseUrl(String llmBaseUrl) {
        this.llmBaseUrl = llmBaseUrl;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public int getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    public int getMaxFolders() {
        return maxFolders;
    }

    public void setMaxFolders(int maxFolders) {
        this.maxFolders = maxFolders;
    }

    public boolean isSkipMethods() {
        return skipMethods;
    }

    public void setSkipMethods(boolean skipMethods) {
        this.skipMethods = skipMethods;
    }

    public long getLlmTimeoutSeconds() {
        return llmTimeoutSeconds;
    }

    public void setLlmTimeoutSeconds(long llmTimeoutSeconds) {
        this.llmTimeoutSeconds = llmTimeoutSeconds;
    }
}
