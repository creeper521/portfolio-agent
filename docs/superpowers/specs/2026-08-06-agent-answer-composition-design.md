# Agent 回答结构化编排设计

> **日期：** 2026-08-06
> **状态：** 待用户审阅
> **对应路线图：** `docs/13-Agent对话体验与智能编排改造路线图.md` 阶段 1
> **目标入口：** `POST /api/v2/answers` 的作品集回答

## 1. 结论

本阶段在 `PortfolioIntelligenceResult` 与最终 HTTP 回答之间加入确定性的语义编排模块。检索 Passage 不再一对一成为前端 Block，而是先形成 `PortfolioAnswerPlan`，再按背景、职责、方案、验证、状态和边界组织为少量完整章节。

前端继续消费 `/api/v2/answers`，但以增强后的 v2 Block 作为唯一生产传输契约：每个 Block 增加章节类型和标题，正文只展示一次来源语义，引用按章节聚合。现有 legacy `sections` 仅在迁移期间由兼容映射读取，不恢复为第二套后端主契约。

本阶段不调用大模型，不改变意图路由，不接入新的工具循环。其产物同时成为后续 `MODEL_GROUNDED` 的确定性 fallback。

## 2. 问题定义

当前 `PortfolioIntelligenceAnswerAssembler.materialBlocks()` 对 `PortfolioIntelligenceResult.getEvidence()` 执行逐 Passage 映射：

```text
PortfolioRetrievedPassage
→ ConversationAnswerBlock
→ 前端逐 Block 展示“作品集资料”与 Evidence 按钮
```

该实现保留了 Claim/Evidence 身份，却没有回答以下表达问题：

- 哪些材料共同回答“背景”；
- 哪些材料属于“我的职责”；
- 多条实现事实应按什么顺序组织；
- 验证事实和当前状态如何与实现区分；
- 重复项、相同引用和重复项目前缀如何合并；
- 用户首先应该看到结论还是材料列表。

因此截图中的长串短句不是单纯的前端样式缺陷，而是后端缺少回答计划。

## 3. 已比较方案

### 3.1 只在前端合并相邻 Block

前端可以按相同 `sourceScope` 合并段落，改动较小。但前端不知道 Claim 分类、任务模式、证据优先级和项目状态，只能做字符串拼接。语义规则会分散到展示层，后续模型表达也无法复用。

**结论：不采用。** 前端只负责呈现，不拥有事实编排决策。

### 3.2 把所有检索材料直接交给模型总结

模型能够改善自然语言，但会引入默认外部依赖、延迟、成本、引用漂移和降级问题。全量材料还会增加重复与越界表达风险。

**结论：不作为第一阶段。** 后续模型只能消费经过筛选的 `EvidenceBundle`，并且必须通过 Grounding Validator。

### 3.3 后端确定性 `AnswerPlan` + 结构化前端

后端利用任务模式、主体信息和 Claim 语义组织章节；Composer 只使用受控模板、连接词和原始公开事实；前端按章节呈现并聚合引用。

**结论：采用。** 它在不增加模型依赖的前提下直接改善用户体验，并为未来模型表达建立稳定 seam。

## 4. 范围

### 4.1 本阶段包含

1. 为检索 Passage 补齐编排所需的稳定语义元数据；
2. 定义不可变 `PortfolioAnswerPlan` 和章节值对象；
3. 实现确定性回答 Composer；
4. 让 `PortfolioIntelligenceAnswerAssembler` 消费 Plan，而不是直接遍历 Passage；
5. 为 v2 Block 增加章节类型和章节标题；
6. 前端统一将 v2 Block 映射为章节视图；
7. 按章节聚合 Claim 和 Evidence；
8. 补齐单元、契约、组件、E2E 和评测用例；
9. 保留澄清、错误、推荐和证据不足的现有失败关闭语义。

### 4.2 本阶段不包含

