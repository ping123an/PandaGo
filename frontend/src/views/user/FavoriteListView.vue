<template>
  <div class="favorite-page container">
    <div class="page-head">
      <h1>我的收藏</h1>
      <span class="sub">{{ favorites.length }} 件商品</span>
    </div>

    <div v-if="!userStore.isLoggedIn" class="card state-card">
      <h2>请先登录</h2>
      <p>登录后即可查看和管理你的收藏。</p>
      <el-button type="primary" @click="goLogin">去登录</el-button>
    </div>

    <div v-else-if="loading" v-loading="loading" class="loading-box" />

    <div v-else-if="errorMessage" class="card state-card">
      <h2>加载失败</h2>
      <p>{{ errorMessage }}</p>
      <el-button type="primary" @click="loadFavorites">重试</el-button>
    </div>

    <div v-else-if="favorites.length === 0" class="card state-card">
      <h2>还没有收藏</h2>
      <p>在商品详情页点击收藏按钮，就能在这里快速找到它。</p>
      <el-button type="primary" @click="router.push('/products')">去逛逛</el-button>
    </div>

    <div v-else class="favorite-grid">
      <div v-for="item in favorites" :key="item.id" class="card favorite-card" :class="{ invalid: !item.available }">
        <router-link :to="{ name: 'product-detail', params: { id: item.productId } }" class="cover">
          <img :src="item.mainImage || '/images/panda-product.svg'" :alt="item.productName" />
        </router-link>
        <div class="info">
          <router-link :to="{ name: 'product-detail', params: { id: item.productId } }" class="name">
            {{ item.productName }}
          </router-link>
          <p class="price">¥{{ item.price == null ? '--' : item.price.toFixed(2) }}</p>
          <p v-if="!item.available" class="reason">{{ item.reason }}，暂不可购买</p>
          <p v-else class="reason ok">可购买</p>
          <el-button type="danger" link size="small" @click="remove(item)">取消收藏</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFavorites, removeFavorite } from '@/api/favorite'
import { useUserStore } from '@/stores/user'
import type { FavoriteVO } from '@/types/user-center'

const router = useRouter()
const userStore = useUserStore()
const favorites = ref<FavoriteVO[]>([])
const loading = ref(true)
const errorMessage = ref('')

async function loadFavorites(): Promise<void> {
  if (!userStore.isLoggedIn) {
    loading.value = false
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await getFavorites()
    favorites.value = data.data
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载失败'
    favorites.value = []
  } finally {
    loading.value = false
  }
}

async function remove(item: FavoriteVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`取消收藏「${item.productName}」？`, '提示', { type: 'warning' })
    await removeFavorite(item.productId)
    ElMessage.success('已取消收藏')
    await loadFavorites()
  } catch {
    // 用户取消确认
  }
}

function goLogin(): void {
  router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
}

onMounted(loadFavorites)
</script>

<style scoped lang="scss">
.favorite-page { padding-top: 24px; padding-bottom: 60px; }
.page-head { display: flex; align-items: baseline; gap: 16px; margin-bottom: 18px; }
.page-head h1 { margin: 0; font-size: 24px; }
.sub { color: var(--pd-muted); font-size: 12px; }
.loading-box { min-height: 300px; }
.state-card { padding: 56px; text-align: center; }
.state-card h2 { margin: 0 0 10px; }
.state-card p { margin: 0 0 22px; color: var(--pd-muted); }
.favorite-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; }
.favorite-card { overflow: hidden; }
.favorite-card.invalid { opacity: 0.75; }
.cover { display: block; height: 160px; background: #f3f4f6; }
.cover img { width: 100%; height: 100%; object-fit: cover; }
.info { padding: 14px; }
.name { display: block; overflow: hidden; color: var(--pd-ink); font-size: 14px; font-weight: 600; text-decoration: none; text-overflow: ellipsis; white-space: nowrap; }
.name:hover { color: var(--pd-primary); }
.price { margin: 8px 0 4px; color: var(--pd-primary); font-size: 18px; font-weight: 700; }
.reason { margin: 0 0 8px; color: #b45309; font-size: 12px; }
.reason.ok { color: #16a34a; }
</style>
