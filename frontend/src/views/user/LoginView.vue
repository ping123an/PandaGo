<template>
  <div class="auth-page">
    <div class="auth-card card">
      <div class="auth-brand">
        <BrandLogo mode="icon" :size="48" />
        <h1>欢迎回来</h1>
        <p>当前身份：<strong class="identity-name">{{ identityLabel }}</strong></p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="auth-form"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            size="large"
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />

        <el-button
          type="primary"
          size="large"
          class="submit-btn"
          :loading="loading"
          native-type="submit"
        >
          登录
        </el-button>
      </el-form>

      <div class="auth-footer">
        <span>还没有账号？</span>
        <router-link :to="{ name: 'register', query: route.query.redirect ? { redirect: route.query.redirect } : {} }">
          立即注册
        </router-link>
      </div>

      <div class="back-to-identity">
        <router-link to="/login">← 返回身份选择</router-link>
      </div>

      <div class="demo-hint">
        <strong>体验提示</strong>
        <span>身份仅决定入口，权限由账号真实角色校验。本地可用 merchant_demo / admin_demo 体验对应工作台（密码见本地演示脚本）。</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import BrandLogo from '@/components/BrandLogo.vue'
import { useUserStore } from '@/stores/user'
import type { LoginAs, LoginPayload } from '@/types/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 由路由 /login/:identity 决定的登录身份；/login 默认按普通用户处理 */
const identity = computed<LoginAs>(() => {
  const raw = String(route.params.identity ?? 'user').toLowerCase()
  if (raw === 'merchant') return 'MERCHANT'
  if (raw === 'admin') return 'ADMIN'
  return 'USER'
})
const identityLabel = computed(() => ({
  USER: '普通用户',
  MERCHANT: '商家',
  ADMIN: '平台管理员',
}[identity.value]))

const formRef = ref<FormInstance>()
const form = reactive<LoginPayload>({ username: '', password: '' })
const loading = ref(false)
const errorMessage = ref('')

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/** 按登录身份决定默认落地页；多角色账号由后端角色与路由守卫最终兜底 */
function homeFor(as: LoginAs): string {
  if (as === 'ADMIN') return '/admin'
  if (as === 'MERCHANT') return '/merchant'
  return '/'
}

async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMessage.value = ''
  try {
    // 传 loginAs：后端会校验该账号是否真实拥有对应角色，前端选择不决定权限
    await userStore.login({ ...form, loginAs: identity.value })
    ElMessage.success(`欢迎回来，${userStore.nickname}`)
    const redirect = route.query.redirect as string | undefined
    // 防止开放重定向：只允许站内路径
    const target = redirect && redirect.startsWith('/') ? redirect : homeFor(identity.value)
    await router.replace(target)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.auth-page {
  display: grid;
  min-height: 70vh;
  place-items: center;
  padding: 40px 16px;
  background: radial-gradient(circle at 50% 0%, #fff 0%, var(--pd-bg) 70%);
}

.auth-card {
  width: 100%;
  max-width: 420px;
  padding: 32px;
}

.auth-brand {
  text-align: center;
  margin-bottom: 24px;

  h1 { margin: 12px 0 6px; font-size: 22px; color: var(--pd-ink); }
  p { margin: 0; color: var(--pd-muted); font-size: 13px; }
}

.auth-form { display: flex; flex-direction: column; gap: 4px; }

.submit-btn { width: 100%; margin-top: 8px; letter-spacing: 2px; }

.identity-name { color: var(--pd-primary); }

.auth-footer {
  margin-top: 20px;
  text-align: center;
  font-size: 13px;
  color: var(--pd-muted);

  a { color: var(--pd-primary); font-weight: 600; margin-left: 4px; }
}

.back-to-identity {
  margin-top: 14px;
  text-align: center;
  font-size: 12px;

  a { color: var(--pd-muted); }

  a:hover { color: var(--pd-primary); }
}

.demo-hint {
  margin-top: 20px;
  padding: 12px 14px;
  border-radius: 6px;
  background: #f9fafb;
  border: 1px solid var(--pd-border);
  font-size: 12px;
  color: var(--pd-muted);
  line-height: 1.6;

  strong { display: block; color: var(--pd-ink); margin-bottom: 2px; }
}
</style>
