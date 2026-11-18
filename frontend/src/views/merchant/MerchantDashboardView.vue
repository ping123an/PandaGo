<template>
  <div class="merchant-dashboard">
    <div class="page-head">
      <div>
        <h1>商家工作台</h1>
        <p>欢迎回来，{{ dashboard?.storeName || userStore.nickname }}。以下是你店铺的真实经营数据。</p>
      </div>
      <el-button :loading="loading" @click="loadDashboard">刷新数据</el-button>
    </div>

    <div v-if="loading" v-loading="loading" class="loading-box" />

    <div v-else-if="loadError" class="card state-card">
      <h2>数据加载失败</h2>
      <p>{{ loadError }}</p>
      <el-button type="primary" @click="loadDashboard">重新加载</el-button>
    </div>

    <template v-else-if="dashboard">
      <!-- 店铺状态条 -->
      <section class="card store-strip">
        <MerchantAvatar :size="52" :round="false" />
        <div class="store-info">
          <h2>{{ dashboard.storeName }}</h2>
          <p>店铺状态：<span class="tag" :class="{ paused: dashboard.storeStatus !== 'NORMAL' }">{{ dashboard.storeStatusDescription }}</span></p>
        </div>
        <el-button type="primary" plain @click="router.push({ name: 'merchant-profile' })">个人资料</el-button>
      </section>

      <!-- 经营数据统计 -->
      <section class="stat-grid">
        <div class="stat-card card">
          <span class="stat-label">商品总数</span>
          <strong class="stat-value">{{ dashboard.productTotal }}</strong>
          <span class="stat-foot">件商品</span>
        </div>
        <div class="stat-card card">
          <span class="stat-label">上架商品数</span>
          <strong class="stat-value">{{ dashboard.productOnSale }}</strong>
          <span class="stat-foot">已审核且上架</span>
        </div>
        <div class="stat-card card highlight">
          <span class="stat-label">待发货订单</span>
          <strong class="stat-value">{{ dashboard.pendingShipmentOrders }}</strong>
          <span class="stat-foot">需尽快处理</span>
        </div>
        <div class="stat-card card">
          <span class="stat-label">已完成订单</span>
          <strong class="stat-value">{{ dashboard.completedOrders }}</strong>
          <span class="stat-foot">累计完成</span>
        </div>
        <div class="stat-card card sales">
          <span class="stat-label">销售额</span>
          <strong class="stat-value">¥{{ dashboard.salesAmount.toFixed(2) }}</strong>
          <span class="stat-foot">已支付且未取消订单</span>
        </div>
      </section>

      <!-- 快捷操作 -->
      <section class="card block">
        <h2>快捷操作</h2>
        <div class="quick-grid">
          <button class="quick-item" @click="router.push({ name: 'merchant-products' })">
            <span class="quick-icon add">＋</span>
            <span><strong>添加商品</strong><small>在商品管理页点击新增</small></span>
          </button>
          <button class="quick-item" @click="router.push({ name: 'merchant-products' })">
            <span class="quick-icon">商</span>
            <span><strong>商品管理</strong><small>维护商品与上下架</small></span>
          </button>
          <button class="quick-item" @click="router.push({ name: 'merchant-orders' })">
            <span class="quick-icon">单</span>
            <span><strong>订单管理</strong><small>查看并处理订单</small></span>
          </button>
          <button class="quick-item" @click="router.push({ name: 'merchant-profile' })">
            <span class="quick-icon">店</span>
            <span><strong>商家资料</strong><small>维护店铺信息</small></span>
          </button>
        </div>
      </section>

      <div class="dual-grid">
        <!-- 最近订单 -->
        <section class="card block">
          <div class="block-head">
            <h2>最近订单</h2>
            <el-button link type="primary" @click="router.push({ name: 'merchant-orders' })">查看全部</el-button>
          </div>
          <div v-if="dashboard.recentOrders.length === 0" class="empty-state">暂无订单</div>
          <div v-for="order in dashboard.recentOrders" :key="order.id" class="order-row">
            <div class="order-main">
              <strong>{{ order.orderNo }}</strong>
              <span class="amount">¥{{ order.totalAmount.toFixed(2) }}</span>
            </div>
            <div class="order-sub">
              <span>{{ formatTime(order.createdAt) }}</span>
              <span class="status-tag" :class="statusClass(order.status)">{{ order.statusDescription }}</span>
            </div>
            <div class="order-buyer">买家：{{ order.buyerName }} {{ order.buyerPhoneMasked }}</div>
            <el-button link type="primary" @click="router.push({ name: 'merchant-orders', query: { detail: String(order.id) } })">
              查看订单详情
            </el-button>
          </div>
        </section>

        <!-- 商品概览 -->
        <section class="card block">
          <div class="block-head">
            <h2>商品概览</h2>
            <el-button link type="primary" @click="router.push({ name: 'merchant-products' })">管理商品</el-button>
          </div>
          <div v-if="dashboard.recentProducts.length === 0" class="empty-state">暂无商品，快去添加第一件商品吧</div>
          <button v-for="product in dashboard.recentProducts" :key="product.id" class="product-row" @click="goEditProduct(product.id)">
            <img :src="product.mainImage || '/images/panda-product.svg'" :alt="product.name" />
            <div class="product-info">
              <strong>{{ product.name }}</strong>
              <span>¥{{ product.price.toFixed(2) }} · 库存 {{ product.stock }} · 已售 {{ product.sales ?? 0 }}</span>
            </div>
            <span class="product-status" :class="product.status === 'ON_SALE' ? 'on' : 'off'">
              {{ product.status === 'ON_SALE' ? '已上架' : '已下架' }}
            </span>
          </button>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMerchantDashboard } from '@/api/merchant'
