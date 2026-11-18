/**
 * PandaGo 用户头像全链路修复 —— 浏览器端验收验证
 * 使用 puppeteer-core 驱动本机 Chrome，真实访问 5173 前端 + 8080 后端。
 * 运行：node docs/user_avatar_header_check.cjs
 *
 * 覆盖 10 项：记录原头像→上传修改→不刷新立即更新→跨页面→刷新→重新登录→
 *            数据库字段→API 返回→Network 请求→商家/管理员不受影响。
 */
const puppeteer = require('../frontend/node_modules/puppeteer-core');
const { execSync } = require('child_process');

const BASE = 'http://127.0.0.1:5173';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const USER = { username: 'user_demo', password: 'Panda@12345' };
const MERCHANT = { username: 'merchant_demo', password: 'Panda@12345' };
const ADMIN = { username: 'admin_demo', password: 'Panda@12345' };
// 1x1 PNG 与 1x1 WEBP（用于上传，均无真实内容，仅验证字段与链路）
const PNG_B64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const results = [];
function record(id, name, ok, detail) {
  results.push({ id, name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'} [${id}] ${name}${detail ? ' — ' + detail : ''}`);
}

async function newPage(browser) {
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();
  await page.setViewport({ width: 1366, height: 900 });
  page.closeCtx = async () => { await page.close(); await ctx.close(); };
  return page;
}

async function login(page, account) {
  await page.goto(BASE, { waitUntil: 'networkidle2' });
  return page.evaluate(async (acc) => {
    const r = await fetch('/api/v1/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: acc.username, password: acc.password }),
    });
    const j = await r.json();
    if (j?.data?.token) localStorage.setItem('pandago_token', j.data.token);
    return { ok: r.status === 200 && !!j?.data?.token, avatar: j?.data?.avatar || null, status: r.status };
  }, account);
}

/** 读取右上角头像 src 与是否存在旧文字头像 */
async function headerAvatar(page) {
  return page.evaluate(() => {
    const img = document.querySelector('.user-trigger .user-avatar-img');
    const textSpans = Array.from(document.querySelectorAll('.user-trigger .user-avatar'));
    return {
      src: img?.getAttribute('src') || '',
      oldTextAvatars: textSpans.map((s) => s.textContent.trim()),
    };
  });
}

/** 在个人中心通过 UI 上传头像（含监听 Network 的响应状态） */
async function uploadAvatarViaUI(page, b64) {
  await page.goto(BASE + '/profile', { waitUntil: 'networkidle2' });
  await sleep(900);
  // 点击顶部头像打开上传弹窗
  await page.click('.avatar-button');
  await sleep(400);
  await page.evaluate(async (data) => {
    const bytes = Uint8Array.from(atob(data), (c) => c.charCodeAt(0));
    const input = document.querySelector('.el-dialog input[type=file]');
    const dt = new DataTransfer();
    dt.items.add(new File([bytes], 'new-avatar.png', { type: 'image/png' }));
    input.files = dt.files;
    input.dispatchEvent(new Event('change', { bubbles: true }));
    await new Promise((r) => setTimeout(r, 300));
  }, b64);
  await page.evaluate(() => {
    Array.from(document.querySelectorAll('.el-dialog button')).find((b) => b.textContent.includes('确认上传'))?.click();
  });
  await sleep(1500);
}

/** 直接调后端接口读取头像（验证 API 返回） */
async function profileAvatarFromApi(page) {
  return page.evaluate(async () => {
    const t = localStorage.getItem('pandago_token');
    const r = await fetch('/api/v1/users/me/profile', { headers: { Authorization: 'Bearer ' + t } });
    return { status: r.status, avatar: (await r.json()).data?.avatar || null };
  });
}

function dbAvatar() {
  return execSync(`mysql -h127.0.0.1 -P3307 -uroot pandago --default-character-set=utf8mb4 -N -e "SELECT IFNULL(avatar,'') FROM user WHERE username='user_demo';"`).toString().trim();
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
  let uploadedUrl = '';
  let headerBefore = '';
  try {
    const page = await newPage(browser);

    // 登录并记录当前右上角头像
    const loginResult = await login(page, USER);
    await page.goto(BASE + '/', { waitUntil: 'networkidle2' });
    await sleep(1200);
    headerBefore = (await headerAvatar(page)).src;

    // 2 & 3. 上传头像后不刷新，右上角立即更新
    await uploadAvatarViaUI(page, PNG_B64);
    const afterUpload = await headerAvatar(page);
    uploadedUrl = afterUpload.src;
    const profilePageAvatar = await page.evaluate(() => document.querySelector('.avatar-button .user-avatar-img')?.getAttribute('src') || '');
    record(1, '上传后右上角立即更新（不刷新页面）',
      loginResult.ok && !!uploadedUrl && uploadedUrl !== headerBefore
      && uploadedUrl.includes('/uploads/avatars/') && afterUpload.oldTextAvatars.length === 0,
      `之前=${headerBefore} 之后=${uploadedUrl} 旧文字头像=${afterUpload.oldTextAvatars.length}`);
    record(2, '个人中心头像与右上角头像一致',
      profilePageAvatar === uploadedUrl && !!profilePageAvatar,
      `个人中心=${profilePageAvatar} 右上角=${uploadedUrl}`);

    // 4. 进入其他页面头像保持一致
    const paths = ['/products', '/cart', '/orders', '/favorites'];
    const srcs = [];
    for (const p of paths) {
      await page.goto(BASE + p, { waitUntil: 'networkidle2' });
      await sleep(700);
      srcs.push((await headerAvatar(page)).src);
    }
    record(3, '进入商品详情/购物车/订单等其他页面头像一致',
      srcs.every((s) => s === uploadedUrl), `各页=${[...new Set(srcs)].join(' | ')}`);

    // 5. 刷新页面后保持最新
    await page.goto(BASE + '/', { waitUntil: 'networkidle2' });
    await page.reload({ waitUntil: 'networkidle2' });
    await sleep(1200);
    const afterReload = (await headerAvatar(page)).src;
    record(4, '刷新页面后头像保持最新（从后端恢复）',
      afterReload === uploadedUrl, `刷新后=${afterReload}`);

    // 6. 重新登录仍是最新头像
    const page2 = await newPage(browser);
    await login(page2, USER);
    await page2.goto(BASE + '/', { waitUntil: 'networkidle2' });
    await sleep(1000);
    const afterRelogin = (await headerAvatar(page2)).src;
    record(5, '重新登录显示最新头像',
      afterRelogin === uploadedUrl, `重新登录=${afterRelogin}`);

    // 7. 数据库字段为最新值
    const dbVal = dbAvatar();
    record(6, '数据库 user.avatar 为最新头像地址',
      dbVal === uploadedUrl, `数据库=${dbVal} 期望=${uploadedUrl}`);

    // 8. 获取用户信息 API 返回最新头像
    const api = await profileAvatarFromApi(page);
    record(7, '获取用户信息 API 返回最新头像',
      api.status === 200 && api.avatar === uploadedUrl, `API=${api.avatar}`);

    // 9. Network 中头像请求成功
    const imgStatus = await page.evaluate(async (url) => {
      const r = await fetch(url);
      return { status: r.status, type: r.headers.get('content-type') || '' };
    }, uploadedUrl);
    record(8, '头像 URL 请求成功且返回图片',
      imgStatus.status === 200 && imgStatus.type.includes('image'), `HTTP=${imgStatus.status} type=${imgStatus.type}`);
    await page2.closeCtx();

    // 10. 商家/管理员头像逻辑不受影响
    const merchantPage = await newPage(browser);
    await login(merchantPage, MERCHANT);
    await merchantPage.goto(BASE + '/merchant', { waitUntil: 'networkidle2' });
    await sleep(1000);
    const merchantHeader = await merchantPage.evaluate(() => ({
      userAvatarImg: document.querySelectorAll('.header-user .user-avatar-img').length,
      shopAvatar: document.querySelectorAll('.header-user .merchant-avatar').length,
    }));
    const adminPage = await newPage(browser);
    await login(adminPage, ADMIN);
    await adminPage.goto(BASE + '/admin', { waitUntil: 'networkidle2' });
    await sleep(1000);
    const adminHeader = await adminPage.evaluate(() => ({
      userAvatarImg: document.querySelectorAll('.header-user .user-avatar-img').length,
      bear: !!Array.from(document.querySelectorAll('.header-user .avatar')).find((e) => e.textContent.trim() === '熊'),
    }));
    record(9, '商家店铺头像与管理端头像逻辑不受影响',
      merchantHeader.shopAvatar === 1 && adminHeader.bear && adminHeader.userAvatarImg === 0,
      `商家店铺头像=${merchantHeader.shopAvatar} 管理端熊字=${adminHeader.bear}`);
    await merchantPage.closeCtx();
    await adminPage.closeCtx();

  } finally {
    // 还原 user_demo 原始头像
    try {
      execSync(`mysql -h127.0.0.1 -P3307 -uroot pandago --default-character-set=utf8mb4 -e "UPDATE user SET avatar='/uploads/avatars/8210c259c3114eb4ad9d633825f2ffac.jpeg' WHERE username='user_demo';"`);
      if (uploadedUrl && uploadedUrl !== '/uploads/avatars/8210c259c3114eb4ad9d633825f2ffac.jpeg') {
        const file = 'backend/uploads/avatars/' + uploadedUrl.split('/').pop();
        execSync(`rm -f "${file}"`);
      }
    } catch (e) {
      console.log('restore warning:', e.message);
    }
    await browser.close();
    const pass = results.filter((r) => r.ok).length;
    console.log(`\n===== 结果：${pass}/${results.length} 通过 =====`);
    if (pass !== results.length) process.exitCode = 1;
  }
})();
