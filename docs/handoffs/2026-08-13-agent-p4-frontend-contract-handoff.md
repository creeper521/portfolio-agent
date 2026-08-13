# Agent P4 前端契约与交互交接

> 日期：2026-08-13
> 状态：待前端 Agent 实施
> 权威后端设计：`../superpowers/specs/2026-08-13-model-grounded-answer-design.md`
> 范围：P4 公共 DTO、状态映射、呈现原则和前端验收；不规定视觉实现细节

## 1. 目标

P4 为已经通过 P3 证据校验的 Portfolio 回答增加可选模型表达。无论最终内容来自确定性 Composer、通过校验的模型 Draft，还是模型失败后的确定性 fallback，前端都使用同一套章节、推荐与 Evidence Desk 体验。

前端不负责：

- 判断是否调用模型；
- 校验模型事实；
- 推导 fallback；
- 改写正文或引用；
- 根据模型状态改变证据可信度；
- 重建 Provider、Validator 或执行阶段。

## 2. 公共契约变化

### 2.1 Enum

```text
GenerationMode
  DETERMINISTIC | MODEL | FALLBACK | MIXED

AnswerConstructionMode
  TEMPLATE | EVIDENCE_COMPOSITION | MODEL_GROUNDED
  | GENERAL_MODEL | MIXED_COMPOSITION
```

### 2.2 Task composition

每个可展示 completed task 可增加：

```json
{
  "composition": {
    "mode": "DETERMINISTIC | MODEL_GROUNDED | FALLBACK",
    "degraded": false
  }
}
```

该字段用于协议状态和测试，不要求在访客主界面显示。

### 2.3 顶层状态

- 全部确定性：`DETERMINISTIC + EVIDENCE_COMPOSITION`。
- 全部模型通过：`MODEL + MODEL_GROUNDED`。
- 发生 fallback：单一模式可为 `FALLBACK + EVIDENCE_COMPOSITION`。
- 多任务表达模式不同：`MIXED + MIXED_COMPOSITION`。
- `degraded=true` 可能来自检索 fallback、表达 fallback 或既有降级；前端不能据此猜具体原因。

## 3. 显示规则

必须：

- 三种 task composition 使用同一 Answer Section、Recommendation Item 和 Evidence Desk。
- 保持 `sourceReferences` 为 P3/P4 权威引用；不得根据 compositionMode 过滤或重排。
- Fallback 仍是成功回答，不显示错误 Toast、重试按钮或警告卡。
- `MIXED/MIXED_COMPOSITION` 必须被类型层和 mapper 接受，不丢正文。
- 旧响应缺少 task composition 时继续安全映射。
- 继续使用后端提供的 sectionType/title/content，不在前端重新分章。

禁止：

- “AI 增强”“由某模型生成”或 Provider 徽标；
- 显示模型失败原因、breaker 状态或 Validator code；
- 增加“正在调用模型”“正在验证模型”等拟真进度；
- 因 `MODEL_GROUNDED` 隐藏引用或降低证据展示；
- 因 `FALLBACK` 把成功回答渲染成失败状态；
- 在诊断中记录正文、source reference key、ResumeToken 或 ContextHandle。

## 4. Execution Snapshot

继续使用 P3 四阶段：

```text
SCOPE_CONFIRMED
MATERIALS_RETRIEVED
EVIDENCE_VALIDATED
RESULT_COMPOSED
```

P4 的 Provider、Draft 与 Validator 是 `RESULT_COMPOSED` 的后端内部实现。最终响应不新增 stage；同步等待继续使用现有固定骨架。

## 5. 推荐前端改动位置

- `frontend/src/features/agent/model/answerTypes.ts`
  - 扩展 `GenerationMode` 和 `AnswerConstructionMode`。
  - 增加 task composition 类型。
- `frontend/src/features/agent/model/semanticTurnView.ts`
  - 严格映射闭集 composition；非法值脱敏诊断并忽略该 metadata，不丢可信正文。
- `frontend/src/features/agent/model/mapAnswerResponse.ts`
  - 接受顶层 MIXED；不改变 section/source reference 映射。
- 对应 mapper、ConversationThread、EvidenceDesk、ExecutionSnapshot、AgentWorkspace 测试。
- E2E Mock 增加 MODEL_GROUNDED、FALLBACK、MIXED 三类响应。

具体组件拆分、样式、动效与视觉由前端 Agent 决定，但不得改变本文公共语义。

## 6. 必测场景

1. 单任务 MODEL_GROUNDED：章节与引用正常。
2. 单任务 FALLBACK：无错误 UI，正文与 Evidence 正常。
3. 多任务 MIXED：每个 task payload 均展示，顶层状态不触发未知 enum。
4. task composition 缺省：兼容既有成功响应。
5. task composition 非法：忽略 metadata、上报脱敏诊断、保留可信正文。
6. MODEL_GROUNDED 中 sourceReferences 非法：沿用 P3 引用校验和降级规则。
7. 桌面和移动：无溢出、来源标签不重复、Evidence Desk 可打开。
8. ExecutionSnapshot：仍只有四个阶段，不出现模型伪进度。
9. Context 恢复、Recommendation、Plan Confirmation 和 Clarification 无回归。

## 7. 完成条件

- TypeScript、单元测试、Lint、构建通过。
- 目标桌面/移动 E2E 通过。
- 不新增模型品牌或技术状态 UI。
- 不修改后端业务决策。
- 前端状态文档准确区分公共 metadata 与用户可见交互。
