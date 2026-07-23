# Agent 正式页响应式作品窗口迁移设计

> **状态：** 已实施并通过单元测试、构建、Playwright 与多视口视觉验收  
> **日期：** 2026-07-23  
> **适用项目：** `D:\code\agent`  
> **目标路由：** `/agent`  
> **视觉基准：** `design-exploration/warm-cream-compare.html` 的 B 暖调 1 档＋牛血红 CTA  
> **前置确认：** 用户已确认 C「混合式响应布局」、完整圆角外壳、明显深色舞台留边，并通过 Demo 视觉验收

## 1. 迁移结论

正式 `/agent` 页面采用与已确认 Demo 一致的作品窗口结构，但不复制 Demo 的静态简化：

1. `site-frame--workspace` 成为唯一的 Agent 作品外壳。
2. Agent Header 与三栏工作区共同位于外壳内。
3. 大屏显示深色舞台、完整圆角和阴影；中屏减少留边；手机恢复全屏。
4. 三栏默认值改为 `250px / 1fr / 340px`，证据栏不再按视口约 31% 分配。
5. 保留正式页已有的分栏拖拽、键盘调整、旧宽度持久化兼容、响应式抽屉、焦点恢复和隐私边界。
6. 保留当前工作区中已经实施但未提交的暖燕麦中栏与牛血红发送按钮，不重写、不回退。

本次只迁移 Agent 页面壳层和视觉密度，不影响首页、项目页、时间线、证据中心或后端运行能力。

## 2. DOM 与职责边界

现有 `App.vue` 结构已经满足迁移所需：

```vue
<div class="site-frame" :class="{ 'site-frame--workspace': workspace }">
  <DossierHeader :compact="workspace" />
  <RouterView />
</div>
```

不增加额外 `AgentShell` 组件。职责固定如下：

- `#app`：提供深暖墨色舞台底色。
- 普通 `.site-frame`：继续用纸色覆盖整个普通路由页面。
- `.site-frame--workspace`：负责作品窗口宽度、高度、留边、圆角、裁切和阴影。
- `.dossier-header`：普通路由继续 fixed；Agent 路由内改为 absolute，定位在作品窗口顶部。
- `AgentWorkspace`：只负责 Header 以下的可用内容区和三栏／抽屉。

采用现有壳层而不是新建组件，可避免重复路由判断和第二套页面框架。

## 3. 全局视觉令牌

在 `frontend/src/app/styles/tokens.css` 中加入：

```css
--agent-stage: #2a2620;
--agent-header: #efe7d8;
--agent-shell-max: 1600px;
--agent-shell-radius: 16px;
--agent-shell-shadow: 0 30px 80px rgba(0, 0, 0, 0.5);
```

保留当前已新增的：

```css
--warm: #f3e8d6;
```

这些令牌只服务 Agent 壳层，不新增颜色家族。正式色盘仍限于纸张色、墨色、灰褐、规则线和暗红。

## 4. 三档响应布局

### 4.1 大屏 `>=1440px`

```text
舞台留边：clamp(24px, 2vw, 32px)
外壳最大宽度：1600px
外壳高度：100dvh - 上下留边
外壳圆角：16px
外壳阴影：0 30px 80px rgba(0, 0, 0, 0.5)
```

外壳在视口中水平居中，Header 和工作区由同一个 `overflow: hidden` 裁切边界形成四个完整圆角。

### 4.2 中屏 `981–1439px`

```text
舞台留边：16px
外壳宽度：calc(100% - 32px)
外壳高度：calc(100dvh - 32px)
外壳圆角：12px
```

现有响应式功能继续生效：

- `<=1279px`：证据栏进入抽屉。
- `<=980px`：会话栏也进入抽屉。

不保留原来的 `1221–1279px` 压缩三栏：在扣除 `16px` 外壳留边后，三栏最低宽度会逼近可用宽度，容易产生横向溢出。`1280px` 是完整三栏的最低视口宽度。

### 4.3 手机 `<=980px`

```text
舞台留边：0
外壳宽高：100% × 100dvh
外壳圆角：0
外壳阴影：none
```

手机不保留装饰性留边。Header、对话区和 Composer 使用全部可用宽度，抽屉继续覆盖在工作区上方。

