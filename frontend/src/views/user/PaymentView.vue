<template>
  <div class="payment-page container">
    <div class="page-head">
      <h1>确认支付</h1>
      <span class="sub">本项目为模拟支付，不接入真实第三方接口；金额与状态均由后端决定</span>
    </div>

    <div class="checkout-body">
      <div class="main-col">
        <section class="card block">
          <h2>订单信息</h2>
          <div v-if="loading" v-loading="loading" class="loading-box" />

          <div v-else-if="errorMessage" class="state-card">
            <h3>查询失败</h3>
            <p>{{ errorMessage }}</p>
            <el-button type="primary" @click="loadPayment">重试</el-button>
          </div>

          <template v-else-if="payment">
            <div class="order-meta">
              <div><span>订单编号</span><strong>{{ payment.orderNo }}</strong></div>
              <div><span>创建时间</span><span>{{ formatTime(payment.createdAt) }}</span></div>
              <div><span>订单状态</span><strong class="status">{{ payment.orderStatusDescription }}</strong></div>
              <div v-if="orderCount > 1" class="split-row">
                <span>拆单提示</span>
                <span>本次提交共 {{ orderCount }} 个订单，当前支付首单；其余可在「我的订单」继续支付</span>
              </div>
            </div>

            <p v-if="payment.paid" class="notice success">
              该订单已支付成功，无需重复支付。
              <el-button type="primary" link @click="goSuccess">查看支付结果</el-button>
            </p>
            <p v-else-if="payment.orderStatus === 'CANCELLED'" class="notice warning">订单已取消，无法支付。</p>
            <p v-else-if="payment.orderStatus !== 'PENDING_PAYMENT'" class="notice warning">
              订单当前状态不允许支付（{{ payment.orderStatusDescription }}），请返回订单列表查看。
            </p>
          </template>
        </section>

        <section v-if="payment && !payment.paid && payment.orderStatus === 'PENDING_PAYMENT'" class="card block">
          <h2>选择支付方式</h2>
          <div class="method-list">
            <label class="method-item" :class="{ active: method === 'ALIPAY' }">
              <input v-model="method" type="radio" value="ALIPAY" name="pay-method" />
              <span class="method-icon alipay">支</span>
              <span class="method-text"><strong>支付宝</strong><small>模拟支付，仅作演示</small></span>
            </label>
            <label class="method-item" :class="{ active: method === 'WECHAT' }">
              <input v-model="method" type="radio" value="WECHAT" name="pay-method" />
              <span class="method-icon wechat">微</span>
              <span class="method-text"><strong>微信支付</strong><small>模拟支付，仅作演示</small></span>
            </label>
          </div>
        </section>
      </div>

      <aside class="side-col">
        <section class="card summary">
          <h2>支付信息</h2>
          <div class="sum-row">
            <span>{{ payment?.paid ? '实付金额' : '应付金额' }}</span>
            <strong class="amount">¥{{ payment ? payment.payAmount.toFixed(2) : '0.00' }}</strong>
          </div>
          <div class="sum-row">
            <span>支付方式</span>
            <span>{{ methodLabel }}</span>
          </div>
          <template v-if="payment?.paid">
            <div class="sum-row"><span>支付流水号</span><span class="mono">{{ payment.paymentNo }}</span></div>
            <div class="sum-row"><span>支付时间</span><span>{{ formatTime(payment.paidAt) }}</span></div>
          </template>

          <template v-if="payment && !payment.paid && payment.orderStatus === 'PENDING_PAYMENT'">
            <el-button
              type="primary"
              size="large"
              class="pay-btn"
              :loading="paying"
              @click="pay"
            >
              确认支付
            </el-button>
            <el-button size="large" class="cancel-btn" :disabled="paying" @click="cancelPay">
              取消支付
            </el-button>
            <p class="tip">按钮防重复点击只是体验优化；幂等由后端状态机与支付记录唯一索引保证。</p>
          </template>

          <template v-else-if="payment?.paid">
            <el-button type="primary" size="large" class="pay-btn" @click="goSuccess">查看支付结果</el-button>
          </template>

          <div class="footer-links">
            <el-button text @click="goDetail">查看订单详情</el-button>
            <el-button text @click="goOrders">我的订单</el-button>
            <el-button text @click="goHome">继续购物</el-button>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPaymentResult, mockPay } from '@/api/payment'
import type { PaymentResult } from '@/types/payment'

const route = useRoute()
const router = useRouter()

const orderNo = computed(() => String(route.params.orderNo || ''))
const orderCount = computed(() => {
  const n = Number(route.query.orderCount)
  return Number.isFinite(n) && n > 0 ? n : 1
})
const payment = ref<PaymentResult | null>(null)
const loading = ref(true)
const paying = ref(false)
const errorMessage = ref('')
const method = ref<'ALIPAY' | 'WECHAT'>('ALIPAY')