- 不修改 `PortfolioTaskResolver` 的关键词路由；
- 不引入 `TurnRouter` 模型分类；
- 不把 `ConversationToolService` 接入主链路；
- 不实现 `MODEL_GROUNDED` 或 `HYBRID/MIXED`；
- 不默认启用 Provider、ONNX 或 PostgreSQL；
- 不扩大 Bundle、Claim、Evidence 或公开资产范围；
- 不改变访客会话的页面内存和不落盘边界；
- 不做与回答呈现无关的前端大规模拆分。

## 5. 设计原则

1. **事实与表达分离。** Retriever 决定有哪些公开事实，Composer 决定如何组织，前端决定如何展示。
2. **确定性是完整能力。** 模型关闭时不能退化为检索段落清单。
3. **章节是用户语义，不是存储颗粒度。** 一个章节可引用多个 Claim 和 Evidence。
4. **不通过改写制造事实。** Composer 允许裁剪重复前缀、规范标点和增加固定连接词，不新增事实判断。
5. **引用跟随语义章节。** 用户能阅读完整段落，也能定位其 Claim/Evidence。
6. **小接口隐藏复杂度。** 调用方只提交已验证的 Intelligence Result 并接收 Plan；分组、去重、排序和模板是模块内部实现。
7. **失败关闭。** 任何已回答的作品集章节都不能引用不存在或未批准的 Evidence。

## 6. 总体数据流

```mermaid
flowchart LR
    A["PortfolioDecision"] --> B["PortfolioIntelligenceResult"]
    B --> C["DeterministicPortfolioAnswerComposer"]
    C --> D["PortfolioAnswerPlan"]
    D --> E["PortfolioIntelligenceAnswerAssembler"]
    E --> F["ConversationAnswerResult / v2 blocks"]
    F --> G["mapAnswerResponse"]
    G --> H["语义章节视图"]
    H --> I["ConversationThread"]
    H --> J["Evidence Desk"]
```

`PortfolioIntelligence` 仍拥有作品集决策权，Composer 不重新路由、不重新检索，也不判断主体。Assembler 仍拥有 HTTP 结果的状态、来源、模式和版本等外壳元数据。

## 7. 深模块与 seam

### 7.1 检索结果的语义投影

当前 `PortfolioRetrievedPassage` 只有 Passage、Subject、Claim、Content 和 Evidence 身份，缺少稳定的 Claim 分类。第一阶段为该值对象增加必填的 `AnswerClaimCategory`，由 Bundle 和 PostgreSQL Retriever 从同一公开 Claim 投影填充，再由 Composer 映射到 `AnswerSectionType`。

不得由 Composer 对中文正文执行关键词猜测来决定章节。这会把当前意图规则的问题复制到回答编排中。

检索顺序继续表示相关性顺序；Composer 不重新计算检索分数。

### 7.2 `DeterministicPortfolioAnswerComposer`

该模块的外部 interface 保持为一个操作：

```text
compose(PortfolioIntelligenceResult) -> PortfolioAnswerPlan
```

调用方只需要知道：

- 输入必须来自已经通过主体、公开状态和 Evidence 校验的 Intelligence Result；
- 输出只包含来自输入的 Claim/Evidence 身份；
- 相同输入产生相同输出；
- 不调用网络、数据库、Provider 或其他工具；
- 不产生日志中的用户原文。

其 implementation 内部负责：

1. 投影并按 Claim ID 去重；
2. 按章节类型分组；
3. 保留检索相关性顺序；
4. 合并相同正文与重复项目名前缀；
5. 为每个章节选择有限数量的有效事实；
6. 使用固定中文连接词组成段落；
7. 合并并稳定去重 Claim/Evidence ID；
8. 生成标题和可选摘要；
9. 验证 Plan 不变量。

第一阶段只有确定性 implementation，因此不提前增加仅有一个 Adapter 的抽象注册表。未来模型 Composer 出现时，再在 Plan seam 上引入真实双 Adapter interface。

### 7.3 `PortfolioAnswerPlan`

建议采用不可变值对象：

```text
PortfolioAnswerPlan
├── title
├── summary
└── sections[]
    ├── sectionType
    ├── title
    ├── content
    ├── claimIds[]
    └── evidenceIds[]
```

