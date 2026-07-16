export default function Footer() {
  return (
    <footer className="border-t border-border py-10 px-6">
      <div className="max-w-6xl mx-auto">
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
          {/* 联系方式 */}
          <div className="flex flex-col gap-3">
            <p className="font-mono text-xs text-muted-foreground uppercase tracking-widest">
              联系方式
            </p>
            <div className="flex flex-wrap items-center gap-4">
              <a
                href="mailto:your@email.com"
                className="flex items-center gap-2 text-sm text-foreground hover:text-primary transition-colors"
              >
                <svg
                  className="size-4 text-muted-foreground"
                  viewBox="0 0 20 20"
                  fill="currentColor"
                >
                  <path d="M2.003 5.884L10 9.882l7.997-3.998A2 2 0 0016 4H4a2 2 0 00-1.997 1.884z" />
                  <path d="M18 8.118l-8 4-8-4V14a2 2 0 002 2h12a2 2 0 002-2V8.118z" />
                </svg>
                求职邮箱（待填写）
              </a>
              <a
                href="https://github.com"
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-2 text-sm text-foreground hover:text-primary transition-colors"
              >
                <svg
                  className="size-4 text-muted-foreground"
                  viewBox="0 0 16 16"
                  fill="currentColor"
                >
                  <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
                </svg>
                GitHub（待填写）
              </a>
              <a
                href="/resume.pdf"
                className="flex items-center gap-2 text-sm text-foreground hover:text-primary transition-colors"
              >
                <svg
                  className="size-4 text-muted-foreground"
                  viewBox="0 0 20 20"
                  fill="currentColor"
                >
                  <path
                    fillRule="evenodd"
                    d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z"
                    clipRule="evenodd"
                  />
                </svg>
                下载简历
              </a>
            </div>
          </div>

          {/* 快速提问 */}
          <a
            href="#chat"
            className="flex items-center gap-2 text-sm font-medium text-primary hover:opacity-80 transition-opacity border border-primary/30 rounded-lg px-4 py-2"
          >
            <svg
              className="size-4"
              viewBox="0 0 20 20"
              fill="currentColor"
            >
              <path
                fillRule="evenodd"
                d="M18 10c0 3.866-3.582 7-8 7a8.841 8.841 0 01-4.083-.98L2 17l1.338-3.123C2.493 12.767 2 11.434 2 10c0-3.866 3.582-7 8-7s8 3.134 8 7z"
                clipRule="evenodd"
              />
            </svg>
            向 Agent 提问
          </a>
        </div>

        {/* 声明 */}
        <div className="mt-8 pt-6 border-t border-border">
          <p className="text-xs text-muted-foreground leading-relaxed max-w-3xl">
            本页面内容来自个人实习记录，已经人工审核脱敏。所有回答由 AI Agent 根据公开快照查询并生成，
            不代表任何公司立场。内部系统、表结构、服务器信息及敏感业务数据均不在公开范围内。
            公司实名按保密要求处理，项目与系统名称使用公开化名。
          </p>
          <p className="mt-3 font-mono text-xs text-muted-foreground">
            Portfolio Agent v1.0 · 快照版本：2026-07-14 · 基于 DeepSeek V4 Pro
          </p>
        </div>
      </div>
    </footer>
  )
}
