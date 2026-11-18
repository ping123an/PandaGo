/**
 * PandaGo 个人中心头像上传 + 编辑资料布局 —— 浏览器端验收验证
 * 使用 puppeteer-core 驱动本机 Chrome，真实访问 5173 前端 + 8080 后端。
 * 运行：node docs/avatar_profile_check.cjs
 *
 * 覆盖：顶部卡片布局与按钮位置、默认头像、头像点击上传与预览、格式/大小校验、
 *       真实上传与刷新后保持、静态资源可访问、后端安全校验、编辑弹窗字段回填与只读项、
 *       昵称/手机号保存与取消、表单校验、窄屏响应式。
 */
const puppeteer = require('../frontend/node_modules/puppeteer-core');

const BASE = 'http://127.0.0.1:5173';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const USER = { username: 'user_demo', password: 'Panda@12345' };
const ORIGINAL = { nickname: '熊猫买家', phone: '' }; // 测试结束后还原
const PNG_BASE64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const results = [];
function record(id, name, ok, detail) {
  results.push({ id, name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'} [${id}] ${name}${detail ? ' — ' + detail : ''}`);
}

async function newPage(browser, viewport = { width: 1280, height: 900 }) {
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();
  await page.setViewport(viewport);
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

async function gotoProfile(page) {
  await loginToken(page, USER);
  await page.goto(BASE + '/profile', { waitUntil: 'networkidle2' });
  await sleep(1100);
}

/** 还原 user_demo 昵称和手机号，头像 URL 由外部脚本按输出的文件名清理 */
async function restoreProfile(browser) {
  const page = await newPage(browser);
  await loginToken(page, USER);
  await page.evaluate(async (orig) => {
    const r = await fetch('/api/v1/users/me/profile', {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nickname: orig.nickname, phone: orig.phone || null }),
    });
    await r.json();
  }, ORIGINAL);
  await sleep(600);
  await page.closeCtx();
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
  let uploadedAvatar = '';
  try {
    // ===== 1. 顶部卡片布局 =====
    {
      const page = await newPage(browser);
      await gotoProfile(page);
      const info = await page.evaluate(() => {
        const hero = document.querySelector('.profile-hero');
        const footer = document.querySelector('.footer-section');
        const buttons = Array.from(hero?.querySelectorAll('button') || []).map((b) => b.textContent.trim());
        return {
          hero: !!hero,
          avatar: !!hero?.querySelector('.avatar-button img'),
          name: hero?.querySelector('.hero-identity h1')?.textContent?.trim() || '',
          username: hero?.querySelector('.hero-identity p')?.textContent?.trim() || '',
          roles: hero?.querySelectorAll('.role-tag').length || 0,
          buttons,
          editInHero: buttons.some((t) => t.includes('编辑个人资料')),
          logoutInHero: buttons.some((t) => t.includes('退出登录')),
          editInFooter: (footer?.textContent || '').includes('编辑个人资料'),
        };
      });
      record(1, '顶部用户卡片含头像/昵称/用户名/角色与操作按钮',
        info.hero && info.avatar && !!info.name && info.username.includes('user_demo')
        && info.roles >= 1 && info.editInHero && info.logoutInHero && !info.editInFooter,
        `昵称=${info.name} 角色数=${info.roles} 按钮=${info.buttons.join('/')} 底部仍有编辑=${info.editInFooter}`);
      await page.closeCtx();
    }

    // ===== 2. 默认头像是图片不是文字 =====
    {
      const page = await newPage(browser);
      await gotoProfile(page);
      const info = await page.evaluate(() => {
        const img = document.querySelector('.avatar-button img');
        return { src: img?.getAttribute('src') || '', text: img?.textContent?.trim() || '' };
      });
      const ok = !!info.src && (info.src.includes('default-avatar.svg') || info.src.includes('/uploads/avatars/'))
        && !info.text;
      record(2, '头像使用图片资源而非固定文字', ok, `src=${info.src}`);
      await page.closeCtx();
    }

    // ===== 3. 头像可点击，弹出上传弹窗并显示预览/确认按钮 =====
    {
      const page = await newPage(browser);
      await gotoProfile(page);
      await page.click('.avatar-button');
      await sleep(400);
      const info = await page.evaluate(() => {
        const dialogs = Array.from(document.querySelectorAll('.el-dialog')).filter((d) => d.offsetParent !== null);
        const dialog = dialogs.find((d) => d.textContent.includes('更换头像'));
        return {
          open: !!dialog,
          hasPreview: !!dialog?.querySelector('.avatar-preview img'),
          hasInput: !!dialog?.querySelector('input[type=file]'),
          hint: dialog?.textContent.includes('2MB') || false,
          confirm: Array.from(dialog?.querySelectorAll('button') || []).some((b) => b.textContent.includes('确认上传')),
        };
      });
      record(3, '点击头像打开上传弹窗，含预览/文件选择/确认上传',
        info.open && info.hasPreview && info.hasInput && info.hint && info.confirm,
        `弹窗=${info.open} 预览=${info.hasPreview} 文件输入=${info.hasInput} 确认=${info.confirm}`);
      await page.closeCtx();
    }

    // ===== 4. 前端拦截超大文件 =====
    {
      const page = await newPage(browser);
      await gotoProfile(page);
      await page.click('.avatar-button');
      await sleep(350);
      const err = await page.evaluate(() => {
        const input = document.querySelector('.el-dialog input[type=file]');
        const big = new Uint8Array(2 * 1024 * 1024 + 1);
        const dt = new DataTransfer();
        dt.items.add(new File([big], 'big.png', { type: 'image/png' }));
        input.files = dt.files;
        input.dispatchEvent(new Event('change', { bubbles: true }));
      });
      await sleep(300);
      const errText = await page.evaluate(() => document.querySelector('.upload-error')?.textContent || '');
      record(4, '超过 2MB 的图片在前端被拦截并提示', errText.includes('2MB'), `提示=${errText}`);
      await page.closeCtx();
    }

    // ===== 5. 前端拦截不支持格式 =====
    {
      const page = await newPage(browser);
      await gotoProfile(page);
      await page.click('.avatar-button');
      await sleep(350);
      await page.evaluate(() => {
        const input = document.querySelector('.el-dialog input[type=file]');
        const dt = new DataTransfer();
        dt.items.add(new File([new Uint8Array([1, 2, 3])], 'a.gif', { type: 'image/gif' }));
        input.files = dt.files;
        input.dispatchEvent(new Event('change', { bubbles: true }));
      });
      await sleep(300);
      const errText = await page.evaluate(() => document.querySelector('.upload-error')?.textContent || '');
      record(5, '不支持的图片格式在前端被拦截', errText.includes('JPG') || errText.includes('WEBP'), `提示=${errText}`);
      await page.closeCtx();
    }

    // ===== 6. 选择图片后显示 blob 预览，确认上传成功 =====
    {
      const page = await newPage(browser);
      await gotoProfile(page);
      await page.click('.avatar-button');
      await sleep(350);
      const preview = await page.evaluate(async (b64) => {
        const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
        const input = document.querySelector('.el-dialog input[type=file]');
        const dt = new DataTransfer();
        dt.items.add(new File([bytes], 'avatar.png', { type: 'image/png' }));
        input.files = dt.files;
        input.dispatchEvent(new Event('change', { bubbles: true }));
        await new Promise((r) => setTimeout(r, 300));
        const img = document.querySelector('.avatar-preview img');
        return { src: img?.getAttribute('src') || '', hint: document.querySelector('.preview-hint')?.textContent || '' };
      }, PNG_BASE64);
      const previewOk = preview.src.startsWith('blob:') && preview.hint.includes('重新选择');
      record(6, '选择图片后展示本地预览且可更换', previewOk, `src前缀=${preview.src.slice(0, 12)} 提示=${preview.hint}`);

      await page.evaluate(() => {
        Array.from(document.querySelectorAll('.el-dialog button')).find((b) => b.textContent.includes('确认上传'))?.click();
      });
      await sleep(1500);
      const after = await page.evaluate(async () => {
        const src = document.querySelector('.avatar-button img')?.getAttribute('src') || '';
        const profile = await fetch('/api/v1/users/me/profile', {
          headers: { Authorization: 'Bearer ' + localStorage.getItem('pandago_token') },
        }).then((r) => r.json());
        return { src, dbAvatar: profile?.data?.avatar || '' };
      });
      uploadedAvatar = after.dbAvatar || after.src;
      const ok = after.src.includes('/uploads/avatars/') && after.dbAvatar.includes('/uploads/avatars/')
        && after.src === after.dbAvatar && after.src.endsWith('.png');
      record(7, '确认上传后头像更新且数据库保存相对 URL', ok,
        `页面=${after.src} 数据库=${after.dbAvatar}`);
      await page.closeCtx();
    }

    // ===== 8. 刷新后头像保持 =====
    {
      const page = await newPage(browser);
      await loginToken(page, USER);
      await page.goto(BASE + '/profile', { waitUntil: 'networkidle2' });
      await sleep(1100);
      const src = await page.evaluate(() => document.querySelector('.avatar-button img')?.getAttribute('src') || '');
      record(8, '刷新页面后头像仍然显示', src.includes('/uploads/avatars/'), `src=${src}`);
      await page.closeCtx();
    }

    // ===== 9. 上传文件可通过公开静态路径访问 =====
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      const result = await page.evaluate(async (url) => {
        const r = await fetch(url);
        return { status: r.status, type: r.headers.get('content-type') || '' };
      }, uploadedAvatar);
      record(9, '头像文件可通过 /uploads 静态路径访问', result.status === 200 && result.type.includes('image/png'),
        `HTTP=${result.status} Content-Type=${result.type}`);
      await page.closeCtx();
    }

    // ===== 10. 后端拒绝伪装文件（EXE 改名 PNG） =====
    {
      const page = await newPage(browser);
      await loginToken(page, USER);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      const result = await page.evaluate(async () => {
        const token = localStorage.getItem('pandago_token');
        const bytes = new Uint8Array([0x4D, 0x5A, 0x90, 0x00, 0x03]);
        const form = new FormData();
        form.append('file', new File([bytes], 'virus.png', { type: 'image/png' }));
        const r = await fetch('/api/v1/users/me/avatar', {
          method: 'POST', headers: { Authorization: 'Bearer ' + token }, body: form,
        });
        return { status: r.status, code: (await r.json()).code };
      });
      record(10, '后端拒绝可执行文件伪装成 PNG', result.status === 400 && result.code === 400,
        `HTTP=${result.status} code=${result.code}`);
      await page.closeCtx();
    }

    // ===== 11. 未登录不能上传头像 =====
    {
      const page = await newPage(browser);
      await page.goto(BASE, { waitUntil: 'networkidle2' });
      const status = await page.evaluate(async (b64) => {
        const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
        const form = new FormData();
        form.append('file', new File([bytes], 'a.png', { type: 'image/png' }));
        const r = await fetch('/api/v1/users/me/avatar', { method: 'POST', body: form });
        return r.status;
      }, PNG_BASE64);
      record(11, '未登录调用头像上传接口返回 401', status === 401, `HTTP=${status}`);
      await page.closeCtx();
    }

    // ===== 12. 编辑弹窗字段回填与只读项 =====
    {
      const page = await newPage(browser);
      await gotoProfile(page);
      const info = await page.evaluate(async () => {
        const profile = await fetch('/api/v1/users/me/profile', {
          headers: { Authorization: 'Bearer ' + localStorage.getItem('pandago_token') },
        }).then((r) => r.json());
        Array.from(document.querySelectorAll('.profile-hero button')).find((b) => b.textContent.includes('编辑个人资料'))?.click();
        await new Promise((r) => setTimeout(r, 400));
        const dialog = Array.from(document.querySelectorAll('.el-dialog')).find((d) => d.offsetParent !== null);
        const items = Array.from(dialog?.querySelectorAll('.el-form-item') || []);
        const find = (label) => items.find((i) => i.textContent.includes(label));
        const input = (label) => find(label)?.querySelector('input');
        return {
          open: !!dialog && dialog.textContent.includes('编辑个人资料'),
          avatar: !!find('头像')?.querySelector('img'),
          userId: input('用户 ID')?.disabled,
          username: input('用户名')?.disabled,
          role: input('角色')?.disabled,
          nickname: input('昵称')?.disabled,
          phone: input('手机号')?.disabled,
          nicknameValue: input('昵称')?.value,
          phoneValue: input('手机号')?.value,
          expectedNickname: profile?.data?.nickname || '',
          expectedPhone: profile?.data?.phone || '',
          noSmsNote: (dialog?.textContent || '').includes('短信验证码'),
        };
      });
      record(12, '编辑弹窗回填真实资料，ID/用户名/角色只读',
        info.open && info.avatar && info.userId === true && info.username === true && info.role === true
        && info.nickname === false && info.phone === false
        && info.nicknameValue === info.expectedNickname && (info.phoneValue || '') === info.expectedPhone
        && info.noSmsNote,
        `回填昵称=${info.nicknameValue} 手机=${info.phoneValue} 只读=${info.userId}/${info.username}/${info.role}`);
      await page.closeCtx();
    }

    // ===== 13. 修改昵称和手机号保存成功 =====
    {
      const page = await newPage(browser);
      await gotoProfile(page);
      const result = await page.evaluate(async () => {
        Array.from(document.querySelectorAll('.profile-hero button')).find((b) => b.textContent.includes('编辑个人资料'))?.click();
        await new Promise((r) => setTimeout(r, 400));
        const dialog = Array.from(document.querySelectorAll('.el-dialog')).find((d) => d.offsetParent !== null);
        const items = Array.from(dialog.querySelectorAll('.el-form-item'));
        const input = (label) => items.find((i) => i.textContent.includes(label))?.querySelector('input');
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
        const nick = input('昵称');
        const phone = input('手机号');
        setter.call(nick, '头像验收昵称');
        nick.dispatchEvent(new Event('input', { bubbles: true }));
        setter.call(phone, '13733334444');
        phone.dispatchEvent(new Event('input', { bubbles: true }));
        Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent.trim() === '保存')?.click();
        await new Promise((r) => setTimeout(r, 1400));
        return { hero: document.querySelector('.hero-identity h1')?.textContent?.trim() || '', text: document.body.innerText };
      });
      record(13, '编辑弹窗保存昵称与手机号成功并刷新页面',
        result.hero === '头像验收昵称' && result.text.includes('13733334444'),
        `昵称=${result.hero} 手机可见=${result.text.includes('13733334444')}`);
      await page.closeCtx();
    }

    // ===== 14. 取消编辑：脏数据提醒且不落库 =====
    {
      const page = await newPage(browser);
      await gotoProfile(page);
      const result = await page.evaluate(async () => {
        Array.from(document.querySelectorAll('.profile-hero button')).find((b) => b.textContent.includes('编辑个人资料'))?.click();
        await new Promise((r) => setTimeout(r, 400));
        const dialog = Array.from(document.querySelectorAll('.el-dialog')).find((d) => d.offsetParent !== null);
        const nick = Array.from(dialog.querySelectorAll('.el-form-item'))
          .find((i) => i.textContent.includes('昵称'))?.querySelector('input');
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
        setter.call(nick, '不应保存的名字');
        nick.dispatchEvent(new Event('input', { bubbles: true }));
        Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent.trim() === '取消')?.click();
        await new Promise((r) => setTimeout(r, 350));
        const confirmVisible = Array.from(document.querySelectorAll('.el-message-box')).some((d) => d.offsetParent !== null);
        Array.from(document.querySelectorAll('.el-message-box button'))
          .find((b) => ['确定', 'OK', '确'].some((t) => b.textContent.includes(t)))?.click();
        await new Promise((r) => setTimeout(r, 600));
        return { confirmVisible, text: document.body.innerText };
      });
      const ok = result.confirmVisible && result.text.includes('头像验收昵称') && !result.text.includes('不应保存的名字');
      record(14, '取消有修改的资料弹出提醒且不落库', ok,
        `提醒=${result.confirmVisible} 未保存名字泄露=${result.text.includes('不应保存的名字')}`);
      await page.closeCtx();
    }

    // ===== 15. 表单校验：空白昵称 / 非法手机号 =====
    {
      const page = await newPage(browser);
      await gotoProfile(page);
      const info = await page.evaluate(async () => {
        Array.from(document.querySelectorAll('.profile-hero button')).find((b) => b.textContent.includes('编辑个人资料'))?.click();
        await new Promise((r) => setTimeout(r, 400));
        const dialog = Array.from(document.querySelectorAll('.el-dialog')).find((d) => d.offsetParent !== null);
        const items = Array.from(dialog.querySelectorAll('.el-form-item'));
        const input = (label) => items.find((i) => i.textContent.includes(label))?.querySelector('input');
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
        const nick = input('昵称'); const phone = input('手机号');
        setter.call(nick, '   '); nick.dispatchEvent(new Event('input', { bubbles: true })); nick.dispatchEvent(new Event('blur', { bubbles: true }));
        setter.call(phone, '12345'); phone.dispatchEvent(new Event('input', { bubbles: true })); phone.dispatchEvent(new Event('blur', { bubbles: true }));
        await new Promise((r) => setTimeout(r, 500));
        return { errs: Array.from(document.querySelectorAll('.el-form-item__error')).map((e) => e.textContent.trim()).join(' ') };
      });
      record(15, '空白昵称与非法手机号被校验拦截',
        info.errs.includes('昵称') && info.errs.includes('手机号'), `错误=${info.errs}`);
      await page.closeCtx();
    }

    // ===== 16. 窄屏无横向溢出 =====
    {
      const page = await newPage(browser, { width: 390, height: 844 });
      await gotoProfile(page);
      const info = await page.evaluate(() => ({
        hero: !!document.querySelector('.profile-hero'),
        avatar: !!document.querySelector('.avatar-button img'),
        width: document.body.scrollWidth,
        viewport: window.innerWidth,
      }));
      record(16, '窄屏下个人中心无横向溢出', info.hero && info.avatar && info.width <= info.viewport + 8,
        `宽度=${info.width}/${info.viewport}`);
      await page.closeCtx();
    }

  } finally {
    try { await restoreProfile(browser); } catch (e) { console.log('restore warning:', e.message); }
    await browser.close();
    const pass = results.filter((r) => r.ok).length;
    console.log(`\n===== 结果：${pass}/${results.length} 通过 =====`);
    console.log(`UPLOADED_AVATAR=${uploadedAvatar}`);
    if (pass !== results.length) process.exitCode = 1;
  }
})();