不变量：

1. `title` 非空；
2. `ANSWERED` 结果至少有一个非空章节；
3. 每个章节的类型、标题和正文非空；
4. Claim/Evidence ID 去重并保持第一次出现顺序；
5. 所有 ID 必须来自输入 Intelligence Result；
6. 普通 `ANSWERED + PORTFOLIO` 章节至少有一个 Evidence；
7. 摘要只能来自公开主体摘要或已选事实，不能凭空概括生产效果；
8. Plan 不携带原始私有对象、检索分数或内部路径。

Plan 是内部编排结果，不直接作为新的公共 HTTP DTO。

### 7.4 Assembler

`PortfolioIntelligenceAnswerAssembler` 的职责收窄为：

- 把 `PortfolioDisposition` 映射为 `AnswerResolution`；
- 保留 contentVersion、Contract、Intent、Source、EvidenceState 和 degraded 状态；
- 对需要材料回答的 Disposition 调用 Composer；
- 把 Plan 映射为 v2 Block；
- 对澄清、输入无效、Contract 过期和能力不可用保留专用短响应。

它不再拥有 Passage 遍历、分组、去重或段落组织规则。

## 8. 章节映射

第一阶段复用现有 `AnswerSectionType`，不新增一套平行枚举：

| Claim 语义 | 回答章节 | 默认标题 |
|---|---|---|
| `BACKGROUND` | `BACKGROUND` | 项目背景 |
| `RESPONSIBILITY` | `RESPONSIBILITY` | 我的职责 |
| `TECHNICAL_DECISION`、`IMPLEMENTATION` | `SOLUTION` | 技术方案与实现 |
| `VERIFICATION` | `VERIFICATION` | 验证过程 |
| `OUTCOME` | `STATUS` | 结果与当前状态 |
| `LIMITATION` | `BOUNDARY` | 边界与限制 |
| `LEARNING`、`REFLECTION` | `BOUNDARY` | 边界与复盘 |

同一个章节包含多个语义类别时，标题使用上表中更具体且覆盖全部内容的固定标题。例如 `LIMITATION + LEARNING` 使用“边界与复盘”。

章节顺序固定为：

```text
BACKGROUND
→ RESPONSIBILITY
→ SOLUTION
→ VERIFICATION
→ STATUS
→ BOUNDARY
```

只输出存在有效材料的章节，不为缺失类别生成空标题或“暂无”占位。用户明确询问但公开证据缺少某一部分时，在 `BOUNDARY` 中用受控文案指出缺失，不根据常识补全。

## 9. 确定性表达规则

### 9.1 直接回答

标题使用主体标题或任务标题。摘要优先使用已验证的公开主体摘要；若主体摘要不可用，则使用最相关且允许进入摘要的首条事实。摘要不超过一个自然段。

### 9.2 去重

按以下顺序去重：

1. Claim ID 完全相同；
2. 规范化后正文完全相同；
3. 同一正文的 Evidence 列表不同则合并 Evidence，不重复正文；
4. 仅删除重复的主体标题前缀，不删除事实限定词；
5. 不使用模型或模糊相似度自动合并两个不同 Claim。

### 9.3 组织

每个章节保留检索顺序，以固定连接模板组织：

- 第一条事实直接陈述；
- 后续事实根据章节使用“实现上”“同时”“验证方面”“当前”等有限连接词；
- 统一中文标点，避免每条事实单独占一个视觉 Block；
- 不把“计划”“原型”“观察”改写成“已交付”“已上线”或“长期有效”。

### 9.4 长度预算

预算以章节和已选事实数量控制，不截断 Claim/Evidence ID：

- 每个章节默认选择 1–3 条最相关事实；
- 详细介绍类问题最多输出 6 个章节；
- 单章节超出预算时保留高相关事实，其余材料仍可从证据面板查看；
- 用户通过显式引用追问某章节时，允许该章节使用更高预算；
- 预算常量必须集中在 Composer 内，不散落到 Assembler 和前端。

