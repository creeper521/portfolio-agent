# 本地文件日志、实时查看与七日归档设计

> **状态：** 已确认，待实施  
> **日期：** 2026-07-31  
> **范围：** 一键启动下的后端、浏览器与 Vite 本地日志落盘、实时查看、每日 ZIP、七日保留和崩溃恢复  
> **明确不包含：** ELK/Loki、OpenTelemetry、云日志平台、日志管理网页、用户行为分析、数据库审计

## 1. 背景与现状判断

项目已有日志系统的安全基础：

- 后端拥有类型化 `DiagnosticEvent`、统一发布接口、请求关联和稳定事件名；
- Agent、Provider、检索、Tool、校验和 fallback 已有安全诊断事件；
- 本地环境输出可读文本，生产环境输出 ECS JSON；
- 前端具有内存诊断队列、受限事件 DTO 和 best-effort 上报入口；
- 隐私门禁禁止问题、回答、Prompt、凭据、请求体和 Provider 载荷进入日志。

但本地运维闭环尚未完成：

- 后端和 Vite 只输出到控制台；
- `start-local.ps1` 使用隐藏子进程，启动后无法重新连接子进程控制台；
- 没有仓库内统一 `logs` 目录；
- 没有按后端/前端、INFO/ERROR 拆分文件；
- 没有每日轮转、ZIP 验证、七日保留和磁盘总量上限；
- 浏览器诊断入口默认关闭，事件没有本地文件落点；
- 没有可跨文件、分片和轮转工作的实时查看器。

因此当前日志系统属于“安全事件契约基本完成、本地文件运维能力未完成”，不能视为完整。

## 2. 已确认决策

1. 日志存放在仓库根目录的 `logs/`，整个目录加入 `.gitignore`。
2. 活动日志严格保持四类：
   - `backend-info.log`
   - `backend-error.log`
   - `frontend-info.log`
   - `frontend-error.log`
3. INFO 文件接收 `DEBUG / INFO / WARN`，ERROR 文件只接收 `ERROR`；同一 ERROR 不重复写入 INFO。
4. 前端日志同时包含浏览器安全诊断和 Vite 输出，通过 `BROWSER`、`VITE` 来源区分。
5. 每个自然日生成一个 ZIP，包含当天四类日志及其分片。
6. 日归档按归档日期保留最近七个完整自然日；没有运行日志的日期不生成空 ZIP。
7. 归档总量不超过 2 GB。
8. 每个活动文件最大 20 MB，最多五个分片，保留最新约 100 MB。
9. 开发阶段通过“启动补归档 + 跨零点归档 + 手动快照”覆盖间歇运行。
10. 启动器默认安静运行，同时支持 `-FollowLogs` 和独立查看脚本。
11. 日志失败不能使前后端业务进程退出。
12. 延续现有永久隐私禁令，不因本地文件日志而放宽。

## 3. 方案选择

### 3.1 采用：启动脚本统一日志路由

`start-local.ps1` 异步接收 Spring Boot 与 Vite 的 stdout/stderr，通过单一日志路由器分类并落盘。
浏览器诊断先进入后端受限接口，再以 `event.origin=browser` 出现在后端诊断流中，由路由器转入前端文件。

```text
Spring Boot stdout/stderr ─┐
                           ├─ LocalLogRouter ── 四个活动日志文件
Vite stdout/stderr ────────┤
                           │
浏览器诊断 → 后端接口 ─────┘
```

该方案只有一个文件写入者，可避免 Windows 下 Java 与 PowerShell 抢写同一文件，也能统一处理轮转、压缩、保留和实时查看。

### 3.2 不采用：Logback 与 PowerShell 分别写文件

后端/浏览器由 Logback 写文件、Vite 由 PowerShell 写文件会造成多个进程并发追加前端日志。文件锁、顺序和轮转边界难以可靠协调。

### 3.3 暂不采用：独立日志采集服务

Fluent Bit、Vector、Loki 等适合多实例生产环境，但会给当前本地项目增加不必要的安装和运行依赖。生产继续保持标准输出，未来由部署平台接管。

## 4. 组件边界

### 4.1 `scripts/start-local.ps1`

保留其启动编排职责：

- 解析仓库外 Secret；
- 完成六项 AI 配置与工具链预检；
- 创建日志路由器；
- 启动、监控和停止后端/Vite；
- 执行 Provider 探针；
- 输出 AI 状态、日志目录和查看命令；
- `-FollowLogs` 时启动合并日志视图；
- `Ctrl+C` 时清理本次创建的全部进程。

启动器不直接承载日志分类、ZIP 实现和历史读取逻辑。

新增公开参数：

```powershell
-LogDirectory <path>  # 默认 <repository>/logs
-FollowLogs
```

