/**
 * PandaGo 商家头像统一 —— 浏览器端验收验证
 * 使用 puppeteer-core 驱动本机 Chrome，真实访问 5173 前端 + 8080 后端。
 * 运行：node docs/merchant_avatar_check.cjs
 *
 * 覆盖：资料页与右上角一致、修改后立即更新、跨页面一致、刷新保持、
 *       未设置显示统一品牌默认头像、加载失败 fallback、用户端与管理端不受影响。
 */
const puppeteer = require('../frontend/node_modules/puppeteer-core');

const BASE = 'http://127.0.0.1:5173';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const MERCHANT = { username: 'merchant_demo', password: 'Panda@12345' };
const ADMIN = { username: 'admin_demo', password: 'Panda@12345' };
const USER = { username: 'user_demo', password: 'Panda@12345' };
const ORIGINAL_LOGO = 'https://pica.zhimg.com/v2-6e90c0c4ee62636699becc8ffd20ffee_720w.jpg?source=172ae18b';
const TEST_LOGO = '/images/panda-product.svg';
const BROKEN_LOGO = '/images/NO_SUCH_AVATAR_FOR_TEST.png';
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
    return r.status === 200 && !!j?.data?.token;
  }, account);
}

/** 通过后端接口直接设置店铺 logo（验收准备/还原用） */
async function setLogoByApi(page, logo) {
  return page.evaluate(async (value) => {
    const token = localStorage.getItem('pandago_token');
    const get = await fetch('/api/v1/merchant/profile', { headers: { Authorization: 'Bearer ' + token } });
    const p = (await get.json()).data;
    const r = await fetch('/api/v1/merchant/profile', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
      body: JSON.stringify({
        storeName: p.storeName, description: p.description, contactName: p.contactName,
        contactPhone: p.contactPhone, address: p.address, logo: value,
      }),
    });
    return (await r.json()).data?.logo || null;
  }, logo);
}

/** 打开商家页面，读取右上角头像与页面内头像（MerchantAvatar 组件渲染的 img） */
async function readMerchantAvatars(page, path) {
  await page.goto(BASE + path, { waitUntil: 'networkidle2' });
  await sleep(1100);
  return page.evaluate(() => {
    const headerImg = document.querySelector('.header-user .merchant-avatar');
    const allImgs = Array.from(document.querySelectorAll('.merchant-avatar'));
    return {
      headerSrc: headerImg?.getAttribute('src') || '',
      avatarCount: allImgs.length,
      srcs: allImgs.map((i) => i.getAttribute('src') || ''),
      headerText: document.querySelector('.header-user')?.textContent || '',
      hasBareBear: !!Array.from(document.querySelectorAll('.header-user .avatar')).find((el) => el.textContent.trim() === '熊'),
    };
  });
}

/** 商家资料页店铺头像 src */
async function readProfileAvatar(page) {
  await page.goto(BASE + '/merchant/profile', { waitUntil: 'networkidle2' });
  await sleep(1000);
  return page.evaluate(() => {
    const img = document.querySelector('.info-head .merchant-avatar');
    return {
      src: img?.getAttribute('src') || '',
      headerSrc: document.querySelector('.header-user .merchant-avatar')?.getAttribute('src') || '',
    };
  });
}

