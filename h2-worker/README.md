# Stinger H2 Worker

A standalone CLI that reads an existing Stinger scan output folder (`code_counter_results/<app>/<run>/`) and persists bottom-up llama-service summaries into a per-app/run H2 database under `code_summary_results/<app>/<run>/h2/`.

## Prereqs

- Java 21+
- A running llama.cpp OpenAI-compatible server (default: `http://localhost:8080`)

## Build

```powershell
mvn -f .\h2-worker\pom.xml -DskipTests package
```

## Run

```powershell
java -jar .\h2-worker\target\stinger-h2-worker-1.0.0-SNAPSHOT.jar --savedResultsDir .\code_counter_results\stngr-001\2025_12_05_a --llmBaseUrl http://localhost:8080
```

### Useful options

- `--model <id>`: model id from `/v1/models` (if omitted, uses the first model returned)
- `--maxFiles <n>` / `--maxFolders <n>`: limit processing for smoke tests
- `--skipMethods`: skip Java method extraction/summaries
- `--promptsDir <path>`: defaults to `work/prompts`

## Output

- H2 DB: `code_summary_results/<app>/<run>/h2/stinger.mv.db`
- Resumability / state: stored in H2 `PROCESSING_STATE` (no file marking)
