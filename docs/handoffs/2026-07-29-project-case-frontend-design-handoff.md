# Project—Case 前端设计 Handoff
<!-- DOCUMENT_STATUS: HISTORICAL -->

**日期：** 2026-07-29

**面向：** 后续负责具体前端视觉与交互方案的 AI

**产品规格：** `docs/superpowers/specs/2026-07-29-project-case-information-architecture-design.md`

## 1. 任务目标

为公开工程作品集重新设计 `/projects`、Project 详情、`/cases` 和 Case 详情的必要增量，使 Project 成为长期主线总览、Case 成为具体工作下钻入口，消除两个一级目录重复收录49个 Case 的问题。

本任务不是全站改版。保留现有品牌、全局导航和编辑式工程档案气质。

## 2. 主要用户

主要用户是技术面试官，典型任务：

1. 在30–90秒内判断候选人的技术方向、实际工程范围、贡献方式和成熟度；
2. 从某个 Project 下钻到具体 Case，检查问题处理与验证过程；
3. 直接从 Case 目录按状态、归属和类型检索；
4. 继续进入 Agent，围绕当前 Project 或 Case 追问。

候选人同时以 Java 后端和 Agent 为主求职方向：

- Java 技术栈和真实工程经历更成熟；
- Agent 仍在学习，但已经形成打通多种概念的最小集成 MVP；
- 两条方向必须并列可见，不能把 Agent 隐藏为附属入口；
- 成熟度必须诚实区分。

## 3. 现有设计基础

先阅读并沿用：

- `frontend/src/app/styles/tokens.css`
- `frontend/src/app/styles/main.css`
- `frontend/src/shared/components/PageLead.vue`
- `frontend/src/shared/components/DossierHeader.vue`
- `frontend/src/pages/ProjectsPage.vue`
- `frontend/src/pages/ProjectPage.vue`
- `frontend/src/pages/CasesPage.vue`
- `frontend/src/pages/CasePage.vue`

现有视觉语言：

- 暖米色纸张背景；
- 深墨色正文与深色反白段；
- 低饱和红色强调；
- 衬线标题与等宽编号；
- 编辑式档案、案卷和索引气质；
- 克制的边框、留白与状态符号。

禁止引入第二套品牌、SaaS Dashboard 外观、高饱和科技渐变、大量装饰图表或无意义图标。

## 4. 内容结构

### 4.1 主要 Project

Java 后端：

1. SQL审计与故障排查工具
2. 活动系统工程实践
3. 测试角色重置工具

Agent：

4. Agent能力集成MVP

次级 Project：

5. 图片上传与审计

### 4.2 Case Collection

以下内容不再作为 Project：

1. 开源项目体验与测试
2. 工程操作与学习
3. 技术写作与分享

Collection 只作为 `/cases` 的浏览筛选，不设计独立详情页。

### 4.3 Case 状态

必须能区分：

1. 已交付
2. 已排查／参与处理
3. 原型验证
4. 学习整理

状态与证据强度是两个维度。缺截图不等于学习，部分验证也不等于未实际工作。

## 5. `/projects` 设计要求

页面名称使用“项目主线”。

硬性要求：

- Java 后端和 Agent 两条主线同时处于第一层视野；
- 不使用默认 Tab 隐藏其中一条；
- 4个主要 Project 与1个次级 Project 层级清楚；
- 不渲染完整 Case 列表；
- 排序固定，不按数量自动排名；
- Project 没有关联 Case 时不显示“0个案例”。

每个 Project 卡片必须表达：

- 求职方向；
- 项目性质；
- 成熟度；
- 贡献方式；
- 一句话目标或结果；
- 核心技术；
- 大于零时的关联 Case 数量；
- 进入 Project 详情的明确操作。

设计 AI 可以探索纵向分区、双列主线、编辑式索引等方案，但不得改变上述信息优先级。

## 6. Project 详情设计要求

保留现有叙事：

