<template>
  <div class="my-reviews-page container">
    <div class="page-head">
      <h1>我的评价</h1>
      <p>已完成订单中的商品评价会显示在这里；每单商品只能评价一次。</p>
    </div>

    <div v-if="!userStore.isLoggedIn" class="card state-card">
      <h2>请先登录</h2>
      <el-button type="primary" @click="goLogin">去登录</el-button>
    </div>

    <div v-else-if="loading" v-loading="loading" class="loading-box" />

    <div v-else-if="reviews.length === 0" class="card state-card">
      <h2>还没有评价</h2>
      <p>完成订单后，可在订单详情中对商品发表评价。</p>
      <el-button type="primary" @click="goOrders">查看我的订单</el-button>
    </div>

    <div v-else class="review-list">
      <div v-for="review in reviews" :key="review.id" class="card review-card">
        <div class="review-head">
          <strong>商品 #{{ review.productId }}</strong>
          <el-rate :model-value="review.rating" disabled size="small" />
          <span class="review-time">{{ formatTime(review.createdAt) }}</span>
        </div>
        <p class="review-content">{{ review.content }}</p>
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
import { ElMessage } from 'element-plus'
import { getMyReviews } from '@/api/review'
import { useUserStore } from '@/stores/user'
import type { Review } from '@/types/review'

const router = useRouter()
const userStore = useUserStore()
const reviews = ref<Review[]>([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(true)

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}
function goLogin() { void router.push({ name: 'login', query: { redirect: '/reviews' } }) }
function goOrders() { void router.push({ name: 'orders' }) }

async function loadReviews(): Promise<void> {
  loading.value = true
  try {
    const { data } = await getMyReviews(pageNum.value, pageSize)
    reviews.value = data.data.list
    total.value = data.data.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载评价失败')
  } finally {
    loading.value = false
  }
}

function onPage(page: number): void {
  pageNum.value = page
  void loadReviews()
}

onMounted(() => {
  if (userStore.isLoggedIn) void loadReviews()
  else loading.value = false
})
</script>

<style scoped lang="scss">
.my-reviews-page { padding: 24px 0 48px; }
.page-head { margin-bottom: 20px; h1 { font-size: 24px; margin: 0 0 6px; } p { color: var(--pd-muted); font-size: 13px; margin: 0; } }
.card { background: #fff; border: 1px solid var(--pd-border); border-radius: 12px; }
.state-card { text-align: center; padding: 48px 20px; p { color: var(--pd-muted); } }
.loading-box { min-height: 240px; }
.review-list { display: flex; flex-direction: column; gap: 14px; }
.review-card { padding: 16px 20px; }
.review-head { display: flex; align-items: center; gap: 12px; font-size: 13px; }
.review-time { margin-left: auto; color: var(--pd-muted); font-size: 12px; }
.review-content { margin: 10px 0 0; color: var(--pd-ink-soft); line-height: 1.7; }
:deep(.el-pagination) { margin-top: 20px; justify-content: flex-end; }
</style>
