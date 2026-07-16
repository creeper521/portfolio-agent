# 实习作品集 Agent：代码架构重构设计

> 文档状态：已完成对话设计确认，待用户书面审阅  
> 日期：2026-07-14  
> 实际修改项目：`D:\code\agent`  
> 只读参考项目：`D:\code\d11_server`

## 1. 背景

V0 已完成公开作品集纵向切片，具备作品集首页、项目详情、确定性问答、隐私扫描、前后端自动测试、Playwright 验收和单 JAR 交付能力。当前问题不在功能可用性，而在代码继续扩展前需要建立更稳定的结构：

- Java 生产代码和测试代码存在较多 `var`；
- 16 个数据类型使用 `record`，不符合本项目强调显式结构和长期维护的约束；
- 后端目前按 `api`、`service`、`domain`、`content`、`answer` 等技术目录平铺，作品集查询和问答边界不够清楚；
- 异常类散落在 `service`、`content` 和 `api`；
- 一个 Controller 同时承担作品集查询和问答入口；
- 前端仍是 V0 最小目录，组件、类型和 API 尚未按功能归属；
- 代码规范只有对话约定，缺少项目内文档和自动检查。

本次是行为保持型架构重构，不增加产品功能，不改变页面设计，不接入 DeepSeek、Spring AI、SSE、数据库或私有知识库。

## 2. 目标与非目标

### 2.1 目标

1. 保持单 Maven 模块，通过包边界实现业务模块优先、模块内部再分层。
2. 把作品集查询和问答拆成两个清晰的业务模块。
3. 把全部 `record` 转为显式普通不可变类。
4. 清除生产代码和测试代码中的 `var`，并禁止 Lombok。
5. 建立公共异常机制，同时让具体异常保留业务归属。
6. 把前端迁移到轻量 feature-first 结构，为后续页面改版预留清晰边界。
7. 新增项目代码约束文档和自动约束检查。
8. 保持现有 REST API、JSON 字段、URL、UI、交互和隐私边界不变。

### 2.2 非目标

- 不拆分多个 Maven 子模块；
- 不引入完整 DDD、完整 FSD、CQRS 或事件总线；
- 不引入 Lombok、MapStruct、ArchUnit、数据库或新的运行时框架；
- 不重写 CSS，不调整视觉设计；
- 不修改公开快照事实内容；
- 不修改只读参考项目 `D:\code\d11_server`；
- 不执行 Git stage、commit 或 push。

## 3. 参考项目结论

`d11_server` 的主要依赖方向是：

```text
d11-bean
   ↓
d11-data / d11-ev
   ↓
d11-enjoy（业务服务）
   ↓
d11-server / d11-web（接入与部署）
```

本项目借鉴以下原则：

- 依赖方向明确，接入层不直接访问数据层；
- Controller 只负责协议、校验和业务分发；
- Service/Application 层负责用例编排；
- 数据访问实现与业务模型分离；
- 按业务主题建立子目录；
- 公共错误机制、命名规则、流程图、自测和交付检查形成书面规范。

以下做法不照搬：

- 17 个 Maven 模块不适合当前规模；
- 不使用 `record`、`var` 或 Lombok；
- 不建立包含所有业务错误的巨型异常文件；
- 不使用注入大量依赖的 `BaseController`；
- 不使用字段注入；
- 不允许 Controller 或 Service 继续膨胀成大型公共基类。

## 4. 后端目标架构

### 4.1 目录结构

```text
backend/src/main/java/com/portfolio/agent/
├─ PortfolioAgentApplication.java
├─ common/
│  ├─ exception/
│  │  ├─ ApplicationException.java
│  │  ├─ ErrorCode.java
│  │  └─ CommonErrorCode.java
│  └─ web/
│     ├─ ApiErrorResponse.java
│     ├─ GlobalExceptionHandler.java
│     └─ SpaForwardController.java
├─ portfolio/
│  ├─ api/
│  │  ├─ PortfolioController.java
│  │  └─ dto/
│  ├─ application/
│  │  ├─ PortfolioQueryService.java
│  │  └─ exception/
│  │     ├─ PortfolioErrorCode.java
│  │     └─ ProjectNotFoundException.java
│  ├─ domain/
│  │  ├─ model/
│  │  └─ repository/
│  │     └─ PublicPortfolioRepository.java
│  └─ infrastructure/
│     └─ json/
│        ├─ JsonPublicPortfolioRepository.java
│        ├─ PortfolioSnapshotValidator.java
│        └─ InvalidPortfolioSnapshotException.java
└─ answer/
   ├─ api/
   │  ├─ AnswerController.java
   │  └─ dto/
   ├─ application/
   │  ├─ AnswerService.java
   │  ├─ AnswerEngine.java
   │  └─ QuestionNormalizer.java
   ├─ domain/
   │  └─ model/
   └─ infrastructure/
      └─ deterministic/
         └─ DeterministicAnswerEngine.java
```

