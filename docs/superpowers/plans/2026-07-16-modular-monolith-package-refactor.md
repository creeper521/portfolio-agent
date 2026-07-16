# Modular Monolith Package Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize the backend into a boundary-clean `common / portfolio / answer` modular monolith with familiar Spring Boot package names, while preserving all public API behavior and avoiding Feign or other remote-call infrastructure.

**Architecture:** Portfolio owns public facts and exposes them through a repository and service boundary. Answer owns its answer models and consumes Portfolio only through `PortfolioKnowledgeGateway`, whose first implementation is an in-process `LocalPortfolioKnowledgeAdapter`. Controllers map service results to response DTOs; services and engines never depend on controller or response packages.

**Tech Stack:** Java 21, Spring Boot 3.5.3, Maven, JUnit 5, Spring Boot Test, AssertJ, MockMvc, PowerShell quality gates.

## Global Constraints

- Keep one Maven module, one Spring Boot process, one executable JAR, and one Docker image.
- Top-level business packages remain exactly `common`, `portfolio`, and `answer`.
- Use familiar package names: `controller`, `dto`, `service`, `domain`, `repository`, `mapper`, `validation`, `engine`, `gateway`, and `adapter`.
- Do not add Feign, Spring Cloud, HTTP Service Client, service discovery, circuit breakers, or remote calls.
- Preserve `GET /api/v1/portfolio`, `GET /api/v1/projects/{slug}`, and `POST /api/v1/answers`.
- Preserve current HTTP status codes, JSON field names, deterministic matching behavior, five-section answer order, Evidence order, and public wording.
- Production and test Java must not use `var`, declare `record`, or use Lombok.
- Value objects remain explicit immutable `public final class` types with defensive collection copies and complete value semantics.
- Do not inspect, modify, build, test, or integrate with frontend source because the frontend is undergoing a separate large refactor.
- Do not modify public snapshot content, `D:\code\d11_server`, Claim/RAG functionality, dynamic publishing, or database behavior.
- Do not create empty future packages.
- Preserve user-owned staged and untracked files. The repository currently has an unborn branch and pre-existing staged `.idea` files; do not commit until the user explicitly authorizes how those staged files should be handled.
- Use test-driven development for behavioral changes and obtain fresh verification before completion.

---

## Planned File Structure

```text
backend/src/main/java/com/portfolio/agent/
├─ PortfolioAgentApplication.java
├─ common/
│  ├─ exception/
│  └─ web/
├─ portfolio/
│  ├─ controller/
│  ├─ dto/response/
│  ├─ service/
│  │  └─ model/
│  ├─ domain/
│  ├─ repository/
│  │  └─ file/
│  ├─ mapper/
│  ├─ validation/
│  └─ exception/
└─ answer/
   ├─ controller/
   ├─ dto/request/
   ├─ dto/response/
   ├─ service/
   ├─ domain/
   ├─ engine/
   │  └─ deterministic/
   ├─ gateway/
   ├─ adapter/portfolio/
   ├─ mapper/
   └─ exception/
```

### Responsibility Map

- `portfolio.service.PortfolioService`: portfolio use cases; reads one snapshot per request.
- `portfolio.service.model.PortfolioOverview`: application result for the home query.
- `portfolio.service.model.ProjectDetails`: application result for project detail.
- `portfolio.mapper.PortfolioResponseMapper`: only place mapping Portfolio service/domain results to Portfolio response DTOs.
- `answer.gateway.PortfolioKnowledgeGateway`: Answer-owned interface for obtaining answer-ready public knowledge.
- `answer.adapter.portfolio.LocalPortfolioKnowledgeAdapter`: only package allowed to understand both Portfolio and Answer models.
- `answer.engine.AnswerEngine`: pure answer engine contract; accepts `AnswerKnowledge`, not a slug or repository.
- `answer.engine.deterministic.DeterministicAnswerEngine`: matching and deterministic answer construction only.
- `answer.mapper.AnswerResponseMapper`: maps `AnswerResult` to HTTP response DTOs.

---

### Task 1: Establish a Fresh Baseline and Add Architecture Test Fixtures

**Files:**
- Create: `scripts/architecture-check.ps1`
- Create: `scripts/architecture-check.test.ps1`
- Test: `scripts/architecture-check.test.ps1`

**Interfaces:**
- Consumes: existing Java package tree under `backend/src/main/java` and `backend/src/test/java`.
- Produces: `architecture-check.ps1 -Path <backend/src>` returning exit code 0 for allowed dependencies and 1 with `rule:file:line:import` output for violations.

- [ ] **Step 1: Record the current baseline**

Run:

```powershell
mvn.cmd -f backend/pom.xml test
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1 -Path backend/src
```

Expected:

```text
Maven: BUILD SUCCESS, 39 tests
Code quality checker tests: pass
Java code quality check: Code quality check passed
```

If the test count differs because the workspace has changed, record the new passing count in the task report before continuing.

- [ ] **Step 2: Write failing architecture checker tests**

Create fixtures inside the test script's temporary directory and assert these forbidden dependencies fail:

```powershell
$cases = @(
    @{
        Name = 'portfolio-service-to-controller'
        File = 'com\portfolio\agent\portfolio\service\BadService.java'
        Source = @'
package com.portfolio.agent.portfolio.service;
import com.portfolio.agent.portfolio.controller.PortfolioController;
public final class BadService {}
'@
        Rule = 'portfolio-service-controller'
    },
    @{
        Name = 'answer-domain-to-portfolio'
        File = 'com\portfolio\agent\answer\domain\BadAnswer.java'
        Source = @'
package com.portfolio.agent.answer.domain;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
public final class BadAnswer {}
'@
        Rule = 'answer-core-portfolio'
    },
    @{
        Name = 'answer-engine-to-repository'
        File = 'com\portfolio\agent\answer\engine\BadEngine.java'
        Source = @'
package com.portfolio.agent.answer.engine;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
public final class BadEngine {}
'@
        Rule = 'answer-core-portfolio'
    },
    @{
        Name = 'controller-to-file-repository'
        File = 'com\portfolio\agent\portfolio\controller\BadController.java'
        Source = @'
package com.portfolio.agent.portfolio.controller;
import com.portfolio.agent.portfolio.repository.file.JsonPublicPortfolioRepository;
public final class BadController {}
'@
        Rule = 'controller-infrastructure'
    },
    @{
        Name = 'common-to-business'
        File = 'com\portfolio\agent\common\web\BadCommon.java'
        Source = @'
package com.portfolio.agent.common.web;
import com.portfolio.agent.answer.domain.AnswerResult;
public final class BadCommon {}
'@
        Rule = 'common-business'
    }
)
```

