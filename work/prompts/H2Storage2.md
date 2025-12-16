# System Prompt: H2 Storage (v2)

You are designing a **persistent, local-only H2 storage layer** for Stinger’s bottom-up analysis results.

This prompt defines **when the H2 step runs, strict data isolation rules, storage layout, resumability rules, and a concrete H2 schema**.

For the analysis JSON record shape and how summaries are produced (including code-vs-document handling and method/file/folder rules), you MUST follow:

- `work/prompts/Bottom-UpCodebaseAnalysis2.md`
- `work/prompts/SummaryDefinition2.md`

Do **not** duplicate summarization instructions covered by those files.

---

## Scope and Current Implementation Status

This spec covers the full desired behavior. However, Stinger currently implements only the **post-scan hook + directory preparation**:

- After a scan is saved, Stinger calls a lightweight hook with the saved results folder.
- When `stinger.h2.enabled=true`, the hook creates the required output directories under `stinger.h2.base-dir`.
- It does **not** yet create an H2 database or write any node rows.

This prompt therefore defines two phases:

- **Phase 0 (Implemented):** optional post-scan hook + output directory preparation.
- **Phase 1 (To Implement):** H2 database initialization + schema + persisting node summaries + resumability.

---

## Goals

- Persist analysis outputs for **each application/run** in H2.
- Keep each application’s data **strictly separated** to prevent cross-contamination.
- Support **restart/resume** if interrupted.
- Store the full JSON object returned from the llama-service for each node.
- Keep outputs in a folder structure under `code_summary_results/` mirroring `code_counter_results/`.

---

## Definitions

- `<ApplicationName>`: the folder name under `code_counter_results/` (sanitized root folder name).
- `<RunFolder>`: the run folder name (e.g., `2025_12_05_a`).
- `savedResultsDir`: the concrete folder produced by the code-counter scan, typically:
  - `code_counter_results/<ApplicationName>/<RunFolder>/`
- `outRoot`: the parallel folder under the H2 base directory:
  - `<baseDir>/<ApplicationName>/<RunFolder>/`

---

## Optional Execution & Configuration

H2 persistence is **optional** and must run only when explicitly enabled. Default must be **disabled**.

Configuration keys (defaults shown):

- `stinger.h2.enabled=false`                     (enable/disable the post-scan H2 step)
- `stinger.h2.mode=perApp`                       (`perApp` or `shared`)
- `stinger.h2.base-dir=code_summary_results`     (base folder for all H2 artifacts)
- `stinger.h2.create_marked_sources=true`        (create `processing/marked_sources/` when enabled)
- `stinger.h2.resume_check=true`                 (enable resume checking when enabled)

Security requirement (non-negotiable): H2 storage must remain **local-only**. Do not use remote JDBC URLs, network shares, or cloud-backed storage.

---

## Lifecycle / Integration Point (Non-Negotiable)

The H2 step runs **after** a scan is successfully saved.

- Input: the `savedResultsDir` returned by saving the scan.
- The H2 step must be **best-effort**:
  - If it fails, it must not fail the scan request.
  - Failures must be logged.

---

## Isolation Requirement (Non-Negotiable)

All information from each application/run MUST be kept separate from all other applications/runs.

Acceptable isolation strategies:

1) **Preferred:** one H2 database file per application/run.
2) Acceptable: a shared H2 database containing multiple applications/runs **only if** every query is constrained by `application_id` and schema constraints/indexes enforce isolation.

If unsure, choose (1).

---

## Storage Layout

Create a parallel structure under `stinger.h2.base-dir`.

### Required directories (Phase 0)

- `<baseDir>/<ApplicationName>/<RunFolder>/h2/`
- `<baseDir>/<ApplicationName>/<RunFolder>/processing/`
- `<baseDir>/<ApplicationName>/<RunFolder>/processing/marked_sources/` (only if `stinger.h2.create_marked_sources=true`)

### Database files (Phase 1)

#### Mode: `perApp` (recommended)

- H2 file database path prefix:
  - `<baseDir>/<ApplicationName>/<RunFolder>/h2/stinger`
- Expected H2 files (created by H2):
  - `stinger.mv.db`
  - `stinger.trace.db` (only if trace is enabled)

#### Mode: `shared` (optional)

- One shared DB under the base directory:
  - `<baseDir>/_shared/h2/stinger` (recommended shared location)
- All rows must be scoped by `application_id`.

### Processing artifacts (Phase 1)

- `<baseDir>/<ApplicationName>/<RunFolder>/processing/progress.json`
- `<baseDir>/<ApplicationName>/<RunFolder>/processing/processed_files.txt`
- `<baseDir>/<ApplicationName>/<RunFolder>/processing/marked_sources/...` (optional)

---

## Resumability / “[x]” Marking (Phase 1)

If `stinger.h2.resume_check=true`, the system must be able to resume after interruption.

Rules:

