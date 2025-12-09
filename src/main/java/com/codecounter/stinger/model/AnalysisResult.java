package com.codecounter.stinger.model;

public class AnalysisResult {
    private int totalFolders;
    private int totalFiles;
    private int totalCodeFiles;
    private int totalDocFiles;
    private int totalOtherFiles;
    private int totalMethods;
    private long totalLines;
    // total lines by category
    private long totalCodeLines;
    private long totalDocLines;
    private String path;
    private String resultsPath;

    public AnalysisResult() {
    }

    public int getTotalFolders() {
        return totalFolders;
    }

    public void setTotalFolders(int totalFolders) {
        this.totalFolders = totalFolders;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public int getTotalCodeFiles() {
        return totalCodeFiles;
    }

    public void setTotalCodeFiles(int totalCodeFiles) {
        this.totalCodeFiles = totalCodeFiles;
    }

    public int getTotalDocFiles() {
        return totalDocFiles;
    }

    public void setTotalDocFiles(int totalDocFiles) {
        this.totalDocFiles = totalDocFiles;
    }

    public int getTotalOtherFiles() {
        return totalOtherFiles;
    }

    public void setTotalOtherFiles(int totalOtherFiles) {
        this.totalOtherFiles = totalOtherFiles;
    }

    public int getTotalMethods() {
        return totalMethods;
    }

    public void setTotalMethods(int totalMethods) {
        this.totalMethods = totalMethods;
    }

    public long getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(long totalLines) {
        this.totalLines = totalLines;
    }

    public long getTotalCodeLines() {
        return totalCodeLines;
    }

    public void setTotalCodeLines(long totalCodeLines) {
        this.totalCodeLines = totalCodeLines;
    }

    public long getTotalDocLines() {
        return totalDocLines;
    }

    public void setTotalDocLines(long totalDocLines) {
        this.totalDocLines = totalDocLines;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getResultsPath() {
        return resultsPath;
    }

    public void setResultsPath(String resultsPath) {
        this.resultsPath = resultsPath;
    }
}
