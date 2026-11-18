/**
 * PandaGo 首页分类展示优化 + 个人中心资料管理 —— 浏览器端验收验证
 * 使用 puppeteer-core 驱动本机 Chrome，真实访问 5173 前端 + 8080 后端。
 * 运行：node docs/profile_category_check.cjs
 *
 * 覆盖：分类卡片无子分类数量、统一高度/图标、hover 效果、点击跳转与子分类筛选；
 *       个人中心资料展示、编辑保存、取消还原、表单校验、越权/只读字段、响应式。
 */
const puppeteer = require('../frontend/node_modules/puppeteer-core');

const BASE = 'http://127.0.0.1:5173';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const USER = { username: 'user_demo', password: 'Panda@12345' };
const ORIGINAL = { nickname: '熊猫买家', phone: '' }; // user_demo 原始资料，测试后还原
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

async function loginToken(page, account) {
  await page.goto(BASE, { waitUntil: 'networkidle2' });
  return page.evaluate(async (acc) => {
    const r = await fetch('/api/v1/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: acc.username, password: acc.password }),
    });
    const j = await r.json();
    const token = j?.data?.token;
    if (token) localStorage.setItem('pandago_token', token);
    return { ok: r.status === 200 && !!token, roles: j?.data?.roles || [] };
  }, account);
}

