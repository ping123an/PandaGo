#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PandaGo 端到端业务演示脚本（阶段 10）。
对运行中的后端执行完整购物链路并记录每步结果，用于验收"核心购物流程完整 + 权限隔离"。

用法：
    python docs/e2e_demo.py --base http://127.0.0.1:8081

前置：
    1. 后端已启动（dev 或 prod profile 均可），数据库已初始化 schema.sql + init-data.sql。
    2. demo 账号已导入（merchant_demo / admin_demo，密码 Panda@12345）——仅教学环境。
    3. pip install requests

演示链路（14 步）：
    商家建商品并提交审核 -> 管理员审核通过 -> 商家上架 -> 注册用户 ->
    登录 -> 浏览分类/商品 -> 搜索/详情 -> 加购 -> 地址 -> 下单 ->
    支付 -> 商家查看订单/发货 -> 确认收货 -> 评价 -> 越权验证。
脚本会新建专属商品与订单，演示结束数据留在库中（可按需手动清理 name LIKE 'E2E-%' 的商品）。
"""
import argparse
import uuid

import requests

DEMO_PWD = "Panda@12345"
results = []


def log(step, ok, detail=""):
    flag = "PASS" if ok else "FAIL"
    results.append((step, ok, detail))
    print(f"[{flag}] {step}" + (f" -> {detail}" if detail else ""))


def call(method, path, token=None, expect=200, **kwargs):
    headers = kwargs.pop("headers", {})
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = requests.request(method, BASE + path, headers=headers, timeout=15, **kwargs)
    body = {}
    try:
        body = r.json() if r.content else {}
    except ValueError:
        pass
    return r.status_code, body


def login(username, password=DEMO_PWD):
    code, body = call("POST", "/api/v1/auth/login", json={
        "username": username, "password": password})
    return (body.get("data") or {}).get("token") if code == 200 else None


def main():
    global BASE
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8081")
    args = parser.parse_args()
    BASE = args.base
    tag = uuid.uuid4().hex[:8]

    print(f"=== PandaGo 端到端演示 @ {BASE} ===\n")

    admin_token = login("admin_demo")
    merchant_token = login("merchant_demo")
    log("0. 管理员/商家登录", bool(admin_token and merchant_token),
        f"admin={'OK' if admin_token else 'FAIL'}, merchant={'OK' if merchant_token else 'FAIL'}")
    if not (admin_token and merchant_token):
        print("\n登录失败，终止。请确认 demo 账号已导入。")
        return

    # 取一个分类
    code, cats = call("GET", "/api/v1/categories")
    cat_list = cats.get("data") or []
    category_id = cat_list[0]["id"] if cat_list else 1

    # 0. 商家创建商品 -> 提交审核 -> 管理员审核 -> 商家上架
    prod_name = f"E2E-{tag} 熊猫演示商品"
    code, created_p = call("POST", "/api/v1/merchant/products", merchant_token, json={
        "categoryId": category_id, "name": prod_name, "subtitle": "端到端演示",
        "mainImage": "/images/panda-product.svg", "price": 66.0, "stock": 50})
    pid = created_p.get("data")
    log("0.1 商家创建商品", code == 200 and bool(pid), f"productId={pid}")

    code, _ = call("POST", f"/api/v1/merchant/products/{pid}/submit-audit", merchant_token)
    log("0.2 商家提交审核", code == 200, f"HTTP {code}")

    code, _ = call("PUT", f"/api/v1/admin/products/{pid}/audit", admin_token,
                   json={"auditStatus": "APPROVED", "reason": "演示审核通过"})
    log("13. 管理员审核商品", code == 200, f"审核通过 HTTP {code}")

    code, _ = call("PUT", f"/api/v1/merchant/products/{pid}/status", merchant_token,
                   json={"status": "ON_SALE"})
    log("0.3 商家上架商品", code == 200, f"HTTP {code}")

    # 1. 注册普通用户
    uname = "e2e_" + tag
    code, _ = call("POST", "/api/v1/auth/register", json={
        "username": uname, "password": DEMO_PWD,
        "confirmPassword": DEMO_PWD, "nickname": "端到端演示用户"})
    log("1. 注册普通用户", code == 200, f"username={uname}, HTTP {code}")
    user_token = login(uname)
    log("2. 用户登录", bool(user_token), f"token={'有' if user_token else '无'}")
    if not user_token:
        return

    # 3. 浏览分类和商品
    code, cats = call("GET", "/api/v1/categories")
    log("3.1 浏览分类", code == 200 and bool(cats.get("data")), f"分类数={len(cats.get('data') or [])}")
    code, prods = call("GET", "/api/v1/products?pageNum=1&pageSize=20")
    product_list = (prods.get("data") or {}).get("list") or []
    log("3.2 浏览商品列表", code == 200 and bool(product_list), f"返回 {len(product_list)} 条")

    # 4. 搜索商品并查看详情（搜索演示商品名确保命中刚建的商品）
    code, searched = call("GET", f"/api/v1/products?keyword=E2E-{tag}&pageSize=10")
    hit_list = (searched.get("data") or {}).get("list") or []
    log("4.1 搜索商品", code == 200 and any(p["id"] == pid for p in hit_list),
        f"关键词命中 {len(hit_list)} 条，含演示商品={any(p['id'] == pid for p in hit_list)}")
    code, detail = call("GET", f"/api/v1/products/{pid}")
    log("4.2 商品详情", code == 200 and detail.get("data", {}).get("id") == pid,
        f"productId={pid}, 名称={detail.get('data', {}).get('name', '')}")

    # 5. 添加购物车
    code, _ = call("POST", "/api/v1/cart/items", user_token, json={"productId": pid, "quantity": 2})
    log("5. 添加购物车", code == 200, f"HTTP {code}")

    # 6. 添加收货地址（data 直接是 ID）
    code, addr = call("POST", "/api/v1/addresses", user_token, json={
        "receiverName": "演示收货人", "phone": "13800000000",
        "province": "浙江省", "city": "杭州市", "district": "西湖区",
        "detailAddress": "熊猫路 1 号", "isDefault": 1})
    address_id = addr.get("data")
    log("6. 添加收货地址", code == 200 and bool(address_id), f"addressId={address_id}")

    # 7. 创建订单（购物车结算）
    code, created = call("POST", "/api/v1/orders", user_token, json={
        "addressId": address_id, "cartItemIds": [],
        "items": [{"productId": pid, "quantity": 2}]})
    order = created.get("data") or {}
    order_id, order_no = order.get("id"), order.get("orderNo")
    log("7. 创建订单", code == 200 and bool(order_no),
        f"orderNo={order_no}, 金额={order.get('totalAmount')}")

    # 8. 模拟支付
    code, paid = call("POST", "/api/v1/payments/mock", user_token, json={"orderNo": order_no})
    log("8. 模拟支付", code == 200, f"流水号={(paid.get('data') or {}).get('paymentNo', '')}")

    # 9. 商家登录并查看订单（演示商品归属 merchant_demo）
    code, morders = call("GET", "/api/v1/merchant/orders", merchant_token)
    mlist = (morders.get("data") or {}).get("list") or []
    log("9. 商家查看订单列表", code == 200 and any(o.get("id") == order_id for o in mlist),
        f"订单数={len(mlist)}，含本单={any(o.get('id') == order_id for o in mlist)}")

    # 10. 商家发货
    code, ship = call("POST", f"/api/v1/merchant/orders/{order_id}/ship", merchant_token,
                      json={"company": "熊猫物流", "trackingNo": "SF" + uuid.uuid4().hex[:10]})
    log("10. 商家发货并填写物流", code == 200, f"HTTP {code}")

    # 11. 用户确认收货
    code, conf = call("POST", f"/api/v1/orders/{order_id}/confirm", user_token)
    log("11. 用户确认收货", code == 200, f"HTTP {code}")

    # 12. 用户评价商品
    code, item = call("GET", f"/api/v1/orders/{order_id}", user_token)
    items = (item.get("data") or {}).get("items") or []
    if items:
        code, review = call("POST", "/api/v1/reviews", user_token, json={
            "orderItemId": items[0]["id"], "rating": 5,
            "content": "熊猫商城体验很棒，商品可爱、物流迅速，非常满意！"})
        log("12. 用户评价商品", code == 200, f"HTTP {code}")
    else:
        log("12. 用户评价商品", False, "未找到订单明细")

    # 14. 越权验证
    code, _ = call("GET", "/api/v1/admin/products", user_token)
    log("14.1 普通用户越权管理端被拒", code == 403, f"HTTP {code}（期望 403）")
    code, _ = call("GET", "/api/v1/merchant/orders", user_token)
    log("14.2 普通用户越权商家端被拒", code == 403, f"HTTP {code}（期望 403）")
    code, _ = call("GET", "/api/v1/orders")
    log("14.3 未登录访问订单被拒", code == 401, f"HTTP {code}（期望 401）")

    passed = sum(1 for _, ok, _ in results if ok)
    total = len(results)
    print(f"\n=== 演示结果：{passed}/{total} 步通过 ===")
    print(f"清理提示：DELETE FROM product WHERE name LIKE 'E2E-{tag}%';（连同其订单/评价按需清理）")


if __name__ == "__main__":
    main()
