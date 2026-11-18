<template>
  <img
    class="user-avatar-img"
    :src="currentSrc"
    :style="avatarStyle"
    :alt="alt"
    @error="onImageError"
  />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { DEFAULT_USER_AVATAR, useUserStore } from '@/stores/user'

/**
 * 用户头像统一展示组件。
 *
 * <p>数据源只有一个：user store 的 avatarUrl（来自后端 user.avatar）。
 * 右上角、个人中心等所有用户头像位置共用本组件，头像更新后随 store 响应式刷新。
 * 未设置头像或图片加载失败时，统一回退到 PandaGo 默认头像。</p>
 */
const props = withDefaults(
  defineProps<{
    size?: number
    alt?: string
    /** 圆角值；'50%' 为圆形 */
    radius?: string
  }>(),
  { size: 30, alt: '用户头像', radius: '50%' },
)

const userStore = useUserStore()
const currentSrc = ref(userStore.avatarUrl)

// store 中头像变化时立即同步显示（含上传成功后的全局更新）
watch(() => userStore.avatarUrl, (url) => {
  currentSrc.value = url
})

function onImageError(): void {
  if (currentSrc.value !== DEFAULT_USER_AVATAR) {
    currentSrc.value = DEFAULT_USER_AVATAR
  }
}

const avatarStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  borderRadius: props.radius,
}))
</script>

<style scoped>
.user-avatar-img {
  display: block;
  flex: none;
  object-fit: cover;
  background: #fff1f0;
}
</style>
