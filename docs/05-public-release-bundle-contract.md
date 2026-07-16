# 公开发布包契约

**日期：** 2026-07-15  
**状态：** 待用户统一审核  
**适用设计：** `docs/superpowers/specs/2026-07-15-dynamic-public-content-agent-design.md`

## 1. 目的

本文定义公开候选包与运行发布包的文件结构、实体关系、跨文件校验、兼容规则和索引元数据。它是私有审核工具、公开包构建器、服务器发布工具和 Spring Boot 加载器之间的共同契约。

公开发布包不等于私有审核包。公开包只包含已批准、已脱敏且允许进入公网运行环境的内容。

## 2. 两阶段包结构

私有环境导出的公开候选包只包含可审计的公开源数据：

```text
<contentVersion>-candidate/
├─ candidate-manifest.json
├─ portfolio.json
├─ presentation.json
└─ rag-documents.jsonl
```

部署服务器验证候选内容并构建索引后，生成完整运行发布包：

```text
<contentVersion>/
├─ manifest.json
├─ portfolio.json
├─ presentation.json
├─ rag-documents.jsonl
├─ keyword-index/
├─ vector-index/
└─ checksums.json
```

所有文件必须位于各自包的根目录内。禁止绝对路径、父目录跳转、符号链接、设备文件和未声明文件。候选包不得携带关键词索引、向量索引、可执行文件或供应商返回的原始响应。

`candidate-manifest.json` 只声明 Schema、contentVersion、源文件名、实体数量和源文件哈希。服务器不得信任其中的审核结论，而应重新执行结构、引用和隐私校验。运行 `manifest.json` 由服务器发布工具生成，增加索引和应用兼容元数据。

## 3. Manifest

`manifest.json` 是运行发布包入口：

```json
{
  "schemaVersion": "2.0",
  "contentVersion": "2026-07-20.1",
  "publishedAt": "2026-07-20T20:00:00+08:00",
  "builtAt": "2026-07-20T19:55:00+08:00",
  "minimumApplicationVersion": "0.1.0",
  "factsFile": "portfolio.json",
  "presentationFile": "presentation.json",
  "ragDocumentsFile": "rag-documents.jsonl",
  "keywordIndex": {
    "format": "portfolio-keyword-v1",
    "documentCount": 24
  },
  "vectorIndex": {
    "format": "portfolio-vector-v1",
    "provider": "configured-adapter",
    "model": "configured-model",
    "dimension": 1536,
    "documentCount": 24
  },
  "retrievalStrategyVersion": "hybrid-v1",
  "counts": {
    "projects": 4,
    "claims": 36,
    "evidence": 13,
    "timelineEvents": 28,
    "questionPresets": 16,
    "ragChunks": 24
  }
}
```

示例中的 provider、model 和维度只说明字段含义，不是供应商选型。真实发布时必须写入实际值。

## 4. 事实文件

`portfolio.json` 顶层结构：

```json
{
  "schemaVersion": "2.0",
  "contentVersion": "2026-07-20.1",
  "owner": {},
  "internshipPeriod": {},
  "projects": [],
  "claims": [],
  "evidence": [],
  "timelineEvents": [],
  "questionPresets": []
}
```

### 4.1 OwnerProfile

```json
{
  "name": "公开姓名",
  "role": "Java 后端开发实习生",
  "summary": "经审核的公开简介",
  "githubUrl": null,
  "email": null,
  "resumeUrl": null
}
```

URL 字段为空时使用 `null`，禁止空白字符串和非 HTTP(S) 链接。姓名是否公开由私有审核决定。

### 4.2 InternshipPeriod

```json
{
  "startDate": "2026-04-01",
  "endDate": "2026-07-31",
  "label": "2026 春夏"
}
```

该对象用于表达审核后的正式实习周期。若不存在，月份统计可以根据已发布 TimelineEvent 计算；两种口径不能混用。

### 4.3 Project

```json
{
  "id": "project-sql-audit",
  "slug": "sql-audit",
  "title": "SQL 审计与故障排查工具",
  "summary": "经审核的项目摘要",
  "periodStart": "2026-05-29",
  "periodEnd": "2026-07-10",
  "status": "DELIVERED",
  "contributionType": "PRIMARY",
  "technologies": ["Java", "Spring Boot", "WebSocket"],
  "topics": ["LOG_QUERY", "ASYNC_TASK"],
  "featured": true,
  "claimIds": ["claim-sql-audit-async"],
  "evidenceIds": ["evidence-sql-audit-test"],
  "timelineEventIds": ["timeline-sql-audit-iteration"]
}
```

规则：

- `slug` 满足 `[a-z0-9-]{1,64}`；
- ID、slug 全局唯一；
- 日期结束不得早于开始；
- 状态和贡献类型必须是固定枚举；
- 所有关联对象必须反向指向同一 Project 或声明为跨项目对象；
- `featured` 只表示可被展示配置选择，不自动决定页面位置。

