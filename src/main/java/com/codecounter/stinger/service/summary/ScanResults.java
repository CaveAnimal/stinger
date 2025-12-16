package com.codecounter.stinger.service.summary;

import java.nio.file.Path;
import java.util.List;

public record ScanResults(
        Path savedResultsDir,
        Path rootPath,
        List<Path> codeFiles,
        List<Path> docFiles,
        List<Path> folders
) {
}
