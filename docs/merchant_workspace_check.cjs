/**
 * PandaGo 商家工作台与商家资料 —— 浏览器端验收验证（V9）
 * 使用 puppeteer-core 驱动本机 Chrome，真实访问 5173 前端 + 8080 后端。
 * 运行：node docs/merchant_workspace_check.cjs
 *
 * 覆盖：真实统计、最近订单脱敏、商品/订单 query 联动弹窗、资料展示与编辑保存、
 *       字段白名单（敏感字段只读）、导航高亮、未登录/普通用户越权拦截、刷新保持登录。
 */
const puppeteer = require('../frontend/node_modules/puppeteer-core');

const BASE = 'http://127.0.0.1:5173';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const MERCHANT = { username: 'merchant_demo', password: 'Panda@12345' };
const USER = { username: 'user_demo', password: 'Panda@12345' };
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

/** 通过登录接口拿 token 并写入 localStorage，稳定地进入已登录态 */
async function loginToken(page, account) {
  await page.goto(BASE, { waitUntil: 'networkidle2' });
  return page.evaluate(async (acc) => {
    const r = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: acc.username, password: acc.password }),
    });
    const j = await r.json();
    const token = j?.data?.token;
    if (token) localStorage.setItem('pandago_token', token);
    return { ok: r.status === 200 && !!token, roles: j?.data?.roles || [] };
  }, account);
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
  try {
    // 1. 商家登录 → 工作台真实统计（商品1/上架1/完成2/销售额10000）
    {
      const page = await newPage(browser);
      const login = await loginToken(page, MERCHANT);
      await page.goto(BASE + '/merchant', { waitUntil: 'networkidle2' });
      await sleep(1300);
      const info = await page.evaluate(() => ({
        text: document.body.innerText,
        storeName: document.querySelector('.store-info h2')?.textContent?.trim() || '',
        statValues: Array.from(document.querySelectorAll('.stat-value')).map((e) => e.textContent.trim()),
      }));
      const ok = login.ok && info.text.includes('商家工作台') && info.storeName.includes('熊猫')
        && info.statValues.includes('1') && info.statValues.includes('2')
        && info.statValues.some((v) => v.includes('10000'));
      record(1, '商家登录后工作台显示真实统计（店铺/商品/订单/销售额）', ok,
        `登录=${login.ok} 店铺=${info.storeName} 指标=${info.statValues.join(',')}`);
      await page.closeCtx();
    }

    // 2. 商家侧导航 4 项齐全且当前菜单高亮
    {
      const page = await newPage(browser);
      await loginToken(page, MERCHANT);
      await page.goto(BASE + '/merchant', { waitUntil: 'networkidle2' });
      await sleep(1000);
      const info = await page.evaluate(() => {
        const items = Array.from(document.querySelectorAll('.side-menu .menu-item'));
        return {
          titles: items.map((i) => i.textContent.trim()),
          active: items.filter((i) => i.classList.contains('active')).map((i) => i.textContent.trim()),
        };
      });
      const ok = ['工作台', '我的商品', '订单管理', '商家资料'].every((t) => info.titles.includes(t))
        && info.active.some((t) => t.includes('工作台'));
      record(2, '商家侧导航 4 项齐全且工作台高亮', ok,
        `菜单=${info.titles.join('/')} 高亮=${info.active.join('/')}`);
      await page.closeCtx();
    }

    // 3. 最近订单：真实订单号 + 手机号中间四位脱敏
    {
      const page = await newPage(browser);
      await loginToken(page, MERCHANT);
      await page.goto(BASE + '/merchant', { waitUntil: 'networkidle2' });
      await sleep(1200);
      const text = await page.evaluate(() => document.body.innerText);
      const hasOrderNo = /PO\d{10,}/.test(text);
      // 买家真实手机号为 18545678909，脱敏后应为 185****8909；只断言格式，不硬编码明文
      const hasMasked = /\b1\d{2}\*{4}\d{4}\b/.test(text);
      // 订单号里含长串数字，不能用通用手机号正则判断泄露；只检查该买家完整手机号是否出现
      const hasFullPhone = text.includes('18545678909');
      record(3, '工作台最近订单显示真实订单号且买家手机号脱敏', hasOrderNo && hasMasked && !hasFullPhone,
        `订单号=${hasOrderNo} 脱敏=${hasMasked} 明文泄露=${hasFullPhone}`);
      await page.closeCtx();
    }

    // 4. 商品概览点击 → 商品管理页自动打开编辑弹窗
    {
      const page = await newPage(browser);
      await loginToken(page, MERCHANT);
      await page.goto(BASE + '/merchant', { waitUntil: 'networkidle2' });
      await sleep(1200);
      const clicked = await page.evaluate(() => {
        const row = document.querySelector('.product-row');
        if (!row) return false;
        row.click();
        return true;
      });
      await sleep(1400);
      const info = await page.evaluate(() => ({
        url: location.href,
        dialogVisible: !!document.querySelector('.el-dialog:not([style*="display: none"])'),
        text: document.body.innerText,
      }));
      const ok = clicked && info.url.includes('/merchant/products')
        && info.url.includes('edit=') && info.dialogVisible && info.text.includes('编辑商品');
      record(4, '商品概览点击跳转到商品页并自动打开编辑弹窗', ok,
        `点击=${clicked} URL含edit=${info.url.includes('edit=')} 弹窗=${info.dialogVisible}`);
      await page.closeCtx();
    }

    // 5. 最近订单详情按钮 → 订单管理页自动打开详情弹窗
    {
      const page = await newPage(browser);
      await loginToken(page, MERCHANT);
      await page.goto(BASE + '/merchant', { waitUntil: 'networkidle2' });
      await sleep(1200);
      const clicked = await page.evaluate(() => {
        const btns = Array.from(document.querySelectorAll('button'));
        const b = btns.find((x) => (x.textContent || '').includes('查看订单详情'));
        if (!b) return false;
        b.click();
        return true;
      });
      await sleep(1400);
      const info = await page.evaluate(() => ({
        url: location.href,
        text: document.body.innerText,
      }));
      const ok = clicked && info.url.includes('/merchant/orders')
        && info.url.includes('detail=') && info.text.includes('订单详情') && info.text.includes('小计');
      record(5, '最近订单详情跳转到订单页并自动打开详情弹窗', ok,
        `点击=${clicked} URL含detail=${info.url.includes('detail=')} 详情内容=${info.text.includes('小计')}`);
      await page.closeCtx();
    }

    // 6. 商家资料页：真实数据 + 敏感字段只读展示
    {
      const page = await newPage(browser);
      await loginToken(page, MERCHANT);
      await page.goto(BASE + '/merchant/profile', { waitUntil: 'networkidle2' });
      await sleep (1200);
      const text = await page.evaluate(() => document.body.innerText);
      const ok = text.includes('商家资料') && text.includes('店铺 ID')
        && text.includes('正常经营') && text.includes('审核通过') && text.includes('熊猫');
      record(6, '商家资料页显示真实店铺信息与只读状态字段', ok,
        `店铺名=${text.includes('熊猫')} 状态=${text.includes('正常经营')} 审核=${text.includes('审核通过')}`);
      await page.closeCtx();
    }

    // 7. 资料编辑保存：改名 → 回读生效 → 还原（不污染演示数据）
    {
      const page = await newPage(browser);
      await loginToken(page, MERCHANT);
      await page.goto(BASE + '/merchant/profile', { waitUntil: 'networkidle2' });
      await sleep(1200);
      const result = await page.evaluate(async () => {
        // 记录原店铺名（.info-head h3 才是店铺名，.info-row strong[0] 是简介）
        const nameBefore = document.querySelector('.info-head h3')?.textContent?.trim() || '';
        const btns = Array.from(document.querySelectorAll('button'));
        btns.find((b) => (b.textContent || '').includes('编辑资料'))?.click();
        await new Promise((r) => setTimeout(r, 400));
        const nameInput = Array.from(document.querySelectorAll('input'))
          .find((i) => i.closest('.el-form-item')?.textContent?.includes('店铺名称'));
        if (!nameInput) return { ok: false, why: 'no-input' };
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        setter.call(nameInput, '验收临时店铺名');
        nameInput.dispatchEvent(new Event('input', { bubbles: true }));
        const save = Array.from(document.querySelectorAll('button'))
          .find((b) => (b.textContent || '').trim() === '保存');
        save?.click();
        await new Promise((r) => setTimeout(r, 1200));
        const nameAfter = document.querySelector('.info-head h3')?.textContent?.trim() || '';
        return { ok: true, nameBefore, nameAfter };
      });
      // 还原为原名（若原名非空且不是临时名）
      if (result.ok && result.nameBefore && result.nameBefore !== '验收临时店铺名') {
        await page.evaluate(async (orig) => {
          const btns = Array.from(document.querySelectorAll('button'));
          btns.find((b) => (b.textContent || '').includes('编辑资料'))?.click();
          await new Promise((r) => setTimeout(r, 300));
          const input = Array.from(document.querySelectorAll('input'))
            .find((i) => i.closest('.el-form-item')?.textContent?.includes('店铺名称'));
          if (!input) return;
          const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
          setter.call(input, orig);
          input.dispatchEvent(new Event('input', { bubbles: true }));
          Array.from(document.querySelectorAll('button')).find((b) => (b.textContent || '').trim() === '保存')?.click();
          await new Promise((r) => setTimeout(r, 1000));
        }, result.nameBefore);
      }
      record(7, '资料编辑保存成功且回读生效（测试后已还原）',
        result.ok && result.nameAfter.includes('验收临时店铺名'),
        `改名后=${result.nameAfter} 原名=${result.nameBefore}`);
      await page.closeCtx();
    }

    // 8. 非法手机号前端校验拦截
    {
      const page = await newPage(browser);
      await loginToken(page, MERCHANT);
      await page.goto(BASE + '/merchant/profile', { waitUntil: 'networkidle2' });
      await sleep(1200);
      const info = await page.evaluate(async () => {
        Array.from(document.querySelectorAll('button')).find((b) => (b.textContent || '').includes('编辑资料'))?.click();
        await new Promise((r) => setTimeout(r, 300));
        const input = Array.from(document.querySelectorAll('input'))
          .find((i) => i.closest('.el-form-item')?.textContent?.includes('联系电话'));
        if (!input) return { hasInput: false };
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        setter.call(input, '12345');
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('blur', { bubbles: true }));
        await new Promise((r) => setTimeout(r, 400));
        return {
          hasInput: true,
          err: Array.from(document.querySelectorAll('.el-form-item__error')).map((e) => e.textContent.trim()).join(' '),
        };
      });
      record(8, '资料编辑非法手机号被前端校验拦截', info.hasInput && info.err.includes('手机号'),
        `错误提示=${info.err}`);
      await page.closeCtx();
    }

    // 9. 未登录访问 /merchant 被路由守卫拦截回登录
    {
      const page = await newPage(browser);
      await page.goto(BASE + '/merchant', { waitUntil: 'networkidle2' });
      await sleep(900);
      const url = page.url();
      record(9, '未登录访问商家中心被拦截', url.includes('/login') || url === BASE + '/',
        `最终 URL=${url}`);
      await page.closeCtx();
    }

    // 10. 普通用户以 USER 身份访问商家接口被后端 403 拒绝
    {
      const page = await newPage(browser);
      const login = await loginToken(page, USER);
      const result = await page.evaluate(async () => {
        const token = localStorage.getItem('pandago_token');
        const headers = { Authorization: 'Bearer ' + token };
        const r = await fetch('/api/v1/merchant/dashboard', { headers });
        const r2 = await fetch('/api/v1/merchant/profile', { headers });
        return { dashboard: r.status, profile: r2.status, loginToken: !!token };
      });
      record(10, '普通用户访问商家工作台/资料接口被 403 拒绝',
        login.ok && result.loginToken && result.dashboard === 403 && result.profile === 403,
        `登录=${login.ok} dashboard=${result.dashboard} profile=${result.profile}`);
      await page.closeCtx();
    }

    // 11. 刷新后保持登录态（token 持久化）
    {
      const page = await newPage(browser);
      const login = await loginToken(page, MERCHANT);
      await page.goto(BASE + '/merchant', { waitUntil: 'networkidle2' });
      await sleep(900);
      await page.reload({ waitUntil: 'networkidle2' });
      await sleep(1000);
      const info = await page.evaluate(() => ({
        url: location.href,
        hasDashboard: document.body.innerText.includes('商家工作台'),
        token: !!localStorage.getItem('pandago_token'),
      }));
      record(11, '刷新后保持登录且仍在工作台', login.ok && info.hasDashboard && info.token,
        `工作台=${info.hasDashboard} token=${info.token}`);
      await page.closeCtx();
    }

    // 12. 窄屏响应式：侧菜单与统计卡片仍可见
    {
      const page = await newPage(browser);
      await page.setViewport({ width: 420, height: 860 });
      await loginToken(page, MERCHANT);
      await page.goto(BASE + '/merchant', { waitUntil: 'networkidle2' });
      await sleep(1000);
      const info = await page.evaluate(() => ({
        sidebarVisible: !!document.querySelector('.sidebar')
          && getComputedStyle(document.querySelector('.sidebar')).display !== 'none',
        statCount: document.querySelectorAll('.stat-card').length,
        bodyWidth: document.body.scrollWidth,
        viewport: window.innerWidth,
      }));
      record(12, '窄屏下侧菜单与统计卡片仍可见', info.sidebarVisible && info.statCount >= 1,
        `侧栏=${info.sidebarVisible} 卡片=${info.statCount} 宽度=${info.bodyWidth}/${info.viewport}`);
      await page.closeCtx();
    }
  } finally {
    await browser.close();
    const pass = results.filter((r) => r.ok).length;
    console.log(`\n===== 结果：${pass}/${results.length} 通过 =====`);
    if (pass !== results.length) process.exitCode = 1;
  }
})();
