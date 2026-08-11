# Semantic Turn Routing 前端原型提示词

请为现有作品集 Agent 工作台制作一份高保真、可交互的 HTML 前端原型，用于验证“真正多任务 Agent”的计划确认、结构化澄清、部分成功和任务状态摘要。

开始前完整阅读：

1. `docs/superpowers/prototypes/2026-08-10-semantic-turn-routing-prototype-brief.md`
2. `docs/13-Agent对话体验与智能编排改造路线图.md`
3. `docs/superpowers/specs/2026-08-06-agent-answer-composition-design.md`
4. `docs/08-当前实现状态.md`
5. `design/demos/作品推荐-V1保守-对话内嵌.html`

可将以下文件作为非权威探索参考：

- `design-exploration/agent-clarification-comparison/clarification-comparison.html`
- `design-exploration/agent-clarification-comparison/comparison-component.html`

目标不是重新设计整站，而是在当前 Agent Conversation Thread 中找到自然、克制、可信的多任务交互方式。保持现有暖黑舞台、米色作品窗口、Evidence Desk 和响应式抽屉的视觉语言。

必须制作一个可交互原型，并能切换或按流程进入以下状态：

1. 单任务成功：默认不展示任务摘要；
2. 三项以内多任务自动执行：展示紧凑可折叠摘要；
3. 五项复杂计划：执行前展示计划、约束与“继续/调整/取消”；
4. 局部澄清：明确部分可以继续，比较任务等待选择第二个主体；
5. 上游关键歧义：整个依赖链等待主体澄清；
6. 部分成功：成功、证据不足、阻塞和通用解释同时存在；
7. 计划失效：明确重新生成，不静默替换；
8. 全局安全边界：整轮终止，不显示部分计划。

为 Plan Confirmation、Clarification、Compact Task Summary、Task Status Summary 和 Plan Invalidated Notice 设计一致的视觉语法。任务图在界面中应转换成普通用户能理解的目标与顺序，不要呈现开发者 DAG 编辑器，不展示模型推理、Prompt、内部工具参数、检索评分或异常堆栈。

复杂计划示例：

```text
1. 介绍 SQL 审计项目
2. 介绍 ABTest 项目
3. 比较两个项目
4. 推荐一个适合后端面试展示的项目
5. 总结面试展示策略
```

明确展示约束“后端 / 面试展示 / 推荐 1 个”，但不要使用夸张的排行榜、最佳命中或拟人化思考文案。

部分成功示例：

```text
介绍 A：成功
介绍 B：公开证据不足
比较 A/B：被依赖阻塞
推荐：被依赖阻塞
通用技术解释：成功
```

正文与任务状态摘要必须分开；保留所有安全完成的正文，不为失败或阻塞任务生成伪正文。作品集事实与通用知识必须明确区分来源。

原型要求：

- 使用本地 Mock 数据，不接后端；
- 保留当前 Agent 工作台总体布局；
- 同时适配桌面和移动端；
- 无横向溢出；
- 键盘可操作，焦点清晰；
- 支持 reduced motion；
- 使用真实中文内容，不使用 Lorem ipsum；
- 不增加无关 Dashboard、统计卡或装饰性功能；
- 优先验证信息层级、状态理解和操作路径。

请先给出 2～3 种“计划确认与任务状态呈现”方向的简要对比，说明各自取舍，并推荐一种；得到确认后再完成完整交互原型。最终同时提供桌面与移动关键状态，附一份简短的原型验收说明，逐项对应 Brief 第 9 节。
