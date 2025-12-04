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