### 4.4 Claim

```json
{
  "id": "claim-sql-audit-async",
  "projectId": "project-sql-audit",
  "type": "TECHNICAL_DECISION",
  "statement": "将远程日志查询设计为异步任务。",
  "detail": "查询耗时不可预测，异步执行避免同步请求长期占用。",
  "achievementStatus": "DELIVERED",
  "contributionType": "PRIMARY",
  "evidenceIds": ["evidence-sql-audit-test"],
  "topics": ["ASYNC_TASK", "RELIABILITY"],
  "audiencePriorities": {
    "INTERVIEWER": 100,
    "MENTOR": 70,
    "HR": 30,
    "GUEST": 50
  }
}
```

Claim 类型：

```text
BACKGROUND
RESPONSIBILITY
TECHNICAL_DECISION
IMPLEMENTATION
VERIFICATION
OUTCOME
LIMITATION
LEARNING
REFLECTION
```

事实状态：

```text
DELIVERED
IMPLEMENTED_TESTED
PROTOTYPE
DESIGNED
LEARNING
PLANNED
UNKNOWN
```

规则：

- `statement` 是最短完整事实，`detail` 只能解释该事实；
- 成果性状态 `DELIVERED / IMPLEMENTED_TESTED / PROTOTYPE / DESIGNED` 必须关联 Evidence；
- `LEARNING / PLANNED / UNKNOWN` 可以没有成果 Evidence，但表达必须保留状态边界；
- 身份优先级范围为 0 至 100，只影响排序；
- 缺少某身份优先级时使用代码默认值，不从其他身份推断；
- 不允许在 Claim 中保存模型 Prompt 或渲染 HTML。

### 4.5 Evidence

```json
{
  "id": "evidence-sql-audit-test",
  "title": "实现与自测记录",
  "type": "TEST_RECORD",
  "periodStart": "2026-05-29",
  "periodEnd": "2026-06-05",
  "sourceCount": 3,
  "summary": "覆盖异步任务、状态恢复和轮询兜底的脱敏摘要。",
  "supportedClaimIds": ["claim-sql-audit-async"],
  "rawContentPublic": false
}
```

正式公开包不需要保存 `DRAFT / APPROVED / REJECTED / BLOCKED`。对象能够进入公开包即代表已批准；`rawContentPublic` 第一阶段必须固定为 false。

规则：

- `sourceCount` 大于 0；
- 日期范围有效；
- 每个 supported Claim 必须反向引用该 Evidence；
- 摘要不得包含原始路径、内部地址、账号或凭据；
- 第一阶段不发布原始 Evidence 正文。

### 4.6 TimelineEvent

```json
{
  "id": "timeline-sql-audit-iteration",
  "dateStart": "2026-05-29",
  "dateEnd": "2026-06-05",
  "type": "ITERATION",
  "title": "SQL 查询工具形成完整闭环",
  "summary": "从固定路径查询扩展到异步、多目标、导出与归档。",
  "achievementStatus": "DELIVERED",
  "projectIds": ["project-sql-audit"],
  "claimIds": ["claim-sql-audit-async"],
  "evidenceIds": ["evidence-sql-audit-test"],
  "topics": ["ASYNC_TASK"]
}
```

事件类型：

```text
DELIVERY
ITERATION
INCIDENT
DESIGN
LEARNING
REFLECTION
```

事件摘要不能比其关联 Claim 表达更强的完成结论。

### 4.7 QuestionPreset

```json
{
  "id": "question-sql-audit-async",
  "text": "查询为什么需要设计成异步？",
  "audiences": ["INTERVIEWER"],
  "projectIds": ["project-sql-audit"],
  "topics": ["ASYNC_TASK"],
  "preferredClaimTypes": ["BACKGROUND", "TECHNICAL_DECISION", "VERIFICATION"],
  "placements": ["HOME", "PROJECT"],
  "deterministicEntry": true,
  "displayOrder": 20
}
```

QuestionPreset 是检索入口，不保存完整答案。`deterministicEntry=true` 表示必须有覆盖该问题的确定性回归用例或模板路径。

## 5. 展示配置

`presentation.json` 顶层结构：

```json
{
  "schemaVersion": "2.0",
  "contentVersion": "2026-07-20.1",
  "audiences": [],
  "homeSections": [],
  "metrics": [],
  "explorePortals": [],
  "footer": {}
}
```

### 5.1 AudienceProfile

```json
{
  "id": "INTERVIEWER",
  "label": "技术面试官",
  "description": "侧重技术方案、取舍、实现和验证。",
  "detailLevel": "DETAILED",
  "claimTypePriorities": [
    "TECHNICAL_DECISION",
    "IMPLEMENTATION",
    "VERIFICATION",
    "LIMITATION"
  ],
  "questionPresetIds": ["question-sql-audit-async"]
}
```

固定身份代码为 `INTERVIEWER / MENTOR / HR / GUEST`。可以调整公开文案和排序，不得新增会改变权限或事实可见性的身份。

