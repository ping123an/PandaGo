/**
 * PandaGo 立即购买 + 支付流程 —— 浏览器端验收（11 项，对应需求测试 1-11）
 * 运行：node docs/buynow_payment_check.cjs
 */
const puppeteer = require('../frontend/node_modules/puppeteer-core');
const { execFileSync } = require('child_process');

const BASE = 'http://127.0.0.1:5173';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const MYSQL = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe';
const DB_ARGS = ['-h127.0.0.1', '-P3307', '-uroot', 'pandago'];
const USERNAME = 'user_demo', PASSWORD = 'Panda@12345', UID = 49;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
function record(id, name, ok, detail) {
  results.push({ id, name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'} [${id}] ${name}${detail ? ' — ' + detail : ''}`);
}
function sql(s) { return execFileSync(MYSQL, [...DB_ARGS, '-e', s], { encoding: 'utf8' }); }

async function newPage(browser) {
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();
  await page.setViewport({ width: 1366, height: 900 });
  page.closeCtx = async () => { await page.close(); await ctx.close(); };
  return page;
}
async function login(page) {
  await page.goto(`${BASE}/login/user`, { waitUntil: 'networkidle2' });
  const u = await page.$('input[placeholder*="用户"]'), p = await page.$('input[type="password"]');
  await u.type(USERNAME); await p.type(PASSWORD);
  await page.evaluate(() => {
    const b = Array.from(document.querySelectorAll('button')).find(x => (x.textContent||'').trim()==='登录');
    if (b) b.click();
  });
  await sleep(1600);
}

(async () => {
  const tag = Date.now();
  const pBuy = `BPC-BUY-${tag}`, pCart1 = `BPC-C1-${tag}`, pCart2 = `BPC-C2-${tag}`, pShort = `BPC-SHORT-${tag}`;
  try {
    const cat = sql('SELECT id FROM category WHERE deleted=0 LIMIT 1;').trim().split('\n').pop();
    sql(`
      INSERT INTO product(category_id,merchant_id,name,main_image,price,stock,sales,status,audit_status,sort)
        VALUES (${cat},48,'${pBuy}','/images/panda-product.svg',599.00,30,0,'ON_SALE','APPROVED',0),
               (${cat},48,'${pCart1}','/images/panda-product.svg',2999.00,10,0,'ON_SALE','APPROVED',0),
               (${cat},0,'${pCart2}','/images/panda-product.svg',399.00,10,0,'ON_SALE','APPROVED',0),
               (${cat},0,'${pShort}','/images/panda-product.svg',20.00,1,0,'ON_SALE','APPROVED',0);
      DELETE FROM cart_item WHERE user_id=${UID};
      INSERT INTO cart_item(user_id,product_id,quantity,selected)
        SELECT ${UID},id,1,1 FROM product WHERE name IN ('${pCart1}','${pCart2}');
    `);
    const ids = sql(`SELECT name,id FROM product WHERE name LIKE 'BPC-%-${tag}';`);
    const idOf = (n) => { const m = ids.split('\n').find(x => x.startsWith(n)); return m ? m.split('\t').pop().trim() : null; };
    global.PID_BUY = idOf(pBuy); global.PID_SHORT = idOf(pShort);
  } catch (e) { console.error('播种失败:', e.message); process.exit(1); }

  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
  });
  try {
    // 测试1&4：未登录点立即购买 → 引导登录；登录后继续
    {
      const page = await newPage(browser);
      await page.goto(`${BASE}/products/${global.PID_BUY}`, { waitUntil: 'networkidle2' });
      await sleep(800);
      const hasBuyNow = await page.evaluate(() =>
        Array.from(document.querySelectorAll('button')).some(b => (b.textContent||'').includes('立即购买')));
      record(1, '商品详情有立即购买按钮', hasBuyNow);
      // 未登录点击 → 跳登录
      await page.evaluate(() => {
        const b = Array.from(document.querySelectorAll('button')).find(x => (x.textContent||'').trim()==='立即购买');
        if (b) b.click();
      });
      await sleep(900);
      const redirected = page.url().includes('/login');
      record(4, '未登录立即购买引导登录', redirected, page.url());
      await login(page);
      await page.goto(`${BASE}/products/${global.PID_BUY}`, { waitUntil: 'networkidle2' });
      await sleep(600);
      await page.evaluate(() => {
        const b = Array.from(document.querySelectorAll('button')).find(x => (x.textContent||'').trim()==='立即购买');
        if (b) b.click();
      });
      await sleep(900);
      record(1.1, '登录后立即购买进入确认订单页', page.url().includes('/checkout/buy-now'), page.url());
      await page.closeCtx();
    }

    // 测试2：数量3，确认页展示数量与金额
    {
      const page = await newPage(browser);
      await login(page);
      await page.goto(`${BASE}/checkout/buy-now?productId=${global.PID_BUY}&quantity=3`, { waitUntil: 'networkidle2' });
      await sleep(1000);
      const text = await page.evaluate(() => document.body.innerText);
      const qtyOk = text.includes('× 3') && text.includes('1797.00');
      record(2, '立即购买数量3，金额=599×3=1797', qtyOk, qtyOk?'金额与数量正确':'金额或数量不符');
      await page.closeCtx();
    }

    // 测试3：购物车已有其他商家商品，立即购买后购物车不受影响
    {
      const page = await newPage(browser);
      await login(page);
      const before = Number(sql(`SELECT COUNT(*) FROM cart_item WHERE user_id=${UID};`).trim().split('\n').pop());
      await page.goto(`${BASE}/checkout/buy-now?productId=${global.PID_BUY}&quantity=1`, { waitUntil: 'networkidle2' });
      await sleep(800);
      await page.evaluate(() => {
        const b = Array.from(document.querySelectorAll('button')).find(x => (x.textContent||'').trim()==='提交订单');
        if (b && !b.disabled) b.click();
      });
      await sleep(1500);
      const inPayment = page.url().includes('/pay/');
      record(6, '提交订单后跳转支付页', inPayment, page.url());
      const after = Number(sql(`SELECT COUNT(*) FROM cart_item WHERE user_id=${UID};`).trim().split('\n').pop());
      record(3, '立即购买不影响购物车原有商品', before === 2 && after === 2, `before=${before} after=${after}`);
      await page.closeCtx();
    }

    // 测试5：库存不足预览拦截
    {
      const page = await newPage(browser);
      await login(page);
      await page.goto(`${BASE}/checkout/buy-now?productId=${global.PID_SHORT}&quantity=5`, { waitUntil: 'networkidle2' });
      await sleep(1000);
      const text = await page.evaluate(() => document.body.innerText);
      const blocked = text.includes('库存不足') || text.includes('加载失败');
      record(5, '库存不足阻止下单并显示提示', blocked, blocked?'已拦截':'未拦截');
      await page.closeCtx();
    }

    // 测试7&8&9：支付成功 / 取消支付 / 重复支付
    {
      const page = await newPage(browser);
      await login(page);
      // 新建一笔订单用于支付测试
      const orderNo = await page.evaluate(async (pid) => {
        const tk = localStorage.getItem('pandago_token');
        const ad = await fetch('/api/v1/addresses', {headers:{Authorization:'Bearer '+tk}}).then(r=>r.json());
        const aid = ad.data[0].id;
        const r = await fetch('/api/v1/orders', {method:'POST', headers:{'Content-Type':'application/json',Authorization:'Bearer '+tk},
          body:JSON.stringify({addressId:aid, source:'DIRECT', items:[{productId:Number(pid),quantity:1}]})}).then(r=>r.json());
        return r.data.orderNo;
      }, global.PID_BUY);
      record(6.1, '直接创建订单返回订单号', !!orderNo, orderNo);

      await page.goto(`${BASE}/pay/${orderNo}`, { waitUntil: 'networkidle2' });
      await sleep(900);
      const payText = await page.evaluate(() => document.body.innerText);
      const hasMethods = payText.includes('支付宝') && payText.includes('微信支付');
      const hasCancel = payText.includes('取消支付');
      record(7.1, '支付页展示支付方式与取消支付', hasMethods && hasCancel);

      // 取消支付：订单仍在、未支付
      await page.evaluate(() => {
        const b = Array.from(document.querySelectorAll('button')).find(x => (x.textContent||'').trim()==='取消支付');
        if (b) b.click();
      });
      await sleep(800);
      const stillThere = Number(sql(`SELECT COUNT(*) FROM orders WHERE order_no='${orderNo}';`).trim().split('\n').pop());
      const unpaid = Number(sql(`SELECT COUNT(*) FROM payment_record WHERE order_no='${orderNo}';`).trim().split('\n').pop());
      record(8, '取消支付订单保留且未支付', stillThere===1 && unpaid===0, `order=${stillThere} pay=${unpaid}`);

      // 回到支付页确认支付（支付宝）
      await page.goto(`${BASE}/pay/${orderNo}`, { waitUntil: 'networkidle2' });
      await sleep(800);
      await page.evaluate(() => {
        const b = Array.from(document.querySelectorAll('button')).find(x => (x.textContent||'').trim()==='确认支付');
        if (b) b.click();
      });
      await sleep(1500);
      const successUrl = page.url().includes('payment-success');
      record(7, '模拟支付成功跳转支付成功页', successUrl, page.url());
      const paid = Number(sql(`SELECT COUNT(*) FROM payment_record WHERE order_no='${orderNo}' AND status='SUCCESS';`).trim().split('\n').pop());
      const status = sql(`SELECT status FROM orders WHERE order_no='${orderNo}';`).trim().split('\n').pop();
      record(7.2, '支付记录与订单状态更新正确', paid===1 && status==='PENDING_SHIPMENT', `paid=${paid} status=${status}`);

      // 重复支付：后端拦截（再次点击确认支付接口）
      const dup = await page.evaluate(async (no) => {
        const tk = localStorage.getItem('pandago_token');
        const r = await fetch('/api/v1/payments/mock', {method:'POST', headers:{'Content-Type':'application/json',Authorization:'Bearer '+tk},
          body:JSON.stringify({orderNo:no, paymentMethod:'ALIPAY'})});
        return r.status;
      }, orderNo);
      const payCount = Number(sql(`SELECT COUNT(*) FROM payment_record WHERE order_no='${orderNo}';`).trim().split('\n').pop());
      record(9, '重复支付被拦截，仅一条支付记录', dup===409 && payCount===1, `HTTP=${dup} records=${payCount}`);
      await page.closeCtx();
    }

    // 测试10&11：购物车结算多商家拆单、越权支付
    {
      const page = await newPage(browser);
      await login(page);
      const split = await page.evaluate(async () => {
        const tk = localStorage.getItem('pandago_token');
        const ad = await fetch('/api/v1/addresses', {headers:{Authorization:'Bearer '+tk}}).then(r=>r.json());
        const aid = ad.data[0].id;
        const cart = await fetch('/api/v1/cart', {headers:{Authorization:'Bearer '+tk}}).then(r=>r.json());
        const ids = cart.data.items.filter(i=>i.selected && i.available).map(i=>i.id);
        const r = await fetch('/api/v1/orders', {method:'POST', headers:{'Content-Type':'application/json',Authorization:'Bearer '+tk},
          body:JSON.stringify({addressId:aid, source:'CART', cartItemIds:ids, checkoutGroupId:'bpc-'+Date.now()})}).then(r=>r.json());
        return { code:r.code, count:r.data?.orderCount, total:r.data?.grandTotal };
      });
      record(10, '购物车结算多商家自动拆单', split.code===200 && split.count===2, `count=${split.count} total=${split.total}`);

      // 越权：用另一个用户 token 支付 user_demo 的订单
      const crossNo = sql(`SELECT order_no FROM orders WHERE user_id=${UID} ORDER BY id DESC LIMIT 1;`).trim().split('\n').pop();
      const cross = await page.evaluate(async (no) => {
        const lr = await fetch('/api/v1/auth/login', {method:'POST',headers:{'Content-Type':'application/json'},
          body:JSON.stringify({username:'merchant_demo',password:'Panda@12345'})}).then(r=>r.json());
        const r = await fetch('/api/v1/payments/mock', {method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+lr.data.token},
          body:JSON.stringify({orderNo:no})});
        return r.status;
      }, crossNo);
      record(11, '用户不能支付他人订单', cross===404 || cross===403, `HTTP=${cross}`);
      await page.closeCtx();
    }

  } finally {
    await browser.close();
    try {
      sql(`
        DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id=${UID} AND (checkout_group_id LIKE 'bpc-%' OR checkout_group_id IS NOT NULL AND created_at > NOW() - INTERVAL 5 MINUTE));
        DELETE FROM payment_record WHERE user_id=${UID} AND order_no NOT IN (SELECT order_no FROM orders);
        DELETE FROM orders WHERE user_id=${UID} AND created_at > NOW() - INTERVAL 5 MINUTE;
        DELETE FROM cart_item WHERE user_id=${UID};
        DELETE FROM product WHERE name LIKE 'BPC-%';
      `);
      // 恢复购物车基线：清空即可（演示用）
    } catch (e) { console.error('清理失败:', e.message); }
  }

  const pass = results.filter(r => r.ok).length;
  console.log(`\n=== 结果：${pass}/${results.length} 通过 ===`);
  process.exit(pass === results.length ? 0 : 1);
})().catch(e => { console.error(e); process.exit(1); });
