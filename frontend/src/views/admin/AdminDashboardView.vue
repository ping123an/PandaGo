<template>
  <div class="admin-dashboard">
    <div class="page-head">
      <div>
        <h1>平台管理工作台</h1>
        <p>欢迎回来，{{ userStore.nickname }} · {{ todayText }}。以下是全平台真实运营数据。</p>
      </div>
      <el-button :loading="loading" @click="loadDashboard">
        <el-icon :size="15"><Refresh /></el-icon>
        <span>刷新数据</span>
      </el-button>
    </div>

    <div v-if="loading" v-loading="loading" class="loading-box" />

    <div v-else-if="loadError" class="card state-card">
      <el-icon :size="30" class="state-icon"><Warning /></el-icon>
      <h2>数据加载失败</h2>
      <p>{{ loadError }}</p>
      <el-button type="primary" @click="loadDashboard">重新加载</el-button>
    </div>

    <template v-else-if="dashboard">
      <!-- 运营统计 -->
      <section class="stat-grid">
        <div class="stat-card card">
          <span class="stat-label">用户总数</span>
          <strong class="stat-value">{{ dashboard.userTotal }}</strong>
          <span class="stat-foot">买家账号 · 平台账号共 {{ dashboard.accountTotal }}</span>
          <span class="stat-extra">今日新增 {{ dashboard.todayNewUsers }}</span>
        </div>
        <div class="stat-card card">
          <span class="stat-label">商品总数</span>
          <strong class="stat-value">{{ dashboard.productTotal }}</strong>
          <span class="stat-foot">全平台未删除商品</span>
          <span class="stat-extra">已上架 {{ dashboard.productOnSale }}</span>
        </div>
        <div class="stat-card card">
          <span class="stat-label">订单总数</span>
          <strong class="stat-value">{{ dashboard.orderTotal }}</strong>
          <span class="stat-foot">全部订单</span>
          <span class="stat-extra">今日新增 {{ dashboard.todayNewOrders }}</span>
        </div>
        <div class="stat-card card sales">
          <span class="stat-label">累计交易金额</span>
          <strong class="stat-value">¥{{ dashboard.totalSalesAmount.toFixed(2) }}</strong>
          <span class="stat-foot">模拟支付 · 已支付且未取消</span>
          <span class="stat-extra">已完成订单 {{ dashboard.completedOrders }}</span>
        </div>
      </section>

      <!-- 待处理事项 -->
      <section class="card block">
        <div class="block-head">
          <h2>待处理事项</h2>
          <span class="block-hint">点击卡片可直接进入处理页面</span>
        </div>
        <div class="task-grid">
          <button class="task-card" @click="goAudit('PENDING')">
            <span class="task-icon pending"><el-icon :size="20"><DocumentChecked /></el-icon></span>
            <span class="task-body">
              <strong>待审核商品</strong>
              <small>商家提交后等待平台处理</small>
            </span>
            <span class="task-count">{{ dashboard.pendingReviewProducts }}</span>
            <el-icon class="task-arrow"><ArrowRight /></el-icon>
          </button>
          <button class="task-card" @click="goOrders('PENDING_SHIPMENT')">
            <span class="task-icon shipping"><el-icon :size="20"><Tickets /></el-icon></span>
            <span class="task-body">
              <strong>待发货订单</strong>
              <small>买家已支付，等待商家发货</small>
            </span>
            <span class="task-count">{{ dashboard.pendingShipmentOrders }}</span>
            <el-icon class="task-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
      </section>

      <!-- 快捷操作 -->
      <section class="card block">
        <div class="block-head"><h2>快捷操作</h2></div>
        <div class="quick-grid">
          <button v-for="item in quickActions" :key="item.name" class="quick-item" @click="router.push({ name: item.route })">
            <span class="quick-icon"><el-icon :size="18"><component :is="item.icon" /></el-icon></span>
            <span class="quick-text">
              <strong>{{ item.name }}</strong>
              <small>{{ item.desc }}</small>
            </span>
          </button>
        </div>
      </section>

      <div class="dual-grid">
        <!-- 最近订单 -->
        <section class="card block">
          <div class="block-head">
            <h2>最近订单</h2>
            <el-button link type="primary" @click="goOrders()">查看全部</el-button>
          </div>
          <div v-if="dashboard.recentOrders.length === 0" class="empty-state">暂无订单</div>
          <div v-for="order in dashboard.recentOrders" :key="order.id" class="list-row">
            <div class="row-main">
              <strong>{{ order.orderNo }}</strong>
              <span class="amount">¥{{ order.totalAmount.toFixed(2) }}</span>
            </div>
            <div class="row-sub">
              <span class="product-text">{{ order.firstProductName || '—' }}<template v-if="order.itemCount > 1"> 等 {{ order.itemCount }} 件</template></span>
              <span class="status-tag" :class="statusClass(order.status)">{{ order.statusDescription }}</span>
            </div>
            <div class="row-sub">
              <span>{{ formatTime(order.createdAt) }}</span>
              <span>{{ order.buyerName }} {{ order.buyerPhoneMasked }}</span>
            </div>
            <el-button link type="primary" class="row-action" @click="goOrderDetail(order.id)">查看订单</el-button>
          </div>
        </section>

        <!-- 最新待审核商品 -->
        <section class="card block">
          <div class="block-head">
            <h2>最新待审核商品</h2>
            <el-button link type="primary" @click="goAudit('PENDING')">查看全部</el-button>
          </div>
          <div v-if="dashboard.pendingReviewProductList.length === 0" class="empty-state">目前没有待审核商品</div>
          <div v-for="product in dashboard.pendingReviewProductList" :key="product.id" class="product-row">
            <img :src="product.mainImage || '/images/panda-product.svg'" :alt="product.name" />
            <div class="product-info">
              <strong :title="product.name">{{ product.name }}</strong>
              <span>{{ product.merchantName }} · ¥{{ product.price.toFixed(2) }}</span>
              <span>提交于 {{ formatTime(product.createdAt) }}</span>
            </div>
            <el-button link type="primary" @click="goAudit('PENDING', product)">去审核</el-button>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  DocumentChecked,
  Goods,
  Grid,
  Refresh,
  Tickets,
  User,
  Warning,
} from '@element-plus/icons-vue'
import { getAdminDashboard } from '@/api/admin'
import { useUserStore } from '@/stores/user'
import type { AdminDashboardResult, AdminPendingProduct } from '@/types/admin'

