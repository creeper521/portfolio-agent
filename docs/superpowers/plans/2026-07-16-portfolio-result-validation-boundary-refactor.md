# Portfolio Result and Validation Boundary Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the Validation-to-file-Repository reverse dependency and rename Portfolio service query models from `service.model` to `service.result`.

**Architecture:** `PortfolioSnapshotValidator` and the JSON Repository will depend on a module-level `portfolio.exception.InvalidPortfolioSnapshotException`. `PortfolioOverview` and `ProjectDetails` remain immutable Service return types, but move to `portfolio.service.result` to make their role explicit. Repository interfaces and implementations remain unchanged.

**Tech Stack:** Java 21, Spring Boot 3.5, Maven, JUnit 5, AssertJ

## Global Constraints

- Do not change public API URLs or JSON response fields.
- Do not change Repository interfaces or the `repository/file` split.
- Production and test Java must not use `var`, records, or Lombok.
- Preserve unrelated frontend and local-tool files.
- Use explicit immutable classes and constructor injection.

---

### Task 1: Move the snapshot exception to the Portfolio exception boundary

**Files:**
- Move: `backend/src/main/java/com/portfolio/agent/portfolio/repository/file/InvalidPortfolioSnapshotException.java`
- To: `backend/src/main/java/com/portfolio/agent/portfolio/exception/InvalidPortfolioSnapshotException.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/repository/file/JsonPublicPortfolioRepository.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidatorTest.java`

**Interfaces:**
- Produces: `com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException`
- Preserves constructors:
  - `InvalidPortfolioSnapshotException(String message)`
  - `InvalidPortfolioSnapshotException(String message, Throwable cause)`

- [ ] **Step 1: Change the validator test to import the module-level exception**

```java
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioSnapshotValidatorTest test
```

Expected: compilation fails because `portfolio.exception.InvalidPortfolioSnapshotException` does not exist.

- [ ] **Step 3: Move the exception and update production imports**

The moved class keeps the same implementation and changes only its package:

```java
package com.portfolio.agent.portfolio.exception;

public class InvalidPortfolioSnapshotException extends RuntimeException {

    public InvalidPortfolioSnapshotException(String message) {
        super(message);
    }

    public InvalidPortfolioSnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Both `PortfolioSnapshotValidator` and `JsonPublicPortfolioRepository` import this package.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioSnapshotValidatorTest,JsonPublicPortfolioRepositoryTest test
```

Expected: both test classes pass.

---

### Task 2: Rename Portfolio Service return models to results

**Files:**
- Move: `backend/src/main/java/com/portfolio/agent/portfolio/service/model/PortfolioOverview.java`
- To: `backend/src/main/java/com/portfolio/agent/portfolio/service/result/PortfolioOverview.java`
- Move: `backend/src/main/java/com/portfolio/agent/portfolio/service/model/ProjectDetails.java`
- To: `backend/src/main/java/com/portfolio/agent/portfolio/service/result/ProjectDetails.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/service/PortfolioService.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapper.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/service/PortfolioServiceTest.java`
- Modify: `docs/07-modular-monolith-backend-review.md`
- Modify: `docs/superpowers/specs/2026-07-16-modular-monolith-package-design.md`

**Interfaces:**
- Produces:
  - `com.portfolio.agent.portfolio.service.result.PortfolioOverview`
  - `com.portfolio.agent.portfolio.service.result.ProjectDetails`
- Preserves all constructors, getters, equality, hash code, and string representations.

- [ ] **Step 1: Change the Service test imports to `service.result`**

```java
import com.portfolio.agent.portfolio.service.result.PortfolioOverview;
import com.portfolio.agent.portfolio.service.result.ProjectDetails;
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioServiceTest test
```

Expected: compilation fails because the `service.result` classes do not exist.

- [ ] **Step 3: Move both immutable result classes and update imports**

Change each package declaration from:

```java
package com.portfolio.agent.portfolio.service.model;
```

to:

```java
package com.portfolio.agent.portfolio.service.result;
```

Update `PortfolioService`, `PortfolioResponseMapper`, and documentation references.

- [ ] **Step 4: Run focused Portfolio tests and verify GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioServiceTest,PortfolioControllerTest test
```

Expected: both test classes pass.

---

### Task 3: Verify the backend refactor

**Files:**
- Verify all files modified by Tasks 1 and 2.

**Interfaces:**
- Confirms no `portfolio.service.model` package remains.
- Confirms Validation no longer imports `portfolio.repository.file`.

- [ ] **Step 1: Run code-quality and architecture checks**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1 -Path backend/src
powershell -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
```

Expected: both commands exit 0.

- [ ] **Step 2: Run all backend tests**

```powershell
mvn.cmd -f backend/pom.xml test
```

Expected: Maven reports `BUILD SUCCESS` with zero test failures.

- [ ] **Step 3: Verify package cleanup and staged diff quality**

```powershell
rg -n "portfolio\.service\.model|portfolio\.repository\.file\.InvalidPortfolioSnapshotException" backend/src docs
git diff --check
git status --short
```

Expected: the obsolete package references produce no output; diff check exits 0; status contains only intentional backend refactor files plus pre-existing local tool files.

- [ ] **Step 4: Commit**

```powershell
git add backend/src docs/07-modular-monolith-backend-review.md docs/superpowers/specs/2026-07-16-modular-monolith-package-design.md docs/superpowers/plans/2026-07-16-portfolio-result-validation-boundary-refactor.md
git commit -m "refactor: clarify portfolio result and validation boundaries"
```