### 4.2 `scripts/logging/LocalLogRouter.psm1`

作为日志深模块，负责：

- 异步读取两个子进程的 stdout/stderr；
- 识别级别与 `BACKEND / BROWSER / VITE / LAUNCHER` 来源；
- 单一写入四个活动文件；
- 有界队列、丢弃策略与路由器健康状态；
- 路径、URL、控制字符和凭据形态的二次防护；
- 大小分片、日期切换、staging、ZIP 验证和清理；
- 向启动器暴露稳定状态码，不暴露原始异常正文。

调用方只理解“启动路由、提交日志行、刷新、轮转、停止”，不依赖内部文件句柄与压缩实现。

### 4.3 `scripts/watch-local-logs.ps1`

只负责读取和展示：

- 默认回看末尾 100 行；
- 持续追踪四个活动文件；
- 按来源、级别过滤；
- 处理分片、替换、截断和跨日轮转；
- 读取指定日期 ZIP；
- 为终端增加颜色，不修改文件内容。

### 4.4 `scripts/archive-local-logs.ps1`

提供显式运维操作：

- 检查并补归档旧活动日志；
- 恢复 staging 或临时 ZIP；
- 执行保留策略；
- `-IncludeCurrentDay` 时生成调试快照；
- 不直接操作未验证的任意路径。

正常日归档仍由日志路由器完成，脚本用于开发间歇期补偿和人工排障。

## 5. 来源与级别路由

| 输入 | 条件 | 输出 |
| --- | --- | --- |
| Spring Boot | DEBUG/INFO/WARN，且非浏览器事件 | `backend-info.log` |
| Spring Boot | ERROR，且非浏览器事件 | `backend-error.log` |
| 后端诊断流 | `event.origin=browser` 且非 ERROR | `frontend-info.log` |
| 后端诊断流 | `event.origin=browser` 且 ERROR | `frontend-error.log` |
| Vite stdout | 普通启动、编译、HMR、WARN | `frontend-info.log` |
| Vite stderr | 明确 ERROR、构建失败、进程异常 | `frontend-error.log` |
| 启动器 | 安全状态与生命周期 | `backend-info.log`，来源 `LAUNCHER` |

WARN 进入 INFO 文件，ERROR 只进入 ERROR 文件，不重复。

文件使用 UTF-8 无 BOM，每行一条事件：

```text
2026-07-31T15:23:01.245+08:00 [BACKEND][INFO][SPRING] event.name=http.request.completed ...
2026-07-31T15:23:02.107+08:00 [FRONTEND][ERROR][BROWSER] event.name=frontend.runtime.failed ...
2026-07-31T15:23:05.301+08:00 [FRONTEND][INFO][VITE] hmr.update module=<repo>/frontend/src/...
```

## 6. 异步、背压与故障隔离

子进程输出通过异步回调进入有界队列，日志写入和压缩不能阻塞 Spring Boot/Vite。

队列压力策略：

1. 优先丢弃 DEBUG；
2. 再丢弃可重复 INFO；
3. WARN/ERROR 尽最大努力保留；
4. 丢弃数量通过限频安全 WARN 记录；
5. manifest 记录各级别丢弃计数。

日志路由失败不终止前后端。启动器保留可见安全状态：

```text
LOG_ROUTER_DEGRADED:WRITE_FAILED
LOG_ROUTER_DEGRADED:ARCHIVE_FAILED
LOG_ROUTER_DEGRADED:QUEUE_OVERFLOW
LOG_ROUTER_DEGRADED:DISK_LIMIT_REACHED
```

状态码不得附带绝对路径、异常原文或日志内容。

## 7. 浏览器与 Vite 日志

### 7.1 本地浏览器诊断

一键启动器只在本地后端子进程中内部设置：

```text
PORTFOLIO_FRONTEND_DIAGNOSTICS_ENABLED=true
```

该值不属于 Secret，不要求用户配置，不改变生产默认关闭策略。

新增安全 INFO 事件：

```text
frontend.application.started
frontend.content.load.completed
frontend.agent.request.completed
frontend.agent.request.cancelled
```

允许字段：

- `clientSessionId`、`clientRequestId`、服务端请求 ID、`turnId`；
- 耗时桶、HTTP 状态；
- `generationMode`、`degraded`、`guidanceStage`；
- 建议问题数量；
- 公开内容版本。

继续保留失败、慢请求、非法响应和运行时失败事件。普通点击、浏览、滚动、停留和用户输入不进入日志。

浏览器异常只允许 `error.kind`、稳定错误码和安全堆栈指纹，不上传 `Error.message` 或完整 stack。

### 7.2 Vite 输出防护

