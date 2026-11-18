<template>
  <div class="profile-page container">
    <div class="profile-wrap">
      <!-- 顶部：头像 / 昵称 / 用户名 / 角色 / 编辑资料 / 退出登录 -->
      <section class="card profile-hero">
        <button class="avatar-button" type="button" title="点击更换头像" @click="openAvatarDialog">
          <UserAvatar :size="76" radius="50%" alt="用户头像" />
          <span class="avatar-overlay"><el-icon :size="16"><Camera /></el-icon></span>
        </button>
        <div class="hero-identity">
          <h1>{{ userStore.nickname || userStore.username }}</h1>
          <p>@{{ userStore.userInfo?.username || userStore.username }}</p>
          <div class="hero-roles">
            <el-tag v-for="role in userStore.roles" :key="role" type="success" size="small" class="role-tag">
              {{ roleLabel(role) }}
            </el-tag>
          </div>
        </div>
        <div class="hero-actions">
          <el-button type="primary" @click="startEdit">编辑个人资料</el-button>
          <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
        </div>
      </section>

      <!-- 加载 / 错误态 -->
      <div v-if="loading && !userStore.userInfo" v-loading="loading" class="card state-card" />
      <div v-else-if="!userStore.userInfo" class="card state-card">
        <p>暂未获取到用户资料，请重新登录。</p>
        <el-button type="primary" @click="goLogin">去登录</el-button>
      </div>

      <template v-else>
        <!-- 个人资料信息区（查看态） -->
        <section class="card profile-section">
          <div class="section-head">
            <h2>个人资料</h2>
            <span class="section-hint">以下信息仅你本人可见</span>
          </div>
          <ul class="info-list">
            <li><span class="info-label">用户 ID</span><span class="info-value mono">{{ userStore.userInfo.userId }}</span></li>
            <li><span class="info-label">用户名</span><span class="info-value">{{ userStore.userInfo.username }}</span></li>
            <li><span class="info-label">昵称</span><span class="info-value">{{ userStore.userInfo.nickname }}</span></li>
            <li><span class="info-label">手机号</span><span class="info-value">{{ userStore.userInfo.phone || '未填写' }}</span></li>
            <li>
              <span class="info-label">角色</span>
              <span class="info-value">
                <el-tag v-for="role in userStore.roles" :key="role" size="small" class="role-tag">{{ roleLabel(role) }}</el-tag>
              </span>
            </li>
          </ul>
        </section>

        <!-- 其他功能入口：仅展示已实现且可正常跳转的功能 -->
        <section class="card profile-section">
          <div class="section-head"><h2>快捷入口</h2></div>
          <div class="entry-grid">
            <router-link :to="{ name: 'orders' }" class="entry">我的订单<span>→</span></router-link>
            <router-link :to="{ name: 'favorites' }" class="entry">我的收藏<span>→</span></router-link>
            <router-link :to="{ name: 'addresses' }" class="entry">我的地址<span>→</span></router-link>
            <router-link :to="{ name: 'my-reviews' }" class="entry">我的评价<span>→</span></router-link>
          </div>
        </section>

        <!-- 安全说明：仅说明，不再放置编辑按钮 -->
        <section class="card profile-section footer-section">
          <div class="security-note">
            <strong>安全说明</strong>
            <p>
              密码以 BCrypt 哈希存储，任何接口都不会返回密码字段。用户 ID、用户名与角色由后端管理，
              个人资料接口只接受昵称和手机号白名单字段；头像上传由服务端校验类型与大小，
              当前身份由后端从 JWT 解析，前端仅用于控制展示。
            </p>
          </div>
        </section>
      </template>
    </div>

    <!-- 编辑个人资料弹窗 -->
    <el-dialog v-model="editVisible" title="编辑个人资料" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" class="edit-form">
        <el-form-item label="头像">
          <div class="dialog-avatar">
            <UserAvatar :size="56" radius="50%" alt="当前头像" />
            <el-button size="small" @click="openAvatarDialog">更换头像</el-button>
          </div>
        </el-form-item>
        <el-form-item label="用户 ID">
          <el-input :model-value="String(userStore.userInfo?.userId ?? '')" disabled />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input :model-value="userStore.userInfo?.username || ''" disabled />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" maxlength="50" show-word-limit placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="手机号（可留空）" />
        </el-form-item>
        <el-form-item label="角色">
          <el-input :model-value="userStore.roles.map(roleLabel).join('、')" disabled />
        </el-form-item>
        <p class="form-hint">
          用户 ID、用户名与角色由系统管理，无法在此修改。当前项目未接入短信验证码，
          手机号按格式校验后直接更新；如需短信验证，后续可在此基础上扩展。
        </p>
        <p v-if="userStore.roles.includes('MERCHANT')" class="form-hint">
          你的账号同时拥有商家身份：此处修改手机号会同步更新店铺资料中的联系电话。
        </p>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="cancelEdit">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 头像上传弹窗：预览 + 确认 -->
    <el-dialog v-model="avatarVisible" title="更换头像" width="440px" destroy-on-close>
      <div class="avatar-upload-area">
        <div class="avatar-preview" @click="triggerFileInput">
          <img v-if="avatarPreview" :src="avatarPreview" alt="头像预览" />
          <UserAvatar v-else :size="128" radius="50%" alt="当前头像" />
          <span class="preview-hint">{{ avatarPreview ? '点击可重新选择' : '点击选择图片' }}</span>
        </div>
        <input ref="fileInputRef" class="hidden-input" type="file" accept="image/jpeg,image/png,image/webp" @change="onAvatarPicked" />
        <p class="upload-hint">支持 JPG、JPEG、PNG、WEBP，最大 2MB。确认上传后立即更新头像。</p>
        <p v-if="avatarError" class="upload-error">{{ avatarError }}</p>
      </div>
      <template #footer>
        <el-button :disabled="uploading" @click="closeAvatarDialog">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="uploading || !avatarFile" @click="confirmAvatarUpload">
          确认上传
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Camera } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import UserAvatar from '@/components/UserAvatar.vue'
import { updateProfile } from '@/api/auth'
import { useMerchantStore } from '@/stores/merchant'
import { useUserStore } from '@/stores/user'

