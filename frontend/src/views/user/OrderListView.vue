<template>
  <div class="orders-page container">
    <div class="page-head">
      <h1>我的订单</h1>
      <div class="filters">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 160px" @change="reload">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </div>
    </div>

    <div v-if="!userStore.isLoggedIn" class="card state-card">
      <h2>请先登录</h2>
      <p>登录后即可查看自己的订单。</p>
      <el-button type="primary" @click="goLogin">去登录</el-button>
    </div>

    <div v-else-if="loading" v-loading="loading" class="loading-box" />

    <div v-else-if="errorMessage" class="card state-card">
      <h2>加载失败</h2>
      <p>{{ errorMessage }}</p>
      <el-button type="primary" @click="loadOrders">重试</el-button>
    </div>

    <div v-else-if="orders.length === 0" class="card state-card">
      <h2>还没有订单</h2>
      <p>去挑选喜欢的商品，提交第一笔订单吧。</p>
      <el-button type="primary" @click="goShopping">去逛逛</el-button>
    </div>

    <div v-else class="order-list">
      <div v-for="order in orders" :key="order.id" class="card order-card">
        <div class="order-head">
          <span class="order-no">订单号：{{ order.orderNo }}</span>
          <span class="status" :class="statusClass(order.status)">{{ order.statusDescription }}</span>
        </div>
        <div class="order-body">
          <img :src="order.firstProductImage || '/images/panda-product.svg'" :alt="order.firstProductName" />
          <div class="goods-info">
            <strong>{{ order.firstProductName }}</strong>
            <p>共 {{ order.itemCount }} 件商品 · 下单时间 {{ formatTime(order.createdAt) }}</p>
          </div>
          <div class="order-amount">
            <span>实付金额</span>
            <strong>¥{{ order.totalAmount.toFixed(2) }}</strong>
          </div>
        </div>
        <div class="order-foot">
          <template v-if="order.status === 'PENDING_PAYMENT'">
            <el-button
              type="primary"
              size="small"
              :loading="payingIds.has(order.id)"
              :disabled="cancellingIds.has(order.id)"
              @click="payOrder(order)"
            >
              模拟支付
            </el-button>
            <el-button
              size="small"
              :loading="cancellingIds.has(order.id)"
              :disabled="payingIds.has(order.id)"
              @click="cancelOne(order)"
            >
              取消订单
            </el-button>
          </template>
          <el-button
            v-if="order.status === 'PENDING_RECEIPT'"
            type="primary"
            size="small"
            :loading="confirmingIds.has(order.id)"
            @click="confirmOne(order)"
          >
            确认收货
          </el-button>
          <el-button
            v-if="order.status === 'COMPLETED'"
            type="primary"
            size="small"
            @click="goReview(order.id)"
          >
            评价商品
          </el-button>
          <el-button size="small" @click="goDetail(order.id)">查看详情</el-button>
        </div>
      </div>

      <el-pagination
        v-if="total > pageSize"
        background
        layout="prev, pager, next"
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        @current-change="onPage"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrders, cancelOrder, confirmOrder } from '@/api/order'
import { useUserStore } from '@/stores/user'
import type { Order } from '@/types/order'

const router = useRouter()
const userStore = useUserStore()

const orders = ref<Order[]>([])
const payingIds = ref<Set<number>>(new Set())
const cancellingIds = ref<Set<number>>(new Set())
const confirmingIds = ref<Set<number>>(new Set())
const status = ref<string>('')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(true)
const errorMessage = ref('')

// 状态选项与后端枚举一致，避免前端硬编码中文映射
const statusOptions = [
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PENDING_SHIPMENT', label: '待发货' },
  { value: 'PENDING_RECEIPT', label: '待收货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
]

function statusClass(value: string) {
  return `status-${value.toLowerCase()}`
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

function goLogin() { void router.push({ name: 'login', query: { redirect: '/orders' } }) }
function goShopping() { void router.push({ name: 'home' }) }
function goDetail(id: number) { void router.push({ name: 'order-detail', params: { id } }) }
function goReview(id: number) { void router.push({ name: 'order-detail', params: { id }, hash: '#order-items' }) }

async function loadOrders() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getOrders({
      status: status.value || undefined,
      pageNum: pageNum.value,
      pageSize,
    })
    orders.value = response.data.data.list
    total.value = response.data.data.total
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载订单失败'
    ElMessage.error(errorMessage.value)
  } finally {
    loading.value = false
  }
}

