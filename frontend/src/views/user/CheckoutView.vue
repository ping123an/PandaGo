<template>
  <div class="checkout-page container">
    <div class="page-head">
      <h1>确认订单</h1>
      <span class="sub">{{ isBuyNow ? '立即购买不经过购物车，价格与库存由后端实时校验' : '前端金额仅为预览，最终成交价以后端提交结果为准' }}</span>
    </div>

    <div v-if="!userStore.isLoggedIn" class="card state-card">
      <h2>请先登录</h2>
      <p>登录后才能提交订单。</p>
      <el-button type="primary" @click="goLogin">去登录</el-button>
    </div>

    <div v-else-if="loading" v-loading="loading" class="loading-box" />

    <div v-else-if="errorMessage" class="card state-card">
      <h2>加载失败</h2>
      <p>{{ errorMessage }}</p>
      <el-button type="primary" @click="loadData">重试</el-button>
    </div>

    <div v-else class="checkout-body">
      <div class="main-col">
        <section class="card block">
          <h2>收货地址</h2>
          <div v-if="addresses.length === 0" class="empty-line">
            还没有收货地址，请先
            <router-link to="/addresses">添加地址</router-link>。
          </div>
          <div v-else class="address-list">
            <label
              v-for="addr in addresses"
              :key="addr.id"
              class="address-item"
              :class="{ active: selectedAddressId === addr.id }"
            >
              <input v-model="selectedAddressId" type="radio" :value="addr.id" name="address" />
              <div>
                <strong>{{ addr.receiverName }}</strong>
                <span class="phone">{{ addr.phone }}</span>
                <p>{{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detailAddress }}</p>
              </div>
              <span v-if="addr.isDefault" class="tag">默认</span>
            </label>
            <router-link to="/addresses" class="manage-address">+ 管理 / 新增地址</router-link>
          </div>
        </section>

        <section class="card block">
          <h2>确认商品</h2>
          <div v-if="displayItems.length === 0" class="empty-line">
            <template v-if="isBuyNow && addresses.length === 0">
              还没有收货地址，请先
              <router-link to="/addresses">添加收货地址</router-link> 后继续购买。
            </template>
            <template v-else>
              没有需要结算的商品，请返回
              <router-link to="/products">商品列表</router-link> 选择。
            </template>
          </div>
          <div v-for="group in merchantGroups" :key="group.key" class="goods-group">
            <div v-if="merchantGroups.length > 1" class="goods-group-head">
              <span class="shop">店铺：{{ group.merchantName }}</span>
              <span class="split-tag">独立成单</span>
            </div>
            <div v-for="item in group.items" :key="String(item.productId)" class="goods-row">
              <img :src="item.productImage || '/images/panda-product.svg'" :alt="item.productName" />
              <div class="goods-info">
                <strong>{{ item.productName }}</strong>
                <p class="spec">{{ item.specText }}</p>
                <p>单价 ¥{{ item.unitPrice.toFixed(2) }} × {{ item.quantity }}</p>
              </div>
              <span class="subtotal">¥{{ item.subtotal.toFixed(2) }}</span>
            </div>
            <div class="goods-group-foot">本店小计：<strong>¥{{ group.subtotal.toFixed(2) }}</strong></div>
          </div>
          <p v-if="merchantGroups.length > 1" class="split-tip">不同店铺的商品将自动拆分为多张订单，一次提交即可全部下单。</p>
        </section>

        <section class="card block">
          <h2>订单备注</h2>
          <el-input v-model="remark" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="选填，最多 200 字" />
        </section>
      </div>

      <aside class="side-col">
        <section class="card summary">
          <h2>结算明细</h2>
          <div class="sum-row">
            <span>已选商品种类</span>
            <span>{{ displayItems.length }} 种</span>
          </div>
          <div class="sum-row">
            <span>商品总数量</span>
            <span>{{ totalQuantity }} 件</span>
          </div>
          <div class="sum-row">
            <span>商品总金额</span>
            <span>¥{{ totalAmount.toFixed(2) }}</span>
          </div>
          <div class="sum-row">
            <span>优惠金额</span>
            <span class="discount">-¥{{ discountAmount.toFixed(2) }}</span>
          </div>
          <div class="sum-row">
            <span>运费</span>
            <span>¥{{ freightAmount.toFixed(2) }}</span>
          </div>
          <div class="sum-row final">
            <span>实际应付金额</span>
            <strong class="amount">¥{{ payableAmount.toFixed(2) }}</strong>
          </div>
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="submitting"
            :disabled="!canSubmit"
            @click="submitOrder"
          >
            提交订单
          </el-button>
          <p class="tip">提交后进入支付页。库存不足或商品下架会导致下单失败；立即购买不改动购物车中的其他商品。</p>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCart } from '@/api/cart'
import { getAddresses } from '@/api/address'
import { createOrder, previewOrder } from '@/api/order'
import { useUserStore } from '@/stores/user'
import type { AddressVO } from '@/types/user-center'
import type { OrderPreviewItem, OrderPreviewResult } from '@/types/order'

