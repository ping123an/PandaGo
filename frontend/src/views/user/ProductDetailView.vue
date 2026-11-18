<template>
  <div class="product-detail-page container">
    <div class="breadcrumb">
      <router-link to="/">首页</router-link>
      <span>/</span>
      <router-link :to="{ name: 'product-list', query: { categoryId: detail?.categoryId } }">
        {{ detail?.categoryName || '商品列表' }}
      </router-link>
      <span>/</span>
      <span class="current">{{ detail?.name || '商品详情' }}</span>
    </div>

    <div v-if="loading" v-loading="loading" class="loading-box" />

    <div v-else-if="detail" class="detail-card card">
      <div class="gallery">
        <img :src="activeImage" :alt="detail.name" />
        <div v-if="detail.images.length > 1" class="thumbs">
          <button
            v-for="(image, index) in detail.images"
            :key="`${image}-${index}`"
            class="thumb"
            :class="{ active: activeImage === image }"
            @click="activeImage = image"
          >
            <img :src="image" :alt="`${detail.name} 图片 ${index + 1}`" />
          </button>
        </div>
      </div>

      <div class="info">
        <p class="category">{{ detail.categoryName }}</p>
        <h1>{{ detail.name }}</h1>
        <p class="subtitle">{{ detail.subtitle }}</p>

        <div class="price-box">
          <span class="label">PandaGo 价</span>
          <span class="price"><small>¥</small>{{ detail.price.toFixed(2) }}</span>
        </div>

        <dl class="meta">
          <div><dt>销量</dt><dd>{{ detail.sales }}</dd></div>
          <div>
            <dt>库存</dt>
            <dd :class="detail.stockStatus === 'OUT_OF_STOCK' ? 'text-out' : 'text-in'">
              {{ detail.stockStatus === 'OUT_OF_STOCK' ? '暂时缺货' : '有货' }}
            </dd>
          </div>
          <div><dt>状态</dt><dd>{{ detail.status === 'ON_SALE' ? '已上架' : '已下架' }}</dd></div>
        </dl>

        <div v-if="!canPurchase" class="alert">
          该商品当前不可加入购物车（未上架或暂时缺货）。
        </div>

        <div class="actions">
          <el-input-number v-model="quantity" :min="1" :max="99" size="large" />
          <el-button
            size="large"
            :loading="adding"
            :disabled="!canPurchase"
            @click="handleAddToCart"
          >
            加入购物车
          </el-button>
          <el-button
            type="primary"
            size="large"
            :loading="buyingNow"
            :disabled="!canPurchase"
            @click="handleBuyNow"
          >
            立即购买
          </el-button>
          <FavoriteButton :product-id="detail.id" />
          <el-button size="large" @click="router.back()">返回列表</el-button>
        </div>

        <p class="hint">价格来自数据库，成交价以下单时为准；能否购买以实际库存校验结果为准。</p>
      </div>
    </div>

    <div v-else class="card error-card">
      <h2>商品不存在或已下架</h2>
      <p>{{ errorMessage }}</p>
      <el-button type="primary" @click="router.push('/products')">返回商品列表</el-button>
    </div>

    <div v-if="detail" class="description-card card">
      <h2>商品详情</h2>
      <p>{{ detail.description || '该商品暂无更多描述。' }}</p>
    </div>

    <div v-if="detail" class="reviews-card card">
      <h2>商品评价 <small>（{{ reviews.length }}）</small></h2>
      <div v-if="reviews.length === 0" class="reviews-empty">
        <p>暂无评价，购买并确认收货后可发表第一条评价。</p>
        <p class="reviews-empty-hint">已购买？请到「我的订单」→ 已完成订单 →「评价商品」发表评价。</p>
      </div>
      <div v-for="review in reviews" :key="review.id" class="review-item">
        <div class="review-head">
          <strong>{{ review.username }}</strong>
          <el-rate :model-value="review.rating" disabled size="small" />
          <span class="review-time">{{ formatTime(review.createdAt) }}</span>
        </div>
        <p class="review-content">{{ review.content }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProductDetail } from '@/api/product'
import { addCartItem } from '@/api/cart'
import { getProductReviews } from '@/api/review'
import FavoriteButton from '@/components/FavoriteButton.vue'
import { useUserStore } from '@/stores/user'
import type { ProductDetail } from '@/types/catalog'
import type { Review } from '@/types/review'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const detail = ref<ProductDetail | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const activeImage = ref('/images/panda-product.svg')
const quantity = ref(1)
const adding = ref(false)
const buyingNow = ref(false)
const reviews = ref<Review[]>([])

/** 是否可购买：仅依据库存状态与上下架状态；真实库存仍由后端下单/加购时严格校验 */
const canPurchase = computed(
  () => detail.value?.status === 'ON_SALE' && detail.value?.stockStatus === 'IN_STOCK',
)

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

async function loadReviews(id: number): Promise<void> {
  try {
    reviews.value = (await getProductReviews(id)).data.data
  } catch {
    reviews.value = []
  }
}

