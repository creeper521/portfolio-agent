# Retrieval Baseline Comparison

> **状态：** Wave 0 本地离线基线
>
> **核对日期：** 2026-07-24
>
> **范围：** 当前公开内容版本上的 Keyword、Vector、Hybrid 三路比较
>
> **结论边界：** 本报告记录可复现基线，不构成生产部署、线上验收或 Hybrid 最终价值结论。

## 1. 不可变身份

| 身份 | 值 |
|---|---|
| Content version | `2026-07-23.1` |
| Benchmark suite version | `retrieval-benchmark-v2` |
| Bundle hash | `sha256:e3b6dc1100b8355a3d32375999b9f0786427b1538e0c8022bb17f5b0a334c2f1` |
| Policy version | `retrieval-policy-v1` |
| Model descriptor hash | `sha256:9ff520c01576e44eb0eb07a420e50bfb7603a0471ebb7c4693b31726858fa37a` |
| `comparison.json` SHA-256 | `C977D03A20212D3C860CE7F35D4C480EA1BA16E17280EBE0EC4C7C0C9489EE48` |

模型为本地只读的 `BAAI/bge-small-zh-v1.5` 固定描述制品。模型目录、主机路径和模型二进制不进入 Git，也不写入报告产物。

## 2. 数据集与安全检查

基线包含 12 个公开用例：

- 8 个正例：5 个 `EXACT_TERM`，3 个 `SEMANTIC_PARAPHRASE`；
- 4 个安全负例：2 个 `OUT_OF_SCOPE`、1 个 `PRIVACY`、1 个 `INJECTION`；
- 主体覆盖 1 个 Project（`sql-audit`）和 3 个 Case；
- 每个用例都生成 Keyword、Vector、Hybrid 三条结果，共 36 条。

逐条将 `comparison.json` 与 fixture 交叉核对后：

- 12 个用例均恰好具有三条路线结果；
- 每条结果的 case ID 都可回查 fixture 中预期的 Project 或 Case，执行器按该主体过滤候选；
- 三条路线的 false-sufficient 均为 0，没有安全负例被判为 `SUFFICIENT`；
- 产物中未发现绝对路径、主机名、私网 IP、私有来源、向量载荷、原始分数、凭据或其他私有运行时材料。

Vector 对 `negative-injection-01` 和 `negative-privacy-contact-01` 返回了比 fixture 预期更保守的 `OUT_OF_SCOPE`，因此记为决策不一致，但不是 false-sufficient。

## 3. 三路结果

### 3.1 总体指标

| Route | Positive cases | Hit@1 | Hit@5 | MRR@5 | Positive decision success | False sufficient |
|---|---:|---:|---:|---:|---:|---:|
| Keyword | 8 | 0.8750 | 1.0000 | 0.9375 | 2/8 | 0 |
| Vector | 8 | 0.7500 | 0.7500 | 0.7500 | 2/8 | 0 |
| Hybrid | 8 | 1.0000 | 1.0000 | 1.0000 | 8/8 | 0 |

Hit 和 MRR 衡量预期 Claim/Chunk 的排名；`Positive decision success` 还要求共享 Grounding Gate 最终返回 `SUFFICIENT`。因此 Keyword 虽然 Hit@5 为 1.0，仍只有 2/8 个正例通过最终决策；两类指标不能互相替代。

### 3.2 分类别指标

| Category | Route | Positive cases | Hit@1 | Hit@5 | MRR@5 | Positive decision success | False sufficient |
|---|---|---:|---:|---:|---:|---:|---:|
| EXACT_TERM | Keyword | 5 | 0.8000 | 1.0000 | 0.9000 | 2/5 | 0 |
| EXACT_TERM | Vector | 5 | 0.6000 | 0.6000 | 0.6000 | 2/5 | 0 |
| EXACT_TERM | Hybrid | 5 | 1.0000 | 1.0000 | 1.0000 | 5/5 | 0 |
| SEMANTIC_PARAPHRASE | Keyword | 3 | 1.0000 | 1.0000 | 1.0000 | 0/3 | 0 |
| SEMANTIC_PARAPHRASE | Vector | 3 | 1.0000 | 1.0000 | 1.0000 | 0/3 | 0 |
| SEMANTIC_PARAPHRASE | Hybrid | 3 | 1.0000 | 1.0000 | 1.0000 | 3/3 | 0 |
| OUT_OF_SCOPE | Keyword | 0 | 0.0000 | 0.0000 | 0.0000 | — | 0 |
| OUT_OF_SCOPE | Vector | 0 | 0.0000 | 0.0000 | 0.0000 | — | 0 |
| OUT_OF_SCOPE | Hybrid | 0 | 0.0000 | 0.0000 | 0.0000 | — | 0 |
| PRIVACY | Keyword | 0 | 0.0000 | 0.0000 | 0.0000 | — | 0 |
| PRIVACY | Vector | 0 | 0.0000 | 0.0000 | 0.0000 | — | 0 |
| PRIVACY | Hybrid | 0 | 0.0000 | 0.0000 | 0.0000 | — | 0 |
| INJECTION | Keyword | 0 | 0.0000 | 0.0000 | 0.0000 | — | 0 |
| INJECTION | Vector | 0 | 0.0000 | 0.0000 | 0.0000 | — | 0 |
| INJECTION | Hybrid | 0 | 0.0000 | 0.0000 | 0.0000 | — | 0 |

