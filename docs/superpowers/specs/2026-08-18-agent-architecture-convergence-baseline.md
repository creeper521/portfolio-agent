# Agent 架构收敛基线
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

- **记录时间：** 2026-08-18（Asia/Shanghai）
- **用途：** Slice 0 复杂度、合同、数据身份与验证基线；只用于前后对比，不设置机械删行 KPI
- **Git 基线：** `9980068dec8fa33b06ce59fa27b0de1427b54603`
- **分支/工作区：** `master` 普通检出；记录时存在大量用户未提交与未跟踪资产，实施不得覆盖、恢复、暂存或提交这些内容
- **生产部署状态：** 尚未生产部署

## 1. 冻结的数据身份

| 身份 | 基线值 | 证据 |
|---|---|---|
| ContentReleaseId | `2026-08-05.1` | `backend/src/main/resources/public-data/bundle/manifest.json` |
| Bundle schema | `4.0` | 同上 |
| Manifest SHA-256 | `dae921eafb1f2124dbdc958fe785e793d6f00ed5b200ed8dcb19c64751967ec7` | 本地文件哈希 |
| Checksums SHA-256 | `2932ff036467e5bfc0b56dfb2c99c59da9522a664991e88e22854ec69ba131d4` | 本地文件哈希 |
| Eval datasetVersion | `2026-08-06.1` | `governance/portfolio-governance/evaluation/manifest.v1.json` |
| Eval datasetHash | `655e60e83a4e0361317518a8514b7719e4c37bd7693cff1fb2c505e6635f6659` | `output/evaluation/phase1-final-validate/report.json` |
| Eval expanded case count | `73` | 同一 validate report |

本次架构收敛不修改 Public Content、Release Bundle 或其事实范围。ContentReleaseId 只作为固定的 Turn 内容快照身份进入目标合同。

## 2. 代码与公开面基线

统计范围为 Slice 0 写入生产代码前的 `com.portfolio.agent.answer` 与 `frontend/src/features/agent`。LOC 为包含空行的物理行；Bean 总数为 `@Bean` 方法与 Spring stereotype 声明的合计，只用于相同口径复测。

| 指标 | 基线 |
|---|---:|
| Answer 主代码 Java 文件 | 505 |
| Answer 主代码 LOC | 42,678 |
| Answer public class/interface/enum | 500 |
| Answer `@Bean` 方法 | 67 |
| Answer Spring stereotype 类型 | 14 |
| Answer Bean/组件入口合计 | 81 |
| Answer Request DTO `private final` 字段 | 52 |
| Answer Response DTO `private final` 字段 | 230 |
| Answer Request/Response DTO 字段合计 | 282 |
| Answer 测试 Java 文件 | 156 |
| Answer 测试 LOC | 17,856 |
| Frontend Agent TS/Vue 文件 | 75 |
| Frontend Agent LOC | 19,940 |
| Frontend Agent 测试文件 | 37 |

### 最大后端测试文件

| 文件 | LOC |
|---|---:|
| `LocalPortfolioKnowledgeAdapterTest.java` | 1,008 |
| `ConversationAnswerResponseMapperTest.java` | 677 |
| `OpenAiCompatibleConversationalModelAdapterTest.java` | 508 |
| `DefaultTurnRouterDeterministicTest.java` | 472 |
| `RoutingContextResolverTest.java` | 446 |
| `ConversationalAgentRuntimeTest.java` | 442 |
| `ProposalCompilerTest.java` | 335 |
| `ConversationAnswerControllerTest.java` | 320 |
| `SemanticPlanValidatorTest.java` | 318 |
| `ConversationIntentRouterTest.java` | 313 |

### 最大前端 Agent 测试文件

| 文件 | LOC |
|---|---:|
| `AgentWorkspace.test.ts` | 2,340 |
| `ConversationThread.test.ts` | 1,995 |
| `mapAnswerResponse.test.ts` | 581 |
| `answerApi.test.ts` | 518 |
| `p5ContractMapping.test.ts` | 455 |

## 3. Answer 顶层包依赖基线

静态扫描 `com.portfolio.agent.answer.<top-level>` 的显式 import。主要边计数：

