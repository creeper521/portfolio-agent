const EVENT_TYPE_CONFIG: Record<
  string,
  { label: string; color: string }
> = {
  LEARNING: { label: '学习', color: 'bg-slate-100 text-slate-600 border-slate-200' },
  IMPLEMENTATION: { label: '开发', color: 'bg-blue-50 text-blue-600 border-blue-200' },
  DEBUG: { label: '排查', color: 'bg-orange-50 text-orange-600 border-orange-200' },
  TEST: { label: '测试', color: 'bg-purple-50 text-purple-600 border-purple-200' },
  SUBMIT: { label: '提交', color: 'bg-green-50 text-green-600 border-green-200' },
  DOCUMENTATION: { label: '文档', color: 'bg-cyan-50 text-cyan-600 border-cyan-200' },
  DESIGN: { label: '设计', color: 'bg-pink-50 text-pink-600 border-pink-200' },
}

const TIMELINE_DATA = [
  {
    month: '2026年4月',
    shortMonth: 'APR',
    events: [
      {
        date: '04月',
        title: '入职与业务熟悉',
        description: '熟悉 Java 游戏服务端架构、活动框架、KCP/Netty 网络层和业务排查流程。',
        type: 'LEARNING',
        project: null,
      },
      {
        date: '04月',
        title: '活动配置与排查实践',
        description: '参与多个活动配置异常排查，逐步形成从客户端包 → 规则表 → Redis → 服务端的完整排查链路。',
        type: 'DEBUG',
        project: null,
      },
    ],
  },
  {
    month: '2026年5月',
    shortMonth: 'MAY',
    events: [
      {
        date: '05月',
        title: '运营后台多语言传图修复',
        description: '定位历史语言被覆盖的根因，采用最小改动方案完成修复并自测，不影响前端接口。',
        type: 'SUBMIT',
        project: 'i18n-image-fix',
      },
      {
        date: '05月底',
        title: 'SQL 审计工具需求启动',
        description: '收集需求，设计异步任务架构、多来源配置路由和 WebSocket 实时推送方案。',
        type: 'DESIGN',
        project: 'sql-audit',
      },
    ],
  },
  {
    month: '2026年6月',
    shortMonth: 'JUN',
    events: [
      {
        date: '06月初',
        title: 'SQL 审计工具核心功能开发',
        description: '实现远程日志查询、异步任务、时间倒序、Excel 导出和查询结果归档。',
        type: 'IMPLEMENTATION',
        project: 'sql-audit',
      },
      {
        date: '06月中',
        title: 'WebSocket + 轮询兜底完成',
        description: 'WebSocket 实时推送上线，增加轮询备用通道，完成使用说明与验收文档。',
        type: 'SUBMIT',
        project: 'sql-audit',
      },
      {
        date: '06月',
        title: '测试工具角色信息清理功能',
        description: '按环境查询角色、二次确认清除，完成实际账号验证和测试人员使用说明。',
        type: 'SUBMIT',
        project: 'role-cleanup',
      },
      {
        date: '06月底',
        title: '操作日志原型设计',
        description: '完成数据模型、代码范围、AOP 切面方案和异常隔离设计，开始实现。',
        type: 'DESIGN',
        project: 'operation-log',
      },
    ],
  },
  {
    month: '2026年7月',
    shortMonth: 'JUL',
    events: [
      {
        date: '07月初',
        title: '操作日志原型实现完成',
        description: '完成实现和自测，保留为原型状态，原始记录明确说明暂未正式提交。',
        type: 'TEST',
        project: 'operation-log',
      },
      {
        date: '07月',
        title: 'Spring AI 技术研究',
        description: 'ChatModel、ChatClient、SSE 流式输出、Tool Calling 和 MCP stdio/SSE 的系统研究。',
        type: 'LEARNING',
        project: null,
      },
      {
        date: '07月',
        title: '公开作品集 Agent 启动',
        description: '设计公开 Portfolio Agent 数据模型、工具链与私有内容发布流程。',
        type: 'DESIGN',
        project: null,
      },
    ],
  },
]

export default function TimelineSection() {
  return (
    <section id="timeline" className="py-16 px-6">
      <div className="max-w-6xl mx-auto">
        {/* 标题 */}
        <div className="flex items-baseline justify-between mb-10">
          <div>
            <p className="font-mono text-xs text-muted-foreground mb-1 uppercase tracking-widest">
              Timeline
            </p>
            <h2 className="text-2xl font-bold text-foreground">成长时间线</h2>
          </div>
          <div className="hidden md:flex items-center gap-3 flex-wrap justify-end">
            {(['LEARNING', 'IMPLEMENTATION', 'DEBUG', 'SUBMIT', 'DESIGN'] as const).map(
              (type) => (
                <span
                  key={type}
                  className={`text-xs border rounded-full px-2.5 py-0.5 ${EVENT_TYPE_CONFIG[type].color}`}
                >
                  {EVENT_TYPE_CONFIG[type].label}
                </span>
              )
            )}
          </div>
        </div>

        {/* 时间线主体 */}
        <div className="space-y-10">
          {TIMELINE_DATA.map((monthGroup) => (
            <div key={monthGroup.month} className="flex gap-6">
              {/* 月份标签 */}
              <div className="flex-shrink-0 w-16 pt-0.5">
                <div className="font-mono text-xs font-bold text-primary">
                  {monthGroup.shortMonth}
                </div>
                <div className="font-mono text-xs text-muted-foreground leading-tight">
                  {monthGroup.month.replace('2026年', '')}
                </div>
              </div>

              {/* 竖线 + 事件列表 */}
              <div className="flex-1 relative">
                {/* 竖线 */}
                <div className="absolute left-0 top-2 bottom-2 w-px bg-border" />

                <div className="space-y-4 pl-6">
                  {monthGroup.events.map((event, idx) => {
                    const typeCfg = EVENT_TYPE_CONFIG[event.type]
                    return (
                      <div key={idx} className="relative">
                        {/* 圆点 */}
                        <span className="absolute -left-6 top-1.5 size-2 rounded-full bg-border border-2 border-background ring-1 ring-border" />

                        <div className="bg-card border border-border rounded-lg px-4 py-3 hover:border-primary/30 transition-colors">
                          <div className="flex items-start justify-between gap-2 mb-1.5">
                            <h4 className="text-sm font-semibold text-foreground leading-snug">
                              {event.title}
                            </h4>
                            <span
                              className={`text-xs border rounded-full px-2 py-0.5 whitespace-nowrap flex-shrink-0 ${typeCfg.color}`}
                            >
                              {typeCfg.label}
                            </span>
                          </div>
                          <p className="text-xs text-muted-foreground leading-relaxed">
                            {event.description}
                          </p>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
