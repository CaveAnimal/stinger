# Goals for Job 3: Fix Plugin & Core Dependency Blocks

## 1. Analyze Build Failures
The build is failing during the `repackage` goal of `spring-boot-maven-plugin`.
The Nexus firewall is blocking:
- `spring-boot-loader-tools:3.2.0`
- `spring-core:6.0.10`
- `jackson-core:2.14.2`

These versions seem to be associated with the `spring-boot-maven-plugin:3.2.0` or its dependencies.

## 2. Upgrade Spring Boot
- [x] **Upgrade Parent Version**: Update `spring-boot-starter-parent` from `3.2.0` to a newer patch version (e.g., `3.2.5`) to pull in non-quarantined versions of the plugin and core libraries.
- [x] **Verify Plugin Version**: Ensure the `spring-boot-maven-plugin` uses the new version.
- [x] **Upgrade to 3.3.2**: Bumped parent to `3.3.2` and configured `spring-boot-maven-plugin` to use `jackson-core:2.17.1` to bypass further quarantine issues.

## 3. Verify Build
- [x] Run `mvn clean package` to confirm the new versions are accepted by the firewall.
