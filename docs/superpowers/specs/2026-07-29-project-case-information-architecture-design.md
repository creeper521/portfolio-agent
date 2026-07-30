# Project—Case 总览下钻信息架构设计

**日期：** 2026-07-29

**状态：** 已完成产品讨论，等待书面审阅

**目标仓库：** `D:\code\agent`

## 1. 背景

当前 `/projects` 使用 `buildDossierIndex(data.projects, data.cases)`，把 Project 与全部 Case 合并为工程案卷目录；`/cases` 又把同一批 Case 按状态展示。两个一级入口因此重复收录49个 Case，用户只能看到不同分组方式，无法形成“长期主线”和“具体工作”的稳定认知。

本设计保留 `/projects` 与 `/cases` 两个独立入口，但建立明确的“总览—下钻”关系：

```text
Project：先理解长期工作、完整工具或统一集成产物
  └─ Case：再查看一次具体问题如何被识别、判断、处理和验证

Case：也可以按状态、Project、Collection 和类型直接检索
```

主要受众是技术面试官，目标是在30–90秒内理解候选人的 Java 后端和 Agent 两条主求职方向，并能继续核验具体工作。

## 2. 已确认的产品决策

1. `/projects` 只展示 Project，不再混入完整 Case 目录。
2. `/cases` 保留全部公开 Case，承担快速检索。
3. 一个 Project 可以关联零个或多个 Case；一个 Case 最多归属一个主要 Project。
4. Case 可以不属于 Project，此时称为独立案例。
5. Case Collection 是浏览策展关系，不是成果归属，不是 Claim 或 Agent 主体。
6. Java 后端和 Agent 是并列主求职方向，不使用默认 Tab 隐藏任一方向。
7. 缺少截图只影响验证强度，不能把实际工作自动归为学习。
8. 不为了页面对称强制 Project 拆出 Case；只有真实独立处理过程才建立 Case。
9. 前端视觉方案由独立的前端设计 AI 完成；本规格定义背景、信息架构、数据契约和验收边界。
10. 后端实现由当前项目的后端开发流程负责，实施前仍需单独的实现计划。

领域固定用语见仓库根目录 `CONTEXT.md`。

## 3. 目标内容结构

### 3.1 Project

最终保留5个 Project。

| 展示顺序 | Project | 求职方向 | 项目性质 | 展示层级 | 公开定位 |
|---|---|---|---|---|---|
| 1 | SQL审计与故障排查工具 | Java 后端 | 工具 | 主要项目 | 已交付、主导贡献 |
| 2 | 活动系统工程实践 | Java 后端 | 工作主线 | 主要项目 | 实习期间部分功能开发、配置协助、活动复开与排查 |
| 3 | 测试角色重置工具 | Java 后端 | 工具 | 主要项目 | 查询、确认、重置、复查和使用说明组成的已交付闭环 |
| 4 | Agent能力集成MVP | Agent | 集成原型 | 主要项目 | 学习型原型，打通 Prompt、Skills、RAG、MCP、Memory 和 Agent 最小链路 |
| 5 | 图片上传与审计 | Java 后端 | 工具 | 次级项目 | 多语言上传保留与上传审计组成的小型项目 |

“主要项目”和“次级项目”是展示层级，不表示生产成熟度。Agent能力集成MVP 必须明确为学习型原型，不得宣称生产可用、持续运营或完整平台能力。

### 3.2 Case Collection

以下3个现有主题 Project 降为 Case Collection：

| Collection | 用途 |
|---|---|
| 开源项目体验与测试 | 聚合开源工具体验、上下文工程与离线评测 |
| 工程操作与学习 | 聚合构建、替换、容器化、远程操作和工程知识整理 |
| 技术写作与分享 | 聚合内部分享、技术长文和历史博客 |

Collection：

- 没有独立详情页；
- 不成为 Claim 主体；
- 不成为 Agent 对话主体；
- 通过 `/cases?collection=<slug>&status=all` 浏览；
- 可以与 Project 归属同时存在。

### 3.3 Case 工作状态

Case 的展示状态调整为：