### 3.3 失败用例

Keyword：

- `case-codegraph-workflow-paraphrase-01`
- `case-multilingual-preservation-paraphrase-01`
- `case-role-reset-flow-paraphrase-01`
- `sql-background-exact-01`
- `sql-routing-decision-exact-01`
- `sql-verification-exact-01`

Vector：

- `case-codegraph-workflow-paraphrase-01`
- `case-multilingual-preservation-paraphrase-01`
- `case-role-reset-flow-paraphrase-01`
- `negative-injection-01`
- `negative-privacy-contact-01`
- `sql-delivered-exact-01`
- `sql-responsibility-exact-01`
- `sql-verification-exact-01`

Hybrid 没有失败用例。

## 4. 基线解读

本轮结果说明，在当前 12 条公开语料上，Hybrid 同时保留了 Keyword 与 Vector 的候选互补性，并在共享 Grounding Gate 下通过了全部 8 个正例和 4 个安全负例。它是后续内容扩增前的有效工程基线。

但 Wave 0 只有 1 个 Project、3 个 Case 和 12 个用例；语料规模、类别覆盖、改写多样性及 holdout 数量都不足。当前结果不能用于宣称 Hybrid 已被最终证明更有价值，也不能据此调整生产 Policy。后续必须在新增真实公开项目、Evidence 与更大独立 holdout 上复测，最终价值判断只看预先冻结的 holdout。

运行时 Profile 没有因本基线改变：仍只有 `DISABLED`、`KEYWORD_ONLY`、`HYBRID`，不存在生产 `VECTOR_ONLY`；检索仍默认关闭。本轮没有部署。

## 5. 本地环境

- OS：Microsoft Windows NT `10.0.19045.0`，amd64；
- PowerShell：`5.1.19041.6456`；
- Java：Eclipse Adoptium Temurin `21.0.11+10`；
- Maven：`3.9.9`；
- Node.js：`v22.21.1`；
- npm：`10.9.4`；
- Vite：`7.3.6`；
- Vitest：`3.2.7`。

本地性能门禁记录为 `p50=2ms`、`p95=3ms`、`committedDelta=4MB`、`successes=100`。这些是本机单次门禁结果，不是生产 SLO。

## 6. 验证记录

真实三路基线：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-local-retrieval-benchmark.ps1 -ModelDirectory "<LOCAL_MODEL_DIR>" -CasesPath backend/src/test/resources/retrieval-benchmark/cases.json -OutputDirectory output/retrieval-benchmark/wave-0
```

结果：退出码 0，最终输出 `Local retrieval real-model comparison passed.`。

完整验证：

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -ModelDirectory "<LOCAL_MODEL_DIR>"
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml package
```

验证结果：

- 后端：328 tests，0 failures，0 errors，6 skipped；
- 前端单元/组件：26 test files，143 tests，全部通过；
- 前端构建：109 modules transformed；
- standalone privacy：241 files，通过；
- standalone architecture：通过；
- `verify-release.ps1`：通过，内部再次完成 328/6 后端测试、143 前端测试、真实模型比较、单 JAR 启动和 Playwright；
- Playwright：32 passed，4 skipped；跳过项为现有套件的预期平台/视口条件；
- Docker CLI 不可用，脚本按现有契约给出 warning，未执行 Docker build check，整体 release verification 仍通过；
- 最终 Maven package：328 tests，0 failures，0 errors，6 skipped，构建成功。

`<LOCAL_MODEL_DIR>` 表示本机只读模型制品目录；实际绝对路径已按公开报告隐私规则脱敏，其余参数与执行命令一致。

后端 6 个预期 skip 来自需要显式真实制品或特定发布 fixture 的测试：`LocalEmbeddingDescriptorTest` 1、`LocalEmbeddingPerformanceTest` 1、`C2ReleaseFixtureBuilderTest` 1、`RetrievalBenchmarkTest` 1、`RetrievalBundleCompilerCliTest` 2。真实模型相关 smoke、acceptance、performance 和三路比较已由独立真实模式及完整发布门禁执行。
