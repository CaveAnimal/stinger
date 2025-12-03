package com.caveanimal.stinger.model;

public class AnalysisResult {
    private int totalFolders;
    private int totalFiles;
    private int totalCodeFiles;
    private int totalDocFiles;
    private int totalMethods;
    private long totalLines;
    private String path;

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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