| 领域状态 | 展示分组 | 含义 |
|---|---|---|
| `DELIVERED`、`IMPLEMENTED_TESTED` | 已交付 | 已完成并有相应实现或验收记录 |
| `INVESTIGATED` | 已排查／参与处理 | 实际参与定位、边界确认或协作转交，但缺少最终修复与完整验收材料 |
| `PROTOTYPE` | 原型验证 | 完成可运行或可验证的探索性产物 |
| `LEARNING` | 学习整理 | 主要结果是知识理解、方案整理或概念学习 |

`VerificationStatus` 和 `VerificationBasis` 继续独立表达证据强度。工作状态不得由是否存在截图推导。

## 4. 领域关系

### 4.1 Project—Case

```text
Project 1 ─── 0..N Case
Case    0..1 ─── Project
```

- `CaseStudy.projectId` 保持可空单值，不改成多对多。
- `projectId = null` 是独立案例的唯一判定。
- Collection 归属不改变 Case 是否独立。
- Project 的 `caseCount` 从关系实时计算，不在内容文件重复存储。

### 4.2 Project 精选 Case

Project 增加有序的 `featuredCaseIds`：

- 0个关联 Case：不显示相关案例区。
- 1–3个关联 Case：全部展示。
- 超过3个：展示策展指定的3–6个 `featuredCaseIds`。
- 同时提供“查看全部 N 个案例”。
- `featuredCaseIds` 只能引用归属于当前 Project 的 Case，不得重复，最多6个。

活动系统精选以下6个 Case：

1. `CASE-42 活动开发流程与红点设计`
2. `CASE-16 重复配置导致唯一性冲突`
3. `CASE-20 结束时间与旧客户端双重过滤`
4. `CASE-21 环境结构缺失字段`
5. `CASE-29 礼包积分被配置覆盖`
6. `CASE-30 定制内容展示排查`

它们分别覆盖功能与架构理解、日志和数据排查、兼容性判断、环境问题处理、配置与版本管理、跨端协作边界。

其他 Project：

- Agent能力集成MVP：展示全部4个关联 Case。
- 图片上传与审计：展示全部关联 Case。
- 测试角色重置工具：展示唯一关联 Case。
- SQL审计工具：当前不制造 Case，不显示相关案例区。

## 5. Project 数据模型

`ProjectProfile` 在现有字段上增加：

```text
careerTrack
projectNature
displayTier
featuredCaseIds
```

建议枚举：

```text
CareerTrack:
  JAVA_BACKEND
  AGENT
  UNCLASSIFIED       # 仅用于旧 schema 规范化

ProjectNature:
  TOOL
  WORKSTREAM
  INTEGRATION_PROTOTYPE
  UNCLASSIFIED       # 仅用于旧 schema 规范化

ProjectDisplayTier:
  PRIMARY
  SECONDARY
```

schema 4.0 内容不得使用 `UNCLASSIFIED`；它只允许加载旧 schema 时内部兼容。生产4.0包必须为每个 Project 提供明确分类。

## 6. Case Collection 数据模型

新增 `CaseCollection`：

```text
id
slug
title
summary
displayOrder
```

`CaseStudy` 增加：

```text
collectionIds: string[]
```

Case 可以属于零个或多个 Collection。Collection 是多对多策展关系，但不改变 Project 的单一主要归属。

`AchievementStatus` 增加：

```text
INVESTIGATED
```

该状态可以用于 Case 与对应 Claim，表示实际排查成果；不能替代验证状态。

## 7. API 契约

继续使用现有接口，不为49条数据新增服务端筛选接口：

```text
GET /api/v1/portfolio
GET /api/v1/projects/{slug}
GET /api/v1/cases
GET /api/v1/cases/{slug}
```

### 7.1 Project 响应

Project 摘要增加：

```text
careerTrack
projectNature
displayTier
caseCount
```

Project 详情增加：

```text
caseCount
featuredCases: CaseSummaryResponse[]
```

`caseCount` 和 `featuredCases` 由服务层根据公开 Case 投影，不由前端自行拼接私有数据。

### 7.2 Case 响应

Case 摘要确保包含：

```text
projectSlug: string | null
collectionSlugs: string[]
```

Case 详情增加 `collectionSlugs`，并继续返回所属 Project slug、公开 Evidence 和建议问题。

### 7.3 Portfolio 响应

Portfolio 增加：

