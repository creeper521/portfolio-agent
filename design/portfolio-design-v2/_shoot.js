const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch();
  const files = [
    ['A-case-file.html', 'A'],
    ['B-log-stream.html', 'B'],
    ['C-ops-ledger.html', 'C'],
  ];
  for (const [file, tag] of files) {
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    const url = 'file:///' + path.resolve(__dirname, file).replace(/\\/g, '/');
    await page.goto(url, { waitUntil: 'networkidle' });
    await page.screenshot({ path: path.join(__dirname, '_shot-' + tag + '-full.png'), fullPage: true });
    await page.screenshot({ path: path.join(__dirname, '_shot-' + tag + '-fold.png') });
    await page.close();
    console.log('shot', tag);
  }

  // B: also capture the fallback-toggle state (signature interaction)
  const pb = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  await pb.goto('file:///' + path.resolve(__dirname, 'B-log-stream.html').replace(/\\/g, '/'), { waitUntil: 'networkidle' });
  await pb.click('#engFb');
  await pb.waitForTimeout(300);
  await pb.screenshot({ path: path.join(__dirname, '_shot-B-fallback.png') });
  await pb.close();
  console.log('shot B fallback');

  // C: capture the ledger table region specifically
  const pc = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  await pc.goto('file:///' + path.resolve(__dirname, 'C-ops-ledger.html').replace(/\\/g, '/'), { waitUntil: 'networkidle' });
  const tbl = await pc.$('table.ledger');
  if (tbl) await tbl.screenshot({ path: path.join(__dirname, '_shot-C-ledger.png') });
  await pc.close();
  console.log('shot C ledger');

  // A: capture a stamp + redaction region
  const pa = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  await pa.goto('file:///' + path.resolve(__dirname, 'A-case-file.html').replace(/\\/g, '/'), { waitUntil: 'networkidle' });
  // reveal one redaction first
  await pa.evaluate(() => { const r = document.querySelector('.redacted'); if (r) r.classList.add('revealed'); });
  const ev = await pa.$('.evidence-grid');
  if (ev) await ev.screenshot({ path: path.join(__dirname, '_shot-A-evidence.png') });
  await pa.close();
  console.log('shot A evidence');

  await browser.close();
  console.log('done');
})();