## 5. Header 迁移

### 5.1 主题

`DossierHeader.vue` 的主题规则从：

```ts
route.meta.workspace === true ? 'ink' : 'paper'
```

改为：

```ts
route.meta.workspace === true ? 'warm' : 'paper'
```

新增 `[data-header-theme='warm']`：

```text
背景：var(--agent-header)
文字：var(--ink)
边线：var(--rule) 的弱化版本
当前 Agent：var(--red) 文字＋底部 1px 红线
```

普通路由仍使用 `paper`，不受影响。

### 5.2 定位

普通 `.dossier-header` 继续 `position: fixed`。

仅在 `.site-frame--workspace` 内：

```css
position: absolute;
inset: 0 0 auto;
width: 100%;
```

这样 Header 会被 Agent 外壳圆角裁切，同时不改变其他路由的固定导航行为。

## 6. 高度与滚动

`.site-frame--workspace` 使用全局 `box-sizing: border-box`：

- 总高度由当前响应档的 `100dvh - 留边` 决定。
- `padding-top: var(--header-height)` 为 Header 预留空间。
- Header 下方的 `AgentWorkspace` 使用 `height: 100%`。

以下桌面组件从 `height: calc(100vh - var(--header-height))` 改为 `height: 100%`：

- `AgentWorkspace`
- `LocalSessionRail`
- `ConversationThread`
- `EvidenceDesk`

抽屉和遮罩必须相对 `AgentWorkspace` 定位，不能相对整个 viewport 使用 `fixed`，否则会越过中屏作品窗口的圆角与 `16px` 留边。进入抽屉状态时统一通过：

```css
position: absolute;
inset-block: 0;
height: 100%;
```

使其占满作品窗口内 Header 以下区域，并继续由外壳 `overflow: hidden` 裁切。

对话区内部继续独立滚动；页面外层不得产生横向滚动。短视口允许壳层内容自身滚动，不把三栏高度硬撑出视口。

加载、错误与失效 handoff 状态也必须使用 Agent 壳层的 `height: 100%`，不能继续按完整视口重复减 Header。

## 7. 三栏默认值与兼容

### 7.1 新契约

```text
会话栏默认：250px
会话栏边界：220–320px
证据栏默认：340px
证据栏边界：300–420px
对话栏最低：640px
```

`workspaceDefaults()` 返回固定的已批准默认值：

```ts
{
  sessions: 250,
  evidence: 340,
}
```

不再根据整个浏览器视口计算证据栏 `31%`。作品窗口已经有最大宽度，固定默认值更稳定。

### 7.2 旧持久化宽度

继续使用：

```text
portfolio.workspace.split.v1
```

不迁移、不更名。读取旧值时经过新边界 `clampWorkspaceSplit()`：

- 旧 evidence `420–760px` 自动收敛到 `420px`。
- 旧 sessions `320–380px` 自动收敛到 `320px`。
- 非法 JSON 和非数值继续回退新默认值。

这保留用户调宽行为，同时不会让旧证据栏继续占据三分之一页面。

在 `1280–1411px` 的窄桌面区间，存储偏好上限 `320px + 420px` 与对话栏最低 `640px` 可能超过作品窗口实际宽度。此时：

- `localStorage` 中的偏好值保持不变；
- 渲染层按两侧栏各自可压缩余量等比计算有效宽度；
- 有效宽度始终给对话栏保留至少 `640px`；
- 分隔条位置和 `aria-valuenow` 使用有效宽度，不显示不可实现的存储值；
- 用户主动调整分隔条时才把新的可实现宽度写回同一个存储键。

## 8. 栏内密度

正式组件目标值：

| 区域 | 当前 | 迁移后 |
|---|---:|---:|
| 会话栏水平 padding | `23px` | `20px` |
| 对话 Header 水平 padding | `35px` | `28px` |
| 对话正文左右可用边界 | `28px` 基准 | 保持最大阅读宽度，不随中栏无限扩张 |
| Composer 左右定位 | `35px` | `28px` |
| 证据栏水平 padding | `25px` | `20px` |

只收紧栏级水平节奏，不降低正文可读字号，不给内部消息或面板增加大圆角。

