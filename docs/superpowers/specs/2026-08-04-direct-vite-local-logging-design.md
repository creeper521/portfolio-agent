# Direct Vite Local Logging Design
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

**Date:** 2026-08-04
**Status:** Implemented and verified locally

## 1. Problem

The repository currently has two local frontend startup paths with different behavior:

- `scripts/start-local.ps1` launches Vite on port `5174` and routes its stdout/stderr into
  `logs/current/frontend-info.log` and `frontend-error.log`.
- `npm.cmd --prefix frontend run dev` uses Vite's default port `5173`, but writes only to the
  terminal because it bypasses `LocalLogRouter`.

This makes the documented local workflow inconsistent and makes missing frontend log files look
like a logging failure. Browser diagnostic events are a separate stream: they are posted to the
backend's closed `/api/v1/client-diagnostics` contract and must not be written directly by an
untrusted browser or a second ad-hoc file writer.

## 2. Goals

1. Use `5173` as the single default local frontend port.
2. Make `npm run dev` create and maintain the existing frontend log files.
3. Reuse the existing sanitization, queueing, rotation, retention, and source classification.
4. Keep Vite output visible in the invoking terminal.
5. Preserve the backend as the only admission and privacy boundary for browser diagnostics.
6. Make the browser diagnostic route work in the supported local workflow.

## 3. Non-goals

- Do not record questions, answers, clicks, browsing, scrolling, dwell time, URLs, headers,
  request/response bodies, complete stacks, credentials, or arbitrary metadata.
- Do not add a browser-accessible filesystem API.
- Do not introduce another logging library or a second frontend log format.
- Do not change production's stdout-first logging strategy.
- Do not make the frontend development server start or own the backend process.

## 4. Selected Architecture

### 4.1 Port contract

`scripts/start-local.ps1` defaults `FrontendPort` to `5173`. Both the unified launcher and direct
frontend startup pass Vite `--strictPort`, so an occupied port fails explicitly instead of silently
moving to another address.

Callers may still override the unified launcher's `-FrontendPort`. Direct `npm run dev` uses `5173`
unless the developer passes Vite arguments after `--`.

### 4.2 Direct frontend startup

The frontend `dev` package script invokes a small PowerShell entry point owned by the repository.
That entry point:

1. resolves the repository and log directories with the same safety rules as the unified launcher;
2. creates one `LocalLogRouter` instance;
3. starts Vite as an owned child process;
4. mirrors sanitized Vite stdout/stderr to the terminal;
5. submits the same lines as `VITE_STDOUT` and `VITE_STDERR` to `LocalLogRouter`;
6. flushes and stops the router when Vite exits or the user presses `Ctrl+C`;
7. returns Vite's exit code.

The script must not load model secrets, start Spring Boot, enable browser diagnostics, or write
backend log files.

### 4.3 Browser diagnostics

Browser events continue through this path:

```text
Vue application
  -> DiagnosticTransport
  -> POST /api/v1/client-diagnostics
  -> validation, body limit, admission rate limit, privacy whitelist
  -> SLF4J diagnostic event with event.origin=browser
  -> LocalLogRouter BROWSER classification
  -> frontend-info.log or frontend-error.log
```

The supported local launcher must bind its diagnostic enable switch to the actual
`portfolio.diagnostics.frontend-ingest-enabled` property. Logback's console format must preserve
the approved key-value pairs needed by `LocalLogRouter`, including `event.origin=browser`.
Backend appenders must exclude browser-origin events if the same event is routed to a frontend
file, preserving one semantic destination per event.

Direct `npm run dev` does not automatically enable the backend endpoint. Browser events are
captured only when a separately running backend has explicitly enabled local ingestion. Vite
process logs remain available regardless of backend availability.

## 5. File and Level Routing

| Source | Level | Destination |
|---|---|---|
| Vite stdout | DEBUG, INFO, WARN | `frontend-info.log` |
| Vite stderr or explicit ERROR | ERROR | `frontend-error.log` |
| Browser approved event | DEBUG, INFO, WARN | `frontend-info.log` |
| Browser approved event | ERROR | `frontend-error.log` |
| Spring backend event | DEBUG, INFO, WARN | `backend-info.log` |
| Spring backend event | ERROR | `backend-error.log` |

Vite lines retain the existing protection rules: remove ANSI/control characters, replace repository
and user-home paths, strip URL query/fragment data, redact credential-shaped lines, and cap each line
at 8 KiB.

## 6. Failure Behavior

- Unsafe or unresolved log layout: keep Vite usable in console-only mode and print one safe
  `LOG_LAYOUT_UNRESOLVED reason=<safe-code>` diagnostic.
- Log router initialization or writer failure: keep Vite running in console-only mode and return a
  safe degradation code; never expose the underlying path or exception text.
- Port occupied: Vite exits non-zero without selecting another port.
- Backend absent or browser ingestion disabled: browser uploads remain best-effort and do not affect
  the page; Vite process logs continue normally.
- Frontend log queue pressure: preserve WARN/ERROR in preference to DEBUG/INFO and report aggregate
  dropped counts in archive metadata.

## 7. Testing

Implementation follows test-first development.

1. A startup-script contract test fails until the unified default is `5173` and `--strictPort` is
   present.
2. A direct-dev fixture test fails until the `npm run dev` path creates both frontend activity files,
   routes stdout/stderr correctly, mirrors output, and propagates the child exit code.
3. Existing router tests continue to verify sanitization, rotation, backpressure, and single-writer
   ownership.
4. A real formatting integration test verifies that an approved browser diagnostic retains
   `event.origin=browser` in the console line consumed by the router.
5. A local configuration test verifies that the launcher enable switch activates
   `portfolio.diagnostics.frontend-ingest-enabled`.
6. Privacy checks verify that neither Vite nor browser fixtures leak questions, answers, credentials,
   paths, URL parameters, or complete exception text.

## 8. Documentation Impact

Update the README local-development section, current implementation status, the authoritative local
startup design, and the project evolution log. Historical implementation plans remain historical;
they may receive a short supersession note but are not rewritten as if they originally used `5173`.

## 9. Acceptance Criteria

- `npm.cmd --prefix frontend run dev` serves on `http://127.0.0.1:5173` by default.
- `scripts/start-local.ps1` also uses `5173` by default.
- Both paths fail clearly when their selected port is occupied.
- Direct frontend startup creates `frontend-info.log` and `frontend-error.log` and keeps terminal
  output visible.
- Vite proxy failures appear in `frontend-error.log` without sensitive URL details.
- With an enabled local backend, every approved browser event reaches exactly one frontend log and
  does not enter a backend activity log.
- Backend absence never prevents Vite log creation.
- Relevant PowerShell, backend, frontend, and privacy tests pass.
