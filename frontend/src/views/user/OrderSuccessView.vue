<template>
  <div class="success-page container">
    <div class="card result-card">
      <div class="check-mark">✓</div>
      <h1>下单成功</h1>
      <p class="split-notice">订单提交成功，本次购买已根据商家拆分为 {{ orderCount }} 个订单。</p>
      <div class="meta">
        <div><span>订单号（首单）</span><strong>{{ orderNo }}</strong></div>
        <div v-if="orderCount > 1"><span>拆单数量</span><strong>{{ orderCount }} 个订单</strong></div>
        <div><span>应付合计</span><strong class="amount">¥{{ amount }}</strong></div>
      </div>
      <p class="hint">该金额由后端按提交时的数据库价格计算，与结算页预估金额可能存在差异。</p>
      <div class="actions">
        <el-button v-if="orderNo" type="primary" @click="goPay">去模拟支付</el-button>
        <el-button type="primary" @click="goDetail">查看订单详情</el-button>
        <el-button @click="goOrders">我的订单</el-button>
        <el-button @click="goHome">继续购物</el-button>
      </div>
      <p class="next">模拟支付已开放：可在支付页、订单列表或订单详情完成，重复请求由后端保证幂等。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const orderNo = computed(() => String(route.params.orderNo || ''))
const amount = computed(() => String(route.query.amount || '0.00'))
const orderCount = computed(() => {
  const n = Number(route.query.orderCount)
  return Number.isFinite(n) && n > 0 ? n : 1
})

function goDetail() {
  // 订单列表第一条即本次订单；详情需数字 id，这里先去列表页定位
  void router.push({ name: 'orders' })
}
function goPay() { void router.push({ name: 'payment', params: { orderNo: orderNo.value } }) }
function goOrders() { void router.push({ name: 'orders' }) }
function goHome() { void router.push({ name: 'home' }) }
</script>

<style scoped>
.success-page { padding: 48px 0; }
.result-card { max-width: 560px; margin: 0 auto; text-align: center; padding: 40px 32px; }
.check-mark { width: 64px; height: 64px; line-height: 64px; margin: 0 auto 16px; border-radius: 50%; background: #e8f7ee; color: #16a34a; font-size: 34px; }
h1 { font-size: 24px; margin: 0 0 8px; }
.result-card > p { color: var(--pd-muted); }
.split-notice { color: var(--pd-primary); font-weight: 600; }
.meta { margin: 24px 0; padding: 16px; background: #fafafa; border-radius: 8px; }
.meta div { display: flex; justify-content: space-between; padding: 6px 0; }
.meta span { color: var(--pd-muted); }
.amount { color: var(--pd-primary); font-size: 18px; }
.hint { font-size: 12px; line-height: 1.6; }
.actions { display: flex; gap: 10px; justify-content: center; margin-top: 24px; flex-wrap: wrap; }
.next { margin-top: 20px; font-size: 12px; }
</style>
