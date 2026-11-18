/**
 * PandaGo 商家手机号联动 + 店铺头像上传 —— 浏览器端验收
 * 运行：node docs/merchant_phone_logo_check.cjs
 * 前提：前端 5173、后端 8080 均在运行；使用 puppeteer-core 驱动本机 Chrome。
 *
 * 覆盖 12 项：个人中心手机号同步、商家资料手机号同步、平台管理两列一致、
 * 头像上传后立即生效、跨页面一致、刷新保持、重新登录保持、非法格式拦截、
 * 普通用户/平台管理端不受影响、URL 输入框已移除、数据还原。
 */
const puppeteer = require('../frontend/node_modules/puppeteer-core');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const BASE = 'http://127.0.0.1:5173';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const MERCHANT = { username: 'merchant_demo', password: 'Panda@12345' };
const ADMIN = { username: 'admin_demo', password: 'Panda@12345' };
const USER = { username: 'user_demo', password: 'Panda@12345' };
const TEST_PHONE_A = '13822223333';
const TEST_PHONE_B = '13744445555';
const TMP_DIR = path.join(__dirname, '.tmp-phone-logo');
const PNG_FILE = path.join(TMP_DIR, 'store-logo.png');
const TXT_FILE = path.join(TMP_DIR, 'fake-logo.txt');
const BACKEND_AVATAR_DIR = path.join(__dirname, '..', 'backend', 'uploads', 'avatars');
const PNG_B64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO6fU9kAAAAASUVORK5CYII=';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
let uploadedUrl = '';
let finished = false;
let original = null;

