# Semantic Turn Routing 前端原型背景 Brief

> 日期：2026-08-10
> 状态：交互验证材料，不是正式 Spec，不授权生产实现
> 对应阶段：Agent 对话体验与智能编排路线图阶段二

## 1. 原型目的

验证真正多任务 Agent 在现有 Agent 工作台中的用户交互，而不是重新设计整个作品集网站。原型结论将用于修订 `SemanticTurnPlan`、确认协议、澄清协议和任务结果摘要，之后才形成阶段二正式 Spec。

本轮必须验证四个问题：

1. 复杂计划怎样展示，用户能否快速理解并确认；
2. 主体或依赖不明确时，怎样一次只澄清一个决策主题；
3. 多任务部分成功时，正文和任务状态怎样同时保持清晰；
4. 简单任务、多任务自动执行和复杂计划确认怎样使用一致但不过度打扰的交互。

## 2. 已批准的产品决策

- 每轮生成一张独立 `SemanticTurnPlan`；整个会话可以有多张计划，但不维护无限增长的全会话任务图。
- 跨轮只使用受控结构化结果引用，不从历史回答正文推断主体。
- `SemanticTurnPlan` 可以同时包含 `PORTFOLIO`、`GENERAL` 和 `SYNTHESIS` 来源域。
- 真正支持多任务及依赖图；语义任务只表示用户可感知目标，不表示检索、校验或工具调用。
- 简单计划直接执行；复杂、高成本、存在冲突或明显扩大范围的计划需要确认。
- 任务局部歧义时，独立且安全的任务可以继续；上游关键歧义先澄清。
- 多任务允许部分成功，必须明确展示失败、阻塞和降级原因。
- 单任务成功默认隐藏任务摘要；多任务成功显示紧凑可折叠摘要；确认、部分成功和失败状态必须可见。
- 确认执行用户看到的原计划；计划失效时明确重新生成，不静默重路由。
- 用户修改 pending plan 会生成新计划；取消只清除当前标签页内存。
- 不展示模型思维过程、Prompt、内部工具参数、检索评分、异常堆栈或安全规则细节。

## 3. 任务类型

```text
PORTFOLIO_FACT
PORTFOLIO_COMPARE
PORTFOLIO_RECOMMEND
PORTFOLIO_REFINE_RECOMMENDATION
GENERAL_EXPLANATION
GENERAL_COMPARISON
SYNTHESIS
```

`CLARIFICATION`、安全边界、检索和工具调用不是语义任务。

依赖类型：

```text
REQUIRES_SUCCESS
USES_AVAILABLE_RESULTS
ORDER_AFTER
```

## 4. 原型必须覆盖的状态

### 状态 A：简单单任务直接回答

问题：`介绍 SQL 审计项目的实现方案。`

- 不展示多余计划卡或步骤列表；
- 保持阶段一已有语义章节、引用和 Evidence Desk。

### 状态 B：多任务自动执行

问题：`介绍 SQL 审计和 ABTest 项目，再比较它们。`

- 三个明确任务直接执行；
- 回答正文保持连贯；
- 显示紧凑、可折叠的任务摘要；
- 展示顺序不随并行完成顺序变化。

### 状态 C：复杂计划等待确认

问题：`分别介绍 SQL 审计和 ABTest，比较它们，再根据比较推荐一个适合后端面试展示的，并总结展示策略。`

```text
1. 介绍 SQL 审计
2. 介绍 ABTest
3. 比较两个项目
4. 推荐一个项目
5. 总结面试展示策略
```

- 显示用户目标和必要依赖，不显示检索步骤；
- 提供“按此计划继续”“调整计划”“取消”；
- 显示约束：后端、面试展示、推荐一个；
- 用户调整后生成新计划，不原地静默修改。

### 状态 D：局部澄清

问题：`介绍 SQL 审计，再把它和另一个项目比较。`

- SQL 审计介绍可以继续；
- 比较任务等待选择另一个项目；
- 一次只询问第二个主体；
- 选项来自当前公开主体目录。

### 状态 E：关键依赖澄清

问题：`比较这两个项目，再推荐一个。`，但没有合法的“这两个”引用。

- 不执行比较或推荐；
- 先询问两个主体；
- 推荐显示为被上游歧义阻塞；
- 不从历史正文猜测主体。

### 状态 F：部分成功

```text
介绍 A：成功
介绍 B：公开证据不足
比较 A/B：依赖阻塞
推荐：依赖阻塞
通用技术解释：成功
```

- 返回 A 和通用解释的安全正文；
- 正文与任务状态摘要分离；
- 不为 B、比较或推荐生成伪正文；
- 作品集事实与通用知识来源可区分。

### 状态 G：计划失效

- 明确说明原计划需要重新生成；
- 提供“重新生成计划”和“取消”；
- 不静默执行新计划；
- 不展示技术异常文案。

### 状态 H：全局安全边界

- 整轮终止；
- 不展示部分计划；
- 不展示内部安全规则；
- 使用现有克制的安全边界表达。

## 5. 原型建议视图

