# 实习作品集 Agent

一个面向技术面试官和实习导师的交互式实习作品集。V0 使用审核后的公开 JSON 快照，展示 SQL 审计与故障排查工具项目，并提供一个确定性问答闭环。

## 当前范围

- Vue 3 首页与项目详情页
- Spring Boot 只读作品集 API
- 一个规范问题及有限等价问法
- 五段式确定性回答
- 脱敏 Evidence 卡片
- 单个可执行 JAR 和 Docker 构建定义

V0 不连接大模型，不读取私有知识库，不保存访客问题。

## 环境要求

- Java 21
- Maven 3.9+
- Node.js 22+
- npm 10+
- Docker（仅容器构建和运行需要）

开始前确认 `java -version` 指向 Java 21。Windows 下命令使用 `mvn.cmd` 和 `npm.cmd`；其他系统可分别替换为 `mvn` 和 `npm`。

## 本地开发

先安装前端依赖：

```powershell
npm.cmd --prefix frontend ci
```

启动后端，默认监听 `http://localhost:8080`：

```powershell
mvn.cmd -f backend/pom.xml spring-boot:run
```

在另一个终端启动前端，Vite 会把 `/api` 请求代理到后端：

```powershell
npm.cmd --prefix frontend run dev
```

## 测试

后端测试：

```powershell
mvn.cmd -f backend/pom.xml test
```

前端单元与组件测试：

```powershell
npm.cmd --prefix frontend test -- --run
```

Playwright 分成两种拓扑。前端独立验收由 Vite 启动页面，并且只对公开内容与问答两个 API 使用浏览器内 mock；完整联调则启动已打包 JAR，访问其中的生产前端资源与真实 Spring Boot API：

```powershell
# Frontend-only visual/interaction acceptance with API mocks
npm.cmd --prefix frontend run test:e2e

# Full packaged-JAR frontend/backend integration
powershell -ExecutionPolicy Bypass -File scripts/run-jar-e2e.ps1
```

完整联调命令要求先完成一次新的前端构建和 Maven 打包，不能复用来源不明的旧制品。

## 构建并运行单 JAR

必须先构建前端，再执行 Maven 打包。Maven 会拒绝缺少 `frontend/dist/index.html` 的构建，并把当前静态资源复制进 JAR。

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run build
mvn.cmd -f backend/pom.xml clean package
java -jar backend/target/portfolio-agent.jar
```

最终制品为 `backend/target/portfolio-agent.jar`。打开 `http://localhost:8080` 查看首页。

## 隐私检查

先验证扫描器自身，再扫描公开快照和前端构建产物：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main/resources/public-data
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path frontend/dist
```

扫描器检查 IPv4、常见内部绝对路径、内部域名和凭据赋值。它是发布前防线，不替代人工脱敏审核。

## Docker

```powershell
docker build --check .
docker build -t internship-portfolio-agent .
docker run --rm -p 8080:8080 internship-portfolio-agent
```

容器以非 root 用户运行，服务端口为 `8080`。

## 完整发布验证

从项目根目录运行原子化发布门禁。它会依次执行代码质量、架构与静态 bundle 校验器自测，完成前端测试与构建、后端 `clean package`、隐私扫描、JAR 解包检查、区分大小写的静态路径及逐文件 SHA-256 对比，再启动该 JAR 完成真实 Playwright 联调，避免验证旧的前端或 JAR。默认也会在 Docker CLI 可用时运行 Dockerfile 检查。

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release.ps1
```

依赖已经通过 `npm ci` 安装时，可使用 `-SkipInstall`；明确只做本机无 Docker 的验收时，可再加 `-SkipDockerCheck`。发布或 CI 不应跳过这两项。

## 公开 API

- `GET /api/v1/public-content`：正式页面使用的审核公开内容聚合
- `GET /api/v1/portfolio`：首页公开快照
- `GET /api/v1/projects/{slug}`：项目详情
- `POST /api/v1/answers`：确定性问答

公开 API 只读取版本化 JSON 快照，不读取私有知识库，也不保存访客问题。

## 目录结构

- `backend/`：Spring Boot API、确定性回答引擎和公开快照
- `frontend/`：Vue 3 页面、组件测试和 Playwright 测试
- `scripts/`：隐私扫描器及其测试
- `docs/`：背景、需求、技术选型和 Superpowers 设计计划

后端 Java 代码采用模块化单体结构：

```text
com.portfolio.agent
├─ common       仅保存跨模块共享机制
├─ portfolio    公开事实、作品集查询与文件仓储
└─ answer       知识转换、回答编排与确定性引擎
```

模块内部使用常见 Spring Boot 命名：

```text
portfolio/controller|service|domain|repository|mapper|validation
answer/controller|service|domain|engine|gateway|adapter|mapper
```

当前模块通信通过 Java Gateway 接口在同一进程内完成：

```text
AnswerService
→ PortfolioKnowledgeGateway
→ LocalPortfolioKnowledgeAdapter
→ PublicPortfolioRepository
```

项目当前不使用 Feign，也不通过 HTTP 或 localhost 对自身模块发起远程调用。

## 设计文档

- `docs/superpowers/specs/2026-07-14-internship-portfolio-v0-design.md`
- `docs/superpowers/plans/2026-07-14-internship-portfolio-v0.md`
- `docs/superpowers/specs/2026-07-16-modular-monolith-package-design.md`
- `docs/07-modular-monolith-backend-review.md`