```text
collections: CaseCollectionResponse[]
```

现有响应字段不删除。49条 Case 的筛选由前端本地执行。

## 8. 前端信息架构要求

### 8.1 `/projects`

页面名称使用“项目主线”，不再使用含义过宽的“工程案卷目录”。

必须满足：

- Java 后端和 Agent 两条主线同时可见；
- 第一层展示4个主要项目；
- 图片上传与审计进入次级区域；
- 不直接铺开完整 Case 目录；
- 排序由内容策展固定，不按案例数量或更新时间自动改变；
- 无关联 Case 时省略数量，不显示“0个案例”。

Project 卡片必须表达：

- 求职方向；
- 项目性质；
- 真实成熟度；
- 贡献方式；
- 一句话目标或结果；
- 核心技术；
- 大于零时的关联 Case 数量。

具体布局、卡片形态和响应式细节由前端设计 AI 决定。

### 8.2 Project 详情

保留现有：

```text
为什么做
我的职责
如何做
如何证明
最终状态
```

新增“相关案例”区，并遵循第4.2节规则。

“查看全部”使用：

```text
/cases?project=<slug>&status=all
```

Case 摘要只显示问题、类型、工作状态和贡献方式，不复制完整处理过程。“询问这个项目”继续携带 Project 上下文，不因相关 Case 自动切换主体。

### 8.3 `/cases`

支持组合筛选：

1. 工作状态：全部、已交付、已排查／参与处理、原型验证、学习整理；
2. 归属范围：全部、某个 Project、某个 Collection、独立案例；
3. Case 类型：功能任务、问题处理、工具评测；
4. 可选的标题关键词搜索。

默认行为：

- 直接进入 `/cases` 默认显示“已交付”；
- 从 Project 详情进入时显式使用 `status=all`；
- Project、Collection、状态、类型写入 URL query；
- 未知筛选值被忽略并回退到安全默认状态；
- 搜索关键词是否进入 query 由前端设计决定。

每条 Case 至少表达：

- 标题与问题摘要；
- Case 类型；
- 工作状态；
- 贡献方式；
- 所属 Project，或“独立案例”；
- 可选 Collection 辅助标签。

### 8.4 Case 详情

继续保留：

```text
问题与背景
采取的动作
关键判断
验证过程
结果
限制与边界
公开证据
建议问题
```

必须明确：

- 所属 Project 或“独立案例”；
- 关联 Collection；
- 工作状态与证据强度分别展示；
- “询问本案例”只提交 `caseSlug` 作为主要上下文；
- 第一版不要求上一条／下一条保持筛选上下文。

## 9. 内容迁移

### 9.1 保留与新增 Project

- 保留 `sql-audit-project`。
- 保留 `image-audit-project`，改为次级 Project。
- 保留 `activity-engineering-project`。
- 保留 `personal-agent-platform-project`，公开标题改为“Agent能力集成MVP”。
- 新增 `role-reset-tool-project`，建议 slug 为 `role-reset-tool`。
- `case-role-reset` 关联到新 Project。
- `case-multilingual-upload` 关联到 `image-audit-project`。

### 9.2 降级为 Collection

- `context-engineering-project` → `open-source-evaluation`
- `technical-writing-project` → `technical-writing`
- `engineering-delivery-learning-project` → `engineering-operations`

被移除 Project 的 Claim、Evidence、Timeline 和 Question 引用必须逐条迁移：

- 可证明具体 Case 的，迁移到 Case；
- 通用建议问题可改为 Case 或全局问题；
- Collection 不接收 Claim；
- 不允许因删除 Project 留下悬空引用；
- 不允许直接丢弃仍有效的公开陈述。

### 9.3 活动系统状态修正

当前被误归为 `LEARNING` 的15条实际排查工作迁移为 `INVESTIGATED`：

```text
CASE-22 至 CASE-36
```

其中 Claim 的 category、statement、achievementStatus、verificationBasis 和 verificationStatus 必须逐条复核，不能只批量替换枚举。

`CASE-45 红点架构与复弹机制学习` 保持 `LEARNING`。

## 10. 旧链接与版本兼容

保留仍存在 Project 的 id 与 slug。

三个被降级 Project 的旧页面由前端规范重定向：

