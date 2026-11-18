<template>
  <div class="address-page container">
    <div class="page-head">
      <h1>收货地址</h1>
      <el-button type="primary" :disabled="!userStore.isLoggedIn" @click="openCreate">新增地址</el-button>
    </div>

    <div v-if="!userStore.isLoggedIn" class="card state-card">
      <h2>请先登录</h2>
      <p>登录后即可维护自己的收货地址。</p>
      <el-button type="primary" @click="goLogin">去登录</el-button>
    </div>

    <div v-else-if="loading" v-loading="loading" class="loading-box" />

    <div v-else-if="errorMessage" class="card state-card">
      <h2>加载失败</h2>
      <p>{{ errorMessage }}</p>
      <el-button type="primary" @click="loadAddresses">重试</el-button>
    </div>

    <div v-else-if="addresses.length === 0" class="card state-card">
      <h2>还没有收货地址</h2>
      <p>添加地址后，下单时可以快速选择。</p>
      <el-button type="primary" @click="openCreate">新增地址</el-button>
    </div>

    <div v-else class="address-grid">
      <div v-for="item in addresses" :key="item.id" class="card address-card" :class="{ default: item.isDefault }">
        <div class="card-head">
          <span class="receiver">{{ item.receiverName }}</span>
          <span class="phone">{{ maskPhone(item.phone) }}</span>
          <el-tag v-if="item.isDefault" type="danger" size="small">默认</el-tag>
        </div>
        <p class="region">{{ item.province }} {{ item.city }} {{ item.district }}</p>
        <p class="detail">{{ item.detailAddress }}</p>
        <div class="ops">
          <el-button v-if="!item.isDefault" type="primary" link size="small" @click="markDefault(item)">
            设为默认
          </el-button>
          <el-button type="primary" link size="small" @click="openEdit(item)">编辑</el-button>
          <el-button type="danger" link size="small" @click="remove(item)">删除</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑地址' : '新增地址'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="收货人" prop="receiverName">
          <el-input v-model="form.receiverName" maxlength="50" placeholder="请输入收货人姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="省份" prop="province">
          <el-input v-model="form.province" maxlength="50" placeholder="如：四川省" />
        </el-form-item>
        <el-form-item label="城市" prop="city">
          <el-input v-model="form.city" maxlength="50" placeholder="如：成都市" />
        </el-form-item>
        <el-form-item label="区县" prop="district">
          <el-input v-model="form.district" maxlength="50" placeholder="如：武侯区" />
        </el-form-item>
        <el-form-item label="详细地址" prop="detailAddress">
          <el-input v-model="form.detailAddress" maxlength="200" placeholder="街道、门牌号等" />
        </el-form-item>
        <el-form-item label="默认地址">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createAddress, getAddresses, removeAddress, setDefaultAddress, updateAddress } from '@/api/address'
import { useUserStore } from '@/stores/user'
import type { AddressFormPayload, AddressVO } from '@/types/user-center'

const userStore = useUserStore()
const addresses = ref<AddressVO[]>([])
const loading = ref(true)
const errorMessage = ref('')
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const emptyForm = (): AddressFormPayload => ({
  receiverName: '', phone: '', province: '', city: '', district: '', detailAddress: '', isDefault: false,
})
const form = reactive<AddressFormPayload>(emptyForm())

const rules: FormRules<AddressFormPayload> = {
  receiverName: [{ required: true, message: '请输入收货人', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  province: [{ required: true, message: '请输入省份', trigger: 'blur' }],
  city: [{ required: true, message: '请输入城市', trigger: 'blur' }],
  district: [{ required: true, message: '请输入区县', trigger: 'blur' }],
  detailAddress: [{ required: true, message: '请输入详细地址', trigger: 'blur' }],
}

function maskPhone(phone: string): string {
  return phone.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
}

async function loadAddresses(): Promise<void> {
  if (!userStore.isLoggedIn) {
    loading.value = false
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await getAddresses()
    addresses.value = data.data
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载失败'
    addresses.value = []
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(item: AddressVO): void {
  editingId.value = item.id
  Object.assign(form, {
    receiverName: item.receiverName,
    phone: item.phone,
    province: item.province,
    city: item.city,
    district: item.district,
    detailAddress: item.detailAddress,
    isDefault: item.isDefault,
  })
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await updateAddress(editingId.value, form)
      ElMessage.success('修改成功')
    } else {
      await createAddress(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadAddresses()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function markDefault(item: AddressVO): Promise<void> {
  try {
    await setDefaultAddress(item.id)
    ElMessage.success('已设为默认地址')
    await loadAddresses()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function remove(item: AddressVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除「${item.receiverName}」的地址吗？`, '提示', { type: 'warning' })
    await removeAddress(item.id)
    ElMessage.success('已删除')
    await loadAddresses()
  } catch {
    // 用户取消确认
  }
}

function goLogin(): void {
  // 由布局的登录入口处理，这里简单跳转
  window.location.href = `/login?redirect=${encodeURIComponent('/addresses')}`
}

onMounted(loadAddresses)
</script>

<style scoped lang="scss">
.address-page { padding-top: 24px; padding-bottom: 60px; }
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.page-head h1 { margin: 0; font-size: 24px; }
.loading-box { min-height: 300px; }
.state-card { padding: 56px; text-align: center; }
.state-card h2 { margin: 0 0 10px; }
.state-card p { margin: 0 0 22px; color: var(--pd-muted); }
.address-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.address-card { padding: 18px; }
.address-card.default { border-color: var(--pd-primary); }
.card-head { display: flex; align-items: center; gap: 10px; }
.receiver { font-size: 15px; font-weight: 600; }
.phone { color: var(--pd-muted); font-size: 13px; }
.region, .detail { margin: 10px 0 0; color: var(--pd-ink-soft); font-size: 13px; }
.ops { display: flex; gap: 8px; margin-top: 16px; }
</style>