async function handleAddToCart(): Promise<void> {
  if (!detail.value) return
  // 前端拦截缺货/下架商品；即使被绕过，后端购物车接口仍会按真实库存再次校验
  if (!canPurchase.value) {
    ElMessage.warning('该商品当前不可加入购物车')
    return
  }
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再加入购物车')
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  if (adding.value) return
  adding.value = true
  try {
    await addCartItem({ productId: detail.value.id, quantity: quantity.value })
    ElMessage.success('已加入购物车')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加入购物车失败')
  } finally {
    adding.value = false
  }
}

/**
 * 立即购买：不写入购物车，直接带商品 ID + 数量跳转确认订单页。
 * 规格在单规格商品下取默认值，SKU ID 即商品 ID；价格/商家/库存仍由后端预览与下单时重新校验。
 */
function handleBuyNow(): void {
  if (!detail.value) return
  if (!canPurchase.value) {
    ElMessage.warning('该商品当前无法购买（未上架或暂时缺货）')
    return
  }
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再购买')
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  if (buyingNow.value) return
  router.push({
    name: 'checkout-buy-now',
    query: { productId: String(detail.value.id), quantity: String(quantity.value) },
  })
}

async function loadDetail(): Promise<void> {
  const id = Number(route.params.id)
  if (!Number.isFinite(id) || id <= 0) {
    loading.value = false
    detail.value = null
    errorMessage.value = '无效的商品 ID'
    return
  }

  loading.value = true
  try {
    const { data } = await getProductDetail(id)
    detail.value = data.data
    activeImage.value = data.data.mainImage || '/images/panda-product.svg'
    errorMessage.value = ''
    await loadReviews(id)
  } catch (error) {
    detail.value = null
    errorMessage.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    loading.value = false
  }
}

watch(() => route.params.id, loadDetail)
onMounted(loadDetail)
</script>

<style scoped lang="scss">
.product-detail-page { padding-top: 20px; padding-bottom: 60px; }

.breadcrumb {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
  color: var(--pd-muted);
  font-size: 12px;

  a:hover { color: var(--pd-primary); }
  .current { color: var(--pd-ink); }
}

.loading-box { min-height: 420px; }

.detail-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 36px;
  padding: 28px;
}

.gallery {
  img.main { width: 100%; }
}

.gallery > img {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 8px;
  background: #f3f4f6;
}

.thumbs {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}

.thumb {
  width: 64px;
  height: 64px;
  padding: 0;
  overflow: hidden;
  border: 2px solid transparent;
  border-radius: 6px;
  background: #f3f4f6;
  cursor: pointer;

  &.active { border-color: var(--pd-primary); }
  img { width: 100%; height: 100%; object-fit: cover; }
}

.info {
  padding-top: 8px;
}

.category {
  margin: 0 0 10px;
  color: var(--pd-primary);
  font-size: 13px;
}

h1 { margin: 0; font-size: 26px; line-height: 1.4; }

.subtitle { margin: 12px 0 0; color: var(--pd-muted); font-size: 14px; }

.price-box {
  margin-top: 26px;
  padding: 18px 20px;
  border-radius: 8px;
  background: #fff5f4;
}

.label { display: block; color: var(--pd-muted); font-size: 12px; }

.price {
  color: var(--pd-primary);
  font-size: 34px;
  font-weight: 700;

  small { margin-right: 3px; font-size: 18px; }
}

.meta {
  display: flex;
  gap: 36px;
  margin: 24px 0;

  div { display: flex; flex-direction: column; }
  dt { color: var(--pd-muted); font-size: 12px; }
  dd { margin: 4px 0 0; font-size: 14px; font-weight: 600; }
  dd.text-in { color: #16a34a; }
  dd.text-out { color: #dc2626; }
}

.actions { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
.alert { margin-top: 20px; padding: 10px 14px; border-radius: 6px; background: #fffbeb; color: #b45309; font-size: 13px; }

.hint { margin-top: 18px; color: var(--pd-muted); font-size: 12px; }

.description-card { margin-top: 20px; padding: 24px 28px; }
.description-card h2 { margin: 0 0 12px; font-size: 18px; }
.description-card p { margin: 0; color: var(--pd-ink-soft); line-height: 1.9; }

.reviews-card { margin-top: 20px; padding: 24px 28px; }
.reviews-card h2 { margin: 0 0 16px; font-size: 18px; }
.reviews-card h2 small { color: var(--pd-muted); font-size: 13px; font-weight: 400; }
.reviews-empty { padding: 20px 0; color: var(--pd-muted); font-size: 13px; }
.reviews-empty p { margin: 4px 0; }
.reviews-empty-hint { color: var(--pd-primary); font-size: 12px; }
.review-item { padding: 14px 0; border-top: 1px solid var(--pd-border); }
.review-head { display: flex; align-items: center; gap: 12px; font-size: 13px; }
.review-time { margin-left: auto; color: var(--pd-muted); font-size: 12px; }
.review-content { margin: 8px 0 0; color: var(--pd-ink-soft); font-size: 13px; line-height: 1.7; }

.error-card { padding: 48px; text-align: center; }
.error-card h2 { margin: 0 0 8px; }
.error-card p { margin: 0 0 22px; color: var(--pd-muted); }

@media (max-width: 768px) {
  .detail-card { grid-template-columns: 1fr; gap: 20px; padding: 18px; }
  h1 { font-size: 21px; }
}
</style>
