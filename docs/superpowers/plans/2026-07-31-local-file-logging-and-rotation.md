# Local File Logging and Rotation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route hidden Spring Boot, browser-diagnostic, Vite, and launcher output into four safe repository-local log files with live viewing, size segmentation, recoverable daily ZIP archives, seven-day retention, and a 2 GB cap.

**Architecture:** Keep application and Vite processes writing to stdout/stderr. Upgrade the local launcher to asynchronously capture both child streams and submit normalized records to one PowerShell log-router module, which is the only file writer. Browser events continue through the existing constrained backend endpoint and are routed to frontend files by `event.origin=browser`; production remains console-first.

**Tech Stack:** PowerShell 5.1+, Java 21, Spring Boot 3.5.3, SLF4J/Logback, Vue 3, TypeScript 5.8, Vitest, JUnit 5, Maven

## Global Constraints

- Do not change frontend visual appearance, layout, CSS, or user-facing interaction design.
- Store local files under `<repository>/logs` and add `/logs/` to `.gitignore`.
- Keep exactly four active logical streams: backend info, backend error, frontend info, frontend error.
- INFO files accept DEBUG/INFO/WARN and exclude ERROR; ERROR files accept only ERROR.
- Preserve permanent privacy rules: never log visitor questions, answers, messages, prompts, Provider payloads, credentials, request/response bodies, raw headers, raw IPs, or raw exception messages.
- Use UTF-8 without BOM and never write ANSI color escapes to files.
- Each active stream uses 20 MB segments and keeps at most five segments.
- Create one verified ZIP per logged calendar day; retain the last seven completed calendar days and at most 2 GB across archives and snapshots.
- Logging, archiving, cleanup, diagnostic upload, and watching failures must not alter business responses or terminate healthy frontend/backend processes.
- Production remains stdout/ECS-first; local file routing is owned by the one-click launcher.
- Preserve unrelated working-tree changes and use Chinese commit messages.

## File Map

### New files

- `scripts/logging/LocalLogRouter.psm1` — classification, sanitization, bounded queue, single-writer files, segmentation, rollover, recovery, archive, retention.
- `scripts/local-log-router.test.ps1` — isolated router, segmentation, archive, recovery, retention, and privacy tests.
- `scripts/watch-local-logs.ps1` — live and archived log reader with filters.
- `scripts/watch-local-logs.test.ps1` — watcher offset, replacement, filtering, and ZIP tests.
- `scripts/archive-local-logs.ps1` — explicit maintenance and current-day snapshot command.
- `scripts/archive-local-logs.test.ps1` — maintenance CLI safety and snapshot tests.

### Modified files

- `.gitignore` — ignore `/logs/`.
- `scripts/start-local.ps1` — use redirected asynchronous child processes, router lifecycle, `-LogDirectory`, `-FollowLogs`, and local browser diagnostic enablement.
- `scripts/start-local.test.ps1` — launcher/router integration and cleanup.
- `scripts/test-fixtures/start-local-fake-server.ps1` — deterministic stdout/stderr fixture lines.
- `backend/src/main/java/com/portfolio/agent/common/observability/FrontendDiagnosticEventName.java` — safe lifecycle event names and levels.
- `backend/src/main/java/com/portfolio/agent/common/observability/DiagnosticEvent.java` — approved frontend event fields.
- `backend/src/main/java/com/portfolio/agent/common/web/FrontendDiagnosticEventRequest.java` — validated lifecycle metadata.
- `backend/src/main/java/com/portfolio/agent/common/web/FrontendDiagnosticsController.java` — map the new fields to typed diagnostics.
- `backend/src/test/java/com/portfolio/agent/common/observability/DiagnosticEventTest.java` — field allowlist coverage.
- `backend/src/test/java/com/portfolio/agent/common/web/FrontendDiagnosticsControllerTest.java` — DTO, level, origin, and privacy coverage.
- `frontend/src/shared/diagnostics/frontendDiagnosticTypes.ts` — safe lifecycle events and fields.
- `frontend/src/shared/diagnostics/frontendDiagnostics.ts` — application lifecycle report helper.
- `frontend/src/shared/diagnostics/frontendDiagnostics.test.ts` — serialization/privacy tests.
- `frontend/src/features/agent/model/answerTypes.ts` — preserve response `contentVersion` in the mapped answer.
- `frontend/src/features/agent/model/mapAnswerResponse.ts` — map `contentVersion` without changing presentation.
- `frontend/src/features/agent/model/mapAnswerResponse.test.ts` — mapped version contract.
- `frontend/src/features/public-content/composables/usePublicContent.ts` — content-load success event.
- `frontend/src/features/public-content/composables/usePublicContent.test.ts` — success event test.
- `frontend/src/features/agent/components/AgentWorkspace.vue` — answer-completed event only; no visual changes.
- `frontend/src/features/agent/components/AgentWorkspace.test.ts` — completed event contract.
- `scripts/privacy-check.ps1` and `scripts/privacy-check.test.ps1` — scan active logs, ZIP entries, snapshots, and watcher output without treating ignored logs as publishable assets.
- `README.md`, `docs/08-当前实现状态.md`, and `docs/11-项目演进日志.md` — operations and status.

