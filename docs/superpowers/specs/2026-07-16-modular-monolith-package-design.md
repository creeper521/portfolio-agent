# 后端模块化单体与包命名设计

**日期：** 2026-07-16  
**状态：** 待用户书面审核  
**适用项目：** `D:\code\agent`

## 1. 目标

将当前后端整理为边界清晰、命名直观的模块化单体：

- 继续使用一个 Maven 模块、一个 Spring Boot 进程、一个 JAR 和一个 Docker 镜像；
- 顶层按业务划分为 `common / portfolio / answer`；
- 模块内部使用常见 Spring Boot 命名；
- 修复当前跨层反向依赖和 Answer 对 Portfolio 内部模型的泄漏；
- 为后续 Claim、RAG、动态发布和数据库演进提供稳定边界；
- 为未来按需拆分 Portfolio Service 与 Answer Service 保留替换点；
- 当前不引入 Feign、服务发现、分布式事务或其他微服务基础设施。

## 2. 选定结构

```text
com.portfolio.agent
├─ PortfolioAgentApplication.java
├─ common
│  ├─ config
│  ├─ exception
│  └─ web
├─ portfolio
│  ├─ controller
│  ├─ dto
│  │  └─ response
│  ├─ service
│  ├─ domain
│  ├─ repository
│  │  └─ file
│  ├─ mapper
│  ├─ validation
│  └─ exception
└─ answer
   ├─ controller
   ├─ dto
   │  ├─ request
   │  └─ response
   ├─ service
   ├─ domain
   ├─ engine
   │  └─ deterministic
   ├─ gateway
   ├─ adapter
   │  └─ portfolio
   ├─ mapper
   └─ exception
```

只创建当前功能实际需要的包。未来目录不提前建立空包。

## 3. 命名映射

| 当前包 | 目标包 |
|---|---|
| `portfolio.api` | `portfolio.controller` |
| `portfolio.api.dto` | `portfolio.dto.response` |
| `portfolio.application` | `portfolio.service` |
| `portfolio.application.exception` | `portfolio.exception` |
| `portfolio.domain.model` | `portfolio.domain` |
| `portfolio.domain.repository` | `portfolio.repository` |
| `portfolio.infrastructure.json` | `portfolio.repository.file` 与 `portfolio.validation` |
| `answer.api` | `answer.controller` |
| `answer.api.dto` | `answer.dto.request` 与 `answer.dto.response` |
| `answer.application` | `answer.service`、`answer.engine` 与 `answer.gateway` |
| `answer.domain.model` | `answer.domain` |
| `answer.infrastructure.deterministic` | `answer.engine.deterministic` |

`api / application / infrastructure` 被替换为更贴近日常 Spring Boot 开发的名称，但依赖方向仍然按模块边界严格控制。

## 4. Portfolio 模块

### 4.1 Controller

负责 HTTP 路由、参数绑定和响应映射：

```text
PortfolioController
TimelineController（后续）
EvidenceController（后续）
```

Controller 只能调用 Service 和响应 Mapper，不得直接访问 Repository 或文件实现。

### 4.2 Service

负责作品集查询用例：

```text
PortfolioService
```

Service 返回 Portfolio 自有的查询结果或 Domain 对象，不返回 Web Response DTO。

一次查询只能读取一次内容快照。同一请求中的 Project、Claim、Evidence、Timeline 和版本信息必须来自同一个不可变内容版本。

### 4.3 Domain

Portfolio 拥有全部公开事实：

```text
PortfolioSnapshot
Project
Claim（后续）
Evidence
TimelineEvent（后续）
QuestionPreset
ProjectStatus
ContributionType
ContentVersion（后续）
```

Answer 不得直接把这些类型作为自己的领域模型或结果字段。

### 4.4 Repository

存储抽象使用 `repository`，不使用 `dao`。当前数据来自文件而非数据库，DAO 容易错误暗示 SQL 或 MyBatis 访问。

```text
portfolio.repository.PublicPortfolioRepository
portfolio.repository.file.JsonPublicPortfolioRepository
```

后期可以增加：

```text
portfolio.repository.file.VersionedFilePublicContentRepository
portfolio.repository.database.DatabasePublicContentRepository
```

Service 只依赖 Repository 接口。

### 4.5 Mapper

Mapper 负责：

```text
Portfolio 查询结果 / Domain
→ Portfolio Response DTO
```

它不表示 MyBatis Mapper。未来数据库 Mapper 必须放在：

```text
portfolio.repository.database.mapper
```

### 4.6 Validation

文件格式、哈希、Schema 解析与公开事实业务不变量需要区分职责：

- Repository File：读取文件和反序列化；
- Validation：引用完整性、状态、Evidence 和公开边界；
- Domain/Service：与查询用例直接相关的业务规则。

第一阶段可以保留统一校验入口，但内部规则必须按上述类别组织。

## 5. Answer 模块

### 5.1 Controller

`AnswerController` 负责请求校验、调用 AnswerService 和响应映射，不直接调用 Portfolio、Repository、模型供应商或确定性引擎实现。

### 5.2 Service

`AnswerService` 负责一次完整回答用例：

```text
解析请求
→ 通过 Gateway 获取回答知识
→ 调用 AnswerEngine
→ 返回 AnswerResult
```

它不读取 `PortfolioSnapshot`，不检查 Portfolio 内部对象，也不依赖具体 HTTP Client。

### 5.3 Domain

Answer 拥有自己的领域模型：

```text
AnswerKnowledge
AnswerClaim
AnswerEvidence
AnswerContext（后续）
AnswerResult
AnswerSection
AnswerMode
FallbackReason（后续）
```

