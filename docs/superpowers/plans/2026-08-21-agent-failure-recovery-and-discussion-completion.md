# Agent 失败恢复与项目讨论补完实施计划
<!-- DOCUMENT_STATUS: ACTIVE -->

> **批准设计：** `docs/superpowers/specs/2026-08-21-agent-failure-recovery-and-discussion-completion-design.md`
> **Guardian：** 已批准 LEVEL_3；未经再次授权不提交或推送
> **当前进展：** Slice 1—4 与确定性高风险门已完成；等待单独授权运行真实 Provider 质量与完整语义矩阵后关闭台账。

## Replacement Slice 1：提交身份与 pending generation

- [x] RED：clarification 失败重试、handoff replay 失败重试保持完全相同 fingerprint 输入。
- [x] RED：取消 A、提交 B、A 迟到完成不得删除 B pending。
- [x] 新增内存态 `TurnSubmissionSnapshot`，`runTurn` 与 retry 只消费 snapshot。
- [x] 修正真正 idempotency conflict 的不可重试文案。
- [x] 运行 Frontend focused/full/check/build。

## Replacement Slice 2：V5 Clarification reservation

- [x] RED：不同 requestId 活预约互斥，同 requestId 可继续；过期预约可回收。
- [x] RED：执行或 settlement 失败不消费 challenge；complete 成功同时消费与结算。
- [x] 新增 V5 migration、closed reservation/settlement mutation 与公共错误。
- [x] Memory/PostgreSQL 同合同，cancel 按 owner 释放，cleanup/expiry 不泄露状态。
- [x] 删除执行阶段直接 `consumeClarification` 的生产写路径。

## Replacement Slice 3：Discussion 语义、TTL 与 Session writer

- [x] RED：单候选 NEEDS_CLARIFICATION 不进入；明确 ENTER 直接进入。
- [x] RED：ENTER 不受 Recommendation expiry 裁剪，三种 transition 只受 discussion/session 裁剪。
- [x] RED：两条 PostgreSQL replacement 路径清 pointer、revision+1，并与 InMemory parity。
- [x] 抽取 package-private Session writer，保持 complete 原事务。
- [x] 补 SWITCH、ASK discussion override、ACTIVE REENTER fail-closed 测试。

## Replacement Slice 4：V6 revision 与权威响应

- [x] 扩展 Session/Response/Summary/Frontend typed contract：pointer + revision。
- [x] REPLACE/CLEAR/replacement 递增，GUARD 不递增。
- [x] 初次完成与 authenticated/tentative replay 都返回当前权威 summary，不返回历史 pointer。
- [x] 前端按 revision 单调应用并删除热路径 post-turn GET；GET 只做冷恢复。
- [x] TTL、恢复动作、malformed 显式错误、DISABLED action guard 与严格 discussionAction 全部闭合。
- [x] 更新共享 fixtures 和前后端 Golden tests。

## Replacement Slice 5：高风险门与完成

- [x] 接入真实 General Quality canary。
- [x] packaged HTTP absolute timeout 与 BODY_STALL fail-hard。
- [x] privacy typed credential literal 负例与规则修复。
- [x] V4/V5/V6 local readiness 与 migration 验证。
- [ ] 桌面/移动 14 场景矩阵、PostgreSQL restart/reclaim、真实 Provider semantic lane。
- [ ] 全量 Backend/Frontend、clean package、privacy、documentation、architecture、Docker check。
- [ ] 删除 A2-22—A2-29，更新 docs/08、docs/11、计划状态和 architecture COMPLETE。

## 提交边界

获得明确提交授权后按 Slice 创建中文小提交；不暂存或提交无关文件。回退只使用版本级 Git/JAR，不保留 runtime 双栈。
