# 模块化单体后端统一审核文档

**日期：** 2026-07-16
**审核范围：** 后端模块化单体重构与后端专项验证
**分支：** `codex/modular-monolith-refactor`
**基线：** `a144c7f`（`chore: establish backend baseline`）

## 1. 审核结论边界

本轮重构把现有 Spring Boot 后端整理为 `common / portfolio / answer` 三个顶层模块，关闭 Portfolio Web DTO 反向依赖和 Answer 跨模块领域类型泄漏，并用独立架构脚本固化依赖规则。

以下产品行为保持不变：

- 仍是一个 Maven 模块、一个 Spring Boot 进程；
- 仍读取审核后的 `public-data/public-portfolio.v1.json`；
- 仍提供 `GET /api/v1/portfolio`、`GET /api/v1/projects/{slug}`、`POST /api/v1/answers`；
- 仍只支持当前规范问题及有限等价问法；
- 仍由确定性引擎生成背景、职责、方案、验证、状态五段回答；
- 仍只返回已批准且不公开原始内容的 Evidence；
- 仍不连接大模型、不读取私有 Obsidian、不保存或记录访客问题。

本轮没有实现动态发布、Claim、TimelineEvent、公开 RAG、模型表达、数据库、SSE 或服务端会话。这些决策继续以动态公开知识设计为准。

## 2. 最终包树与职责

```text
com.portfolio.agent
├─ PortfolioAgentApplication
├─ common
│  ├─ exception        跨模块异常契约
│  └─ web              全局异常处理与 SPA 转发
├─ portfolio
│  ├─ controller       作品集 HTTP 路由
│  ├─ dto.response     作品集公开响应 DTO
│  ├─ service          作品集查询用例
│  │  └─ model         Service 自有查询结果
│  ├─ domain           公开作品集事实模型
│  ├─ repository       作品集仓储接口
│  │  └─ file          classpath JSON 仓储实现
│  ├─ mapper           Service/Domain 到响应 DTO 的映射
│  ├─ validation       公开快照不变量校验
│  └─ exception        Portfolio 业务错误
└─ answer
   ├─ controller       回答 HTTP 路由与请求校验
   ├─ dto.request      回答请求 DTO
   ├─ dto.response     Answer 自有响应 DTO
   ├─ service          回答用例编排
   ├─ domain           Answer 自有知识、结果与 Evidence
   ├─ engine           回答引擎接口与 QuestionNormalizer
   │  └─ deterministic 当前确定性实现
   ├─ gateway          Answer 所需的作品集知识端口
   ├─ adapter
   │  └─ portfolio     Portfolio 到 Answer 的本地翻译适配器
   ├─ mapper           AnswerResult 到响应 DTO 的映射
   └─ exception        Answer 业务错误
```

`common` 只保存共享机制，不保存 Project、Evidence 或 Answer 等业务实体。旧的 `api / application / infrastructure / domain.model / domain.repository` 包已经从 Portfolio 和 Answer 中移除。

## 3. 依赖方向

### 3.1 Portfolio

```text
PortfolioController
→ PortfolioService
→ PublicPortfolioRepository
→ PortfolioSnapshot

PortfolioController
→ PortfolioResponseMapper
→ portfolio.dto.response
```

`PortfolioService` 返回 `PortfolioOverview` 或 `ProjectDetails`，不返回 Response DTO。`getPortfolio()` 和 `getProject()` 都在一次调用内只执行一次 `repository.getSnapshot()`；项目详情的 Project、Evidence 和推荐问题因此来自同一个不可变快照。

### 3.2 Answer

```text
AnswerController
→ AnswerService
→ PortfolioKnowledgeGateway
→ LocalPortfolioKnowledgeAdapter
→ PublicPortfolioRepository
→ AnswerKnowledge
→ AnswerEngine
→ DeterministicAnswerEngine
→ AnswerResult
→ AnswerResponseMapper
```

`AnswerService`、Answer Domain、Engine 和 Gateway 都不 import Portfolio 包。`LocalPortfolioKnowledgeAdapter` 是唯一同时 import Portfolio 与 Answer 类型的位置；它从一个快照筛选已批准 Evidence，并把 Portfolio 类型转换为 `AnswerKnowledge / AnswerQuestion / AnswerEvidence`。

