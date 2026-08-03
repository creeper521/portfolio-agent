# Local Profile File Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make IntelliJ, Maven and `start-local.ps1` local-profile launches create the same repository-local logs without Logback/PowerShell double writing.

**Architecture:** An early Spring environment initializer resolves a safe local log directory and exposes it to Logback. Logback exclusively owns backend files; the PowerShell router owns frontend and launcher files plus maintenance. Unknown layouts degrade to console-only with a safe diagnostic instead of blocking startup.

**Tech Stack:** Spring Boot logging, Logback, PowerShell 5.1+, Pester-style repository scripts.

## Global Constraints

- Default directory is `<repository>/logs` and remains Git-ignored.
- Repository root requires `.git`, `backend/pom.xml`, and `frontend/package.json`.
- Explicit safe log directory wins.
- Unknown layout is console-only and non-blocking.
- A file has exactly one active writer.
- Logging failures never alter healthy business responses.
- Do not commit without explicit authorization.

---

### Task 1: Early local log path resolution

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/common/observability/LocalLogDirectoryResolver.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/observability/LocalLogEnvironmentPostProcessor.java`
- Create: `backend/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports`
- Test: `backend/src/test/java/com/portfolio/agent/common/observability/LocalLogDirectoryResolverTest.java`

- [ ] **Step 1: Write failing path tests**

Cover repo-root working directory, `backend/` working directory, explicit directory, filesystem root rejection and unknown layout.

```java
@Test
void resolvesSameRepositoryLogsFromRootAndBackendDirectory() {
    assertThat(resolver.resolve(repositoryRoot))
            .contains(repositoryRoot.resolve("logs"));
    assertThat(resolver.resolve(repositoryRoot.resolve("backend")))
            .contains(repositoryRoot.resolve("logs"));
}
```

- [ ] **Step 2: Run RED**

Run: `C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=LocalLogDirectoryResolverTest test`

- [ ] **Step 3: Implement safe resolver and environment post-processor**

Only activate for the local profile. Set a dedicated property such as `portfolio.local-log.directory`; on unresolved layout leave file property unset and print one safe `LOG_LAYOUT_UNRESOLVED` console diagnostic.

- [ ] **Step 4: Run GREEN**

Run Task 1 tests.

### Task 2: Logback owns backend files

**Files:**
- Create: `backend/src/main/resources/logback-spring.xml`
- Modify: `backend/src/main/resources/application-local.yml`
- Test: `backend/src/test/java/com/portfolio/agent/common/observability/LocalFileLoggingIntegrationTest.java`

- [ ] **Step 1: Write failing integration test**

Start a local-profile application context with a temporary explicit log directory, emit INFO and ERROR markers, close logging, and assert markers appear only in their intended backend files.

- [ ] **Step 2: Run RED**

Expected: no backend files exist.

- [ ] **Step 3: Add Logback appenders**

Create size/date-rolling backend info and error appenders. Never log question/message/answer fields. Console output remains available for IDE and launcher readiness.

- [ ] **Step 4: Run GREEN**

Run the integration test.

### Task 3: Remove PowerShell backend double writing

**Files:**
- Modify: `scripts/logging/LocalLogRouter.psm1`
- Modify: `scripts/start-local.ps1`
- Modify: `scripts/watch-local-logs.ps1`
- Test: `scripts/local-log-router.test.ps1`
- Test: `scripts/start-local.test.ps1`

- [ ] **Step 1: Add failing ownership tests**

Assert the router does not create/write backend files, still creates frontend and launcher files, forwards backend readiness to console/launcher events, and does not archive active Logback files.

- [ ] **Step 2: Run RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/local-log-router.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.test.ps1
```

- [ ] **Step 3: Implement single-writer ownership**

Pass the absolute log directory into the backend environment. Keep reading backend stdout for readiness and safe launcher events, but never route it into backend-info/backend-error. Update watchers to tolerate console-only fallback.

- [ ] **Step 4: Run GREEN**

Run both script tests.

### Task 4: Three-mode acceptance

**Files:**
- Modify: `README.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`

- [ ] **Step 1: Verify Maven local startup**

Launch with an isolated temporary port, wait for health, and assert `<repository>/logs/current/backend-info.log` exists.

- [ ] **Step 2: Verify official launcher**

Run the local launcher fixture/acceptance path and assert frontend, launcher and backend files exist with no duplicate marker.

- [ ] **Step 3: Document IntelliJ setup**

Document that the working directory may be repository root or `backend/`; both resolve the same log directory. Explain explicit override and console-only unresolved-layout behavior.

- [ ] **Step 4: Run complete logging and privacy checks**

Run local log router tests, start-local tests, backend tests and `scripts/privacy-check.ps1`.