Vite 输出进入文件前：

- 移除 ANSI 颜色和终端控制字符；
- 仓库绝对路径替换为 `<repo>`；
- 用户目录替换为 `<home>`；
- URL 删除 query string 和 fragment；
- 单行最多 8 KB；
- 检测到 Authorization、Bearer、API Key、Token、Private Key 等模式时整行替换：

```text
VITE_OUTPUT_REDACTED reason=CREDENTIAL_PATTERN
```

无法安全解析的输出只记录稳定错误码。

### 7.3 后端二次防线

虽然后端已有类型化事件和隐私门禁，路由器仍执行：

- 控制字符清理；
- 单行长度上限；
- 路径归一化；
- 凭据形态拦截；
- 请求体、响应体和 Header 类字段拦截；
- 第三方异常只保留异常类型和安全应用栈位置。

## 8. 目录结构

```text
logs/
├── current/
│   ├── .active-date
│   ├── backend-info.log
│   ├── backend-error.log
│   ├── frontend-info.log
│   ├── frontend-error.log
│   └── 可能存在的 .1～.4 分片
├── staging/
│   └── <date>/
├── archive/
│   └── portfolio-agent-YYYY-MM-DD.zip
└── snapshots/
    └── portfolio-agent-YYYY-MM-DD-HHmmss.zip
```

`.gitignore` 增加：

```gitignore
/logs/
```

清理和归档代码在执行任何删除、移动前，必须解析并确认目标位于仓库 `logs` 目录内，拒绝目录穿越和重解析点逃逸。

## 9. 同日重启与大小分片

同一天内反复启动持续追加到相同活动文件。每次启动写入：

```text
event.name=local.session.started
local.session.id=<random UUID>
```

每个活动文件最大 20 MB，最多五个分片：

```text
backend-info.log
backend-info.1.log
backend-info.2.log
backend-info.3.log
backend-info.4.log
```

达到上限后采用环形保留，删除最旧分片，保留最近约 100 MB。四类活动日志理论单日最大约 400 MB。

一旦发生覆盖，日归档 manifest 必须声明：

```json
{
  "truncated": true,
  "discardedSegmentCount": 1
}
```

## 10. 每日 ZIP 与崩溃恢复

### 10.1 跨零点

日志路由器在本地时区日期变化时：

1. 暂停从队列取新日志；
2. 刷新并关闭旧文件句柄；
3. 将旧文件原子移动到 `staging/<date>/`；
4. 立即创建新日期活动文件并恢复消费；
5. 后台生成临时 ZIP；
6. 验证 ZIP 可读取、条目和哈希正确；
7. 临时 ZIP 原子改名为正式日归档；
8. 正式 ZIP 成功后删除 staging。

压缩不占用日志消费的日期切换临界区。

### 10.2 启动补归档

每次启动检查：

- `.active-date` 是否早于今天；
- staging 是否存在未完成归档；
- 是否存在临时 ZIP；
- 正式 ZIP 与 staging 是否重复。

恢复规则：

- 旧活动日志进入 staging 并归档；
- 有 staging、无正式 ZIP时重新压缩；
- 有完整正式 ZIP、仍有 staging 时验证后删除 staging；
- 临时 ZIP 损坏时从 staging 重建；
- 同日期正式 ZIP 已存在但与 staging 哈希不一致时，禁止覆盖正式 ZIP，保留 staging 并报告稳定恢复错误码；
- 未运行的日期不生成空 ZIP。

### 10.3 ZIP 内容

```text
portfolio-agent-2026-07-30.zip
├── backend-info-2026-07-30.log
├── backend-info-2026-07-30.1.log
├── backend-error-2026-07-30.log
├── frontend-info-2026-07-30.log
├── frontend-error-2026-07-30.log
└── manifest.json
```

`manifest.json` 只包含：

- 日期与时区；
- 文件名、字节数、SHA-256；
- 各来源事件数量；
- 分片覆盖与队列丢弃计数；
- 归档创建时间；
- 日志契约版本。

ZIP 成功前不得删除原始日志。

## 11. 保留和空间上限

每次成功归档及每次启动后：

1. 以本地当天为基准，删除归档日期早于最近七个完整自然日窗口的正式 ZIP；
2. 计算 `archive` 与 `snapshots` 总大小；
3. 若超过 2 GB，从最旧正式日归档开始删除；
4. 需要继续释放时再删除最旧调试快照；
5. 不删除当天活动文件；
6. 不删除未成功归档的 staging；
7. 清理失败只发布安全警告。

正式 ZIP 只能按严格文件名与日期契约识别。未知文件不自动删除。

## 12. 手动快照

```powershell
scripts/archive-local-logs.ps1 -IncludeCurrentDay
```