const methodLabel = computed(() =>
  payment.value?.paid
    ? (payment.value.paymentMethod === 'ALIPAY' ? '支付宝（模拟）'
      : payment.value.paymentMethod === 'WECHAT' ? '微信支付（模拟）' : '模拟支付')
    : (method.value === 'ALIPAY' ? '支付宝（模拟）' : '微信支付（模拟）'),
)

function formatTime(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

function messageOf(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

async function loadPayment() {
  loading.value = true
  errorMessage.value = ''
  try {
    payment.value = (await getPaymentResult(orderNo.value)).data.data
  } catch (error) {
    errorMessage.value = messageOf(error, '支付结果查询失败')
  } finally {
    loading.value = false
  }
}

async function pay() {
  if (paying.value || !payment.value || payment.value.paid) return
  paying.value = true
  try {
    const response = await mockPay({ orderNo: orderNo.value, paymentMethod: method.value })
    payment.value = response.data.data
    ElMessage.success('支付成功')
    await router.push({
      name: 'payment-success',
      params: { orderNo: orderNo.value },
      query: { amount: String(payment.value.payAmount), method: method.value },
    })
  } catch (error) {
    ElMessage.error(messageOf(error, '支付失败，请稍后重试'))
    await loadPayment()
  } finally {
    paying.value = false
  }
}

/** 取消支付：不调用任何状态变更接口，订单保持待支付，可在我的订单继续支付 */
function cancelPay() {
  ElMessage.info('已取消支付，订单保留，可在「我的订单」中继续支付')
  void router.push({ name: 'orders' })
}

function goSuccess() {
  void router.push({
    name: 'payment-success',
    params: { orderNo: orderNo.value },
    query: {
      amount: payment.value ? String(payment.value.payAmount) : '0',
      method: payment.value?.paymentMethod || method.value,
    },
  })
}
function goDetail() {
  if (payment.value) void router.push({ name: 'order-detail', params: { id: payment.value.orderId } })
}
function goOrders() { void router.push({ name: 'orders' }) }
function goHome() { void router.push({ name: 'home' }) }

onMounted(loadPayment)
</script>

<style scoped>
.payment-page { padding: 24px 0 48px; background: #f5f6f8; min-height: calc(100vh - 64px); }
.page-head { display: flex; align-items: baseline; gap: 16px; margin-bottom: 20px; }
.page-head h1 { font-size: 24px; margin: 0; }
.sub { color: var(--pd-muted); font-size: 13px; }
.card { background: #fff; border: 1px solid var(--pd-border); border-radius: 12px; padding: 20px; box-shadow: 0 2px 12px rgba(0,0,0,.04); }
.checkout-body { display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: 20px; align-items: start; }
.main-col { min-width: 0; }
.block { margin-bottom: 16px; }
.block h2, .summary h2 { font-size: 16px; margin: 0 0 16px; }
.loading-box { min-height: 200px; }
.state-card { text-align: center; padding: 40px 0; }
.state-card p { color: var(--pd-muted); }
.order-meta { background: #fafafa; border-radius: 10px; padding: 6px 16px; }
.order-meta > div { display: flex; justify-content: space-between; gap: 16px; padding: 10px 0; font-size: 13px; }
.order-meta > div + div { border-top: 1px solid var(--pd-border); }
.order-meta span:first-child { color: var(--pd-muted); flex-shrink: 0; }
.order-meta .status { color: var(--pd-primary); }
.order-meta .split-row { flex-direction: column; gap: 4px; color: #b45309; font-size: 12px; }
.method-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
.method-item { display: flex; align-items: center; gap: 12px; padding: 16px; border: 1px solid var(--pd-border); border-radius: 10px; cursor: pointer; }
.method-item.active { border-color: var(--pd-primary); background: #fff5f4; }
.method-icon { width: 36px; height: 36px; border-radius: 8px; color: #fff; display: inline-flex; align-items: center; justify-content: center; font-weight: 700; }
.method-icon.alipay { background: #1677ff; }
.method-icon.wechat { background: #16a34a; }
.method-text { display: flex; flex-direction: column; gap: 2px; }
.method-text small { color: var(--pd-muted); }
.side-col { position: sticky; top: 20px; }
.summary .sum-row { display: flex; justify-content: space-between; padding: 9px 0; font-size: 14px; color: #333; gap: 12px; }
.amount { color: var(--pd-primary); font-size: 26px; }
.mono { font-family: monospace; font-size: 12px; word-break: break-all; }
.pay-btn { width: 100%; margin-top: 16px; }
.cancel-btn { width: 100%; margin-top: 10px; }
.tip { color: var(--pd-muted); font-size: 12px; line-height: 1.6; margin: 12px 0 0; }
.notice { margin: 16px 0 0; padding: 12px 14px; border-radius: 8px; font-size: 13px; }
.notice.success { background: #e8f7ee; color: #16a34a; }
.notice.warning { background: #fff7e6; color: #b45309; }
.footer-links { margin-top: 20px; border-top: 1px solid var(--pd-border); padding-top: 10px; display: flex; flex-wrap: wrap; gap: 4px; }
@media (max-width: 1000px) { .checkout-body { grid-template-columns: 1fr; } .side-col { position: static; } }
</style>
