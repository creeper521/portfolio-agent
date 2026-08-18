# Agent 架构收敛后端回退方案

- **基线提交：** `9980068dec8fa33b06ce59fa27b0de1427b54603`
- **适用范围：** Slice 1～6 架构替换期间及首次生产发布前后
- **原则：** 回退使用完整代码/JAR/部署单元，不在生产运行时保留旧 Router、Coordinator、DTO、API 或兼容开关

## 当前未提交工作树

当前仓库包含用户未提交与未跟踪资产，禁止通过 `git reset`、`checkout`、`restore` 或批量覆盖回退。开发期间的回退必须：

1. 先保存并核对完整 `git status --short`；
2. 只反向应用本次架构收敛明确拥有的文件变更；
3. 不处理任务开始前已存在的修改；
4. 每次 Replacement Slice 保持文件级清单和验证证据，使回退目标可逐文件辨认；
5. 未经用户授权不创建 commit、branch 或 tag。

## 首次发布回退

首次部署只允许整体回退到上一份已验证制品：

1. 停止接收新 Agent Turn；公开内容页面继续可用；
2. 切换上一份已验证 JAR/镜像和对应部署配置；
3. State schema 在首次生产前可重建；若已产生短期 State，则撤销 Token、清空 Context/Challenge/Replay，不迁移回旧业务模型；
4. Public Content Release Bundle 不回退、不修改，除非独立内容发布流程要求；
5. 重新运行 readiness、公开内容 API、Agent API、隐私和核心行为冒烟；
6. 记录回退制品身份、ContentReleaseId、State 清理结果和失败原因，不记录问题或答案正文。

## 禁止的“回退”形式

- 不保留 `LEGACY/SHADOW/MODEL_LED` 运行模式；
- 不保留旧 `/api/v2/answers` 或 `/api/v2/conversation-context` 别名；
- 不保留 new-to-old Plan/Outcome/Response converter；
- 不通过 Memory Store 自动接管 Production PostgreSQL State；
- 不把旧测试构造器、Compatibility Adapter 或双 DTO 当作回退机制。

该方案保留可恢复性，同时不破坏首次生产只有一条调用链、一个合同和一个 API 表面的目标。
