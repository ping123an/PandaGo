const puppeteer = require('../frontend/node_modules/puppeteer-core');
const BASE = 'http://127.0.0.1:5173';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const OUT = 'D:/AI_example/DH_test/Mark/docs';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function shot(browser, path, file, opts = {}) {
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();
  await page.setViewport({ width: opts.w || 1280, height: opts.h || 900 });
  await page.goto(BASE + path, { waitUntil: 'networkidle2' });
  await sleep(700);
  if (opts.loginAs) {
    // 登录进后台：直接调接口拿 token 写入 localStorage，再刷新目标页
    const token = await page.evaluate(({ u, p, as }) => {
      return fetch('/api/v1/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: u, password: p, loginAs: as }),
      }).then((r) => r.json()).then((j) => j?.data?.token);
    }, opts.loginAs);
    await page.evaluate((t) => localStorage.setItem('pandago_token', t), token);
    await page.goto(BASE + path, { waitUntil: 'networkidle2' });
    await sleep(700);
  }
  await page.screenshot({ path: `${OUT}/${file}` });
  const info = await page.evaluate(() => {
    const imgs = Array.from(document.querySelectorAll('img')).map((i) => i.getAttribute('src') || '');
    return {
      markImgs: imgs.filter((s) => s.includes('pandago-mark')).length,
      bigPanda: !!document.querySelector('.big-panda'),
      bamboo: document.querySelectorAll('.bamboo').length,
      brandLogoCount: document.querySelectorAll('.brand-logo').length,
    };
  });
  console.log(`${path} -> ${file}`, JSON.stringify(info));
  await ctx.close();
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
  try {
    await shot(browser, '/', 'logo-home.png');
    await shot(browser, '/login', 'logo-login.png');
    await shot(browser, '/register', 'logo-register.png');
    await shot(browser, '/login/user', 'logo-identity.png');
    await shot(browser, '/merchant', 'logo-dashboard.png', {
      loginAs: { u: 'merchant_demo', p: 'Panda@12345', as: 'MERCHANT' },
    });

    // favicon 校验
    const ctx = await browser.createBrowserContext();
    const page = await ctx.newPage();
    await page.goto(BASE, { waitUntil: 'networkidle2' });
    const fav = await page.evaluate(() => document.querySelector('link[rel="icon"]')?.getAttribute('href'));
    const favSvg = await page.evaluate(async () => {
      const r = await fetch('/favicon.svg'); const t = await r.text();
      return { hasPandaFace: t.includes('ellipse') && t.includes('panda'), hasBag: t.includes('#e1251b') };
    });
    console.log('favicon href:', fav, JSON.stringify(favSvg));
    await ctx.close();
  } finally {
    await browser.close();
    console.log('done');
  }
})().catch((e) => { console.error(e); process.exitCode = 2; });