function record(id, name, ok, detail) {
  results.push({ id, name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'} [${id}] ${name}${detail ? ' — ' + detail : ''}`);
}

function mysql(sql) {
  return execFileSync('mysql', [
    '-h', '127.0.0.1', '-P', '3307', '-uroot', '--default-character-set=utf8mb4',
    '-N', '-B', '--raw', '-e', sql, 'pandago',
  ], { encoding: 'utf8' }).trim();
}

async function newPage(browser) {
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();
  await page.setViewport({ width: 1440, height: 960 });
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

async function apiGet(page, url) {
  return page.evaluate(async (u) => {
    const token = localStorage.getItem('pandago_token');
    const r = await fetch(u, { headers: { Authorization: 'Bearer ' + token } });
    return { status: r.status, body: await r.json() };
  }, url);
}

async function merchantState(page) {
  const me = await apiGet(page, '/api/v1/auth/me');
  const store = await apiGet(page, '/api/v1/merchant/profile');
  return {
    userPhone: me.body?.data?.phone || null,
    storePhone: store.body?.data?.contactPhone || null,
    logo: store.body?.data?.logo || null,
  };
}

async function clickByText(page, text, selector = 'button') {
  const handles = await page.$$(selector);
  for (const handle of handles) {
    const value = await page.evaluate((el) => el.textContent || '', handle);
    if (value.includes(text)) {
      await handle.click();
      return true;
    }
  }
  return false;
}

async function setInput(page, selector, value) {
  await page.waitForSelector(selector);
  // Element Plus 的 el-input 受 Vue v-model 控制，逐字 keyboard.type 在部分场景会被输入法/最大长度吞掉。
  // 走原生 value setter + input 事件，确保 Vue 收到完整的最终值。
  await page.evaluate((sel, val) => {
    const el = document.querySelector(sel);
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
    setter.call(el, val);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }, selector, value);
}

async function headerLogoSrc(page) {
  return page.evaluate(() => document.querySelector('.header-user .merchant-avatar')?.getAttribute('src') || '');
}

async function pageLogoSrcs(page) {
  return page.evaluate(() => Array.from(document.querySelectorAll('.merchant-avatar'))
    .map((el) => el.getAttribute('src') || ''));
}

async function waitForState(page, predicate, timeoutMs = 6000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const state = await merchantState(page);
    if (predicate(state)) return state;
    await sleep(200);
  }
  return merchantState(page);
}

(async () => {
  fs.mkdirSync(TMP_DIR, { recursive: true });
  fs.writeFileSync(PNG_FILE, Buffer.from(PNG_B64, 'base64'));
  fs.writeFileSync(TXT_FILE, 'not an image');

  const userPhone = mysql("SELECT IFNULL(phone,'') FROM user WHERE username='merchant_demo'");
  const storeRow = mysql("SELECT CONCAT(IFNULL(contact_phone,''), CHAR(9), IFNULL(logo,'')) "
    + "FROM merchant_profile WHERE user_id=(SELECT id FROM user WHERE username='merchant_demo')");
  const [storePhone = '', storeLogo = ''] = storeRow.split('\t');
  original = { userPhone, storePhone, logo: storeLogo };

  const browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox'] });
  try {
    const merchantPage = await newPage(browser);
    const loggedIn = await login(merchantPage, MERCHANT);
    record(1, '商家账号登录成功', loggedIn);
    if (!loggedIn) return finish(browser);

    // 2. 旧 Logo URL 输入框应已移除
    await merchantPage.goto(BASE + '/merchant/profile', { waitUntil: 'networkidle2' });
    await sleep(800);
    await clickByText(merchantPage, '编辑资料');
    await sleep(400);
    const ui = await merchantPage.evaluate(() => ({
      hasUrlInput: !!document.querySelector('input[placeholder="Logo 图片地址（可选）"]'),
      hasFileInput: !!document.querySelector('input[type="file"][accept*="image/png"]'),
      hasConfirmButton: Array.from(document.querySelectorAll('button')).some((b) => b.textContent.includes('确认上传')),
    }));
    record(2, '店铺头像改为上传控件，URL 输入框已移除', !ui.hasUrlInput && ui.hasFileInput && ui.hasConfirmButton);
    await clickByText(merchantPage, '取消');
    await sleep(300);

    // 3. 个人中心修改手机号 → 店铺联系电话同步
    await merchantPage.goto(BASE + '/profile', { waitUntil: 'networkidle2' });
    await sleep(700);
    await clickByText(merchantPage, '编辑个人资料');
    await sleep(400);
    await setInput(merchantPage, 'input[placeholder="手机号（可留空）"]', TEST_PHONE_A);
    await clickByText(merchantPage, '保存');
    const afterUserEdit = await waitForState(merchantPage, (s) => s.userPhone === TEST_PHONE_A && s.storePhone === TEST_PHONE_A);
    record(3, '个人中心改手机号后店铺联系电话同步', afterUserEdit.userPhone === TEST_PHONE_A && afterUserEdit.storePhone === TEST_PHONE_A,
      `user.phone=${afterUserEdit.userPhone}, contact_phone=${afterUserEdit.storePhone}`);
    await sleep(400);

    // 4. 进入商家资料页，页面显示同步后的手机号
    await merchantPage.goto(BASE + '/merchant/profile', { waitUntil: 'networkidle2' });
    await sleep(900);
    const profilePhoneVisible = await merchantPage.evaluate(() => document.body.innerText.includes('13822223333'));
    record(4, '商家资料页显示同步后的手机号', profilePhoneVisible);

    // 5. 商家资料修改手机号 → 个人资料手机号同步
    await clickByText(merchantPage, '编辑资料');
    await sleep(400);
    await setInput(merchantPage, 'input[placeholder="11 位手机号，可留空"]', TEST_PHONE_B);
    await clickByText(merchantPage, '保存');
    const afterStoreEdit = await waitForState(merchantPage, (s) => s.userPhone === TEST_PHONE_B && s.storePhone === TEST_PHONE_B);
    record(5, '商家资料改手机号后用户手机号同步', afterStoreEdit.userPhone === TEST_PHONE_B && afterStoreEdit.storePhone === TEST_PHONE_B,
      `user.phone=${afterStoreEdit.userPhone}, contact_phone=${afterStoreEdit.storePhone}`);
    await sleep(500);

    // 6. 平台管理只显示一个联系电话列
    const adminPage = await newPage(browser);
    await login(adminPage, ADMIN);
    await adminPage.goto(BASE + '/admin/users', { waitUntil: 'networkidle2' });
    await sleep(900);
    await setInput(adminPage, 'input[placeholder="按用户名搜索"]', 'merchant_demo');
    await adminPage.keyboard.press('Enter');
    await sleep(900);
    const adminView = await adminPage.evaluate(() => {
      const row = Array.from(document.querySelectorAll('tbody tr')).find((tr) => tr.innerText.includes('merchant_demo'));
      return {
        rowText: row ? row.innerText.replace(/\s+/g, ' ') : '',
        hasOldColumn: document.body.innerText.includes('店铺联系电话'),
        headers: Array.from(document.querySelectorAll('th')).map((th) => th.innerText.trim()),
      };
    });
    const phoneCount = (adminView.rowText.match(/13744445555/g) || []).length;
    record(6, '平台管理只显示一个联系电话列',
      phoneCount === 1 && !adminView.hasOldColumn && adminView.headers.includes('联系电话'),
      adminView.rowText.slice(0, 160));

    // 7. 上传店铺头像：选择 → 预览 → 确认上传
    const logoBefore = (await merchantState(merchantPage)).logo;
    await merchantPage.goto(BASE + '/merchant/profile', { waitUntil: 'networkidle2' });
    await sleep(800);
    await clickByText(merchantPage, '编辑资料');
    await sleep(400);
    const fileInput = await merchantPage.$('input[type="file"][accept*="image/png"]');
    await fileInput.uploadFile(PNG_FILE);
    await sleep(400);
    const previewReady = await merchantPage.evaluate(() => !!document.querySelector('.logo-preview img'));
    await clickByText(merchantPage, '确认上传');
    await sleep(1200);
    const afterUpload = await waitForState(merchantPage, (s) => s.logo && s.logo !== logoBefore);
    uploadedUrl = afterUpload.logo || '';
    const headerAfter = await headerLogoSrc(merchantPage);
    record(7, '选择图片、预览并确认上传后头像立即更新',
      previewReady && !!uploadedUrl && uploadedUrl.startsWith('/uploads/avatars/') && headerAfter === uploadedUrl,
      `logo=${uploadedUrl}, header=${headerAfter}`);

    // 8. 非法格式在本地即被拒绝
    const fileInput2 = await merchantPage.$('input[type="file"][accept*="image/png"]');
    await fileInput2.uploadFile(TXT_FILE);
    await sleep(400);
    const uploadError = await merchantPage.evaluate(() => document.querySelector('.upload-error')?.textContent || '');
    record(8, '非图片格式被前端拦截并提示', uploadError.includes('仅支持 JPG'), uploadError);
    await clickByText(merchantPage, '取消选择');
    await clickByText(merchantPage, '取消');
    await sleep(400);

    // 9. 跨页面头像一致
    await merchantPage.goto(BASE + '/merchant', { waitUntil: 'networkidle2' });
    await sleep(1000);
    const dashboardSrcs = await pageLogoSrcs(merchantPage);
    const dashboardHeader = await headerLogoSrc(merchantPage);
    record(9, '商家工作台/右上角头像与店铺资料一致',
      dashboardSrcs.length > 0 && dashboardSrcs.every((s) => s === uploadedUrl) && dashboardHeader === uploadedUrl,
      `srcs=${dashboardSrcs.length}`);

    // 10. 刷新后保持
    await merchantPage.reload({ waitUntil: 'networkidle2' });
    await sleep(1000);
    const afterReload = await headerLogoSrc(merchantPage);
    const reloadState = await merchantState(merchantPage);
    record(10, '刷新页面后头像与手机号保持',
      afterReload === uploadedUrl && reloadState.userPhone === TEST_PHONE_B && reloadState.storePhone === TEST_PHONE_B);

    // 11. 重新登录后保持
    const reloginPage = await newPage(browser);
    await login(reloginPage, MERCHANT);
    await reloginPage.goto(BASE + '/merchant/profile', { waitUntil: 'networkidle2' });
    await sleep(1000);
    const relogin = await merchantState(reloginPage);
    const reloginHeader = await headerLogoSrc(reloginPage);
    record(11, '退出后重新登录，头像与手机号仍一致',
      relogin.logo === uploadedUrl && reloginHeader === uploadedUrl
      && relogin.userPhone === TEST_PHONE_B && relogin.storePhone === TEST_PHONE_B);

    // 12. 普通用户与平台管理端不受影响
    const userPage = await newPage(browser);
    await login(userPage, USER);
    await userPage.goto(BASE + '/profile', { waitUntil: 'networkidle2' });
    await sleep(900);
    const userHeader = await userPage.evaluate(() => ({
      userImg: document.querySelector('.user-trigger .user-avatar-img')?.getAttribute('src') || '',
      shopImg: document.querySelectorAll('.header-user .merchant-avatar').length,
    }));
    await adminPage.goto(BASE + '/admin', { waitUntil: 'networkidle2' });
    await sleep(800);
    const adminHeaderCheck = await adminPage.evaluate(() => ({
      hasBear: !!Array.from(document.querySelectorAll('.header-user .avatar')).find((el) => el.textContent.trim() === '熊'),
      shopImg: document.querySelectorAll('.header-user .merchant-avatar').length,
    }));
    record(12, '普通用户与平台管理端头像逻辑未受影响',
      !!userHeader.userImg && userHeader.shopImg === 0 && adminHeaderCheck.hasBear && adminHeaderCheck.shopImg === 0,
      `用户头像=${userHeader.userImg}`);

    await userPage.closeCtx();
    await adminPage.closeCtx();
    await reloginPage.closeCtx();
    await merchantPage.closeCtx();
  } finally {
    finish(browser);
  }
})().catch((error) => {
  console.error('验收脚本异常:', error);
  process.exitCode = 1;
});

function finish(browser) {
  if (finished) return;
  finished = true;
  // 还原演示账号：使用 SQL 直接恢复两个独立字段；头像恢复原 URL，删除本次上传文件
  try {
    mysql(`UPDATE user SET phone=${original.userPhone ? `'${original.userPhone}'` : 'NULL'} WHERE username='merchant_demo'; `
      + `UPDATE merchant_profile SET contact_phone=${original.storePhone ? `'${original.storePhone}'` : 'NULL'}, logo=${original.logo ? `'${original.logo.replace(/'/g, "''")}'` : 'NULL'} `
      + `WHERE user_id=(SELECT id FROM user WHERE username='merchant_demo');`);
    if (uploadedUrl) {
      const file = path.join(BACKEND_AVATAR_DIR, path.basename(uploadedUrl));
      if (fs.existsSync(file)) fs.unlinkSync(file);
    }
  } catch (error) {
    console.error('数据还原失败，请手动检查 merchant_demo:', error.message);
  }
  if (browser) browser.close().catch(() => undefined);

  const passed = results.filter((r) => r.ok).length;
  console.log(`\n结果：${passed}/${results.length} 通过`);
  if (passed !== results.length) process.exitCode = 1;
}
