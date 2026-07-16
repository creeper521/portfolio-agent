const SKILL_GROUPS = [
  {
    category: '后端 & 框架',
    skills: [
      { name: 'Java', level: '独立完成相关功能' },
      { name: 'Spring Boot', level: '独立完成相关功能' },
      { name: 'Spring AI', level: '完成实验' },
      { name: 'MyBatis', level: '用于实际任务' },
    ],
  },
  {
    category: '网络 & 异步',
    skills: [
      { name: 'WebSocket', level: '独立完成相关功能' },
      { name: 'SSE', level: '完成实验' },
      { name: 'Netty', level: '已了解' },
      { name: 'KCP', level: '已了解' },
    ],
  },
  {
    category: '数据 & 缓存',
    skills: [
      { name: 'MySQL', level: '用于实际任务' },
      { name: 'Redis', level: '用于实际任务' },
      { name: 'Excel 导出', level: '独立完成相关功能' },
    ],
  },
  {
    category: '基础设施',
    skills: [
      { name: 'Docker', level: '完成实验' },
      { name: 'Docker Compose', level: '完成实验' },
      { name: 'Linux', level: '用于实际任务' },
      { name: 'Jenkins', level: '已了解' },
    ],
  },
  {
    category: 'AI 工程',
    skills: [
      { name: 'Tool Calling', level: '完成实验' },
      { name: 'MCP stdio/SSE', level: '完成实验' },
      { name: 'Prompt Engineering', level: '用于实际任务' },
      { name: 'DeepSeek', level: '用于实际任务' },
    ],
  },
]

const LEVEL_CONFIG: Record<
  string,
  { width: string; color: string; barColor: string }
> = {
  已了解: { width: 'w-1/5', color: 'text-slate-500', barColor: 'bg-slate-200' },
  完成实验: { width: 'w-2/5', color: 'text-blue-500', barColor: 'bg-blue-200' },
  用于实际任务: { width: 'w-3/5', color: 'text-primary', barColor: 'bg-primary/30' },
  独立完成相关功能: {
    width: 'w-4/5',
    color: 'text-primary',
    barColor: 'bg-primary/60',
  },
  能进行方案设计与取舍: {
    width: 'w-full',
    color: 'text-primary',
    barColor: 'bg-primary',
  },
}

export default function SkillsSection() {
  return (
    <section id="skills" className="py-16 px-6 bg-muted/30">
      <div className="max-w-6xl mx-auto">
        {/* 标题 */}
        <div className="mb-8">
          <p className="font-mono text-xs text-muted-foreground mb-1 uppercase tracking-widest">
            Tech Stack
          </p>
          <h2 className="text-2xl font-bold text-foreground">技术能力摘要</h2>
          <p className="text-sm text-muted-foreground mt-1.5">
            能力等级来自实际工程产出，不来自自我评估。
          </p>
        </div>

        {/* 图例 */}
        <div className="flex flex-wrap gap-4 mb-8 p-4 bg-card border border-border rounded-lg">
          {Object.entries(LEVEL_CONFIG).map(([level, cfg]) => (
            <div key={level} className="flex items-center gap-2">
              <div className={`h-1.5 w-12 rounded-full ${cfg.barColor}`} />
              <span className="text-xs text-muted-foreground">{level}</span>
            </div>
          ))}
        </div>

        {/* 技能分组网格 */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {SKILL_GROUPS.map((group) => (
            <div
              key={group.category}
              className="bg-card border border-border rounded-xl p-5"
            >
              <h3 className="font-mono text-xs font-semibold text-muted-foreground uppercase tracking-widest mb-4">
                {group.category}
              </h3>
              <div className="space-y-3">
                {group.skills.map((skill) => {
                  const levelCfg = LEVEL_CONFIG[skill.level] ?? LEVEL_CONFIG['已了解']
                  return (
                    <div key={skill.name}>
                      <div className="flex items-center justify-between mb-1">
                        <span className="font-mono text-sm text-foreground">
                          {skill.name}
                        </span>
                        <span className={`text-xs ${levelCfg.color}`}>
                          {skill.level}
                        </span>
                      </div>
                      <div className="h-1 w-full bg-muted rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full ${levelCfg.barColor} ${levelCfg.width}`}
                        />
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
