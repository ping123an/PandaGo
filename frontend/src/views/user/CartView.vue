<template>
  <div class="cart-page container">
    <div class="page-head">
      <h1>我的购物车</h1>
      <span class="sub">价格仅供参考，成交价以订单提交时数据库价格为准</span>
    </div>

    <div v-if="!userStore.isLoggedIn" class="card state-card">
      <h2>请先登录</h2>
      <p>登录后即可查看和管理你的购物车。</p>
      <el-button type="primary" @click="goLogin">去登录</el-button>
    </div>

    <div v-else-if="loading" v-loading="loading" class="loading-box" />

    <div v-else-if="errorMessage" class="card state-card">
      <h2>加载失败</h2>
      <p>{{ errorMessage }}</p>
      <el-button type="primary" @click="loadCart">重试</el-button>
    </div>

    <div v-else-if="!cart || cart.items.length === 0" class="card state-card">
      <h2>购物车是空的</h2>
      <p>去挑选一些喜欢的熊猫好物吧。</p>
      <el-button type="primary" @click="router.push('/products')">去逛逛</el-button>
    </div>

    <div v-else class="cart-body">
      <div v-if="cart.invalidCount > 0" class="alert card">
        有 {{ cart.invalidCount }} 件商品需要重新确认（下架、未审核或库存变化），已不计入预估金额。
      </div>

      <div class="cart-list card">
        <div class="list-head">
          <el-checkbox :model-value="cart.allSelected" @change="toggleAll">全选</el-checkbox>
          <span class="col">商品</span>
          <span class="col">单价</span>
          <span class="col">数量</span>
          <span class="col">小计</span>
          <span class="col">操作</span>
        </div>

        <div v-for="group in groups" :key="group.key" class="merchant-group">
          <div class="merchant-head">
            <el-checkbox
              :model-value="group.allSelected"
              :indeterminate="group.someSelected"
              :disabled="group.availableItems.length === 0"
              @change="(value: boolean) => toggleGroup(group, value)"
            >
              本店全选
            </el-checkbox>
            <span class="shop-name">店铺：{{ group.merchantName }}</span>
            <span class="shop-hint">该店商品将单独生成一张订单</span>
          </div>

          <div
            v-for="item in group.items"
            :key="item.id"
            class="cart-row"
            :class="{ invalid: !item.available }"
          >
            <el-checkbox
              :model-value="item.selected"
              :disabled="!item.available"
              @change="(value: boolean) => updateItem(item, { selected: value })"
            />
            <div class="goods">
              <img :src="item.mainImage || '/images/panda-product.svg'" :alt="item.productName" />
              <div>
                <router-link :to="{ name: 'product-detail', params: { id: item.productId } }">
                  {{ item.productName }}
                </router-link>
                <p v-if="!item.available" class="reason">{{ item.invalidReason }}</p>
                <p v-else class="stock in-stock">有货</p>
              </div>
            </div>
            <span class="col price">¥{{ item.price == null ? '--' : item.price.toFixed(2) }}</span>
            <div class="col">
              <el-input-number
                :model-value="item.quantity"
                :min="1"
                :max="999"
                size="small"
                @change="(value: number) => changeQuantity(item, value)"
              />
              <p v-if="item.available && item.quantity >= item.stock" class="stock-limit">
                仅剩 {{ item.stock }} 件
              </p>
            </div>
            <span class="col price">¥{{ item.subtotal.toFixed(2) }}</span>
            <div class="col">
              <el-button type="danger" link size="small" @click="removeItem(item)">删除</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="summary card">
        <div class="summary-info">
          已选 {{ selectedAvailableCount }} 件 · 共 {{ totalQuantity }} 件商品，合计（预估）：
          <strong class="total">¥{{ cart.estimatedTotal.toFixed(2) }}</strong>
          <span class="hint"> 不含运费，最终金额在提交订单时计算</span>
        </div>
        <el-button type="primary" size="large" :disabled="selectedAvailableCount === 0" @click="checkout">
          去结算（{{ selectedAvailableCount }} 件）
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCart, removeCartItem, updateCartItem } from '@/api/cart'
import { useUserStore } from '@/stores/user'
import type { CartUpdatePayload, CartVO, CartItemVO } from '@/types/user-center'

interface MerchantGroup {
  key: string
  merchantId: number | null
  merchantName: string
  items: CartItemVO[]
  availableItems: CartItemVO[]
  allSelected: boolean
  someSelected: boolean
}

const router = useRouter()
const userStore = useUserStore()
const cart = ref<CartVO | null>(null)
const loading = ref(true)
const errorMessage = ref('')

/** 按真实商家分组，保持后端返回的条目顺序 */
const groups = computed<MerchantGroup[]>(() => {
  const map = new Map<string, CartItemVO[]>()
  for (const item of cart.value?.items ?? []) {
    const key = String(item.merchantId ?? 'platform')
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(item)
  }
  return [...map.entries()].map(([key, items]) => {
    const availableItems = items.filter((item) => item.available)
    const selectedCount = availableItems.filter((item) => item.selected).length
    return {
      key,
      merchantId: items[0].merchantId,
      merchantName: items[0].merchantName || '平台自营',
      items,
      availableItems,
      allSelected: availableItems.length > 0 && selectedCount === availableItems.length,
      someSelected: selectedCount > 0 && selectedCount < availableItems.length,
    }
  })
})

