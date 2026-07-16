import { Analytics } from '@vercel/analytics/next'
import type { Metadata, Viewport } from 'next'
import { Inter } from 'next/font/google'
import './globals.css'

const inter = Inter({ subsets: ['latin'], variable: '--font-sans' })

export const metadata: Metadata = {
  title: '实习作品集 · Portfolio Agent',
  description:
    '2026年 Java 游戏服务端实习生作品集，包含 SQL 审计工具、运营后台修复、测试工具等工程实践，可通过 AI Agent 深入了解项目细节。',
  generator: 'v0.app',
}

export const viewport: Viewport = {
  colorScheme: 'light',
  themeColor: '#ffffff',
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="zh-CN" className={`${inter.variable} bg-background`}>
      <body className="antialiased font-sans">{children}</body>
      {process.env.NODE_ENV === 'production' && <Analytics />}
    </html>
  )
}