Also create an allowed fixture:

```java
package com.portfolio.agent.answer.adapter.portfolio;

import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;

public final class AllowedAdapter {
    private PortfolioKnowledgeGateway gateway;
    private PublicPortfolioRepository repository;
}
```

- [ ] **Step 3: Run the checker tests and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.test.ps1
```

Expected: FAIL because `scripts/architecture-check.ps1` does not exist.

- [ ] **Step 4: Implement the architecture checker**

Implement rules over Java import statements:

```powershell
param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = 'Stop'
$resolvedPath = (Resolve-Path -LiteralPath $Path).Path
$violations = New-Object System.Collections.Generic.List[string]

function Add-Violation(
    [string]$Rule,
    [System.IO.FileInfo]$File,
    [Microsoft.PowerShell.Commands.MatchInfo]$Match
) {
    $violations.Add(
        "$Rule`:$($File.FullName)`:$($Match.LineNumber)`:$($Match.Line.Trim())"
    )
}

$javaFiles = Get-ChildItem -LiteralPath $resolvedPath -Recurse -File -Filter '*.java'
foreach ($file in $javaFiles) {
    $relative = $file.FullName.Substring($resolvedPath.Length).TrimStart('\')
    $imports = Select-String -LiteralPath $file.FullName `
        -Pattern '^\s*import\s+com\.portfolio\.agent\.[^;]+;'

    foreach ($import in $imports) {
        $line = $import.Line

        if ($relative -match '^com\\portfolio\\agent\\common\\' -and
                $line -match 'com\.portfolio\.agent\.(portfolio|answer)\.') {
            Add-Violation 'common-business' $file $import
        }

        if ($relative -match '^com\\portfolio\\agent\\portfolio\\service\\' -and
                $line -match 'com\.portfolio\.agent\.portfolio\.(controller|dto)\.') {
            Add-Violation 'portfolio-service-controller' $file $import
        }

        if ($relative -match '^com\\portfolio\\agent\\answer\\(service|domain|engine|gateway)\\' -and
                $line -match 'com\.portfolio\.agent\.portfolio\.') {
            Add-Violation 'answer-core-portfolio' $file $import
        }

        if ($relative -match '\\controller\\' -and
                $line -match '\.(repository\.file|adapter|engine\.deterministic)\.') {
            Add-Violation 'controller-infrastructure' $file $import
        }

        if ($relative -match '^com\\portfolio\\agent\\portfolio\\' -and
                $line -match 'com\.portfolio\.agent\.answer\.') {
            Add-Violation 'portfolio-answer' $file $import
        }
    }
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Output $_ }
    exit 1
}

Write-Output "Architecture check passed for $resolvedPath."
```

- [ ] **Step 5: Run checker tests and verify GREEN**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.test.ps1
```

Expected: all unsafe fixtures return exit 1 with the expected rule, and the allowed adapter fixture returns exit 0.

- [ ] **Step 6: Record the integration deferral**

Record in the task report:

```text
The architecture checker is delivered as a standalone backend gate.
Wiring it into scripts/verify-release.ps1 is deferred until the independently
refactored frontend and the full release pipeline are ready for integration.
```

The checker is expected to fail against the current production package tree until Tasks 2–5 finish.

- [ ] **Step 7: Record the suggested checkpoint**

Suggested commit after user resolves the pre-existing staged `.idea` files:

```text
test: add modular monolith architecture gate
```

Do not run `git commit` while unrelated staged files remain.

---

### Task 2: Repackage Portfolio Domain, Repository, Validation, Exceptions, and DTOs

**Files:**
- Move: `backend/src/main/java/com/portfolio/agent/portfolio/domain/model/*.java` → `backend/src/main/java/com/portfolio/agent/portfolio/domain/`
- Move: `backend/src/main/java/com/portfolio/agent/portfolio/domain/repository/PublicPortfolioRepository.java` → `backend/src/main/java/com/portfolio/agent/portfolio/repository/PublicPortfolioRepository.java`
- Move: `backend/src/main/java/com/portfolio/agent/portfolio/infrastructure/json/JsonPublicPortfolioRepository.java` → `backend/src/main/java/com/portfolio/agent/portfolio/repository/file/JsonPublicPortfolioRepository.java`
- Move: `backend/src/main/java/com/portfolio/agent/portfolio/infrastructure/json/InvalidPortfolioSnapshotException.java` → `backend/src/main/java/com/portfolio/agent/portfolio/repository/file/InvalidPortfolioSnapshotException.java`
- Move: `backend/src/main/java/com/portfolio/agent/portfolio/infrastructure/json/PortfolioSnapshotValidator.java` → `backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java`
- Move: `backend/src/main/java/com/portfolio/agent/portfolio/application/exception/*.java` → `backend/src/main/java/com/portfolio/agent/portfolio/exception/`
- Move: `backend/src/main/java/com/portfolio/agent/portfolio/api/dto/*.java` → `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/`
- Move matching tests to mirrored packages.

**Interfaces:**
- Consumes: unchanged JSON resource schema and current public API DTO fields.
- Produces: `portfolio.domain.*`, `portfolio.repository.PublicPortfolioRepository`, `portfolio.repository.file.JsonPublicPortfolioRepository`, `portfolio.validation.PortfolioSnapshotValidator`, `portfolio.exception.*`, and `portfolio.dto.response.*`.

- [ ] **Step 1: Run focused baseline tests**

Run:

```powershell
mvn.cmd -f backend/pom.xml `
  -Dtest=PortfolioModelContractTest,JsonPublicPortfolioRepositoryTest,PortfolioSnapshotValidatorTest `
  test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Mechanically move domain and enum files**

Use `Move-Item` only after verifying source and target paths are under `D:\code\agent`:

```powershell
$root = (Resolve-Path 'D:\code\agent').Path
$source = (Resolve-Path 'backend\src\main\java\com\portfolio\agent\portfolio\domain\model').Path
$target = Join-Path $root 'backend\src\main\java\com\portfolio\agent\portfolio\domain'
if (-not $source.StartsWith($root) -or -not $target.StartsWith($root)) {
    throw 'Portfolio move path escaped repository root.'
}
New-Item -ItemType Directory -Force -Path $target | Out-Null
Get-ChildItem -LiteralPath $source -File -Filter '*.java' |
    ForEach-Object { Move-Item -LiteralPath $_.FullName -Destination $target }
```

Change package declarations from:

```java
package com.portfolio.agent.portfolio.domain.model;
```

to:

```java
package com.portfolio.agent.portfolio.domain;
```

Update imports throughout production and test Java.

- [ ] **Step 3: Move repository and file implementation**

Final declarations:

```java
package com.portfolio.agent.portfolio.repository;

import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;

public interface PublicPortfolioRepository {
    PortfolioSnapshot getSnapshot();
}
```

```java
package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;

@Repository
public class JsonPublicPortfolioRepository implements PublicPortfolioRepository {

    private final PortfolioSnapshot snapshot;

    public JsonPublicPortfolioRepository(
            ObjectMapper objectMapper,
            @Value("classpath:public-data/public-portfolio.v1.json") Resource resource,
            PortfolioSnapshotValidator validator
    ) {
        try (InputStream inputStream = resource.getInputStream()) {
            PortfolioSnapshot loaded = objectMapper.readValue(inputStream, PortfolioSnapshot.class);
            validator.validate(loaded);
            this.snapshot = loaded;
        } catch (IOException | IllegalArgumentException exception) {
            throw new InvalidPortfolioSnapshotException(
                    "unable to load public portfolio snapshot",
                    exception
            );
        }
    }

    @Override
    public PortfolioSnapshot getSnapshot() {
        return snapshot;
    }
}
```

- [ ] **Step 4: Move validator and exception packages without changing behavior**

Final packages:

```java
package com.portfolio.agent.portfolio.validation;
```

```java
package com.portfolio.agent.portfolio.repository.file;
```

Update Validator imports to `com.portfolio.agent.portfolio.domain.*`. Do not alter validation messages or rules in this task.

- [ ] **Step 5: Move Portfolio exceptions**

Final declarations:

```java
package com.portfolio.agent.portfolio.exception;
```

Keep `PROJECT_NOT_FOUND`, public message, HTTP 404, and exception message unchanged.

- [ ] **Step 6: Move response DTOs**

Final package:

```java
package com.portfolio.agent.portfolio.dto.response;
```

Update their Domain imports to `com.portfolio.agent.portfolio.domain.*`. Preserve constructors, getters, field order, value semantics, and JSON names.

- [ ] **Step 7: Move mirrored tests and verify**

Target tests:

```text
backend/src/test/java/com/portfolio/agent/portfolio/domain/PortfolioModelContractTest.java
backend/src/test/java/com/portfolio/agent/portfolio/repository/file/JsonPublicPortfolioRepositoryTest.java
backend/src/test/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidatorTest.java
```

Run:

```powershell
mvn.cmd -f backend/pom.xml `
  -Dtest=PortfolioModelContractTest,JsonPublicPortfolioRepositoryTest,PortfolioSnapshotValidatorTest `
  test
```

Expected: BUILD SUCCESS with the same test count and assertions as baseline.

- [ ] **Step 8: Record the suggested checkpoint**

Suggested commit:

```text
refactor: rename portfolio domain and repository packages
```

Do not commit while unrelated staged files remain.

---

### Task 3: Close the Portfolio Service Boundary

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/service/model/PortfolioOverview.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/service/model/ProjectDetails.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapper.java`
- Move/Modify: `portfolio/application/PortfolioQueryService.java` → `portfolio/service/PortfolioService.java`
- Move/Modify: `portfolio/api/PortfolioController.java` → `portfolio/controller/PortfolioController.java`
- Create: `backend/src/test/java/com/portfolio/agent/portfolio/service/PortfolioServiceTest.java`
- Move/Modify: `portfolio/api/PortfolioControllerTest.java` → `portfolio/controller/PortfolioControllerTest.java`

**Interfaces:**
- Consumes: `PublicPortfolioRepository#getSnapshot()`.
- Produces:
  - `PortfolioOverview PortfolioService#getPortfolio()`
  - `ProjectDetails PortfolioService#getProject(String slug)`
  - `PortfolioHomeResponse PortfolioResponseMapper#toPortfolioResponse(PortfolioOverview overview)`
  - `ProjectDetailResponse PortfolioResponseMapper#toProjectResponse(ProjectDetails details)`

- [ ] **Step 1: Write failing PortfolioService tests**

Use a counting repository:

```java
private static final class CountingRepository implements PublicPortfolioRepository {
    private final PortfolioSnapshot snapshot;
    private int reads;

    private CountingRepository(PortfolioSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public PortfolioSnapshot getSnapshot() {
        reads++;
        return snapshot;
    }
}
```

Add tests proving:

```java
@Test
void getProjectReadsExactlyOneSnapshot() {
    CountingRepository repository = new CountingRepository(snapshot());
    PortfolioService service = new PortfolioService(repository);

    ProjectDetails details = service.getProject("sql-audit");

    assertThat(details.getProject().getSlug()).isEqualTo("sql-audit");
    assertThat(repository.reads).isEqualTo(1);
}

@Test
void getPortfolioReturnsApplicationModelInsteadOfResponseDto() {
    PortfolioService service = new PortfolioService(new CountingRepository(snapshot()));

    PortfolioOverview overview = service.getPortfolio();

    assertThat(overview.getContentVersion()).isEqualTo("2026-07-14.1");
    assertThat(overview.getProjects()).hasSize(1);
}
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioServiceTest test
```

Expected: test compilation fails because `PortfolioService`, `PortfolioOverview`, and `ProjectDetails` do not exist.

- [ ] **Step 3: Implement application result models**

`PortfolioOverview` fields:

```java
private final String contentVersion;
private final OffsetDateTime publishedAt;
private final OwnerProfile owner;
private final List<ProjectProfile> projects;
```

`ProjectDetails` fields:

```java
private final ProjectProfile project;
private final List<EvidenceRecord> evidence;
private final List<String> suggestedQuestions;
```

Both classes must:

```java
public final class ...
```

and use:

```java
this.projects = List.copyOf(projects);
this.evidence = List.copyOf(evidence);
this.suggestedQuestions = List.copyOf(suggestedQuestions);
```

Implement explicit getters plus complete `equals`, `hashCode`, and `toString`.

- [ ] **Step 4: Implement PortfolioService**

```java
package com.portfolio.agent.portfolio.service;

import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.exception.ProjectNotFoundException;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.portfolio.service.model.PortfolioOverview;
import com.portfolio.agent.portfolio.service.model.ProjectDetails;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class PortfolioService {

    private final PublicPortfolioRepository repository;

    public PortfolioService(PublicPortfolioRepository repository) {
        this.repository = repository;
    }

    public PortfolioOverview getPortfolio() {
        PortfolioSnapshot snapshot = repository.getSnapshot();
        return new PortfolioOverview(
                snapshot.getContentVersion(),
                snapshot.getPublishedAt(),
                snapshot.getOwner(),
                snapshot.getProjects()
        );
    }

    public ProjectDetails getProject(String slug) {
        PortfolioSnapshot snapshot = repository.getSnapshot();
        ProjectProfile project = findProject(snapshot, slug);
        Set<String> evidenceIds = Set.copyOf(project.getEvidenceIds());

        List<EvidenceRecord> evidence = snapshot.getEvidence().stream()
                .filter(item -> evidenceIds.contains(item.getId()))
                .filter(item -> item.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(item -> Boolean.FALSE.equals(item.getRawContentPublic()))
                .toList();

        List<String> suggestedQuestions = snapshot.getQuestions().stream()
                .filter(question -> project.getId().equals(question.getProjectId()))
                .filter(question -> project.getQuestionIds().contains(question.getId()))
                .map(QuestionDefinition::getSuggestion)
                .toList();

        return new ProjectDetails(project, evidence, suggestedQuestions);
    }

    private ProjectProfile findProject(PortfolioSnapshot snapshot, String slug) {
        return snapshot.getProjects().stream()
                .filter(project -> project.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new ProjectNotFoundException(slug));
    }
}
```

- [ ] **Step 5: Run PortfolioService tests and verify GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioServiceTest test
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Implement PortfolioResponseMapper**

```java
package com.portfolio.agent.portfolio.mapper;

import com.portfolio.agent.portfolio.dto.response.EvidenceResponse;
import com.portfolio.agent.portfolio.dto.response.OwnerResponse;
import com.portfolio.agent.portfolio.dto.response.PortfolioHomeResponse;
import com.portfolio.agent.portfolio.dto.response.ProjectDetailResponse;
import com.portfolio.agent.portfolio.dto.response.ProjectSummaryResponse;
import com.portfolio.agent.portfolio.service.model.PortfolioOverview;
import com.portfolio.agent.portfolio.service.model.ProjectDetails;
import org.springframework.stereotype.Component;

@Component
public class PortfolioResponseMapper {

    public PortfolioHomeResponse toPortfolioResponse(PortfolioOverview overview) {
        return new PortfolioHomeResponse(
                overview.getContentVersion(),
                overview.getPublishedAt(),
                OwnerResponse.from(overview.getOwner()),
                overview.getProjects().stream()
                        .map(ProjectSummaryResponse::from)
                        .toList()
        );
    }

    public ProjectDetailResponse toProjectResponse(ProjectDetails details) {
        return ProjectDetailResponse.from(
                details.getProject(),
                details.getEvidence().stream().map(EvidenceResponse::from).toList(),
                details.getSuggestedQuestions()
        );
    }
}
```

Add this exact factory to `ProjectDetailResponse`:

```java
public static ProjectDetailResponse from(
        ProjectProfile project,
        List<EvidenceResponse> evidence,
        List<String> suggestedQuestions
) {
    return new ProjectDetailResponse(
            project.getSlug(),
            project.getTitle(),
            project.getSummary(),
            project.getBackground(),
            project.getResponsibilities(),
            project.getSolution(),
            project.getKeyDecisions(),
            project.getTechnologies(),
            project.getVerification(),
            project.getOutcome(),
            project.getHandoff(),
            project.getStatus(),
            project.getContributionType(),
            evidence,
            suggestedQuestions
    );
}
```

- [ ] **Step 7: Move and update PortfolioController**

```java
package com.portfolio.agent.portfolio.controller;

import com.portfolio.agent.portfolio.dto.response.PortfolioHomeResponse;
import com.portfolio.agent.portfolio.dto.response.ProjectDetailResponse;
import com.portfolio.agent.portfolio.mapper.PortfolioResponseMapper;
import com.portfolio.agent.portfolio.service.PortfolioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioResponseMapper responseMapper;

    public PortfolioController(
            PortfolioService portfolioService,
            PortfolioResponseMapper responseMapper
    ) {
        this.portfolioService = portfolioService;
        this.responseMapper = responseMapper;
    }

    @GetMapping("/portfolio")
    public PortfolioHomeResponse getPortfolio() {
        return responseMapper.toPortfolioResponse(portfolioService.getPortfolio());
    }

    @GetMapping("/projects/{slug}")
    public ProjectDetailResponse getProject(@PathVariable String slug) {
        return responseMapper.toProjectResponse(portfolioService.getProject(slug));
    }
}
```

- [ ] **Step 8: Move Controller test and verify API compatibility**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioControllerTest,PortfolioServiceTest test
```

Expected:

```text
GET /api/v1/portfolio remains 200 with the same fields.
GET /api/v1/projects/sql-audit remains 200 with the same fields and Evidence.
Unknown project remains 404 PROJECT_NOT_FOUND.
```

- [ ] **Step 9: Run architecture checker for Portfolio**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/architecture-check.ps1 -Path backend/src
```

Expected: it may still fail on Answer legacy packages, but there must be no `portfolio-service-controller` violation.

- [ ] **Step 10: Record the suggested checkpoint**

Suggested commit:

```text
refactor: close portfolio service boundary
```

---

### Task 4: Introduce Answer-Owned Knowledge Models and Portfolio Gateway

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerKnowledge.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerQuestion.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerEvidence.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/gateway/PortfolioKnowledgeGateway.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapter.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapterTest.java`

**Interfaces:**
- Consumes: `PublicPortfolioRepository#getSnapshot()`.
- Produces:

```java
Optional<AnswerKnowledge> PortfolioKnowledgeGateway#findBySlug(String projectSlug);
```

- [ ] **Step 1: Write failing adapter tests**

Test these behaviors:

```java
@Test
void mapsOnlyKnowledgeOwnedByRequestedProject() { ... }

@Test
void preservesQuestionAndEvidenceOrder() { ... }

@Test
void filtersEvidenceThatIsNotApprovedOrExposesRawContent() { ... }

@Test
void returnsEmptyForUnknownProject() { ... }

@Test
void answerModelsDefensivelyCopyCollections() { ... }
```

The key assertion:

```java
Optional<AnswerKnowledge> result = adapter.findBySlug("sql-audit");

assertThat(result).isPresent();
assertThat(result.orElseThrow().getQuestions())
        .extracting(AnswerQuestion::getSuggestion)
        .containsExactly(
                "请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？"
        );
assertThat(result.orElseThrow().getEvidence())
        .extracting(AnswerEvidence::getId)
        .containsExactly("sql-audit-delivery-set");
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=LocalPortfolioKnowledgeAdapterTest test
```

Expected: test compilation fails because Gateway, Adapter, and Answer knowledge types do not exist.

- [ ] **Step 3: Implement AnswerQuestion**

Fields:

```java
private final String canonicalQuestion;
private final List<String> aliases;
private final String suggestion;
```

Use `List.copyOf(aliases)` and implement explicit getters plus complete value semantics.

- [ ] **Step 4: Implement AnswerEvidence**

Fields:

```java
private final String id;
private final String title;
private final String type;
private final LocalDate periodStart;
private final LocalDate periodEnd;
private final int sourceCount;
private final String summary;
private final List<String> supportedClaims;
private final String publicStatus;
private final boolean rawContentPublic;
```

Use strings for `type` and `publicStatus` so Answer Domain does not import Portfolio enums.

- [ ] **Step 5: Implement AnswerKnowledge**

Fields:

```java
private final String slug;
private final String title;
private final String background;
private final List<String> responsibilities;
private final String solution;
private final List<String> keyDecisions;
private final List<String> verification;
private final String outcome;
private final String handoff;
private final String status;
private final List<AnswerQuestion> questions;
private final List<AnswerEvidence> evidence;
```

Use defensive copies and complete value semantics.

- [ ] **Step 6: Implement Gateway**

```java
package com.portfolio.agent.answer.gateway;

import com.portfolio.agent.answer.domain.AnswerKnowledge;

import java.util.Optional;

public interface PortfolioKnowledgeGateway {
    Optional<AnswerKnowledge> findBySlug(String projectSlug);
}
```

- [ ] **Step 7: Implement LocalPortfolioKnowledgeAdapter**

```java
package com.portfolio.agent.answer.adapter.portfolio;

import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class LocalPortfolioKnowledgeAdapter implements PortfolioKnowledgeGateway {

    private final PublicPortfolioRepository repository;

    public LocalPortfolioKnowledgeAdapter(PublicPortfolioRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AnswerKnowledge> findBySlug(String projectSlug) {
        PortfolioSnapshot snapshot = repository.getSnapshot();
        Optional<ProjectProfile> project = snapshot.getProjects().stream()
                .filter(candidate -> candidate.getSlug().equals(projectSlug))
                .findFirst();

        if (project.isEmpty()) {
            return Optional.empty();
        }

        ProjectProfile value = project.orElseThrow();
        Set<String> questionIds = Set.copyOf(value.getQuestionIds());
        Set<String> evidenceIds = Set.copyOf(value.getEvidenceIds());

        List<AnswerQuestion> questions = snapshot.getQuestions().stream()
                .filter(candidate -> value.getId().equals(candidate.getProjectId()))
                .filter(candidate -> questionIds.contains(candidate.getId()))
                .map(this::toQuestion)
                .toList();

        List<AnswerEvidence> evidence = snapshot.getEvidence().stream()
                .filter(candidate -> evidenceIds.contains(candidate.getId()))
                .filter(candidate -> candidate.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(candidate -> Boolean.FALSE.equals(candidate.getRawContentPublic()))
                .map(this::toEvidence)
                .toList();

        return Optional.of(new AnswerKnowledge(
                value.getSlug(),
                value.getTitle(),
                value.getBackground(),
                value.getResponsibilities(),
                value.getSolution(),
                value.getKeyDecisions(),
                value.getVerification(),
                value.getOutcome(),
                value.getHandoff(),
                value.getStatus().name(),
                questions,
                evidence
        ));
    }

    private AnswerQuestion toQuestion(QuestionDefinition question) {
        return new AnswerQuestion(
                question.getCanonicalQuestion(),
                question.getAliases(),
                question.getSuggestion()
        );
    }

    private AnswerEvidence toEvidence(EvidenceRecord evidence) {
        return new AnswerEvidence(
                evidence.getId(),
                evidence.getTitle(),
                evidence.getType().name(),
                evidence.getPeriodStart(),
                evidence.getPeriodEnd(),
                evidence.getSourceCount(),
                evidence.getSummary(),
                evidence.getSupportedClaims(),
                evidence.getPublicStatus().name(),
                false
        );
    }
}
```

- [ ] **Step 8: Run adapter tests and verify GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=LocalPortfolioKnowledgeAdapterTest test
```

Expected: BUILD SUCCESS.

- [ ] **Step 9: Run import scan**

Run:

```powershell
Get-ChildItem -Recurse -File `
  'backend\src\main\java\com\portfolio\agent\answer\domain',`
  'backend\src\main\java\com\portfolio\agent\answer\gateway' |
  Select-String -Pattern 'com\.portfolio\.agent\.portfolio\.'
```

Expected: no output.

- [ ] **Step 10: Record the suggested checkpoint**

Suggested commit:

```text
refactor: add answer portfolio gateway
```

---

### Task 5: Repackage Answer and Make the Engine Pure

**Files:**
- Move: `answer/domain/model/*.java` → `answer/domain/`
- Move/Modify: `answer/application/AnswerEngine.java` → `answer/engine/AnswerEngine.java`
- Move: `answer/application/QuestionNormalizer.java` → `answer/service/QuestionNormalizer.java`
- Move/Modify: `answer/infrastructure/deterministic/DeterministicAnswerEngine.java` → `answer/engine/deterministic/DeterministicAnswerEngine.java`
- Move/Modify: `answer/application/AnswerService.java` → `answer/service/AnswerService.java`
- Create: `answer/exception/AnswerErrorCode.java`
- Create: `answer/exception/AnswerProjectNotFoundException.java`
- Modify: `answer/domain/AnswerResult.java`
- Move and update mirrored tests.

**Interfaces:**
- Consumes:

```java
Optional<AnswerKnowledge> PortfolioKnowledgeGateway#findBySlug(String projectSlug)
```

- Produces:

```java
AnswerResult AnswerEngine#answer(AnswerKnowledge knowledge, String question)
AnswerResult AnswerService#answer(String projectSlug, String question)
```

- [ ] **Step 1: Rewrite engine tests against AnswerKnowledge and verify RED**

Construct `AnswerKnowledge` directly in `DeterministicAnswerEngineTest` and call:

```java
AnswerResult result = engine.answer(knowledge(), canonicalQuestion);
```

Remove Repository test doubles from this test. Assert the same:

- canonical question matches;
- aliases match;
- unsupported question returns BOUNDARY;
- five section order is unchanged;
- Evidence and suggestions preserve order.

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=DeterministicAnswerEngineTest test
```

Expected: test compilation fails because `AnswerEngine` still accepts slug and the engine still requires Repository.

- [ ] **Step 2: Move Answer Domain package**

Change:

```java
package com.portfolio.agent.answer.domain.model;
```

to:

```java
package com.portfolio.agent.answer.domain;
```

Move tests to `backend/src/test/java/com/portfolio/agent/answer/domain/`.

- [ ] **Step 3: Change AnswerResult Evidence ownership**

Replace:

```java
private final List<EvidenceRecord> evidence;
```

with:

```java
private final List<AnswerEvidence> evidence;
```

Update constructor and getter accordingly. Preserve field order, defensive copies, equals/hashCode/toString, and JSON output values through the response mapper in Task 6.

- [ ] **Step 4: Move and change AnswerEngine**

```java
package com.portfolio.agent.answer.engine;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerResult;

public interface AnswerEngine {
    AnswerResult answer(AnswerKnowledge knowledge, String question);
}
```

- [ ] **Step 5: Move QuestionNormalizer**

Final package:

```java
package com.portfolio.agent.answer.service;
```

Do not change normalization behavior.

- [ ] **Step 6: Implement pure DeterministicAnswerEngine**

The constructor accepts only:

```java
public DeterministicAnswerEngine(QuestionNormalizer normalizer)
```

The answer method begins:

```java
@Override
public AnswerResult answer(AnswerKnowledge knowledge, String question) {
    String normalizedQuestion = normalizer.normalize(question);
    boolean matched = knowledge.getQuestions().stream()
            .anyMatch(candidate -> matches(candidate, normalizedQuestion));

    List<String> suggestions = knowledge.getQuestions().stream()
            .map(AnswerQuestion::getSuggestion)
            .toList();

    if (!matched) {
        return boundaryResult(knowledge.getTitle(), suggestions);
    }

    return new AnswerResult(
            AnswerMode.DETERMINISTIC,
            true,
            false,
            knowledge.getTitle(),
            buildSections(knowledge),
            knowledge.getEvidence(),
            suggestions
    );
}
```

`buildSections` must preserve:

```java
return List.of(
        new AnswerSection(AnswerSectionType.BACKGROUND, knowledge.getBackground()),
        new AnswerSection(
                AnswerSectionType.RESPONSIBILITY,
                joinSentences(knowledge.getResponsibilities())
        ),
        new AnswerSection(
                AnswerSectionType.SOLUTION,
                knowledge.getSolution()
                        + " 关键决策包括："
                        + joinSentences(knowledge.getKeyDecisions())
        ),
        new AnswerSection(
                AnswerSectionType.VERIFICATION,
                joinSentences(knowledge.getVerification())
        ),
        new AnswerSection(
                AnswerSectionType.STATUS,
                knowledge.getOutcome() + " " + knowledge.getHandoff()
        )
);
```

Boundary wording remains unchanged.

- [ ] **Step 7: Run engine and domain tests**

Run:

```powershell
mvn.cmd -f backend/pom.xml `
  -Dtest=DeterministicAnswerEngineTest,AnswerModelContractTest,QuestionNormalizerTest `
  test
```

Expected: BUILD SUCCESS.

- [ ] **Step 8: Add Answer-owned not-found error**

```java
package com.portfolio.agent.answer.exception;

import com.portfolio.agent.common.exception.ErrorCode;

public enum AnswerErrorCode implements ErrorCode {

    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND", "公开项目不存在", 404);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    AnswerErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
```

```java
package com.portfolio.agent.answer.exception;

import com.portfolio.agent.common.exception.ApplicationException;

public final class AnswerProjectNotFoundException extends ApplicationException {

    public AnswerProjectNotFoundException(String slug) {
        super(AnswerErrorCode.PROJECT_NOT_FOUND, "公开项目不存在: " + slug);
    }
}
```

- [ ] **Step 9: Rewrite AnswerService**

```java
package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.engine.AnswerEngine;
import com.portfolio.agent.answer.exception.AnswerProjectNotFoundException;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import org.springframework.stereotype.Service;

@Service
public class AnswerService {

    private final PortfolioKnowledgeGateway knowledgeGateway;
    private final AnswerEngine answerEngine;

    public AnswerService(
            PortfolioKnowledgeGateway knowledgeGateway,
            AnswerEngine answerEngine
    ) {
        this.knowledgeGateway = knowledgeGateway;
        this.answerEngine = answerEngine;
    }

    public AnswerResult answer(String projectSlug, String question) {
        AnswerKnowledge knowledge = knowledgeGateway.findBySlug(projectSlug)
                .orElseThrow(() -> new AnswerProjectNotFoundException(projectSlug));
        return answerEngine.answer(knowledge, question);
    }
}
```

- [ ] **Step 10: Add AnswerService tests**

Verify:

```java
@Test
void delegatesResolvedKnowledgeToEngine() { ... }

@Test
void throwsAnswerOwnedNotFoundExceptionWhenGatewayReturnsEmpty() { ... }
```

Use handwritten fakes, not Mockito, to keep the contract explicit.

- [ ] **Step 11: Run Answer core tests**

Run:

```powershell
mvn.cmd -f backend/pom.xml `
  -Dtest=AnswerServiceTest,DeterministicAnswerEngineTest,AnswerModelContractTest,QuestionNormalizerTest `
  test
```

Expected: BUILD SUCCESS.

- [ ] **Step 12: Verify forbidden imports are gone**

Run:

```powershell
Get-ChildItem -Recurse -File `
  'backend\src\main\java\com\portfolio\agent\answer\service',`
  'backend\src\main\java\com\portfolio\agent\answer\domain',`
  'backend\src\main\java\com\portfolio\agent\answer\engine',`
  'backend\src\main\java\com\portfolio\agent\answer\gateway' |
  Select-String -Pattern 'com\.portfolio\.agent\.portfolio\.'
```

Expected: no output.

- [ ] **Step 13: Record the suggested checkpoint**

Suggested commit:

```text
refactor: isolate answer engine from portfolio
```

---

### Task 6: Repackage Answer HTTP Boundary and Add Response Mapper

**Files:**
- Move: `answer/api/AnswerController.java` → `answer/controller/AnswerController.java`
- Move: `answer/api/dto/AnswerRequest.java` → `answer/dto/request/AnswerRequest.java`
- Move: remaining Answer DTOs → `answer/dto/response/`
- Create: `answer/mapper/AnswerResponseMapper.java`
- Modify: `portfolio/dto/response/EvidenceResponse.java`
- Move/Modify: `answer/api/AnswerControllerTest.java` → `answer/controller/AnswerControllerTest.java`

**Interfaces:**
- Consumes: `AnswerResult`.
- Produces: unchanged Answer HTTP JSON.

- [ ] **Step 1: Add mapper test and verify RED**

Create `AnswerResponseMapperTest` asserting an `AnswerEvidence` maps to the same JSON-ready fields:

```java
AnswerResponse response = mapper.toResponse("request-1", result);

assertThat(response.getRequestId()).isEqualTo("request-1");
assertThat(response.getEvidence().getFirst().getType()).isEqualTo("COLLECTION");
assertThat(response.getEvidence().getFirst().getPublicStatus()).isEqualTo("APPROVED");
assertThat(response.getEvidence().getFirst().isRawContentPublic()).isFalse();
```

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=AnswerResponseMapperTest test
```

Expected: test compilation fails because Mapper does not exist and EvidenceResponse still uses Portfolio enums.

- [ ] **Step 2: Make EvidenceResponse API-native**

Change fields:

```java
private final String type;
private final String publicStatus;
```

Keep dates and `rawContentPublic` unchanged.

`from(EvidenceRecord)` maps:

```java
evidence.getType().name()
evidence.getPublicStatus().name()
```

This preserves JSON values while removing Portfolio enum types from the public constructor.

- [ ] **Step 3: Move Answer DTOs**

Final packages:

```java
package com.portfolio.agent.answer.dto.request;
package com.portfolio.agent.answer.dto.response;
```

Do not change request validation annotations, messages, field names, or response field order.

- [ ] **Step 4: Implement AnswerResponseMapper**

```java
package com.portfolio.agent.answer.mapper;

import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.dto.response.AnswerPayload;
import com.portfolio.agent.answer.dto.response.AnswerResponse;
import com.portfolio.agent.answer.dto.response.AnswerSectionResponse;
import com.portfolio.agent.portfolio.dto.response.EvidenceResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AnswerResponseMapper {

    public AnswerResponse toResponse(String requestId, AnswerResult result) {
        List<AnswerSectionResponse> sections = result.getSections().stream()
                .map(section -> new AnswerSectionResponse(
                        section.getType(),
                        section.getContent()
                ))
                .toList();

        List<EvidenceResponse> evidence = result.getEvidence().stream()
                .map(this::toEvidenceResponse)
                .toList();

        return new AnswerResponse(
                requestId,
                result.getAnswerMode(),
                result.isMatched(),
                result.isFallback(),
                new AnswerPayload(result.getTitle(), sections),
                evidence,
                result.getSuggestedQuestions()
        );
    }

    private EvidenceResponse toEvidenceResponse(AnswerEvidence evidence) {
        return new EvidenceResponse(
                evidence.getId(),
                evidence.getTitle(),
                evidence.getType(),
                evidence.getPeriodStart(),
                evidence.getPeriodEnd(),
                evidence.getSourceCount(),
                evidence.getSummary(),
                evidence.getSupportedClaims(),
                evidence.getPublicStatus(),
                evidence.isRawContentPublic()
        );
    }
}
```

Remove the static `AnswerResponse.from` method so mapping ownership is unambiguous.

- [ ] **Step 5: Move and update AnswerController**

```java
package com.portfolio.agent.answer.controller;

import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.dto.request.AnswerRequest;
import com.portfolio.agent.answer.dto.response.AnswerResponse;
import com.portfolio.agent.answer.mapper.AnswerResponseMapper;
import com.portfolio.agent.answer.service.AnswerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/answers")
public class AnswerController {

    private final AnswerService answerService;
    private final AnswerResponseMapper responseMapper;

    public AnswerController(
            AnswerService answerService,
            AnswerResponseMapper responseMapper
    ) {
        this.answerService = answerService;
        this.responseMapper = responseMapper;
    }

    @PostMapping
    public AnswerResponse answer(@Valid @RequestBody AnswerRequest request) {
        String requestId = UUID.randomUUID().toString();
        AnswerResult result = answerService.answer(
                request.getProjectSlug(),
                request.getQuestion()
        );
        return responseMapper.toResponse(requestId, result);
    }
}
```

- [ ] **Step 6: Run mapper and Controller tests**

Run:

```powershell
mvn.cmd -f backend/pom.xml `
  -Dtest=AnswerResponseMapperTest,AnswerControllerTest,GlobalExceptionHandlerTest `
  test
```

Expected:

- canonical answer remains 200;
- five sections remain in the same order;
- Evidence JSON values remain unchanged;
- unsupported question remains a BOUNDARY response;
- missing project remains 404 `PROJECT_NOT_FOUND`;
- validation, 405, and 415 contracts remain unchanged.

- [ ] **Step 7: Record the suggested checkpoint**

Suggested commit:

```text
refactor: rename answer web boundary
```

---

### Task 7: Remove Legacy Packages and Enforce Final Dependency Rules

**Files:**
- Delete empty/obsolete package trees:
  - `portfolio/api`
  - `portfolio/application`
  - `portfolio/domain/model`
  - `portfolio/domain/repository`
  - `portfolio/infrastructure`
  - `answer/api`
  - `answer/application`
  - `answer/domain/model`
  - `answer/infrastructure`
- Modify: `scripts/architecture-check.ps1`
- Modify: `scripts/architecture-check.test.ps1`
- Test: all Java tests plus architecture checker.

**Interfaces:**
- Consumes: final package tree from Tasks 2–6.
- Produces: no legacy imports or duplicate Spring beans/routes.

- [ ] **Step 1: Search for old packages**

Run:

```powershell
$patterns = @(
  'portfolio\.api',
  'portfolio\.application',
  'portfolio\.domain\.model',
  'portfolio\.domain\.repository',
  'portfolio\.infrastructure',
  'answer\.api',
  'answer\.application',
  'answer\.domain\.model',
  'answer\.infrastructure'
)
Get-ChildItem -Recurse -File backend -Filter '*.java' |
  Select-String -Pattern $patterns
```

Expected: no output before deletion. If output exists, update the import or package declaration first.

- [ ] **Step 2: Verify and remove obsolete directories**

Resolve every target and verify it remains under:

```text
D:\code\agent\backend\src
```

Then remove only empty or confirmed-obsolete trees with native PowerShell `Remove-Item -LiteralPath ... -Recurse`.

- [ ] **Step 3: Strengthen architecture checker final rules**

Add exact bans for legacy package declarations/imports:

```powershell
if ($line -match 'com\.portfolio\.agent\.(portfolio|answer)\.(api|application|infrastructure|domain\.model|domain\.repository)\.') {
    Add-Violation 'legacy-package' $file $import
}
```

Also scan package declarations:

```powershell
$packageMatches = Select-String -LiteralPath $file.FullName `
    -Pattern '^\s*package\s+com\.portfolio\.agent\.[^;]+;'
```

Reject any legacy package segment in the declaration.

- [ ] **Step 4: Run architecture checker**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
```

Expected:

```text
Architecture checker tests passed.
Architecture check passed for ...\backend\src.
```

- [ ] **Step 5: Run full backend tests**

Run:

```powershell
mvn.cmd -f backend/pom.xml test
```

Expected: BUILD SUCCESS; all original tests plus new service, adapter, mapper, and architecture tests pass.

- [ ] **Step 6: Inspect Spring route uniqueness**

Run:

```powershell
Get-ChildItem -Recurse -File backend\src\main\java -Filter '*.java' |
  Select-String -Pattern '@(GetMapping|PostMapping|RequestMapping)'
```

Expected:

- one Portfolio controller with two GET routes;
- one Answer controller with one POST route;
- no duplicate old controllers.

- [ ] **Step 7: Record the suggested checkpoint**

Suggested commit:

```text
refactor: remove legacy backend packages
```

---

### Task 8: Backend-Only Verification and Documentation Alignment

**Files:**
- Modify: `README.md`
- Modify: `docs/04-项目代码约束.md`
- Modify: `docs/superpowers/specs/2026-07-15-dynamic-public-content-agent-design.md`
- Verify: backend compilation, JUnit, architecture checks, public-data privacy checks, and MockMvc API contracts only.

**Interfaces:**
- Consumes: completed modular monolith refactor.
- Produces: documentation matching the implemented package names and fresh backend-only verification evidence.

- [ ] **Step 1: Update README directory description**

Document:

```text
common       shared mechanisms only
portfolio    public facts and portfolio queries
answer       retrieval/answer orchestration and deterministic engine
```

State explicitly:

```text
Current module communication is in-process through Java Gateway interfaces.
The project does not use Feign or remote calls.
```

- [ ] **Step 2: Update code constraints**

Replace old package examples with:

```text
common
portfolio/controller|service|domain|repository|mapper|validation
answer/controller|service|domain|engine|gateway|adapter|mapper
```

Add rules:

```text
Portfolio service must not depend on controller or response DTO.
Answer service/domain/engine/gateway must not import Portfolio packages.
Only answer.adapter.portfolio may translate Portfolio types into Answer types.
Feign and HTTP clients are not allowed in the modular monolith.
```

- [ ] **Step 3: Align dynamic-content design terminology**

Update only package references in the dynamic-content design:

```text
api        → controller
application → service
infrastructure adapter → adapter/repository implementation
```

Do not change the approved dynamic publishing, Claim, RAG, model, or database decisions.

- [ ] **Step 4: Run code and architecture gates**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1 -Path backend/src
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
```

Expected: all pass.

- [ ] **Step 5: Run focused backend contract tests**

Run:

```powershell
mvn.cmd -f backend/pom.xml `
  -Dtest=PortfolioControllerTest,AnswerControllerTest,GlobalExceptionHandlerTest,SpaForwardControllerTest `
  test
```

Expected: BUILD SUCCESS with unchanged HTTP contracts.

- [ ] **Step 6: Run all backend tests**

Run:

```powershell
mvn.cmd -f backend/pom.xml test
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Run backend compilation without frontend packaging**

Run:

```powershell
mvn.cmd -f backend/pom.xml -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

Do not run `mvn package`, because the current Maven packaging phase requires `frontend/dist/index.html` and would couple this backend refactor to the independently changing frontend.

- [ ] **Step 8: Run public snapshot privacy checks**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/privacy-check.ps1 `
  -Path backend/src/main/resources/public-data
```

Expected: privacy checker tests and public snapshot scan pass.

- [ ] **Step 9: Explicitly skip frontend and integration verification**

Record in the implementation report:

```text
Frontend source, frontend tests, frontend build, Maven package, executable JAR checks,
Playwright, Docker checks, and browser/API integration were intentionally not run because
the frontend is undergoing an independent large refactor. This task claims backend-only
verification, not release readiness.
```

- [ ] **Step 10: Inspect the final package tree**

Run:

```powershell
Get-ChildItem -Recurse -Directory `
  backend\src\main\java\com\portfolio\agent |
  Select-Object -ExpandProperty FullName
```

Expected top-level business directories:

```text
common
portfolio
answer
```

Expected no `api`, `application`, or `infrastructure` directories under Portfolio or Answer.

- [ ] **Step 11: Inspect dependency direction**

Run:

```powershell
Get-ChildItem -Recurse -File `
  backend\src\main\java\com\portfolio\agent\answer\service,`
  backend\src\main\java\com\portfolio\agent\answer\domain,`
  backend\src\main\java\com\portfolio\agent\answer\engine,`
  backend\src\main\java\com\portfolio\agent\answer\gateway |
  Select-String -Pattern 'com\.portfolio\.agent\.portfolio\.'

Get-ChildItem -Recurse -File `
  backend\src\main\java\com\portfolio\agent\portfolio\service |
  Select-String -Pattern 'com\.portfolio\.agent\.portfolio\.(controller|dto)\.'
```

Expected: no output.

- [ ] **Step 12: Record final suggested commit**

After the user explicitly resolves the pre-existing staged `.idea` files, suggested commit:

```text
refactor: establish modular monolith backend boundaries
```

Before any commit, run:

```powershell
git status --short
git diff --cached --name-only
```

Do not commit if unrelated staged files remain.

---

## Final Completion Checklist

- [ ] Public API URLs, JSON fields, status codes, and messages are unchanged.
- [ ] Portfolio Service returns application/domain results, not response DTOs.
- [ ] A Portfolio project query reads exactly one snapshot.
- [ ] Answer Service uses `PortfolioKnowledgeGateway`.
- [ ] Only `LocalPortfolioKnowledgeAdapter` imports both Portfolio and Answer types.
- [ ] DeterministicAnswerEngine does not access Repository or Portfolio types.
- [ ] AnswerResult owns `AnswerEvidence`, not `EvidenceRecord`.
- [ ] No Feign, Spring Cloud, HTTP Service Client, or remote self-call is added.
- [ ] Legacy `api/application/infrastructure/domain.model/domain.repository` packages are removed.
- [ ] Architecture checker passes as a standalone backend gate; full release-pipeline wiring is explicitly deferred.
- [ ] Code-quality, architecture, backend JUnit, backend compilation, and public snapshot privacy verification all pass.
- [ ] Frontend tests/build, Maven package, JAR, Playwright, Docker, and browser integration are explicitly deferred and are not claimed as passing.
- [ ] Documentation matches the implemented package names.
- [ ] No unrelated staged or untracked user files are modified or committed.