---

### Task 1: Pure log classification and sanitization

**Files:**
- Create: `scripts/logging/LocalLogRouter.psm1`
- Create: `scripts/local-log-router.test.ps1`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `ConvertTo-LocalLogRecord -Stream <string> -Line <string> -RepositoryRoot <string> -HomeDirectory <string> -Now <DateTimeOffset>`
- Produces record fields: `Timestamp`, `Domain`, `Level`, `Source`, `Text`, `Redacted`
- Produces: `Format-LocalLogRecord -Record <pscustomobject>`
- No file I/O is introduced in this task.

- [ ] **Step 1: Write failing classification and privacy tests**

Add `/logs/` to the expected ignore contract and create tests with explicit cases:

```powershell
$cases = @(
    @{
        Stream = 'BACKEND_STDOUT'
        Line = '2026-07-31 INFO com.portfolio.agent.diagnostics - http.request.completed'
        Domain = 'BACKEND'
        Level = 'INFO'
        Source = 'SPRING'
    },
    @{
        Stream = 'BACKEND_STDOUT'
        Line = '2026-07-31 ERROR com.portfolio.agent.diagnostics - http.request.failed'
        Domain = 'BACKEND'
        Level = 'ERROR'
        Source = 'SPRING'
    },
    @{
        Stream = 'BACKEND_STDOUT'
        Line = 'INFO event.origin=browser event.name=frontend.agent.request.completed'
        Domain = 'FRONTEND'
        Level = 'INFO'
        Source = 'BROWSER'
    },
    @{
        Stream = 'VITE_STDOUT'
        Line = '[vite] hmr update /src/App.vue'
        Domain = 'FRONTEND'
        Level = 'INFO'
        Source = 'VITE'
    },
    @{
        Stream = 'VITE_STDERR'
        Line = '[vite] Internal server error'
        Domain = 'FRONTEND'
        Level = 'ERROR'
        Source = 'VITE'
    }
)

foreach ($case in $cases) {
    $record = ConvertTo-LocalLogRecord `
        -Stream $case.Stream `
        -Line $case.Line `
        -RepositoryRoot $repositoryRoot `
        -HomeDirectory $homeDirectory `
        -Now $fixedNow
    Assert-Equal $case.Domain $record.Domain "$($case.Stream) domain"
    Assert-Equal $case.Level $record.Level "$($case.Stream) level"
    Assert-Equal $case.Source $record.Source "$($case.Stream) source"
}
```

Add sentinel cases for ANSI escapes, repository/home paths, query strings, `Authorization: Bearer`, `api_key=`, private-key material, control characters, and lines longer than 8 KB. Credential-like lines must become exactly:

```text
OUTPUT_REDACTED reason=CREDENTIAL_PATTERN
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/local-log-router.test.ps1
```

Expected: FAIL because `LocalLogRouter.psm1` and the conversion functions do not exist.

- [ ] **Step 3: Implement pure conversion functions**

Create the module with exported pure functions:

```powershell
function ConvertTo-LocalLogRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet(
            'BACKEND_STDOUT',
            'BACKEND_STDERR',
            'VITE_STDOUT',
            'VITE_STDERR',
            'LAUNCHER'
        )]
        [string]$Stream,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Line,
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,
        [Parameter(Mandatory = $true)]
        [string]$HomeDirectory,
        [Parameter(Mandatory = $true)]
        [DateTimeOffset]$Now
    )

    $domain = if ($Stream.StartsWith('VITE_')) { 'FRONTEND' } else { 'BACKEND' }
    $source = if ($Stream.StartsWith('VITE_')) { 'VITE' } `
        elseif ($Stream -eq 'LAUNCHER') { 'LAUNCHER' } `
        else { 'SPRING' }
    if ($Line -match '(?:^|\s)event\.origin=browser(?:\s|$)') {
        $domain = 'FRONTEND'
        $source = 'BROWSER'
    }

    $level = Get-LocalLogLevel -Stream $Stream -Line $Line
    $sanitized = Protect-LocalLogText `
        -Line $Line `
        -RepositoryRoot $RepositoryRoot `
        -HomeDirectory $HomeDirectory

    return [pscustomobject]@{
        Timestamp = $Now
        Domain = $domain
        Level = $level
        Source = $source
        Text = $sanitized.Text
        Redacted = $sanitized.Redacted
    }
}

function Format-LocalLogRecord {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][pscustomobject]$Record)
    return '{0} [{1}][{2}][{3}] {4}' -f `
        $Record.Timestamp.ToString('yyyy-MM-ddTHH:mm:ss.fffzzz'), `
        $Record.Domain, `
        $Record.Level, `
        $Record.Source, `
        $Record.Text
}