### 5.2 HomeSection

```json
{
  "type": "METRICS",
  "visible": true,
  "displayOrder": 20,
  "eyebrow": "01 · VERIFIED OVERVIEW",
  "title": "先建立可信度，再进入细节。",
  "description": "首页只展示能够核对的公开摘要。"
}
```

固定类型：

```text
HERO
METRICS
LIGHT_AGENT
EXPLORE_PORTALS
FOOTER
```

每个类型最多出现一次。未知类型、重复类型和缺少必要字段均拒绝发布。

### 5.3 MetricPresentation

```json
{
  "metric": "PUBLIC_PROJECT_COUNT",
  "label": "PROJECTS · 核心项目",
  "description": "项目状态与个人贡献独立标记。",
  "target": "projects",
  "displayOrder": 20
}
```

允许指标：

```text
INTERNSHIP_MONTH_COUNT
PUBLIC_PROJECT_COUNT
PUBLIC_EVIDENCE_COUNT
PUBLIC_CLAIM_COUNT
```

配置中不存在数值字段。数值必须由后端根据当前公开实体计算。

### 5.4 ExplorePortal

入口只使用代码支持的目标：

```text
PROJECTS
TIMELINE
EVIDENCE
AGENT
```

快照可以配置标题、说明、顺序和是否显示，不能配置任意 URL 跳转到未知内部地址。

## 6. RAG 文档

`rag-documents.jsonl` 每行一个完整 JSON 对象：

```json
{"chunkId":"chunk-sql-audit-async-01","projectIds":["project-sql-audit"],"claimIds":["claim-sql-audit-async"],"evidenceIds":["evidence-sql-audit-test"],"topics":["ASYNC_TASK"],"text":"经审核的公开补充说明。","contentHash":"sha256:..."}
```

规则：

- UTF-8，每行不得跨行；
- `chunkId` 唯一；
- `text` 非空且满足发布长度限制；
- 所有引用存在；
- 没有关联 Claim 的 Chunk 只能作为背景补充，不能支持成果结论；
- `contentHash` 由规范化文本和关联元数据共同计算；
- 文本或引用变化后，私有审核批准自动失效；
- Chunk 不包含 Markdown 图片、HTML、脚本、内部 URL 或原始附件路径。

## 7. 索引契约

关键词和向量索引必须由服务器发布工具根据 `rag-documents.jsonl` 构建，不能由上传者直接提供可执行索引文件。

索引必须记录：

- contentVersion；
- index format version；
- source file hash；
- document count；
- chunk ID 集合摘要；
- 向量 provider、model 和 dimension；
- 构建时间。

任何不一致都拒绝激活。更换向量模型、provider 输出维度或文本规范化策略时必须完整重建索引。

## 8. Checksums

`checksums.json` 列出 manifest 之外所有受保护文件的 SHA-256。Manifest 自身哈希由发布日志保存，避免自引用。

加载顺序：

1. 读取 manifest；
2. 检查允许文件清单；
3. 读取 checksums；
4. 验证事实、展示、RAG 和索引文件；
5. 解析内容；
6. 执行跨文件业务校验。

## 9. 跨文件不变量

- schemaVersion、contentVersion 全部一致；
- Manifest counts 与实际数量一致；
- Project、Claim、Evidence、TimelineEvent、QuestionPreset 和 Chunk ID 唯一；
- 所有正反向引用一致；
- 成果性 Claim 有 Evidence；
- Evidence 只支持实际反向引用它的 Claim；
- TimelineEvent 不引用不存在或未发布对象；
- QuestionPreset 目标存在；
- presentation 只引用已发布实体；
- RAG Chunk 引用存在且哈希正确；
- 索引只包含当前版本 Chunk；
- 禁止未知 JSON 字段静默通过；
- 禁止 `null` 代替必填集合或必填文本。

## 10. Schema 与应用兼容

- Schema 主版本不兼容时拒绝加载；
- Schema 次版本只能做向后兼容的加字段演进；
- 应用必须显式声明支持的 Schema 范围；
- 发布包声明 `minimumApplicationVersion`；
- 旧应用不能激活需要新能力的发布包；
- 第一阶段不在运行时自动迁移发布包；迁移由私有构建器生成新版本完成。

## 11. 私有审核包约束

私有审核对象需要保存审核状态、审核时间、审核人、审核说明、来源修订和内容哈希。任一公开正文、状态、贡献类型、关联关系或内容哈希变化，都必须使原批准失效并回到 `DRAFT`。

私有审核包不得上传部署服务器。公开包生成器必须重新构造白名单字段，而不是从私有对象序列化后删除部分字段。

## 12. 数据库演进

后期数据库表或文档集合可以按上述实体拆分，但 API 和业务用例继续依赖 `PublicContentRepository`。数据库发布仍应生成不可变 release 记录和 contentVersion；不能退化为直接在线修改当前公开行而没有版本边界。
