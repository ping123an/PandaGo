<template>
  <el-button
    :type="favorited ? 'warning' : 'default'"
    :loading="loading"
    size="small"
    class="favorite-btn"
    @click.stop="handleToggle"
  >
    {{ favorited ? '★ 已收藏' : '☆ 收藏' }}
  </el-button>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addFavorite, checkFavorite, removeFavorite } from '@/api/favorite'
import { useUserStore } from '@/stores/user'

/**
 * 收藏按钮：在商品卡片和详情页共用。
 * 前端状态仅用于交互提示，重复收藏由后端唯一索引拦截并返回 409。
 */
const props = defineProps<{ productId: number }>()

const router = useRouter()
const userStore = useUserStore()
const favorited = ref(false)
const loading = ref(false)

async function loadState(): Promise<void> {
  if (!userStore.isLoggedIn) {
    favorited.value = false
    return
  }
  try {
    const { data } = await checkFavorite(props.productId)
    favorited.value = data.data
  } catch {
    favorited.value = false
  }
}

async function handleToggle(): Promise<void> {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再收藏商品')
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  if (loading.value) return
  loading.value = true
  try {
    if (favorited.value) {
      await removeFavorite(props.productId)
      favorited.value = false
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(props.productId)
      favorited.value = true
      ElMessage.success('收藏成功')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    loading.value = false
  }
}

watch(() => props.productId, loadState)
onMounted(loadState)
</script>

<style scoped lang="scss">
.favorite-btn {
  font-size: 12px;
}
</style>