Answer Domain 不导入 Portfolio Domain。

### 5.4 Engine

```text
answer.engine.AnswerEngine
answer.engine.deterministic.DeterministicAnswerEngine
```

Engine 接收已经准备好的 `AnswerKnowledge` 或 `AnswerContext`，不直接访问 Repository。

确定性引擎只负责：

- 问题匹配；
- Claim 或字段排序；
- 回答段落构造；
- 边界回答；
- 确定性兜底。

数据读取、Portfolio 转换和 Evidence 公开过滤不放在 Engine。

### 5.5 Gateway

Answer 在自身模块定义需要的能力：

```java
public interface PortfolioKnowledgeGateway {
    AnswerKnowledge getKnowledge(AnswerKnowledgeQuery query);
}
```

Gateway 表达业务需要，不表达 URL、HTTP Method、Feign 或文件结构。

### 5.6 Adapter

单体阶段使用本地适配器：

```text
answer.adapter.portfolio.LocalPortfolioKnowledgeAdapter
```

它是当前唯一同时理解 Answer 与 Portfolio 的位置，负责：

- 调用 Portfolio Repository 或公开查询接口；
- 读取一个一致内容版本；
- 过滤公开 Claim 和 Evidence；
- 将 Portfolio 类型转换成 Answer 自有类型。

禁止以下依赖：

```text
answer.service → PortfolioSnapshot
answer.domain → portfolio.domain
answer.engine → PublicPortfolioRepository
answer.dto → portfolio.dto
```

## 6. 依赖方向

### Portfolio

```text
portfolio.controller
  → portfolio.service
  → portfolio.domain
  → portfolio.repository（接口）

portfolio.repository.file
  → portfolio.repository
  → portfolio.domain

portfolio.controller
  → portfolio.mapper
  → portfolio.dto.response
```

### Answer

```text
answer.controller
  → answer.service
  → answer.domain
  → answer.engine
  → answer.gateway

answer.engine.deterministic
  → answer.engine
  → answer.domain

answer.adapter.portfolio
  → answer.gateway
  → answer.domain
  → portfolio.repository / portfolio.domain
```

`common` 不依赖 Portfolio 或 Answer，也不保存共享业务实体。

## 7. Feign 决策

当前模块化单体内部不使用 Feign。

单体内调用使用：

```text
PortfolioKnowledgeGateway
→ LocalPortfolioKnowledgeAdapter
→ Portfolio
```

不使用：

```text
Answer
→ Feign
→ localhost HTTP
→ PortfolioController
```

原因：

- 避免单体内部无意义的序列化和网络开销；
- 避免 Service 依赖 HTTP 契约；
- 避免人为引入超时、重试、连接池和端口依赖；
- 保持单元测试和本地调试简单；
- Gateway 已足以建立可替换边界。

未来真正拆分独立进程后，保留 Gateway，替换 Adapter：

```text
当前：
PortfolioKnowledgeGateway
└─ LocalPortfolioKnowledgeAdapter

未来：
PortfolioKnowledgeGateway
└─ HttpPortfolioKnowledgeAdapter
   └─ PortfolioHttpClient
```

远程客户端优先评估 Spring HTTP Service Client。若团队基础设施统一采用 Feign，可以在远程 Adapter 内使用 Feign，但 Answer Service 和 Domain 不得直接依赖 Feign Client。

## 8. 微服务演进边界

Portfolio 拥有：

- Project、Claim、Evidence、TimelineEvent；
- 公开内容版本；
- 内容查询和发布数据；
- 后期 Portfolio 数据库表。

Answer 拥有：

- AnswerContext、AnswerResult；
- 检索和回答编排；
- 模型与确定性兜底；
- 后期服务端会话及其数据库表。

即使单体阶段共用一个数据库，也禁止跨模块直接读写对方表。模块通过 Gateway 或明确查询接口协作。

只有出现独立部署、独立扩容、独立团队、明确网络边界等实际需求时才拆微服务。当前不提前引入服务发现、负载均衡、熔断、分布式事务或共享 API 工程。

## 9. 重构后的请求链路

### Portfolio

```text
PortfolioController
→ PortfolioService
→ PublicPortfolioRepository
→ Portfolio 查询结果
→ PortfolioResponseMapper
→ Response DTO
```

### Answer

```text
AnswerController
→ AnswerService
→ PortfolioKnowledgeGateway
→ LocalPortfolioKnowledgeAdapter
→ AnswerKnowledge
→ AnswerEngine
→ AnswerResult
→ AnswerResponseMapper
→ Response DTO
```

## 10. 验收标准

- 顶层只使用 `common / portfolio / answer` 业务模块；
- 当前行为、API URL、JSON 字段和公开内容不变；
- `portfolio.service` 不依赖 controller 或 response DTO；
- `answer.service/domain/engine` 不依赖 Portfolio 内部类型；
- `DeterministicAnswerEngine` 不读取 Repository；
- Portfolio 跨模块访问集中在一个 Local Adapter；
- 当前工程不新增 Feign 或 Spring Cloud 依赖；
- Controller 不直接访问 Repository；
- Common 不包含业务模型；
- 架构检查可以自动阻断反向依赖；
- 全部现有测试和发布验证继续通过。

## 11. 非目标

- 本次重构不实现 Claim、RAG、动态发布或数据库；
- 不拆 Maven 模块或微服务；
- 不修改前端；
- 不改变公开 API；
- 不提前创建未来空包；
- 不引入 Feign、HTTP Service Client、服务发现或远程调用。
