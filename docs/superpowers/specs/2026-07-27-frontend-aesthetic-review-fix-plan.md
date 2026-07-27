# 前端审美评审整改方案(A/B 两级)

> **状态:** 待审核 —— 决策已按推荐项确认(2026-07-27),经批准前不实施任何代码改动
> **日期:** 2026-07-27
> **依据:** 2026-07-27 全站桌面(1440px)+ 移动(390px)截图评审,截图存于 `output/shots/`;评审已与需求方逐页核对
> **范围:** 已授权的 A 级缺陷(可验证的真 bug)与 B 级设计语言收口;C 级移动端细节与风格演进不在本轮

## 1. 背景与依据

对六个正式路由(首页 / 项目 / 项目详情 / 时间线 / 证据 / Agent)做了桌面与移动双端截图评审,并通读了对应组件与样式。结论分两级:

- **A 级(缺陷):** 选择器优先级串色、深墨块内红色漏切换、用户消息实心气泡违反设计文档、移动端大标题孤行、Agent 空态按钮裁切与提示重叠。
- **B 级(设计语言):** 网格按满数据设计但实际数据量小(1 项目 / 3 案例 / 5 证据 / 5 时间线条目)导致大面积死空;中文衬线依赖访客 OS 抽签;红色语义通胀;四个子页无页脚收尾。

数据基线(与 `docs/08` 核对一致):1 个 Project(slug `sql-audit`)、3 个 CaseStudy、5 条 APPROVED Evidence、5 条 TimelineEvent、6 个 QuestionPreset,内容版本 `2026-07-23.1`。

## 2. 整改原则

1. **最小侵入:** 不重排任何页面骨架,不改视觉契约锁定的 Agent 壳层(`--agent-stage` / 悬浮纸壳 / 四档纸色,见 `visualContract.test.ts`);只做选择器级、组件级收口。
2. **不动已审核公开数据:** 所有修改在前端展示层派生;`backend/src/main/resources/public-data/` 的 JSON 一个字节不改。
3. **TDD:** 每个工作包先在 `frontend/src/app/styles/visualContract.test.ts` 或对应组件测试写失败断言,再改实现(RED → GREEN → REFACTOR)。
4. **红色纪律:** `var(--red)` 只保留给真实强调(当前页、CTA、章标);结构性、中性的标签一律退出红色。

## 3. 工作包

### WP1 项目 / 详情 / 证据页修复包

**涉及文件:** `frontend/src/pages/ProjectsPage.vue`、`frontend/src/pages/ProjectPage.vue`、`frontend/src/pages/EvidencePage.vue`、`frontend/src/shared/components/DossierFooter.vue`(仅引用)