const router = useRouter()
const userStore = useUserStore()
const dashboard = ref<AdminDashboardResult | null>(null)
const loading = ref(true)
const loadError = ref('')

const quickActions = [
  { name: '用户管理', desc: '查看账号与启用状态', route: 'admin-users', icon: markRaw(User) },
  { name: '分类管理', desc: '维护商品分类结构', route: 'admin-categories', icon: markRaw(Grid) },
  { name: '商品审核', desc: '处理商家提交申请', route: 'admin-audit', icon: markRaw(DocumentChecked) },
  { name: '商品管理', desc: '全平台商品与上下架', route: 'admin-products', icon: markRaw(Goods) },
  { name: '订单管理', desc: '查询全平台订单', route: 'admin-orders', icon: markRaw(Tickets) },
]

const todayText = computed(() => new Date().toLocaleDateString('zh-CN', {
  year: 'numeric', month: 'long', day: 'numeric', weekday: 'long',
}))

async function loadDashboard(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    dashboard.value = (await getAdminDashboard()).data.data
  } catch (error) {
    // 接口失败时清空数据，绝不能把失败误显示为 0
    dashboard.value = null
    loadError.value = error instanceof Error ? error.message : '工作台数据加载失败'
  } finally {
    loading.value = false
  }
}

function goAudit(auditStatus: 'PENDING', product?: AdminPendingProduct): void {
  void router.push({
    name: 'admin-audit',
    query: product
      ? { auditStatus, id: String(product.id), keyword: product.name }
      : { auditStatus },
  })
}

function goOrders(status?: string): void {
  void router.push({ name: 'admin-orders', query: status ? { status } : {} })
}

