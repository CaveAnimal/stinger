package com.stinger.analyzer.model;

public class AnalysisResult {
    private int totalFolders;
    private int totalFiles;
    private int codeFiles;
    private int documentFiles;
    private int otherFiles;
    private int totalMethods;
    private int totalLines;
    private String path;

    public AnalysisResult() {
    }

    public AnalysisResult(String path) {
        this.path = path;
    }

    // Getters and setters
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

    public int getCodeFiles() {
        return codeFiles;
    }

    public void setCodeFiles(int codeFiles) {
        this.codeFiles = codeFiles;
    }

    public int getDocumentFiles() {
        return documentFiles;
    }

    public void setDocumentFiles(int documentFiles) {
        this.documentFiles = documentFiles;
    }

    public int getOtherFiles() {
        return otherFiles;
    }

    public void setOtherFiles(int otherFiles) {
        this.otherFiles = otherFiles;
    }

    public int getTotalMethods() {
        return totalMethods;
    }

    public void setTotalMethods(int totalMethods) {
        this.totalMethods = totalMethods;
    }

    public int getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(int totalLines) {
        this.totalLines = totalLines;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void incrementFolders() {
        this.totalFolders++;
    }

    public void incrementFiles() {
        this.totalFiles++;
    }

    public void incrementCodeFiles() {
        this.codeFiles++;
    }

    public void incrementDocumentFiles() {
        this.documentFiles++;
    }

    public void incrementOtherFiles() {
        this.otherFiles++;
    }

    public void addMethods(int methods) {
        this.totalMethods += methods;
    }

    public void addLines(int lines) {
        this.totalLines += lines;
    }

    public void merge(AnalysisResult other) {
        this.totalFolders += other.totalFolders;
        this.totalFiles += other.totalFiles;
        this.codeFiles += other.codeFiles;
        this.documentFiles += other.documentFiles;
        this.otherFiles += other.otherFiles;
        this.totalMethods += other.totalMethods;
        this.totalLines += other.totalLines;
    }
}