interface GoodsGroup {
  key: string
  merchantName: string
  items: OrderPreviewItem[]
  subtotal: number
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const errorMessage = ref('')
const submitting = ref(false)
const remark = ref('')
const addresses = ref<AddressVO[]>([])
const selectedAddressId = ref<number | null>(null)
const buyNowItems = ref<OrderPreviewItem[]>([])
const cartItems = ref<OrderPreviewItem[]>([])
const cartItemIds = ref<number[]>([])
const previewMeta = ref<OrderPreviewResult | null>(null)
const cartPreviewTotal = ref(0)
/** 结算幂等键：本次结算页生命周期内固定，失败重试复用，防止重复建单 */
const checkoutGroupId = ref<string>(`co-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`)

const isBuyNow = computed(() => route.name === 'checkout-buy-now')

/** 立即购买：后端预览返回的条目；购物车：本地购物车条目映射成的统一结构 */
const displayItems = computed<OrderPreviewItem[]>(() =>
  isBuyNow.value ? buyNowItems.value : cartItems.value,
)

const totalQuantity = computed(() => displayItems.value.reduce((sum, item) => sum + item.quantity, 0))
const totalAmount = computed(() =>
  isBuyNow.value && previewMeta.value ? previewMeta.value.totalAmount : cartPreviewTotal.value,
)
const discountAmount = computed(() =>
  isBuyNow.value && previewMeta.value ? previewMeta.value.discountAmount : 0,
)
const freightAmount = computed(() =>
  isBuyNow.value && previewMeta.value ? previewMeta.value.freightAmount : 0,
)
const payableAmount = computed(() =>
  isBuyNow.value && previewMeta.value
    ? previewMeta.value.payableAmount
    : Math.max(0, cartPreviewTotal.value - discountAmount.value + freightAmount.value),
)

const merchantGroups = computed<GoodsGroup[]>(() => {
  const map = new Map<string, OrderPreviewItem[]>()
  for (const item of displayItems.value) {
    const key = String(item.merchantId ?? 'platform')
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(item)
  }
  return [...map.entries()].map(([key, items]) => ({
    key,
    merchantName: items[0].merchantName || '平台自营',
    items,
    subtotal: items.reduce((sum, item) => sum + item.subtotal, 0),
  }))
})

const canSubmit = computed(
  () => selectedAddressId.value != null && displayItems.value.length > 0 && !submitting.value,
)

function goLogin() {
  void router.push({ name: 'login', query: { redirect: route.fullPath } })
}

/** 立即购买解析路由参数：只取商品 ID 与数量，价格/商家/库存交给后端预览 */
function buyNowIntent(): Array<{ productId: number; quantity: number }> {
  const productId = Number(route.query.productId)
  const quantity = Number(route.query.quantity ?? 1)
  if (!Number.isFinite(productId) || productId <= 0 || !Number.isFinite(quantity) || quantity < 1) {
    return []
  }
  return [{ productId, quantity: Math.min(999, Math.floor(quantity)) }]
}

async function loadData() {
  loading.value = true
  errorMessage.value = ''
  try {
    const addrRes = await getAddresses()
    addresses.value = addrRes.data.data
    const def = addresses.value.find((a) => a.isDefault)
    selectedAddressId.value = def ? def.id : addresses.value[0]?.id ?? null

    if (isBuyNow.value) {
      const intent = buyNowIntent()
      if (intent.length === 0) {
        errorMessage.value = '购买参数无效，请从商品详情页重新发起立即购买'
        return
      }
      if (selectedAddressId.value == null) {
        // 无地址：仍展示商品，但提示先添加地址；用默认占位地址 id=0 让后端返回明确错误
        buyNowItems.value = []
        return
      }
      const res = await previewOrder({ source: 'DIRECT', addressId: selectedAddressId.value, items: intent })
      previewMeta.value = res.data.data
      buyNowItems.value = res.data.data.items
    } else {
      const cartRes = await getCart()
      const selected = cartRes.data.data.items.filter((i) => i.selected && i.available)
      cartItemIds.value = selected.map((i) => i.id)
      cartItems.value = selected.map((i) => ({
        productId: i.productId,
        productName: i.productName,
        productImage: i.mainImage,
        specText: '默认规格（单规格商品）',
        unitPrice: i.price ?? 0,
        quantity: i.quantity,
        subtotal: i.subtotal,
        merchantId: i.merchantId,
        merchantName: i.merchantName,
      }))
      cartPreviewTotal.value = cartRes.data.data.estimatedTotal
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载结算信息失败'
  } finally {
    loading.value = false
  }
}

/** 地址切换后重新预览（立即购买依赖地址做归属校验） */
watch(selectedAddressId, (value) => {
  if (isBuyNow.value && value != null) void loadData()
})

async function submitOrder() {
  if (submitting.value || selectedAddressId.value == null || displayItems.value.length === 0) return
  submitting.value = true
  try {
    const payload = isBuyNow.value
      ? {
          addressId: selectedAddressId.value,
          source: 'DIRECT' as const,
          items: buyNowIntent(),
          remark: remark.value || undefined,
        }
      : {
          addressId: selectedAddressId.value,
          source: 'CART' as const,
          cartItemIds: cartItemIds.value,
          remark: remark.value || undefined,
          checkoutGroupId: checkoutGroupId.value,
        }
    const response = await createOrder(payload)
    const result = response.data.data
    const count = result.orderCount ?? 1
    ElMessage.success(
      count > 1
        ? `订单提交成功，本次购买已根据商家拆分为 ${count} 个订单。`
        : '订单提交成功，请完成支付。',
    )
    await router.push({
      name: 'payment',
      params: { orderNo: result.orderNo },
      query: {
        orderCount: String(count),
        amount: String(result.grandTotal ?? result.totalAmount),
      },
    })
  } catch (error) {
    // 失败不清空购物车、不重置幂等键：用户可直接重试
    ElMessage.error(error instanceof Error ? error.message : '提交订单失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (userStore.isLoggedIn) void loadData()
  else loading.value = false
})
</script>

<style scoped>
.checkout-page { padding: 24px 0 48px; background: #f5f6f8; min-height: calc(100vh - 64px); }
.page-head { display: flex; align-items: baseline; gap: 16px; margin-bottom: 20px; }
.page-head h1 { font-size: 24px; margin: 0; }
.sub { color: var(--pd-muted); font-size: 13px; }
.card { background: #fff; border: 1px solid var(--pd-border); border-radius: 12px; padding: 20px; box-shadow: 0 2px 12px rgba(0, 0, 0, .04); }
.state-card { text-align: center; padding: 48px 20px; }
.state-card p { color: var(--pd-muted); }
.loading-box { min-height: 240px; }
.checkout-body { display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: 20px; align-items: start; }
.main-col { min-width: 0; }
.block { margin-bottom: 16px; }
.block h2, .summary h2 { font-size: 16px; margin: 0 0 16px; }
.empty-line { color: var(--pd-muted); padding: 12px 0; }
.address-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px; }
.address-item { display: flex; gap: 10px; align-items: flex-start; padding: 14px; border: 1px solid var(--pd-border); border-radius: 8px; cursor: pointer; }
.address-item.active { border-color: var(--pd-primary); background: #fff5f4; }
.address-item .phone { margin-left: 8px; color: var(--pd-muted); }
.address-item p { margin: 6px 0 0; color: var(--pd-muted); font-size: 13px; }
.address-item .tag { margin-left: auto; color: var(--pd-primary); font-size: 12px; }
.manage-address { display: inline-flex; align-items: center; padding: 14px; border: 1px dashed var(--pd-border); border-radius: 8px; color: var(--pd-primary); font-size: 13px; }
.goods-group { border: 1px solid var(--pd-border); border-radius: 10px; padding: 14px 16px; margin-bottom: 14px; }
.goods-group-head { display: flex; align-items: center; gap: 10px; padding-bottom: 10px; border-bottom: 1px solid var(--pd-border); }
.goods-group-head .shop { font-weight: 600; font-size: 14px; }
.split-tag { color: var(--pd-primary); background: #fff5f4; border-radius: 4px; padding: 2px 8px; font-size: 12px; }
.goods-row { display: flex; align-items: center; gap: 14px; padding: 12px 0; }
.goods-row img { width: 64px; height: 64px; object-fit: cover; border-radius: 8px; background: #f5f5f5; }
.goods-info { flex: 1; }
.goods-info p { color: var(--pd-muted); font-size: 13px; margin: 6px 0 0; }
.goods-info p.spec { color: var(--pd-muted); font-size: 12px; }
.subtotal { color: var(--pd-primary); font-weight: 500; }
.goods-group-foot { text-align: right; padding-top: 10px; border-top: 1px solid var(--pd-border); font-size: 13px; }
.goods-group-foot strong { color: var(--pd-primary); }
.split-tip { color: var(--pd-muted); font-size: 12px; line-height: 1.6; margin: 4px 0 0; }
.side-col { position: sticky; top: 20px; }
.summary .sum-row { display: flex; justify-content: space-between; padding: 9px 0; font-size: 14px; color: #333; }
.summary .sum-row.final { border-top: 1px solid var(--pd-border); margin-top: 8px; padding-top: 14px; align-items: center; }
.summary .discount { color: #16a34a; }
.amount { color: var(--pd-primary); font-size: 24px; }
.submit-btn { width: 100%; margin-top: 16px; }
.tip { color: var(--pd-muted); font-size: 12px; line-height: 1.6; margin: 12px 0 0; }
@media (max-width: 1000px) {
  .checkout-body { grid-template-columns: 1fr; }
  .side-col { position: static; }
}
</style>
