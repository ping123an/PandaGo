<template>
  <div class="placeholder-page container">
    <div class="placeholder-card card">
      <div class="placeholder-brand" aria-hidden="true">
        <BrandLogo mode="icon" :size="72" />
      </div>

      <span v-if="route.meta.phase" class="phase-badge">{{ route.meta.phase }}待实现</span>
      <h1>{{ route.meta.title ?? '页面建设中' }}</h1>
      <p>
        {{ route.meta.description ?? '该页面将在后续开发阶段实现。' }}
      </p>
      <p class="path">当前路径：<code>{{ route.fullPath }}</code></p>

      <div class="actions">
        <el-button type="primary" @click="router.push('/')">返回首页</el-button>
        <el-button @click="router.back()">上一页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import BrandLogo from '@/components/BrandLogo.vue'

const route = useRoute()
const router = useRouter()
</script>

<style scoped lang="scss">
.placeholder-page {
  padding: 64px 16px 96px;
}

.placeholder-card {
  max-width: 640px;
  margin: 0 auto;
  padding: 52px 40px;
  text-align: center;
}

.placeholder-brand {
  margin: 0 auto 20px;
  width: fit-content;

  :deep(.brand-logo) { pointer-events: none; }
}

h1 {
  margin: 16px 0 10px;
  font-size: 26px;
}

p {
  margin: 0;
  color: var(--pd-muted);
}

.path {
  margin-top: 14px;
  font-size: 12px;

  code {
    padding: 2px 6px;
    border-radius: 4px;
    background: var(--pd-bg);
    color: var(--pd-primary);
  }
}

.actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 28px;
}
</style>