- Never modify original files in the user’s project.
- Copy input code/doc files into `processing/marked_sources/` preserving relative paths.
- When a file is fully processed and all required rows are stored, rewrite the copied file so **each line is prefixed** with `[x] `.
- Files not yet processed must remain unprefixed.
- On restart:
  - If a file exists in `marked_sources/` and every non-empty line begins with `[x] `, treat it as processed and skip it.

Note: The marker is purely for restart tracking; it must not change the semantic meaning of summaries.

---

## What Must Be Stored (Minimum Fields)

Per application/run:

- Application name (`<ApplicationName>`)
- Run folder (`<RunFolder>`)
- Root path (optional, but recommended)

Per analyzed node (method/paragraph, file, folder):

- Project-relative path (stable key; do not store machine-absolute paths as the primary key)
- Node type (`method` / `file` / `folder`)
- Full JSON object returned from llama-service
- Optional extracted `summary_markdown` (recommended for quick display)

---

## H2 Schema (Recommended)

This schema is written for H2 and supports both `perApp` and `shared` modes.

### DDL

```sql
CREATE TABLE IF NOT EXISTS APPLICATION (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  name VARCHAR(512) NOT NULL,
  version VARCHAR(256),
  root_path VARCHAR(2048),
  run_folder VARCHAR(128) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS UX_APPLICATION_NAME_RUN
  ON APPLICATION(name, run_folder);

CREATE INDEX IF NOT EXISTS IX_APPLICATION_NAME
  ON APPLICATION(name);

CREATE TABLE IF NOT EXISTS NODE (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  application_id BIGINT NOT NULL,
  full_path VARCHAR(4096) NOT NULL,
  element_type VARCHAR(32) NOT NULL,
  name VARCHAR(1024) NOT NULL,
  granularity VARCHAR(32) NOT NULL,
  file_type VARCHAR(32) NOT NULL,
  summary_json CLOB NOT NULL,
  summary_markdown CLOB,
  executive_summary CLOB,
  technical_breakdown CLOB,
  dataflow CLOB,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT FK_NODE_APPLICATION FOREIGN KEY (application_id)
    REFERENCES APPLICATION(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS UX_NODE_APP_PATH_TYPE
  ON NODE(application_id, full_path, element_type);

CREATE INDEX IF NOT EXISTS IX_NODE_APP_ELEMENT
  ON NODE(application_id, element_type);

CREATE INDEX IF NOT EXISTS IX_NODE_APP_PATH
  ON NODE(application_id, full_path);

CREATE TABLE IF NOT EXISTS NODE_TERM (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  application_id BIGINT NOT NULL,
  node_id BIGINT NOT NULL,
  term VARCHAR(512) NOT NULL,
  CONSTRAINT FK_NODE_TERM_APPLICATION FOREIGN KEY (application_id)
    REFERENCES APPLICATION(id) ON DELETE CASCADE,
  CONSTRAINT FK_NODE_TERM_NODE FOREIGN KEY (node_id)
    REFERENCES NODE(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS IX_NODE_TERM_APP_TERM
  ON NODE_TERM(application_id, term);

CREATE INDEX IF NOT EXISTS IX_NODE_TERM_NODE
  ON NODE_TERM(node_id);

CREATE TABLE IF NOT EXISTS PROCESSING_STATE (
  application_id BIGINT NOT NULL,
  k VARCHAR(256) NOT NULL,
  v CLOB NOT NULL,
  PRIMARY KEY (application_id, k),
  CONSTRAINT FK_PROCESSING_STATE_APPLICATION FOREIGN KEY (application_id)
    REFERENCES APPLICATION(id) ON DELETE CASCADE
);
```

### Notes

- `APPLICATION(name, run_folder)` is unique.
- `NODE(application_id, full_path, element_type)` is unique and provides idempotent upserts.
- `full_path` must be project-relative (stable across machines).

---

## Write Rules (Phase 1)

- Only insert/update rows for the current application/run.
- Writes must be idempotent:
  - If a node already exists (same `application_id`, `full_path`, `element_type`), update `summary_json`, optional extracted fields, and `updated_at`.
- Do not create cross-application joins or mixed-run merges.
- Prefer H2 `MERGE INTO` for upsert behavior.

---

## Read Rules (Retrieval)

All retrieval must be isolated:

- In `perApp` mode: isolation is enforced by separate DB file per app/run.
- In `shared` mode: every query must include `application_id`.

Common retrieval patterns:

- Get a node by (`full_path`, `element_type`).
- List children of a folder (path prefix search).
- Search by term via `NODE_TERM`.

---

## Output Expectations

### Phase 0 (Implemented)

- If `stinger.h2.enabled=false`: skip everything.
- If `stinger.h2.enabled=true`: create the required directory structure under `<baseDir>/<ApplicationName>/<RunFolder>/`.

### Phase 1 (To Implement)

- Create/initialize the H2 database and schema.
- Store one row per node processed (method/file/folder).
- Update resumability markers and/or processing state.

All operations must be local-only (no internet access).
