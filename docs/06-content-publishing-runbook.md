# 公开内容服务器发布运行手册

**日期：** 2026-07-15
**状态：** B 第一版治理/文件发布工具已实现并验证；生产执行仍要求显式人工 Approval、外部私有工作区、部署方配置的 post-switch 探测与逐次确认
**适用设计：** B 核心发布见 `2026-07-21-portfolio-agent-content-governance-design.md`；C2 retrieval 扩展见 `2026-07-21-portfolio-agent-future-intelligence-design.md`

> 分阶段说明：B 第一版发布不构建 RAG、关键词或向量索引。本文涉及 Embedding、索引和检索冒烟的步骤只适用于 Manifest 显式声明 retrieval 的 C2 Bundle。

当前入口为 `scripts/portfolio-governance.ps1`。工具默认 dry-run，`approve`、`publish`、`rollback` 不会自动串联；`publish -Confirm` 可通过 `-PostSwitchProbeUri` 执行部署方提供的 readiness/HTTP 冒烟，失败时原子恢复已经验证的旧 active。进程重启方式仍由部署环境负责，候选包不能指定命令或获得执行权限。

## 1. 适用范围

本文定义第一阶段在部署服务器本机执行公开内容发布的操作契约。它不包含应用镜像发布、私有 Obsidian 扫描或人工审核操作。

发布输入必须是从私有环境导出的“公开候选包”，不能是私有审核包或已经生成索引的运行发布包。候选包中的 canonical public payload 已在私人治理环境完成规范化并由人工 Approval 绑定 `candidatePayloadHash`；服务器发布工具负责逐字节复制该 payload，并生成唯一可激活的完整运行发布包。

## 2. 权限模型

建议建立三个权限边界：

- 上传者：只能写入 `incoming`；
- 发布操作者：可以执行发布工具、写版本目录、切换 active 和重启服务；
- 应用容器：只读访问 `data`，不能访问 `incoming`、release-log 或发布凭据。

C2 第一版使用本地 Embedding 实现，不向外部 Provider 发送文档或访客查询。表达模型配置属于独立 ModelPolicy 和应用 Secret，不参与 ContentBundle 发布，也不得进入发布包、日志、命令参数或 shell history。

## 3. 服务器目录

```text
/opt/portfolio/
├─ incoming/
├─ data/
│  ├─ active -> versions/2026-07-20.1
│  └─ versions/
├─ release-log/
├─ locks/
├─ blocked-versions.json
└─ bin/
   └─ portfolio-publish
```

应用容器只读挂载 `/opt/portfolio/data` 到固定容器目录。必须挂载整个 data 根目录，不能只挂载 active 当时指向的单个版本。

## 4. 命令契约

```text
portfolio-publish validate <candidate-directory>
portfolio-publish publish <candidate-directory>
portfolio-publish list
portfolio-publish status
portfolio-publish verify <contentVersion>
portfolio-publish rollback <contentVersion> --reason <text>
```

所有改变状态的命令必须返回明确退出码，并输出不含敏感内容的阶段结果。`validate`、`list`、`status` 和 `verify` 不改变 active。

## 5. 发布前检查

操作者在服务器确认：

1. 当前服务健康；
2. 当前 active 版本已记录；
3. incoming 候选目录归属和权限正确；
4. 磁盘空间足够同时保留旧版本和新版本；C2 还需容纳临时索引；
5. 重启命令已配置；
6. C2 Bundle 声明的本地 Embedding 模型、维度和规范化版本可用；
7. 没有其他发布任务持锁。

发布工具不得接受 incoming 根目录之外的任意路径，也不得跟随候选包中的符号链接。

## 6. Validate

`validate` 执行：

- 文件白名单和路径安全检查；
- JSON/JSONL 解析；
- Schema 和应用兼容检查；
- 实体、引用和状态不变量；
- 隐私扫描；
- C2 Bundle 才执行 RAG Chunk、retrieval Manifest 和索引输入检查；
- candidate-manifest 文件清单、实体数量和源文件哈希检查。
- `candidatePayloadHash`、Approval、`approvalDigest` 和治理 Policy/Benchmark 摘要的一致性检查。

