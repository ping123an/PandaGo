/**
 * PandaGo 首页与身份登录系统优化 —— 浏览器端 14 项验收验证
 * 使用 puppeteer-core 驱动本机 Chrome，真实访问 5173 前端。
 * 运行：node docs/ui_acceptance_check.cjs
 */
const puppeteer = require('../frontend/node_modules/puppeteer-core');

const BASE = 'http://127.0.0.1:5173';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const ACCOUNTS = {
  user: { username: 'user_demo', password: 'Panda@12345' },
  merchant: { username: 'merchant_demo', password: 'Panda@12345' },
  admin: { username: 'admin_demo', password: 'Panda@12345' },
};

const results = [];
function record(id, name, ok, detail) {
  results.push({ id, name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} [${id}] ${name}${detail ? ' — ' + detail : ''}`);
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * 每个用例使用独立的无痕上下文，避免 localStorage（token）在用例间串扰。
 * 返回的 page 关闭时一并关闭其 context。
 */
async function newPage(browser) {
  const context = await browser.createBrowserContext();
  const page = await context.newPage();
  await page.setViewport({ width: 1280, height: 900 });
  page.__ctx = context;
  const origClose = page.close.bind(page);
  page.close = async () => { await origClose(); await context.close(); };
  return page;
}

async function readToken(page) {
  return page.evaluate(() => localStorage.getItem('pandago_token'));
}

/** 直接在身份登录页填表提交（稳定路径） */
async function submitLogin(page, username, password) {
  const userInput = await page.$('input[placeholder*="用户"]');
  const passInput = await page.$('input[type="password"]');
  if (!userInput || !passInput) return false;
  await userInput.type(username);
  await passInput.type(password);
  await page.evaluate(() => {
    const btn = Array.from(document.querySelectorAll('button')).find(
      (b) => (b.textContent || '').trim() === '登录');
    if (btn) btn.click();
  });
  await sleep(1500);
  return true;
}

/** 登录并断言跳转，返回 { url, token } */
async function loginAs(browser, identity, account) {
  const page = await newPage(browser);
  await page.goto(`${BASE}/login/${identity}`, { waitUntil: 'networkidle2' });
  await submitLogin(page, account.username, account.password);
  return { page, url: page.url(), token: await readToken(page) };
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
  try {
    // 1. 未登录可访问首页，且首页无调试信息、无后台入口、两行导航齐全
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      const text = await page.evaluate(() => document.body.innerText);
      const healthHidden = !text.includes('/api/v1/health') && !text.includes('后端服务已连接') && !text.includes('8080 端口');
      const noAdminEntry = !text.includes('查看管理端') && !text.includes('商家中心') && !text.includes('平台管理');
      const navOk = ['全部商品分类', '首页', '手机数码', '家用电器', '服装鞋包', '食品生鲜', '家居生活', '美妆护肤']
        .every((n) => text.includes(n));
      const guestLinks = text.includes('登录') && text.includes('注册');
      record(1, '未登录访问首页 + 两行导航 + 无调试信息/后台入口',
        healthHidden && noAdminEntry && navOk && guestLinks,
        `health隐藏=${healthHidden} 无后台入口=${noAdminEntry} 导航齐全=${navOk} 登录注册=${guestLinks}`);
      await page.close();
    }

    // 2. 首页有热门商品与推荐商品区，商品卡片含价格与购买入口
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      await sleep(800);
      const text = await page.evaluate(() => document.body.innerText);
      const sections = text.includes('热门推荐') && text.includes('为你推荐');
      const buyHint = text.includes('去购买');
      const price = /¥\s*\d/.test(text);
      record(2, '首页热门/推荐商品区 + 卡片价格与购买入口', sections && buyHint && price,
        `双商品区=${sections} 购买入口=${buyHint} 价格=${price}`);
      await page.close();
    }

    // 3. 身份选择页标题正确，三张卡片齐全，点击卡片能跳到对应登录页
    {
      const page = await newPage(browser);
      await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2' });
      const text = await page.evaluate(() => document.body.innerText);
      const title = text.includes('欢迎来到 PandaGo') && text.includes('请选择登录身份');
      const cards = text.includes('普通用户') && text.includes('商家') && text.includes('平台管理员');
      // 点击“普通用户”卡片跳转
      const jumped = await page.evaluate(() => {
        const el = Array.from(document.querySelectorAll('*')).find(
          (e) => e.children.length === 0 && (e.textContent || '').trim() === '普通用户');
        if (el) { el.click(); return true; }
        return false;
      });
      await sleep(600);
      const url = page.url();
      const cardNav = jumped && url.includes('/login/user');
      record(3, '身份选择页标题/三卡片 + 点击卡片跳转', title && cards && cardNav,
        `标题=${title} 三卡片=${cards} 点击跳转=${cardNav}(${url})`);
      await page.close();
    }

    // 4. 注册只获得 USER 角色（不可自我提权）
    {
      const page = await newPage(browser);
      const uname = 'ui_check_' + Date.now();
      await page.goto(`${BASE}/register`, { waitUntil: 'networkidle2' });
      const resp = await page.evaluate(async ({ u }) => {
        const r = await fetch('/api/v1/auth/register', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: u, password: 'Panda@12345', confirmPassword: 'Panda@12345', roleCode: 'ADMIN' }),
        });
        return r.json();
      }, { u: uname });
      const loginResp = await page.evaluate(async ({ u }) => {
        const r = await fetch('/api/v1/auth/login', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: u, password: 'Panda@12345' }),
        });
        return r.json();
      }, { u: uname });
      const roles = loginResp?.data?.roles || [];
      record(4, '注册只获得 USER（不可自我提权为 ADMIN）',
        resp?.code === 200 && roles.length === 1 && roles[0] === 'USER',
        `注册code=${resp?.code} 角色=${JSON.stringify(roles)}`);
      await page.close();
    }

    // 5. USER 登录进入用户端（跳首页，token 写入，无后台入口）
    {
      const { page, url, token } = await loginAs(browser, 'user', ACCOUNTS.user);
      const text = await page.evaluate(() => document.body.innerText);
      const noBackend = !text.includes('商家中心') && !text.includes('平台管理');
      const atHome = url === `${BASE}/` || url === BASE;
      record(5, 'USER 登录进入用户端且无后台入口', !!token && atHome && noBackend,
        `url=${url} token=${!!token} 无后台入口=${noBackend}`);
      await page.close();
    }

    // 6. USER 冒用商家身份登录被拒绝（明确错误提示，无 token，停留登录页）
    {
      const page = await newPage(browser);
      await page.goto(`${BASE}/login/merchant`, { waitUntil: 'networkidle2' });
      const submitted = await submitLogin(page, ACCOUNTS.user.username, ACCOUNTS.user.password);
      const text = await page.evaluate(() => document.body.innerText);
      const token = await readToken(page);
      const url = page.url();
      const rejected = submitted && !token && url.includes('/login/merchant')
        && (text.includes('无法以此身份登录') || text.includes('没有「商家」身份'));
      record(6, 'USER 冒用商家身份登录被拒绝', rejected,
        `无token=${!token} 停留登录页=${url.includes('login')} 有错误提示=${text.includes('无法以此身份')}`);
      await page.close();
    }

    // 7. USER 冒用管理员身份登录被拒绝
    {
      const page = await newPage(browser);
      await page.goto(`${BASE}/login/admin`, { waitUntil: 'networkidle2' });
      const submitted = await submitLogin(page, ACCOUNTS.user.username, ACCOUNTS.user.password);
      const text = await page.evaluate(() => document.body.innerText);
      const token = await readToken(page);
      const url = page.url();
      const rejected = submitted && !token && url.includes('/login/admin')
        && (text.includes('无法以此身份登录') || text.includes('没有「管理员」身份'));
      record(7, 'USER 冒用管理员身份登录被拒绝', rejected,
        `无token=${!token} 停留登录页=${url.includes('login')}`);
      await page.close();
    }

    // 8. 商家登录进入商家工作台
    {
      const { page, url, token } = await loginAs(browser, 'merchant', ACCOUNTS.merchant);
      record(8, '商家登录进入商家工作台', !!token && url.includes('/merchant'), `url=${url} token=${!!token}`);
      await page.close();
    }

    // 9. 管理员登录进入平台管理后台
    {
      const { page, url, token } = await loginAs(browser, 'admin', ACCOUNTS.admin);
      record(9, '管理员登录进入平台管理后台', !!token && url.includes('/admin'), `url=${url} token=${!!token}`);
      await page.close();
    }

    // 10. 未登录访问后台 URL 被路由守卫拦截
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      await page.evaluate(() => localStorage.clear());
      await page.goto(`${BASE}/admin`, { waitUntil: 'networkidle2' });
      await sleep(600);
      const url = page.url();
      const blocked = url.includes('/login') || url === `${BASE}/` || url === BASE;
      record(10, '未登录直接访问后台 URL 被拦截', blocked, `跳转后 url=${url}`);
      await page.close();
    }

    // 11. 未登录不能访问后台接口
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      const status = await page.evaluate(async () => {
        const r = await fetch('/api/v1/admin/products?pageNum=1&pageSize=5');
        return r.status;
      });
      record(11, '未登录访问后台接口被拒绝', status === 401 || status === 403, `HTTP ${status}`);
      await page.close();
    }

    // 12. 商家不能访问管理员接口
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      const status = await page.evaluate(async ({ username, password }) => {
        const lr = await fetch('/api/v1/auth/login', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password }),
        });
        const lj = await lr.json();
        if (!lj?.data?.token) return -1;
        const r = await fetch('/api/v1/admin/products?pageNum=1&pageSize=5', {
          headers: { Authorization: 'Bearer ' + lj.data.token },
        });
        return r.status;
      }, ACCOUNTS.merchant);
      record(12, '商家访问管理员接口被拒绝', status === 403 || status === 401, `HTTP ${status}`);
      await page.close();
    }

    // 13. 退出清理 Token
    {
      const { page } = await loginAs(browser, 'user', ACCOUNTS.user);
      const before = await readToken(page);
      await page.evaluate(() => {
        const btn = Array.from(document.querySelectorAll('button')).find((b) => (b.textContent || '').trim() === '退出');
        if (btn) btn.click();
      });
      await sleep(300);
      // Element Plus 中英文环境下确认按钮可能是“确定”或“OK”
      await page.evaluate(() => {
        const btn = Array.from(document.querySelectorAll('button')).find((b) => {
          const t = (b.textContent || '').trim();
          return t === '确定' || t === 'OK' || t.includes('确');
        });
        if (btn) btn.click();
      });
      await sleep(800);
      const after = await readToken(page);
      record(13, '退出登录清理 Token', !!before && !after, `退出前=${!!before} 退出后=${!!after}`);
      await page.close();
    }

    // 14. 刷新后登录状态保持
    {
      const { page } = await loginAs(browser, 'user', ACCOUNTS.user);
      const before = await readToken(page);
      await page.reload({ waitUntil: 'networkidle2' });
      await sleep(500);
      const after = await readToken(page);
      const text = await page.evaluate(() => document.body.innerText);
      const stillLoggedIn = text.includes('个人中心') || text.includes('退出');
      record(14, '刷新后登录状态保持', !!before && before === after && stillLoggedIn,
        `token保持=${before === after} 仍登录=${stillLoggedIn}`);
      await page.close();
    }
  } finally {
    await browser.close();
    const pass = results.filter((r) => r.ok).length;
    console.log(`\n===== 结果：${pass}/${results.length} 通过 =====`);
    if (pass !== results.length) process.exitCode = 1;
  }
})().catch((e) => { console.error('脚本异常：', e); process.exitCode = 2; });
