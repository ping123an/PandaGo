/**
 * PandaGo 商家商品审核状态重复显示修复 —— 浏览器端验收验证
 * 使用 puppeteer-core 驱动本机 Chrome，真实访问 5173 前端 + 8080 后端。
 * 运行：node docs/merchant_audit_status_check.cjs
 *
 * 覆盖：审核列单一标签、三态映射、上架/审核两列独立、状态流转（提交审核/通过/驳回/重新提交）、
 *       上架下架互不影响、刷新保持、筛选一致、异常状态不误显示。
 */
const puppeteer = require('../frontend/node_modules/puppeteer-core');

const BASE = 'http://127.0.0.1:5173';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const MERCHANT = { username: 'merchant_demo', password: 'Panda@12345' };
const ADMIN = { username: 'admin_demo', password: 'Panda@12345' };
const TEST_NAME = 'AUDITSTATUS-CHECK-TEST' + Date.now().toString().slice(-6);
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

/** 在页面上下文中登录并写入 token */
async function login(page, account) {
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

/** 从页面发 API 请求（带当前页面 token） */
async function api(page, method, path, body) {
  return page.evaluate(async ({ method, path, body }) => {
    const headers = { Authorization: 'Bearer ' + localStorage.getItem('pandago_token') };
    if (body) headers['Content-Type'] = 'application/json';
    const r = await fetch(path, {
      method, headers, body: body ? JSON.stringify(body) : undefined,
    });
    let json = null;
    try { json = await r.json(); } catch (_) { /* noop */ }
    return { status: r.status, json };
  }, { method, path, body });
}

/** 打开商家商品页并读取指定商品的审核列/上架列 DOM */
async function readRow(page, productId) {
  await page.goto(BASE + '/merchant/products', { waitUntil: 'networkidle2' });
  await sleep(900);
  return page.evaluate((id) => {
    const rows = Array.from(document.querySelectorAll('.el-table__body tbody tr'));
    const row = rows.find((tr) => tr.querySelector('td')?.textContent.trim() === String(id));
    if (!row) return { found: false, rows: rows.length };
    const cells = Array.from(row.querySelectorAll('td'));
    const saleCell = cells[5];
    const auditCell = cells[6];
    const tags = Array.from(auditCell.querySelectorAll('.el-tag'));
    return {
      found: true,
      saleText: saleCell?.innerText.trim() || '',
      auditText: auditCell?.innerText.trim() || '',
      tagCount: tags.length,
      tagTexts: tags.map((t) => t.innerText.trim()),
      auditCellText: auditCell?.textContent || '',
      hasAuditReasonParagraph: !!auditCell.querySelector('.audit-reason'),
    };
  }, productId);
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
  let productId = null;
  try {
    // 商家登录 + 创建测试商品（PENDING / OFF_SALE）
    const merchantPage = await newPage(browser);
    const mLogin = await login(merchantPage, MERCHANT);
    if (!mLogin.ok) throw new Error('商家登录失败');

    const created = await api(merchantPage, 'POST', '/api/v1/merchant/products', {
      categoryId: 1,
      name: TEST_NAME,
      subtitle: '审核状态验收测试',
      mainImage: '/images/panda-product.svg',
      detailImages: [],
      price: 10.00,
      stock: 5,
      description: '仅用于审核状态验收，测试后删除',
      sort: 0,
    });
    productId = created.json?.data;
    if (!productId) throw new Error('创建测试商品失败: ' + JSON.stringify(created));

    // 1. 新商品提交后显示待审核，审核列只有一个标签
    {
      const row = await readRow(merchantPage, productId);
      record(1, '新商品显示待审核且审核列只有一个标签',
        row.found && row.tagCount === 1 && row.auditText === '待审核' && row.saleText === '已下架',
        `审核=${row.auditText} 标签数=${row.tagCount} 上架=${row.saleText}`);
    }

    // 管理员登录并审核通过
    const adminPage = await newPage(browser);
    const aLogin = await login(adminPage, ADMIN);
    if (!aLogin.ok) throw new Error('管理员登录失败');
    const approve = await api(adminPage, 'PUT', `/api/v1/admin/products/${productId}/audit`, { auditStatus: 'APPROVED' });

    // 2. 管理员通过后显示已通过，仍只有单一标签
    {
      const row = await readRow(merchantPage, productId);
      record(2, '审核通过后显示已通过且只有一个标签',
        approve.status === 200 && row.found && row.tagCount === 1 && row.auditText === '已通过' && row.saleText === '已下架',
        `HTTP=${approve.status} 审核=${row.auditText} 标签数=${row.tagCount} 上架=${row.saleText}`);
    }

    // 3/4. 上架后审核状态不变，两列独立
    {
      const up = await api(merchantPage, 'PUT', `/api/v1/merchant/products/${productId}/status`, { status: 'ON_SALE' });
      const row = await readRow(merchantPage, productId);
      record(3, '已通过商品上架后审核仍显示已通过',
        up.status === 200 && row.found && row.auditText === '已通过' && row.saleText === '已上架' && row.tagCount === 1,
        `上架=${row.saleText} 审核=${row.auditText} 标签数=${row.tagCount}`);

      const down = await api(merchantPage, 'PUT', `/api/v1/merchant/products/${productId}/status`, { status: 'OFF_SALE' });
      const row2 = await readRow(merchantPage, productId);
      record(4, '已通过商品下架后审核仍显示已通过',
        down.status === 200 && row2.found && row2.auditText === '已通过' && row2.saleText === '已下架' && row2.tagCount === 1,
        `上架=${row2.saleText} 审核=${row2.auditText}`);
    }

    // 5. 管理员驳回 → 已驳回 + 强制下架，单一标签
    {
      const reject = await api(adminPage, 'PUT', `/api/v1/admin/products/${productId}/audit`, {
        auditStatus: 'REJECTED', reason: 'AUTOMATED CHECK REASON',
      });
      const row = await readRow(merchantPage, productId);
      record(5, '驳回后显示已驳回且强制下架，审核列只有一个标签',
        reject.status === 200 && row.found && row.auditText === '已驳回' && row.saleText === '已下架' && row.tagCount === 1,
        `HTTP=${reject.status} 审核=${row.auditText} 上架=${row.saleText} 标签数=${row.tagCount}`);
    }

    // 6. 驳回原因不显示为第二个状态标签
    {
      const row = await readRow(merchantPage, productId);
      record(6, '驳回原因不作为第二个状态文字出现在审核列',
        row.found && !row.hasAuditReasonParagraph && row.tagCount === 1,
        `标签数=${row.tagCount} 原因段落=${row.hasAuditReasonParagraph} 审核列文本=${row.auditText}`);
    }

    // 7. 商家重新提交审核 → 待审核
    {
      const submit = await api(merchantPage, 'POST', `/api/v1/merchant/products/${productId}/submit-audit`);
      const row = await readRow(merchantPage, productId);
      record(7, '已驳回商品重新提交后显示待审核',
        submit.status === 200 && row.found && row.auditText === '待审核' && row.saleText === '已下架' && row.tagCount === 1,
        `HTTP=${submit.status} 审核=${row.auditText} 上架=${row.saleText}`);
    }

    // 8. 页面刷新后状态正确
    {
      const row = await readRow(merchantPage, productId);
      record(8, '刷新后审核状态与上架状态保持正确',
        row.found && row.auditText === '待审核' && row.saleText === '已下架' && row.tagCount === 1,
        `审核=${row.auditText} 上架=${row.saleText}`);
    }

    // 9. 筛选结果与标签一致（先通过，再按三态筛选）
    {
      await api(adminPage, 'PUT', `/api/v1/admin/products/${productId}/audit`, { auditStatus: 'APPROVED' });
      const check = await merchantPage.evaluate(async (name) => {
        const token = localStorage.getItem('pandago_token');
        const out = {};
        for (const status of ['PENDING', 'APPROVED', 'REJECTED']) {
          const r = await fetch(`/api/v1/merchant/products?auditStatus=${status}&pageNum=1&pageSize=50&keyword=${name.slice(-6)}`, {
            headers: { Authorization: 'Bearer ' + token },
          });
          const j = await r.json();
          out[status] = { status: r.status, list: j?.data?.list || [] };
        }
        return out;
      }, TEST_NAME);
      const approvedList = check.APPROVED.list.filter((p) => p.name === TEST_NAME);
      const pendingList = check.PENDING.list.filter((p) => p.name === TEST_NAME);
      const rejectedList = check.REJECTED.list.filter((p) => p.name === TEST_NAME);
      const ok = check.APPROVED.status === 200 && check.PENDING.status === 200 && check.REJECTED.status === 200
        && approvedList.length === 1 && approvedList[0].auditStatus === 'APPROVED'
        && pendingList.length === 0 && rejectedList.length === 0;
      record(9, '筛选结果与商品当前审核状态一致',
        ok, `APPROVED命中=${approvedList.length} PENDING命中=${pendingList.length} REJECTED命中=${rejectedList.length}`);
    }

    // 10. 后端返回异常状态时不会错误显示多个标签（拦截响应注入未知状态）
    {
      const page = await newPage(browser);
      await login(page, MERCHANT);
      await page.setRequestInterception(true);
      page.on('request', async (req) => {
        if (req.url().includes('/api/v1/merchant/products?')) {
          const body = JSON.stringify({
            code: 200, message: '成功', success: true,
            data: {
              list: [{
                id: 999999999, name: 'ABNORMAL-STATUS-CHECK', categoryName: '测试',
                mainImage: '/images/panda-product.svg', price: 1, stock: 1, sales: 0,
                status: 'ON_SALE', auditStatus: 'UNKNOWN_CLEAN', auditReason: '待审核',
              }],
              total: 1, pageNum: 1, pageSize: 10, pages: 1,
            },
            timestamp: new Date().toISOString(),
          });
          await req.respond({ status: 200, contentType: 'application/json', body });
        } else {
          await req.continue();
        }
      });
      await page.goto(BASE + '/merchant/products', { waitUntil: 'networkidle2' });
      await sleep(900);
      const info = await page.evaluate(() => {
        const rows = Array.from(document.querySelectorAll('.el-table__body tbody tr'));
        const row = rows.find((tr) => tr.textContent.includes('ABNORMAL-STATUS-CHECK'));
        const cell = row && Array.from(row.querySelectorAll('td'))[6];
        const tags = Array.from(cell?.querySelectorAll('.el-tag') || []);
        return {
          found: !!row,
          tagCount: tags.length,
          auditText: cell?.innerText.trim() || '',
        };
      });
      record(10, '异常审核状态显示状态异常且只有一个标签，不默认待审核',
        info.found && info.tagCount === 1 && info.auditText === '状态异常',
        `审核=${info.auditText} 标签数=${info.tagCount}`);
      await page.closeCtx();
    }

  } finally {
    // 清理测试商品：直接删库（仅本次自动生成、名称固定的测试数据，非历史数据）
    if (productId) {
      try {
        const { execSync } = require('child_process');
        execSync(`mysql -h127.0.0.1 -P3307 -uroot pandago -e "DELETE FROM product_image WHERE product_id=${productId}; DELETE FROM product WHERE id=${productId};"`, { stdio: 'ignore' });
      } catch (e) {
        console.log('cleanup warning:', e.message);
      }
    }
    await browser.close();
    const pass = results.filter((r) => r.ok).length;
    console.log(`\n===== 结果：${pass}/${results.length} 通过 =====`);
    console.log(`TEST_PRODUCT_ID=${productId || 'none'}`);
    if (pass !== results.length) process.exitCode = 1;
  }
})();