Export-ModuleMember -Function `
    ConvertTo-LocalLogRecord, `
    Format-LocalLogRecord
```

Implement `Get-LocalLogLevel` with explicit ERROR/WARN/DEBUG tokens and stream fallback; implement `Protect-LocalLogText` with ANSI/control removal, path replacement, URL query/fragment removal, credential whole-line redaction, and 8 KB cap.

- [ ] **Step 4: Run focused tests and privacy assertions**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/local-log-router.test.ps1
```

Expected: all pure classification and sanitization tests pass; the test output and temporary files contain none of the sentinels.

- [ ] **Step 5: Commit**

```powershell
git add .gitignore scripts/logging/LocalLogRouter.psm1 scripts/local-log-router.test.ps1
git commit -m "日志：增加安全的本地日志分类与脱敏"
```

---

### Task 2: Single-writer queue, four files, and size segments

**Files:**
- Modify: `scripts/logging/LocalLogRouter.psm1`
- Modify: `scripts/local-log-router.test.ps1`

**Interfaces:**
- Consumes: `ConvertTo-LocalLogRecord`, `Format-LocalLogRecord`
- Produces: `New-LocalLogRouter -RepositoryRoot -LogDirectory -Clock -MaxFileBytes -MaxSegments -QueueCapacity`
- Produces: `Submit-LocalLogLine -Router -Stream -Line`
- Produces: `Flush-LocalLogRouter -Router`
- Produces: `Stop-LocalLogRouter -Router`
- Router health fields: `StatusCode`, `DroppedDebug`, `DroppedInfo`, `DroppedWarn`, `DroppedError`

- [ ] **Step 1: Write failing routing, non-duplication, segmentation, and pressure tests**

Use a temporary log root, `MaxFileBytes=1024`, `MaxSegments=3`, and a deterministic clock. Assert:

```powershell
Submit-LocalLogLine -Router $router -Stream BACKEND_STDOUT `
    -Line 'INFO backend-info-sentinel'
Submit-LocalLogLine -Router $router -Stream BACKEND_STDERR `
    -Line 'ERROR backend-error-sentinel'
Submit-LocalLogLine -Router $router -Stream BACKEND_STDOUT `
    -Line 'INFO event.origin=browser browser-info-sentinel'
Submit-LocalLogLine -Router $router -Stream VITE_STDERR `
    -Line 'ERROR vite-error-sentinel'
Flush-LocalLogRouter -Router $router

Assert-Contains $backendInfo 'backend-info-sentinel'
Assert-NotContains $backendInfo 'backend-error-sentinel'
Assert-Contains $backendError 'backend-error-sentinel'
Assert-Contains $frontendInfo 'browser-info-sentinel'
Assert-Contains $frontendError 'vite-error-sentinel'
```

Generate enough lines to create `.1` and `.2`, then verify a fourth rotation removes the oldest segment and records truncation. Fill a small queue and verify DEBUG/INFO drops occur before WARN/ERROR without blocking the submitting loop.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/local-log-router.test.ps1
```

Expected: FAIL because router lifecycle and file writer functions are missing.

- [ ] **Step 3: Implement one writer and bounded queue**

The router object must own:

```powershell
[pscustomobject]@{
    RepositoryRoot = $resolvedRepositoryRoot
    LogDirectory = $resolvedLogDirectory
    CurrentDirectory = (Join-Path $resolvedLogDirectory 'current')
    Queue = [System.Collections.Concurrent.ConcurrentQueue[object]]::new()
    QueueSignal = [System.Threading.AutoResetEvent]::new($false)
    QueueCapacity = $QueueCapacity
    MaxFileBytes = $MaxFileBytes
    MaxSegments = $MaxSegments
    StopRequested = $false
    StatusCode = 'READY'
    DroppedDebug = 0L
    DroppedInfo = 0L
    DroppedWarn = 0L
    DroppedError = 0L
}
```

Use one background runspace as the only writer. Map records with:

```powershell
function Get-LocalLogBaseName {
    param([pscustomobject]$Record)
    if ($Record.Domain -eq 'BACKEND' -and $Record.Level -eq 'ERROR') {
        return 'backend-error'
    }
    if ($Record.Domain -eq 'BACKEND') {
        return 'backend-info'
    }
    if ($Record.Level -eq 'ERROR') {
        return 'frontend-error'
    }
    return 'frontend-info'
}
```

Before each append, rotate when the current file plus encoded line exceeds `MaxFileBytes`. Shift `.3 → .4`, `.2 → .3`, `.1 → .2`, active → `.1`, and remove anything beyond `MaxSegments - 1`. Never write one ERROR to both files.

- [ ] **Step 4: Run focused tests**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/local-log-router.test.ps1
```