测试目录镜像生产代码的包结构。测试类随被测类迁移，不建立另一套按测试类型平铺的目录。

### 4.2 模块职责

#### `common`

只保存多个业务模块共同需要的机制，不保存作品集或问答业务规则。禁止把暂时无法归类的代码放入 `common`。

#### `portfolio`

负责公开作品集快照、项目详情、Evidence 过滤和只读查询。它不依赖问答模块。

#### `answer`

负责问题规范化、问答用例编排和回答引擎。它可以通过 `PublicPortfolioRepository` 的只读端口读取已审核公开事实，但不得读取私有知识库或原始日报。

### 4.3 依赖方向

```text
portfolio.api → portfolio.application → portfolio.domain
portfolio.infrastructure → portfolio.domain

answer.api → answer.application → answer.domain
answer.infrastructure → answer.application + answer.domain
answer.application/infrastructure → portfolio.domain.repository

各业务模块 → common
common ─X→ 任何具体业务模块
portfolio ─X→ answer
```

API DTO 不得进入领域层；Repository 实现不得被 Controller 直接依赖；基础设施类不得成为 API 返回对象。

## 5. Controller 与数据流

现有 `PortfolioController` 拆分为：

- `PortfolioController`：处理 `GET /api/v1/portfolio` 和 `GET /api/v1/projects/{slug}`；
- `AnswerController`：处理 `POST /api/v1/answers`。

URL、HTTP 状态和 JSON 字段保持不变。

```text
公开作品集请求
→ PortfolioController
→ PortfolioQueryService
→ PublicPortfolioRepository
→ Portfolio DTO

问答请求
→ AnswerController
→ AnswerService
→ AnswerEngine
→ DeterministicAnswerEngine
→ PublicPortfolioRepository
→ Answer DTO
```

`AnswerService` 负责项目存在性检查和用例编排，确定性引擎只负责匹配与组织回答。未来 DeepSeek 实现应作为 `answer.infrastructure` 中的另一个 `AnswerEngine` 适配器接入，不能反向污染作品集模块。

## 6. 普通不可变类规范

全部生产和测试 Java 文件禁止 `record`、`var` 和 Lombok。

原 `record` 转换规则：

- 类型声明为 `final class`；
- 字段声明为 `private final`；
- 使用显式全参数构造器；
- 使用显式 JavaBean getter：`getXxx()`，布尔值使用 `isXxx()`；
- 不提供 setter；
- 集合使用 `List.copyOf()`、`Set.copyOf()`、`Map.copyOf()` 做防御性复制；
- 需要保留值语义的类型手动实现 `equals()`、`hashCode()` 和 `toString()`；
- API 或 JSON 反序列化需要时，在构造器上使用明确的 Jackson 注解，不依赖隐式参数名；
- 所有调用点由 `value()` 改为 `getValue()` 或 `isValue()`；
- 局部变量写出明确类型，包括流结果、请求 ID、快照、项目和 DTO。

本次不额外复制一套 JSON Document 模型。公开快照模型保持不可变，并通过显式构造器完成 Jackson 反序列化。若未来出现第二种持久化来源，再评估独立持久化模型和映射层。

## 7. 异常设计

采用“公共机制集中、具体异常归属模块”的方式：

- `ErrorCode`：错误码契约接口，定义稳定标识和默认公开文案；
- `CommonErrorCode`：只保存参数校验、方法不支持和系统错误等通用错误；
- `ApplicationException`：允许安全转换为公开 API 错误的公共基类，持有 `ErrorCode`；
- `PortfolioErrorCode`：保存 `PROJECT_NOT_FOUND` 等作品集模块错误，避免公共枚举演变成巨型业务错误表；
- `ProjectNotFoundException`：位于 `portfolio.application.exception`，继承公共基类；
- `InvalidPortfolioSnapshotException`：位于 `portfolio.infrastructure.json`，表示启动期数据损坏，不作为普通业务错误；
- `GlobalExceptionHandler`：位于 `common.web`，统一处理业务异常、校验错误、404、405、415 和未知异常。

异常响应继续包含 `requestId`、`code`、`message` 和 `timestamp`。未知异常只能返回固定公开文案，不得返回堆栈、内部路径或原始异常消息。异常处理不通过 Controller 继承完成。

## 8. 前端目标架构

