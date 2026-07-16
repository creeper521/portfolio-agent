# 实习作品集 Agent V0 实施计划

> **For Codex:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task by task. Every business behavior follows `test-driven-development`; completion claims follow `verification-before-completion`.

**Goal:** 交付一个可运行的端到端纵向切片，让访问者从首页进入 SQL 审计项目，通过确定性问答获得五段式回答和一张脱敏 Evidence 卡片。

**Architecture:** Vue 3 前端通过 REST 访问 Spring Boot 后端。后端启动时校验并加载版本化公开 JSON 到内存，通过 `AnswerEngine` 接口组织确定性回答。生产构建把 Vue 静态产物打入 Spring Boot 可执行 JAR，并提供 Docker 镜像入口。

**Tech Stack:** Java 21、Spring Boot 3、Maven、Vue 3、TypeScript、Vite、Vitest、Vue Test Utils、Playwright、Docker。

---

## 执行规则

- 当前仓库没有首个提交，无法创建 Git worktree。用户已明确授权在 `D:\code\agent` 持续实现；保留已有 `.idea` 暂存状态和三份文档，不执行重置或覆盖。
- 文件先在可写暂存区通过 `apply_patch` 创建，再复制到项目目录，避免使用 shell 文本重定向编辑文件。
- 默认 Java 17 不满足技术选型，构建前安装或定位 JDK 21，并在验证命令中显式设置 `JAVA_HOME`。
- 每个功能任务必须记录 RED 命令和预期失败，再编写最小实现并记录 GREEN 命令。
- 不在 V0 引入 DeepSeek、Spring AI、SSE、数据库或私有知识库读取。
- 不提交或修改用户现有 Git 暂存内容，除非用户后续明确要求。

### Task 1: 建立仓库规则、构建骨架和安全边界

**Files:**

- Create: `AGENTS.md`
- Create: `.gitignore`
- Create: `README.md`
- Create: `SECURITY.md`
- Create: `Dockerfile`
- Create: `.dockerignore`
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/portfolio/agent/PortfolioAgentApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `frontend/package.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/index.html`

**Step 1: 验证环境基线**

Run: Java 21 `java -version`、Maven `mvn -version`、`node --version`、`npm --version`。

Expected: Java 21、Maven 3.9.x、Node 22.x 和 npm 10.x 可用。

**Step 2: 创建最小骨架和安全忽略规则**

忽略构建产物、IDE 本地文件、`.env*`、密钥、候选快照、隐私扫描报告和原始素材。README 只写已实现命令，不声明未完成能力。

**Step 3: 安装依赖并验证空骨架编译**

Run: `npm.cmd install` in `frontend/`。  
Run: `mvn.cmd -q -f backend/pom.xml -DskipTests compile`。

Expected: 两个命令退出码为 0。

