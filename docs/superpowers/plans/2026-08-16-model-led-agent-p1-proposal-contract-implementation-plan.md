# 模型主导 Agent P1 提议合同 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立严格、不可变且厂商无关的 `TurnInterpretationPort`、`TurnProposal` 与 JSON Codec，但不接入当前 `DefaultTurnRouter` 生产主链。

**Architecture:** 新合同位于 `answer.routing` 的 domain/gateway/adapter 边界。模型只可提交闭集 proposal 和 `TextAnchor(verbatimText, occurrence)`；Codec 以 Jackson 的未知字段失败关闭，领域构造器再验证互斥字段、长度、数量与锚点。P2 才把合法提议编译为现有语义计划。

**Tech Stack:** Java 21、Spring Boot、Jackson、JUnit 5、AssertJ、Maven。

## Global Constraints

- 不接入或修改 `DefaultTurnRouter`、执行器、Provider 配置、stp-v1/v2 HTTP DTO 或前端。
- Java 生产/测试代码禁止 `var`、`record`、Lombok；所有合同对象显式不可变。
- 模型输入/输出不得包含 Evidence、Claim、工具、Provider、计划 ID、令牌、路径、私有数据或自由执行状态。
- JSON 未知字段、未知枚举、重复键、空/超长锚点、任务数量越界和 proposal-kind 字段混用全部 fail-closed。
- 严格 TDD；所有异常响应映射为既有 `ConversationModelFailureCode.INVALID_RESPONSE`，且错误对象不保留原始 Provider payload。

---

## File Structure

- `backend/src/main/java/com/portfolio/agent/answer/routing/domain/TextAnchor.java`：原文锚点及 Java UTF-16 span 解析。
- `backend/src/main/java/com/portfolio/agent/answer/routing/domain/TurnProposal.java`：proposal kind、任务、主体候选、依赖、澄清与 conversation act 的不可变闭集。
- `backend/src/main/java/com/portfolio/agent/answer/routing/gateway/TurnInterpretationPort.java`：厂商无关的输入和成功/失败结果边界。
- `backend/src/main/java/com/portfolio/agent/answer/routing/adapter/model/TurnProposalCodec.java`：严格 Wire JSON → `TurnProposal` 解码。
- 对应 `backend/src/test/...`：锚点、领域不变量、Codec hostile JSON 与端口值对象测试。

## Task 1: TextAnchor 与内部 TextSpan

**Files:**
- Create: `backend/src/test/java/com/portfolio/agent/answer/routing/domain/TextAnchorTest.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/routing/domain/TextAnchor.java`

**Interfaces:**
- Produces: `new TextAnchor(String verbatimText, int occurrence)` and `TextAnchor.resolveIn(String currentInput): TextSpan`.

- [x] **Step 1: Write failing anchor tests**

```java
assertThat(new TextAnchor("😀", 2).resolveIn("😀 x 😀").getStartInclusive()).isEqualTo(5);
assertThatThrownBy(() -> new TextAnchor("missing", 1).resolveIn("input"))
        .isInstanceOf(IllegalArgumentException.class);
```

- [x] **Step 2: Run RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=TextAnchorTest test`

- [x] **Step 3: Implement immutable anchor/span types**

Scan left-to-right with `String.indexOf`, advancing from the last matched end so occurrences are non-overlapping. Reject blank text, `occurrence < 1`, verbatim text above 256 UTF-16 code units, missing occurrence and input above the approved turn budget. `TextSpan` exposes only start/end/text and validates the original exact substring.

- [x] **Step 4: Run GREEN**

Run: `mvn.cmd -f backend/pom.xml -Dtest=TextAnchorTest test`

## Task 2: 不可变提议领域与端口

**Files:**
- Create: `backend/src/test/java/com/portfolio/agent/answer/routing/domain/TurnProposalTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/routing/gateway/TurnInterpretationPortTest.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/routing/domain/TurnProposal.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/routing/gateway/TurnInterpretationPort.java`

**Interfaces:**
- Produces: `PROPOSE_EXECUTION`, `ASK_CLARIFICATION`, `CONVERSE`; task count 1–6 only for execution; proposal fields mutually exclusive.

- [x] **Step 1: Write failing domain tests**

```java
assertThatThrownBy(() -> TurnProposal.execution(List.of(), List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
assertThatThrownBy(() -> TurnProposal.converse("SOCIAL_ACKNOWLEDGEMENT", List.of("preset-a"),
        List.of(new TurnProposal.TaskProposal(...))))
        .isInstanceOf(IllegalArgumentException.class);
```

- [x] **Step 2: Run RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=TurnProposalTest,TurnInterpretationPortTest test`

- [x] **Step 3: Implement the minimal closed model**

Use nested explicit immutable classes for task proposal, subject candidate, dependency, clarification and conversation action. Restrict all free text to documented budgets; task keys must be local `[a-z][a-z0-9-]{0,31}`; no class has fields named evidence, provider, tool, taskId, planId or token. `TurnInterpretationInput` contains only current input, approved public subject references, bounded structured context metadata and allowed enum sets.

- [x] **Step 4: Run GREEN**

Run: `mvn.cmd -f backend/pom.xml -Dtest=TurnProposalTest,TurnInterpretationPortTest test`

## Task 3: 严格 JSON Codec

**Files:**
- Create: `backend/src/test/java/com/portfolio/agent/answer/routing/adapter/model/TurnProposalCodecTest.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/routing/adapter/model/TurnProposalCodec.java`

**Interfaces:**
- Consumes: provider JSON plus `TurnInterpretationInput`.
- Produces: successful immutable `TurnProposal` or `INVALID_RESPONSE` with no payload echo.

- [x] **Step 1: Write failing hostile-input tests**

Cover valid one-task proposal, unknown root field, duplicate `clientTaskKey`, unknown enum, tasks mixed with clarification, invented subject, anchor outside current input and a payload containing `evidenceIds`.

- [x] **Step 2: Run RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=TurnProposalCodecTest test`

- [x] **Step 3: Implement from the existing strict codec pattern**

Copy `ObjectMapper` before enabling `FAIL_ON_UNKNOWN_PROPERTIES`; use `@JsonIgnoreProperties(ignoreUnknown = false)` wire-only classes, explicit presence flags for mutually exclusive optional fields, and a duplicate-key `JsonFactory` configuration. Resolve every anchor against `input.getCurrentInput()` before returning a proposal; independently re-bind every subject to the supplied public catalog.

- [x] **Step 4: Run GREEN**

Run: `mvn.cmd -f backend/pom.xml -Dtest=TurnProposalCodecTest test`

## Task 4: Regression and documentation

**Files:**
- Modify: `docs/00-文档状态索引.md`
- Modify after real behavior change only: `docs/08-当前实现状态.md`, `docs/11-项目演进日志.md`

- [x] **Step 1: Verify contract isolation**

Run full backend tests plus `scripts/code-quality-check.ps1 -Path backend`, `scripts/architecture-check.ps1 -Path backend/src/main/java`, and `scripts/privacy-check.ps1 -Path backend`.

- [x] **Step 2: Register actual state**

Index P1 as implemented-but-unwired only after Tasks 1–3 pass. Do not update current implementation/evolution logs until a public runtime behavior changes.

## Plan Self-Review

- P1 produces contracts only; it cannot alter current routing or start a real Provider call.
- All wire data is revalidated against the current input and public catalog before it becomes a domain value.
- P2 alone may compile these proposals into executable work; no task here grants model execution authority.
