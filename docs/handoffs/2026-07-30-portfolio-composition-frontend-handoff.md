# 资产包组合推荐前端变更 Handoff

> **状态：已被取代。** 独立组合推荐 HTTP 表面已移除。当前前端契约以
> [`agent-portfolio-recommendation-frontend-ai-prompt.md`](agent-portfolio-recommendation-frontend-ai-prompt.md)
> 为准：所有推荐和调整都通过 `POST /api/v2/answers`，并在 Agent 消息内展示。

**日期：** 2026-07-30
**面向：** 后续负责前端设计与开发的 AI
**后端设计：** `docs/superpowers/specs/2026-07-30-postgresql-portfolio-composition-design.md`

## 1. 工作边界

本轮由后端完成 PostgreSQL/pgvector、Markdown 导入治理、Release 发布和资产组合推荐。前端不在本轮实现范围内。

后续前端任务不是全站重构。应保留现有 Vue 3 信息架构、品牌和 Project/Case 页面，重点处理组合推荐的表达，以及可选的私有管理界面。

## 2. 必须适配：公开组合推荐结果

以下是历史响应草案，不再对应可调用的独立接口；现行 Agent 推荐字段仍需要表达其中适用的公开信息：

- 推荐状态：`READY`、`INSUFFICIENT`、`TEMPORARILY_UNAVAILABLE`；
- 实际选择数量：2–5，默认 3；
- 每个推荐主体的 `PROJECT` / `CASE` 类型；
- 标题、摘要、公开路由和求职方向；
- 该主体的入选原因；
- 它提供的能力及其公开证据引用；
- 整套资产的能力覆盖；
- 各资产之间的互补说明；
- 可选替代项；
- 降级状态，不得把 FTS-only 或不足结果伪装为完整成功。

建议后端响应形状：

```json
{
  "selectionId": "sel_...",
  "releaseVersion": "2026-07-30.1",
  "policyVersion": "selection-v1",
  "retrievalMode": "HYBRID",
  "selectionMode": "EXHAUSTIVE",
  "status": "READY",
  "requestedSize": 3,
  "actualSize": 3,
  "coverage": [
    {
      "capabilityCode": "JAVA_BACKEND",
      "label": "Java 后端工程",
      "coveredBySubjectIds": ["PROJECT-02", "CASE-16"]
    }
  ],
  "items": [
    {
      "subjectId": "PROJECT-02",
      "subjectType": "PROJECT",
      "title": "活动系统工程实践",
      "summary": "……",
      "route": "/projects/project-02",
      "careerTrack": "JAVA_BACKEND",
      "selectionReason": "证明持续参与真实业务工程",
      "capabilities": ["JAVA_BACKEND", "INCIDENT_ANALYSIS"],
      "evidenceRefs": [
        {
          "claimId": "CLAIM-...",
          "evidenceId": "EVIDENCE-...",
          "label": "……"
        }
      ]
    }
  ],
  "complementarity": [
    {
      "leftSubjectId": "PROJECT-02",
      "rightSubjectId": "CASE-16",
      "reason": "项目展示长期工程范围，案例展示具体故障定位深度"
    }
  ],
  "alternatives": [],
  "degradation": null
}
```

该历史字段列表不再是现行 DTO 契约；现行字段以后端 Agent 回答契约为准，设计时不得擅自增加未经后端支持的事实字段。

## 3. 推荐交互入口

至少支持以下输入：

- 求职方向：Java 后端、Agent 或不限定；
- 访客身份：技术面试官、HR、同行开发者等受控枚举；
- 能力目标：受控能力多选；
- 数量：2–5，默认 3；
- 可选自然语言目标。

建议先把入口放在现有 Agent 或作品集导览流程中，避免新增一个与现有导航平级但内容重复的大页面。

## 4. 推荐结果状态

### READY

展示完整推荐集合、总体覆盖、逐项原因和公开证据。

### INSUFFICIENT

明确说明只能找到少于请求数量的合格资产。展示实际可用资产，但不使用空卡片、占位卡或弱证据补齐。

### TEMPORARILY_UNAVAILABLE

不展示未经验证的推荐。保留现有 Project/Case 浏览入口，并使用稳定错误文案。

### 降级模式

当 `retrievalMode = FTS_ONLY` 时，可以用轻量提示说明当前使用关键词检索。不要用技术故障弹窗遮挡已有公开内容。

## 5. 证据展示约束

- 只展示后端返回的公开、已批准 Evidence；
- 不构造本地文件路径或私有知识库链接；
- 不展示原始 Markdown、数据库 ID 以外的内部标识、向量分数或内部调试字段；
- Claim 与 Evidence 的展示必须保持关联，不把 Evidence 脱离其支持的陈述使用；
- `releaseVersion` 和 `policyVersion` 可放入调试信息或折叠的“推荐依据”，无需成为主视觉。

## 6. 可选管理界面：不属于公开站点 V1 必做

后端 V1 会优先提供 CLI。若后续增加私有管理端，可设计：

1. 扫描目录与 dry-run；
2. ADDED / CHANGED / UNCHANGED / MISSING / FAILED / BLOCKED 预览；
3. 文档级导入和重试；
4. `VECTOR_PENDING` 状态；
5. 标签和 Project/Case/Claim/Evidence 映射建议审核；
6. Release 差异、校验、发布和回滚。

管理界面必须与公网应用隔离，不能只依靠隐藏路由。它需要独立部署、独立鉴权和治理库凭据；在这些基础设施未建立前，只保留 CLI。

## 7. 不应改动

- 不重做 Project/Case 详情页；
- 不改变既有 Project、Case、Collection 的领域含义；
- 不增加未经证据支撑的能力雷达图或百分比分数；
- 不向浏览器持久化访客问题或推荐请求；
- 不在 URL 和浏览器历史中写入自然语言问题；
- 不让前端自行重排后端已经确定的选择；
- 不让前端调用私有治理库或导入接口。

## 8. 前端验收建议

- 2、3、5 个结果均能稳定布局；
- READY、INSUFFICIENT、TEMPORARILY_UNAVAILABLE 和 FTS_ONLY 均有明确状态；
- Project 与 Case 路由正确；
- 所有证据引用均来自响应；
- 刷新或关闭后访客问题不被持久化；
- 后端返回未知枚举时 fail-safe，不展示误导性成功态；
- 不影响现有 Project/Case 浏览与 Agent 回答流程。
