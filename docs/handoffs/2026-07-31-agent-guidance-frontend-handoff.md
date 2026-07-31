# Agent 会话标题与动态引导问题：前端交接方案

> 本文只定义前端实现方案，不在本次后端开发中修改前端源码。

## 目标

1. 长问题作为会话标题时，数据层保留完整文本，界面空间不足时才做视觉省略。
2. 每次回答后始终展示 3 个可点击的后续问题。
3. 前端把后端返回的对话进度带入下一轮，使问题比例从 `3+0` 逐步变化为 `2+1`、`1+2`；用户明确要求看其他项目时为 `0+3`。
4. AI 不可用时仍使用后端的基础回答和 3 个引导问题，不把“降级”误判成请求失败。

## 已可使用的后端契约

接口仍为 `POST /api/v2/answers`。

请求的 `context` 新增：

```ts
type ConversationTopic =
  | 'BACKGROUND'
  | 'RESPONSIBILITY'
  | 'SOLUTION'
  | 'TRADEOFF'
  | 'FAILURE'
  | 'VERIFICATION'
  | 'OUTCOME'

interface AnswerContext {
  projectSlug: string | null
  caseSlug: string | null
  audienceRole: AudienceRole
  source: AnswerRequestSource
  coveredTopics: ConversationTopic[]
}
```

`coveredTopics` 最多 7 项，不能重复。首轮传 `[]`；后续轮次传当前会话上一次响应返回的完整值。

响应新增：

```ts
type ConversationGuidanceStage =
  | 'OPENING'
  | 'DEEPENING'
  | 'WRAP_UP'
  | 'EXPLORE_OTHERS'

interface AnswerResponse {
  // 原有字段……
  coveredTopics: ConversationTopic[]
  guidanceStage: ConversationGuidanceStage
  suggestedQuestions: ConversationSuggestedQuestion[]
}
```

后端保证正常业务响应中的 `suggestedQuestions` 恰好有 3 项，并按阶段分配：

| 阶段 | 当前项目 | 其他项目 |
| --- | ---: | ---: |
| `OPENING` | 3 | 0 |
| `DEEPENING` | 2 | 1 |
| `WRAP_UP` | 1 | 2 |
| `EXPLORE_OTHERS` | 0 | 3 |

AI 降级属于正常 `200` 响应：`generationMode` 为 `FALLBACK`，仍会带 3 个问题。前端应展示回答、降级提示和问题，不进入网络错误视图。

## 修改点

### 1. 类型与映射

涉及文件：

- `frontend/src/features/agent/model/answerTypes.ts`
- `frontend/src/features/agent/model/mapAnswerResponse.ts`
- `frontend/src/features/agent/model/sessionTypes.ts`

要求：

- 给 `PortfolioKnowledgeFacet` 补上后端已支持的 `RESPONSIBILITY`、`OUTCOME`。
- 增加 `ConversationTopic`、`ConversationGuidanceStage`。
- `AnswerResponse` 和 `MappedAnswer` 增加 `coveredTopics`、`guidanceStage`，不再把它们视为可有可无的展示字段。
- 会话对象增加 `coveredTopics: ConversationTopic[]`，仅保存在当前页面的会话内存中；新会话初始为 `[]`。
- 映射时复制数组，避免响应对象与本地会话共享可变引用。

### 2. 下一轮请求携带进度

涉及文件：

- `frontend/src/features/agent/api/answerApi.ts`
- `frontend/src/features/agent/components/AgentWorkspace.vue`

要求：

- `AnswerApiRequest` 增加 `coveredTopics`。
- 序列化到 `body.context.coveredTopics`，不能放在顶层。
- 请求成功并追加 Agent 消息时，把 `mapped.coveredTopics` 回写到对应会话。
- 重试沿用原请求快照中的 `coveredTopics`，不能读取另一个已切换会话的状态。
- 新建会话、清空会话或切换到独立会话时不能继承旧会话进度。

### 3. 严格渲染 3 个问题

涉及文件：

- `frontend/src/features/agent/model/mapAnswerResponse.ts`
- `frontend/src/features/agent/components/ConversationThread.vue`
- `frontend/src/features/agent/components/AgentWorkspace.vue`

要求：

- 每条回答只渲染该回答自己的 `suggestedQuestions`，恰好 3 项。
- 点击后继续使用对象中的 `projectSlug`、`caseSlug` 和 `text` 发起请求。
- 不要按当前页面项目覆盖后端返回的目标项目。
- 问题文字应完整进入请求和消息历史；按钮可视觉换行或省略，但不能修改数据。
- 对格式异常（少于 3 项、重复、同时含 `projectSlug` 和 `caseSlug`）做确定性恢复：从公开 `questionPresets` 中补齐到 3 项，并排除当前问题与最近 6 个用户问题。
- 恢复日志只能记录 `errorCode: 'SUGGESTION_CONTRACT_RECOVERED'`、数量和阶段，禁止记录问题文本。

网络请求失败和后端 AI 降级必须区分：

- 网络失败：保留“重试”入口，同时可展示 3 个本地公开预设问题。
- AI 降级：正常展示后端回答及后端返回的 3 个问题。

### 4. 会话标题不再被数据截断

涉及文件：

- `frontend/src/features/agent/composables/useLocalSessions.ts`
- `frontend/src/features/agent/components/ConversationThread.vue`
- `frontend/src/features/agent/components/LocalSessionRail.vue`

当前问题来自：

```ts
session.title = session.messages[0].content.slice(0, 24)
```

这会永久丢失标题后半段。修改为保存首个用户问题的完整 `trim()` 结果，不在状态层使用 `slice`、`substring` 或 CSS 宽度推导后的字符串裁剪。

展示规则：

- 主标题区域允许最多两行，超出时使用 CSS line clamp。
- 左侧会话栏使用单行 `text-overflow: ellipsis`。
- 省略元素必须设置 `min-width: 0`。
- 主标题和左侧标题都设置完整文本的 `title`；管理按钮的 `aria-label` 继续使用完整标题。
- 手动重命名后保持“人工标题锁”，后续消息不能覆盖。
- 手动输入的长度限制如果保留，应按 Unicode code point 计算，并在提交前明确提示；不能静默截断。

后端响应中的 `title` 是“本轮回答标题”，不是“会话标题”。不要直接用它覆盖会话标题。若以后增加独立的 `conversationTitle` 字段，再实现一次性的语义标题升级。

## 必测场景

1. 首个问题超过 24 个中文字符：会话状态保存全文；主区两行省略；侧栏单行省略；悬停可见全文。
2. 手动重命名后继续问答：标题不被首问或回答标题覆盖。
3. 首轮项目问答：3 个问题全部指向当前项目。
4. `DEEPENING`：2 个当前项目、1 个其他项目。
5. `WRAP_UP`：1 个当前项目、2 个其他项目。
6. 用户输入“推荐其他项目的问题”：3 个问题均指向其他项目。
7. `generationMode: FALLBACK`：仍显示回答和 3 个问题，不出现网络错误页。
8. 第二轮请求：`context.coveredTopics` 等于上一轮响应值。
9. 切换会话：两个会话的 `coveredTopics` 互不污染。
10. 异常响应只有 1 个问题：用公开预设补齐为 3 个，诊断信息不含问题原文。

## 验收标准

- 数据层不再截断会话标题。
- 每个成功回答下方恒有 3 个可点击问题。
- 问题比例与 `guidanceStage` 一致。
- 降级模式可以连续对话。
- 请求体中的 `coveredTopics` 随会话独立演进。
- 不新增包含用户问题原文的日志或持久化存储。
