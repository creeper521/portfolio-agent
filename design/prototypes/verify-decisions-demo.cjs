/* eslint-disable no-console */
// P2 前端决策点 demo 自检脚本：截图 + 交互冒烟 + 控制台错误收集。非生产代码。
const { chromium } = require('../../frontend/node_modules/playwright')
const path = require('path')

const FILE = 'file:///' + path.resolve(__dirname, 'semantic-turn-routing-frontend-decisions-demo.html').replace(/\\/g, '/')
const OUT = path.resolve(__dirname, 'shots')

async function main() {
  const browser = await chromium.launch()
  const errors = []

  async function newPage(viewport) {
    const page = await browser.newPage({ viewport })
    page.on('pageerror', (err) => errors.push(`pageerror: ${err.message}`))
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(`console: ${msg.text()}`)
    })
    await page.goto(FILE)
    await page.waitForTimeout(600)
    return page
  }

  // 桌面全页
  const desktop = await newPage({ width: 1440, height: 900 })
  await desktop.screenshot({ path: path.join(OUT, 'decisions-demo-desktop-full.png'), fullPage: true })

  // 场景 1 交互：进入调整态
  await desktop.locator('#btn-adjust').click()
  await desktop.locator('#s1').screenshot({ path: path.join(OUT, 'decisions-demo-s1-adjusting.png') })
  const adjustingLabel = await desktop.locator('#plan-status').textContent()
  console.log('调整态角标:', adjustingLabel)
  // 调整中确认原计划（反悔路径）
  await desktop.locator('#btn-confirm').click()
  await desktop.waitForTimeout(300)
  const toastText = await desktop.locator('#demo-toast').textContent()
  console.log('调整中确认 toast:', toastText)
  await desktop.locator('#btn-adjust-exit').click()
  console.log('退出调整后角标:', await desktop.locator('#plan-status').textContent())

  // 场景 2a：单选 pending
  await desktop.locator('[data-demo-select]').first().click()
  const singlePending = await desktop.locator('#clarify-single').getAttribute('data-pending')
  console.log('单选提交后 pending:', singlePending)

  // 场景 2b：多选禁用/启用
  const multiSubmit = desktop.locator('#btn-multi-submit')
  console.log('多选初始禁用:', await multiSubmit.isDisabled())
  await desktop.locator('[data-demo-multi]').nth(0).click()
  await desktop.locator('[data-demo-multi]').nth(1).click()
  console.log('多选两项后禁用:', await multiSubmit.isDisabled())
  await desktop.locator('#s2').screenshot({ path: path.join(OUT, 'decisions-demo-s2-multi.png') })

  // 场景 2c：SHORT_TEXT 空禁提交
  const textSubmit = desktop.locator('#btn-text-submit')
  console.log('SHORT_TEXT 空输入禁用:', await textSubmit.isDisabled())
  await desktop.locator('#short-text-input').fill('先只做介绍这两步')
  console.log('SHORT_TEXT 有输入禁用:', await textSubmit.isDisabled())

  // 场景 3：摘要折叠/展开
  await desktop.locator('#summary-ok [data-demo-toggle]').click()
  console.log('全成功摘要展开:', await desktop.locator('#summary-ok').getAttribute('data-open'))
  console.log('部分成功摘要默认展开:', await desktop.locator('#summary-partial').getAttribute('data-open'))
  await desktop.locator('#s3').screenshot({ path: path.join(OUT, 'decisions-demo-s3-expanded.png') })

  // 场景 4：暂不处理
  await desktop.locator('#btn-dismiss').click()
  const cardVisible = await desktop.locator('#invalidated-card').isVisible()
  const noteVisible = await desktop.locator('#dismissed-note').isVisible()
  console.log('dismiss 后卡片隐藏:', !cardVisible, '· 记录行显示:', noteVisible)
  await desktop.locator('#s4').screenshot({ path: path.join(OUT, 'decisions-demo-s4-dismissed.png') })

  // 横向溢出检查（桌面）
  const overflowDesktop = await desktop.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  console.log('桌面横向溢出 px:', overflowDesktop)
  await desktop.close()

  // 移动端
  const mobile = await newPage({ width: 390, height: 844 })
  await mobile.screenshot({ path: path.join(OUT, 'decisions-demo-mobile-full.png'), fullPage: true })
  const overflowMobile = await mobile.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  console.log('移动横向溢出 px:', overflowMobile)
  await mobile.locator('#btn-adjust').click()
  await mobile.locator('#s1').screenshot({ path: path.join(OUT, 'decisions-demo-mobile-s1-adjusting.png') })
  await mobile.close()

  await browser.close()
  console.log(errors.length ? `控制台错误 ${errors.length} 条:\n${errors.join('\n')}` : '控制台错误: 0')
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
