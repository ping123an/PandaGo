<template>
  <div class="detail-page container">
    <div class="page-head">
      <h1>订单详情</h1>
      <div class="head-actions">
        <template v-if="order?.status === 'PENDING_PAYMENT'">
          <el-button type="primary" :loading="paying" :disabled="cancelling" @click="payCurrent">模拟支付</el-button>
          <el-button :loading="cancelling" :disabled="paying" @click="cancelCurrent">取消订单</el-button>
        </template>
        <el-button
          v-if="order?.status === 'PENDING_RECEIPT'"
          type="primary"
          :loading="confirming"
          @click="confirmCurrent"
        >确认收货</el-button>
        <el-button text @click="goBack">返回列表</el-button>
      </div>
    </div>

    <div v-if="loading" v-loading="loading" class="loading-box" />

    <div v-else-if="errorMessage" class="card state-card">
      <h2>加载失败</h2>
      <p>{{ errorMessage }}</p>
      <el-button type="primary" @click="loadDetail">重试</el-button>
    </div>

    <div v-else-if="order" class="detail-body">
      <section class="card block">
        <div class="order-meta">
          <div><span>订单号</span><strong>{{ order.orderNo }}</strong></div>
          <div><span>订单状态</span><strong class="status">{{ order.statusDescription }}</strong></div>
          <div><span>下单时间</span><span>{{ order.createdAt }}</span></div>
          <div v-if="order.remark"><span>备注</span><span>{{ order.remark }}</span></div>
        </div>
      </section>

      <section v-if="payment" class="card block">
        <h2>支付结果</h2>
        <div v-if="payment.paid" class="payment-grid">
          <div><span>支付状态</span><strong class="paid">支付成功</strong></div>
          <div><span>支付流水号</span><strong>{{ payment.paymentNo }}</strong></div>
          <div><span>实付金额</span><strong>¥{{ payment.payAmount.toFixed(2) }}</strong></div>
          <div><span>支付时间</span><span>{{ formatTime(payment.paidAt) }}</span></div>
        </div>
        <p v-else class="tip">订单尚未支付；实付金额以后端订单金额为准。</p>
      </section>

      <!-- 物流信息：仅公司与单号，明确无轨迹 -->
      <section v-if="shipment" class="card block">
        <h2>物流信息</h2>
        <div class="payment-grid">
          <div><span>物流公司</span><strong>{{ shipment.company }}</strong></div>
          <div><span>物流单号</span><strong>{{ shipment.trackingNo }}</strong></div>
          <div><span>发货时间</span><span>{{ formatTime(shipment.shippedAt ?? null) }}</span></div>
        </div>
        <p class="tip">本项目不接入真实物流平台，无轨迹数据，不展示伪造的物流进度。</p>
      </section>

      <section class="card block">
        <h2>收货信息（下单快照）</h2>
        <p class="address">
          {{ order.receiverName }} · {{ order.receiverPhone }}<br />
          {{ order.receiverProvince }} {{ order.receiverCity }} {{ order.receiverDistrict }} {{ order.receiverDetailAddress }}
        </p>
        <p class="tip">地址与商品信息均为下单时固化的快照，不会因后续修改而变化。</p>
      </section>

      <section id="order-items" class="card block">
        <h2>商品明细（下单快照）<small class="review-hint">— 可对商品发表评价</small></h2>
        <div v-for="item in order.items" :key="item.id" class="goods-row">
          <img :src="item.productImage || '/images/panda-product.svg'" :alt="item.productName" />
          <div class="goods-info">
            <strong>{{ item.productName }}</strong>
            <p>单价 ¥{{ item.unitPrice.toFixed(2) }} × {{ item.quantity }}</p>
          </div>
          <el-button
            v-if="order.status === 'COMPLETED' && reviewedItemIds.has(item.id)"
            size="small"
            type="success"
            plain
            disabled
          >已评价</el-button>
          <el-button
            v-else-if="order.status === 'COMPLETED'"
            size="small"
            type="primary"
            @click="openReview(item)"
          >评价</el-button>
          <span class="subtotal">¥{{ item.subtotal.toFixed(2) }}</span>
        </div>
      </section>

      <section class="card block total-block">
        <span>订单总金额</span>
        <strong>¥{{ order.totalAmount.toFixed(2) }}</strong>
      </section>
    </div>

    <ReviewDialog
      v-model:visible="reviewDialogVisible"
      :order-no="order?.orderNo ?? ''"
      :item="reviewTarget || fallbackItem"
      @submitted="onReviewed"
    />
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderDetail, cancelOrder, confirmOrder } from '@/api/order'
import { getPaymentResult } from '@/api/payment'
import { getMyShipment } from '@/api/shipment'
import { getMyReviews } from '@/api/review'
import ReviewDialog from '@/components/ReviewDialog.vue'
import type { OrderDetail, OrderItem } from '@/types/order'
import type { PaymentResult } from '@/types/payment'
import type { Shipment } from '@/types/shipment'

const route = useRoute()
const router = useRouter()

const order = ref<OrderDetail | null>(null)
const payment = ref<PaymentResult | null>(null)
const shipment = ref<Shipment | null>(null)
const loading = ref(true)
const paying = ref(false)
const cancelling = ref(false)
const confirming = ref(false)
const errorMessage = ref('')

const reviewDialogVisible = ref(false)
const reviewTarget = ref<OrderItem | null>(null)
const reviewedItemIds = ref<Set<number>>(new Set())
const fallbackItem: OrderItem = {
  id: 0,
  productId: 0,
  productName: '',
  productImage: '',
  unitPrice: 0,
  quantity: 0,
  subtotal: 0,
}

