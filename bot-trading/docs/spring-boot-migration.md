# Spring Boot Parent Version Tracking

## Current status
- Temporary downgrade to `spring-boot-starter-parent` **3.3.3** because 3.3.4 is not yet available in the corporate mirrors/local cache.
- Keep `project.parent.version` aligned with the pinned Spring Boot release.

## Follow-up actions
1. Revert the parent version back to **3.3.4** once the artifact is published in the reachable repository or cached locally.
2. After upgrading, run the full Maven test suite to confirm compatibility.
3. Update this note and any release documentation once the upgrade is complete.

## Context
- Attempting to download `org.springframework.boot:spring-boot-starter-parent:3.3.4` from Maven Central returns HTTP 403 (Forbidden).
- No alternative Nexus/Artifactory mirror containing 3.3.4 was found in the current environment.
