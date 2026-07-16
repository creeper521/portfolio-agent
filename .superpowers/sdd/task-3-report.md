# Task 3 Report: Close the Portfolio Service Boundary

## Scope

Implemented Task 3 only:

- added immutable application models `PortfolioOverview` and `ProjectDetails`;
- replaced `portfolio.application.PortfolioQueryService` with
  `portfolio.service.PortfolioService`;
- added `PortfolioResponseMapper`;
- moved `PortfolioController` and its test from `portfolio.api` to
  `portfolio.controller`;
- added the specified `ProjectDetailResponse.from(...)` factory;
- added `PortfolioServiceTest` with a counting repository.

No Answer structure, frontend source, release verification script, public resource
schema, or Task 4+ behavior was changed.

## TDD Evidence

RED:

```text
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml
  -Dtest=PortfolioServiceTest test

BUILD FAILURE
PortfolioService, PortfolioOverview, and ProjectDetails did not exist.
```

GREEN:

```text
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml
  -Dtest=PortfolioServiceTest test

Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The service test proves `getProject("sql-audit")` reads exactly one snapshot.

## Compatibility Verification

Focused Portfolio verification:

```text
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml
  -Dtest=PortfolioControllerTest,PortfolioServiceTest test

Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

This preserves:

- `GET /api/v1/portfolio` with the existing status and JSON contract;
- `GET /api/v1/projects/sql-audit` with existing project, Evidence, and
  suggestion values;
- unknown project HTTP 404 with `PROJECT_NOT_FOUND`.

Full backend verification:

```text
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml test

Tests run: 41, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Quality and Architecture Gates

```text
scripts/code-quality-check.test.ps1
code-quality-check tests passed

scripts/code-quality-check.ps1 -Path backend/src
Code quality check passed

scripts/architecture-check.test.ps1
architecture-check tests passed
```

The production architecture scan remains non-zero only for planned Answer legacy
imports that are outside Task 3:

```text
answer-core-portfolio: ... AnswerResult.java
answer-core-portfolio: ... AnswerModelContractTest.java
ARCH_EXIT=1
PORTFOLIO_SERVICE_CONTROLLER_VIOLATIONS=0
```

There are no `portfolio-service-controller` violations. The Portfolio service
imports neither controller nor response DTO packages.

## Notes

`mvn.cmd` was not on this shell's `PATH`; verification used the installed Maven
executable at `C:\tools\apache-maven-3.9.9\bin\mvn.cmd`.
