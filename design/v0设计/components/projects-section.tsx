const STATUS_CONFIG: Record<
  string,
  { label: string; color: string; dot: string }
> = {
  COMPLETED: {
    label: '已完成',
    color: 'text-green-700 bg-green-50 border-green-200',
    dot: 'bg-green-500',
  },
  IMPLEMENTED_TESTED: {
    label: '实现并测试',
    color: 'text-blue-700 bg-blue-50 border-blue-200',
    dot: 'bg-blue-500',
  },
  PROTOTYPE: {
    label: '原型',
    color: 'text-amber-700 bg-amber-50 border-amber-200',
    dot: 'bg-amber-500',
  },
  DESIGNED: {
    label: '完成设计',
    color: 'text-purple-700 bg-purple-50 border-purple-200',
    dot: 'bg-purple-500',
  },
}

const CONTRIBUTION_CONFIG: Record<string, string> = {
  INDEPENDENT: '独立完成',
  PRIMARY: '主要负责',
  COLLABORATIVE: '协作参与',
}

const PROJECTS = [
  {
    id: 'sql-audit',
    name: 'SQL 审计 / 排查工具',
    summary:
      '支持远程 SQL 日志查询、异步任务、多配置路由和 Excel 导出的内部排查工具，WebSocket 实时推送并配有轮询兜底。',
    period: '2026.05 — 2026.06',
    status: 'COMPLETED',
    contribution: 'PRIMARY',
    tags: ['Java', 'Spring Boot', 'WebSocket', 'SSE', 'Async', 'Excel'],
    evidenceCount: 8,
    highlights: ['WebSocket + 轮询双通道', '多目标多来源路由', '查询结果归档复用'],
  },
  {
    id: 'i18n-image-fix',
    name: '运营后台多语言传图修复',
    summary:
      '定位历史语言被当次上传覆盖的根因，采用"历史语言 ∪ 本次上传语言"的最小改动方案，不修改前端接口和数据库结构。',
    period: '2026.05',
    status: 'COMPLETED',
    contribution: 'PRIMARY',
    tags: ['Java', 'Spring Boot', 'Bug Fix', 'i18n', 'MySQL'],
    evidenceCount: 4,
    highlights: ['最小改动原则', '连续上传场景自测', '零前端接口变更'],
  },
  {
    id: 'role-cleanup',
    name: '内部测试工具角色信息清理',
    summary:
      '按环境查询角色、二次确认后清除记录，使账号重新进入创角流程，完成实际账号验证和使用说明文档。',
    period: '2026.06',
    status: 'COMPLETED',
    contribution: 'PRIMARY',
    tags: ['Java', 'Spring Boot', 'Internal Tool', 'MySQL'],
    evidenceCount: 3,
    highlights: ['按环境路由查询', '二次确认安全防护', '使用说明文档'],
  },
  {
    id: 'operation-log',
    name: '运营后台操作日志原型',
    summary:
      '完成数据模型、代码范围、异常隔离和验收标准设计，完成实现和自测。原始记录明确说明暂未正式提交。',
    period: '2026.06 — 2026.07',
    status: 'IMPLEMENTED_TESTED',
    contribution: 'PRIMARY',
    tags: ['Java', 'Spring Boot', 'AOP', 'MySQL', 'Audit Log'],
    evidenceCount: 5,
    highlights: ['AOP 切面拦截', '异常隔离设计', '完整数据模型'],
  },
]

export default function ProjectsSection() {
  return (
    <section id="projects" className="py-16 px-6 bg-muted/30">
      <div className="max-w-6xl mx-auto">
        {/* 标题 */}
        <div className="flex items-baseline justify-between mb-8">
          <div>
            <p className="font-mono text-xs text-muted-foreground mb-1 uppercase tracking-widest">
              Core Projects
            </p>
            <h2 className="text-2xl font-bold text-foreground">
              核心工程项目
            </h2>
          </div>
          <span className="font-mono text-xs text-muted-foreground">
            {PROJECTS.length} 个项目
          </span>
        </div>

        {/* 项目卡片网格 */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          {PROJECTS.map((project) => {
            const statusCfg = STATUS_CONFIG[project.status]
            return (
              <article
                key={project.id}
                className="bg-card border border-border rounded-xl p-6 flex flex-col gap-4 hover:border-primary/40 hover:shadow-sm transition-all duration-200 group"
              >
                {/* 卡片头 */}
                <div className="flex items-start justify-between gap-3">
                  <div className="flex-1 min-w-0">
                    <h3 className="text-base font-semibold text-foreground leading-snug group-hover:text-primary transition-colors">
                      {project.name}
                    </h3>
                    <p className="font-mono text-xs text-muted-foreground mt-1">
                      {project.period}
                    </p>
                  </div>
                  <span
                    className={`inline-flex items-center gap-1.5 text-xs font-medium border rounded-full px-2.5 py-1 whitespace-nowrap flex-shrink-0 ${statusCfg.color}`}
                  >
                    <span className={`size-1.5 rounded-full ${statusCfg.dot}`} />
                    {statusCfg.label}
                  </span>
                </div>

                {/* 摘要 */}
                <p className="text-sm text-muted-foreground leading-relaxed">
                  {project.summary}
                </p>

                {/* 亮点列表 */}
                <ul className="space-y-1.5">
                  {project.highlights.map((h) => (
                    <li key={h} className="flex items-center gap-2 text-xs text-foreground">
                      <span className="size-1 rounded-full bg-primary flex-shrink-0" />
                      {h}
                    </li>
                  ))}
                </ul>

                {/* 卡片底部 */}
                <div className="flex items-center justify-between pt-2 border-t border-border/50">
                  {/* 技术标签 */}
                  <div className="flex flex-wrap gap-1.5">
                    {project.tags.slice(0, 4).map((tag) => (
                      <span
                        key={tag}
                        className="font-mono text-xs text-muted-foreground bg-muted border border-border/60 rounded px-2 py-0.5"
                      >
                        {tag}
                      </span>
                    ))}
                    {project.tags.length > 4 && (
                      <span className="font-mono text-xs text-muted-foreground">
                        +{project.tags.length - 4}
                      </span>
                    )}
                  </div>

                  {/* 元信息 */}
                  <div className="flex items-center gap-3 text-xs text-muted-foreground flex-shrink-0">
                    <span>
                      {CONTRIBUTION_CONFIG[project.contribution]}
                    </span>
                    <span className="flex items-center gap-1">
                      <svg className="size-3" viewBox="0 0 16 16" fill="currentColor">
                        <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
                      </svg>
                      {project.evidenceCount} 条证据
                    </span>
                  </div>
                </div>
              </article>
            )
          })}
        </div>
      </div>
    </section>
  )
}