```text
/projects/context-engineering-evaluation
  → /cases?collection=open-source-evaluation&status=all

/projects/technical-writing
  → /cases?collection=technical-writing&status=all

/projects/engineering-delivery-learning
  → /cases?collection=engineering-operations&status=all
```

旧 `GET /api/v1/projects/{slug}` 请求返回统一404，不把 Project API 偷换为 Collection 响应。

公开内容升级到 schema 4.0。加载器继续接受2.0、3.0和4.0：

- 2.0、3.0 缺失 Collection 时规范化为空集合；
- 旧 Project 展示元数据规范化为 `UNCLASSIFIED`；
- 前端遇到 `UNCLASSIFIED` 时使用单一“项目”分区回退；
- 4.0 必须提供完整分类，且不得使用 `UNCLASSIFIED`；
- 未知 schema 版本继续拒绝加载。

## 11. 校验与失败策略

继续采用 fail-closed：

- Project、Case、Collection id 与 slug 必须唯一；
- `collectionIds` 必须引用现有 Collection；
- `featuredCaseIds` 必须归属当前 Project、不得重复、最多6个；
- schema 4.0 Project 分类字段必须完整；
- Project、Case、Claim、Evidence、Timeline、Question 的引用必须有效；
- 被移除 Project 不能留下悬空引用；
- Case 缺公开 Evidence 可以展示，但必须明确证据状态；
- 任何迁移不得公开内部路径、账号、原始配置、截图或私有知识库内容；
- API slug 不存在时返回统一404；
- 数据引用错误必须在发布验证或启动加载阶段失败，不返回部分内容。

## 12. 测试范围

后端至少覆盖：

- schema 4.0 领域模型序列化与不可变性；
- 2.0、3.0、4.0 加载兼容；
- 未知版本拒绝；
- Collection 重复、缺失和悬空引用；
- `featuredCaseIds` 跨 Project、重复和超限；
- `INVESTIGATED` 映射与验证强度独立性；
- Project `caseCount` 和 `featuredCases` 投影；
- Case `projectSlug` 与 `collectionSlugs` 投影；
- Project、Case API 和统一404；
- 被移除 Project 引用迁移；
- 发布包结构验证；
- Agent 检索能通过 Collection 术语找到具体 Case，但不生成 Collection 主体；
- 隐私检查与完整发布门禁。

前端实现 AI 至少补齐：

- `/projects` 只出现5个 Project；
- 双主线同时可见；
- 4个主要项目与1个次级项目层级明确；
- 活动系统精选6条并能查看全部24条；
- Case 状态、Project、Collection、类型组合筛选；
- URL query 恢复与非法值回退；
- 15条实际排查不再显示为学习；
- `CASE-45` 仍为学习；
- 三个旧 Project URL 重定向；
- 独立 Case、无 Evidence、无相关 Case；
- 键盘、焦点、窄屏和 Reduced Motion。

## 13. 前端设计背景与非目标

前端设计必须延续暖米色、深墨色、低饱和红色和编辑式工程档案风格，保留现有全局导航。状态、贡献和证据强度不能只靠颜色表达。

本轮不要求：

- 重做首页与全局视觉系统；
- 新增 Collection 详情页；
- 新增服务端 Case 搜索或筛选接口；
- 为每个 Project 强制制造 Case；
- 把全部 Case 改造成重型故事页；
- 把学习型 Agent MVP 表述为生产系统；
- 把协作排查表述为独立交付；
- 让 Case 上一条／下一条保持复杂筛选上下文。

## 14. 验收结果

实现完成后应满足：

1. `/projects` 只展示5个 Project，不再重复收录49个 Case。
2. Java 后端与 Agent 两条主线同时可见。
3. 4个主要项目、1个次级项目及其成熟度表达真实。
4. Project 的 Case 数量由真实关系计算。
5. 活动系统展示6个精选 Case，并可查看全部24个。
6. `/cases` 能组合筛选状态、Project、Collection 和类型。
7. 实际排查、学习整理和证据强度不再混为一谈。
8. 三个主题 Project 完整迁移为 Collection，旧页面可达新入口。
9. schema 2.0、3.0继续兼容，4.0非法引用拒绝。
10. 后端测试、API测试、发布包验证和隐私门禁全部通过。
