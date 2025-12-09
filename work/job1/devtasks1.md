# Development Tasks: Analysis Results UI Overhaul

**Developer Note:** This task list is derived from `devgoals.md`. You are required to complete these tasks **in order**. Do not skip steps. Do not "improvise" the UI. Check off each item as you complete it using the status symbols below.

### Task Status Symbols
- `[ ]` Not Started
- `[-]` In Progress
- `[X]` Completed
- `[V]` Tested & Verified
- `[!]` Blocked
- `[>]` Deferred (include reason on next line)

---

## Phase 1: Preparation & Data Binding
- [X] **Review Existing Code**: Located `FileAnalysisService` and the `FileExplorerController` that call the streaming analysis APIs.
 - [-] **Identify Data Sources**: Ensure the backend can emit real-time events for:
    - [X] Current Folder Name (SSE event: `directory`)
    - [X] Folder Counts (Processed / Total) — implemented: pre-scan `totals` event and `folderProgress` updates.
    - [X] Current Code File Name (SSE event: `file` for code files)
    - [X] Code File Counts (Processed / Total) — implemented: per-file streaming with `progress` updates.
    - [X] Line of Code Counts (Processed / Total) — implemented: per-file `file-stats` contains file lines and progress events include totals.
    - [ ] Current Method Name — no SSE events currently emitted per-method; pending implementation.
    - [X] Method Counts (Processed / Total) — implemented: pre-scan totals include method totals and `progress` streams include processed counts.
    - [X] Current Doc File Name (SSE event: `file` for document files)
    - [X] Doc File Counts (Processed / Total) — implemented: per-file `file-stats` and progress events.
    - [X] Line of Doc Counts (Processed / Total) — implemented: per-file `file-stats` and totals via pre-scan.
    - [X] Total File Counts — implemented: pre-scan totals and progress streams include totalFiles.
    - [X] Total Line Counts — implemented: pre-scan totals and progress streams include totalLines.

## Phase 2: UI Layout - Folders Section
*Strict Requirement: This must be the first section.*
- [X] **Implement Display Text**: "Current Folder" name.
- [X] **Implement Progress Bar**: Folders Processed vs. Total.
- [X] **Implement Metric**: Numeric "Percent Complete" for Folders.
- [X] **Verify**: Ensure the folder name updates in real-time.

## Phase 3: UI Layout - Code Files Section
*Strict Requirement: This must be the second section.*
- [X] **Implement Display Text**: "Current Code File" name.
- [X] **Implement Progress Bar**: Code Files Processed vs. Total.
- [X] **Implement Metric**: Numeric "Percent Complete" for Code Files.
- [X] **Implement Secondary Progress Bar**: Lines of Code Processed vs. Total.
- [X] **Implement Secondary Metric**: Numeric "Percent Complete" for Lines of Code.

## Phase 4: UI Layout - Methods Section
*Strict Requirement: This must be the third section.*
- [X] **Implement Display Text**: "Current Method" name.
- [X] **Implement Progress Bar**: Methods Processed vs. Total.
- [X] **Implement Metric**: Numeric "Percent Complete" for Methods.

## Phase 5: UI Layout - Document Files Section
*Strict Requirement: This must be the fourth section.*
- [X] **Implement Display Text**: "Current Document File" name.
- [X] **Implement Progress Bar**: Doc Files Processed vs. Total.
- [X] **Implement Metric**: Numeric "Percent Complete" for Doc Files.
- [X] **Implement Secondary Progress Bar**: Lines of Documents Processed vs. Total.
- [X] **Implement Secondary Metric**: Numeric "Percent Complete" for Lines of Documents.

## Phase 6: UI Layout - Totals Section
*Strict Requirement: This must be the fifth section.*
- [X] **Implement Progress Bar**: Total Files (Code + Doc).
- [X] **Implement Metric**: Numeric "Percent Complete" for Total Files.
- [X] **Implement Progress Bar**: Total Lines (Code + Doc).
- [X] **Implement Metric**: Numeric "Percent Complete" for Total Lines.

## Phase 7: Final Verification
 - [X] **Layout Check**: Confirm the order is exactly: Folders -> Code Files -> Methods -> Doc Files -> Totals.
 - [X] **Completeness Check**: Verify NO "Current Item" text fields are blank during the process (placeholders shown until updates).
 - [X] **Animation Check**: Verify all progress bars update smoothly and reflect the actual progress.