`QuestionNormalizer` 位于 `answer.engine`，供确定性引擎直接使用，避免 Engine 反向依赖 Service。`DeterministicAnswerEngine` 只接收 `AnswerKnowledge`，不读取 Repository，也不知道 Portfolio 类型。`AnswerResult` 持有 Answer 自己的 `AnswerEvidence`，HTTP 层再映射为 `AnswerEvidenceResponse`。

## 4. 当前为什么不用 Feign

当前 Portfolio 与 Answer 位于同一 JVM、同一 Spring 容器和同一部署单元。模块通信通过普通 Java Gateway 完成：

```text
PortfolioKnowledgeGateway
└─ LocalPortfolioKnowledgeAdapter
```

当前 `backend/pom.xml` 没有 Spring Cloud OpenFeign、Spring HTTP Service Client 或其他 HTTP 客户端依赖，代码也没有通过 localhost 调用自身 Controller。此时引入 Feign 只会增加序列化、网络失败、超时、重试和端口配置，并让 Service 绑定 HTTP 契约。

如果未来出现独立部署、独立扩容或明确团队边界，保留 `PortfolioKnowledgeGateway`，只替换 Adapter：

```text
当前：
PortfolioKnowledgeGateway
└─ LocalPortfolioKnowledgeAdapter

未来：
PortfolioKnowledgeGateway
└─ HttpPortfolioKnowledgeAdapter
   └─ PortfolioHttpClient
```

远程客户端实现可以在届时根据团队基础设施选择 Spring HTTP Service Client 或 Feign，但不得让 Answer Service、Domain 或 Engine 直接依赖具体客户端。

## 5. 关键类映射

| 职责 | 重构后关键类 |
|---|---|
| 作品集 HTTP 边界 | `portfolio.controller.PortfolioController` |
| 作品集查询用例 | `portfolio.service.PortfolioService` |
| Service 查询结果 | `portfolio.service.model.PortfolioOverview`、`ProjectDetails` |
| 作品集仓储端口 | `portfolio.repository.PublicPortfolioRepository` |
| JSON 文件仓储 | `portfolio.repository.file.JsonPublicPortfolioRepository` |
| 快照校验 | `portfolio.validation.PortfolioSnapshotValidator` |
| 作品集响应映射 | `portfolio.mapper.PortfolioResponseMapper` |
| 回答 HTTP 边界 | `answer.controller.AnswerController` |
| 回答用例编排 | `answer.service.AnswerService` |
| Answer 知识端口 | `answer.gateway.PortfolioKnowledgeGateway` |
| 本地跨模块适配 | `answer.adapter.portfolio.LocalPortfolioKnowledgeAdapter` |
| 引擎契约 | `answer.engine.AnswerEngine` |
| 问题归一化 | `answer.engine.QuestionNormalizer` |
| 确定性引擎 | `answer.engine.deterministic.DeterministicAnswerEngine` |
| Answer 响应映射 | `answer.mapper.AnswerResponseMapper` |
| 共享错误转换 | `common.web.GlobalExceptionHandler` |

## 6. 架构门禁

`scripts/architecture-check.ps1` 扫描生产与测试 Java 的 package/import 语句及代码体中的 `com.portfolio.agent...` 全限定引用，并阻断：

- `common` 依赖 Portfolio 或 Answer；
- Portfolio Service 依赖 Controller 或 DTO；
- Answer Service、Domain、Engine、Gateway 依赖 Portfolio；
- `answer.adapter.portfolio` 之外的 Answer 代码依赖 Portfolio；
- 本地 Portfolio Adapter 越过允许的 Portfolio Domain 与 Repository 接口；
- Answer Engine 依赖 `answer.engine`、`answer.domain` 之外的任何项目内部包；
- Portfolio/Answer Controller 依赖 Repository、Adapter、Engine 或 Validation；
- Portfolio 依赖 Answer；
- Java 声明的 package 与 Maven source-set 内的文件目录不一致；
- 旧 `api / application / infrastructure / domain.model / domain.repository` 包重新出现。

