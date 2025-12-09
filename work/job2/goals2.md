# Goals for Job 2: Fix Build & Logging

## 1. Resolve Build Failures (Quarantined Artifacts)
The build is failing because the corporate Nexus proxy has quarantined specific versions of libraries.
- **Logback**: `ch.qos.logback:logback-classic:1.4.11` and `ch.qos.logback:logback-core:1.4.11` are blocked.
- **WebDriverManager**: `io.github.bonigarcia:webdrivermanager:5.4.1` is blocked.

## 2. Update Dependencies
- [x] **Upgrade WebDriverManager**: Change version `5.4.1` to a newer version (e.g., `5.9.2` or latest stable) to bypass the quarantine.
- [x] **Manage Logback Version**: Explicitly override the `logback` version managed by Spring Boot to a safe version (e.g., `1.4.14` or `1.5.3`).
- [x] **Add Lombok**: Add `org.projectlombok:lombok` dependency and configure `annotationProcessorPaths` in the build plugins.

## 3. Configure Logging & Exclusions
- [x] **Apply Exclusions**: Exclude `log4j-to-slf4j` from `spring-boot-starter-web` and `spring-boot-starter-validation` as requested.
- [x] **Verify Logging**: Ensure the application starts up correctly with the new logging configuration.

## 4. Verify Build
- [x] Run `mvn clean package` to confirm all dependencies download successfully and the build passes.
