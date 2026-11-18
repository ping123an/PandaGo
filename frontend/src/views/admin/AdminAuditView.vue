<template>
  <div class="admin-audit-page">
    <div class="page-head">
      <div>
        <h1>商品审核</h1>
        <p>仅管理员可操作；审核状态与上架状态相互独立，驳回后商品强制下架并记录原因。</p>
      </div>
      <div class="head-filters">
        <el-input
          v-model="filters.keyword"
          class="keyword-input"
          placeholder="搜索商品名称"
          clearable
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button @click="reload">查询</el-button>
      </div>
    </div>

    <el-tabs v-model="filters.auditStatus" class="audit-tabs" @tab-change="onTabChange">
      <el-tab-pane label="待审核" name="PENDING" />
      <el-tab-pane label="已通过" name="APPROVED" />
      <el-tab-pane label="已驳回" name="REJECTED" />
    </el-tabs>

    <div v-loading="loading" class="table-card card">
      <div v-if="loadError && !loading" class="load-error">
        <p>{{ loadError }}</p>
        <el-button type="primary" @click="loadProducts">重新加载</el-button>
      </div>
      <el-table
        v-else
        :data="products"
        :row-class-name="rowClassName"
        :empty-text="emptyText"
        stripe
      >
        <el-table-column prop="id" label="ID" width="76" />
        <el-table-column label="商品" min-width="240">
          <template #default="{ row }">
            <div class="product-cell">
              <img :src="row.mainImage" :alt="row.name" />
              <div>
                <strong>{{ row.name }}</strong>
                <small>{{ row.categoryName || '未分类' }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="merchantName" label="商家" min-width="130">
          <template #default="{ row }">{{ row.merchantName || '平台自营' }}</template>
        </el-table-column>
        <el-table-column label="价格" width="110">
          <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="84" />
        <el-table-column label="上架状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ON_SALE' ? 'success' : 'info'" effect="light">
              {{ row.status === 'ON_SALE' ? '已上架' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="auditTagType(row.auditStatus)" effect="dark">
              {{ auditLabel(row.auditStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核记录" min-width="200">
          <template #default="{ row }">
            <div v-if="row.auditStatus === 'PENDING'" class="muted">尚未审核</div>
            <div v-else class="audit-record">
              <span>审核人：管理员 #{{ row.auditBy ?? '—' }}</span>
              <span>时间：{{ formatTime(row.auditTime) }}</span>
              <span v-if="row.auditReason" class="reason">原因：{{ row.auditReason }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="row.auditStatus === 'PENDING'">
              <el-button link type="success" @click="approve(row)">通过</el-button>
              <el-button link type="danger" @click="openReject(row)">驳回</el-button>
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            </template>
            <template v-else>
              <el-button link type="primary" @click="openDetail(row, true)">查看审核记录</el-button>
            </template>
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

    <el-dialog v-model="rejectVisible" title="驳回商品" width="480px" destroy-on-close>
      <p class="reject-target">商品：{{ rejecting?.name }}</p>
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="3"
        maxlength="255"
        show-word-limit
        placeholder="请填写驳回原因，便于商家修改后重新提交"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      :title="detailFocusAudit ? '审核记录' : '商品详情'"
      width="720px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <template v-if="detailRow">
          <div class="detail-summary">
            <img :src="detailRow.mainImage" :alt="detailRow.name" />
            <div class="detail-meta">
              <strong>{{ detailRow.name }}</strong>
              <span>商品 ID：{{ detailRow.id }} ｜ 分类：{{ detailRow.categoryName || '未分类' }}</span>
              <span>商家：{{ detailRow.merchantName || '平台自营' }}</span>
              <span>价格：¥{{ Number(detailRow.price).toFixed(2) }} ｜ 库存：{{ detailRow.stock }}</span>
              <span>上架状态：{{ detailRow.status === 'ON_SALE' ? '已上架' : '已下架' }}</span>
            </div>
          </div>

          <el-descriptions :column="2" border class="audit-info">
            <el-descriptions-item label="审核状态">
              <el-tag :type="auditTagType(detailRow.auditStatus)">{{ auditLabel(detailRow.auditStatus) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="审核人">
              {{ detailRow.auditBy == null ? '—' : `管理员 #${detailRow.auditBy}` }}
            </el-descriptions-item>
            <el-descriptions-item label="审核时间">{{ formatTime(detailRow.auditTime) }}</el-descriptions-item>
            <el-descriptions-item label="商品提交时间">{{ formatTime(detailRow.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="审核意见 / 驳回原因" :span="2">
              {{ detailRow.auditReason || '—' }}
            </el-descriptions-item>
          </el-descriptions>

          <div v-if="detailProduct?.description" class="detail-block">
            <h4>商品介绍</h4>
            <p>{{ detailProduct.description }}</p>
          </div>
          <div v-if="detailImages.length" class="detail-block">
            <h4>商品图片</h4>
            <div class="detail-images">
              <img v-for="(url, index) in detailImages" :key="`${url}-${index}`" :src="url" :alt="`商品图片 ${index + 1}`" />
            </div>
          </div>
          <div v-if="!detailProduct?.description && !detailImages.length" class="muted detail-empty">
            商家未填写商品介绍或详情图
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detailRow?.auditStatus === 'PENDING'"
          type="primary"
          @click="approveFromDetail(detailRow)"
        >通过审核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditProduct, getAdminProductDetail, getAdminProducts } from '@/api/product'
import type {
  ProductAuditPayload,
  ProductDetail,
  ProductManagementItem,
  ProductManagementQuery,
} from '@/types/catalog'

type AuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const loadError = ref('')
const products = ref<ProductManagementItem[]>([])
const total = ref(0)

const rejectVisible = ref(false)
const rejecting = ref<ProductManagementItem | null>(null)
const rejectReason = ref('')

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailFocusAudit = ref(false)
const detailRow = ref<ProductManagementItem | null>(null)
const detailProduct = ref<ProductDetail | null>(null)

const filters = reactive<ProductManagementQuery>({
  keyword: '',
  auditStatus: 'PENDING',
  pageNum: 1,
  pageSize: 10,
})

const emptyText = computed(() => {
  if (filters.keyword?.trim()) return '没有匹配的商品，请调整关键词或审核状态'
  if (filters.auditStatus === 'PENDING') {
    return '暂无待审核商品，所有提交的商品已处理完成'
  }
  if (filters.auditStatus === 'APPROVED') return '暂无已通过审核的商品'
  return '暂无已驳回的商品'
})

const detailImages = computed(() => {
  const all = detailProduct.value?.images ?? []
  const main = detailProduct.value?.mainImage
  // 详情接口返回的 images 含主图，避免重复展示
  return main && all[0] === main ? all : main ? [main, ...all] : all
})

function auditLabel(status: string): string {
  return status === 'APPROVED' ? '已通过' : status === 'REJECTED' ? '已驳回' : '待审核'
}

function auditTagType(status: string): 'success' | 'danger' | 'warning' {
  return status === 'APPROVED' ? 'success' : status === 'REJECTED' ? 'danger' : 'warning'
}

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

function rowClassName({ row }: { row: ProductManagementItem }): string {
  return row.id === Number(route.query.id) ? 'highlight-row' : ''
}

async function loadProducts(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await getAdminProducts({
      keyword: filters.keyword?.trim() || undefined,
      auditStatus: filters.auditStatus,
      pageNum: filters.pageNum,
      pageSize: filters.pageSize,
    })
    products.value = data.data.list
    total.value = data.data.total
  } catch (error) {
    products.value = []
    total.value = 0
    loadError.value = error instanceof Error ? error.message : '商品审核列表加载失败'
  } finally {
    loading.value = false
  }
}

function reload(): void {
  filters.pageNum = 1
  void loadProducts()
}

function onTabChange(): void {
  reload()
}

async function approve(row: ProductManagementItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认审核通过「${row.name}」？通过后仍需商家手动上架才会对顾客展示。`,
      '审核确认',
      { type: 'success' },
    )
  } catch {
    return
  }
  try {
    const payload: ProductAuditPayload = { auditStatus: 'APPROVED' }
    await auditProduct(row.id, payload)
    detailVisible.value = false
    ElMessage.success('审核通过')
    await loadProducts()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核失败')
  }
}

async function approveFromDetail(row: ProductManagementItem | null): Promise<void> {
  if (!row) return
  await approve(row)
}

function openReject(row: ProductManagementItem): void {
  rejecting.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function confirmReject(): Promise<void> {
  if (!rejecting.value) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  submitting.value = true
  try {
    const payload: ProductAuditPayload = {
      auditStatus: 'REJECTED',
      reason: rejectReason.value.trim(),
    }
    await auditProduct(rejecting.value.id, payload)
    rejectVisible.value = false
    ElMessage.success('已驳回，商品已强制下架')
    await loadProducts()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '驳回失败')
  } finally {
    submitting.value = false
  }
}

async function openDetail(row: ProductManagementItem, focusAudit = false): Promise<void> {
  detailRow.value = row
  detailProduct.value = null
  detailFocusAudit.value = focusAudit
  detailVisible.value = true
  detailLoading.value = true
  try {
    const { data } = await getAdminProductDetail(row.id)
    detailProduct.value = data.data
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '商品详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

/** 商品管理页「前往审核」会带 auditStatus / keyword / id 跳转过来 */
function applyRouteQuery(): void {
  const status = route.query.auditStatus
  if (status === 'PENDING' || status === 'APPROVED' || status === 'REJECTED') {
    filters.auditStatus = status as AuditStatus
  }
  const keyword = route.query.keyword
  filters.keyword = typeof keyword === 'string' ? keyword : ''
  filters.pageNum = 1
}

watch(() => [filters.pageNum, filters.pageSize], () => loadProducts())
watch(() => route.query, () => {
  applyRouteQuery()
  void loadProducts()
})

onMounted(() => {
  applyRouteQuery()
  void loadProducts()
})
</script>

<style scoped lang="scss">
.admin-audit-page { padding: 24px; }

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;

  h1 { margin: 0; font-size: 22px; }
  p { margin: 6px 0 0; color: var(--pd-muted); font-size: 13px; }
}

.head-filters { display: flex; gap: 8px; flex-shrink: 0; }
.keyword-input { width: 220px; }
.audit-tabs { margin-bottom: 12px; }
.table-card { padding: 8px 16px 16px; }

.product-cell {
  display: flex;
  align-items: center;
  gap: 10px;

  img { width: 46px; height: 46px; border-radius: 6px; object-fit: cover; background: #f3f4f6; }
  strong { display: block; font-size: 13px; }
  small { color: var(--pd-muted); font-size: 11px; }
}

.audit-record {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: var(--pd-text);

  .reason { color: var(--pd-danger, #f56c6c); }
}

.muted { color: var(--pd-muted); font-size: 12px; }
.reject-target { margin: 0 0 12px; font-size: 13px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 16px; }
.load-error { padding: 48px 20px; color: var(--pd-muted); text-align: center; }

.detail-summary {
  display: flex;
  gap: 14px;
  padding-bottom: 14px;

  img { width: 110px; height: 110px; object-fit: cover; border-radius: 8px; background: #f3f4f6; }
  .detail-meta { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: var(--pd-text); }
  strong { font-size: 16px; }
}
.audit-info { margin-bottom: 16px; }
.detail-block { margin-top: 14px; }
.detail-block h4 { margin: 0 0 8px; font-size: 14px; }
.detail-block p { margin: 0; font-size: 13px; line-height: 1.7; white-space: pre-wrap; }
.detail-images { display: flex; flex-wrap: wrap; gap: 10px; }
.detail-images img { width: 120px; height: 120px; object-fit: cover; border-radius: 6px; }
.detail-empty { padding: 12px 0; }
:deep(.highlight-row) { background: #fff8e6; }
</style>
