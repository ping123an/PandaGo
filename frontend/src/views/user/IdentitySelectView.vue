<template>
  <div class="identity-page">
    <div class="identity-card card">
      <div class="brand">
        <BrandLogo mode="icon" :size="48" />
        <h1>欢迎来到 PandaGo，请选择登录身份</h1>
        <p>不同身份进入不同的工作空间，权限由账号实际角色决定</p>
      </div>

      <div class="identity-grid">
        <button
          v-for="option in identities"
          :key="option.value"
          type="button"
          class="identity-option"
          :class="`opt-${option.value.toLowerCase()}`"
          @click="choose(option.value)"
        >
          <span class="opt-icon">{{ option.icon }}</span>
          <strong>{{ option.title }}</strong>
          <span class="opt-desc">{{ option.desc }}</span>
          <span class="opt-action">进入登录 →</span>
        </button>
      </div>

      <p class="register-hint">
        还没有账号？<router-link to="/register">注册普通用户</router-link>
      </p>
      <p class="security-note">
        身份选择仅决定登录入口，真实权限由后端账号角色校验，前端无法自行提权。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import BrandLogo from '@/components/BrandLogo.vue'
import type { LoginAs } from '@/types/auth'

const router = useRouter()
const route = useRoute()

const identities: Array<{ value: LoginAs; icon: string; title: string; desc: string }> = [
  {
    value: 'USER',
    icon: 'U',
    title: '普通用户',
    desc: '浏览商品、购物车、下单、查看订单和商品评价。',
  },
  {
    value: 'MERCHANT',
    icon: 'M',
    title: '商家',
    desc: '管理商品、处理订单、填写物流信息和查看经营数据。',
  },
  {
    value: 'ADMIN',
    icon: 'A',
    title: '平台管理员',
    desc: '管理用户、审核商品、管理分类和查看平台订单。',
  },
]

function choose(value: LoginAs): void {
  const redirect = route.query.redirect
  void router.push({
    name: 'login-by-identity',
    params: { identity: value.toLowerCase() },
    query: redirect ? { redirect: String(redirect) } : {},
  })
}
</script>

<style scoped lang="scss">
.identity-page {
  display: grid;
  min-height: calc(100vh - 200px);
  place-items: center;
  padding: 48px 16px;
  background: radial-gradient(circle at 50% 0%, #fff 0%, var(--pd-bg) 70%);
}

.identity-card {
  width: 100%;
  max-width: 900px;
  padding: 40px;
}

.brand {
  text-align: center;
  margin-bottom: 32px;

  h1 {
    margin: 16px 0 8px;
    font-size: 24px;
    color: var(--pd-ink);
  }

  p {
    margin: 0;
    color: var(--pd-muted);
    font-size: 13px;
  }
}

.identity-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}

.identity-option {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 26px 22px;
  border: 1px solid var(--pd-border);
  border-radius: 12px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;

  &:hover {
    transform: translateY(-4px);
    border-color: var(--pd-primary);
    box-shadow: 0 12px 28px rgb(0 0 0 / 8%);
  }

  strong {
    font-size: 17px;
    color: var(--pd-ink);
  }
}

.opt-icon {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 12px;
  background: rgb(225 37 27 / 8%);
  color: var(--pd-primary);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.opt-merchant .opt-icon { background: rgb(37 99 235 / 8%); color: #2563eb; }
.opt-admin .opt-icon { background: rgb(124 58 237 / 8%); color: #7c3aed; }

.opt-desc {
  color: var(--pd-muted);
  font-size: 13px;
  line-height: 1.7;
  min-height: 66px;
}

.opt-action {
  margin-top: auto;
  color: var(--pd-primary);
  font-size: 13px;
  font-weight: 600;
}

.register-hint {
  margin: 28px 0 0;
  text-align: center;
  color: var(--pd-muted);
  font-size: 13px;

  a {
    margin-left: 6px;
    color: var(--pd-primary);
    font-weight: 600;
  }
}

.security-note {
  margin: 16px 0 0;
  text-align: center;
  color: var(--pd-muted);
  font-size: 11px;
}

@media (max-width: 768px) {
  .identity-card { padding: 24px 18px; }
  .identity-grid { grid-template-columns: 1fr; }
  .opt-desc { min-height: 0; }
}
</style>
