'use client'

import { useState, useEffect } from 'react'
import Link from 'next/link'

export default function Navbar() {
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 20)
    window.addEventListener('scroll', handleScroll)
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  return (
    <header
      className={`fixed top-0 left-0 right-0 z-50 transition-all duration-200 ${
        scrolled
          ? 'bg-background/80 backdrop-blur-md border-b border-border'
          : 'bg-background/40 backdrop-blur-sm border-b border-transparent'
      }`}
    >
      <div className="max-w-6xl mx-auto px-6 h-14 flex items-center justify-between">
        {/* 左侧 logo */}
        <div className="flex items-center gap-3">
          <span className="font-mono text-xs text-muted-foreground border border-border rounded px-2 py-0.5 bg-muted">
            v1.0
          </span>
          <span className="text-sm font-semibold text-foreground tracking-tight">
            Portfolio Agent
          </span>
        </div>

        {/* 中间导航 */}
        <nav className="hidden md:flex items-center gap-6">
          {[
            { label: '项目', href: '#projects' },
            { label: '时间线', href: '#timeline' },
            { label: '技术栈', href: '#skills' },
          ].map((item) => (
            <a
              key={item.href}
              href={item.href}
              className="text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              {item.label}
            </a>
          ))}
        </nav>

        {/* 右侧 CTA */}
        <a
          href="#chat"
          className="flex items-center gap-2 text-sm font-medium text-primary-foreground bg-primary hover:opacity-90 transition-opacity px-4 py-1.5 rounded-md"
        >
          <svg
            className="size-3.5"
            viewBox="0 0 16 16"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              d="M14 2H2C1.45 2 1 2.45 1 3V11C1 11.55 1.45 12 2 12H5V15L8.5 12H14C14.55 12 15 11.55 15 11V3C15 2.45 14.55 2 14 2Z"
              fill="currentColor"
            />
          </svg>
          向 Agent 提问
        </a>
      </div>
    </header>
  )
}