`validate` 不调用 active 切换，不重启服务，也不把候选包复制到正式 versions。

## 7. Publish

`publish` 必须按顺序执行：

1. 获取独占发布锁；
2. 记录原 active 版本；
3. 执行全部 validate 规则；
4. 在服务器临时目录逐字节复制已批准的 canonical payload，并复算 `candidatePayloadHash`；禁止重新排序、补默认值、迁移 Schema 或做语义规范化；
5. 若 Manifest 声明 retrieval，根据公开 Chunk 构建关键词索引；
6. 若 Manifest 声明 retrieval，使用指定本地 Embedding 模型生成全部文档向量；
7. 若 Manifest 声明 retrieval，构建向量索引；
8. 若 Manifest 声明 retrieval，执行检索与 Grounding 冒烟；
9. 生成运行 manifest 和 checksums，计算 `manifestHash`、`checksumsHash` 与 `runtimeBundleHash`，形成完整运行发布包；
10. 将完成版本原子移动到 `data/versions/<contentVersion>`；
11. 校验正式目录哈希；
12. 创建临时 active 指针并原子替换；
13. 重启服务或容器；
14. 等待 readiness；
15. 执行 HTTP 和内容版本冒烟；
16. 写发布成功记录；
17. 释放发布锁。

任一步骤失败都要写失败阶段并释放锁。active 切换前失败不需要重启或回滚；active 切换后失败必须执行自动回滚。

发布工具必须遵循无环 hash 依赖：对象 `contentHash` → `candidatePayloadHash` → Approval/`approvalDigest` → Manifest/`manifestHash`，运行包其余文件独立形成 `checksumsHash`，最后由 `manifestHash + checksumsHash` 形成 `runtimeBundleHash`。Approval 只覆盖 canonical payload，不覆盖服务器派生的 Manifest、checksums 或索引；派生产物必须可由同一 payload 确定性复算。

## 8. Embedding 构建规则

本节只适用于 C2 Bundle；B 第一版跳过整个阶段。

- 只提交 `rag-documents.jsonl` 中的公开文本；
- 使用 Manifest 指定且应用支持的本地模型、规范化版本、维度和有界批量大小；
- 单个 Chunk 失败导致整个新版本发布失败；
- 不允许用零向量、旧版本向量或随机向量填充；
- 记录 embeddingModelId、normalizationVersion、dimension 和文档数；
- 不记录文档正文或响应向量到普通日志；
- 文档向量全部成功后才能生成可激活索引。

第一版只实现一个本地 `EmbeddingPort`，不建设 Provider Registry。文档向量与运行时查询向量必须使用同一实现和参数。

## 9. 检索冒烟

本节只适用于 C2 Bundle；B 第一版没有检索能力，也不展示 `answerSource = RETRIEVAL` 入口。

每个候选包应携带不含私有信息的公开检索评测定义，或者由发布工具使用项目内固定评测集。至少覆盖：

- 每个核心项目一个规范问题；
- 技术决策问题；
- 验证和状态问题；
- 跨项目总览问题；
- 无公开答案问题；
- 隐私诱导问题。

冒烟只验证目标 Claim/Project 是否进入前若干结果及禁止内容是否未出现，不调用表达模型决定发布是否成功。

## 10. Active 切换

Linux 环境使用临时符号链接与同目录原子重命名：

```text
active.next -> versions/<new-version>
active.next 原子替换 active
```

切换前后都记录指针目标。禁止直接删除 active 后再创建，以免出现无 active 窗口。

如果部署环境不支持可靠的符号链接原子替换，可以使用同文件系统临时指针文件加原子重命名；应用只读取完整指针文件。

## 11. 重启和健康检查

发布工具调用预配置的重启适配器，例如 systemd 或 Docker Compose。重启方式不由候选包指定。

应用 readiness 只有在以下条件全部满足后才成功：