const MAX_AVATAR_BYTES = 2 * 1024 * 1024
const ACCEPTED_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp']

const router = useRouter()
const userStore = useUserStore()
const merchantStore = useMerchantStore()
const loading = ref(false)
const editVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const avatarVisible = ref(false)
const uploading = ref(false)
const fileInputRef = ref<HTMLInputElement>()
const avatarFile = ref<File | null>(null)
const avatarPreview = ref('')
const avatarError = ref('')

interface ProfileForm {
  nickname: string
  phone: string
}
const form = reactive<ProfileForm>({ nickname: '', phone: '' })
const original = reactive<ProfileForm>({ nickname: '', phone: '' })
const isDirty = computed(() => form.nickname !== original.nickname || form.phone !== original.phone)

const phonePattern = /^$|^1[3-9]\d{9}$/
const rules: FormRules<ProfileForm> = {
  nickname: [
    { required: true, message: '昵称不能为空', trigger: 'blur' },
    { validator: (_rule, value: string, callback) => {
        if (value && !value.trim()) callback(new Error('昵称不能仅为空格'))
        else callback()
      }, trigger: 'blur' },
    { max: 50, message: '昵称长度不能超过 50', trigger: 'blur' },
  ],
  phone: [
    { pattern: phonePattern, message: '请输入正确的手机号', trigger: 'blur' },
  ],
}

function roleLabel(role: string): string {
  return ({ USER: '普通用户', MERCHANT: '商家', ADMIN: '管理员' } as Record<string, string>)[role] ?? role
}

/** 打开编辑弹窗：先填入当前资料；取消不写库 */
function startEdit(): void {
  const info = userStore.userInfo
  if (!info) return
  original.nickname = info.nickname ?? ''
  original.phone = info.phone ?? ''
  form.nickname = original.nickname
  form.phone = original.phone
  formRef.value?.clearValidate()
  editVisible.value = true
}

async function cancelEdit(): Promise<void> {
  if (isDirty.value) {
    try {
      await ElMessageBox.confirm('确定放弃修改吗？未保存的内容将丢失。', '提示', { type: 'warning' })
    } catch {
      return
    }
  }
  editVisible.value = false
}

