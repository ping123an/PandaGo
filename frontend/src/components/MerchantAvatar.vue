<template>
  <img
    class="merchant-avatar"
    :src="currentSrc"
    :style="avatarStyle"
    :alt="alt"
    @error="onImageError"
  />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { DEFAULT_MERCHANT_AVATAR, useMerchantStore } from '@/stores/merchant'

/**
 * 商家头像统一展示组件。
 * 数据来源只有一个：merchant store 的 logoUrl（来自 merchant_profile.logo）。
 * 未设置头像或图片加载失败时，统一回退到 PandaGo 品牌图标。
 */
const props = withDefaults(
  defineProps<{
    size?: number
    /** true=圆形（右上角），false=圆角方形（工作台/资料页店铺 Logo） */
    round?: boolean
    alt?: string
  }>(),
  { size: 32, round: true, alt: '店铺头像' },
)

const merchantStore = useMerchantStore()
const currentSrc = ref(merchantStore.logoUrl)

// 店铺头像变化时（含资料页保存后的全局更新）同步显示最新 URL
watch(() => merchantStore.logoUrl, (url) => {
  currentSrc.value = url
})

function onImageError(): void {
  if (currentSrc.value !== DEFAULT_MERCHANT_AVATAR) {
    currentSrc.value = DEFAULT_MERCHANT_AVATAR
  }
}

const avatarStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  borderRadius: props.round ? '50%' : '12px',
}))
</script>

<style scoped>
.merchant-avatar {
  display: block;
  flex: none;
  object-fit: cover;
  background: #fff1f0;
}
</style>
