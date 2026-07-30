# Portfolio V1 Case 与发布收尾设计

**状态：** 已确认，等待实施计划  
**日期：** 2026-07-28  
**适用仓库：** `D:\code\agent`  
**目标分支基线：** `master` / `e4f2abf`  

## 1. 目标

本轮把当前“本地可构建、核心链路可运行”的公开求职作品集 Agent 收口为具有明确 Case 信息架构、完整后端契约、可执行真实 Provider 验收和统一发布门禁的 V1 候选。

本轮包含：

1. 修正项目状态文档与真实代码不一致的问题。
2. 将 Case 确立为与 Project 并列的独立公开入口。
3. 固定 Case → Agent 的后端请求契约和边界。
4. 增加真实 Provider 的显式验收入口。
5. 补齐 Case API、Case Agent 和完整发布门禁。
6. 为后续负责前端的 AI 提供明确设计 brief。

## 2. 已确认的产品方向

采用以下组合：

- 使用方案 B 的信息架构：Project 与 Case 使用独立入口和独立规范 URL。
- 使用方案 A 的实现策略：Project 与 Case 可继续复用现有 Dossier 数据模型和详情能力，避免复制实现。
- 将方案 C 保留为上线后的精选增强：未来仅选择少量 Case 制作故事型页面，不把 49 个 Case 全量改造成故事系统。

正式领域含义：

- **Project：** 长期工作主线、完整项目或连续能力建设。
- **Case：** 一个具体任务、问题、事故或评测如何被识别、处理和验证。
- **Evidence：** 对 Project、Case 和 Claim 提供支持的已批准公开证据。
- **Agent：** 在公开内容边界内围绕 Project、Case 和通用问题进行回答。

## 3. 范围边界

### 3.1 本轮实施

- 项目文档、API 文档、前端设计交接文档和发布说明。
- 后端 Java 代码及其自动化测试。
- PowerShell 发布、JAR 冒烟和真实 Provider 验收脚本。
- 对现有前端测试、构建和 Playwright 的发布门禁调用。

### 3.2 本轮不实施

- Vue 页面、路由、组件、CSS、动画和具体视觉实现。
- 独立 Case Story 内容模型。
- SSE 或 WebSocket 流式回答。
- 用户系统、数据库、持久会话、动态插件、多 Agent 或私有知识库。
- 自动 Provider 故障转移。
- 生产部署本身。

## 4. 当前实现基线

当前随包公开 Bundle 为 schema `3.0`、内容版本 `2026-07-27.1`，包含：

- 7 个 Project
- 49 个 Case
- 81 个 Claim
- 59 个 Evidence
- 81 条 Claim–Evidence 关联
- 11 条 TimelineEvent
- 16 个 QuestionPreset

现有后端已经提供：

```http
GET /api/v1/public-content
GET /api/v1/portfolio
GET /api/v1/projects/{slug}
GET /api/v1/cases
GET /api/v1/cases/{slug}
POST /api/v1/answers
POST /api/v2/answers
```

现有 `CaseDetailResponse` 已包含问题、动作、判断、验证、结果、限制、可选关联项目、公开 Evidence 和 Case 建议问题。本轮不增加重复的 Case Agent endpoint。

## 5. 前端设计交接 brief

本节供后续负责前端 Demo 和代码的 AI 使用。它定义产品目标和交互契约，不规定最终视觉稿。

### 5.1 页面与路由

目标规范路由：

```text
/projects
/projects/:slug
/cases
/cases/:slug
/agent
```

旧的 `/projects/{caseSlug}` 必须兼容并重定向到 `/cases/{caseSlug}`。重定向后 URL 应替换为规范 Case URL，避免两个公开 URL 长期指向相同内容。

### 5.2 导航结构

桌面主导航应明确区分：

```text
首页 | 项目 | 案例 | 时间线 | 证据 | Agent
```

窄屏可以折叠，但 Project 和 Case 不得再次合并成含义模糊的单一入口。

### 5.3 Case 目录目标

Case 目录服务于快速浏览 49 个具体工作案例，至少表达：

- Case 标题和摘要。
- Case 类型。
- AchievementStatus。
- ContributionType。
- 可选关联 Project。
- 进入 Case 详情的明确操作。

类型筛选使用现有领域数据，不在前端发明新的内容分类。可以将现有 Case 类型映射成更自然的中文展示文案，但不得改变后端枚举和公开事实。

### 5.4 Case 详情目标

Case 详情应围绕以下结构组织：

