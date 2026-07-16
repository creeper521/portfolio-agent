# Portfolio Design v2 — 三个方向

> 使用 `frontend-design` skill，基于 `docs/01-项目背景.md` 与 `docs/02-需求探索文档.md` 从 0 设计。
> 每个方向是一个**自包含 HTML**（内联 CSS / 原生 JS），打开即可看，便于横向比较与讨论。
> 内容均来自两份文档的真实素材（四个核心主题、状态、证据等级、贡献类型、保密审查）。

---

## 0. 设计前先固定的事实（锁定 brief）

skill 要求：brief 没钉死的，先自己钉死。先说清这个产品「是什么」。

- **对象**：一个公开的实习作品集 Agent。但它真正的差异点不是「能聊天」，而是 **可审计 / 可核验**。
  两份文档反复强调：结构化事实优先于语言表现、**不把计划当成果**、每个成果都有 `status`、每条结论都有 `verificationLevel`、每份证据都有 `confidentialityReviewStatus`、存在一个确定性 **兜底引擎**、展示用证据是**脱敏**后的。
- **主角本人**：Java 游戏服务端实习生，旗舰产出是 **SQL 审计/排查工具**（远程 SQL 日志查询、异步任务、时间倒序、Excel 导出、WebSocket + 轮询兜底）。
- **对象世界的原生语言**：审计日志、脱敏条、核验印章、状态机、发布快照、兜底引擎、脱敏摘要、证据链、倒序的 SQL 日志。
  → 设计应当**长成这个世界该有的样子**，而不是又一个「卡片 + 渐变」作品集。
- **受众**：技术面试官（默认）、导师/答辩、HR、普通访客。身份只改表达，不改数据范围。
- **页面唯一的任务**：让访客**敢于相信**他们读到的东西——任何时候 status / 证据等级 / 保密审查都要可见。

由此得出**四条不可妥协的约束**（三个方向都遵守）：

1. 状态永远可见（`COMPLETED / IMPLEMENTED_TESTED / PROTOTYPE / DESIGNED / LEARNING`）。
2. 证据等级永远可见（`VERIFIED / SUPPORTED / SELF_REPORTED / INFERRED / UNVERIFIED`）。
3. 缺证据时**必须明示缺口**（文档原文：「资料不足」「现有记录不足以确认」），绝不糊过去。
4. 第 4 个项目（操作日志原型）**只能表述为原型/已实现自测，不得说已上线/已正式交付**——文档明确这条是验收红线。

---

## 1. 共享内容（三个方向都用同一份真实素材）

四个核心主题（来自 `01-项目背景.md` §4.1 与 `02-需求探索文档.md` §13 V1）：

| # | 项目 | status | contributionType | verificationLevel | 保密审查 |
|---|------|--------|------------------|-------------------|----------|
| 01 | SQL 审计/排查工具（公开化名） | COMPLETED | INDEPENDENT | VERIFIED | SAFE_GENERALIZED |
| 02 | 运营后台多语言传图修复（公开化名） | IMPLEMENTED_TESTED | PRIMARY | SUPPORTED | SAFE_GENERALIZED |
| 03 | 内部测试工具角色信息清理（公开化名） | COMPLETED | INDEPENDENT | VERIFIED | SAFE_GENERALIZED |
| 04 | 运营后台操作日志原型（公开化名） | PROTOTYPE | PRIMARY | SELF_REPORTED | SAFE_GENERALIZED |

身份切换：技术面试官 / 导师 / HR / 普通访客（默认技术面试官）。
声明：「内容来自个人记录并经人工审核，AI 负责查询与表达。」

---

## 2. 三个方向（一句话 + 调色板 + 字体 + 签名元素）

### 方向 A — 「Case File / 卷宗」
- **一句话**：这是一份**已解密、盖章、脱敏**的实习卷宗；每个结论都是被归档、核验、盖印的条目。
- **调色板（6）**：`#EDE6D6` 陈年纸 / `#1A1714` 墨黑 / `#0B0B0B` 脱敏条 / `#2E5D3B` 核验印绿 / `#B23A2E` 警示朱 / `#A8731B` 待核琥珀。
- **字体**：衬线叙事（Source Serif）+ 等宽打字机做数据标签（Courier 风 slab）+ 全大写 tracked 小标。
- **签名元素**：**可用的脱敏条**（hover 可「揭起」看脱敏后摘要）+ **倾斜核验印章**（`VERIFIED` 像真盖上去）。状态用印章封蜡呈现。
- **风险**：易显 gimmicky → 用克制的留白与严格的网格压住。

### 方向 B — 「Log Stream / 运行日志」
- **一句话**：作品集就是一条**正在运行的操作日志**；成果以倒序条目流入，严重级别即证据等级，带一个真实可见的「兜底引擎」开关。
- **调色板（5）**：`#0A0E0D` 暖黑（非 zinc）/ `#C8D4C8` 磷光字 / `#E0A526` **信号琥珀**（仅用于 status/核验，**刻意避开终端绿默认**）/ `#3A8A7B` 已核验青 / `#C24A3A` 告警红。
- **字体**：等宽（JetBrains Mono）扛所有数据 + 人类主义 sans（IBM Plex Sans）只给自然语言回答。
- **签名元素**：**持续流入的日志 hero**（带闪烁光标，条目按 `LEARNING→DESIGN→IMPL→TEST→SUBMIT` 流入）+ **兜底模式开关**：切到「兜底」时整页可见地切换引擎，直接物化文档里的兜底要求。
- **风险**：暗色终端很常见 → 用**琥珀**（而非绿）做信号色 + 把兜底开关做成核心交互来赎回独特性。

### 方向 C — 「Ops Ledger / 审计台账」
- **一句话**：一张**冷静、宽留白、可诚实地暴露缺口**的审计台账；信任来自这张表是**完整且诚实**的——缺证据的格子就是空着。
- **调色板（5）**：`#F6F4EF` 暖白纸 / `#23211D` 石墨墨 / `#2D3A5C` **墨蓝**（交互主色，**刻意避开 cream+terracotta 默认**）/ `#B8893A` 待确认赭 / `#2F6B4A` 已核验森绿。
- **字体**：中性 grotesk（Space Grotesk）做 UI + 表格用 tabular-nums + 单一 hero 用编辑级衬线（Fraunces）。
- **签名元素**：**审计台账表**——每行一个项目，列就是文档数据模型本身（`status / verification / confidentiality / contribution`）。**缺证据的格子渲染成显式 `—` 并标注「证据不足」**，绝不伪造。把文档的诚实原则做成主视觉。
- **风险**：表格易显 utilitarian → 用排版、留白、tabular figures 把「诚实的表」做成设计本身。

---

## 3. 自我评审（写代码前，对照 skill 的三点默认检查）

- 三个调色板都**不是** cream+terracotta、不是 black+acid-green、不是 zero-radius broadsheet 这三个 AI 默认聚类。✅
- 每个方向只有一个「签名元素」，其余保持克制（Chanel: 离家前摘掉一件配饰）。✅
- 编号 / eyebrow 等结构装置只在「真的是序列」时用（方向 B 的日志流入、方向 C 的台账行序），不滥用 01/02/03。✅

## 4. 文件

```
design/portfolio-design-v2/
├── README.md                 ← 本文件
├── A-case-file.html          ← 方向 A：卷宗
├── B-log-stream.html         ← 方向 B：运行日志
└── C-ops-ledger.html         ← 方向 C：审计台账
```

每个 HTML 双击即可在浏览器打开，移动端响应、键盘可见焦点、尊重 `prefers-reduced-motion`。
