<template>
  <router-link
    to="/"
    class="brand-logo"
    :class="[`brand-logo--${mode}`, { 'brand-logo--inverse': inverse }]"
    :aria-label="ariaLabel"
  >
    <!-- 图形标志：购物袋 + 字母 P，统一 SVG 资源，缩放清晰 -->
    <img
      v-if="mode !== 'text'"
      class="brand-logo__mark"
      :style="markStyle"
      :src="markSrc"
      alt=""
      aria-hidden="true"
    />

    <!-- 品牌名称（深灰/黑；inverse 模式为白色） -->
    <span v-if="mode !== 'icon'" class="brand-logo__text">
      <strong>PandaGo</strong>
    </span>
  </router-link>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import markSrc from '@/assets/images/brand/pandago-mark.svg'

/**
 * PandaGo 统一品牌标志。
 * - mode=icon：仅图形（导航栏/登录卡片/Banner）
 * - mode=text：仅文字
 * - mode=full：图形 + 文字（默认）
 * - inverse：深色背景上使用白色文字，图形仍为品牌红
 * - size：图形边长（px）
 */
const props = withDefaults(
  defineProps<{
    mode?: 'icon' | 'text' | 'full'
    inverse?: boolean
    size?: number
  }>(),
  { mode: 'full', inverse: false, size: 42 },
)

const markStyle = computed(() => ({ width: `${props.size}px`, height: `${props.size}px` }))
const ariaLabel = computed(() => (props.mode === 'icon' ? 'PandaGo 首页' : 'PandaGo · 首页'))
</script>

<style scoped lang="scss">
.brand-logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
  text-decoration: none;
}

.brand-logo__mark {
  display: block;
  flex: none;
}

.brand-logo__text {
  display: flex;
  align-items: center;
  line-height: 1.15;

  strong {
    font-size: 22px;
    font-weight: 700;
    letter-spacing: -0.5px;
    color: var(--pd-ink);
  }
}

.brand-logo--inverse .brand-logo__text strong {
  color: #ffffff;
}

.brand-logo--icon {
  gap: 0;
}
</style>
