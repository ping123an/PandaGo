<template>
  <div class="not-found container">
    <div class="card not-found-card">
      <div class="code">404</div>
      <h1>这只熊猫迷路了</h1>
      <p>你访问的页面不存在，或者已经被移动到其他位置。</p>
      <el-button type="primary" @click="router.push('/')">回到首页</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped lang="scss">
.not-found { padding: 80px 16px; }

.not-found-card {
  max-width: 520px;
  margin: 0 auto;
  padding: 56px 32px;
  text-align: center;
}

.code {
  color: var(--pd-primary);
  font-size: 76px;
  font-weight: 800;
  line-height: 1;
}

h1 { margin: 16px 0 8px; font-size: 24px; }
p { margin: 0 0 26px; color: var(--pd-muted); }
</style>
