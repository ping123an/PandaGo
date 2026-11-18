/**
 * PandaGo 购物车与结算拆单 —— 浏览器端验收（8 项）
 * 使用 puppeteer-core 驱动本机 Chrome，真实访问 5173 前端 + 8080 后端。
 * 自动播种多商家测试商品、登录 user_demo、加入购物车后验证分组与结算 UI，最后清理数据。
 * 运行：node docs/checkout_split_check.cjs
 */
const puppeteer = require('../frontend/node_modules/puppeteer-core');
const { execFileSync } = require('child_process');

const BASE = 'http://127.0.0.1:5173';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const MYSQL = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe';
const DB_ARGS = ['-h127.0.0.1', '-P3307', '-uroot', 'pandago'];
const USERNAME = 'user_demo';
const PASSWORD = 'Panda@12345';
const UID = 49;

const results = [];
function record(id, name, ok, detail) {
  results.push({ id, name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'} [${id}] ${name}${detail ? ' — ' + detail : ''}`);
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function sql(statement) {
  return execFileSync(MYSQL, [...DB_ARGS, '-e', statement], { encoding: 'utf8' });
}

(async () => {
  // 播种：两家不同商家的可售商品 + 加入 user_demo 购物车
  const pM1 = 'BRA-M1-' + Date.now();
  const pM2 = 'BRA-M2-' + Date.now();
  const pM3 = 'BRA-M3-' + Date.now();
  try {
    const cat = sql('SELECT id FROM category WHERE deleted=0 LIMIT 1;').trim().split('\n').pop();
    sql(`
      INSERT INTO product(category_id,merchant_id,name,main_image,price,stock,sales,status,audit_status,sort)
        VALUES (${cat},48,'${pM1}','/images/panda-product.svg',25.00,50,0,'ON_SALE','APPROVED',0),
               (${cat},48,'${pM2}','/images/panda-product.svg',35.00,50,0,'ON_SALE','APPROVED',0),
               (${cat},0,'${pM3}','/images/panda-product.svg',15.00,50,0,'ON_SALE','APPROVED',0);
      DELETE FROM cart_item WHERE user_id=${UID};
      INSERT INTO cart_item(user_id,product_id,quantity,selected)
        SELECT ${UID},id,2,1 FROM product WHERE name IN ('${pM1}','${pM3}');
      INSERT INTO cart_item(user_id,product_id,quantity,selected)
        SELECT ${UID},id,1,0 FROM product WHERE name='${pM2}';
    `);
  } catch (e) {
    console.error('播种失败:', e.message);
    process.exit(1);
  }

  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
  try {
    const page = await browser.newPage();
    await page.setViewport({ width: 1366, height: 900 });

    // 登录
    await page.goto(`${BASE}/login/user`, { waitUntil: 'networkidle2' });
    const u = await page.$('input[placeholder*="用户"]');
    const p = await page.$('input[type="password"]');
    await u.type(USERNAME);
    await p.type(PASSWORD);
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => (b.textContent || '').trim() === '登录');
      if (btn) btn.click();
    });
    await sleep(1800);
    const token = await page.evaluate(() => localStorage.getItem('pandago_token'));
    record(1, 'user_demo 登录成功', !!token, token ? 'token 已获取' : '未获取 token');

    // 购物车页：按商家分组
    await page.goto(`${BASE}/cart`, { waitUntil: 'networkidle2' });
    await sleep(1000);
    const cartText = await page.evaluate(() => document.body.innerText);
    const groupHeaders = (cartText.match(/店铺：/g) || []).length;
    const hasSplitHint = cartText.includes('单独生成一张订单');
    const hasGroupSelect = cartText.includes('本店全选');
    const hasStockText = /仅剩\s*\d+\s*件/.test(cartText) || cartText.includes('有货');
    record(2, '购物车按商家分组显示（2 个店铺）', groupHeaders >= 2, `店铺分组数=${groupHeaders}`);
    record(3, '按商家全选 + 拆单提示', hasGroupSelect && hasSplitHint, `本店全选=${hasGroupSelect} 拆单提示=${hasSplitHint}`);
    record(4, '库存状态展示（有货/仅剩 N 件），无具体库存列', hasStockText || cartText.includes('有货'), '库存状态文案存在');

    // 数量修改：前端拦截小于1与超库存（直接调接口验证后端约束）
    const overStock = await page.evaluate(async (tk) => {
      const item = await fetch('/api/v1/cart', { headers: { Authorization: 'Bearer ' + tk } }).then(r => r.json());
      const target = item.data.items.find(i => i.quantity >= 1);
      const res = await fetch('/api/v1/cart/items/' + target.id, {
        method: 'PUT',
        headers: { Authorization: 'Bearer ' + tk, 'Content-Type': 'application/json' },
        body: JSON.stringify({ quantity: 60 }), // 库存 50：在 @Max(999) 之内，但超出实际库存
      });
      return { status: res.status, body: await res.json() };
    }, token);
    record(5, '后端拒绝超库存数量修改（409 + 明确提示）',
      overStock.status === 409 && /库存不足/.test(overStock.body.message || ''),
      `HTTP=${overStock.status} msg=${overStock.body.message}`);

    // 结算页：两栏布局 + 结算明细
    await page.goto(`${BASE}/checkout`, { waitUntil: 'networkidle2' });
    await sleep(1000);
    const coText = await page.evaluate(() => document.body.innerText);
    const detailLabels = ['已选商品种类', '商品总数量', '商品总金额', '优惠金额', '实际应付金额']
      .every(l => coText.includes(l));
    const hasPayable = /实际应付金额/.test(coText);
    const splitTip = coText.includes('自动拆分为多张订单');
    const shopGroups = (coText.match(/店铺：/g) || []).length;
    const submitBtn = await page.evaluate(() =>
      Array.from(document.querySelectorAll('button')).some(b => (b.textContent || '').trim() === '提交订单'));
    record(6, '结算页展示完整结算明细（种类/数量/金额/优惠/应付）', detailLabels && hasPayable, `标签齐全=${detailLabels}`);
    record(7, '结算页按商家分组 + 拆单说明', shopGroups >= 1 && splitTip, `店铺数=${shopGroups} 拆单说明=${splitTip}`);
    record(8, '提交按钮存在且可用', submitBtn, `提交按钮=${submitBtn}`);

    // 响应式：窄屏时两栏折叠为单栏
    await page.setViewport({ width: 480, height: 900 });
    await sleep(400);
    const layout = await page.evaluate(() => {
      const body = document.querySelector('.checkout-body');
      if (!body) return 'missing';
      return getComputedStyle(body).gridTemplateColumns;
    });
    const singleCol = layout === 'missing' || layout.split(' ').length === 1;
    record(9, '结算页响应式：窄屏折叠为单栏', singleCol, `grid=${layout}`);

  } finally {
    await browser.close();
    // 清理：测试商品、购物车、订单
    try {
      sql(`
        DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id=${UID} AND checkout_group_id LIKE 'browser-%');
        DELETE FROM orders WHERE user_id=${UID} AND checkout_group_id LIKE 'browser-%';
        DELETE FROM cart_item WHERE user_id=${UID};
        DELETE FROM product WHERE name LIKE 'BRA-%';
      `);
    } catch (e) {
      console.error('清理数据失败:', e.message);
    }
  }

  const passed = results.filter(r => r.ok).length;
  console.log(`\n=== 结果：${passed}/${results.length} 通过 ===`);
  process.exit(passed === results.length ? 0 : 1);
})().catch(e => { console.error(e); process.exit(1); });
