# 前端 AI 开发提示词：Agent 对话内结构化作品推荐

你正在修改项目 `D:\code\agent` 的 Vue 3 + TypeScript 前端。

## 任务目标

在现有 Agent 对话界面中展示后端 `/api/v2/answers` 返回的可选结构化字段 `portfolioRecommendation`。

产品只有一个入口：Agent 对话。推荐是 Agent 回答的增强内容，不是独立产品。

你只负责前端实现：

- 类型定义；
- 响应映射；
- Agent 消息内的推荐卡片；
- 继续对话操作；
- 当前标签页内的会话状态；
- 前端单元测试、组件测试和构建验证。

不要修改后端 Java、数据库、Docker、SQL、推荐算法或后端接口。

## 开发前必须阅读

按顺序阅读：

1. `AGENTS.md`
2. `docs/superpowers/specs/2026-07-31-portfolio-intelligence-hard-routing-design.md`
3. `docs/handoffs/agent-portfolio-recommendation-frontend.md`；如果该文件尚未生成，以本提示词中的契约为准
4. `frontend/src/features/agent/model/answerTypes.ts`
5. `frontend/src/features/agent/model/mapAnswerResponse.ts`
6. `frontend/src/features/agent/api/answerApi.ts`
7. `frontend/src/features/agent/components/AgentWorkspace.vue`
8. `frontend/src/features/agent/components/ConversationThread.vue`
9. 对应的 `.test.ts` 文件

先理解现有 Agent 消息、证据、建议问题、降级提示和窄屏布局，再开始修改。

## 产品边界

必须遵守：

- 不新增 Selection 页面。
- 不新增 Selection 路由、导航入口或菜单。
- 不新增 `/api/portfolio-selections` 请求。
- 所有推荐和调整仍调用 `POST /api/v2/answers`。
- 前端不得筛选、重排、增删或替换后端推荐项。
- 前端不得生成 SQL、选择检索器或推荐策略。
- 后端返回的 `items` 顺序是最终权威顺序。
- 推荐卡片只是 Agent 消息的一部分，不能覆盖普通文本回答和证据展示。

## 隐私与状态边界

- 对话和推荐上下文只保存在当前标签页内存。
- 不写入 `localStorage`、`sessionStorage`、IndexedDB、URL 或浏览器历史。
- 刷新或关闭标签页后允许丢失推荐上下文。
- 不记录原始问题、回答、批次 ID 或推荐项到前端诊断事件。
- 不展示 SQL、内部策略名、数据库状态、异常堆栈或本地路径。
- 不改变现有页面关于“刷新后会话消失”的隐私说明。

## 后端响应契约

普通回答没有 `portfolioRecommendation` 字段。

推荐回答示例：

```json
{
  "turnId": "turn-100",
  "contentVersion": "public-2026-07-31",
  "intent": "PORTFOLIO_GROUNDED",
  "answerScope": "PORTFOLIO",
  "resolution": "ANSWERED",
  "title": "推荐结果",
  "blocks": [
    {
      "sourceScope": "PORTFOLIO",
      "content": "我按公开证据和你给出的条件选出了 2 个作品。",
      "claimIds": [],
      "evidenceIds": ["evidence-1"]
    }
  ],
  "suggestedQuestions": [],
  "degraded": false,
  "generationMode": "DETERMINISTIC",
  "answerSource": "RETRIEVAL",
  "portfolioRecommendation": {
    "recommendationBatchId": "rec_0123456789abcdef0123456789abcdef",
    "items": [
      {
        "portfolioId": "project-1",
        "title": "项目一",
        "route": "/projects/project-one",
        "matchReasons": ["匹配后端能力要求"],
        "evidenceIds": ["evidence-1"]
      },
      {
        "portfolioId": "case-2",
        "title": "案例二",
        "route": "/cases/case-two",
        "matchReasons": ["补充 PostgreSQL 与验证能力"],
        "evidenceIds": ["evidence-2"]
      }
    ],
    "satisfiedConstraints": ["audienceRole", "requestedSize"],
    "unsatisfiedConstraints": []
  }
}
```

推荐调整时，请在下一次 `/api/v2/answers` 请求的 `context` 中原样回传：

```json
{
  "recommendationBatchId": "rec_0123456789abcdef0123456789abcdef"
}
```

不得自行构造、修改或解析批次 ID。

## TypeScript 类型

在 `frontend/src/features/agent/model/answerTypes.ts` 增加：

```ts
export interface PortfolioRecommendationItem {
  portfolioId: string
  title: string
  route: string
  matchReasons: string[]
  evidenceIds: string[]
}

export interface PortfolioRecommendation {
  recommendationBatchId: string
  items: PortfolioRecommendationItem[]
  satisfiedConstraints: string[]
  unsatisfiedConstraints: string[]
}
```