function formatTime(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

function messageOf(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

function goBack() { void router.push({ name: 'orders' }) }

async function loadPayment(orderNo: string) {
  try {
    payment.value = (await getPaymentResult(orderNo)).data.data
  } catch {
    payment.value = null
  }
}

/** 物流仅待收货/已完成时有意义；未发货时不打扰用户 */
async function loadShipment(id: number) {
  shipment.value = null
  try {
    shipment.value = (await getMyShipment(id)).data.data
  } catch {
    shipment.value = null
  }
}

/** 已完成订单：查询我的评价中已评价的订单明细，避免重复评价入口 */
async function loadReviewedItems() {
  reviewedItemIds.value = new Set()
  try {
    const { data } = await getMyReviews(1, 100)
    reviewedItemIds.value = new Set(data.data.list.map((r) => r.orderItemId))
  } catch {
    reviewedItemIds.value = new Set()
  }
}

async function loadDetail() {
  loading.value = true
  errorMessage.value = ''
  try {
    const id = Number(route.params.id)
    const response = await getOrderDetail(id)
    order.value = response.data.data
    await Promise.all([
      loadPayment(order.value.orderNo),
      loadShipment(order.value.id),
      order.value.status === 'COMPLETED' ? loadReviewedItems() : Promise.resolve(),
    ])
  } catch (error) {
    errorMessage.value = messageOf(error, '加载订单详情失败')
    ElMessage.error(errorMessage.value)
  } finally {
    loading.value = false
  }
  // 来自订单列表「评价商品」入口：滚动到商品明细，让评价按钮直接进入视野
  if (route.hash === '#order-items') {
    await nextTick()
    document.getElementById('order-items')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

/** 继续支付：进入支付页统一处理 */
function payCurrent() {
  if (!order.value || paying.value || cancelling.value) return
  void router.push({ name: 'payment', params: { orderNo: order.value.orderNo } })
}

async function cancelCurrent() {
  if (!order.value || paying.value || cancelling.value) return
  try {
    await ElMessageBox.confirm(
      `确定取消订单 ${order.value.orderNo} 吗？取消后库存将恢复。`,
      '取消订单',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '暂不取消' },
    )
  } catch {
    return
  }
  cancelling.value = true
  try {
    const response = await cancelOrder(order.value.id)
    if (order.value) {
      order.value.status = response.data.data.status
      order.value.statusDescription = response.data.data.statusDescription
    }
    ElMessage.success('订单已取消')
    await loadDetail()
  } catch (error) {
    ElMessage.error(messageOf(error, '取消失败，请稍后重试'))
  } finally {
    cancelling.value = false
  }
}

async function confirmCurrent() {
  if (!order.value || confirming.value) return
  try {
    await ElMessageBox.confirm(
      `确认已收到订单 ${order.value.orderNo} 的商品吗？确认后订单完成，可对商品评价。`,
      '确认收货',
      { type: 'info', confirmButtonText: '确认收货', cancelButtonText: '再等等' },
    )
  } catch {
    return
  }
  confirming.value = true
  try {
    const response = await confirmOrder(order.value.id)
    if (order.value) {
      order.value.status = response.data.data.status
      order.value.statusDescription = response.data.data.statusDescription
    }
    ElMessage.success('确认收货成功，订单已完成')
    await loadDetail()
  } catch (error) {
    ElMessage.error(messageOf(error, '确认收货失败，请稍后重试'))
  } finally {
    confirming.value = false
  }
}

function openReview(item: OrderItem): void {
  reviewTarget.value = item
  reviewDialogVisible.value = true
}

function onReviewed(): void {
  void loadReviewedItems()
}

onMounted(loadDetail)
</script>

<style scoped>
.detail-page { padding: 24px 0 48px; }
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-head h1 { font-size: 24px; margin: 0; }
.head-actions { display: flex; gap: 8px; align-items: center; }
.card { background: #fff; border: 1px solid var(--pd-border); border-radius: 12px; padding: 20px; }
.state-card { text-align: center; padding: 48px 20px; }
.state-card p { color: var(--pd-muted); }
.loading-box { min-height: 240px; }
.block { margin-bottom: 16px; }
.block h2 { font-size: 16px; margin: 0 0 14px; }
.review-hint { color: var(--pd-muted); font-size: 12px; font-weight: 400; }
.order-meta > div { display: flex; gap: 16px; padding: 6px 0; font-size: 13px; }
.order-meta span:first-child { color: var(--pd-muted); min-width: 72px; }
.status { color: var(--pd-primary); }
.payment-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 24px; }
.payment-grid > div { display: flex; justify-content: space-between; gap: 12px; font-size: 13px; border-bottom: 1px dashed var(--pd-border); padding-bottom: 8px; }
.payment-grid span:first-child { color: var(--pd-muted); }
.payment-grid .paid { color: #16a34a; }
.address { line-height: 1.8; }
.tip { color: var(--pd-muted); font-size: 12px; }
.goods-row { display: flex; gap: 14px; align-items: center; padding: 12px 0; border-top: 1px solid var(--pd-border); }
.goods-row img { width: 64px; height: 64px; object-fit: cover; border-radius: 8px; background: #f5f5f5; }
.goods-info { flex: 1; }
.goods-info p { color: var(--pd-muted); font-size: 13px; margin: 6px 0 0; }
.subtotal { color: var(--pd-primary); font-weight: 500; }
.total-block { display: flex; justify-content: flex-end; align-items: center; gap: 16px; }
.total-block strong { color: var(--pd-primary); font-size: 24px; }
</style>