function goOrderDetail(id: number): void {
  void router.push({ name: 'admin-orders', query: { detail: String(id) } })
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
.admin-dashboard { padding: 24px; background: #f5f6f8; min-height: calc(100vh - 68px); }
.page-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.page-head h1 { margin: 0 0 6px; font-size: 22px; }
.page-head p { margin: 0; color: var(--pd-muted); font-size: 13px; }
.page-head .el-button { display: inline-flex; align-items: center; gap: 6px; }
.loading-box { min-height: 320px; }
.card { background: #fff; border: 1px solid var(--pd-border); border-radius: 12px; box-shadow: 0 2px 12px rgba(0, 0, 0, .04); }
.state-card { padding: 56px 24px; text-align: center; }
.state-card h2 { margin: 10px 0 6px; font-size: 18px; }
.state-card p { margin: 0 0 18px; color: var(--pd-muted); font-size: 13px; }
.state-icon { color: var(--pd-primary); }

.stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 14px; margin-bottom: 18px; }
.stat-card { display: flex; flex-direction: column; gap: 5px; padding: 18px 20px; }
.stat-card.sales { border-left: 3px solid var(--pd-primary); }
.stat-label { color: var(--pd-muted); font-size: 13px; }
.stat-value { color: var(--pd-ink); font-size: 27px; line-height: 1.2; }
.stat-card.sales .stat-value { color: var(--pd-primary); }
.stat-foot { color: var(--pd-muted); font-size: 12px; }
.stat-extra { color: var(--pd-primary); font-size: 12px; font-weight: 600; }

.block { padding: 20px; margin-bottom: 18px; }
.block h2 { margin: 0; font-size: 16px; }
.block-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.block-hint { color: var(--pd-muted); font-size: 12px; }
.task-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 12px; }
.task-card {
  display: flex; align-items: center; gap: 12px; width: 100%; padding: 16px;
  border: 1px solid var(--pd-border); border-radius: 10px; background: #fff; cursor: pointer; text-align: left;
}
.task-card:hover { border-color: var(--pd-primary); background: #fff9f8; }
.task-icon { width: 42px; height: 42px; border-radius: 10px; display: grid; place-items: center; flex-shrink: 0; }
.task-icon.pending { color: #b45309; background: #fffbeb; }
.task-icon.shipping { color: #dc2626; background: #fef2f2; }
.task-body { display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 0; }
.task-body strong { font-size: 14px; }
.task-body small { color: var(--pd-muted); font-size: 12px; }
.task-count { font-size: 24px; font-weight: 700; color: var(--pd-primary); }
.task-arrow { color: var(--pd-muted); }

.quick-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; }
.quick-item {
  display: flex; align-items: center; gap: 11px; padding: 14px;
  border: 1px solid var(--pd-border); border-radius: 10px; background: #fff; cursor: pointer; text-align: left;
}
.quick-item:hover { border-color: var(--pd-primary); background: #fff9f8; }
.quick-icon { width: 38px; height: 38px; border-radius: 9px; display: grid; place-items: center; background: #fff1f0; color: var(--pd-primary); flex-shrink: 0; }
.quick-text { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.quick-text strong { font-size: 13px; }
.quick-text small { color: var(--pd-muted); font-size: 11px; }

.dual-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(380px, 1fr)); gap: 18px; align-items: start; }
.empty-state { padding: 36px 12px; color: var(--pd-muted); font-size: 13px; text-align: center; }
.list-row { padding: 12px 0; border-top: 1px solid var(--pd-border); }
.row-main { display: flex; justify-content: space-between; gap: 10px; font-size: 13px; }
.row-main .amount { color: var(--pd-primary); font-weight: 600; }
.row-sub { display: flex; justify-content: space-between; gap: 10px; margin-top: 5px; color: var(--pd-muted); font-size: 12px; }
.product-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.status-tag { padding: 1px 8px; border-radius: 10px; background: #f3f4f6; flex-shrink: 0; }
.status-tag.danger { color: #dc2626; background: #fef2f2; }
.status-tag.warn { color: #b45309; background: #fffbeb; }
.status-tag.success { color: #16a34a; background: #e8f7ee; }
.row-action { padding: 4px 0 0; }

.product-row { display: flex; align-items: center; gap: 12px; width: 100%; padding: 10px 0; border-top: 1px solid var(--pd-border); }
.product-row img { width: 48px; height: 48px; border-radius: 6px; object-fit: cover; background: #f3f4f6; flex-shrink: 0; }
.product-info { display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 0; }
.product-info strong { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.product-info span { color: var(--pd-muted); font-size: 12px; }

@media (max-width: 900px) { .dual-grid { grid-template-columns: 1fr; } }
@media (max-width: 640px) {
  .admin-dashboard { padding: 14px; }
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .page-head { align-items: flex-start; flex-direction: column; }
}
</style>