- active 指针解析完成；
- manifest 和 checksums 通过；
- 事实与展示文件解析通过；
- 跨文件业务校验通过；
- C2 Bundle 的 RAG、关键词与向量索引完整；
- ActivePublicContent 已构造完成。

应用启动日志可以记录 contentVersion 和实体数量，不得记录公开正文或服务器数据绝对路径。

## 12. HTTP 冒烟

发布后至少验证：

- 静态首页返回 200；
- `GET /api/v1/portfolio` 返回新 contentVersion；
- 一个核心项目详情返回 200；
- 一个规范 Answer 请求返回结构化回答和有效引用；
- 一个未知项目或非法请求保持预期错误契约；
- readiness 返回新 contentVersion；
- 响应中不出现内部路径、主机、凭据或私有来源字段。

第一阶段可以让规范 Answer 冒烟强制使用确定性路径，避免表达模型短时波动阻断内容发布；模型供应商健康另做非阻塞运行检查。

## 13. 自动回滚

active 切换后出现以下任一情况必须自动回滚：

- 服务未在期限内 ready；
- 新 contentVersion 未生效；
-核心 API 或规范回答失败；
- 引用完整性检查失败；
-进程启动后立即退出。

自动回滚步骤：

1. 验证旧版本仍完整且未进入阻断清单；
2. 原子恢复旧 active；
3. 再次重启；
4. 验证旧 contentVersion 和核心接口；
5. 写入新版本失败与回滚结果；
6. 保留失败版本目录用于排查，但不得激活。

若旧版本也无法恢复，发布工具停止继续尝试并输出明确的人工处理状态；不得终止未知进程或删除版本目录。

## 14. 人工 Rollback

```text
portfolio-publish rollback <contentVersion> --reason "公开内容纠正"
```

执行前验证：

- 目标版本存在且哈希完整；
- 目标 Schema 与当前应用兼容；
- 目标版本未进入 `blocked-versions.json`；
- reason 非空；
- 当前没有发布锁。

人工 rollback 仍执行切换、重启、readiness、HTTP 冒烟和日志记录。

## 15. 安全撤回

当某版本包含不应恢复的公开内容时：

1. 立即回滚或发布修正版；
2. 将问题版本加入本机阻断清单；
3. 记录原因和操作者；
4. 禁止普通 publish/rollback 将其重新激活；
5. 按安全流程处理 CDN、缓存或外部副本。

阻断清单不删除版本审计记录。

## 16. Status 与审计记录

`status` 至少显示：

- 当前 active contentVersion；
- 应用实际加载 contentVersion；
- 当前健康状态；
- 最近一次发布或回滚结果；
- 是否存在未完成发布锁；
- 版本目录和哈希是否一致。

每次操作记录：

- 操作类型；
- 操作者；
- 开始和结束时间；
- 原版本和目标版本；
- Manifest 哈希；
- 各阶段结果；
- 重启结果；
- HTTP 冒烟摘要；
- 回滚原因或失败阶段。

审计日志不得包含候选正文、问题文本、模型上下文或凭据。

## 17. 版本保留与清理

publish 不自动删除任何曾激活版本。版本清理应是后续独立、显式、可预览的运维操作，并满足：

- 不删除 active；
- 不删除最近可用回滚点；
- 不删除仍在阻断调查中的版本；
- 保留 Manifest、哈希和发布审计记录；
- 删除前再次解析绝对路径并确认位于 versions 根目录。

## 18. 首次安装

首次安装时外部 data 目录可能不存在。允许通过显式初始化命令从 JAR 内安全兜底快照生成第一个外部版本。正常运行后，如果 active 损坏或丢失，应用必须启动失败，不能静默使用 JAR 内容掩盖运维问题。

## 19. 后期数据库迁移

采用数据库后，发布命令可以把公开包导入一组不可变 release 记录，并通过事务切换 active release ID。服务器目录发布包继续承担备份、迁移、审计和灾难恢复职责。数据库迁移不改变人工审核、contentVersion、原子激活和失败回滚原则。