生成：

```text
logs/snapshots/portfolio-agent-2026-07-31-152301.zip
```

路由器先刷新活动文件，再在短暂一致性边界内复制，随后后台压缩。快照不占用七个日归档名额，但计入 2 GB 总量上限。

## 13. 实时查看

### 13.1 默认启动

```powershell
scripts/start-local.ps1 -SecretsFile C:\secrets\portfolio-agent-model.env
```

启动器输出：

```text
AI_CONNECTED provider=...
LOG_DIRECTORY <repository>\logs
LOG_WATCH_COMMAND scripts/watch-local-logs.ps1
```

### 13.2 同窗口查看

```powershell
scripts/start-local.ps1 `
  -SecretsFile C:\secrets\portfolio-agent-model.env `
  -FollowLogs
```

默认回看最近 100 行并持续追踪。此模式下 `Ctrl+C` 同时停止查看、前端、后端和日志路由器。

### 13.3 独立查看

```powershell
scripts/watch-local-logs.ps1
scripts/watch-local-logs.ps1 -Level ERROR
scripts/watch-local-logs.ps1 -Source BACKEND
scripts/watch-local-logs.ps1 -Source BROWSER
scripts/watch-local-logs.ps1 -Source VITE
scripts/watch-local-logs.ps1 -Tail 300
scripts/watch-local-logs.ps1 -ArchiveDate 2026-07-30
```

独立查看器的 `Ctrl+C` 不影响项目。

查看器维护每个文件的身份、读取偏移和最后事件时间；检测文件缩小、替换、分片或日期轮转后自动恢复。历史 ZIP 从压缩流读取，不解压到仓库。

终端可以使用颜色，但文件中不得包含 ANSI 颜色字符。

## 14. 测试策略

### 14.1 路由

- 四类来源和级别准确映射；
- ERROR 不进入 INFO；
- UTF-8 中文正确；
- 控制字符、路径、URL 参数、凭据和超长行被处理；
- 测试只使用临时目录。

### 14.2 异步与进程

- 后端/Vite 大量同时输出不互相阻塞；
- 队列满时按级别丢弃；
- 日志失败不终止业务；
- 子进程退出可检测；
- 两种 `Ctrl+C` 语义正确；
- 路由器状态码稳定且无敏感信息。

### 14.3 时间、分片和归档

- 使用可替换时钟模拟跨日；
- 同日重启追加；
- 多日停机后补归档；
- 20 MB/五分片策略使用小测试阈值验证；
- ZIP 条目、大小和 SHA-256 正确；
- 每个归档临界点中断后均可恢复。

### 14.4 保留

- 按归档日期计算的七个完整自然日保留；
- 2 GB 总量上限；
- current/staging 不被误删；
- 非法路径、目录穿越和重解析点被拒绝；
- 未知文件不自动删除。

### 14.5 查看器

- 默认 Tail 100；
- 多文件按时间合并；
- Level/Source 组合过滤；
- 分片、截断、替换和跨日后继续追踪；
- ZIP 流式读取；
- `NoColor` 和文件无 ANSI。

### 14.6 隐私门禁

在后端、浏览器和 Vite 输出中植入问题、回答、API Key、Authorization、本地路径、URL 参数、Provider 载荷和异常原文哨兵。

检查范围：

- 四个活动日志；
- 日归档 ZIP；
- 手动快照 ZIP；
- 查看器输出；
- 前端 dist 与最终 JAR。

任何哨兵泄漏必须阻止发布。

## 15. 验收命令

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/start-local.test.ps1

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/local-log-router.test.ps1

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/watch-local-logs.test.ps1

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/archive-local-logs.test.ps1

mvn.cmd -f backend/pom.xml -DskipFrontend=true test

npm.cmd --prefix frontend run test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run build

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/privacy-check.ps1 `
  -Path .
```

## 16. 完成标准

- 一键启动后四个日志文件持续产生正确内容；
- 后台启动不影响实时排障；
- INFO/ERROR 严格不重复；
- BROWSER/VITE 来源可区分；
- 跨日、同日重启和异常中断后归档正确；
- 最近七日、单文件分片和 2 GB 上限生效；
- 日志故障不影响前后端；
- 日志、ZIP、快照和查看器不泄漏敏感内容；
- README 与排障手册说明启动、查看、归档、快照和恢复。

## 17. 实施边界

- 不修改生产“标准输出优先”的部署策略；
- 不引入外部日志服务；
- 不提供浏览器日志查询或下载接口；
- 不记录普通用户行为；
- 不记录问题、回答和原始异常正文；
- 不依赖项目持续运行才能完成归档；
- 不把日志路由器扩张成通用日志框架。