function reload() { pageNum.value = 1; void loadOrders() }
function onPage(page: number) { pageNum.value = page; void loadOrders() }

function messageOf(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

/** 继续支付：进入支付页统一处理支付方式选择、确认与结果展示 */
function payOrder(order: Order) {
  if (payingIds.value.has(order.id) || cancellingIds.value.has(order.id)) return
  void router.push({ name: 'payment', params: { orderNo: order.orderNo } })
}

async function cancelOne(order: Order) {
  if (payingIds.value.has(order.id) || cancellingIds.value.has(order.id)) return
  try {
    await ElMessageBox.confirm(
      `确定取消订单 ${order.orderNo} 吗？取消后库存将恢复。`,
      '取消订单',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '暂不取消' },
    )
  } catch {
    return
  }
  cancellingIds.value.add(order.id)
  try {
    const response = await cancelOrder(order.id)
    order.status = response.data.data.status
    order.statusDescription = response.data.data.statusDescription
    ElMessage.success('订单已取消')
    await loadOrders()
  } catch (error) {
    ElMessage.error(messageOf(error, '取消失败，请稍后重试'))
  } finally {
    cancellingIds.value.delete(order.id)
  }
}

async function confirmOne(order: Order) {
  if (confirmingIds.value.has(order.id)) return
  try {
    await ElMessageBox.confirm(
      `确认已收到订单 ${order.orderNo} 的商品吗？确认后订单完成。`,
      '确认收货',
      { type: 'info', confirmButtonText: '确认收货', cancelButtonText: '再等等' },
    )
  } catch {
    return
  }
  confirmingIds.value.add(order.id)
  try {
    const response = await confirmOrder(order.id)
    order.status = response.data.data.status
    order.statusDescription = response.data.data.statusDescription
    ElMessage.success('确认收货成功')
    await loadOrders()
  } catch (error) {
    ElMessage.error(messageOf(error, '确认收货失败，请稍后重试'))
  } finally {
    confirmingIds.value.delete(order.id)
  }
}

onMounted(() => {
  if (userStore.isLoggedIn) void loadOrders()
  else loading.value = false
})
</script>

<style scoped>
.orders-page { padding: 24px 0 48px; }
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-head h1 { font-size: 24px; margin: 0; }
.card { background: #fff; border: 1px solid var(--pd-border); border-radius: 12px; }
.state-card { text-align: center; padding: 48px 20px; }
.state-card p { color: var(--pd-muted); }
.loading-box { min-height: 240px; }
.order-list { display: flex; flex-direction: column; gap: 14px; }
.order-card { padding: 16px 20px; }
.order-head { display: flex; justify-content: space-between; padding-bottom: 12px; border-bottom: 1px solid var(--pd-border); }
.order-no { color: var(--pd-muted); font-size: 13px; }
.status { color: var(--pd-primary); }
.status-pending_payment { color: #e1251b; }
.status-pending_shipment { color: #2563eb; }
.status-pending_receipt { color: #7c3aed; }
.status-cancelled { color: #999; }
.status-completed { color: #16a34a; }
.order-body { display: flex; gap: 14px; align-items: center; padding: 16px 0; }
.order-body img { width: 72px; height: 72px; object-fit: cover; border-radius: 8px; background: #f5f5f5; }
.goods-info { flex: 1; }
.goods-info p { color: var(--pd-muted); font-size: 13px; margin: 6px 0 0; }
.order-amount { text-align: right; }
.order-amount span { display: block; color: var(--pd-muted); font-size: 12px; }
.order-amount strong { color: var(--pd-primary); font-size: 18px; }
.order-foot { text-align: right; border-top: 1px solid var(--pd-border); padding-top: 12px; }
.order-foot :deep(.el-button + .el-button) { margin-left: 8px; }
:deep(.el-pagination) { margin-top: 20px; justify-content: flex-end; }
</style>