给 `AnswerResponse` 和 `MappedAnswer` 增加：

```ts
portfolioRecommendation?: PortfolioRecommendation
```

给发往 `/api/v2/answers` 的上下文类型增加：

```ts
recommendationBatchId?: string
```

保持该字段可选，不能破坏普通问答和旧响应。

## 响应映射

在 `mapAnswerResponse.ts` 中：

- 缺少 `portfolioRecommendation` 时保持 `undefined`。
- 深拷贝 `items`、`matchReasons`、`evidenceIds`、`satisfiedConstraints` 和 `unsatisfiedConstraints`。
- 不排序、不去重、不重写后端 `items`。
- 结构化字段不合法时，不允许整条文本回答崩溃；保留可信的 `blocks`，忽略非法推荐结构，并调用现有安全诊断入口。
- 诊断中不能包含完整响应、问题、批次 ID 或作品内容。

建议提取一个小型运行时校验函数，不引入新的 schema 库，除非项目已经使用该库。

## Agent 消息内推荐卡片

在 `ConversationThread.vue` 的现有结构化回答区域中渲染。

推荐回答的视觉顺序：

1. 现有 Agent 元信息；
2. 降级提示；
3. 标题和文本 `blocks`；
4. `portfolioRecommendation` 推荐卡片组；
5. 现有证据入口和建议问题。

每张卡片至少展示：

- 顺序编号；
- 标题；
- `matchReasons`；
- 查看作品按钮，目标使用后端 `route`；
- 与现有证据桌面兼容的证据入口。

整组推荐：

- 有 `satisfiedConstraints` 时以低强调信息展示；
- 只有存在 `unsatisfiedConstraints` 时才展示未满足条件；
- `items=[]` 时显示后端文本回答与未满足条件，不创建假作品卡片；
- `degraded=true` 时沿用现有降级提示，不隐藏真实返回的推荐卡片。

不要：

- 制作排行榜视觉；
- 使用“AI 精准命中”“最佳选择”等夸张文案；
- 添加拖拽排序、删除、收藏或客户端筛选；
- 复制一个新的 Agent 页面。

样式沿用现有 Agent 工作区的颜色、间距、圆角、边框和响应式断点。窄屏不得横向溢出。

## 继续对话操作

卡片组可以提供这些轻量操作：

- `换掉这个`
- `为什么推荐这个？`
- `再偏后端一点`
- `把数量改成 2 个`

点击后只做两件事：

1. 生成自然语言问题，例如第 2 张卡片点击“换掉这个”时发送 `换掉第二个`；
2. 在请求 `context.recommendationBatchId` 中回传当前批次 ID。

仍然调用现有 `askQuestion()` / `/api/v2/answers` 链路。

不要为这些操作创建独立推荐接口，不要把作品 ID写进用户可见问题，不要在客户端计算替换结果。

## 测试驱动要求

先写失败测试，再实现。

至少覆盖：

### `mapAnswerResponse.test.ts`

- 普通回答映射后无推荐字段。
- 推荐字段完整深拷贝。
- 后端顺序不变。
- 修改映射结果数组不会改变原响应。
- 非法推荐结构不影响可信文本回答。

### `ConversationThread.test.ts`

- 普通消息不渲染推荐容器。
- 多个推荐项按后端顺序显示。
- 空推荐显示未满足约束。
- 推荐卡片显示理由和作品链接。
- 降级回答仍显示推荐。
- 窄屏结构使用现有响应式类，不产生额外页面。

### `AgentWorkspace.test.ts`

- 点击“换掉这个”仍调用 `/api/v2/answers`。
- 请求包含当前 `recommendationBatchId`。
- 下一批推荐替换旧批次，不混合两个批次。
- 普通问题不携带陈旧批次 ID。
- 刷新或创建新标签会话后不恢复批次上下文。
- 不产生 `/api/portfolio-selections` 请求。

## 验证命令

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
```

两条命令都必须通过。

最后检查：

```powershell
rg -n "/api/portfolio-selections|localStorage|sessionStorage|indexedDB" frontend/src
```

不得新增独立推荐接口或推荐上下文持久化。

## 完成交付格式

完成后输出：

1. 修改文件列表；
2. 推荐卡片交互说明；
3. 测试命令与结果；
4. 构建结果；
5. 明确声明没有修改后端；
6. 明确声明没有新增 Selection 页面或接口；
7. 明确声明推荐上下文只存在当前标签页内存。

不要声称后端已完成，除非 `/api/v2/answers` 的结构化字段已经实际联调验证。