Expected: routing, strict non-duplication, segmentation, queue-pressure, UTF-8, and stop/flush tests pass.

- [ ] **Step 5: Commit**

```powershell
git add scripts/logging/LocalLogRouter.psm1 scripts/local-log-router.test.ps1
git commit -m "日志：实现四路单写入与大小分片"
```

---

### Task 3: Daily ZIP, crash recovery, retention, and maintenance CLI

**Files:**
- Modify: `scripts/logging/LocalLogRouter.psm1`
- Modify: `scripts/local-log-router.test.ps1`
- Create: `scripts/archive-local-logs.ps1`
- Create: `scripts/archive-local-logs.test.ps1`

**Interfaces:**
- Produces: `Invoke-LocalLogMaintenance -Router`
- Produces: `Invoke-LocalLogDateRollover -Router -NewDate <DateOnly-compatible string>`
- Produces: `New-LocalLogSnapshot -RepositoryRoot -LogDirectory -Now`
- ZIP contains `manifest.json` with schema version, date/timezone, hashes, sizes, counts, truncation, and drop counts.

- [ ] **Step 1: Write failing date rollover and recovery tests**

Use injected dates rather than sleeping. Cover these exact states:

```text
old current + old .active-date
staging only
staging + valid temporary ZIP
staging + valid final ZIP with matching hashes
staging + conflicting final ZIP
corrupt temporary ZIP
```

Assert that a conflicting final ZIP is never overwritten and returns:

```text
LOG_ARCHIVE_CONFLICT
```

Create archives dated 8, 7, 6, and 1 days ago and verify only the 8-day archive is removed. Create small archives with an injected `TotalArchiveBytes=2048` cap and verify oldest-first deletion. Unknown files and staging must remain untouched.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/local-log-router.test.ps1

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/archive-local-logs.test.ps1
```

Expected: FAIL because maintenance, archive, manifest, and CLI functions are missing.

- [ ] **Step 3: Implement atomic rollover and verified ZIP**

Implement the critical sequence:

```powershell
Flush-LocalLogRouter -Router $Router
Close-LocalLogWriters -Router $Router
Move-LocalLogCurrentToStaging -Router $Router -ArchiveDate $oldDate
Open-LocalLogWriters -Router $Router -ActiveDate $newDate
Start-LocalLogArchiveWork -Router $Router -ArchiveDate $oldDate
```

Archive work must:

1. write `manifest.json` with UTF-8 no BOM;
2. create `archive/<name>.tmp`;
3. open the ZIP and verify every entry;
4. compare entry sizes and SHA-256 with manifest;
5. rename `.tmp` to `portfolio-agent-YYYY-MM-DD.zip`;
6. only then remove matching staging.

Path safety must resolve all delete/move targets and reject anything outside the resolved log root, including reparse-point escape.

- [ ] **Step 4: Implement maintenance and snapshot CLI**

`archive-local-logs.ps1` parameters:

```powershell
param(
    [string]$LogDirectory = '',
    [switch]$IncludeCurrentDay,
    [ValidateRange(1, 365)]
    [int]$RetentionDays = 7,
    [ValidateRange(1, 10240)]
    [int]$TotalSizeMegabytes = 2048
)
```

Without `-IncludeCurrentDay`, recover/roll old logs and clean retention. With it, flush/copy current logs into a timestamped snapshot ZIP without consuming a daily archive name.

- [ ] **Step 5: Run archive and recovery tests**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/local-log-router.test.ps1

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/archive-local-logs.test.ps1
```

Expected: all date, segmentation manifest, ZIP verification, crash recovery, seven-day, 2 GB, snapshot, and path safety tests pass.

- [ ] **Step 6: Commit**

