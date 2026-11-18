<template>
  <div class="merchant-orders-page">
    <div class="page-head">
      <div>
        <h1>商家订单管理</h1>
        <p>仅展示归属当前商家的订单；发货需填写物流公司与单号，状态由后端判定。</p>
      </div>
      <el-select v-model="filters.status" class="status-filter" clearable placeholder="全部状态">
        <el-option label="待支付" value="PENDING_PAYMENT" />
        <el-option label="待发货" value="PENDING_SHIPMENT" />
        <el-option label="待收货" value="PENDING_RECEIPT" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
    </div>

    <div v-loading="loading" class="table-card card">
      <el-table :data="orders" empty-text="暂无商家订单" stripe>
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
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'PENDING_SHIPMENT'"
              link
              type="success"
              @click="openShip(row)"
            >发货</el-button>
            <el-button
              v-if="row.status === 'PENDING_SHIPMENT' || row.status === 'PENDING_RECEIPT'"
              link
              @click="viewShipment(row)"
            >物流</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > 0" class="pagination">
        <el-pagination
          v-model:current-page="filters.pageNum"
          v-model:page-size="filters.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @change="loadOrders"
        />
      </div>
    </div>

    <!-- 发货表单弹窗 -->
    <el-dialog v-model="shipDialog" title="填写物流信息" width="460px">
      <p class="dialog-hint">订单 {{ shipTarget?.orderNo }} · 仅保存公司与单号，不接入真实物流轨迹。</p>
      <el-form :model="shipForm" label-width="90px">
        <el-form-item label="物流公司" required>
          <el-input v-model="shipForm.company" placeholder="例如：熊猫速递" maxlength="50" />
        </el-form-item>
        <el-form-item label="物流单号" required>
          <el-input v-model="shipForm.trackingNo" placeholder="至少 4 个字符" maxlength="64" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialog = false">取消</el-button>
        <el-button type="primary" :loading="shipSubmitting" @click="submitShip">确认发货</el-button>
      </template>
    </el-dialog>

    <!-- 订单详情弹窗 -->
    <el-dialog v-model="detailDialog" title="订单详情" width="620px">
      <template v-if="detail">
        <p class="detail-meta">订单号：{{ detail.orderNo }} · 状态：{{ detail.statusDescription }}</p>
        <p class="detail-meta">
          收货：{{ detail.receiverName }} {{ detail.receiverPhone }} ·
          {{ detail.receiverProvince }}{{ detail.receiverCity }}{{ detail.receiverDistrict }}{{ detail.receiverDetailAddress }}
        </p>
        <el-table :data="detail.items" size="small">
          <el-table-column prop="productName" label="商品" min-width="200" />
          <el-table-column prop="quantity" label="数量" width="70" />
          <el-table-column label="单价" width="100">
            <template #default="{ row }">¥{{ Number(row.unitPrice).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="小计" width="100">
            <template #default="{ row }">¥{{ Number(row.subtotal).toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>

    <!-- 物流信息弹窗 -->
    <el-dialog v-model="shipmentDialog" title="物流信息" width="420px">
      <template v-if="shipment">
        <p class="detail-meta">订单号：{{ shipment.orderNo }}</p>
        <p class="detail-meta">物流公司：{{ shipment.company }}</p>
        <p class="detail-meta">物流单号：{{ shipment.trackingNo }}</p>
        <p class="detail-meta">发货时间：{{ shipment.shippedAt || '—' }}</p>
        <p class="dialog-hint">本项目不接入真实物流平台，暂无轨迹数据，不展示伪造轨迹。</p>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMerchantOrders, getMerchantOrderDetail } from '@/api/merchant-order'
import { getMerchantShipment, shipOrder } from '@/api/shipment'
import type { Order, OrderDetail } from '@/types/order'
import type { Shipment, ShipmentPayload } from '@/types/shipment'

const loading = ref(false)
const orders = ref<Order[]>([])
const total = ref(0)
const filters = reactive({ status: '', pageNum: 1, pageSize: 10 })

const shipDialog = ref(false)
const shipSubmitting = ref(false)
const shipTarget = ref<Order | null>(null)
const shipForm = reactive<ShipmentPayload>({ company: '', trackingNo: '' })

const detailDialog = ref(false)
const detail = ref<OrderDetail | null>(null)
const shipmentDialog = ref(false)
const shipment = ref<Shipment | null>(null)
const route = useRoute()

function statusTag(status: string): string {
  const map: Record<string, string> = {
    PENDING_PAYMENT: 'warning',
    PENDING_SHIPMENT: 'primary',
    PENDING_RECEIPT: 'purple',
    COMPLETED: 'success',
    CANCELLED: 'info',
  }
  return map[status] ?? 'info'
}

async function loadOrders(): Promise<void> {
  loading.value = true
  try {
    const { data } = await getMerchantOrders({
      status: filters.status || undefined,
      pageNum: filters.pageNum,
      pageSize: filters.pageSize,
    })
    orders.value = data.data.list
    total.value = data.data.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载订单失败')
  } finally {
    loading.value = false
  }
}

function openShip(row: Order): void {
  shipTarget.value = row
  shipForm.company = ''
  shipForm.trackingNo = ''
  shipDialog.value = true
}

async function submitShip(): Promise<void> {
  if (!shipTarget.value) return
  if (!shipForm.company.trim() || shipForm.trackingNo.trim().length < 4) {
    ElMessage.warning('请填写物流公司与至少 4 位的物流单号')
    return
  }
  shipSubmitting.value = true
  try {
    await shipOrder(shipTarget.value.id, {
      company: shipForm.company.trim(),
      trackingNo: shipForm.trackingNo.trim(),
    })
    ElMessage.success('发货成功，订单已进入待收货')
    shipDialog.value = false
    await loadOrders()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发货失败')
  } finally {
    shipSubmitting.value = false
  }
}

async function openDetail(row: Order): Promise<void> {
  try {
    const { data } = await getMerchantOrderDetail(row.id)
    detail.value = data.data
    detailDialog.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载详情失败')
  }
}

async function viewShipment(row: Order): Promise<void> {
  try {
    const { data } = await getMerchantShipment(row.id)
    shipment.value = data.data
    shipmentDialog.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '暂无物流信息')
  }
}

/** 工作台“最近订单”点击后通过 query.detail 指定订单 ID，进入本页自动打开详情弹窗 */
function openDetailFromQuery(): void {
  const raw = route.query.detail
  const detailId = Number(Array.isArray(raw) ? raw[0] : raw)
  if (!detailId || !Number.isFinite(detailId)) return
  const target = orders.value.find((item) => item.id === detailId)
  if (target) {
    void openDetail(target)
  }
}

onMounted(async () => {
  await loadOrders()
  openDetailFromQuery()
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
.status-filter { width: 160px; }
.card { background: #fff; border: 1px solid var(--pd-border); border-radius: 12px; padding: 18px; }
.pagination { margin-top: 16px; justify-content: flex-end; display: flex; }
.dialog-hint { margin: 0 0 14px; color: var(--pd-muted); font-size: 12px; line-height: 1.6; }
.detail-meta { margin: 6px 0; font-size: 13px; color: var(--pd-ink-soft); }
</style>