| 边 | import 数 |
|---|---:|
| `service -> domain` | 121 |
| `intelligence -> domain` | 47 |
| `adapter -> domain` | 45 |
| `adapter -> routing` | 34 |
| `mapper -> dto` | 34 |
| `composition -> domain` | 30 |
| `intelligence -> composition` | 28 |
| `intelligence -> routing` | 26 |
| `context -> routing` | 25 |
| `routing -> domain` | 22 |
| `routing -> intelligence` | 20 |
| `mapper -> routing` | 18 |
| `dto -> domain` | 18 |
| `routing -> composition` | 17 |
| `service -> context` | 17 |
| `gateway -> domain` | 16 |

检测到 10 组双向顶层包依赖：

- `adapter <-> intelligence`
- `context <-> domain`
- `context <-> dto`
- `context <-> routing`
- `context <-> service`
- `domain <-> intelligence`
- `domain <-> routing`
- `general <-> routing`
- `intelligence <-> routing`
- `intelligence <-> service`

## 4. 迁移/阶段引用基线

统计范围：`backend/src/main/java` 与 `frontend/src`，按固定字符串逐行命中；数字只用于在相同口径下验证最终归零，不代表全部命中都具有同一业务含义。

| 字符串 | 命中行数 |
|---|---:|
| `stp-v1` | 48 |
| `stp-v2` | 96 |
| `stp-v3` | 18 |
| `P2` | 20 |
| `P3` | 185 |
| `P4` | 93 |
| `P5` | 114 |
| `Legacy` | 46 |
| `Compatibility` | 10 |
| `Shadow` | 11 |
| `PlanConfirmation` | 210 |
| `TaskResultPayload` | 75 |
| `GroundedAnswerContribution` | 21 |
| `ConversationalModelPort` | 31 |
| `degraded` | 231 |
| `CompletionReceiptResponse` | 17 |

## 5. 验证时间基线

在相同工作树、Java 21 与现有依赖安装状态下记录：

| 命令 | 结果 | 墙钟/工具报告时间 |
|---|---|---:|
| `mvn.cmd -f backend/pom.xml test` | 1,273 passed；21 environment-skipped；0 failure/error | 28.691 s |
| `npm.cmd --prefix frontend test -- --run` | 67 files；728 passed；0 failed | 7.37 s |

Docker/Testcontainers 集成项在本机条件不满足时按既有规则跳过；本基线不把这些项标记为通过。

## 6. 目标模块依赖检查

最终 `com.portfolio.agent.turn` 只允许以下主方向：

```text
api -> lifecycle
lifecycle -> planning / execution / projection / continuation
capability.* -> execution SPI
projection -> planning / execution / continuation public model
synthesis -> general semantic result / portfolio semantic result
infrastructure -> typed model/retrieval/store ports
state adapters -> lifecycle / continuation persistence ports
```

明确禁止：

- `planning`、`execution`、`capability`、`projection`、`continuation` 依赖 HTTP DTO；
- `planning -> capability implementation`；
- `execution -> projection/lifecycle/state adapter`；
- `capability -> lifecycle/api/projection`；
- `projection -> adapter/retrieval/model transport`；
- `general/portfolio -> synthesis` 反向依赖；
- 新 `turn.* -> answer.*` 稳态转发层；
- 任何双向顶层模块依赖。

Slice 6 将把该目标表转成源码依赖测试；各中间 Slice 先以零引用门和文件级 Replacement Manifest 保证没有永久 bridge。

## 7. Slice 0 目标行为资产

- `contracts/agent-turn/scenarios/`：7 个 manifest、35 个目标语义场景；
- `contracts/agent-turn/fixtures/`：8 个 PublicAgentTurn Golden Fixtures；
- 后端结构门：`AgentTurnScenarioManifestTest`、`PublicAgentTurnGoldenFixtureStructureTest`；
- Frontend 消费测试由 Frontend Agent 直接读取同一 fixtures，不复制合同；后端主开发不修改 `frontend/src/features/agent/**`。

## 8. Feature Freeze

从 Slice 0 起到 Slice 6 Exit Gate：

1. 只实施已批准的 Agent 架构 Replacement 工作与必要缺陷修复；
2. 不新增 Tool Registry、多 Agent、SSE、长期记忆、账号认证、External Knowledge 或外部写操作；
3. 不扩展公开事实、不修改 Release Bundle；
4. 未属于 Slice 0～6 Replacement Manifest 的产品功能进入后续 backlog；
5. 不为旧测试保留 Compatibility constructor、双 DTO、双 Router 或 new-to-old converter；
6. Frontend Agent 责任区只通过共享 fixtures 与 handoff 协调。
