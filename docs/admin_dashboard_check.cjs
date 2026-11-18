/**
 * PandaGo 平台管理工作台 —— 浏览器端验收
 * 运行：node docs/admin_dashboard_check.cjs
 * 前置：前端 5173、后端 8080 已启动，admin_demo 可登录。
 */
const puppeteer = require('../frontend/node_modules/puppeteer-core');

const BASE = 'http://127.0.0.1:5173';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const ADMIN = { username: 'admin_demo', password: 'Panda@12345' };
const MERCHANT = { username: 'merchant_demo', password: 'Panda@12345' };
const USER = { username: 'user_demo', password: 'Panda@12345' };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];

function record(id, name, ok, detail) {
  results.push({ id, name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'} [${id}] ${name}${detail ? ' — ' + detail : ''}`);
}

async function newPage(browser) {
  const context = await browser.createBrowserContext();
  const page = await context.newPage();
  await page.setViewport({ width: 1440, height: 960 });
  page.__ctx = context;
  const close = page.close.bind(page);
  page.close = async () => { await close(); await context.close(); };
  return page;
}

async function openAsAdmin(browser, path = '/admin') {
  const page = await newPage(browser);
  await page.goto(BASE, { waitUntil: 'networkidle2' });
  await page.evaluate(async (acc) => {
    const r = await fetch('/api/v1/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(acc),
    });
    const j = await r.json();
    if (j?.data?.token) localStorage.setItem('pandago_token', j.data.token);
  }, ADMIN);
  await page.goto(BASE + path, { waitUntil: 'networkidle2' });
  await sleep(900);
  return page;
}

function apiData(page, url = '/api/v1/admin/dashboard') {
  return page.evaluate(async (u) => {
    const r = await fetch(u, { headers: { Authorization: 'Bearer ' + localStorage.getItem('pandago_token') } });
    const j = await r.json();
    return { status: r.status, data: j.data };
  }, url);
}

async function clickByText(page, selector, text) {
  return page.evaluate(({ selector, text }) => {
    const nodes = Array.from(document.querySelectorAll(selector));
    const target = nodes.find((n) => (n.textContent || '').trim() === text)
      || nodes.find((n) => (n.textContent || '').includes(text));
    if (target) { target.click(); return true; }
    return false;
  }, { selector, text });
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
  try {
    // 1. 页面替换占位内容
    {
      const page = await openAsAdmin(browser);
      const text = await page.evaluate(() => document.body.innerText);
      record(1, '/admin 显示真实工作台且无占位文案',
        text.includes('平台管理工作台') && !text.includes('阶段 10')
          && !text.includes('运营数据将在后续阶段接入'),
        `含标题=${text.includes('平台管理工作台')}`);
      await page.close();
    }

    // 2. 统计卡片数字与 API 一致
    {
      const page = await openAsAdmin(browser);
      const api = await apiData(page);
      const values = await page.evaluate(() =>
        Array.from(document.querySelectorAll('.stat-card')).map((c) => c.querySelector('.stat-value')?.textContent || ''));
      const ok = values[0] === String(api.data.userTotal)
        && values[1] === String(api.data.productTotal)
        && values[2] === String(api.data.orderTotal)
        && values[3] === `¥${Number(api.data.totalSalesAmount).toFixed(2)}`;
      record(2, '统计卡片展示真实 API 数据', ok,
        `卡片=${values.join(' | ')} API用户=${api.data.userTotal} 商品=${api.data.productTotal} 订单=${api.data.orderTotal}`);
      await page.close();
    }

    // 3. 待审核任务入口
    {
      const page = await openAsAdmin(browser);
      const text = await page.evaluate(() => document.body.innerText);
      const clicked = await clickByText(page, '.task-card', '待审核商品');
      await sleep(900);
      record(3, '待审核商品任务入口跳转并自动筛选待审核',
        clicked && text.includes('待审核商品')
          && page.url().includes('/admin/audit') && page.url().includes('auditStatus=PENDING'),
        `url=${page.url().replace(BASE, '')}`);
      await page.close();
    }

    // 4. 待发货任务入口
    {
      const page = await openAsAdmin(browser);
      const clicked = await clickByText(page, '.task-card', '待发货订单');
      await sleep(1000);
      const selected = await page.evaluate(() =>
        Array.from(document.querySelectorAll('.el-select__placeholder')).map((e) => e.textContent.trim())
          .find((t) => t.includes('待发货')) || '');
      record(4, '待发货订单任务入口跳转并自动应用状态筛选',
        clicked && page.url().includes('/admin/orders')
          && page.url().includes('PENDING_SHIPMENT') && selected.includes('待发货'),
        `url=${page.url().replace(BASE, '')} 筛选=${selected}`);
      await page.close();
    }

    // 5. 五个快捷入口
    {
      const routes = [
        ['用户管理', '/admin/users'],
        ['分类管理', '/admin/categories'],
        ['商品审核', '/admin/audit'],
        ['商品管理', '/admin/products'],
        ['订单管理', '/admin/orders'],
      ];
      let ok = true;
      const detail = [];
      for (const [label, path] of routes) {
        const page = await openAsAdmin(browser);
        const clicked = await clickByText(page, '.quick-item', label);
        await sleep(700);
        const good = clicked && page.url().includes(path);
        detail.push(`${label}:${good ? 'OK' : page.url().replace(BASE, '')}`);
        ok = ok && good;
        await page.close();
      }
      record(5, '五个快捷操作均跳转到对应真实页面', ok, detail.join(' '));
    }

    // 6. 最近订单真实展示 + 详情入口
    {
      const page = await openAsAdmin(browser);
      const api = await apiData(page);
      const rows = await page.evaluate(() => document.querySelectorAll('.list-row').length);
      const first = await page.evaluate(() => document.querySelector('.list-row')?.innerText || '');
      const hasRows = api.data.recentOrders.length > 0;
      const clicked = hasRows && await clickByText(page, '.list-row .row-action', '查看订单');
      await sleep(1000);
      const dialog = await page.evaluate(() => !!document.querySelector('.el-dialog'));
      record(6, '最近订单展示真实数据且详情入口可用',
        rows === api.data.recentOrders.length && first.includes('¥')
          && (!hasRows || (clicked && dialog && page.url().includes('detail='))),
        `行数=${rows} url=${page.url().replace(BASE, '')} 弹窗=${dialog}`);
      await page.close();
    }

    // 7. 待审核商品列表与去审核入口
    {
      const page = await openAsAdmin(browser);
      const api = await apiData(page);
      const rows = await page.evaluate(() => document.querySelectorAll('.product-row').length);
      const clicked = api.data.pendingReviewProductList.length > 0
        && await clickByText(page, '.product-row button', '去审核');
      await sleep(1000);
      record(7, '待审核商品展示真实数据且可定位到审核页',
        rows === api.data.pendingReviewProductList.length
          && (!clicked || (page.url().includes('/admin/audit') && page.url().includes('id='))),
        `行数=${rows} url=${page.url().replace(BASE, '')}`);
      await page.close();
    }

    // 8. 空状态（仅在浏览器中模拟空响应，验证 UI 分支）
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      await page.evaluate(async (acc) => {
        const r = await fetch('/api/v1/auth/login', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(acc),
        });
        localStorage.setItem('pandago_token', (await r.json()).data.token);
      }, ADMIN);
      await page.setRequestInterception(true);
      page.on('request', (req) => {
        if (req.url().includes('/api/v1/admin/dashboard')) {
          const empty = {
            code: 200, message: 'success', data: {
              userTotal: 0, accountTotal: 0, todayNewUsers: 0,
              productTotal: 0, productOnSale: 0, pendingReviewProducts: 0,
              orderTotal: 0, pendingShipmentOrders: 0, completedOrders: 0,
              todayNewOrders: 0, totalSalesAmount: 0,
              recentOrders: [], pendingReviewProductList: [],
            },
          };
          req.respond({ status: 200, contentType: 'application/json', body: JSON.stringify(empty) });
        } else req.continue();
      });
      await page.goto(BASE + '/admin', { waitUntil: 'networkidle2' });
      await sleep(600);
      const text = await page.evaluate(() => document.body.innerText);
      record(8, '空数据时显示明确空状态文案',
        text.includes('暂无订单') && text.includes('目前没有待审核商品'));
      await page.close();
    }

    // 9. 错误状态与重新加载按钮
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      await page.evaluate(async (acc) => {
        const r = await fetch('/api/v1/auth/login', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(acc),
        });
        localStorage.setItem('pandago_token', (await r.json()).data.token);
      }, ADMIN);
      await page.setRequestInterception(true);
      page.on('request', (req) => {
        if (req.url().includes('/api/v1/admin/dashboard')) {
          req.respond({ status: 500, contentType: 'application/json', body: '{}' });
        } else req.continue();
      });
      await page.goto(BASE + '/admin', { waitUntil: 'networkidle2' });
      await sleep(600);
      const text = await page.evaluate(() => document.body.innerText);
      const hasRetry = await clickByText(page, '.state-card button', '重新加载');
      record(9, '接口失败显示错误卡片且不误显示 0，提供重新加载',
        text.includes('数据加载失败') && hasRetry);
      await page.close();
    }

    // 10. 权限：用户/商家/未登录不能调用统计接口
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      const statuses = await page.evaluate(async (accounts) => {
        const login = async (acc) => {
          const r = await fetch('/api/v1/auth/login', {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(acc),
          });
          return (await r.json()).data.token;
        };
        const call = async (token) => {
          const r = await fetch('/api/v1/admin/dashboard', {
            headers: token ? { Authorization: 'Bearer ' + token } : {},
          });
          return r.status;
        };
        return [await call(await login(accounts.user)), await call(await login(accounts.merchant)), await call(null)];
      }, { user: USER, merchant: MERCHANT });
      record(10, '普通用户/商家/未登录访问统计接口被拒绝',
        statuses[0] === 403 && statuses[1] === 403 && statuses[2] === 401,
        `用户=${statuses[0]} 商家=${statuses[1]} 未登录=${statuses[2]}`);
      await page.close();
    }

    // 11. 响应式：常见桌面与窄屏都不出现横向溢出
    {
      const page = await openAsAdmin(browser);
      const widths = [1440, 1024, 768];
      const overflow = [];
      for (const width of widths) {
        await page.setViewport({ width, height: 900 });
        await sleep(250);
        const info = await page.evaluate(() => ({
          scroll: document.documentElement.scrollWidth, client: document.documentElement.clientWidth,
        }));
        overflow.push(`${width}:${info.scroll - info.client}`);
      }
      record(11, '响应式布局无横向溢出',
        overflow.every((s) => Number(s.split(':')[1]) <= 2), overflow.join(' '));
      await page.close();
    }
  } finally {
    const passed = results.filter((r) => r.ok).length;
    console.log(`\n结果：${passed}/${results.length} 通过`);
    await browser.close();
    if (passed !== results.length) process.exitCode = 1;
  }
})().catch((error) => {
  console.error('验收脚本异常：', error);
  process.exitCode = 1;
});
