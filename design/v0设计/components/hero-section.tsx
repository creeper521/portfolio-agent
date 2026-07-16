'use client'

import { useState } from 'react'

const SUGGESTED_QUESTIONS = [
  'SQL 审计工具的技术方案是什么？',
  '实习期间最复杂的排查案例？',
  '哪些项目已经正式完成？',
  '你在这次实习中学到了什么？',
]

const STATS = [
  { value: '4', unit: '个月', label: '实习时长' },
  { value: '4', unit: '个', label: '核心工程项目' },
  { value: '105', unit: '篇', label: 'Markdown 记录' },
  { value: '73', unit: '篇', label: '任务产出日报' },
]

export default function HeroSection() {
  const [inputValue, setInputValue] = useState('')

  const handleSuggest = (q: string) => {
    setInputValue(q)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!inputValue.trim()) return
    window.location.href = `/chat?q=${encodeURIComponent(inputValue.trim())}`
  }

  return (
    <section className="relative pt-32 pb-20 px-6 overflow-hidden">
      {/* 点阵背景 */}
      <div
        className="pointer-events-none absolute inset-0 bg-dot-grid mask-fade-b"
        aria-hidden="true"
      />

      <div className="relative max-w-3xl mx-auto flex flex-col items-center text-center">
        {/* 身份标识行 */}
        <div className="flex flex-wrap items-center justify-center gap-2 mb-7">
          <span className="inline-flex items-center gap-1.5 font-mono text-xs bg-accent text-accent-foreground border border-border/60 rounded-full px-3 py-1">
            <span className="size-1.5 rounded-full bg-green-500 inline-block" />
            Java 游戏服务端 · 实习生
          </span>
          <span className="font-mono text-xs text-muted-foreground border border-border rounded-full px-3 py-1">
            2026.04 — 2026.07
          </span>
        </div>

        {/* 标题 */}
        <h1 className="text-4xl md:text-5xl font-bold text-foreground leading-[1.15] tracking-tight text-balance mb-5">
          不用翻简历，
          <br />
          <span className="text-primary">直接问我的 AI 分身</span>
        </h1>
        <p className="text-base md:text-lg text-muted-foreground leading-relaxed max-w-xl mb-10 text-pretty">
          四个月 Java 游戏后端实习的可交互作品集。所有回答基于人工审核的脱敏事实，
          由 AI Agent 实时查询并表达——想了解什么，直接开口。
        </p>

        {/* AI 对话入口 —— 视觉焦点 */}
        <div
          id="chat"
          className="w-full max-w-2xl bg-card border border-border rounded-2xl shadow-xl shadow-primary/5 p-5 md:p-6 text-left"
        >
          {/* 头部 */}
          <div className="flex items-center gap-2.5 pb-4 border-b border-border">
            <div className="size-8 rounded-lg bg-primary flex items-center justify-center shrink-0">
              <svg
                className="size-4 text-primary-foreground"
                viewBox="0 0 20 20"
                fill="currentColor"
              >
                <path
                  fillRule="evenodd"
                  d="M18 10c0 3.866-3.582 7-8 7a8.841 8.841 0 01-4.083-.98L2 17l1.338-3.123C2.493 12.767 2 11.434 2 10c0-3.866 3.582-7 8-7s8 3.134 8 7zM7 9H5v2h2V9zm8 0h-2v2h2V9zM9 9h2v2H9V9z"
                  clipRule="evenodd"
                />
              </svg>
            </div>
            <div className="min-w-0">
              <p className="text-sm font-semibold text-foreground">
                Portfolio Agent
              </p>
              <p className="text-xs text-muted-foreground truncate">
                关于项目 · 技术方案 · 排查经验，随便问
              </p>
            </div>
            <span className="ml-auto inline-flex items-center gap-1.5 font-mono text-xs bg-muted text-muted-foreground px-2 py-1 rounded-md shrink-0">
              <span className="size-1.5 rounded-full bg-green-500 inline-block" />
              在线
            </span>
          </div>

          {/* 模拟对话气泡 */}
          <div className="py-4">
            <div className="bg-muted rounded-xl rounded-tl-sm px-4 py-3 text-sm text-foreground leading-relaxed">
              你好！我是基于实习记录训练的 Portfolio Agent。你可以直接问我项目细节、技术决策或实习经历，我会根据审核后的事实作答。
            </div>
          </div>

          {/* 输入框 */}
          <form onSubmit={handleSubmit} className="flex gap-2">
            <input
              type="text"
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              placeholder="输入你的问题，或点选下方推荐…"
              className="flex-1 text-sm bg-background border border-input rounded-xl px-4 py-3 text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring/40 focus:border-primary transition-colors"
            />
            <button
              type="submit"
              className="inline-flex items-center gap-1.5 px-5 py-3 bg-primary text-primary-foreground text-sm font-medium rounded-xl hover:opacity-90 transition-opacity whitespace-nowrap"
            >
              发送
              <svg className="size-3.5" viewBox="0 0 16 16" fill="none">
                <path
                  d="M2 8h11M9 4l4 4-4 4"
                  stroke="currentColor"
                  strokeWidth="1.6"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </button>
          </form>

          {/* 推荐问题 */}
          <div className="mt-3 flex flex-wrap gap-2">
            {SUGGESTED_QUESTIONS.map((q) => (
              <button
                key={q}
                onClick={() => handleSuggest(q)}
                className="text-xs text-muted-foreground border border-border/70 rounded-full px-3 py-1.5 hover:border-primary hover:text-primary hover:bg-accent/40 transition-colors bg-background"
              >
                {q}
              </button>
            ))}
          </div>
        </div>

        {/* 声明 */}
        <p className="mt-5 text-xs text-muted-foreground max-w-lg leading-relaxed">
          内容经人工审核脱敏，AI 负责查询与表达，不代表前雇主立场。公司内部信息与原始日志不在公开范围内。
        </p>

        {/* 统计指标条带 */}
        <div className="mt-12 w-full max-w-2xl grid grid-cols-2 md:grid-cols-4 divide-x divide-border border border-border rounded-xl bg-card overflow-hidden">
          {STATS.map((s, i) => (
            <div
              key={s.label}
              className={`px-4 py-4 ${i >= 2 ? 'border-t md:border-t-0 border-border' : ''}`}
            >
              <div className="flex items-baseline justify-center gap-1">
                <span className="text-2xl font-bold text-foreground font-mono">
                  {s.value}
                </span>
                <span className="text-xs text-muted-foreground">{s.unit}</span>
              </div>
              <p className="text-xs text-muted-foreground mt-1">{s.label}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