```text
Plan Confirmation View
Clarification View
Compact Task Summary
Task Status Summary
Plan Invalidated Notice
```

这些视图嵌入当前 Conversation Thread，并继续与 Evidence Desk、响应式抽屉和已有回答章节协作。

## 6. Mock 响应契约

字段命名仍可根据原型反馈修订。

```json
{
  "turnId": "turn-42",
  "contentVersion": "2026-08-10.1",
  "resolution": "AWAITING_CONFIRMATION",
  "blocks": [],
  "agentTurn": {
    "disposition": "CONFIRMATION_REQUIRED",
    "semanticPlan": {
      "planId": "plan-42",
      "contentVersion": "2026-08-10.1",
      "source": "MIXED_VALIDATED",
      "tasks": [
        { "taskId": "task-01", "taskType": "PORTFOLIO_FACT", "sourceDomain": "PORTFOLIO", "label": "介绍 SQL 审计项目" },
        { "taskId": "task-02", "taskType": "PORTFOLIO_FACT", "sourceDomain": "PORTFOLIO", "label": "介绍 ABTest 项目" },
        { "taskId": "task-03", "taskType": "PORTFOLIO_COMPARE", "sourceDomain": "PORTFOLIO", "label": "比较两个项目" },
        { "taskId": "task-04", "taskType": "PORTFOLIO_RECOMMEND", "sourceDomain": "PORTFOLIO", "label": "推荐一个适合后端面试展示的项目" },
        { "taskId": "task-05", "taskType": "SYNTHESIS", "sourceDomain": "SYNTHESIS", "label": "总结面试展示策略" }
      ],
      "dependencies": [
        { "upstreamTaskId": "task-01", "downstreamTaskId": "task-03", "dependencyType": "REQUIRES_SUCCESS" },
        { "upstreamTaskId": "task-02", "downstreamTaskId": "task-03", "dependencyType": "REQUIRES_SUCCESS" },
        { "upstreamTaskId": "task-03", "downstreamTaskId": "task-04", "dependencyType": "REQUIRES_SUCCESS" },
        { "upstreamTaskId": "task-04", "downstreamTaskId": "task-05", "dependencyType": "USES_AVAILABLE_RESULTS" }
      ],
      "constraints": ["后端", "面试展示", "推荐 1 个"]
    },
    "planConfirmation": {
      "integrityToken": "opaque-prototype-token",
      "expiresAt": "2026-08-10T13:00:00+08:00"
    }
  }
}
```

```json
{
  "agentTurn": {
    "disposition": "READY",
    "taskSummary": {
      "outcome": "PARTIAL",
      "tasks": [
        { "taskId": "task-01", "label": "介绍 A", "executionStatus": "SUCCEEDED", "resolution": "ANSWERED" },
        { "taskId": "task-02", "label": "介绍 B", "executionStatus": "SUCCEEDED", "resolution": "NOT_SUPPORTED", "reasonCode": "INSUFFICIENT_APPROVED_EVIDENCE" },
        { "taskId": "task-03", "label": "比较 A 与 B", "executionStatus": "BLOCKED", "reasonCode": "DEPENDENCY_NOT_SATISFIED" },
        { "taskId": "task-04", "label": "推荐一个项目", "executionStatus": "BLOCKED", "reasonCode": "DEPENDENCY_NOT_SATISFIED" },
        { "taskId": "task-05", "label": "解释通用优缺点", "executionStatus": "SUCCEEDED", "resolution": "ANSWERED" }
      ]
    }
  }
}
```

## 7. 原型交互约束

- 使用本地 Mock 状态，不接真实后端；
- 不改变全站导航和 Agent 工作台总体布局；
- 不把任务图画成开发者 DAG 编辑器；
- 不允许直接编辑 JSON、任务 ID、依赖类型或完整性令牌；
- “调整计划”通过自然语言或受控选项产生新计划；
- 不展示模型推理、工具参数、检索评分或异常堆栈；
- 桌面与移动端均需验证，无横向溢出；
- 支持键盘、焦点状态与 reduced motion。

## 8. 视觉基线

保持当前 Agent 工作台的暖黑舞台、米色内容窗口、Conversation Thread、Evidence Desk 和抽屉行为。重点是信息架构与状态表达，不进行全站视觉改版。

正式参考：

- `design/demos/作品推荐-V1保守-对话内嵌.html`

非权威探索参考：

- `design-exploration/agent-clarification-comparison/clarification-comparison.html`
- `design-exploration/agent-clarification-comparison/comparison-component.html`

## 9. 原型验收

- 用户能在 10 秒内理解复杂计划；
- 用户能区分确认和澄清；
- 用户能看出哪个任务失败以及为何阻塞下游；
- 单任务体验没有被计划 UI 打扰；
- 多任务摘要不取代正常回答正文；
- 不展示内部推理、工具或安全细节；
- 作品集事实和通用知识来源不会混淆；
- 移动端可以完成确认、调整、取消和澄清；
- 能据此决定任务上限、确认阈值和状态摘要密度。