脚本会先处理 Java Unicode escape，并忽略注释、字符串、字符字面量和 text block；随后提取 normalized package、完整 package/import 语句，并从剩余代码体提取全限定引用。全部依赖规则都以声明 package 判断来源模块与层，不使用文件路径分类。Maven `main/java` 与 `test/java` 下的文件目录必须与 package path 一致，默认 package 也会被拒绝；不一致输出 `package-path-mismatch:file:line:package ...;`。两类依赖引用都统一归一化空白与点号，package/import 已处理区域会从代码体扫描中排除，避免重复报告。这样既能阻断错放源码、多行 import、静态 import、Unicode 转义和代码体全限定类名绕过，也不会把示例文本误判为真实依赖。代码体全限定引用的违规输出为 `rule:file:line:normalized-reference`。

`answer-engine-boundary` 对项目内部引用采用白名单：`answer.engine` 只允许依赖 `answer.engine` 与 `answer.domain`。JDK、Spring 注解等非 `com.portfolio.agent` 引用不属于该规则的检查范围。

该架构检查当前作为独立后端门禁运行；把它接入 `verify-release.ps1` 的完整发布流水线明确延期。

## 7. 测试与验证证据

本次只声明后端专项验证，不声明发布就绪。验证命令与最终结果在 Task 8 执行后记录如下：

| 门禁 | 命令 | 结果 |
|---|---|---|
| 代码质量脚本自测 | `scripts/code-quality-check.test.ps1` | 通过，exit 0 |
| 后端源码质量扫描 | `scripts/code-quality-check.ps1 -Path backend/src` | 通过，exit 0 |
| 架构脚本自测 | `scripts/architecture-check.test.ps1` | 通过，exit 0 |
| 后端架构扫描 | `scripts/architecture-check.ps1 -Path backend/src` | 通过，exit 0 |
| 聚焦 Normalizer/Engine/Controller | 指定 4 个 Normalizer、Engine、Controller 测试类 | `BUILD SUCCESS`，17/17 通过 |
| 后端全量 JUnit | `mvn.cmd -f backend/pom.xml test` | `BUILD SUCCESS`，51/51 通过 |
| 后端编译 | `mvn.cmd -f backend/pom.xml -DskipTests compile` | `BUILD SUCCESS` |
| 隐私脚本自测 | `scripts/privacy-check.test.ps1` | 通过，exit 0 |
| 公开快照扫描 | `scripts/privacy-check.ps1 -Path backend/src/main/resources/public-data` | 1 个文件通过，exit 0 |
| 包树检查 | 递归列出 `com.portfolio.agent` 目录并检查旧目录 | 顶层为 `common / portfolio / answer`；无旧目录 |
| 依赖方向检查 | 检索 Answer 核心与 Portfolio Service 禁止 import | 无输出、exit 0 |
| Maven 依赖树 | `dependency:tree` | `BUILD SUCCESS`；未包含 Feign 或 Spring Cloud |
| 路由检查 | 枚举 Controller Mapping 注解 | 三个公开 API 路由与 SPA 项目页转发保持存在 |

当前终端没有把 `mvn.cmd` 配置到 `PATH`，因此本轮 Maven 命令使用本机 Maven Wrapper 缓存中的 Maven 3.9.14 可执行文件运行；`pom.xml`、目标、测试筛选条件与 Task 8 要求一致。聚焦测试命令的 `-Dtest` 值在 PowerShell 中加引号，避免逗号被解释为参数分隔符。

## 8. 与动态知识快照、Claim 和 RAG 的衔接

本轮边界为后续设计提供以下替换点，但没有提前实现未来功能：

