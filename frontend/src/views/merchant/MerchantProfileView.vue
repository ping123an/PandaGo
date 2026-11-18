<template>
  <div class="merchant-profile">
    <div class="page-head">
      <div>
        <h1>商家资料</h1>
        <p>店铺信息由平台维护状态与审核，你只能修改下方可编辑字段。</p>
      </div>
      <el-button v-if="!editing" type="primary" :disabled="!profile" @click="startEdit">编辑资料</el-button>
    </div>

    <div v-if="loading" v-loading="loading" class="loading-box" />

    <div v-else-if="loadError" class="card state-card">
      <h2>资料加载失败</h2>
      <p>{{ loadError }}</p>
      <el-button type="primary" @click="loadProfile">重新加载</el-button>
    </div>

    <div v-else-if="!profile" class="card state-card">
      <h2>暂无店铺资料</h2>
      <p>请联系平台管理员开通店铺资料。</p>
    </div>

    <template v-else>
      <section v-if="!editing" class="card block">
        <h2>店铺信息</h2>
        <div class="info-grid">
          <div class="info-head">
            <MerchantAvatar :size="64" :round="false" />
            <div>
              <h3>{{ profile.storeName }}</h3>
              <p class="muted">店铺 ID：{{ profile.id }} · 所属用户：{{ profile.userId }}</p>
            </div>
          </div>
          <div class="info-row"><span>店铺简介</span><strong>{{ profile.description || '未填写' }}</strong></div>
          <div class="info-row"><span>联系人</span><strong>{{ profile.contactName || '未填写' }}</strong></div>
          <div class="info-row"><span>联系电话</span><strong>{{ profile.contactPhone || '未填写' }}</strong></div>
          <div class="info-row"><span>店铺地址</span><strong>{{ profile.address || '未填写' }}</strong></div>
          <div class="info-row">
            <span>经营状态</span>
            <strong class="tag" :class="{ paused: profile.status !== 'NORMAL' }">{{ profile.statusDescription }}</strong>
          </div>
          <div class="info-row">
            <span>审核状态</span>
            <strong class="tag" :class="auditClass(profile.auditStatus)">{{ profile.auditStatusDescription }}</strong>
          </div>
          <div class="info-row"><span>创建时间</span><strong>{{ formatTime(profile.createdAt) }}</strong></div>
          <div class="info-row"><span>最后更新</span><strong>{{ formatTime(profile.updatedAt) }}</strong></div>
        </div>
      </section>

      <section v-else class="card block">
        <h2>编辑店铺资料</h2>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="edit-form">
          <el-form-item label="店铺名称" prop="storeName">
            <el-input v-model="form.storeName" maxlength="64" show-word-limit placeholder="请输入店铺名称" />
          </el-form-item>
          <el-form-item label="店铺简介" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="简单介绍你的店铺" />
          </el-form-item>
          <el-form-item label="联系人" prop="contactName">
            <el-input v-model="form.contactName" maxlength="32" placeholder="联系人姓名" />
          </el-form-item>
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="form.contactPhone" maxlength="20" placeholder="手机号或固话，可留空" />
          </el-form-item>
          <el-form-item label="店铺地址" prop="address">
            <el-input v-model="form.address" maxlength="200" placeholder="店铺所在地址" />
          </el-form-item>
          <el-form-item label="店铺 Logo" prop="logo">
            <el-input v-model="form.logo" maxlength="255" placeholder="Logo 图片地址（可选）" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="save">保存</el-button>
            <el-button :disabled="saving" @click="cancelEdit">取消</el-button>
          </el-form-item>
        </el-form>
        <p class="hint">店铺 ID、所属用户、经营状态与审核状态由平台管理，不在此处修改。</p>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import MerchantAvatar from '@/components/MerchantAvatar.vue'
import { useMerchantStore } from '@/stores/merchant'
import type { MerchantProfile } from '@/types/merchant'

interface ProfileForm {
  storeName: string
  description: string
  contactName: string
  contactPhone: string
  address: string
  logo: string
}

const profile = ref<MerchantProfile | null>(null)
const merchantStore = useMerchantStore()
const loading = ref(true)
const loadError = ref('')
const editing = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<ProfileForm>({
  storeName: '', description: '', contactName: '', contactPhone: '', address: '', logo: '',
})

