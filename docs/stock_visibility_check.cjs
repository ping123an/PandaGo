/**
 * 库存可见性浏览器验收
 * 运行：node docs/stock_visibility_check.cjs
 */
const puppeteer = require('../frontend/node_modules/puppeteer-core');
const BASE = 'http://127.0.0.1:5173';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
function record(id, name, ok, detail) {
  results.push({ id, name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'} [${id}] ${name}${detail ? ' — ' + detail : ''}`);
}
async function newPage(browser) {
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();
  await page.setViewport({ width: 1280, height: 900 });
  page.closeCtx = async () => { await page.close(); await ctx.close(); };
  return page;
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
  try {
    // 1&2. 用户端商品列表：商品卡片不显示具体库存数字
    {
      const page = await newPage(browser);
      await page.goto(BASE + '/products', { waitUntil: 'networkidle2' });
      await sleep(1200);
      const text = await page.evaluate(() => document.body.innerText);
      const hasStockNumber = /库存\s*\d/.test(text) || /\d+\s*件\s*·\s*库存/.test(text);
      const hasStatus = text.includes('有货') || text.includes('暂时缺货');
      record(1, '用户端列表卡片不显示具体库存数量', !hasStockNumber && hasStatus,
        `含库存数字=${hasStockNumber} 含状态=${hasStatus}`);
      await page.closeCtx();
    }

    // 3. 首页卡片同样不显示库存数字
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      await sleep(1200);
      const text = await page.evaluate(() => document.body.innerText);
      const hasStockNumber = /库存\s*\d/.test(text);
      record(2, '首页商品卡片不显示具体库存数量', !hasStockNumber, `含库存数字=${hasStockNumber}`);
      await page.closeCtx();
    }

    // 4. 用户端详情（当前 id=1 库存已设为0）：显示"暂时缺货"且加入购物车按钮禁用
    {
      const page = await newPage(browser);
      await page.goto(BASE + '/products/1', { waitUntil: 'networkidle2' });
      await sleep(1000);
      const info = await page.evaluate(() => {
        const btns = Array.from(document.querySelectorAll('button'));
        const addBtn = btns.find((b) => (b.textContent || '').includes('加入购物车'));
        return {
          text: document.body.innerText,
          addDisabled: addBtn ? addBtn.disabled : null,
        };
      });
      const noStockNumber = !/库存\s*\d/.test(info.text);
      const showsOutOfStock = info.text.includes('暂时缺货');
      record(3, '缺货商品详情：无库存数字、显示暂时缺货、禁止加购',
        noStockNumber && showsOutOfStock && info.addDisabled === true,
        `无库存数字=${noStockNumber} 显示缺货=${showsOutOfStock} 加购禁用=${info.addDisabled}`);
      await page.closeCtx();
    }

    // 5. 有货商品详情：显示"有货"，加购按钮可用（用 id=2）
    {
      const page = await newPage(browser);
      await page.goto(BASE + '/products/2', { waitUntil: 'networkidle2' });
      await sleep(1000);
      const info = await page.evaluate(() => {
        const btns = Array.from(document.querySelectorAll('button'));
        const addBtn = btns.find((b) => (b.textContent || '').includes('加入购物车'));
        return { text: document.body.innerText, addDisabled: addBtn ? addBtn.disabled : null };
      });
      const noStockNumber = !/库存\s*\d/.test(info.text);
      const showsInStock = info.text.includes('有货');
      record(4, '有货商品详情：无库存数字、显示有货、可加购',
        noStockNumber && showsInStock && info.addDisabled === false,
        `无库存数字=${noStockNumber} 显示有货=${showsInStock} 加购可用=${info.addDisabled === false}`);
      await page.closeCtx();
    }

    // 6. 商家后台：仍能看到具体库存数量
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      const token = await page.evaluate(async () => {
        const r = await fetch('/api/v1/auth/login', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: 'merchant_demo', password: 'Panda@12345' }),
        });
        return (await r.json()).data.token;
      });
      await page.evaluate((t) => localStorage.setItem('pandago_token', t), token);
      await page.goto(BASE + '/merchant/products', { waitUntil: 'networkidle2' });
      await sleep(1200);
      const info = await page.evaluate(() => {
        const text = document.body.innerText;
        const headers = Array.from(document.querySelectorAll('th')).map((t) => t.innerText.trim());
        return { text, hasStockCol: headers.includes('库存') };
      });
      record(5, '商家后台商品列表保留具体库存列', info.hasStockCol, `库存列=${info.hasStockCol}`);
      await page.closeCtx();
    }

    // 7. 后端加购接口对缺货商品仍然拦截（真实库存校验，不依赖前端）
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      const status = await page.evaluate(async () => {
        const lr = await fetch('/api/v1/auth/login', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: 'user_demo', password: 'Panda@12345' }),
        });
        const token = (await lr.json()).data.token;
        const r = await fetch('/api/v1/cart/items', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
          body: JSON.stringify({ productId: 1, quantity: 1 }),
        });
        return r.status;
      });
      record(6, '后端对缺货商品加购仍做真实库存校验', status === 400 || status === 409,
        `HTTP ${status}`);
      await page.closeCtx();
    }
  } finally {
    await browser.close();
    const pass = results.filter((r) => r.ok).length;
    console.log(`\n===== 结果：${pass}/${results.length} 通过 =====`);
    if (pass !== results.length) process.exitCode = 1;
  }
})().catch((e) => { console.error('脚本异常：', e); process.exitCode = 2; });
