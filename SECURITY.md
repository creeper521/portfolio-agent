# Security Policy

## Public data boundary

The deployed application reads only the reviewed public snapshot packaged with the backend. The private knowledge base, raw daily reports, candidate snapshots, internal screenshots, credentials, and privacy reports must never be included in runtime artifacts.

## Reporting a problem

Do not open a public issue containing private internship information. Report suspected data exposure directly to the repository owner through a private channel.

## Required checks

- Evidence must be marked `APPROVED` before it can be returned.
- Raw evidence is not publicly accessible in V0.
- Public content and packaged artifacts must pass `scripts/privacy-check.ps1`.
- API errors must not include stack traces, local paths, internal hosts, or credentials.

