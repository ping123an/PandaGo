<template>
  <div class="payment-success-page container">
    <div class="card result-card">
      <div class="check-mark">✓</div>
      <h1>支付成功</h1>
      <p class="lead">订单已完成模拟支付，商家将尽快为你发货。</p>

      <div class="meta">
        <div><span>订单编号</span><strong class="mono">{{ orderNo }}</strong></div>
        <div><span>支付金额</span><strong class="amount">¥{{ amount }}</strong></div>
        <div><span>支付方式</span><strong>{{ methodLabel }}</strong></div>
        <div><span>支付时间</span><strong>{{ paidAt }}</strong></div>
      </div>

      <div class="actions">
        <el-button type="primary" size="large" @click="goDetail">查看订单</el-button>
        <el-button size="large" @click="goHome">继续购物</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPaymentResult } from '@/api/payment'

const route = useRoute()
const router = useRouter()

const orderNo = computed(() => String(route.params.orderNo || ''))
const amount = computed(() => String(route.query.amount || '0.00'))
const methodLabel = computed(() => {
  const m = String(route.query.method || 'MOCK')
  return m === 'ALIPAY' ? '支付宝（模拟）' : m === 'WECHAT' ? '微信支付（模拟）' : '模拟支付'
})
const paidAt = ref('')

function formatTime(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 19) : new Date().toLocaleString('zh-CN', { hour12: false })
}

onMounted(async () => {
  // 优先展示后端权威支付时间；接口异常时回退为本地时间，不影响成功态展示
  try {
    const res = await getPaymentResult(orderNo.value)
    paidAt.value = formatTime(res.data.data.paidAt)
  } catch {
    paidAt.value = formatTime(null)
  }
})

function goDetail() { void router.push({ name: 'orders' }) }
function goHome() { void router.push({ name: 'home' }) }
</script>

<style scoped>
.payment-success-page { padding: 56px 0; }
.result-card { max-width: 560px; margin: 0 auto; text-align: center; padding: 44px 36px; }
.check-mark { width: 72px; height: 72px; line-height: 72px; margin: 0 auto 18px; border-radius: 50%; background: #e8f7ee; color: #16a34a; font-size: 40px; font-weight: 700; }
h1 { font-size: 26px; margin: 0 0 8px; }
.lead { color: var(--pd-muted); margin: 0 0 26px; }
.meta { margin: 0 0 26px; padding: 16px 20px; background: #fafafa; border-radius: 10px; text-align: left; }
.meta div { display: flex; justify-content: space-between; gap: 16px; padding: 8px 0; font-size: 14px; }
.meta span { color: var(--pd-muted); }
.meta .mono { font-family: monospace; font-size: 13px; }
.amount { color: var(--pd-primary); font-size: 20px; }
.actions { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }
@media (max-width: 600px) { .result-card { padding: 32px 20px; } .meta div { font-size: 13px; } }
</style>
