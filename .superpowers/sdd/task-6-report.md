# Task 6：确定性推荐策略与无状态上下文指纹

基线 SHA：`1ad1b17`

## RED / GREEN

- RED：先新增 policy、fingerprint 和 context validator 测试；首次运行指定 Maven 命令因目标类型不存在而在 testCompile 阶段失败。
- RED：Evidence 门禁校准后，新增“空 Evidence 不可入选/不可继承”的测试；实现前聚焦测试分别错误地选入空 Evidence 候选并把空 Evidence context 视为有效。
- GREEN：实现后运行：

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  '-Dtest=PortfolioRecommendationPolicyTest,RecommendationBatchFingerprintTest,RecommendationContextValidatorTest,TopKSelectionStrategyTest,ExhaustiveSelectionStrategyTest' test
```

结果：15 tests，0 failures，0 errors。

## 实现边界

- `PortfolioRecommendationPolicy` 仅接收 `SelectionCandidate`、规范化条件和替换排除 ID；候选不含至少一条 APPROVED Evidence 时在策略前排除。
- 小候选集复用 `ExhaustiveSelectionStrategy`，大候选集复用 `TopKSelectionStrategy`；结果集和顺序不由模型决定。
- `RecommendationContextValidator` 不读取 Registry、Session、缓存或持久化状态。回传 context 必须匹配当前内容版本、受控条件、数量、唯一 ID、当前允许 ID 与 Evidence 门禁；失败返回固定枚举原因码。
- validator 对被继承的候选要求 Evidence 列表非空且全部 APPROVED。

## Canonical 指纹规则

`RecommendationBatchFingerprint` 以 UTF-8 SHA-256 计算 `rec_` 加 64 位小写十六进制。Canonical 字段按固定顺序写入：

1. `contentVersion`
2. `careerTrack`
3. `audienceRole`
4. `requestedSize`
5. 已排序的 `capabilityCodes`
6. 保持推荐顺序的 `selectedPortfolioIds`

字段使用长度前缀避免拼接歧义。原始问题、`goal`、时间戳和随机数均不进入指纹。

## 集成风险

- Task 7 必须将当前公开 Release 且通过 Evidence 门禁的候选传给 validator；不得将历史推荐或未过滤候选当作允许集合。
- 调整推荐时必须把被替换作品 ID 传入排除集合，并在重新执行 policy 后重新计算 context 与指纹。
- 本任务没有修改检索、运行时、HTTP、Selection Controller 或前端；上述接线由后续集成任务负责。