| 编号 | 问题 | 证据 |
|---|---|---|
| A1 | 项目页组说明被误染成红色 10px 等宽体 | `ProjectsPage.vue` 中 `.dossier-group__head p`(优先级 0,1,1)压过 `.dossier-group__note`(0,1,0);`--muted` 声明成死代码 |
| A2 | 详情页深墨块内三处红色漏切换,`--red`(#7a2e2a)压 `#201c17` 对比约 1.5:1 | `ProjectPage.vue` `.project-story__dark` 作用域内:`.section-code`、`li::before`、`.section-trace a` 仍用 `var(--red)`;文件内已有 trace 文字切换浅色红的先例可对齐 |
| B1-证据 | 证据页左列底部约 466px 死区;卡片右 1/3 空置;左右栏顶错位 32px | 截图 `output/shots/d-evidence.png` |
| B4 | 项目 / 详情 / 证据三页底部 120–207px 纯空,戛然而止 | 截图 `d-projects.png` / `d-project-detail.png` / `d-evidence.png` |

**方案:**

- A1:给组代码那个 `<p>` 加专属类 `.dossier-group__code`,把 `.dossier-group__head p` 元素选择器替换为类选择器,消除对 `.dossier-group__note` 的优先级压制。红色保留给组代码(它是编号,红色合法),组说明回到 `--muted`。
- A2:在 `.project-story__dark` 作用域内补三条覆盖,将 `.section-code`、`li::before`、`.section-trace a` 的红色切换到深底专用浅色红 token;具体用 `--red-hi` 还是 `--red-on-ink` 以实现时文件内既有深墨块先例为准(复用已在用的那个,不新增 token)。
- B1-证据:左列底部加一张"索引小结卡"(等宽体小号:本页证据数 / 覆盖项目与案例数 / 最近更新月份,数据从已有 store 派生,纯展示);证据卡片网格收窄第三列或改两列,消除右侧 1/3 空置;左右栏 `align-items: start` 对齐,修掉 32px 顶错位。具体数值实施时按截图定。
- B4:三页模板尾部接入现有 `<DossierFooter :content-version="version">`(组件已存在、内容通用、首页在用,无需改造)。

**TDD 断言:** 组件测试断言 `.dossier-group__note` 计算色为 `--muted`;`ProjectPage` 深墨块内三选择器不再解析到 `#7a2e2a`;三页均渲染 `DossierFooter`。

**验收:** 相关 vitest 全绿;`npm run build` 通过;复拍三页截图对比。

---

### WP2 时间线一致面包

**涉及文件:** `frontend/src/pages/TimelinePage.vue`

| 编号 | 问题 | 证据 |
|---|---|---|
| B3-dt | `dt { color: var(--red) }`:「问题 / 行动 / 影响」三个中性结构标签也用红,稀释红色语义 | `TimelinePage.vue` 样式(评审时 :128) |
| B1-轴 | 时间轴观感断裂:每个 article 各自 `border-right` 当轴线 + 80px 下留白;左编号柱 2500px 死柱 | `TimelinePage.vue` `.timeline-ledger__axis`;截图 `d-timeline.png` |
| B1-序 | 5 条记录既非正序也非倒序:2026.06–07、2026.07、2026.04–06、2026.06–07、2026.05 | `bundle/portfolio.json`(运行时聚合数据,字段 `timelineEvents`)实测顺序;`public-portfolio.v1.json` 仅含 1 条、非服务数据 |
| B4 | 无页脚 | 同上 |

**方案:**

- B3-dt:`dt` 颜色 `var(--red)` → `var(--muted)`。结构标签回归墨色层级,红色只留在节点与日期。
- B1-轴:连续轴线从 article 移到容器——`.timeline-ledger` 加一条通高的 `::before` 竖线,各 article 保留菱形节点、去掉各自的 `border-right` 轴线职责;左编号柱随 5 条记录自然收短。移动端 ≤620px 现有横排切换不动。
- B1-序:在现有 `computed`(按 `route.query.project` 过滤)之后加稳定排序。**排序键从 `dateLabel` 前端解析**,格式为正则 `^(\d{4})\.(\d{2})(?:–(\d{2}))?$`(en-dash U+2013,与 JSON 实测一致),取起始年月 `year * 100 + month`;**倒序,最新在前**(已确认,符合档案从最近往回翻的阅读方向);同键保持原相对顺序(现代 JS `sort` 稳定);解析失败的条目排到最后且保持原序。**不改动已审核 JSON,排序是纯展示层派生。**
- 日期粒度:**保持事实标签原样**(`2026.06–07` 与 `2026.07` 的区间/单月差异是事实,不是排版问题),只统一排序行为;en-dash 实测已一致,无需规范化。
- B4:接入 `<DossierFooter>`。

**实施记录(2026-07-27 已实现):**

- **B1-轴 实现偏离**:容器 `::before` 用 `fr` 单位无法在 `calc()` 里定位到轴线列右边缘(`fr` 不是长度)。改为等效方案:把 `article` 的 `padding-bottom: 80px` 移到 `.timeline-ledger__body`,使 `.timeline-ledger__axis` 的 `border-right` 贯穿整个 article 高度,相邻 article 的边框自然连成连续竖线,同样消除 80px 下留白断裂。移动端 `≤620px` 在断点内把 `.timeline-ledger__body` 的 `padding-bottom` 收回 `56px`(横排无需通高竖线)。
- **B1-序 dash 兼容**:生产 `bundle/portfolio.json` 实测为 en-dash U+2013,但前端预览 fixture(`previewPublicContent.ts`)用 em-dash U+2014。正则放宽为 `^(\d{4})\.(\d{2})(?:[–—](\d{2}))?$` 同时支持两种,保证测试与生产行为一致。解析失败返回 `-Infinity`(倒序沉底)。
- 排序纯函数落点:`frontend/src/features/portfolio/model/timelineOrder.ts`(对齐 `sectionTrace.ts` 的 model 函数惯例)+ 同目录 `timelineOrder.test.ts`(4 例:乱序倒序/em-dash/同键稳定/解析失败兜底)。

**TDD 断言:** 排序比较器纯函数单测(正序输入、乱序输入、同键稳定、解析失败兜底);`dt` 计算色断言;页脚渲染断言。

**验收:** 单测 + vitest 全绿;复拍截图确认轴线连续、顺序为 07 → 06–07 → 06–07 → 05 → 04–06。

---

### WP3 Agent 工作台包

**涉及文件:** `frontend/src/features/agent/components/ConversationThread.vue`、`frontend/src/features/agent/components/LocalSessionRail.vue`、`frontend/src/features/agent/components/AgentWorkspace.vue`

| 编号 | 问题 | 证据 |
|---|---|---|
| A3 | 用户消息是深墨实心气泡 + `border-radius: 12px 12px 4px 12px`,两个设计文档均未授权气泡;07-22 文档明确"用户问题用 2px 左线、不使用实心气泡" | `ConversationThread.vue` `.message--user .message__body`(评审时 :653) |
| A5 | 空会话时"清空本地会话"按钮被几何裁切至 0 像素可见;右下角绝对定位的隐私提示与证据卡重叠 | `LocalSessionRail.vue` footer;`AgentWorkspace.vue`;截图 `d-agent.png` |
| B5 | 空态左栏约 600px、中栏约 368px 空白 | 截图 `d-agent.png` |

**方案:**

- A3(**已确认:回归文档化样式**):`.message--user .message__body` 去掉实心背景与圆角,改透明底 + 2px `--workspace-accent` 左线的文本流(与 07-22 文档第 116 行一致);assistant 侧不动。现有 `visualContract.test.ts` 只断言 ≤620px `max-width: 85%`,不锁气泡,改后不破坏旧断言。若未来想恢复实心气泡,需先回写设计文档授权,再改实现。
- A5:先按系统化调试复现(空会话 vs 有会话两种状态),最小修复:空会话时"清空本地会话"按钮禁用或隐藏(同时修 footer padding/overflow 裁切);隐私提示"当前对话未保存"改放入会话栏底部行或 composer 上方静态位,不再与证据卡共用右下锚点。
- B5:空态时 thread 引导区垂直居中;会话栏补空态内容(如"会话仅存于当前标签页,刷新即清"的说明行——复用现有文案,不新增事实)。

**实施记录(2026-07-27 已实现):**

- **A5 第一部分方案与代码现状不符(已澄清)**:方案称"空会话时清空按钮被几何裁切至 0 像素",但 `LocalSessionRail.vue` 的清空按钮已有 `v-if="sessions.length"` 守卫,空会话时本就不渲染(经调试测试验证:`wrapper.find('[data-session-clear]').exists()` 为 `false`)。因此该子项已由既有守卫满足,本轮不再重复处理,只锁定现状不回退。
- **A5 第二部分(隐私提示重叠)已修**:移除 `AgentWorkspace.vue` 右下角绝对定位的 `.session-privacy` 覆盖层(`position:absolute; right:18px; bottom:4px`,压在 conversation 右下角与 jump-latest/证据区重叠);完整隐私文案"当前对话未保存,刷新后记录会消失"迁入 `LocalSessionRail.vue` footer 静态位(替换原较短文案"会话仅保留在当前标签页"),满足 AGENTS.md 8.3 安全规则对完整提示的要求。
- **B5 会话栏空态内容**:由 A5 迁入的 footer 隐私文案同时覆盖空态说明需求(复用既有文案,不新增事实),无需额外 DOM。
- **B5 thread 空态居中**:`.thread[data-conversation-state='empty']` 加 `min-height:100% + grid + align-content:center + margin-block:auto`,引导区在滚动视口垂直居中,消除中栏约 368px 顶部死空。
- 同步更新一处既有测试(`AgentWorkspace.test.ts:300` 的 footer 文案断言),从旧文案"会话仅保留在当前标签页"改为安全规则要求的完整版,这是 A5 的预期结果而非测试妥协。

**TDD 断言:** `visualContract.test.ts` 新增独立 describe:`.message--user .message__body` 不存在实心背景/border-radius、存在 2px 左边线;组件测试断言空会话时清空按钮 `disabled` 或不渲染。

---

### WP4 首页包

**涉及文件:** `frontend/src/features/home/components/PortfolioHero.vue`、`frontend/src/features/audience/components/AudienceDialogue.vue`

| 编号 | 问题 | 证据 |
|---|---|---|
| A4 | 移动端「工程实践档案」6 字孤行"案" | `PortfolioHero.vue` h1 `max-width: 10ch`(评审时 :111)+ 移动 `clamp(48px, 15vw, 62px)`(:213);390px 下 58.5px × 6 字 ≈ 335px > 10ch ≈ 290px |
| B1-首页 | Hero 右半 600×550px 死空;问答台面板底部约 45% 空;卡片按满数据定高 | 截图 `d-home.png` |

**方案:**

- A4:≤620px 断点内 `h1 { max-width: none; font-size: clamp(44px, 13.5vw, 56px) }`。验证 320px(13.5vw → 44px 下限,6 字 ≈ 252px < 276px 可用)与 390px(52.7px,6 字 ≈ 302px < 346px 可用)均不孤行、不溢出;桌面 10ch 保留(它约束拉丁名行折行,是构图一部分)。
- B1-Hero(**已确认选项 1:不动构图**):留白在纸面语言里合法;仅把现有 `::after` 放射/竖线纹理存在感略提(opacity 0.48 → 约 0.65),右下加一个竖排等宽体边注(项目代号 + 起止月,从 store 派生),DOM 只加一个元素。(选项 2"完全不动 Hero"未采纳。)
- B1-问答台/卡片:问答台面板去掉固定高、改内容自适应 + 引导内容垂直居中;首页卡片区 `grid-auto-rows` 改内容自适应。具体值实施时按截图定。

**TDD 断言:** ≤620px 规则存在 `max-width: none`(或等效放宽)的样式断言;问答台组件无固定 min-height 断言。

---

### WP5 导航包

**涉及文件:** `frontend/src/app/styles/base.css`

| 编号 | 问题 | 证据 |
|---|---|---|
| B3-nav | 当前页与「完整 Agent ↗」都是红字 + 2px 红下划线,两条红条互相稀释 | `base.css`(评审时 :225-239) |

**方案:** `.primary-nav__agent` 去掉 `::after` 红条,保留红字 + ↗(它本来就是外向入口,字色足够区分);当前页 `aria-current` 样式不动。移动端抽屉导航同样生效,一并核对。

**TDD 断言:** `visualContract.test.ts` 断言 `.primary-nav__agent::after` 不再存在红色边框声明。

---

### WP6 中文衬线字体包

**涉及文件:** `frontend/src/app/styles/tokens.css`、`frontend/index.html`、`frontend/public/fonts/`(新增)、`frontend/src/app/styles/visualContract.test.ts`

**问题(B2):** `--serif: Georgia, 'Noto Serif SC', 'Songti SC', serif`(`tokens.css:35`)。Georgia 无中文字形,中文大标题(全站签名资产「工程实践档案」)实际落到哪个字体取决于访客 OS:macOS 是宋体类,Windows 大概率黑体感,Linux 随机。品牌最重的一笔由抽签决定。

**注意:** `tokens.css:32-34` 注释记录了既定决策"仅系统字体、不加载网络字体,避免第三方请求与布局漂移"。本包是对该决策的**显式偏离**,但其动机(无第三方请求 = 隐私;无漂移)在自托管子集方案下仍然成立,注释将同步改写。

**已确认走方案 A:自托管子集化思源宋体(Noto Serif SC,OFL 1.1)**

1. 来源:googlefonts/noto-cjk 官方 release;许可证 `OFL.txt` 随字体放入 `frontend/public/fonts/`。
2. 字重:400 + 700(实施时先 grep `var(--serif)` 确认全站 serif 实际字重使用,只打包用到的)。
3. 子集化:`pyftsubset`(fonttools)按"全站当前实际用字 + GB2312 常用 3500 字 + CJK 标点/拉丁/数字"双集合生成 woff2;**体积预算:单字重 ≤ 600KB,总量 ≤ 1.2MB**,超预算则收缩到"实际用字 + 500 常用字"。
4. 接入:`@font-face`(`font-display: swap`),`--serif` 栈首换为自托管 family;**不用 `local()`**,统一走自托管文件保证跨机一致,子集外字符回退到栈内下一位('Songti SC' / serif)。`index.html` 只对 700 字重加 `preload`(首屏大标题),避免双 preload 阻塞。
5. 注释与文档:改写 `tokens.css:32-34` 注释(说明自托管同源、无第三方请求、子集化控体积);核对 `docs/04-项目代码约束.md` 是否有字体约束需同步。

**降级 fallback(仅当实施环境无外网或无 fonttools 时启用):** 保持系统栈,显式补 Windows 保底衬线:`--serif: Georgia, 'Noto Serif SC', 'Songti SC', 'STSong', 'SimSun', serif`。零成本,但 Windows 下是 SimSun 屏显效果,只解决"黑体抽签",不解决身份统一。启用 fallback 需在本文件中记录原因。

**TDD 断言:** `--serif` 栈首项断言;`@font-face` + `font-display: swap` 存在性断言;构建后脚本检查 `dist` 字体文件体积在预算内;复拍截图对比标题字形。

## 4. 不在本轮范围

- C 级移动端细节(7px fine print、证据页日期/值域折行等)——下轮单独评;
- B6 模板感风格演进(放大追溯脚注、证据编号等独特资产)——仅记录方向,不写代码;
- 详情页左目录柱填充(sticky 目录在真实浏览中可用,截图空置是 fullPage 假象,优先级低);
- CaseStudy 前端列表/详情页(属功能缺口,已记录在 `docs/00` 第 4 节,非审美问题)。

## 5. 实施顺序与依赖

- 文件归属互不重叠:WP1(三个页面文件)、WP2(TimelinePage)、WP3(agent 组件三件)、WP4(home/audience 两件)、WP5(base.css)、WP6(tokens/index.html/fonts)。
- `visualContract.test.ts` 会被 WP3 / WP4 / WP5 / WP6 追加断言:**各包只新增独立 describe 块,不改既有断言**;按 WP1 → WP6 顺序串行实施,避免同文件冲突。
- 每个 WP 完成后跑:`npm.cmd --prefix frontend test -- --run` + `npm.cmd --prefix frontend run build`;全部完成后用 `frontend/scripts/_shoot-all.mjs` 复拍桌面 + 移动全页截图,与 `output/shots/` 基线对比。

## 6. 决策汇总(2026-07-27 已按推荐确认)

| # | 决策点 | 已定 |
|---|---|---|
| 1 | A3 用户消息样式 | 回归文档红线方案:透明底 + 2px `--workspace-accent` 左线,去实心气泡;未来恢复气泡需先回写设计文档 |
| 2 | B1-Hero 右半 | 选项 1:不动构图,提纹理存在感 + 右下竖排等宽体边注 |
| 3 | WP6 字体 | 方案 A:自托管子集化 Noto Serif SC(400/700,woff2,总量 ≤ 1.2MB);无外网时降级方案 B 并记录原因 |
| 4 | 时间线排序方向 | 倒序,最新在前 |
| 5 | 工作包范围 | 六个工作包全部执行,按 WP1 → WP6 串行 |