```powershell
git add scripts/logging/LocalLogRouter.psm1 `
  scripts/local-log-router.test.ps1 `
  scripts/archive-local-logs.ps1 `
  scripts/archive-local-logs.test.ps1
git commit -m "日志：增加每日归档、恢复与七日清理"
```

---

### Task 4: Live and archived log watcher

**Files:**
- Create: `scripts/watch-local-logs.ps1`
- Create: `scripts/watch-local-logs.test.ps1`

**Interfaces:**
- Parameters: `-LogDirectory`, `-Level`, `-Source`, `-Tail`, `-NoColor`, `-ArchiveDate`
- Active mode follows file identity and byte offsets.
- Archive mode streams ZIP entries without extracting to the repository.

- [ ] **Step 1: Write failing watcher tests**

Create four temporary files with interleaved ISO timestamps. Assert:

```powershell
$result = Invoke-Watcher -Level ERROR -Source BACKEND -Tail 2
Assert-Equal 2 $result.Lines.Count 'tail count'
Assert-AllMatch $result.Lines '\\[BACKEND\\]\\[ERROR\\]'
Assert-NotMatch ($result.Lines -join "`n") ([char]27)
```

Replace and truncate one active file while the watcher test seam polls; verify it resumes from offset zero exactly once. Build a ZIP in memory and verify `-ArchiveDate` reads it without creating extracted files.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/watch-local-logs.test.ps1
```

Expected: FAIL because the watcher does not exist.

- [ ] **Step 3: Implement reader state and filters**

Use a state object per active file:

```powershell
[pscustomobject]@{
    Path = $path
    Identity = $null
    Offset = 0L
    PendingBytes = [byte[]]@()
}
```

On each poll:

- compare file identity and length;
- reset offset when identity changes or length shrinks;
- decode complete UTF-8 lines only;
- parse timestamp/domain/level/source prefix;
- filter without changing file content;
- merge newly read records by timestamp and stable file order.

Color only terminal rendering, never stored strings.

- [ ] **Step 4: Run watcher tests**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/watch-local-logs.test.ps1
```

Expected: Tail, filter combinations, replacement, truncation, rollover, UTF-8 partial-line, no-color, and ZIP streaming tests pass.

- [ ] **Step 5: Commit**

```powershell
git add scripts/watch-local-logs.ps1 scripts/watch-local-logs.test.ps1
git commit -m "工具：增加本地日志实时与归档查看器"
```

---

### Task 5: One-click launcher integration

**Files:**
- Modify: `scripts/start-local.ps1`
- Modify: `scripts/start-local.test.ps1`
- Modify: `scripts/test-fixtures/start-local-fake-server.ps1`

**Interfaces:**
- Consumes: router lifecycle from Tasks 1–3.
- Consumes: `scripts/watch-local-logs.ps1`.
- Produces launcher parameters: `-LogDirectory`, `-FollowLogs`.
- Produces status lines: `LOG_DIRECTORY`, `LOG_WATCH_COMMAND`, `LOG_ROUTER_DEGRADED:<code>`.

- [ ] **Step 1: Write failing orchestration tests**

Extend the fake backend/frontend fixtures to emit deterministic lines:

```powershell
Write-Output 'INFO backend-fixture-info'
[Console]::Error.WriteLine('ERROR backend-fixture-error')
Write-Output 'INFO event.origin=browser browser-fixture-info'
```

For frontend mode:

```powershell
Write-Output '[vite] ready vite-fixture-info'
[Console]::Error.WriteLine('[vite] Internal server error vite-fixture-error')
```

Run the launcher with a temporary `-LogDirectory` and `-ExitAfterProbe`, then assert all four files contain only their intended sentinels, ports are closed, and the Secret sentinel is absent.

- [ ] **Step 2: Run launcher tests and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/start-local.test.ps1
```

Expected: FAIL because the launcher does not accept `-LogDirectory`/`-FollowLogs` and does not capture child streams.

- [ ] **Step 3: Replace hidden unmanaged output with redirected async processes**

Replace `Start-Process -WindowStyle Hidden` with a focused helper using:

```powershell
$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $Executable
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$startInfo.WorkingDirectory = $script:repositoryRoot
```

Attach `OutputDataReceived` and `ErrorDataReceived` handlers that only submit lines to the bounded router queue. Never perform disk I/O inside the process event callback.

Inject this non-secret local child setting separately from the Secret whitelist:

```text
PORTFOLIO_DIAGNOSTICS_FRONTEND_INGEST_ENABLED=true
```

Do not add it to the external Secret file requirements.