const selectedAvailableCount = computed(() =>
  (cart.value?.items ?? [])
    .filter((item) => item.selected && item.available)
    .reduce((sum, item) => sum + item.quantity, 0),
)

const totalQuantity = computed(() =>
  (cart.value?.items ?? []).reduce((sum, item) => sum + item.quantity, 0),
)

async function loadCart(): Promise<void> {
  if (!userStore.isLoggedIn) {
    loading.value = false
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await getCart()
    cart.value = data.data
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载失败'
    cart.value = null
  } finally {
    loading.value = false
  }
}

async function updateItem(item: CartItemVO, payload: CartUpdatePayload): Promise<void> {
  try {
    await updateCartItem(item.id, payload)
    await loadCart()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '修改失败')
    await loadCart()
  }
}

/** 数量变更前端先拦截越界，后端仍会以数据库库存二次校验 */
async function changeQuantity(item: CartItemVO, value: number): Promise<void> {
  if (value < 1) {
    ElMessage.error('商品数量不能小于 1')
    await loadCart()
    return
  }
  if (value > item.stock) {
    ElMessage.error(`「${item.productName}」库存不足，当前仅剩 ${item.stock} 件`)
    await loadCart()
    return
  }
  await updateItem(item, { quantity: value })
}

/** 全选 / 取消全选：只操作可购买条目，逐个调用单条接口，不额外增加后端接口 */
async function toggleAll(value: boolean): Promise<void> {
  const targets = (cart.value?.items ?? []).filter((item) => item.available && item.selected !== value)
  try {
    for (const item of targets) {
      await updateCartItem(item.id, { selected: value })
    }
    await loadCart()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
    await loadCart()
  }
}

/** 按商家全选 / 取消全选 */
async function toggleGroup(group: MerchantGroup, value: boolean): Promise<void> {
  const targets = group.availableItems.filter((item) => item.selected !== value)
  try {
    for (const item of targets) {
      await updateCartItem(item.id, { selected: value })
    }
    await loadCart()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
    await loadCart()
  }
}

async function removeItem(item: CartItemVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定移除「${item.productName}」吗？`, '提示', { type: 'warning' })
    await removeCartItem(item.id)
    ElMessage.success('已移除')
    await loadCart()
  } catch {
    // 用户取消确认，不做处理
  }
}

function checkout(): void {
  void router.push({ name: 'checkout' })
}

function goLogin(): void {
  router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
}

onMounted(loadCart)
</script>

<style scoped lang="scss">
.cart-page { padding-top: 24px; padding-bottom: 60px; }
.page-head { display: flex; align-items: baseline; gap: 16px; margin-bottom: 18px; }
.page-head h1 { margin: 0; font-size: 24px; }
.sub { color: var(--pd-muted); font-size: 12px; }
.loading-box { min-height: 320px; }
.state-card { padding: 56px; text-align: center; }
.state-card h2 { margin: 0 0 10px; }
.state-card p { margin: 0 0 22px; color: var(--pd-muted); }
.alert { margin-bottom: 14px; padding: 12px 18px; color: #b45309; background: #fffbeb; font-size: 13px; }
.cart-list { padding: 0 20px; }
.list-head,
.cart-row { display: grid; grid-template-columns: 44px minmax(0, 2.4fr) 90px 130px 100px 70px; align-items: center; gap: 12px; }
.list-head { height: 48px; border-bottom: 1px solid var(--pd-border); color: var(--pd-muted); font-size: 12px; }
.merchant-group { border-bottom: 1px solid var(--pd-border); }
.merchant-group:last-child { border-bottom: none; }
.merchant-head { display: flex; align-items: center; gap: 14px; padding: 12px 0; background: #fafafa; }
.shop-name { font-weight: 600; font-size: 14px; }
.shop-hint { color: var(--pd-muted); font-size: 12px; }
.cart-row { min-height: 96px; border-top: 1px solid var(--pd-border); }
.cart-row.invalid { background: #fffbeb; }
.goods { display: flex; align-items: center; gap: 14px; }
.goods img { width: 64px; height: 64px; border-radius: 6px; object-fit: cover; background: #f3f4f6; }
.goods a { font-size: 14px; font-weight: 600; }
.goods a:hover { color: var(--pd-primary); }
.reason { margin: 4px 0 0; color: #b45309; font-size: 12px; }
.stock { margin: 4px 0 0; color: var(--pd-muted); font-size: 12px; }
.stock.in-stock { color: #16a34a; font-weight: 600; }
.stock-limit { margin: 4px 0 0; color: #b45309; font-size: 12px; }
.col { font-size: 13px; }
.price { color: var(--pd-primary); font-weight: 600; }
.summary { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; padding: 20px; }
.summary-info { font-size: 14px; }
.total { color: var(--pd-primary); font-size: 24px; }
.hint { color: var(--pd-muted); font-size: 12px; }
@media (max-width: 900px) {
  .list-head { display: none; }
  .cart-row { grid-template-columns: 30px 1fr; gap: 8px; padding: 14px 0; }
  .cart-row .col { grid-column: 2; }
  .merchant-head { flex-wrap: wrap; }
  .summary { flex-direction: column; gap: 16px; }
}
</style>