具体字符阈值在实施计划中通过现有内容样本和组件视口测试确定，不在本设计中凭经验冻结。

## 10. v2 HTTP 契约演进

保留现有 `blocks` 字段，并为每个 Block 增加两个字段：

```json
{
  "sourceScope": "PORTFOLIO",
  "sectionType": "SOLUTION",
  "title": "技术方案与实现",
  "content": "……",
  "claimIds": ["claim-a", "claim-b"],
  "evidenceIds": ["evidence-a", "evidence-b"]
}
```

约束：

- `sourceScope`、`content`、`claimIds`、`evidenceIds` 保持现有语义；
- `sectionType` 和 `title` 对新作品集 `ANSWERED` 回答必填；
- 澄清和错误短响应允许使用 `BOUNDARY` 或不带语义章节的兼容 Block，具体由 DTO 契约测试锁定；
- 顶层可增加可选 `summary`，其来源必须满足 Plan 不变量；
- 不同时向新客户端发送两套内容不同的 `sections` 和 `blocks`。

迁移期读取旧 `sections` 只是前端兼容行为。生产后端以增强后的 v2 `blocks` 为唯一回答正文来源。

## 11. 前端呈现

### 11.1 映射层

`mapAnswerResponse` 将增强后的 v2 Block 映射成前端统一章节视图。视图模型包含：

- 章节类型；
- 标题；
- 正文；
- Claim ID；
- Evidence ID；
- 来源范围。

如果响应来自迁移期旧 fixture，映射层可以读取 legacy `sections`；业务组件不同时判断两套协议。

### 11.2 `ConversationThread`

每个回答按下面的视觉顺序呈现：

1. 回答状态和范围元信息；
2. 回答标题；
3. 可选摘要；
4. 语义章节；
5. 章节引用；
6. 版本变化、推荐卡或后续问题。

不再为每个章节重复显示“作品集资料”。作品集范围已经在回答级元信息中表达。若未来 `HYBRID` 同时出现 `GENERAL` 与 `PORTFOLIO`，才在范围变化处显示来源分区。

### 11.3 引用

- 同一章节内 Evidence ID 稳定去重；
- 同一 Evidence 可被多个章节引用，但 Evidence Desk 只保留一个实体；
- 点击章节引用继续打开现有 Evidence Desk；
- Claim ID 不直接作为访客主视觉标签，但用于引用上下文和诊断；
- 无 Evidence 的作品集事实不能渲染为“已验证”。

## 12. 错误和降级

| 场景 | 行为 |
|---|---|
| Intelligence 要求澄清 | 保留单一、直接的澄清问题，不进入普通 Composer |
| 结构化主体无效 | 保留 `INVALID_INPUT` 与当前失败关闭文案 |
| Preset Contract 过期 | 保留刷新提示，不用旧 Evidence 继续编排 |
| Contract Evidence 不可用 | 返回能力不可用或证据不足，不生成空章节 |
| 单个 Passage 缺少 `AnswerClaimCategory` | Retriever 拒绝构造该 Passage 并进入现有证据不足语义；Composer 不猜分类 |
| Passage 引用不存在 Evidence | 该事实不得进入 Plan；全部事实失效时返回证据不足 |
| Composer 不变量失败 | 返回确定性安全短响应并标记 degraded，不泄露异常细节 |
| 前端收到旧 v2 Block | 兼容映射为无标题章节；不导致整条回答不可见 |

日志只记录 noticeCode、章节数量、Claim/Evidence 数量和失败枚举，不记录访客问题或完整回答正文。

## 13. 测试设计

### 13.1 Composer 单元测试

至少覆盖：

- 单 Passage 生成一个正确章节；
- 多 Passage 按语义合并；
- 同 Claim、同正文和同 Evidence 去重；
- 章节顺序稳定；
- Claim/Evidence 顺序稳定；
- 详细介绍问题形成背景、职责、方案、验证、状态和边界；
- 计划、原型、协作贡献和限制词不被改写；
- 证据缺失时失败关闭；
- 同一输入重复执行得到相同结果；
- 输入集合不能从输出对象被修改。

