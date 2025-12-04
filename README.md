# Code Repository Analyzer

A powerful Java 17 Spring Boot application with a modern browser-based frontend for analyzing code repositories. Navigate through directories like File Explorer and get comprehensive insights about your codebase.

## Features

- 📁 **File Explorer Interface**: Navigate through directories with an intuitive, File Explorer-style UI
- 📊 **Comprehensive Analysis**: Get detailed statistics about your code repositories
- 🔍 **File Classification**: Automatically classifies files as code, documents, or other
- 📈 **Code Metrics**: 
  - Count total folders and files
  - Identify code files vs documentation
  - Count methods in code files
  - Count lines of code
- 🎨 **Beautiful UI**: Modern, responsive design with gradient backgrounds and smooth interactions
- ⚡ **Fast Analysis**: Recursive directory analysis with detailed breakdowns

## Supported File Types

### Code Files
Java, Python, JavaScript, TypeScript, C/C++, C#, Go, Ruby, PHP, Swift, Kotlin, Rust, Scala, and many more

### Document Files
Markdown, TXT, PDF, DOC/DOCX, HTML, XML, JSON, YAML, CSV, and more

## Prerequisites

- Java 17 or higher
- Maven 3.6+

## Building the Application

```bash
mvn clean package
```

## Running the Application

```bash
java -jar target/code-analyzer-1.0.0.jar
```

The application will start on `http://localhost:8080`

## Usage

1. **Navigate Directories**: Click on folders to browse through your file system
2. **Go to Path**: Enter a custom path in the input field and click "Go"
3. **Home Button**: Click "Home" to return to your home directory
4. **Analyze**: Click "Analyze Current Directory" to get comprehensive statistics about the current directory and all subdirectories

## Analysis Metrics

The analysis provides the following metrics:
- **Folders**: Total number of subdirectories
- **Total Files**: Count of all files (excluding hidden files)
- **Code Files**: Number of files identified as source code
- **Documents**: Number of documentation files
- **Methods**: Total count of methods/functions in code files
- **Lines of Code**: Total lines across all code files

## Technology Stack

- **Backend**: Spring Boot 3.2.0
- **Java Version**: 17
- **Frontend**: HTML5, CSS3, JavaScript (Vanilla)
- **Build Tool**: Maven
- **Code Analysis**: JavaParser for Java files, heuristic analysis for other languages

## Project Structure

```
src/
├── main/
│   ├── java/com/stinger/analyzer/
│   │   ├── CodeAnalyzerApplication.java    # Main application
│   │   ├── controller/
│   │   │   └── FileSystemController.java   # REST API endpoints
│   │   ├── model/
│   │   │   ├── FileNode.java               # File/directory model
│   │   │   ├── AnalysisResult.java         # Analysis result model
│   │   │   └── FileAnalysis.java           # File analysis model
│   │   └── service/
│   │       ├── FileSystemService.java      # File system operations
│   │       ├── FileClassificationService.java  # File type classification
│   │       └── CodeAnalysisService.java    # Code analysis logic
│   └── resources/
│       ├── static/
│       │   ├── index.html                  # Main UI
│       │   ├── styles.css                  # Styles
│       │   └── app.js                      # Frontend logic
│       └── application.properties          # Configuration
```

## API Endpoints

- `GET /api/list?path={path}` - List files in a directory
- `GET /api/tree?path={path}&maxDepth={depth}` - Get file tree
- `GET /api/analyze?path={path}` - Analyze directory recursively
- `GET /api/roots` - Get system root directories

## Screenshots

### Main Interface
![Code Repository Analyzer](https://github.com/user-attachments/assets/543cf101-68c8-498a-9396-832d5e587b69)

### File Navigation
![File Navigation](https://github.com/user-attachments/assets/54dc16a2-9263-40dc-b5ad-e2992ba498dd)

## License

MIT License
