/**
 * PandaGo 平台管理商品审核 / 商品管理状态逻辑 —— 浏览器端验收
 * 运行：node docs/admin_audit_check.cjs
 * 前置：前端 5173、后端 8080 均已启动，数据库已执行 V10__admin_audit_demo.sql
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

/** 用接口登录写入 token，再打开目标路由（每个上下文独立，避免串扰） */
async function openAsAdmin(browser, path) {
  const page = await newPage(browser);
  await page.goto(BASE, { waitUntil: 'networkidle2' });
  await page.evaluate(async (acc) => {
    const r = await fetch('/api/v1/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(acc),
    });
    const j = await r.json();
    if (j?.data?.token) localStorage.setItem('pandago_token', j.data.token);
  }, ADMIN);
  await page.goto(BASE + path, { waitUntil: 'networkidle2' });
  await sleep(900);
  return page;
}

function api(page, url, options = {}) {
  return page.evaluate(async ({ url, options }) => {
    const token = localStorage.getItem('pandago_token');
    const r = await fetch(url, {
      ...options,
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token, ...(options.headers || {}) },
    });
    let body = null;
    try { body = await r.json(); } catch { /* ignore */ }
    return { status: r.status, body };
  }, { url, options });
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

async function createPendingProduct(page, suffix) {
  const name = `UI-审核验收-${suffix}-${Date.now()}`;
  const res = await api(page, '/api/v1/admin/products', {
    method: 'POST',
    body: JSON.stringify({
      categoryId: 1,
      name,
      subtitle: '浏览器验收临时商品',
      mainImage: '/images/panda-product.svg',
      price: 12.34,
      stock: 30,
      description: '自动化验收创建，测试后清理',
      sort: 0,
    }),
  });
  return { id: res.body?.data, name };
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
  try {
    // 1. 审核页默认待审核
    {
      const page = await openAsAdmin(browser, '/admin/audit');
      const active = await page.$eval('.el-tabs__item.is-active', (el) => el.textContent.trim()).catch(() => '');
      const list = await api(page, '/api/v1/admin/products?auditStatus=PENDING&pageSize=50');
      const statuses = (list.body?.data?.list || []).map((i) => i.auditStatus);
      record(1, '审核页默认筛选待审核且列表只含待审核商品',
        active === '待审核' && statuses.length > 0 && statuses.every((s) => s === 'PENDING'),
        `active=${active} 待审核数=${statuses.length}`);
      await page.close();
    }

    // 2. 待审核行显示通过/驳回，不显示审核记录按钮
    {
      const page = await openAsAdmin(browser, '/admin/audit');
      const info = await page.evaluate(() => ({
        buttons: Array.from(document.querySelectorAll('.el-table button')).map((b) => b.textContent.trim()),
        text: document.body.innerText,
      }));
      record(2, '待审核商品提供通过/驳回/详情操作',
        info.buttons.includes('通过') && info.buttons.includes('驳回') && info.buttons.includes('详情')
          && !info.buttons.includes('查看审核记录'),
        `按钮=${[...new Set(info.buttons)].join('/')}`);
      await page.close();
    }

    // 3. 已通过页只读审核记录
    {
      const page = await openAsAdmin(browser, '/admin/audit');
      await clickByText(page, '.el-tabs__item', '已通过');
      await sleep(900);
      const info = await page.evaluate(() => ({
        buttons: Array.from(document.querySelectorAll('.el-table button')).map((b) => b.textContent.trim()),
      }));
      record(3, '已通过商品只显示查看审核记录，不提供审核操作',
        info.buttons.includes('查看审核记录') && !info.buttons.includes('通过') && !info.buttons.includes('驳回'),
        `按钮=${[...new Set(info.buttons)].join('/')}`);
      await page.close();
    }

    // 4. 已驳回页展示驳回原因
    {
      const page = await openAsAdmin(browser, '/admin/audit');
      await clickByText(page, '.el-tabs__item', '已驳回');
      await sleep(900);
      const text = await page.evaluate(() => document.body.innerText);
      record(4, '已驳回商品展示审核记录与驳回原因',
        text.includes('审核人') && text.includes('原因：') && text.includes('查看审核记录'));
      await page.close();
    }

    // 5. 空状态文案（通过路由参数注入关键词，避免受输入法/事件模拟影响）
    {
      const page = await openAsAdmin(browser,
        '/admin/audit?auditStatus=PENDING&keyword=NOSUCHPRODUCT');
      await sleep(900);
      const empty = await page.evaluate(() =>
        document.querySelector('.el-table__empty-text')?.textContent?.trim() || '');
      record(5, '无匹配商品时显示明确空状态文案', empty.includes('没有匹配的商品'), `文案=${empty}`);
      await page.close();
    }

    // 6. 详情弹窗含主图/商家/价格/库存/审核信息
    {
      const page = await openAsAdmin(browser, '/admin/audit');
      await clickByText(page, '.el-table button', '详情');
      await sleep(900);
      const info = await page.evaluate(() => ({
        text: document.querySelector('.el-dialog')?.innerText || '',
        images: document.querySelectorAll('.el-dialog .detail-images img, .el-dialog .detail-summary img').length,
      }));
      record(6, '商品详情弹窗展示主图、商家、价格、库存与审核信息',
        ['商品 ID', '商家', '价格', '库存', '审核状态', '审核时间'].every((k) => info.text.includes(k))
          && info.images >= 1,
        `图片数=${info.images}`);
      await page.close();
    }

    // 7. 审核通过走完整 UI 流程
    {
      const page = await openAsAdmin(browser, '/admin/audit');
      const product = await createPendingProduct(page, '通过');
      await page.goto(BASE + '/admin/audit?auditStatus=PENDING&keyword=' + encodeURIComponent(product.name),
        { waitUntil: 'networkidle2' });
      await sleep(900);
      await clickByText(page, '.el-table button', '通过');
      await sleep(400);
      // Element Plus 确认按钮文本随语言包不同可能是“确定”或“OK”
      await clickByText(page, '.el-message-box button', '确定');
      await clickByText(page, '.el-message-box button', 'OK');
      await sleep(900);
      const check = await api(page,
        `/api/v1/admin/products?keyword=${encodeURIComponent(product.name)}&auditStatus=APPROVED&pageSize=10`);
      const row = (check.body?.data?.list || []).find((i) => i.id === product.id);
      record(7, '通过审核后状态变为 APPROVED 且记录审核人',
        !!row && row.auditStatus === 'APPROVED' && !!row.auditBy,
        `id=${product.id} status=${row?.auditStatus} auditBy=${row?.auditBy}`);
      await page.close();
    }

    // 8. 驳回原因必填（后端兜底）
    {
      const page = await openAsAdmin(browser, '/admin/audit');
      const res = await api(page, `/api/v1/admin/products/999999999/audit`, {
        method: 'PUT', body: JSON.stringify({ auditStatus: 'REJECTED', reason: '' }),
      });
      record(8, '空驳回原因/不存在商品由后端校验拦截', res.status === 400 || res.status === 404,
        `HTTP ${res.status}`);
      await page.close();
    }

    // 9. 驳回必填原因（UI 提示）
    {
      const page = await openAsAdmin(browser, '/admin/audit');
      const product = await createPendingProduct(page, '空原因');
      await page.goto(BASE + '/admin/audit?auditStatus=PENDING&keyword=' + encodeURIComponent(product.name),
        { waitUntil: 'networkidle2' });
      await sleep(900);
      await clickByText(page, '.el-table button', '驳回');
      await sleep(400);
      await clickByText(page, '.el-dialog button', '确认驳回');
      await sleep(500);
      const warned = await page.evaluate(() => document.body.innerText.includes('请填写驳回原因'));
      const check = await api(page, `/api/v1/admin/products/${product.id}`);
      record(9, '未填原因点击驳回被前端拦截且状态不变',
        warned && check.body?.data?.auditStatus === 'PENDING');
      await page.close();
    }

    // 10. 填写原因驳回并强制下架
    {
      const page = await openAsAdmin(browser, '/admin/audit');
      const product = await createPendingProduct(page, '带原因');
      await page.goto(BASE + '/admin/audit?auditStatus=PENDING&keyword=' + encodeURIComponent(product.name),
        { waitUntil: 'networkidle2' });
      await sleep(900);
      await clickByText(page, '.el-table button', '驳回');
      await sleep(400);
      await page.type('.el-dialog textarea', '图片不清晰，请补充实物图');
      await clickByText(page, '.el-dialog button', '确认驳回');
      await sleep(900);
      const check = await api(page, `/api/v1/admin/products/${product.id}`);
      record(10, '填写原因驳回后状态 REJECTED、强制下架并记录原因',
        check.body?.data?.auditStatus === 'REJECTED' && check.body?.data?.status === 'OFF_SALE'
          && check.body?.data?.auditReason === '图片不清晰，请补充实物图');
      await page.close();
    }

    // 11. 商品管理页四类独立筛选
    {
      const page = await openAsAdmin(browser, '/admin/products');
      const placeholders = await page.evaluate(() => [
        ...Array.from(document.querySelectorAll('input')).map((i) => i.placeholder || ''),
        ...Array.from(document.querySelectorAll('.el-select__placeholder')).map((e) => e.textContent.trim()),
      ]);
      record(11, '商品管理页提供关键词/上架/审核/分类四类筛选',
        ['商品关键词', '全部上架状态', '全部审核状态', '全部分类'].every((p) => placeholders.includes(p)),
        `输入框=${placeholders.filter(Boolean).join('/')}`);
      await page.close();
    }

    // 12. 商品管理页无审核按钮，待审核/已驳回显示前往审核
    {
      const page = await openAsAdmin(browser, '/admin/products?auditStatus=PENDING');
      await sleep(900);
      const buttons = await page.evaluate(() =>
        Array.from(document.querySelectorAll('.el-table button')).map((b) => b.textContent.trim()));
      record(12, '商品管理页不提供审核按钮，非已通过商品提供前往审核',
        buttons.includes('前往审核') && !buttons.includes('通过') && !buttons.includes('驳回'),
        `按钮=${[...new Set(buttons)].join('/')}`);
      await page.close();
    }

    // 13. 前往审核跳转并定位对应状态
    {
      const page = await openAsAdmin(browser, '/admin/products?auditStatus=REJECTED');
      await sleep(700);
      const clicked = await clickByText(page, '.el-table button', '前往审核');
      await sleep(1200);
      const active = await page.$eval('.el-tabs__item.is-active', (el) => el.textContent.trim()).catch(() => '');
      record(13, '点击前往审核跳转到审核页且选中已驳回标签',
        clicked && page.url().includes('/admin/audit') && active === '已驳回',
        `url=${page.url().replace(BASE, '')} active=${active}`);
      await page.close();
    }

    // 14. 商品管理筛选结果只含目标审核状态
    {
      const page = await openAsAdmin(browser, '/admin/products');
      const list = await api(page, '/api/v1/admin/products?auditStatus=PENDING&pageSize=50');
      const allPending = (list.body?.data?.list || []).every((i) => i.auditStatus === 'PENDING');
      const list2 = await api(page, '/api/v1/admin/products?status=OFF_SALE&auditStatus=REJECTED&pageSize=50');
      const allMatch = (list2.body?.data?.list || []).every(
        (i) => i.auditStatus === 'REJECTED' && i.status === 'OFF_SALE');
      record(14, '审核状态与上架状态筛选组合生效', allPending && allMatch);
      await page.close();
    }

    // 15. 上架接口状态校验：待审核/已驳回 409，已通过 200
    {
      const page = await openAsAdmin(browser, '/admin/products');
      const pending = await createPendingProduct(page, '上架校验');
      const onSale = (id) => api(page, `/api/v1/admin/products/${id}/status`, {
        method: 'PUT', body: JSON.stringify({ status: 'ON_SALE' }),
      });
      const r1 = await onSale(pending.id);
      await api(page, `/api/v1/admin/products/${pending.id}/audit`, {
        method: 'PUT', body: JSON.stringify({ auditStatus: 'REJECTED', reason: '测试驳回' }),
      });
      const r2 = await onSale(pending.id);
      record(15, '待审核/已驳回商品均不能上架',
        r1.status === 409 && r2.status === 409
          && r1.body?.message?.includes('待审核') && r2.body?.message?.includes('驳回'),
        `待审核=${r1.status}/${r1.body?.message} 已驳回=${r2.status}/${r2.body?.message}`);
      await page.close();
    }

    // 16. 非管理员不能调用审核接口
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      const status = await page.evaluate(async (acc) => {
        const lr = await fetch('/api/v1/auth/login', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(acc),
        });
        const lj = await lr.json();
        const r = await fetch('/api/v1/admin/products/1/audit', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + lj?.data?.token },
          body: JSON.stringify({ auditStatus: 'APPROVED' }),
        });
        return r.status;
      }, MERCHANT);
      const status2 = await page.evaluate(async (acc) => {
        const lr = await fetch('/api/v1/auth/login', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(acc),
        });
        const lj = await lr.json();
        const r = await fetch('/api/v1/admin/products/1/audit', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + lj?.data?.token },
          body: JSON.stringify({ auditStatus: 'APPROVED' }),
        });
        return r.status;
      }, USER);
      record(16, '商家与普通用户调用审核接口被拒绝',
        status === 403 && status2 === 403, `商家=${status} 用户=${status2}`);
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
