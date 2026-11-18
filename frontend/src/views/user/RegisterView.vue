<template>
  <div class="auth-page">
    <div class="auth-card card">
      <div class="auth-brand">
        <BrandLogo mode="icon" :size="48" />
        <h1>创建账号</h1>
        <p>加入 PandaGo，开启你的熊猫购之旅</p>
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
            placeholder="字母开头，4-30 位字母数字下划线"
            size="large"
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="8-64 位，须包含字母和数字"
            size="large"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            size="large"
            show-password
            autocomplete="new-password"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <el-form-item label="昵称（选填）" prop="nickname">
          <el-input v-model="form.nickname" placeholder="给自己起个昵称" size="large" />
        </el-form-item>

        <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />

        <el-button
          type="primary"
          size="large"
          class="submit-btn"
          :loading="loading"
          native-type="submit"
        >
          注册
        </el-button>
      </el-form>

      <div class="auth-footer">
        <span>已有账号？</span>
        <router-link to="/login">返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import BrandLogo from '@/components/BrandLogo.vue'
import { useUserStore } from '@/stores/user'
import type { RegisterPayload } from '@/types/auth'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const form = reactive<RegisterPayload>({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
})
const loading = ref(false)
const errorMessage = ref('')

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z][A-Za-z0-9_]*$/, message: '字母开头，仅限字母数字下划线', trigger: 'blur' },
    { min: 4, max: 30, message: '长度 4-30 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '长度 8-64 位', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '须同时包含字母和数字', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  nickname: [{ max: 50, message: '昵称不超过 50 字', trigger: 'blur' }],
}

async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMessage.value = ''
  try {
    await userStore.register({ ...form })
    ElMessage.success('注册成功，请登录')
    await router.push({ name: 'login' })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '注册失败，请重试'
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

.auth-form { display: flex; flex-direction: column; gap: 2px; }

.submit-btn { width: 100%; margin-top: 8px; letter-spacing: 2px; }

.auth-footer {
  margin-top: 20px;
  text-align: center;
  font-size: 13px;
  color: var(--pd-muted);

  a { color: var(--pd-primary); font-weight: 600; margin-left: 4px; }
}
</style>