/** 保存昵称/手机号：前端校验 + Loading + 防重复；成功后回写并刷新 */
async function save(): Promise<void> {
  if (saving.value || !formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const { data } = await updateProfile({
      nickname: form.nickname.trim(),
      phone: form.phone.trim() || null,
    })
    userStore.userInfo = data.data
    // 商家账号的手机号与店铺联系电话联动：同步刷新商家 Store 缓存，
    // 避免之后进入商家中心仍显示修改前的联系电话。
    if (userStore.roles.includes('MERCHANT')) {
      await merchantStore.fetchProfile(true).catch(() => undefined)
    }
    await userStore.fetchProfile()
    ElMessage.success('个人资料保存成功')
    editVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

// ---------- 头像上传 ----------

function openAvatarDialog(): void {
  avatarFile.value = null
  avatarPreview.value = ''
  avatarError.value = ''
  avatarVisible.value = true
}

function triggerFileInput(): void {
  fileInputRef.value?.click()
}

function releasePreview(): void {
  if (avatarPreview.value) URL.revokeObjectURL(avatarPreview.value)
  avatarPreview.value = ''
  avatarFile.value = null
  avatarError.value = ''
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function closeAvatarDialog(): void {
  if (uploading.value) return
  avatarVisible.value = false
  releasePreview()
}

function onAvatarPicked(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!ACCEPTED_TYPES.includes(file.type)) {
    avatarError.value = '只支持 JPG、JPEG、PNG、WEBP 图片'
    input.value = ''
    return
  }
  if (file.size > MAX_AVATAR_BYTES) {
    avatarError.value = '图片不能超过 2MB'
    input.value = ''
    return
  }
  releasePreview()
  avatarError.value = ''
  avatarFile.value = file
  avatarPreview.value = URL.createObjectURL(file)
}

async function confirmAvatarUpload(): Promise<void> {
  if (uploading.value || !avatarFile.value) return
  uploading.value = true
  try {
    // 统一走 Store：上传 → 后端写库 → 更新全局 avatarUrl → 右上角等所有组件立即同步
    await userStore.uploadAvatar(avatarFile.value)
    ElMessage.success('头像上传成功')
    avatarVisible.value = false
    releasePreview()
  } catch (error) {
    avatarError.value = error instanceof Error ? error.message : '头像上传失败，请稍后重试'
    ElMessage.error(avatarError.value)
  } finally {
    uploading.value = false
  }
}

watch(avatarVisible, (visible) => {
  if (!visible) releasePreview()
})
onBeforeUnmount(releasePreview)

async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  userStore.logout()
  router.push({ name: 'home' })
}

function goLogin(): void {
  router.push({ name: 'login' })
}

onMounted(async () => {
  loading.value = true
  try {
    await userStore.fetchProfile()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="scss">
.profile-page { padding: 32px 16px 72px; }
.profile-wrap { max-width: 760px; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; }

.card {
  background: var(--pd-paper);
  border: 1px solid var(--pd-border);
  border-radius: 12px;
  box-shadow: 0 2px 12px rgb(0 0 0 / 4%);
}

.profile-hero { display: flex; align-items: center; gap: 18px; padding: 26px 28px; }
.avatar-button {
  position: relative; width: 76px; height: 76px; flex: none; padding: 0;
  border: 2px solid #fff; border-radius: 50%; overflow: hidden; cursor: pointer;
  box-shadow: 0 0 0 2px var(--pd-primary);
  img { display: block; width: 100%; height: 100%; object-fit: cover; background: #fff1f0; }
}
.avatar-overlay {
  position: absolute; inset: auto 0 0; height: 24px; display: grid; place-items: center;
  background: rgb(0 0 0 / 45%); color: #fff; opacity: 0; transition: opacity .18s ease;
}
.avatar-button:hover .avatar-overlay { opacity: 1; }

.hero-identity { flex: 1; min-width: 0; }
.hero-identity h1 { margin: 0 0 4px; font-size: 21px; color: var(--pd-ink); }
.hero-identity p { margin: 0 0 6px; color: var(--pd-muted); font-size: 13px; }
.hero-roles .role-tag { margin-right: 6px; }
.hero-actions { display: flex; flex-direction: column; gap: 8px; flex: none; }

.profile-section { padding: 22px 28px; }
.section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.section-head h2 { margin: 0; font-size: 16px; color: var(--pd-ink); }
.section-hint { color: var(--pd-muted); font-size: 12px; }

.info-list { margin: 0; padding: 0; list-style: none; }
.info-list li {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  padding: 12px 0; border-bottom: 1px dashed var(--pd-border); font-size: 14px;
  &:last-child { border-bottom: none; }
}
.info-label { color: var(--pd-muted); flex: none; }
.info-value { text-align: right; color: var(--pd-ink); word-break: break-all; }
.info-value.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.role-tag { margin-left: 4px; }

.edit-form { max-width: 460px; }
.form-hint { margin: 4px 0 0; color: var(--pd-muted); font-size: 12px; line-height: 1.6; }
.dialog-avatar { display: flex; align-items: center; gap: 12px; }
.dialog-avatar img { width: 56px; height: 56px; border-radius: 50%; object-fit: cover; background: #fff1f0; }

.avatar-upload-area { text-align: center; }
.avatar-preview {
  position: relative; width: 132px; height: 132px; margin: 0 auto 12px;
  border: 2px dashed var(--pd-border); border-radius: 50%; overflow: hidden; cursor: pointer;
  img { width: 100%; height: 100%; object-fit: cover; }
  &:hover { border-color: var(--pd-primary); }
}
.preview-hint {
  position: absolute; left: 0; right: 0; bottom: 0; padding: 4px 0;
  background: rgb(0 0 0 / 50%); color: #fff; font-size: 11px;
}
.hidden-input { display: none; }
.upload-hint { margin: 8px 0 0; color: var(--pd-muted); font-size: 12px; }
.upload-error { margin: 6px 0 0; color: #dc2626; font-size: 12px; }

.entry-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.entry {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 14px; border: 1px solid var(--pd-border); border-radius: 8px;
  color: var(--pd-ink); font-size: 13px; text-decoration: none;
  transition: border-color .18s ease, background .18s ease;
  span { color: var(--pd-muted); }
  &:hover { border-color: var(--pd-primary); background: #fff9f8; }
}

.footer-section { font-size: 12px; color: var(--pd-muted); line-height: 1.7; }
.security-note strong { color: var(--pd-ink); }
.security-note p { margin: 4px 0 0; }
.state-card { padding: 40px; text-align: center; color: var(--pd-muted); min-height: 160px; }

@media (max-width: 640px) {
  .profile-page { padding: 18px 12px 48px; }
  .profile-hero { padding: 18px; flex-wrap: wrap; }
  .hero-actions { flex-direction: row; width: 100%; }
  .profile-section { padding: 18px; }
  .entry-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