```text
为什么做
我的职责
如何做
如何证明
最终状态
```

新增“相关案例”区：

- 0个：不显示该区；
- 1–3个：全部显示；
- 超过3个：显示3–6个精选案例；
- 有更多案例时提供“查看全部 N 个案例”；
- 摘要只显示问题、类型、工作状态和贡献方式；
- 不复制 Case 的完整正文。

活动系统精选6个：

1. 活动开发流程与红点设计
2. 重复配置导致唯一性冲突
3. 结束时间与旧客户端双重过滤
4. 环境结构缺失字段
5. 礼包积分被配置覆盖
6. 定制内容展示排查

“询问这个项目”继续携带 Project 上下文。

## 7. `/cases` 设计要求

需要为49个 Case 提供三层组合筛选：

### 工作状态

- 全部
- 已交付
- 已排查／参与处理
- 原型验证
- 学习整理

### 归属范围

- 全部
- 某个 Project
- 某个 Collection
- 独立案例

### Case 类型

- 功能任务
- 问题处理
- 工具评测

可以增加标题关键词搜索。

交互要求：

- 直接进入 `/cases` 默认显示已交付；
- 从 Project 进入时显示该 Project 的全部 Case；
- 当前筛选条件和数量始终可见；
- URL query 可以恢复状态；
- 非法 query 回退到安全默认；
- 移动端可以收起筛选器，但不能让用户忘记当前条件。

每条 Case 至少显示：

- 标题与问题摘要；
- Case 类型；
- 工作状态；
- 贡献方式；
- 所属 Project 或“独立案例”；
- 可选 Collection 标签；
- 进入详情的明确操作。

不要把 Case 列表设计成缩小版 Project 卡片墙。

## 8. Case 详情设计要求

保留现有正文结构：

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

补充表达：

- 所属 Project 和返回入口；
- 无 Project 时的“独立案例”；
- 关联 Collection；
- 工作状态；
- 与工作状态分离的证据强度；
- “询问本案例”的明确入口。

前端只提交 `caseSlug` 作为主要 Agent 上下文，不同时制造 Project 与 Case 两个竞争主体。

## 9. 旧入口

设计和实现需覆盖三个旧 Project URL 转向 Case Collection 筛选结果：

```text
/projects/context-engineering-evaluation
/projects/technical-writing
/projects/engineering-delivery-learning
```

重定向目标见产品规格。

## 10. 必须设计的状态

至少提供以下桌面与窄屏状态：

- Project 主线正常数据；
- 某一主线为空；
- Project 无 Case；
- Project 有1–3个 Case；
- Project 有大量 Case；
- Case 目录默认已交付；
- Project 筛选生效；
- Collection 筛选生效；
- 多条件组合后无结果；
- 独立 Case；
- Case 无公开 Evidence；
- 加载、失败和重试；
- 键盘焦点；
- Reduced Motion。

## 11. 可访问性与内容真实性

- 状态、贡献和证据强度不能只用颜色区分；
- 筛选器具有可理解的标签、选中状态和键盘行为；
- 点击区域、焦点环和阅读顺序清楚；
- 窄屏放大后不能丢失筛选上下文；
- 不把协作参与写成独立完成；
- 不把排查定位写成已修复；
- 不把学习型 Agent MVP 写成生产平台；
- 不因为缺截图把实际工作标成学习。

## 12. 交付物

前端设计 AI 应交付：

1. `/projects` 桌面与窄屏方案；
2. Project 详情相关案例区的0、少量、大量三种状态；
3. `/cases` 桌面与窄屏筛选方案；
4. Case 行或卡片的字段层级；
5. Case 详情新增元信息区；
6. 加载、错误、空结果和非法筛选回退；
7. 关键交互说明；
8. 与现有设计系统的复用说明；
9. 实现 AI 可直接执行的组件与页面变更清单。

具体设计需要单独评审通过后才能进入前端生产代码。