- `PublicPortfolioRepository` 可以从当前 `JsonPublicPortfolioRepository` 演进为版本化文件仓储，再演进为数据库仓储；
- Service 查询结果与 Web DTO 已分离，后续加入 Claim、TimelineEvent、首页计算指标和展示编排时，不需要让 Service 依赖 Controller；
- `PortfolioKnowledgeGateway` 隔离 Answer 与 Portfolio 事实模型，后续可以把 `AnswerKnowledge` 扩展为受控 Answer Context；
- `AnswerEngine` 隔离回答编排与具体实现，后续可以在保持确定性兜底的同时接入 Claim 检索、公开 RAG 和模型表达；
- `LocalPortfolioKnowledgeAdapter` 适合作为版本一致性和公开事实翻译边界，后续仍必须保证单次请求只使用一个 `contentVersion`；
- Claim 的事实状态、贡献类型与 Evidence 支撑关系仍由 Portfolio 公开事实拥有，模型不得创造或改写这些事实；
- 文件发布包继续是动态发布、回滚、数据库导入导出和灾难恢复的设计基础。

动态发布设计中已经确认的独立发布、审核状态、Claim、混合检索、模型/Embedding 端口、数据库后续迁移等决策均未被本轮文档改写。

## 9. 已明确延期项

以下内容没有在 Task 8 中执行或验证：

- 前端源代码、前端单元/组件测试、前端生产构建；
- Maven `package`、可执行 JAR 内容与启动检查；
- Playwright、浏览器/API 集成；
- Dockerfile 检查、镜像构建与容器运行；
- `verify-release.ps1` 完整发布流水线；
- 动态外部发布包、active 指针、发布/回滚工具；
- Claim、TimelineEvent、展示编排、公开 RAG、模型与 Embedding；
- 数据库、审核后台、服务端会话、SSE 与多实例协调。

原因是前端正在独立大规模重构，本任务仅对后端包边界和后端契约负责。上述延期项不得被描述为已通过或已发布。

## 10. 提交列表

本分支从后端基线起的实现与门禁关键提交如下；列表不包含 `a567e48` 这类仅用于保持本地 SDD 报告 ignored 的 housekeeping 提交：

| 提交 | 内容 |
|---|---|
| `82455c1`、`2f12545` | 新增并修正架构依赖门禁 |
| `613ee04` | 移动 Portfolio Domain、Repository、Validation、Exception 与 DTO |
| `8de2f80` | 关闭 Portfolio Service 到 Response DTO 的反向依赖 |
| `58fd793` | 增加 Answer 自有知识模型、Gateway 与本地 Portfolio Adapter |
| `274ae54` | 重排 Answer 核心并隔离确定性引擎 |
| `c32b29d`、`93d0f5d`、`5c3a255` | 移动 Answer Web 边界、增加 Answer Evidence DTO、收紧 Adapter 例外 |
| `93f2da7` 至 `5b86d51` | 删除旧包，并加固多行 package/import、静态 import、text block、注释与 Unicode escape 检查；全部依赖规则统一使用完整 normalized statement |
| `e34df41` | 覆盖代码体全限定引用，强化 Controller/Engine 依赖方向，并将 `QuestionNormalizer` 移入 Engine 边界 |
| `7c8d904` | 将 Answer Engine 的项目内部依赖改为 `answer.engine / answer.domain` 白名单 |
| `8a6bdf9` | 使用声明 package 分类依赖规则，并阻断 package 与 Maven 源码目录不一致 |

Task 8 文档建议提交信息：

```text
docs: align modular monolith backend documentation
```

## 11. 用户审核清单

- [ ] 是否认可 `common / portfolio / answer` 三个顶层模块；
- [ ] 是否认可 `controller / service / domain / repository / mapper / validation / engine / gateway / adapter` 命名；
- [ ] 是否认可当前只使用进程内 Java Gateway，不引入 Feign 或远程自调用；
- [ ] 是否认可唯一跨模块翻译点为 `answer.adapter.portfolio`；
- [ ] 是否确认公共 API 与当前确定性回答行为保持不变；
- [ ] 是否确认动态发布、Claim、RAG、数据库等既有设计决策继续保留；
- [ ] 是否接受前端、JAR/package、Playwright、Docker、浏览器集成和发布就绪验证延期；
- [ ] 是否同意后续把独立架构门禁接入完整发布流水线；
- [ ] 是否同意使用建议提交信息提交 Task 8 文档。
