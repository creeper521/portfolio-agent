# Wave 1 混合检索真实模型比较与策略 v2.1 验证报告
<!-- DOCUMENT_STATUS: HISTORICAL -->

> **状态：** 已验证并导入随包公开运行时
> **执行日期：** 2026-07-27
> **部署状态：** 未部署；运行时检索仍默认关闭
> **比较范围：** Keyword、Vector、Hybrid 三条离线路线

## 1. 不可变身份

| 项目 | 值 |
|---|---|
| Content version | `2026-07-24.1` |
| Benchmark suite | `retrieval-benchmark-v4-wave1-policy-v2` |
| Retrieval policy | `retrieval-policy-v2.1-query-risk` |
| Runtime Bundle hash | `sha256:9f646774b9c96f37327ff630066bb35ab90219d0c57305e8d6017bafcff6abff` |
| Candidate payload hash | `sha256:53271cf750c2706fd73bafbdda692262e1dae9e6737e1b2cc3ebd22bf73f1ff0` |
| Decision ledger hash | `sha256:38950fff8e262582123ab3955aee240d6ed258bdfc23cf555c6a6e4ef88e21e3` |
| Approval ID | `APR-263a634c50db44d3b524bee3056983a8` |
| Review run | `261d3d00aec844b08c56a46864633734` |
| Compiler JAR hash | `sha256:d2450ed2553500c130fe7d721a9d132b427145f0c2918669dc6a0f0f35db5c51` |
| Model descriptor hash | `sha256:9ff520c01576e44eb0eb07a420e50bfb7603a0471ebb7c4693b31726858fa37a` |

## 2. 公开内容与评测规模

随包 Bundle 包含 1 个 Project、3 个 Case、29 个 Claim、7 个 APPROVED Evidence、29 条 Claim–Evidence Link、5 条 TimelineEvent 和 15 个 QuestionPreset。检索索引包含 29 个 Claim Chunk。

比较套件共有 37 个问题：

- 26 个 Holdout 正例；
- 7 个 Holdout 负例，其中 3 个是策略实现冻结后加入的安全变体；
- 3 个 Regression 负例，来自策略 v1 真实比较暴露的误判；
- 1 个 Calibration 隐私负例。

已经暴露过的失败样例被单独标记为 Regression，没有冒充未见 Holdout。

## 3. Holdout 结果

| 路线 | 正例数 | Hit@1 | Hit@5 | MRR@5 | 正向充分判定 | 正向充分率 | False sufficient |
|---|---:|---:|---:|---:|---:|---:|---:|
| Keyword | 26 | 0.8462 | 1.0000 | 0.9231 | 1 | 0.0385 | 0 |
| Vector | 26 | 0.7692 | 0.9231 | 0.8333 | 5 | 0.1923 | 0 |
| Hybrid | 26 | 0.8846 | 1.0000 | 0.9359 | 20 | 0.7692 | 0 |

在本批公开内容和固定模型上，Hybrid 相对 Keyword 的 Hit@1 提高 3.84 个百分点、MRR@5 提高 1.28 个百分点；相对 Vector 的 Hit@1 提高 11.54 个百分点、Hit@5 提高 7.69 个百分点、MRR@5 提高 10.26 个百分点。Hybrid 同时把正向充分判定从 Keyword 的 1/26、Vector 的 5/26 提高到 20/26。

因此，当前证据支持“RRF Hybrid 对本项目 Wave 1 的公开 Claim 检索具有实际价值”。这个结论只适用于当前内容、模型、策略和 37 例套件，不外推为所有项目或所有问题上的普遍优势。

## 4. 安全回归与未见变体验证

策略 v1 的真实比较曾出现 3 个 false-sufficient：

- 提示注入请求错误命中公开 Claim；
- 绕过环境选择和确认的任意批量删除请求被误判为充分；
- 对失败来源自动重试并保证成功的无依据承诺被误判为充分。

策略 v2 引入查询风险门禁后，三项误判归零，但真实比较发现它误伤了“为什么必须二次确认、而不能直接批量删除？”这一正向安全说明问题，Hybrid 正向充分判定从 20/26 降至 19/26，因此 v2 没有进入运行时。

策略 v2.1 增加否定安全说明语境识别后：

- 3 个 Regression 样例在 Keyword、Vector、Hybrid 下全部返回 `AMBIGUOUS`；
- 3 个独立 Holdout 安全变体在三条路线下全部返回 `AMBIGUOUS`；
- 正向安全说明问题恢复为 Hybrid `SUFFICIENT`；
- 全部路线 false-sufficient 均为 0。

## 5. 性能与工程门禁

- 本地模型查询性能：p50 `2ms`、p95 `2ms`；
- 100 次成功查询的提交内存增量：`4MB`；
- 完整后端：356 项测试通过，0 失败，6 项按环境条件跳过；
- 内容治理：96 个命令场景通过；
- 发布、Verify 和七文件原子导入均通过；
- Approval 同时绑定候选、68 项决策台账、治理输入指纹和编译器 JAR 哈希。

## 6. 结论与边界

Wave 1 已完成“内容扩增 → 人工 Approval → 私有发布 → 真实三路比较 → 安全修复 → 重新 Approval → 原子导入”的闭环。仓库随包运行时现在包含已验证的七文件检索 Bundle。

本轮没有服务器部署，没有把检索默认配置改为开启，也没有把本地 ONNX 模型二进制提交到 Git。生产环境若要启用 `HYBRID`，仍需安装固定 revision 的本地模型、显式配置 Profile，并完成独立部署与线上验收。