1. 背景或问题。
2. 采取的动作。
3. 关键判断。
4. 验证过程。
5. 结果。
6. 限制与边界。
7. 公开证据。
8. 建议问题及“询问本案例”入口。

Project 与 Case 可以复用详情基础组件，但页面标题、类型标识、面包屑、规范 URL 和 Agent 上下文必须明确区分。

### 5.5 Case → Agent 交接

从 Case 页面触发 Agent 时：

- 携带当前 `caseSlug`。
- 不同时携带 `projectSlug`。
- 使用 `source=CASE`。
- 可以携带用户选择的 Case 建议问题。
- Agent 页面应能让用户看见并清除当前 Case 上下文。
- 清除上下文后，后续请求不得继续隐式携带旧 `caseSlug`。

标准请求：

```json
{
  "turnId": "turn-id",
  "question": "这个案例如何验证？",
  "messages": [],
  "context": {
    "projectSlug": null,
    "caseSlug": "multilingual-image-preservation",
    "audienceRole": "INTERVIEWER",
    "source": "CASE"
  }
}
```

### 5.6 前端必须覆盖的状态

- Case 目录加载、空数据、失败和重试。
- Case 详情加载、404、失败和重试。
- 独立 Case 与关联 Project Case。
- Case 没有 Evidence 或没有建议问题。
- Case → Agent 正常交接。
- Agent 返回确定性降级、边界、拒答和服务错误。
- 旧 Case URL 重定向。
- 键盘操作、焦点返回、窄屏和 Reduced Motion。

### 5.7 视觉方向

沿用当前暖米色、深墨色、低饱和红色强调色和编辑式作品集气质。Project 更强调长期主线和整体交付，Case 更强调具体问题、判断、行动和验证。二者应属于同一设计系统，不应像两个独立网站。

不得为了区分 Case 而引入：

- 高饱和科技渐变。
- 通用 SaaS Dashboard 外观。
- 大量无意义图标。
- 缺少真实内容的装饰图表。
- 与现有 Agent 工作台不一致的第二套颜色体系。

未来可以从 49 个 Case 中选择 3 至 6 个制作故事型 Featured Case，但这不属于当前 V1。

## 6. 后端设计

### 6.1 保持现有 Case API

`GET /api/v1/cases` 继续返回公开 Case 摘要列表。

`GET /api/v1/cases/{slug}` 继续返回 Case 详情、经过公开状态过滤的 Evidence 和 Case 建议问题。

本轮不把前端路径、视觉信息或页面组件结构写入后端 DTO，避免 Portfolio 模块依赖前端实现。

### 6.2 增加 Case 请求来源

在 `AnswerRequestSource` 增加：

```java
CASE
```

它只表示请求来自公开 Case 页面，不解锁任何额外事实，也不改变审批边界。

### 6.3 v2 Case 上下文约束

继续执行：

- `projectSlug` 与 `caseSlug` 不可同时设置。
- 两个字段均只接受 `[a-z0-9-]{1,64}`。
- `audienceRole` 和 `source` 必填。
- 消息最多 40 条，即 20 轮。
- Case 不存在时不得调用 Provider 获取猜测答案。
- Case 关联 Project 不代表允许扩大到整个 Project 的全部事实。

Case 上下文只允许使用当前公开 Bundle 中：

- 与当前 Case 直接关联的 Claim。
- 与当前 Case 直接关联且为 APPROVED、非 raw-public 的 Evidence。
- 显式关联当前 Case 的 QuestionPreset。

### 6.4 响应契约

保留当前 `ConversationAnswerResponse`：

```text
turnId
contentVersion
intent
answerScope
resolution
title
blocks
suggestedQuestions
degraded
```

动态建议问题已经携带可选 `projectSlug` 和 `caseSlug`。Case 上下文下返回的 Case 建议问题必须保持当前 Case slug，除非问题明确要求退出当前主体。

本轮不增加响应中的前端路由字段。前端根据现有上下文和建议问题中的 slug 决定导航。

## 7. 错误与安全行为

| 场景 | 后端行为 |
|---|---|
| Case slug 格式错误 | 400，统一错误结构 |
| Case 不存在 | Case API 返回 `CASE_NOT_FOUND`；Agent fail-closed |
| 同时设置 Project 与 Case | 400 |
| Provider 未批准或无 Key | 不调用 Provider，返回确定性降级或能力边界 |
| Provider 超时、失败或非法结构 | 一次调用后安全降级，不跨 Provider 重发 |
| Case 没有足够公开证据 | 不生成充分核验结论，明确证据不足 |
| 请求包含私有信息诉求 | 拒答，不进入私有数据源 |

