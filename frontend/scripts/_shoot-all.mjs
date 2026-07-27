import { chromium } from 'playwright'

const base = 'http://localhost:8080'
const pages = [
  ['home', '/'],
  ['projects', '/projects'],
  ['project-detail', '/projects/sql-audit'],
  ['timeline', '/timeline'],
  ['evidence', '/evidence'],
  ['agent', '/agent'],
]

const browser = await chromium.launch()
for (const [name, path] of pages) {
  for (const [tag, vp] of [['d', { width: 1440, height: 900 }], ['m', { width: 390, height: 844 }]]) {
    const page = await browser.newPage({ viewport: vp })
    await page.goto(base + path, { waitUntil: 'networkidle' })
    await page.waitForTimeout(1200)
    await page.screenshot({ path: `../output/shots/${tag}-${name}.png`, fullPage: true })
    console.log(`${tag}-${name} done`)
    await page.close()
  }
}
await browser.close()