> **补充说明（2026-08-04）：** 该计划已执行；启动器注入的变量此后改为直接绑定
> `portfolio.diagnostics.frontend-ingest-enabled` 的 `PORTFOLIO_DIAGNOSTICS_FRONTEND_INGEST_ENABLED`，
> 直接 `npm run dev` 的日志落盘由 [Direct Vite Local Logging Design](../specs/2026-08-04-direct-vite-local-logging-design.md) 接替。

- [ ] **Step 4: Wire startup, rollover, watch, and cleanup**

At startup:

1. resolve default `<repository>/logs`;
2. recover old logs before child start;
3. create router;
4. start backend and frontend with redirected streams;
5. publish launcher lifecycle records;
6. print log directory and watch command;
7. invoke watcher when `-FollowLogs`.

In `finally`, detach process handlers, flush/stop router, and preserve existing owned-child cleanup guarantees.

- [ ] **Step 5: Run launcher and all PowerShell component tests**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/local-log-router.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/watch-local-logs.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/archive-local-logs.test.ps1
```

Expected: all tests pass; no test-owned process or port remains.

- [ ] **Step 6: Commit**

```powershell
git add scripts/start-local.ps1 `
  scripts/start-local.test.ps1 `
  scripts/test-fixtures/start-local-fake-server.ps1
git commit -m "工具：一键启动接入异步文件日志"
```

---

### Task 6: Backend browser lifecycle event contract

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/common/observability/FrontendDiagnosticEventName.java`
- Modify: `backend/src/main/java/com/portfolio/agent/common/observability/DiagnosticEvent.java`
- Modify: `backend/src/main/java/com/portfolio/agent/common/web/FrontendDiagnosticEventRequest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/common/web/FrontendDiagnosticsController.java`
- Modify: `backend/src/test/java/com/portfolio/agent/common/observability/DiagnosticEventTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/common/web/FrontendDiagnosticsControllerTest.java`

**Interfaces:**
- New event names: `frontend.application.started`, `frontend.content.load.completed`, `frontend.agent.request.completed`.
- Existing cancelled event remains INFO.
- New JSON fields: `httpStatus`, `generationMode`, `degraded`, `guidanceStage`, `suggestedQuestionCount`, `contentVersion`, `recoveredCount`.
- Diagnostic fields: `http.status_code`, `generation.mode`, `answer.degraded`, `guidance.stage`, `suggestion.count`, `content.version`, `recovery.count`.

- [ ] **Step 1: Write failing DTO and event-level tests**

Post one completed event:

```json
{
  "schemaVersion": 1,
  "eventName": "frontend.agent.request.completed",
  "occurredAt": "2026-07-31T07:23:01.245Z",
  "clientSessionId": "10000000-0000-4000-8000-000000000001",
  "clientRequestId": "10000000-0000-4000-8000-000000000002",
  "turnId": "10000000-0000-4000-8000-000000000003",
  "durationBucket": "FROM_1000_TO_4999_MS",
  "httpStatus": 200,
  "generationMode": "MODEL",
  "degraded": false,
  "guidanceStage": "DEEPENING",
  "suggestedQuestionCount": 3,
  "contentVersion": "2026-07-29.1"
}
```

Assert `event.origin=browser`, INFO level, and every mapped safe field. Add invalid tests for status outside 100–599, unknown enums, counts outside 0–3, duplicate/unknown JSON fields, and content version over 64 characters.

Change failure-level expectations so content-load failure, agent-request failure, and runtime failure are ERROR; slow/invalid remain WARN; cancelled remains INFO.

- [ ] **Step 2: Run focused backend tests and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -DskipFrontend=true `
  '-Dtest=FrontendDiagnosticsControllerTest,DiagnosticEventTest' test
```

Expected: FAIL because event names and fields are unsupported.

- [ ] **Step 3: Implement validated enums and allowlists**

Add enums inside `FrontendDiagnosticEventRequest`:

```java
public enum GenerationMode {
    DETERMINISTIC,
    MODEL,
    FALLBACK
}

public enum GuidanceStage {
    OPENING,
    DEEPENING,
    WRAP_UP,
    EXPLORE_OTHERS
}
```

Use `@Min/@Max` for `httpStatus`, `suggestedQuestionCount`, and `recoveredCount`; use `@Size(max = 64)` plus a conservative version pattern for `contentVersion`.

Extend `FRONTEND_FIELDS` and add the three event names to `APPROVED_FIELDS_BY_EVENT`. Map only non-null request values in `FrontendDiagnosticsController`.

- [ ] **Step 4: Run focused and privacy tests**

Run:

```powershell
mvn.cmd -f backend/pom.xml -DskipFrontend=true `
  '-Dtest=FrontendDiagnosticsControllerTest,DiagnosticEventTest,RuntimeCompositePrivacyTest' test
```

Expected: all tests pass and captured logs contain no visitor/credential sentinels.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/common/observability/FrontendDiagnosticEventName.java `
  backend/src/main/java/com/portfolio/agent/common/observability/DiagnosticEvent.java `
  backend/src/main/java/com/portfolio/agent/common/web/FrontendDiagnosticEventRequest.java `
  backend/src/main/java/com/portfolio/agent/common/web/FrontendDiagnosticsController.java `
  backend/src/test/java/com/portfolio/agent/common/observability/DiagnosticEventTest.java `
  backend/src/test/java/com/portfolio/agent/common/web/FrontendDiagnosticsControllerTest.java
git commit -m "日志：扩展浏览器安全生命周期事件"
```

---

### Task 7: Non-visual frontend lifecycle reporting

**Files:**
- Modify: `frontend/src/shared/diagnostics/frontendDiagnosticTypes.ts`
- Modify: `frontend/src/shared/diagnostics/frontendDiagnostics.ts`
- Modify: `frontend/src/shared/diagnostics/frontendDiagnostics.test.ts`
- Modify: `frontend/src/features/agent/model/answerTypes.ts`
- Modify: `frontend/src/features/agent/model/mapAnswerResponse.ts`
- Modify: `frontend/src/features/agent/model/mapAnswerResponse.test.ts`
- Modify: `frontend/src/features/public-content/composables/usePublicContent.ts`
- Modify: `frontend/src/features/public-content/composables/usePublicContent.test.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`

**Interfaces:**
- Adds only diagnostic types and calls; no template/CSS/layout changes.
- Produces safe application-started, content-load-completed, and answer-completed events.
- Serialization remains fail-closed and best effort.

- [ ] **Step 1: Write failing serialization and emission tests**

Extend the TS event union and tests with:

```ts
const completed = createFrontendDiagnosticEvent({
  eventName: 'frontend.agent.request.completed',
  turnId: '10000000-0000-4000-8000-000000000003',
  durationBucket: 'FROM_1000_TO_4999_MS',
  httpStatus: 200,
  generationMode: 'MODEL',
  degraded: false,
  guidanceStage: 'DEEPENING',
  suggestedQuestionCount: 3,
  contentVersion: '2026-07-29.1',
})
```

Assert exact sanitized output and rejection of invalid enum/count/version/status values.

Mock `frontendDiagnostics.report` in public-content and AgentWorkspace tests. Successful content load must emit once; successful answer must emit once with no question, answer, messages, request body, or response body field.

Extend `mapAnswerResponse.test.ts` to assert that the response `contentVersion` is copied to `MappedAnswer.contentVersion`.

- [ ] **Step 2: Run focused frontend tests and verify RED**

Run:

```powershell
npm.cmd --prefix frontend run test -- --run `
  src/shared/diagnostics/frontendDiagnostics.test.ts `
  src/features/public-content/composables/usePublicContent.test.ts `
  src/features/agent/components/AgentWorkspace.test.ts
```

Expected: FAIL because new event names and fields are not accepted or emitted.

- [ ] **Step 3: Implement safe types and application/content events**

Add types:

```ts
export type FrontendGenerationMode = 'DETERMINISTIC' | 'MODEL' | 'FALLBACK'

export interface FrontendDiagnosticEventInput {
  // existing fields
  httpStatus?: number
  generationMode?: FrontendGenerationMode
  degraded?: boolean
  suggestedQuestionCount?: number
  contentVersion?: string
}
```

Update `serializeFrontendEvent` with closed enum/range/pattern checks. In `installRuntimeDiagnostics`, report `frontend.application.started` once after installing handlers. In successful public-content resolution, report `frontend.content.load.completed` with content version only.

Add this field immediately after `turnId` in `MappedAnswer`:

```ts
contentVersion: string
```

Add this property immediately after `turnId: response.turnId` in the `mapAnswerResponse` return object:

```ts
contentVersion: response.contentVersion,
```

- [ ] **Step 4: Emit answer-completed without visual changes**

Measure elapsed time inside the existing request lifecycle and, after mapping/completing suggestions, report:

```ts
frontendDiagnostics.report(createFrontendDiagnosticEvent({
  eventName: 'frontend.agent.request.completed',
  turnId: mapped.turnId,
  durationBucket: durationBucketFor(elapsedMilliseconds),
  httpStatus: 200,
  ...(mapped.generationMode === undefined
    ? {}
    : { generationMode: mapped.generationMode }),
  degraded: mapped.degraded === true,
  ...(mapped.guidanceStage === null
    ? {}
    : { guidanceStage: mapped.guidanceStage }),
  suggestedQuestionCount: mapped.suggestedQuestions.length,
  contentVersion: mapped.contentVersion,
}))
```