日志不得记录：

- API Key。
- 完整访客问题。
- 完整历史对话。
- 原始 Provider 请求或响应。
- 私有 Evidence 内容。

## 8. 真实 Provider 验收

真实 Provider 验收必须显式执行，普通本地测试不得默认产生外部请求。

发布脚本增加 `-RequireLiveProvider`。开启后要求：

```text
PORTFOLIO_MODEL_ENABLED=true
PORTFOLIO_MODEL_DATA_POLICY_APPROVED=true
PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED=true
PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED=true
PORTFOLIO_MODEL_PROVIDER=<approved-provider>
所选 Provider 的项目专用 API Key 已设置
```

验收使用已发布 Case 和非敏感公开问题。通过条件：

- v2 返回 HTTP 200。
- `contentVersion` 等于当前随包版本。
- `degraded=false`。
- `resolution` 不是拒答或能力边界。
- 至少返回一个合法 Block。
- 建议问题结构合法。
- 日志只输出状态、版本、枚举和计数，不输出完整问题、回答或密钥。

缺少审批、开关或 Key 时，`-RequireLiveProvider` 必须失败，不能静默跳过。

## 9. 发布门禁

`scripts/verify-release.ps1` 作为统一入口，覆盖：

1. Java 21。
2. Java 代码质量与架构检查。
3. 隐私检查器测试及源码扫描。
4. 七文件公开 Bundle 校验。
5. 前端类型检查、单测和构建。
6. 后端单测、Case Bundle 集成测试和 clean package。
7. JAR 内容与静态资源一致性检查。
8. 打包后 Case API 冒烟。
9. 打包后 v2 Case Agent 确定性冒烟。
10. JAR Playwright。
11. Docker build check。
12. 显式要求时的真实 Provider 冒烟。
13. 临时文件、进程和环境变量恢复。

普通 CI 可以不要求真实 Provider。生产发布候选必须记录一次 `-RequireLiveProvider` 成功证据。

## 10. 测试设计

### 10.1 后端单元与 MVC 测试

- `source=CASE` 的反序列化。
- Case API 不暴露内部 relation IDs。
- 独立 Case 返回显式 `projectSlug=null`。
- 不存在 Case 返回稳定错误结构。
- Project 与 Case 冲突返回 400。

### 10.2 真实 Bundle 集成测试

- 当前 Bundle 中至少存在一个 Case。
- Case 详情 Evidence 均满足公开状态边界。
- Case 建议问题确实关联当前 Case。
- v2 使用真实 Case slug 能进入确定性运行时。
- 不存在 Case 不产生 Provider 调用。
- Case 动态建议不漂移到无关主体。

### 10.3 Provider Adapter 测试

- 成功响应通过验证。
- 超时、HTTP 失败、空响应和非法结构触发降级。
- 只尝试一次。
- 不跨 Provider 重发。

### 10.4 脚本测试

- `-RequireLiveProvider` 缺少审批时失败。
- 缺少所选 Provider Key 时失败。
- v2 返回 `degraded=true` 时失败。
- 通过时不输出问题、回答和 Key。
- 所有环境变量在成功和失败路径均恢复。

## 11. 文档更新

实施时同步更新：

- `README.md`
- `docs/00-文档状态索引.md`
- `docs/08-当前实现状态.md`
- `docs/05-公开发布包契约.md`
- `docs/06-公开内容发布运行手册.md`

必须修正的旧结论：

- 运行 Bundle 已不是 `2026-07-23.1 / 1 Project / 3 Case`。
- Case 前端数据映射和共享详情能力并非完全未实现。
- Agent v2 前端已经调用 `/api/v2/answers`。
- 当前缺口应描述为独立 Case 路由和前端体验、Case 专属联调、真实 Provider 验收及生产部署。

## 12. 完成标准

本轮在以下条件全部满足后完成：

- 规格、状态、API、前端交接和发布文档一致。
- 后端接受 `source=CASE`。
- Case API 和 Case Agent 真实 Bundle 集成测试通过。
- 打包后 Case API 与 v2 Case Agent 冒烟通过。
- 真实 Provider 门禁已实现并拥有自动化脚本测试。
- 如果运行环境提供已审批 Key，真实 Provider 冒烟通过；否则明确记录为外部验收阻塞。
- 完整本地发布验证通过，任何跳过项明确列出。
- 未修改前端页面、组件、CSS 或具体视觉实现。

