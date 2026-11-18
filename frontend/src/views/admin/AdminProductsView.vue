<template>
  <div class="admin-products-page">
    <div class="page-head">
      <div>
        <h1>平台商品管理</h1>
        <p>全平台商品查询与上下架；待审核 / 已驳回商品不能上架，审核操作请在「商品审核」菜单完成。</p>
      </div>
    </div>

    <div class="filter-card card">
      <label class="filter-field">
        <span>商品关键词</span>
        <el-input
          v-model="filters.keyword"
          class="filter-input"
          placeholder="商品关键词"
          clearable
          @keyup.enter="reload"
          @clear="reload"
        />
      </label>
      <label class="filter-field">
        <span>上架状态</span>
        <el-select v-model="filters.status" class="filter-select" clearable placeholder="全部上架状态" @change="reload">
          <el-option label="已上架" value="ON_SALE" />
          <el-option label="已下架" value="OFF_SALE" />
        </el-select>
      </label>
      <label class="filter-field">
        <span>审核状态</span>
        <el-select v-model="filters.auditStatus" class="filter-select" clearable placeholder="全部审核状态" @change="reload">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
      </label>
      <label class="filter-field">
        <span>商品分类</span>
        <el-select v-model="filters.categoryId" class="filter-select" clearable filterable placeholder="全部分类" @change="reload">
          <el-option v-for="item in categoryOptions" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
      </label>
      <div class="filter-actions">
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </div>

    <div v-loading="loading" class="table-card card">
      <div v-if="loadError && !loading" class="load-error">
        <p>{{ loadError }}</p>
        <el-button type="primary" @click="loadProducts">重新加载</el-button>
      </div>
      <el-table v-else :data="products" :empty-text="emptyText" stripe>
        <el-table-column prop="id" label="ID" width="76" />
        <el-table-column label="商品" min-width="230">
          <template #default="{ row }">
            <div class="product-cell">
              <img :src="row.mainImage" :alt="row.name" />
              <div>
                <strong :title="row.name">{{ row.name }}</strong>
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
            <el-tag :type="auditTag(row.auditStatus)" effect="dark">
              {{ auditLabel(row.auditStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ON_SALE'"
              link
              type="danger"
              @click="toggleSale(row, 'OFF_SALE')"
            >下架</el-button>
            <el-button
              v-else-if="row.auditStatus === 'APPROVED'"
              link
              type="success"
              @click="toggleSale(row, 'ON_SALE')"
            >上架</el-button>
            <el-button
              v-if="row.auditStatus !== 'APPROVED'"
              link
              type="warning"
              @click="goAudit(row)"
            >前往审核</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminCategories } from '@/api/category'
import { getAdminProducts, updateAdminProductStatus } from '@/api/product'
import type { CategoryVO, ProductManagementItem, ProductManagementQuery } from '@/types/catalog'

type AuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
type SaleStatus = 'ON_SALE' | 'OFF_SALE'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const products = ref<ProductManagementItem[]>([])
const categories = ref<CategoryVO[]>([])
const total = ref(0)

const filters = reactive<ProductManagementQuery>({
  keyword: '',
  status: undefined,
  auditStatus: undefined,
  categoryId: undefined,
  pageNum: 1,
  pageSize: 10,
})

const categoryOptions = computed(() => {
  const options: Array<{ id: number; label: string }> = []
  for (const parent of categories.value) {
    options.push({ id: parent.id, label: parent.name })
    for (const child of parent.children ?? []) {
      options.push({ id: child.id, label: `${parent.name} / ${child.name}` })
    }
  }
  return options
})

const emptyText = computed(() => {
  const hasFilter = Boolean(filters.keyword?.trim() || filters.status || filters.auditStatus || filters.categoryId)
  return hasFilter ? '当前筛选条件下没有商品' : '暂无商品'
})

function auditTag(status: string): string {
  return status === 'APPROVED' ? 'success' : status === 'REJECTED' ? 'danger' : 'warning'
}
function auditLabel(status: string): string {
  return status === 'APPROVED' ? '已通过' : status === 'REJECTED' ? '已驳回' : '待审核'
}

async function loadCategories(): Promise<void> {
  try {
    const { data } = await getAdminCategories()
    categories.value = data.data
  } catch {
    categories.value = []
  }
}

async function loadProducts(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await getAdminProducts({
      keyword: filters.keyword?.trim() || undefined,
      status: filters.status,
      auditStatus: filters.auditStatus,
      categoryId: filters.categoryId,
      pageNum: filters.pageNum,
      pageSize: filters.pageSize,
    })
    products.value = data.data.list
    total.value = data.data.total
  } catch (error) {
    products.value = []
    total.value = 0
    loadError.value = error instanceof Error ? error.message : '商品列表加载失败'
  } finally {
    loading.value = false
  }
}

function reload(): void {
  filters.pageNum = 1
  void loadProducts()
}

function resetFilters(): void {
  filters.keyword = ''
  filters.status = undefined
  filters.auditStatus = undefined
  filters.categoryId = undefined
  reload()
}

/** 平台管理端上下架复用已有 /admin/products/{id}/status 接口 */
async function toggleSale(row: ProductManagementItem, status: SaleStatus): Promise<void> {
  try {
    await ElMessageBox.confirm(
      status === 'ON_SALE'
        ? `确认上架「${row.name}」？上架后顾客可见。`
        : `确认下架「${row.name}」？下架后顾客不可购买。`,
      '操作确认',
      { type: status === 'ON_SALE' ? 'success' : 'warning' },
    )
  } catch {
    return
  }
  try {
    await updateAdminProductStatus(row.id, { status })
    ElMessage.success(status === 'ON_SALE' ? '已上架' : '已下架')
    await loadProducts()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

/** 跳转到商品审核页，并按当前商品定位对应审核状态与关键词 */
function goAudit(row: ProductManagementItem): void {
  const auditStatus = (row.auditStatus === 'REJECTED' ? 'REJECTED' : 'PENDING') as AuditStatus
  void router.push({
    name: 'admin-audit',
    query: { auditStatus, keyword: row.name, id: String(row.id) },
  })
}

watch(() => [filters.pageNum, filters.pageSize], () => loadProducts())

onMounted(() => {
  void loadCategories()
  void loadProducts()
})
</script>

<style scoped lang="scss">
.page-head {
  margin-bottom: 18px;

  h1 { margin: 0 0 6px; font-size: 22px; }
  p { margin: 0; color: var(--pd-muted); font-size: 13px; }
}
.card { background: #fff; border: 1px solid var(--pd-border); border-radius: 12px; padding: 18px; }
.filter-card {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: flex-end;
  margin-bottom: 14px;
}
.filter-field { display: flex; flex-direction: column; gap: 6px; }
.filter-field > span { font-size: 12px; color: var(--pd-muted); }
.filter-actions { display: flex; gap: 8px; padding-bottom: 1px; }
.filter-input { width: 220px; }
.filter-select { width: 170px; }
.table-card { padding: 8px 16px 16px; }
.product-cell { display: flex; align-items: center; gap: 10px; }
.product-cell img { width: 48px; height: 48px; object-fit: cover; border-radius: 6px; flex-shrink: 0; }
.product-cell div { display: flex; flex-direction: column; min-width: 0; }
.product-cell strong { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.product-cell small { color: var(--pd-muted); font-size: 11px; }
.pagination { margin-top: 16px; justify-content: flex-end; display: flex; }
.load-error { padding: 48px 20px; color: var(--pd-muted); text-align: center; }
</style>
