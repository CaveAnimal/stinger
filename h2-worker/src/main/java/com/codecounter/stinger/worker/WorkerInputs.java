package com.codecounter.stinger.worker;

import java.nio.file.Path;

public record WorkerInputs(
        Path savedResultsDir,
        Path baseDir,
        String applicationName,
        String runFolder,
        Path outRoot,
        Path h2Dir,
        Path h2DbPathPrefix,
        Path rootPath
) {

    public static WorkerInputs fromSavedResultsDir(Path savedResultsDir, Path baseDir) {
        Path normalized = savedResultsDir.toAbsolutePath().normalize();
        Path runFolder = normalized.getFileName();
        if (runFolder == null) {
            throw new IllegalArgumentException("savedResultsDir has no run folder segment: " + savedResultsDir);
        }
        Path appFolderPath = normalized.getParent();
        if (appFolderPath == null || appFolderPath.getFileName() == null) {
            throw new IllegalArgumentException("savedResultsDir must be code_counter_results/<app>/<run>: " + savedResultsDir);
        }
        String appName = appFolderPath.getFileName().toString();
        String run = runFolder.toString();

        Path outRoot = baseDir.toAbsolutePath().normalize().resolve(appName).resolve(run);
        Path h2Dir = outRoot.resolve("h2");
        Path h2DbPrefix = h2Dir.resolve("stinger");

        // rootPath will be read from summary.txt; placeholder here, set later.
        return new WorkerInputs(normalized, baseDir, appName, run, outRoot, h2Dir, h2DbPrefix, null);
    }

    public WorkerInputs withRootPath(Path rootPath) {
        return new WorkerInputs(savedResultsDir, baseDir, applicationName, runFolder, outRoot, h2Dir, h2DbPathPrefix, rootPath);
    }

    public String applicationName() {
        return applicationName;
    }

    public String runFolder() {
        return runFolder;
    }
}
