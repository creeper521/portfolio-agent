# Agent 行为 P0 后端基线冻结 Implementation Plan
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 P-1 修复后的确定性后端行为固定为 HTTP 回归基线，同时将第四版未来目标与当前行为严格分开记录。

**Architecture:** 沿用治理目录的单一行为语料和前端 Oracle，不在后端复制浏览器状态机。后端只新增真实 `/api/v2/answers` 边界回归；路由与 Mapper 单测保持领域级原因定位。未来 `CONVERSATIONAL`、stp-v3、RecentResultSet 与 Provider 主导路线仅作为目标行为，不能在 P0 写成当前实现。

**Tech Stack:** Java 21、Spring Boot、MockMvc、JUnit 5、AssertJ、Maven。

## Global Constraints

- 生产与测试 Java 不使用 `var`、`record` 或 Lombok；不引入新的 Provider、模型调用、存储或前端依赖。
- API 只输出公开 DTO；问题、答案、Evidence 原文和凭据不得写入日志或持久化。
- P0 只冻结后端可复现的现状；不改变 P4 以后设计、默认模式、公共事实或 stp-v2 合同。
- 全部新增行为断言使用真实公开快照和 HTTP 响应；不把未来目标断言为当前应通过的测试。
- 不触碰 `frontend/`，不 stage、commit 或 push。

---

## File Structure

- `governance/portfolio-governance/evaluation/cases/holdout/behavior-routing.v1.json`：唯一治理行为语料，已包含跨层目标；不重复为后端测试数据源。
- `backend/src/test/java/com/portfolio/agent/answer/routing/service/DefaultTurnRouterDeterministicTest.java`：纯领域路由不变量。
- `backend/src/test/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapperTest.java`：最终 DTO 的来源脱敏不变量。
- `backend/src/test/java/com/portfolio/agent/answer/controller/NoiseConversationIntegrationTest.java`：真实 HTTP 噪声输入的 P0 基线。
- `docs/00-文档状态索引.md`：登记 P0 计划及其实现状态。

## Task 1: 固定纯噪声的 HTTP 安全基线

**Files:**
- Create: `backend/src/test/java/com/portfolio/agent/answer/controller/NoiseConversationIntegrationTest.java`

**Interfaces:**
- Consumes: `POST /api/v2/answers`、当前审核公开快照及默认关闭的模型能力。
- Produces: `112233` 的 HTTP 200、`resolution=NEEDS_CLARIFICATION`、`evidenceState=NOT_REQUIRED`、空 blocks 和不存在的 stp-v1 public-source catalog。

- [ ] **Step 1: Write the HTTP regression test**

```java
mockMvc.perform(post("/api/v2/answers").contentType(MediaType.APPLICATION_JSON)
        .content(noiseRequest("112233")))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.resolution").value("NEEDS_CLARIFICATION"))
    .andExpect(jsonPath("$.evidenceState").value("NOT_REQUIRED"))
    .andExpect(jsonPath("$.blocks").isEmpty())
    .andExpect(jsonPath("$.publicSourceCatalog").doesNotExist());
```

- [ ] **Step 2: Run it against the production Spring composition**

Run: `mvn.cmd -f backend/pom.xml -Dtest=NoiseConversationIntegrationTest test`

Expected: PASS only when the request reaches the live router and Mapper with no Evidence or source projection. If the local JBR requires Byte Buddy agent injection, record that as a test-runtime requirement rather than changing production configuration.

## Task 2: 保持“现状”和“未来目标”分离

**Files:**
- Modify: `docs/00-文档状态索引.md`

**Interfaces:**
- Consumes: P-1 implementation plan, fourth-version orchestration plan and Task 1 evidence.
- Produces: P0 listed as an implemented test baseline; P1+ remains unauthorized until separately approved.

- [ ] **Step 1: Register the plan**

Add an index row stating that P0 freezes only the deterministic HTTP/routing/source boundary. It must explicitly state that `CONVERSATIONAL`, Provider-led routing, stp-v3, bare-pronoun binding and Project-only recommendation are future targets, not current behavior claims.

- [ ] **Step 2: Verify documentation and source scope**

Run:

```powershell
git diff --check
powershell -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1 -Path backend
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend
```

Expected: no whitespace error, no disallowed Java construction and no privacy scan finding.

## Plan Self-Review

- P0 coverage is intentionally narrow: browser context, Playwright drivers and UI fixture ownership remain with the frontend handoff; no duplicate Oracle is introduced.
- Future behavior is not asserted as current PASS, avoiding a false baseline that would block the planned Provider-led transition.
- The single new test executes the real server composition rather than mocking router or mapper internals.