Do not edit `<template>`, `<style>`, CSS, labels, layout, or interaction behavior.

- [ ] **Step 5: Run frontend tests, typecheck, and build**

Run:

```powershell
npm.cmd --prefix frontend run test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run build
```

Expected: all tests pass, typecheck passes, build succeeds, and no visual snapshot/contract changes are required.

- [ ] **Step 6: Commit**

```powershell
git add frontend/src/shared/diagnostics/frontendDiagnosticTypes.ts `
  frontend/src/shared/diagnostics/frontendDiagnostics.ts `
  frontend/src/shared/diagnostics/frontendDiagnostics.test.ts `
  frontend/src/features/agent/model/answerTypes.ts `
  frontend/src/features/agent/model/mapAnswerResponse.ts `
  frontend/src/features/agent/model/mapAnswerResponse.test.ts `
  frontend/src/features/public-content/composables/usePublicContent.ts `
  frontend/src/features/public-content/composables/usePublicContent.test.ts `
  frontend/src/features/agent/components/AgentWorkspace.vue `
  frontend/src/features/agent/components/AgentWorkspace.test.ts
git commit -m "日志：补充前端非视觉生命周期诊断"
```

---

### Task 8: Privacy gates, documentation, and full verification

**Files:**
- Modify: `scripts/privacy-check.ps1`
- Modify: `scripts/privacy-check.test.ps1`
- Modify: `README.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`

**Interfaces:**
- Privacy scanner explicitly inspects local log fixtures and ZIP entries when requested, while `/logs/` remains ignored by Git.
- Documentation exposes launcher, watcher, archive, snapshot, retention, and recovery commands.

- [ ] **Step 1: Write failing privacy archive tests**

Create safe and unsafe active logs, daily ZIPs, and snapshot ZIPs. Assert safe files pass and each forbidden sentinel fails with a stable category without echoing the secret:

```text
visitor-content
credential
authorization
absolute-path
provider-payload
request-body
response-body
raw-exception
```

Add a ZIP-slip fixture and assert the scanner reads entries as streams without extracting them.

- [ ] **Step 2: Run privacy tests and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/privacy-check.test.ps1
```

Expected: FAIL because log ZIP/snapshot scanning contracts are not implemented.

- [ ] **Step 3: Extend the scanner and operations docs**

Add an explicit log-artifact scan path that:

- bounds ZIP entry count and uncompressed bytes;
- rejects traversal entry names;
- scans text entries in memory;
- never prints matched secret values;
- does not make ignored `/logs/` a release artifact.

Document:

```powershell
scripts/start-local.ps1 -SecretsFile C:\secrets\portfolio-agent-model.env
scripts/start-local.ps1 -SecretsFile C:\secrets\portfolio-agent-model.env -FollowLogs
scripts/watch-local-logs.ps1 -Level ERROR
scripts/watch-local-logs.ps1 -ArchiveDate 2026-07-30
scripts/archive-local-logs.ps1
scripts/archive-local-logs.ps1 -IncludeCurrentDay
```

State the four files, 20 MB/five-segment rule, seven completed calendar days, 2 GB cap, startup recovery, and permanent forbidden data.

- [ ] **Step 4: Run the complete verification matrix**

Run fresh:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/local-log-router.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/watch-local-logs.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/archive-local-logs.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1

mvn.cmd -f backend/pom.xml -DskipFrontend=true test

npm.cmd --prefix frontend run test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run build

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/privacy-check.ps1 `
  -Path backend
```

Expected:

- every command exits 0;
- backend reports 0 failures;
- frontend tests/typecheck/build succeed;
- all PowerShell suites print their explicit passed marker;
- no fixture process, file handle, port, staging directory, or temporary ZIP remains.

- [ ] **Step 5: Inspect final repository state**

Run:

```powershell
git status --short
git diff --check
git log -10 --oneline
```

Expected: only known pre-existing user changes remain unstaged; implementation files are committed in Chinese responsibility-based commits; `/logs/` is ignored.

- [ ] **Step 6: Commit**

```powershell
git add scripts/privacy-check.ps1 `
  scripts/privacy-check.test.ps1 `
  README.md `
  docs/08-当前实现状态.md `
  docs/11-项目演进日志.md
git commit -m "文档测试：补全本地日志运维与隐私门禁"
```
