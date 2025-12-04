# Stinger 🐝

**Aerial Code Repository Analysis Tool**

A Java 21 Spring Boot application with a browser-based front end for analyzing code repositories. Navigate through directories and get comprehensive insights into your codebase structure.

## Features

- **File Explorer Interface**: Browse any directory on your system with an intuitive, Windows File Explorer-style interface
- **Recursive Analysis**: Analyze entire directory trees recursively
- **File Classification**: Automatically classify files as code or documentation
- **Code Metrics**:
  - Count folders and files
  - Count methods in code files (with enhanced support for Java)
  - Count lines of code
  - Track totals across folders, files, documents, methods, and lines

## Supported File Types

### Code Files
Java, JavaScript, TypeScript, Python, C++, C, C#, Go, Ruby, PHP, Swift, Kotlin, Rust, Scala, Shell scripts, SQL, HTML, CSS, and more

### Document Files
Markdown, Text files, RST, AsciiDoc, PDF, Word documents

## Requirements

- Java 21 or higher
- Maven 3.6+

## Getting Started

### Build the Application

```bash
mvn clean install
```

### Run the Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### Run Tests

```bash
mvn test
```

## Usage

1. Open your browser and navigate to `http://localhost:8080`
2. Enter a directory path in the "Current Path" field
3. Click **Navigate** to browse the directory contents
4. Click on folders to navigate into subdirectories
5. Click **Analyze** to get comprehensive statistics about the directory and all its subdirectories

Note: analysis output is saved to ./code_counter_results/<sanitized-root>/<YYYY_MM_DD>_<alpha> by default; you can change the base folder using the Spring property `stinger.results.dir`.

Ignored directories by default
----------------------------
To keep analysis fast and avoid scanning common large or binary directories the analyzer ignores several directory names (case-insensitive) by default. These include:

- code_counter_results (analysis results folder)
- data
- target, .github, .idea, .vscode
- virtualenv and Python cache directories: .venv, .venv2, venv, venv2, env, __pycache__, site-packages
- node_modules

If you have additional folders you want ignored (e.g., custom build caches), we can add configurable ignore patterns or expose a setting for users to extend this list.

How to configure additional ignored directories
---------------------------------------------
Stinger supports a runtime property that lets you add your own directory names or prefix patterns to the ignore list: stinger.ignore.dirs

- Where to set it
  - JVM system property when launching the app (recommended):

    ```bash
    java -Dstinger.ignore.dirs="my_ignore,cache*,temp*" -jar stinger.jar
    # or via mvn
    mvn -Dstinger.ignore.dirs="my_ignore,cache*,temp*" spring-boot:run
    ```

  - In unit tests you can set it programmatically with System.setProperty("stinger.ignore.dirs", "pattern1,pattern2*")

- Format and behavior
  - Comma-separated values (no spaces required, but they are allowed and trimmed).
  - Exact names are matched case-insensitively (example: `my_ignore`).
  - Prefix wildcards are supported using a trailing asterisk (example: `cache*` matches `cache`, `cache1`, `cache-other`).
  - The property is evaluated at runtime and applied in addition to the built-in ignore names (code_counter_results, data, target, .venv, node_modules, etc.).

This makes it easy to skip any local build caches, tool-specific folders, or other large directories you don't want analyzed.

## Analysis Metrics

The analysis provides the following metrics:

- **Folders**: Total number of subdirectories
- **Total Files**: Total number of files found
- **Code Files**: Number of files classified as source code
- **Documents**: Number of documentation files
- **Methods**: Total number of methods/functions detected in code files
- **Lines of Code**: Total lines in all code files

## Technology Stack

- **Backend**: Java 21, Spring Boot 3.2.0
- **Frontend**: HTML5, CSS3, Vanilla JavaScript
- **Template Engine**: Thymeleaf
- **Code Parsing**: JavaParser (for Java method counting)
- **Build Tool**: Maven

## Project Structure

```
stinger/
├── src/
│   ├── main/
│   │   ├── java/com/codecounter/stinger/
│   │   │   ├── controller/          # REST API and web controllers
│   │   │   ├── service/              # Business logic
│   │   │   ├── model/                # Data models
│   │   │   └── StingerApplication.java
│   │   └── resources/
│   │       ├── static/               # CSS and JavaScript
│   │       ├── templates/            # HTML templates
│   │       └── application.properties
│   └── test/                         # Unit tests
└── pom.xml
```

## API Endpoints

### GET `/api/list?path={path}`
List files and directories at the specified path

### POST `/api/analyze`
Analyze a directory recursively
```json
{
  "path": "/path/to/directory"
}
```

## License

This project is open source and available under the MIT License.
