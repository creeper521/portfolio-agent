import { chromium } from 'playwright';

const shots = [
  { label: 'A-original', variant: 'v-original', cta: 'ink' },
  { label: 'B-warm-l1-ink', variant: 'v-warm l1', cta: 'ink' },
  { label: 'C-warm-l2-ink', variant: 'v-warm l2', cta: 'ink' },
  { label: 'D-warm-l3-red', variant: 'v-warm l3', cta: 'red' },
];

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1340, height: 800 } });
await page.goto('file:///D:/code/agent/design-exploration/warm-cream-compare.html');

for (const s of shots) {
  // click variant button matching data-v
  await page.evaluate((variant) => {
    const btns = [...document.querySelectorAll('#seg-variant button')];
    btns.forEach(b => b.classList.toggle('on', b.dataset.v === variant));
    const ctaBtns = [...document.querySelectorAll('#seg-cta button')];
    // set cta via the apply logic
    window.__setCta = ctaBtns.find(b=>b.classList.contains('on'))?.dataset.cta;
  }, s.variant);
  // apply via clicking the variant button then cta button
  await page.click(`#seg-variant button[data-v="${s.variant}"]`);
  await page.click(`#seg-cta button[data-cta="${s.cta}"]`);
  await page.waitForTimeout(250);
  await page.screenshot({ path: `D:/code/agent/design-exploration/shot-${s.label}.png` });
  console.log('shot', s.label);
}
await browser.close();
console.log('done');