/** Vite 会把小 SVG 内联为 data URI，品牌默认头像可能表现为路径或 data:image/svg+xml */
function isBrandDefault(src) {
  return src.includes('pandago-mark') || src.startsWith('data:image/svg+xml')
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
  try {
    // 准备：商家登录，设置一个本地可正常加载的头像
    const page = await newPage(browser);
    const mOk = await login(page, MERCHANT);
    if (!mOk) throw new Error('商家登录失败');
    await setLogoByApi(page, TEST_LOGO);

    // 1. 资料页头像与右上角一致
    {
      const info = await readProfileAvatar(page);
      record(1, '商家资料页头像与右上角头像来源一致',
        !!info.src && info.src === info.headerSrc && info.src.includes('panda-product.svg'),
        `资料页=${info.src} 右上角=${info.headerSrc}`);
    }

    // 2. 在资料页通过 UI 修改头像后，右上角立即更新
    {
      await page.goto(BASE + '/merchant/profile', { waitUntil: 'networkidle2' });
      await sleep(900);
      const result = await page.evaluate(async () => {
        // 找到编辑按钮并进入编辑态
        const editBtn = Array.from(document.querySelectorAll('button')).find((b) => b.textContent.includes('编辑资料'));
        editBtn?.click();
        await new Promise((r) => setTimeout(r, 350));
        const logoInput = Array.from(document.querySelectorAll('.el-form-item'))
          .find((i) => i.textContent.includes('店铺 Logo'))?.querySelector('input');
        if (!logoInput) return { ok: false, why: 'no-input' };
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
        setter.call(logoInput, '/images/default-avatar.svg');
        logoInput.dispatchEvent(new Event('input', { bubbles: true }));
        const saveBtn = Array.from(document.querySelectorAll('button')).find((b) => b.textContent.trim() === '保存');
        saveBtn?.click();
        await new Promise((r) => setTimeout(r, 1300));
        return {
          ok: true,
          headerSrc: document.querySelector('.header-user .merchant-avatar')?.getAttribute('src') || '',
          profileSrc: document.querySelector('.info-head .merchant-avatar')?.getAttribute('src') || '',
        };
      });
      record(2, '资料页修改头像后右上角立即更新',
        result.ok && result.headerSrc.includes('default-avatar.svg') && result.headerSrc === result.profileSrc,
        `右上角=${result.headerSrc} 资料页=${result.profileSrc}`);
    }

    // 3. 进入商品/订单等页面头像一致
    {
      const paths = ['/merchant', '/merchant/products', '/merchant/orders', '/merchant/profile'];
      const srcs = [];
      for (const p of paths) {
        const info = await readMerchantAvatars(page, p);
        srcs.push(info.headerSrc);
      }
      record(3, '工作台/商品/订单/资料页右上角头像一致',
        srcs.every((s) => s.includes('default-avatar.svg')) && new Set(srcs).size === 1,
        `各页头像=${[...new Set(srcs)].join(' | ')}`);
    }

    // 4. 刷新后保持最新头像
    {
      const info = await readMerchantAvatars(page, '/merchant/dashboard'.replace('/dashboard', ''));
      await page.reload({ waitUntil: 'networkidle2' });
      await sleep(1000);
      const after = await page.evaluate(() => document.querySelector('.header-user .merchant-avatar')?.getAttribute('src') || '');
      record(4, '刷新页面后头像不恢复默认',
        after.includes('default-avatar.svg'), `刷新后=${after}`);
    }

    // 5. 未设置头像时各页面显示统一品牌默认头像
    {
      const cleared = await setLogoByApi(page, '');
      // 通过 API 改数据后强制刷新，让 merchant store 从后端重新拉取最新资料
      await page.reload({ waitUntil: 'networkidle2' })
      await sleep(800)
      const paths = ['/merchant', '/merchant/products', '/merchant/orders', '/merchant/profile'];
      const srcs = [];
      for (const p of paths) {
        const info = await readMerchantAvatars(page, p);
        srcs.push(info.headerSrc);
      }
      const profileInfo = await readProfileAvatar(page);
      const all = [...srcs, profileInfo.src];
      record(5, '未设置头像时各页面统一显示 PandaGo 品牌默认头像',
        cleared === null && all.every(isBrandDefault) && new Set(all).size === 1,
        `默认头像来源=${[...new Set(all)].join(' | ')}`);
    }

    // 6. 头像 URL 加载失败时统一 fallback
    {
      await setLogoByApi(page, BROKEN_LOGO);
      await page.reload({ waitUntil: 'networkidle2' })
      await sleep(800)
      const info = await readProfileAvatar(page);
      record(6, '头像 URL 加载失败时显示统一 fallback',
        isBrandDefault(info.headerSrc) && isBrandDefault(info.src),
        `右上角=${info.headerSrc.slice(0, 40)} 资料页=${info.src.slice(0, 40)}`);
    }

    // 7. 用户端与平台管理端头像不受影响
    {
      // 用户端：应无商家头像组件，仍是 user-layout 首字头像
      const userPage = await newPage(browser);
      await login(userPage, USER);
      await userPage.goto(BASE + '/', { waitUntil: 'networkidle2' });
      await sleep(800);
      const userLayout = await userPage.evaluate(() => ({
        merchantAvatar: document.querySelectorAll('.merchant-avatar').length,
        userAvatar: !!document.querySelector('.user-avatar'),
      }));

      // 平台管理端：右上角仍是「熊」字头像，不使用商家头像组件
      const adminPage = await newPage(browser);
      await login(adminPage, ADMIN);
      await adminPage.goto(BASE + '/admin', { waitUntil: 'networkidle2' });
      await sleep(1000);
      const adminLayout = await adminPage.evaluate(() => ({
        merchantAvatar: document.querySelectorAll('.merchant-avatar').length,
        bearAvatar: !!Array.from(document.querySelectorAll('.header-user .avatar')).find((el) => el.textContent.trim() === '熊'),
      }));

      record(7, '用户端与平台管理端头像不受影响',
        userLayout.userAvatar && userLayout.merchantAvatar === 0
        && adminLayout.bearAvatar && adminLayout.merchantAvatar === 0,
        `用户端商家头像组件=${userLayout.merchantAvatar} 管理端熊字头像=${adminLayout.bearAvatar}`);
      await userPage.closeCtx();
      await adminPage.closeCtx();
    }

  } finally {
    // 还原商家 logo 与演示账号资料
    try {
      const restorePage = await newPage(browser);
      await login(restorePage, MERCHANT);
      await setLogoByApi(restorePage, ORIGINAL_LOGO);
      await sleep(300);
      await restorePage.closeCtx();
    } catch (e) {
      console.log('restore warning:', e.message);
    }
    await browser.close();
    const pass = results.filter((r) => r.ok).length;
    console.log(`\n===== 结果：${pass}/${results.length} 通过 =====`);
    if (pass !== results.length) process.exitCode = 1;
  }
})();
