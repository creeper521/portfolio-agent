# 公开内容服务器发布运行手册

**日期：** 2026-07-15  
**状态：** 待用户统一审核  
**适用设计：** `docs/superpowers/specs/2026-07-15-dynamic-public-content-agent-design.md`

## 1. 适用范围

本文定义第一阶段在部署服务器本机执行公开内容发布的操作契约。它不包含应用镜像发布、私有 Obsidian 扫描或人工审核操作。

发布输入必须是从私有环境导出的“公开候选包”，不能是私有审核包或已经生成索引的运行发布包。服务器发布工具负责生成唯一可激活的完整运行发布包。

## 2. 权限模型

建议建立三个权限边界：

- 上传者：只能写入 `incoming`；
- 发布操作者：可以执行发布工具、写版本目录、切换 active 和重启服务；
- 应用容器：只读访问 `data`，不能访问 `incoming`、release-log 或发布凭据。

Embedding 和表达模型凭据由服务器 Secret 或环境配置管理。发布包、日志、命令参数和 shell history 不得出现 API Key。

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
4. 磁盘空间足够同时保留旧版本、新版本和临时索引；
5. 重启命令已配置；
6. Embedding 配置可用；
7. 没有其他发布任务持锁。

发布工具不得接受 incoming 根目录之外的任意路径，也不得跟随候选包中的符号链接。

## 6. Validate

`validate` 执行：

- 文件白名单和路径安全检查；
- JSON/JSONL 解析；
- Schema 和应用兼容检查；
- 实体、引用和状态不变量；
- 隐私扫描；
- RAG Chunk 内容与哈希检查；
- candidate-manifest 文件清单、实体数量和源文件哈希检查。

`validate` 不调用 active 切换，不重启服务，也不把候选包复制到正式 versions。

## 7. Publish

`publish` 必须按顺序执行：

1. 获取独占发布锁；
2. 记录原 active 版本；
3. 执行全部 validate 规则；
4. 在服务器临时目录规范化公开文件；
5. 根据公开片段构建关键词索引；
6. 批量调用 EmbeddingProvider 生成全部文档向量；
7. 构建向量索引；
8. 执行检索评测冒烟；
9. 生成运行 manifest 和 checksums，形成完整运行发布包；
10. 将完成版本原子移动到 `data/versions/<contentVersion>`；
11. 校验正式目录哈希；
12. 创建临时 active 指针并原子替换；
13. 重启服务或容器；
14. 等待 readiness；
15. 执行 HTTP 和内容版本冒烟；
16. 写发布成功记录；
17. 释放发布锁。

任一步骤失败都要写失败阶段并释放锁。active 切换前失败不需要重启或回滚；active 切换后失败必须执行自动回滚。

## 8. Embedding 构建规则

- 只提交 `rag-documents.jsonl` 中的公开文本；
- 使用可配置批量大小、超时和有限重试；
- 单个 Chunk 失败导致整个新版本发布失败；
- 不允许用零向量、旧版本向量或随机向量填充；
- 记录 provider 标识、model、dimension 和文档数；
- 不记录请求正文、响应向量或 API Key 到普通日志；
- 文档向量全部成功后才能生成可激活索引。

供应商协议尚未锁定，由 `EmbeddingProvider` adapter 适配。发布工具不能把某个供应商 HTTP 格式扩散到内容契约和业务代码。

## 9. 检索冒烟

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
-事实与展示文件解析通过；
-跨文件业务校验通过；
-关键词和向量索引完整；
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
