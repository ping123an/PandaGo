<template>
  <div class="admin-orders-page">
    <div class="page-head">
      <div>
        <h1>平台订单管理</h1>
        <p>全平台只读查询；管理员不绕过订单状态机随意改状态。</p>
      </div>
      <div class="filters">
        <el-input v-model="orderNo" class="filter-input" placeholder="按订单号搜索" clearable @keyup.enter="reload" />
        <el-select v-model="status" class="filter-select" clearable placeholder="全部状态">
          <el-option label="待支付" value="PENDING_PAYMENT" />
          <el-option label="待发货" value="PENDING_SHIPMENT" />
          <el-option label="待收货" value="PENDING_RECEIPT" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
        <el-button type="primary" @click="reload">查询</el-button>
      </div>
    </div>

    <div v-loading="loading" class="table-card card">
      <el-table :data="orders" empty-text="没有订单" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="orderNo" label="订单号" min-width="180" />
        <el-table-column label="商品" min-width="200">
          <template #default="{ row }">
            <span>{{ row.firstProductName || '—' }}</span>
            <small v-if="row.itemCount > 1"> 等 {{ row.itemCount }} 件</small>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">¥{{ Number(row.totalAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ row.statusDescription }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" min-width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > 0" class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @change="loadOrders"
        />
      </div>
    </div>

    <el-dialog v-model="detailDialog" title="订单详情" width="620px">
      <template v-if="detail">
        <p class="detail-meta">订单号：{{ detail.orderNo }} · 状态：{{ detail.statusDescription }}</p>
        <p class="detail-meta">收货：{{ detail.receiverName }} {{ detail.receiverPhone }}</p>
        <el-table :data="detail.items" size="small">
          <el-table-column prop="productName" label="商品" min-width="200" />
          <el-table-column prop="quantity" label="数量" width="70" />
          <el-table-column label="小计" width="120">
            <template #default="{ row }">¥{{ Number(row.subtotal).toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAdminOrders, getAdminOrderDetail } from '@/api/admin'
import type { Order, OrderDetail } from '@/types/admin'

const route = useRoute()
const loading = ref(false)
const orders = ref<Order[]>([])
const total = ref(0)
const orderNo = ref('')
const status = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const detailDialog = ref(false)
const detail = ref<OrderDetail | null>(null)

function statusTag(value: string): string {
  const map: Record<string, string> = {
    PENDING_PAYMENT: 'warning',
    PENDING_SHIPMENT: 'primary',
    PENDING_RECEIPT: 'purple',
    COMPLETED: 'success',
    CANCELLED: 'info',
  }
  return map[value] ?? 'info'
}

async function loadOrders(): Promise<void> {
  loading.value = true
  try {
    const { data } = await getAdminOrders({
      orderNo: orderNo.value || undefined,
      status: status.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    orders.value = data.data.list
    total.value = data.data.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载订单失败')
  } finally {
    loading.value = false
  }
}

function reload(): void {
  pageNum.value = 1
  void loadOrders()
}

/** 支持从平台工作台带 query 进入：status 自动筛选，detail 自动打开详情 */
function applyRouteQuery(): void {
  const queryStatus = route.query.status
  if (typeof queryStatus === 'string' && queryStatus) {
    status.value = queryStatus
  }
}

async function openDetail(id: number): Promise<void> {
  try {
    const { data } = await getAdminOrderDetail(id)
    detail.value = data.data
    detailDialog.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载详情失败')
  }
}

watch(() => route.query, () => {
  applyRouteQuery()
  reload()
  const raw = route.query.detail
  const id = Number(Array.isArray(raw) ? raw[0] : raw)
  if (id && Number.isFinite(id)) void openDetail(id)
})

onMounted(() => {
  applyRouteQuery()
  void loadOrders()
  const raw = route.query.detail
  const id = Number(Array.isArray(raw) ? raw[0] : raw)
  if (id && Number.isFinite(id)) void openDetail(id)
})
</script>

<style scoped lang="scss">
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;

  h1 { margin: 0 0 6px; font-size: 22px; }
  p { margin: 0; color: var(--pd-muted); font-size: 13px; }
}
.filters { display: flex; gap: 10px; }
.filter-input { width: 220px; }
.filter-select { width: 150px; }
.card { background: #fff; border: 1px solid var(--pd-border); border-radius: 12px; padding: 18px; }
.pagination { margin-top: 16px; justify-content: flex-end; display: flex; }
.detail-meta { margin: 6px 0; font-size: 13px; color: var(--pd-ink-soft); }
</style>