/** 直接调用后端接口还原 user_demo 资料 */
async function restoreProfile(browser) {
  const page = await newPage(browser);
  await loginToken(page, USER);
  await page.evaluate(async (orig) => {
    const r = await fetch('/api/v1/users/me/profile', {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nickname: orig.nickname, phone: orig.phone || null }),
    });
    return r.status;
  }, ORIGINAL);
  await sleep(300); // 等待还原请求完成，避免 browser.close 时中断
  await page.closeCtx();
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
  try {
    // ===== 首页分类展示 =====
    // 1. 分类卡片不显示子分类数量文字
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      await sleep(1200);
      const info = await page.evaluate(() => {
        const cards = Array.from(document.querySelectorAll('.category-card'));
        return {
          count: cards.length,
          text: cards.map((c) => c.innerText).join(' | '),
          hasSubCount: cards.some((c) => /\d+\s*个子分类/.test(c.innerText)),
          names: cards.map((c) => c.querySelector('strong')?.textContent?.trim() || ''),
        };
      });
      record(1, '首页分类卡片不再显示子分类数量',
        info.count >= 6 && !info.hasSubCount && info.names.every((n) => n.length > 0),
        `卡片数=${info.count} 含数量文字=${info.hasSubCount}`);
      await page.closeCtx();
    }

    // 2. 分类卡片高度统一、图标尺寸统一、名称居中
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      await sleep(1000);
      const info = await page.evaluate(() => {
        const cards = Array.from(document.querySelectorAll('.category-card'));
        const heights = cards.map((c) => Math.round(c.getBoundingClientRect().height));
        const icons = cards.map((c) => {
          const el = c.querySelector('.category-icon');
          const r = el.getBoundingClientRect();
          return `${Math.round(r.width)}x${Math.round(r.height)}`;
        });
        const centered = cards.every((c) => getComputedStyle(c).textAlign === 'center'
          && getComputedStyle(c).justifyContent === 'center');
        return { heights, icons, centered };
      });
      const sameHeight = new Set(info.heights).size === 1;
      const sameIcon = new Set(info.icons).size === 1;
      record(2, '分类卡片高度统一、图标尺寸统一、名称居中',
        sameHeight && sameIcon && info.centered,
        `高度=${info.heights.join(',')} 图标=${[...new Set(info.icons)].join(',')} 居中=${info.centered}`);
      await page.closeCtx();
    }

    // 3. hover 提供轻微视觉效果
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      await sleep(900);
      const card = await page.$('.category-card');
      const before = await page.evaluate((el) => getComputedStyle(el).boxShadow, card);
      await card.hover();
      await sleep(400);
      const after = await page.evaluate((el) => getComputedStyle(el).boxShadow, card);
      record(3, '分类卡片 hover 有轻微视觉效果', before !== after,
        `hover前=${before.slice(0, 24)} hover后=${after.slice(0, 24)}`);
      await page.closeCtx();
    }

    // 4. 点击有子分类的分类 → 商品列表展示子分类 chip 与商品
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      await sleep(1000);
      await page.evaluate(() => document.querySelector('.category-card')?.click());
      await sleep(1300);
      const info = await page.evaluate(() => ({
        url: location.href,
        text: document.body.innerText,
        chips: document.querySelectorAll('.category-chip, .filter-chip, .chip').length,
      }));
      const ok = info.url.includes('/products?categoryId=')
        && (info.text.includes('商品') || info.text.includes('全部'))
        && info.chips >= 1;
      record(4, '点击分类跳转到商品列表并展示子分类筛选', ok,
        `URL含categoryId=${info.url.includes('categoryId=')} 筛选chip=${info.chips}`);
      await page.closeCtx();
    }

    // ===== 个人中心 =====
    // 5. 个人中心展示当前用户完整资料（卡片布局）
    {
      const page = await newPage(browser);
      const login = await loginToken(page, USER);
      await page.goto(BASE + '/profile', { waitUntil: 'networkidle2' });
      await sleep(1300);
      const info = await page.evaluate(() => ({
        text: document.body.innerText,
        hasDescriptions: !!document.querySelector('.el-descriptions'),
        hero: !!document.querySelector('.profile-hero'),
        infoList: document.querySelectorAll('.info-list li').length,
        logout: Array.from(document.querySelectorAll('button')).some((b) => b.textContent.includes('退出登录')),
        edit: Array.from(document.querySelectorAll('button')).some((b) => b.textContent.includes('编辑个人资料')),
      }));
      const ok = login.ok && info.hero && info.infoList >= 5 && !info.hasDescriptions
        && info.logout && info.edit && info.text.includes('user_demo') && info.text.includes('普通用户');
      record(5, '个人中心以卡片布局展示真实资料且无旧表格', ok,
        `信息项=${info.infoList} 旧表格=${info.hasDescriptions} 退出/编辑=${info.logout}/${info.edit}`);
      await page.closeCtx();
    }

    // 6. 编辑个人资料：自动填充 + 保存昵称与手机号成功 + 数据刷新
    {
      const page = await newPage(browser);
      await loginToken(page, USER);
      await page.goto(BASE + '/profile', { waitUntil: 'networkidle2' });
      await sleep(1200);
      const result = await page.evaluate(async () => {
        Array.from(document.querySelectorAll('button')).find((b) => b.textContent.includes('编辑个人资料'))?.click();
        await new Promise((r) => setTimeout(r, 400));
        const inputs = Array.from(document.querySelectorAll('.el-dialog .el-form-item'));
        const findInput = (label) => inputs.find((i) => i.textContent.includes(label))?.querySelector('input');
        const nickname = findInput('昵称');
        const phone = findInput('手机号');
        if (!nickname || !phone) return { ok: false, why: 'no-input' };
        const filledNickname = nickname.value;
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        setter.call(nickname, '验收临时昵称');
        nickname.dispatchEvent(new Event('input', { bubbles: true }));
        setter.call(phone, '13711112222');
        phone.dispatchEvent(new Event('input', { bubbles: true }));
        Array.from(document.querySelectorAll('.el-dialog button')).find((b) => b.textContent.trim() === '保存')?.click();
        await new Promise((r) => setTimeout(r, 1300));
        return { ok: true, filledNickname, text: document.body.innerText };
      });
      const ok = result.ok && result.text.includes('验收临时昵称') && result.text.includes('13711112222');
      record(6, '编辑资料保存成功并刷新显示', ok,
        `原名=${result.filledNickname} 新名可见=${result.text?.includes('验收临时昵称')} 手机可见=${result.text?.includes('13711112222')}`);
      await page.closeCtx();
    }

    // 7. 取消编辑不修改数据库
    {
      const page = await newPage(browser);
      await loginToken(page, USER);
      await page.goto(BASE + '/profile', { waitUntil: 'networkidle2' });
      await sleep(1200);
      const result = await page.evaluate(async () => {
        Array.from(document.querySelectorAll('button')).find((b) => b.textContent.includes('编辑个人资料'))?.click();
        await new Promise((r) => setTimeout(r, 350));
        const nickname = Array.from(document.querySelectorAll('.el-dialog .el-form-item'))
          .find((i) => i.textContent.includes('昵称'))?.querySelector('input');
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        setter.call(nickname, '不应保存的名字');
        nickname.dispatchEvent(new Event('input', { bubbles: true }));
        const dialog = Array.from(document.querySelectorAll('.el-dialog')).find((d) => d.offsetParent !== null);
        Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent.trim() === '取消')?.click();
        await new Promise((r) => setTimeout(r, 300));
        Array.from(document.querySelectorAll('.el-message-box button'))
          .find((b) => ['确定', 'OK', '确'].some((t) => b.textContent.includes(t)))?.click();
        await new Promise((r) => setTimeout(r, 500));
        return {
          text: document.body.innerText,
          editing: Array.from(document.querySelectorAll('.el-dialog'))
            .some((d) => d.offsetParent !== null && d.textContent.includes('编辑个人资料')),
        };
      });
      const ok = !result.editing && result.text.includes('验收临时昵称') && !result.text.includes('不应保存的名字');
      record(7, '取消编辑不修改数据、恢复查看态', ok,
        `仍在编辑=${result.editing} 未保存名字泄露=${result.text.includes('不应保存的名字')}`);
      await page.closeCtx();
    }

    // 8. 表单校验：空白昵称 / 非法手机号
    {
      const page = await newPage(browser);
      await loginToken(page, USER);
      await page.goto(BASE + '/profile', { waitUntil: 'networkidle2' });
      await sleep(1200);
      const info = await page.evaluate(async () => {
        Array.from(document.querySelectorAll('button')).find((b) => b.textContent.includes('编辑个人资料'))?.click();
        await new Promise((r) => setTimeout(r, 350));
        const items = Array.from(document.querySelectorAll('.el-dialog .el-form-item'));
        const nickname = items.find((i) => i.textContent.includes('昵称'))?.querySelector('input');
        const phone = items.find((i) => i.textContent.includes('手机号'))?.querySelector('input');
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        setter.call(nickname, '   ');
        nickname.dispatchEvent(new Event('input', { bubbles: true }));
        nickname.dispatchEvent(new Event('blur', { bubbles: true }));
        setter.call(phone, '12345');
        phone.dispatchEvent(new Event('input', { bubbles: true }));
        phone.dispatchEvent(new Event('blur', { bubbles: true }));
        await new Promise((r) => setTimeout(r, 500));
        return { errs: Array.from(document.querySelectorAll('.el-form-item__error')).map((e) => e.textContent.trim()).join(' ') };
      });
      record(8, '空白昵称与非法手机号被前端校验拦截',
        info.errs.includes('昵称') && info.errs.includes('手机号'), `错误=${info.errs}`);
      await page.closeCtx();
    }

    // 9. 只读字段：用户ID/用户名/角色不可编辑
    {
      const page = await newPage(browser);
      await loginToken(page, USER);
      await page.goto(BASE + '/profile', { waitUntil: 'networkidle2' });
      await sleep(1100);
      const info = await page.evaluate(async () => {
        Array.from(document.querySelectorAll('button')).find((b) => b.textContent.includes('编辑个人资料'))?.click();
        await new Promise((r) => setTimeout(r, 350));
        const items = Array.from(document.querySelectorAll('.el-dialog .el-form-item'));
        const disabledByLabel = (label) => {
          const item = items.find((i) => i.textContent.includes(label));
          const input = item?.querySelector('input');
          return input ? input.disabled : null;
        };
        return {
          userId: disabledByLabel('用户 ID'), username: disabledByLabel('用户名'), role: disabledByLabel('角色'),
          nickname: disabledByLabel('昵称'), phone: disabledByLabel('手机号'),
        };
      });
      record(9, '用户ID/用户名/角色只读，昵称与手机号可编辑',
        info.userId === true && info.username === true && info.role === true
        && info.nickname === false && info.phone === false,
        `只读=${info.userId}/${info.username}/${info.role} 可编辑=${info.nickname}/${info.phone}`);
      await page.closeCtx();
    }

    // 10. 普通用户无法通过接口修改角色/他人资料（后端越权校验）
    {
      const page = await newPage(browser);
      await loginToken(page, USER);
      const result = await page.evaluate(async () => {
        const token = localStorage.getItem('pandago_token');
        const evil = await fetch('/api/v1/users/me/profile', {
          method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
          body: JSON.stringify({ nickname: '合法昵称', phone: '', userId: 48, username: 'hacked', roles: ['ADMIN'], password: 'hacked' }),
        });
        const j = await evil.json();
        return { status: evil.status, userId: j?.data?.userId, username: j?.data?.username, roles: j?.data?.roles };
      });
      const ok = result.status === 200 && result.userId === 49 && result.username === 'user_demo'
        && Array.isArray(result.roles) && result.roles.includes('USER') && !result.roles.includes('ADMIN');
      record(10, '用户无法通过接口篡改 userId/用户名/角色', ok,
        `HTTP=${result.status} userId=${result.userId} username=${result.username} roles=${result.roles}`);
      await page.closeCtx();
    }

    // 11. 未登录访问个人中心被拦截/接口 401
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      const status = await page.evaluate(async () => {
        const r = await fetch('/api/v1/users/me/profile', {
          method: 'PUT', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ nickname: 'x', phone: '' }),
        });
        return r.status;
      });
      record(11, '未登录调用资料更新接口返回 401', status === 401, `HTTP=${status}`);
      await page.closeCtx();
    }

    // 12. 响应式：窄屏布局正常
    {
      const page = await newPage(browser);
      await page.setViewport({ width: 390, height: 844 });
      await loginToken(page, USER);
      await page.goto(BASE + '/profile', { waitUntil: 'networkidle2' });
      await sleep(1000);
      const info = await page.evaluate(() => ({
        hero: !!document.querySelector('.profile-hero'),
        entries: document.querySelectorAll('.entry').length,
        width: document.body.scrollWidth,
        viewport: window.innerWidth,
      }));
      record(12, '窄屏下个人中心布局正常', info.hero && info.entries >= 4 && info.width <= info.viewport + 8,
        `快捷入口=${info.entries} 宽度=${info.width}/${info.viewport}`);
      await page.closeCtx();
    }

    // 13. 登录/注册/退出功能未受影响
    {
      const page = await newPage(browser);
      const login = await loginToken(page, USER);
      await page.goto(BASE + '/profile', { waitUntil: 'networkidle2' });
      await sleep(900);
      const before = await page.evaluate(() => !!localStorage.getItem('pandago_token'));
      await page.evaluate(() => {
        Array.from(document.querySelectorAll('button')).find((b) => b.textContent.includes('退出登录'))?.click();
      });
      await sleep(300);
      await page.evaluate(() => {
        const ok = Array.from(document.querySelectorAll('button')).find((b) => ['确定', 'OK', '确'].some((t) => b.textContent.includes(t)));
        ok?.click();
      });
      await sleep(600);
      const after = await page.evaluate(() => !!localStorage.getItem('pandago_token'));
      record(13, '登录与退出登录功能正常', login.ok && before && !after,
        `登录=${login.ok} 退出前token=${before} 退出后token=${after}`);
      await page.closeCtx();
    }

  } finally {
    // 无论用例是否抛错，都还原 user_demo 资料，避免污染演示数据
    try { await restoreProfile(browser); } catch (e) { console.log('restore warning:', e.message); }
    await browser.close();
    const pass = results.filter((r) => r.ok).length;
    console.log(`\n===== 结果：${pass}/${results.length} 通过 =====`);
    if (pass !== results.length) process.exitCode = 1;
  }
})();