## 9. 当前暖调与 CTA 变更的归属

正式工作区当前已有未提交改动：

- `tokens.css`：新增 `--warm: #f3e8d6`。
- `AgentWorkspace.vue`：中栏使用 `var(--warm)`。
- `AgentWorkspace.vue`：新增牛血红 action token。
- `ConversationThread.vue`：发送按钮使用牛血红及深红 hover。
- `visualContract.test.ts`：已更新对应视觉契约。

本次迁移把这些改动视为用户已确认的 B＋CTA 基线。实施时只在其上增加作品窗口，不覆盖或恢复旧深墨发送按钮。

## 10. 文件修改边界

预计修改：

- `frontend/src/app/styles/base.css`
- `frontend/src/app/styles/tokens.css`
- `frontend/src/shared/components/DossierHeader.vue`
- `frontend/src/shared/components/DossierHeader.test.ts`
- `frontend/src/pages/AgentPage.vue`
- `frontend/src/features/agent/components/AgentWorkspace.vue`
- `frontend/src/features/agent/components/ConversationThread.vue`
- `frontend/src/features/agent/components/LocalSessionRail.vue`
- `frontend/src/features/agent/components/EvidenceDesk.vue`
- `frontend/src/features/agent/composables/useWorkspaceSplit.ts`
- `frontend/src/features/agent/composables/useWorkspaceSplit.test.ts`
- `frontend/src/app/styles/visualContract.test.ts`
- `frontend/e2e/portfolio.spec.ts`
- `docs/04-项目代码约束.md`
- `docs/00-文档状态索引.md`

不修改后端、API、公开内容、消息结构、会话隐私、路由 seed 或 C1/C2/C3 能力。

## 11. 测试策略

严格按 RED→GREEN：

1. `DossierHeader.test.ts` 先要求 Agent 路由使用 `warm`。
2. `useWorkspaceSplit.test.ts` 先要求新边界、固定默认值和旧值 clamp。
3. `visualContract.test.ts` 先锁定舞台、外壳、圆角、三栏默认宽度、暖 Header、红 CTA。
4. 组件测试确认现有抽屉、键盘、焦点和会话行为无回归。
5. Playwright 更新旧的深 Header／双深墨按钮断言，并检查：
   - `2048×1080`：外壳 `<=1600px`、`16px` 圆角、三栏可见。
   - `1440×900`：大屏留边与 `16px` 圆角。
   - `1279×900` 与 `1219×900`：`12px` 圆角、证据抽屉可开关。
   - `980×800`：`0px` 圆角、双抽屉可开关。
   - `390×844`：无横向溢出、Composer 可用。
6. 截图必须使用相同真实公开内容和相同对话状态。

验证命令：

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend run test:e2e
```

## 12. 文档治理

正式迁移通过后：

1. 更新 `docs/04-项目代码约束.md` 的分栏边界与 Agent 壳层断点。
2. 把本设计状态改为“已实施并验证”。
3. 更新 `docs/00-文档状态索引.md`，说明暖燕麦、牛血红 CTA 与响应式作品窗口共同构成当前 Agent 视觉基线。
4. 旧的浅色工作台设计保留为历史前序，不删除。

## 13. 非目标

- 不把普通路由放进作品窗口。
- 不给三栏内部重复增加圆角卡片。
- 不修改对话回答结构或证据内容。
- 不新增 UI 框架、状态库或动画依赖。
- 不恢复深色 Agent Header。
- 不在本次顺带重构 DossierHeader 导航信息架构。
- 不 stage、commit 或 push，除非用户另行明确授权。

## 14. 验收结论

迁移完成必须同时满足：

1. 大屏效果与已确认 B＋CTA Demo 保持同一视觉语法。
2. Header 和三栏由同一个圆角外壳裁切。
3. 证据栏默认值为 `340px`，旧大宽度自动 clamp。
4. 桌面可拖拽与键盘分栏行为继续工作。
5. 中屏／手机抽屉的 Escape、遮罩和焦点恢复无回归。
6. 页面无横向溢出，五个规定视口截图通过肉眼检查。
7. 单元测试、构建和前端 E2E 全绿。
