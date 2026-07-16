// 视觉审查脚本：多视口 × 多路由截图，检测横向溢出与控制台错误。
import { chromium } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const BASE = 'http://127.0.0.1:4173'
const OUT = new URL('../.visual-audit/', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')

const viewports = [
  { name: '2048x1080', width: 2048, height: 1080 },
  { name: '1440x900', width: 1440, height: 900 },
  { name: '1219x900', width: 1219, height: 900 },
  { name: '980x800', width: 980, height: 800 },
  { name: '390x844', width: 390, height: 844 },
]

const routes = [
  { name: 'home', path: '/' },
  { name: 'projects', path: '/projects' },
  { name: 'project-detail', path: '/projects/sql-audit' },
  { name: 'timeline', path: '/timeline' },
  { name: 'evidence', path: '/evidence' },
  { name: 'agent', path: '/agent' },
  { name: 'not-found', path: '/nope' },
]

mkdirSync(OUT, { recursive: true })

const browser = await chromium.launch()
const issues = []

for (const vp of viewports) {
  const context = await browser.newContext({ viewport: { width: vp.width, height: vp.height } })
  const page = await context.newPage()
  page.on('console', (msg) => {
    if (msg.type() === 'error') issues.push(`[console] ${vp.name} ${page.url()}: ${msg.text()}`)
  })
  page.on('pageerror', (err) => issues.push(`[pageerror] ${vp.name} ${page.url()}: ${err.message}`))

  for (const route of routes) {
    await page.goto(BASE + route.path, { waitUntil: 'networkidle' })
    await page.waitForTimeout(400)
    const overflow = await page.evaluate(() => {
      const doc = document.documentElement
      return { scrollW: doc.scrollWidth, clientW: doc.clientWidth }
    })
    if (overflow.scrollW > overflow.clientW + 1) {
      issues.push(`[overflow] ${vp.name} ${route.path}: scrollWidth=${overflow.scrollW} > clientWidth=${overflow.clientW}`)
    }
    await page.screenshot({ path: `${OUT}${route.name}-${vp.name}.png`, fullPage: route.name !== 'agent' })
  }
  await context.close()
}

await browser.close()
if (issues.length) {
  console.log('ISSUES:')
  for (const issue of issues) console.log(issue)
} else {
  console.log('NO ISSUES DETECTED')
}