import MerchantAvatar from '@/components/MerchantAvatar.vue'
import { useUserStore } from '@/stores/user'
import type { MerchantDashboardResult } from '@/types/merchant'

const router = useRouter()
const userStore = useUserStore()
const dashboard = ref<MerchantDashboardResult | null>(null)
const loading = ref(true)
const loadError = ref('')

async function loadDashboard(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    dashboard.value = (await getMerchantDashboard()).data.data
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '统计信息加载失败'
    dashboard.value = null
  } finally {
    loading.value = false
  }
}

/** 商品概览点击进入现有商品管理页的编辑弹窗（通过 query 指定商品 ID） */
function goEditProduct(id: number): void {
  void router.push({ name: 'merchant-products', query: { edit: String(id) } })
}

function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function statusClass(status: string): string {
  const map: Record<string, string> = {
    PENDING_PAYMENT: 'warn',
    PENDING_SHIPMENT: 'danger',
    PENDING_RECEIPT: 'info',
    COMPLETED: 'success',
    CANCELLED: 'muted',
  }
  return map[status] ?? 'muted'
}

onMounted(loadDashboard)
</script>

<style scoped>
.merchant-dashboard { padding: 24px; background: #f5f6f8; min-height: calc(100vh - 68px); }
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.page-head h1 { margin: 0 0 6px; font-size: 22px; }
.page-head p { margin: 0; color: var(--pd-muted); font-size: 13px; }
.loading-box { min-height: 300px; }
.card { background: #fff; border: 1px solid var(--pd-border); border-radius: 12px; box-shadow: 0 2px 12px rgba(0,0,0,.04); }
.state-card { padding: 48px; text-align: center; }
.state-card p { color: var(--pd-muted); }
.store-strip { display: flex; align-items: center; gap: 16px; padding: 18px 22px; margin-bottom: 18px; }
.store-info { flex: 1; }
.store-info h2 { margin: 0 0 6px; font-size: 18px; }
.store-info p { margin: 0; font-size: 13px; color: var(--pd-muted); }
.tag { color: #16a34a; font-weight: 600; }
.tag.paused { color: #b45309; }
.stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 14px; margin-bottom: 18px; }
.stat-card { padding: 18px 20px; display: flex; flex-direction: column; gap: 6px; }
.stat-card.highlight { border-left: 3px solid var(--pd-primary); }
.stat-card.sales { background: linear-gradient(135deg, #fff 60%, #fff5f4); }
.stat-label { color: var(--pd-muted); font-size: 13px; }
.stat-value { font-size: 28px; color: var(--pd-ink); }
.stat-card.sales .stat-value { color: var(--pd-primary); }
.stat-foot { color: var(--pd-muted); font-size: 12px; }
.block { padding: 20px; margin-bottom: 18px; }
.block h2 { font-size: 16px; margin: 0; }
.block-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.quick-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; }
.quick-item { display: flex; align-items: center; gap: 12px; padding: 16px; border: 1px solid var(--pd-border); border-radius: 10px; background: #fff; cursor: pointer; text-align: left; }
.quick-item:hover { border-color: var(--pd-primary); background: #fff9f8; }
.quick-item span:last-child { display: flex; flex-direction: column; gap: 2px; }
.quick-item small { color: var(--pd-muted); }
.quick-icon { width: 38px; height: 38px; border-radius: 9px; background: #f3f4f6; color: var(--pd-ink); display: grid; place-items: center; font-weight: 700; }
.quick-icon.add { background: var(--pd-primary); color: #fff; }
.dual-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(360px, 1fr)); gap: 18px; align-items: start; }
.order-row { padding: 12px 0; border-top: 1px solid var(--pd-border); }
.order-main { display: flex; justify-content: space-between; font-size: 13px; }
.order-main .amount { color: var(--pd-primary); font-weight: 600; }
.order-sub { display: flex; justify-content: space-between; margin-top: 6px; font-size: 12px; color: var(--pd-muted); }
.order-buyer { margin-top: 4px; font-size: 12px; color: var(--pd-muted); }
.status-tag { padding: 1px 8px; border-radius: 10px; background: #f3f4f6; }
.status-tag.danger { color: #dc2626; background: #fef2f2; }
.status-tag.warn { color: #b45309; background: #fffbeb; }
.status-tag.success { color: #16a34a; background: #e8f7ee; }
.product-row { display: flex; align-items: center; gap: 12px; width: 100%; padding: 10px 0; border: none; border-top: 1px solid var(--pd-border); background: none; cursor: pointer; text-align: left; }
.product-row:hover { background: #fff9f8; }
.product-row img { width: 48px; height: 48px; border-radius: 6px; object-fit: cover; background: #f3f4f6; }
.product-info { flex: 1; display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
.product-info span { color: var(--pd-muted); font-size: 12px; }
.product-status { font-size: 12px; }
.product-status.on { color: #16a34a; }
.product-status.off { color: var(--pd-muted); }
.empty-state { padding: 32px; text-align: center; color: var(--pd-muted); font-size: 13px; }
@media (max-width: 640px) { .merchant-dashboard { padding: 14px; } .stat-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