const rules: FormRules<ProfileForm> = {
  storeName: [
    { required: true, message: '店铺名称不能为空', trigger: 'blur' },
    { validator: (_rule, value: string, callback) => {
        if (value && !value.trim()) callback(new Error('店铺名称不能仅为空格'))
        else callback()
      }, trigger: 'blur' },
  ],
  description: [{ max: 500, message: '店铺简介不能超过 500 个字符', trigger: 'blur' }],
  contactName: [{ max: 32, message: '联系人不能超过 32 个字符', trigger: 'blur' }],
  contactPhone: [
    { pattern: /^$|^1[3-9]\d{9}$|^0\d{2,3}-?\d{7,8}$/, message: '请输入正确的手机号或固话', trigger: 'blur' },
  ],
  address: [{ max: 200, message: '店铺地址不能超过 200 个字符', trigger: 'blur' }],
}

async function loadProfile(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    await merchantStore.fetchProfile(true)
    profile.value = merchantStore.profile
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '店铺资料加载失败'
    profile.value = null
  } finally {
    loading.value = false
  }
}

/** 进入编辑态：用当前资料填充表单（取消时不会写库） */
function startEdit(): void {
  if (!profile.value) return
  Object.assign(form, {
    storeName: profile.value.storeName,
    description: profile.value.description ?? '',
    contactName: profile.value.contactName ?? '',
    contactPhone: profile.value.contactPhone ?? '',
    address: profile.value.address ?? '',
    logo: profile.value.logo ?? '',
  })
  editing.value = true
}

function cancelEdit(): void {
  editing.value = false
  formRef.value?.clearValidate()
}

/** 保存：前端校验 + 后端再次校验；成功后刷新真实数据 */
async function save(): Promise<void> {
  if (saving.value || !formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await merchantStore.updateProfile({
      storeName: form.storeName,
      description: form.description || null,
      contactName: form.contactName || null,
      contactPhone: form.contactPhone || null,
      address: form.address || null,
      logo: form.logo || null,
    })
    profile.value = merchantStore.profile
    ElMessage.success('店铺资料保存成功')
    editing.value = false
    await loadProfile()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

function auditClass(status: string): string {
  return status === 'APPROVED' ? 'approved' : status === 'REJECTED' ? 'rejected' : 'pending'
}

onMounted(loadProfile)
</script>

<style scoped>
.merchant-profile { padding: 24px; background: #f5f6f8; min-height: calc(100vh - 68px); }
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.page-head h1 { margin: 0 0 6px; font-size: 22px; }
.page-head p { margin: 0; color: var(--pd-muted); font-size: 13px; }
.loading-box { min-height: 300px; }
.card { background: #fff; border: 1px solid var(--pd-border); border-radius: 12px; box-shadow: 0 2px 12px rgba(0,0,0,.04); }
.state-card { padding: 48px; text-align: center; }
.state-card p { color: var(--pd-muted); }
.block { padding: 22px 24px; }
.block h2 { font-size: 16px; margin: 0 0 18px; }
.info-head { display: flex; align-items: center; gap: 16px; padding-bottom: 18px; border-bottom: 1px solid var(--pd-border); margin-bottom: 6px; }
.info-head h3 { margin: 0 0 6px; font-size: 20px; }
.muted { color: var(--pd-muted); font-size: 12px; margin: 0; }
.info-row { display: flex; justify-content: space-between; gap: 20px; padding: 11px 0; border-bottom: 1px dashed var(--pd-border); font-size: 13px; }
.info-row span { color: var(--pd-muted); flex-shrink: 0; }
.info-row strong { text-align: right; font-weight: 500; }
.tag { color: #16a34a; }
.tag.paused, .tag.pending { color: #b45309; }
.tag.rejected { color: #dc2626; }
.tag.approved { color: #16a34a; }
.edit-form { max-width: 560px; }
.hint { color: var(--pd-muted); font-size: 12px; margin: 8px 0 0 100px; }
@media (max-width: 640px) { .merchant-profile { padding: 14px; } .hint { margin-left: 0; } .info-row { font-size: 12px; } }
</style>