```text
frontend/src/
├─ app/
│  ├─ App.vue
│  ├─ router.ts
│  └─ styles/main.css
├─ pages/
│  ├─ HomePage.vue
│  ├─ ProjectPage.vue
│  └─ NotFoundPage.vue
├─ features/
│  ├─ portfolio/
│  │  ├─ api/portfolioApi.ts
│  │  ├─ model/
│  │  │  ├─ portfolioTypes.ts
│  │  │  └─ projectLabels.ts
│  │  └─ components/
│  │     ├─ ProjectCard.vue
│  │     └─ EvidenceCard.vue
│  └─ agent/
│     ├─ api/answerApi.ts
│     ├─ model/answerTypes.ts
│     └─ components/
│        ├─ AgentPanel.vue
│        └─ AnswerSections.vue
├─ shared/
│  └─ components/LoadingBlock.vue
├─ test/setup.ts
└─ main.ts
```

约束如下：

- 页面 URL、API 地址、JSON 契约、文案、样式和交互不变；
- 测试文件与实现文件就近放置；
- `shared` 只保存至少被两个功能模块使用的内容；
- 本次只移动 `main.css`，不拆分或重写样式；
- 不引入 Pinia、Axios、设计系统、`entities`、`widgets` 或其他空层级；
- 后续视觉改版在此结构上继续演进，不在本次重构中提前实现。

## 9. 自动代码约束检查

新增 `scripts/code-quality-check.ps1`，并由 `scripts/verify-release.ps1` 调用。检查范围覆盖：

- `backend/src/main/java/**/*.java`；
- `backend/src/test/java/**/*.java`。

至少阻断以下模式：

- 局部变量类型推断 `var`；
- `record` 类型声明；
- `import lombok.*`；
- Lombok 数据类注解。

检查失败必须输出文件、行号和命中规则，并返回非零退出码。脚本只检查代码约束，不替代编译、测试、隐私扫描和代码审查。

## 10. 迁移策略

这是结构重构，采用小步迁移而不是一次性重写：

1. 使用指定 JDK 21 运行现有后端、前端和发布验证，记录基线。
2. 先建立公共异常机制，并保持当前错误响应契约。
3. 拆分 Controller 和应用服务，逐个端点运行 MockMvc 测试。
4. 迁移 `portfolio` 包及对应测试。
5. 迁移 `answer` 包及对应测试。
6. 分组把 16 个 `record` 转成普通不可变类，逐组编译和测试。
7. 清理生产及测试 Java 中的全部 `var`。
8. 新增并验证代码约束脚本。
9. 迁移前端目录和测试，不修改模板结构和 CSS 内容。
10. 运行完整发布验证、Playwright 桌面和移动端验收。
11. 使用最终 JAR 启动应用，检查首页、详情页和三个 API。

移动或改名时同步更新包名、import、测试路径和构建引用，不保留兼容壳类或重复 DTO。

## 11. 测试与验收

### 11.1 后端

- 所有现有测试继续通过；
- Controller 拆分后，三个端点契约不变；
- 项目不存在、参数校验和未知异常响应不变；
- 快照校验失败仍阻止应用启动；
- 不可变类集合不能被外部修改；
- `equals()` 和 `hashCode()` 保持原 `record` 值语义。

### 11.2 前端

- 所有 Vitest 测试继续通过；
- 首页、项目详情、推荐问题、回答、Evidence、错误和加载状态不变；
- 路由与刷新 fallback 不变；
- 生产构建通过。

### 11.3 发布门禁

- `scripts/code-quality-check.ps1` 通过；
- `scripts/privacy-check.ps1` 通过；
- Maven 测试和打包通过；
- Vitest 和前端构建通过；
- Playwright 桌面与移动端通过；
- 最终 JAR 包含 Vue 静态资源并可使用 JDK 21 启动。

## 12. 风险与控制

| 风险 | 控制方式 |
|---|---|
| `record` 改类后 JSON 字段变化 | 显式 Jackson 构造器和 API 契约测试 |
| getter 改名导致调用遗漏 | 按模块迁移并频繁编译 |
| 集合仍可被外部修改 | 构造器防御性复制及针对性测试 |
| Controller 拆分改变路由 | MockMvc 契约测试 |
| 前端移动导致循环依赖 | 明确 `pages → features → shared` 依赖方向 |
| CSS 或 UI 意外变化 | CSS 内容不改，运行 Playwright 桌面和移动端 |
| 规范仅停留在文档 | 自动代码约束脚本接入发布验证 |
| 错误响应泄漏内部信息 | 全局异常测试与隐私扫描 |

## 13. 完成定义

只有同时满足以下条件才可声明重构完成：

1. 生产与测试 Java 代码中没有 `var`、`record` 或 Lombok；
2. 后端按 `common`、`portfolio`、`answer` 组织且依赖方向符合设计；
3. 前端按 `app`、`pages`、`features`、`shared` 组织；
4. 现有 API、UI、交互、公开数据和安全边界没有变化；
5. 新增代码约束文档和自动检查；
6. 全量发布验证通过；
7. 最终 JAR 已启动并完成 HTTP 与浏览器验收；
8. `d11_server` 未被修改，用户现有 Git 状态未被擅自处理。
