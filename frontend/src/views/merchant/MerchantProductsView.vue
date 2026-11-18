<template>
  <div class="merchant-products-page">
    <div class="page-head">
      <div>
        <h1>我的商品</h1>
        <p>仅显示当前商家创建的商品；新增商品默认待审核且下架。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增商品</el-button>
    </div>

    <div class="filter-bar card">
      <el-input v-model="filters.keyword" clearable placeholder="搜索我的商品名称" class="filter-input" />
      <el-select v-model="filters.status" clearable placeholder="上架状态" class="filter-select">
        <el-option label="已上架" value="ON_SALE" />
        <el-option label="已下架" value="OFF_SALE" />
      </el-select>
      <el-select v-model="filters.auditStatus" clearable placeholder="审核状态" class="filter-select">
        <el-option label="待审核" value="PENDING" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已驳回" value="REJECTED" />
      </el-select>
      <el-button type="primary" @click="reload">筛选</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <div v-loading="loading" class="table-card card">
      <div v-if="loadError && !loading" class="load-error">
        <p>{{ loadError }}</p>
        <el-button type="primary" @click="loadProducts">重新加载</el-button>
      </div>
      <el-table v-else :data="products" empty-text="暂无商品，点击右上角新增" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="商品" min-width="260">
          <template #default="{ row }">
            <div class="product-cell">
              <img :src="row.mainImage" :alt="row.name" />
              <div>
                <strong>{{ row.name }}</strong>
                <small>{{ row.categoryName }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="price" label="价格" width="110">
          <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="90" />
        <el-table-column prop="sales" label="销量" width="90" />
        <el-table-column label="上架" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ON_SALE' ? 'success' : 'info'">
              {{ row.status === 'ON_SALE' ? '已上架' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核" width="120">
          <template #default="{ row }">
            <!-- 审核列只展示当前商品的一个有效审核状态；驳回原因以悬停提示呈现，不作为状态文字 -->
            <el-tooltip
              v-if="auditView(row.auditStatus).abnormal"
              :content="`未知审核状态：${row.auditStatus || '空'}，请联系管理员核对`"
              placement="top"
            >
              <el-tag :type="auditView(row.auditStatus).type">状态异常</el-tag>
            </el-tooltip>
            <el-tooltip
              v-else-if="row.auditStatus === 'REJECTED' && row.auditReason"
              :content="`驳回原因：${row.auditReason}`"
              placement="top"
            >
              <el-tag :type="auditView(row.auditStatus).type">{{ auditView(row.auditStatus).label }}</el-tag>
            </el-tooltip>
            <el-tag v-else :type="auditView(row.auditStatus).type">
              {{ auditView(row.auditStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="toggleStatus(row)">
              {{ row.status === 'ON_SALE' ? '下架' : '上架' }}
            </el-button>
            <el-button
              v-if="row.auditStatus !== 'APPROVED'"
              link
              type="warning"
              @click="submitAudit(row)"
            >
              提交审核
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > 0" class="pagination">
        <el-pagination
          v-model:current-page="filters.pageNum"
          v-model:page-size="filters.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
        />
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑商品' : '新增商品'"
      width="720px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" maxlength="150" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类">
            <el-option
              v-for="option in categoryOptions"
              :key="option.id"
              :label="option.name"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="商品简介">
          <el-input v-model="form.subtitle" maxlength="255" />
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input-number v-model="form.price" :min="0.01" :precision="2" :controls="false" />
        </el-form-item>
        <el-form-item label="库存" prop="stock">
          <el-input-number v-model="form.stock" :min="0" :controls="false" />
        </el-form-item>
        <el-form-item label="主图 URL" prop="mainImage">
          <el-input v-model="form.mainImage" placeholder="/images/panda-product.svg 或 https://..." />
        </el-form-item>
        <el-form-item label="详情图">
          <div class="image-rows">
            <div v-for="(_, index) in form.detailImages" :key="index" class="image-row">
              <el-input v-model="form.detailImages[index]" placeholder="详情图 URL" />
              <el-button type="danger" link @click="form.detailImages.splice(index, 1)">移除</el-button>
            </div>
            <el-button size="small" @click="form.detailImages.push('')">添加详情图</el-button>
            <p class="form-hint">支持 http(s) 或 /images/ 开头的 jpg/png/webp/gif/svg 地址，最多 9 张。</p>
          </div>
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number v-model="form.sort" :min="0" :controls="false" />
        </el-form-item>
        <el-form-item label="商品详情">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="4000" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="saveProduct">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getCategories } from '@/api/category'
import {
  createMyProduct,
  getMyProductDetail,
  getMyProducts,
  submitProductAudit,
  updateMyProduct,
  updateMyProductStatus,
} from '@/api/product'
import type {
  CategoryVO,
  ProductFormPayload,
  ProductManagementItem,
  ProductManagementQuery,
  ProductStatusPayload,
} from '@/types/catalog'

interface MerchantForm {
  categoryId: number | undefined
  name: string
  subtitle: string
  mainImage: string
  detailImages: string[]
  price: number | undefined
  stock: number | undefined
  description: string
  sort: number
}

const loading = ref(false)
const submitting = ref(false)
const loadError = ref('')
const products = ref<ProductManagementItem[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const route = useRoute()

const filters = reactive<ProductManagementQuery>({
  keyword: '',
  status: undefined,
  auditStatus: undefined,
  pageNum: 1,
  pageSize: 10,
})

const form = reactive<MerchantForm>({
  categoryId: undefined,
  name: '',
  subtitle: '',
  mainImage: '/images/panda-product.svg',
  detailImages: [],
  price: undefined,
  stock: 0,
  description: '',
  sort: 0,
})

const imagePattern = /^(https?:\/\/|\/images\/).+\.(png|jpe?g|webp|gif|svg)(\?.*)?$/i
const rules: FormRules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }],
  mainImage: [
    { required: true, message: '请输入主图 URL', trigger: 'blur' },
    { pattern: imagePattern, message: '图片地址或格式不合法', trigger: 'blur' },
  ],
}

const category = ref<CategoryVO[]>([])

const categoryOptions = computed(() => {
  const options: Array<{ id: number; name: string }> = []
  for (const parent of category.value) {
    options.push({ id: parent.id, name: parent.name })
    for (const child of parent.children) {
      options.push({ id: child.id, name: `${parent.name} / ${child.name}` })
    }
  }
  return options
})

/** 审核状态映射：一个状态只对应一个标签；未知/空状态显式标记，禁止默认显示待审核掩盖数据问题 */
type AuditTagType = 'success' | 'warning' | 'danger' | 'info'
interface AuditView {
  label: string
  type: AuditTagType
  abnormal: boolean
}
const AUDIT_STATUS_VIEW: Record<string, Omit<AuditView, 'abnormal'>> = {
  PENDING: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
}

function auditView(status: unknown): AuditView {
  const item = typeof status === 'string' ? AUDIT_STATUS_VIEW[status] : undefined
  return item ? { ...item, abnormal: false } : { label: '状态异常', type: 'info', abnormal: true }
}

async function loadCategories(): Promise<void> {
  try {
    const { data } = await getCategories()
    category.value = data.data
  } catch {
    category.value = []
  }
}

async function loadProducts(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await getMyProducts({ ...filters })
    products.value = data.data.list
    total.value = data.data.total
  } catch (error) {
    products.value = []
    total.value = 0
    loadError.value = error instanceof Error ? error.message : '我的商品加载失败'
  } finally {
    loading.value = false
  }
}

function reload(): void {
  const previousPage = filters.pageNum
  filters.pageNum = 1
  // 页码变化时交给 watch 统一加载；页码未变则主动加载，避免重复请求
  if (previousPage === 1) void loadProducts()
}

watch(() => [filters.pageNum, filters.pageSize], () => loadProducts())

function resetFilters(): void {
  filters.keyword = ''
  filters.status = undefined
  filters.auditStatus = undefined
  reload()
}

function resetForm(): void {
  Object.assign(form, {
    categoryId: categoryOptions.value[0]?.id,
    name: '',
    subtitle: '',
    mainImage: '/images/panda-product.svg',
    detailImages: [],
    price: undefined,
    stock: 0,
    description: '',
    sort: 0,
  })
}

async function openCreate(): Promise<void> {
  editingId.value = null
  resetForm()
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

async function openEdit(row: ProductManagementItem): Promise<void> {
  editingId.value = row.id
  loadError.value = ''
  try {
    const { data } = await getMyProductDetail(row.id)
    const detail = data.data
    Object.assign(form, {
      categoryId: detail.categoryId,
      name: detail.name,
      subtitle: detail.subtitle ?? '',
      mainImage: detail.mainImage,
      detailImages: detail.images.slice(1),
      price: detail.price,
      stock: detail.stock,
      description: detail.description ?? '',
      sort: 0,
    })
    formRef.value?.clearValidate()
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '商品详情加载失败')
  }
}

function buildPayload(): ProductFormPayload {
  const detailImages = form.detailImages
    .map((url) => url.trim())
    .filter(Boolean)
    .map((url) => ({ url, type: 'DETAIL' as const }))
  return {
    categoryId: Number(form.categoryId),
    name: form.name.trim(),
    subtitle: form.subtitle.trim() || null,
    mainImage: form.mainImage.trim(),
    detailImages,
    price: Number(form.price),
    stock: Number(form.stock),
    description: form.description.trim() || null,
    sort: Number(form.sort),
  }
}

async function saveProduct(): Promise<void> {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload = buildPayload()
    if (editingId.value) {
      await updateMyProduct(editingId.value, payload)
      ElMessage.success('商品已更新')
    } else {
      await createMyProduct(payload)
      ElMessage.success('商品已创建，请提交审核')
    }
    dialogVisible.value = false
    await loadProducts()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: ProductManagementItem): Promise<void> {
  const target = row.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  try {
    await ElMessageBox.confirm(
      `确定要将该商品${target === 'ON_SALE' ? '上架' : '下架'}吗？`,
      '操作确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    const payload: ProductStatusPayload = { status: target }
    await updateMyProductStatus(row.id, payload)
    ElMessage.success(target === 'ON_SALE' ? '上架成功' : '已下架')
    await loadProducts()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态更新失败')
  }
}

async function submitAudit(row: ProductManagementItem): Promise<void> {
  try {
    await ElMessageBox.confirm('提交后商品进入待审核队列，期间无法上架。确定提交吗？', '操作确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await submitProductAudit(row.id)
    ElMessage.success('已提交审核')
    await loadProducts()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交审核失败')
  }
}

/** 工作台“商品概览”点击后通过 query.edit 指定商品 ID，进入本页自动打开编辑弹窗 */
function openEditFromQuery(): void {
  const raw = route.query.edit
  const editId = Number(Array.isArray(raw) ? raw[0] : raw)
  if (!editId || !Number.isFinite(editId)) return
  const target = products.value.find((item) => item.id === editId)
  if (target) {
    void openEdit(target)
  }
}

onMounted(async () => {
  await loadCategories()
  await loadProducts()
  openEditFromQuery()
})
</script>

<style scoped lang="scss">
.merchant-products-page { padding: 24px; }

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;

  h1 { margin: 0; font-size: 22px; }
  p { margin: 6px 0 0; color: var(--pd-muted); font-size: 13px; }
}

.filter-bar { display: flex; gap: 12px; padding: 14px 18px; margin-bottom: 16px; }
.filter-input { max-width: 280px; }
.filter-select { width: 150px; }

.table-card { padding: 8px 16px 16px; }

.product-cell {
  display: flex;
  align-items: center;
  gap: 10px;

  img { width: 46px; height: 46px; border-radius: 6px; object-fit: cover; background: #f3f4f6; }
  strong { display: block; font-size: 13px; }
  small { color: var(--pd-muted); font-size: 11px; }
}

.pagination { display: flex; justify-content: flex-end; margin-top: 16px; }
.image-rows { width: 100%; }
.image-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.form-hint { margin: 6px 0 0; color: var(--pd-muted); font-size: 12px; }
.load-error { padding: 48px 20px; color: var(--pd-muted); text-align: center; }

@media (max-width: 900px) {
  .filter-bar { flex-wrap: wrap; }
  .page-head { align-items: flex-start; gap: 12px; flex-direction: column; }
}
</style>