### Task 2: 公开快照模型和启动校验

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/domain/*.java`
- Create: `backend/src/main/java/com/portfolio/agent/content/PublicPortfolioRepository.java`
- Create: `backend/src/main/java/com/portfolio/agent/content/JsonPublicPortfolioRepository.java`
- Create: `backend/src/main/java/com/portfolio/agent/content/PortfolioSnapshotValidator.java`
- Create: `backend/src/main/resources/public-data/public-portfolio.v1.json`
- Test: `backend/src/test/java/com/portfolio/agent/content/JsonPublicPortfolioRepositoryTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/content/PortfolioSnapshotValidatorTest.java`

**Step 1: RED，先定义合法快照加载行为**

测试合法快照可加载，且 SQL 项目状态为 `DELIVERED`、贡献类型为 `PRIMARY`。

Run: `mvn.cmd -f backend/pom.xml -Dtest=JsonPublicPortfolioRepositoryTest test`。

Expected: 编译或测试失败，因为模型与仓库尚不存在。

**Step 2: GREEN，实现最小模型和 JSON 加载**

使用 Java record 和 Jackson 反序列化。仓库构造时加载 classpath 资源并只读保存。

Run: 同上。Expected: PASS。

**Step 3: RED，定义快照不变量**

测试 schema 版本、唯一 ID、项目问题引用、项目 Evidence 引用、`APPROVED` Evidence 和 `rawContentPublic=false`。

Run: `mvn.cmd -f backend/pom.xml -Dtest=PortfolioSnapshotValidatorTest test`。

Expected: FAIL，因为校验器尚不存在或未拒绝非法数据。

**Step 4: GREEN，实现启动校验**

实现清晰的异常信息。任何非法公开快照必须让应用启动失败。

Run: `mvn.cmd -f backend/pom.xml -Dtest=PortfolioSnapshotValidatorTest test`。

Expected: PASS。

### Task 3: 确定性问题匹配与回答引擎

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/answer/QuestionNormalizer.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/AnswerEngine.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/DeterministicAnswerEngine.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/AnswerResult.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/QuestionNormalizerTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/DeterministicAnswerEngineTest.java`

**Step 1: RED，规范问题匹配测试**

覆盖 Unicode/空白/大小写/句末标点规范化、规范问题命中、等价问法命中和无关问题不命中。

Run: `mvn.cmd -f backend/pom.xml -Dtest=QuestionNormalizerTest,DeterministicAnswerEngineTest test`。

Expected: FAIL。

**Step 2: GREEN，实现可解释的完全匹配**

只匹配规范化后的规范问题或人工 aliases，不做关键词评分。

Run: 同上。Expected: PASS。

**Step 3: RED，五段回答和 Evidence 过滤测试**

断言顺序固定为 `BACKGROUND`、`RESPONSIBILITY`、`SOLUTION`、`VERIFICATION`、`STATUS`，只返回项目关联且审核通过的 Evidence。

Run: 同上。Expected: FAIL。

**Step 4: GREEN，组织确定性回答**

回答直接读取 Project 字段，不复制第二份项目事实。

Run: 同上。Expected: PASS。

### Task 4: REST API 与统一错误处理

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/api/PortfolioController.java`
- Create: `backend/src/main/java/com/portfolio/agent/api/ApiExceptionHandler.java`
- Create: `backend/src/main/java/com/portfolio/agent/api/dto/*.java`
- Create: `backend/src/main/java/com/portfolio/agent/service/PortfolioQueryService.java`
- Test: `backend/src/test/java/com/portfolio/agent/api/PortfolioControllerTest.java`

**Step 1: RED，首页和详情 API 测试**

使用 MockMvc 验证 `GET /api/v1/portfolio`、`GET /api/v1/projects/sql-audit` 和未知项目 404。

Run: `mvn.cmd -f backend/pom.xml -Dtest=PortfolioControllerTest test`。

Expected: FAIL。

**Step 2: GREEN，实现只读查询 API**

DTO 只暴露公开字段，不直接返回内部领域对象。

Run: 同上。Expected: PASS。

**Step 3: RED，回答和校验 API 测试**

覆盖规范问题、未命中问题、空问题、超长问题、非法 slug 和 `requestId`。

Run: 同上。Expected: FAIL。

**Step 4: GREEN，实现回答 API 和异常映射**

业务未命中返回 200；输入非法返回 400；项目不存在返回 404。

Run: 同上。Expected: PASS。

### Task 5: Vue 数据访问层和路由状态

**Files:**

- Create: `frontend/src/types/portfolio.ts`
- Create: `frontend/src/api/portfolio.ts`
- Create: `frontend/src/router.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/main.ts`
- Test: `frontend/src/api/portfolio.test.ts`
- Test: `frontend/src/router.test.ts`

**Step 1: RED，API 客户端行为测试**

覆盖成功解析、非 2xx 错误、请求 payload 和超时/网络错误的用户消息。

Run: `npm.cmd test -- --run src/api/portfolio.test.ts`。

Expected: FAIL。

**Step 2: GREEN，实现类型和 fetch 客户端**

不引入全局状态库。所有 API 以 `/api/v1` 为基准。

Run: 同上。Expected: PASS。

**Step 3: RED/GREEN，实现两条路由**

测试 `/` 和 `/projects/sql-audit` 映射到对应页面，未知路由回到首页或显示明确 404。

Run: `npm.cmd test -- --run src/router.test.ts`。

Expected: 先 FAIL，最小实现后 PASS。

### Task 6: 首页和项目详情展示

**Files:**

- Create: `frontend/src/views/HomeView.vue`
- Create: `frontend/src/views/ProjectView.vue`
- Create: `frontend/src/components/ProjectCard.vue`
- Create: `frontend/src/components/EvidenceCard.vue`
- Create: `frontend/src/components/LoadingBlock.vue`
- Create: `frontend/src/styles/main.css`
- Test: `frontend/src/views/HomeView.test.ts`
- Test: `frontend/src/views/ProjectView.test.ts`

**Step 1: RED，首页加载和导航测试**

断言加载、错误、项目卡片、状态、贡献类型和导航入口。

Run: `npm.cmd test -- --run src/views/HomeView.test.ts`。

Expected: FAIL。

**Step 2: GREEN，实现首页**

采用非对称首屏与真实项目内容，不使用虚构姓名和链接。

Run: 同上。Expected: PASS。

**Step 3: RED，详情内容测试**

断言背景、职责、方案、验证、状态、交接说明和 Evidence 摘要。

Run: `npm.cmd test -- --run src/views/ProjectView.test.ts`。

Expected: FAIL。

**Step 4: GREEN，实现响应式详情页**

桌面双列，移动端单列；提供加载与错误状态。

Run: 同上。Expected: PASS。

### Task 7: Agent 对话区完整状态

**Files:**

- Create: `frontend/src/components/AgentPanel.vue`
- Create: `frontend/src/components/AnswerSections.vue`
- Test: `frontend/src/components/AgentPanel.test.ts`

**Step 1: RED，推荐问题和成功回答测试**

测试点击推荐问题、提交 payload、提交中禁用、五段回答和 Evidence 卡片。

Run: `npm.cmd test -- --run src/components/AgentPanel.test.ts`。

Expected: FAIL。

**Step 2: GREEN，实现成功路径**

使用原生表单语义和可见标签。回答完成后用 `aria-live` 通知。

Run: 同上。Expected: PASS。

**Step 3: RED/GREEN，边界和错误路径**

覆盖未命中提示、网络错误、保留输入和重复提交保护。

Run: 同上。Expected: 先 FAIL，完成后 PASS。

### Task 8: 单制品构建、SPA fallback 和容器化

**Files:**

- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/portfolio/agent/web/SpaForwardController.java`
- Test: `backend/src/test/java/com/portfolio/agent/web/SpaForwardControllerTest.java`
- Modify: `Dockerfile`
- Modify: `README.md`

**Step 1: RED，详情路由 fallback 测试**

断言 `/projects/sql-audit` 返回 `index.html`，且 `/api/**` 不被前端 fallback 接管。

Run: `mvn.cmd -f backend/pom.xml -Dtest=SpaForwardControllerTest test`。

Expected: FAIL。

**Step 2: GREEN，实现 SPA fallback**

只 forward 已知前端路由模式。

Run: 同上。Expected: PASS。

**Step 3: 构建前端并打入 JAR**

Run: `npm.cmd ci`、`npm.cmd run build`。  
Run: `mvn.cmd -f backend/pom.xml package`。

Expected: JAR 包含 `BOOT-INF/classes/static/index.html`。

**Step 4: 验证 Docker 构建定义**

Run: `docker build --check .`（本机支持时）。  
Expected: Dockerfile 语法检查通过。

### Task 9: 隐私扫描、端到端验收与文档

**Files:**

- Create: `scripts/privacy-check.ps1`
- Create: `frontend/e2e/portfolio.spec.ts`
- Create: `frontend/playwright.config.ts`
- Modify: `frontend/package.json`
- Modify: `README.md`
- Modify: `SECURITY.md`

**Step 1: RED，隐私扫描验证**

先用测试 fixture 确认脚本能发现 IPv4、Windows 绝对路径、内部 Linux 路径和凭据键。

Run: `powershell -File scripts/privacy-check.ps1 -Path <unsafe-fixture>`。

Expected: 非 0。

**Step 2: GREEN，扫描公开内容与构建产物**

Run: `powershell -File scripts/privacy-check.ps1 -Path backend/src/main/resources/public-data`。  
Expected: 0 个禁止命中。

**Step 3: 端到端浏览器测试**

启动可执行 JAR，运行 Playwright 验证首页、详情、推荐问题、回答和 Evidence。

Run: `npm.cmd run test:e2e`。

Expected: PASS。

**Step 4: 完整验证**

Run: `mvn.cmd -f backend/pom.xml test`。  
Run: `npm.cmd test -- --run`。  
Run: `npm.cmd run build`。  
Run: `mvn.cmd -f backend/pom.xml package`。  
Run: privacy check against source and unpacked JAR。  
Expected: 全部退出码 0。

**Step 5: 按验收清单人工复核**

逐项检查设计规格第 12 节，并执行前端设计 pre-flight：可见文案无 em dash、单一强调色、统一圆角、按钮与表单对比度、移动端折叠、深浅模式、减少动画、加载和错误状态。

### Task 10: 代码审查和交付检查

**Step 1: 审查差异**

Run: `git status --short`、`git diff --check`、文件级差异审阅。保留用户原有暂存状态，不自动 stage 或 commit。

**Step 2: 按需求追踪矩阵复核**

将设计规格的 V0 验收标准逐项映射到代码、测试或运行证据。发现缺口必须修复并重跑验证。

**Step 3: 最终运行证据**

启动单 JAR，使用 HTTP 检查首页、详情页和三个 API，再停止进程。只有取得新鲜成功输出后才声明切片完成。