### 13.2 Assembler 契约测试

- `ANSWERED` 返回增强后的 v2 Block；
- constructionMode 仍为 `EVIDENCE_COMPOSITION`；
- generationMode 仍为 `DETERMINISTIC`；
- contentVersion、Contract、noticeCode 和 reference context 不丢失；
- 澄清、无效输入、不可用和推荐回答保持原有 Resolution；
- 所有输出 Evidence 均属于输入结果。

### 13.3 前端测试

- 同一回答只显示一次作品集范围；
- 章节标题和正文按权威顺序展示；
- 引用在章节内去重；
- Evidence Desk 仍能定位全部证据；
- 键盘和触屏可以打开证据；
- 旧 fixture 经过兼容映射仍可展示；
- 空标题、空正文或非法 Block 被映射层拒绝并产生脱敏诊断。

### 13.4 E2E 与评测

新增截图问题对应的端到端场景：

> 请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？

验收不锁定完整措辞，而锁定：

- 必要章节存在且顺序正确；
- 不出现连续重复的“作品集资料”；
- 正文 Block 数显著少于原始 Passage 数；
- 每个事实章节都有合法 Evidence；
- 不扩大长期生产效果或个人贡献；
- 证据不足类别明确表达边界；
- 桌面和移动视口均无引用挤压或内容溢出。

该场景同时进入既有 Eval Harness 的回答层。确定性 Grader 检查事实、章节和引用；语义 Grader只评价直接性、连贯性、冗余和清晰度。

## 14. 迁移顺序

1. 为检索结果增加稳定 Claim 语义并锁定 Bundle/PostgreSQL 一致性；
2. 以测试驱动实现 `PortfolioAnswerPlan` 与确定性 Composer；
3. 让 Assembler 输出增强后的 v2 Block；
4. 前端映射层支持新 Block，同时保留旧 fixture 兼容；
5. `ConversationThread` 切换到统一章节视图；
6. 更新 Evidence Desk 和引用上下文测试；
7. 运行后端、前端、架构、E2E、隐私和评测门禁；
8. 确认不再有生产调用依赖 legacy `sections` 后，单独删除兼容分支；
9. 更新 `docs/08-当前实现状态.md`、`docs/11-项目演进日志.md` 和本路线图任务状态。

迁移采用同一 `/api/v2/answers` 的加法式字段演进，不恢复已删除的 v1 HTTP 入口。

## 15. 验收标准

本阶段完成必须同时满足：

1. 作品集 Passage 不再一对一成为最终 UI Block；
2. 普通详细介绍回答按稳定语义章节组织；
3. 前端不再重复显示相同来源标签；
4. 引用按章节聚合且全部可打开；
5. Composer 不调用模型、网络或新工具；
6. 相同输入得到相同结构和顺序；
7. 项目状态、贡献类型、限制和验证边界保持不变；
8. 证据无效时失败关闭，不能为了流畅而补写事实；
9. 旧响应兼容只存在于映射层，不形成第二套业务渲染；
10. 后端测试、前端测试、构建、架构检查和目标 E2E 全部通过；
11. Eval Harness 能比较改造前后的结构、重复度、引用完整性和语义质量；
12. 当前实现状态和演进日志与真实代码状态同步。

## 16. 后续关系

本阶段只建立“已验证材料 → AnswerPlan → 确定性回答”的深模块。后续阶段分别在它的上下游扩展：

- `TurnRouter` 改善进入哪种回答任务；
- `PortfolioExecutionPlanner` 改善如何获得最小必要材料；
- `MODEL_GROUNDED` 在同一 Plan seam 上提供可选表达 Adapter；
- `GroundingValidator` 校验模型 Plan 后再允许进入最终 Assembler；
- `HYBRID/MIXED` 扩展多来源章节，但仍复用章节和引用契约。

这些后续能力不得绕过本阶段建立的 Evidence 身份、不变量和确定性 fallback。
