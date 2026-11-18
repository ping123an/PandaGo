<template>
  <div class="product-list-page container">
    <div class="page-head">
      <h1>{{ selectedCategoryName }}</h1>
      <p>共 {{ total }} 件商品 · 只展示审核通过且已上架的商品</p>
    </div>

    <div class="filter-bar card">
      <form class="filter-row search-row" @submit.prevent="applyKeyword">
        <span class="filter-label">搜索</span>
        <el-input
          v-model="keywordInput"
          clearable
          maxlength="50"
          placeholder="输入商品名称或简介关键词"
          class="keyword-input"
          @keyup.enter="applyKeyword"
        />
        <el-button type="primary" native-type="submit">搜索</el-button>
        <el-button @click="resetFilters">重置筛选</el-button>
      </form>

      <div class="filter-row">
        <span class="filter-label">分类</span>
        <div class="chips">
          <button class="chip" :class="{ active: filters.categoryId === null }" @click="selectCategory(null)">
            全部
          </button>
          <button
            v-for="option in categoryOptions"
            :key="option.id"
            class="chip"
            :class="{ active: filters.categoryId === option.id }"
            @click="selectCategory(option.id)"
          >
            {{ option.name }}
          </button>
        </div>
      </div>

      <div class="filter-row">
        <span class="filter-label">价格</span>
        <div class="price-range">
          <el-input-number v-model="minPrice" :min="0" :controls="false" placeholder="最低价" />
          <span>—</span>
          <el-input-number v-model="maxPrice" :min="0" :controls="false" placeholder="最高价" />
          <el-button size="small" @click="applyPrice">应用</el-button>
        </div>
      </div>

      <div class="filter-row">
        <span class="filter-label">排序</span>
        <div class="chips">
          <button
            v-for="option in sortOptions"
            :key="option.value"
            class="chip"
            :class="{ active: filters.sort === option.value }"
            @click="changeSort(option.value)"
          >
            {{ option.label }}
          </button>
          <button class="chip order" @click="toggleOrder">
            {{ filters.order === 'desc' ? '↓ 降序' : '↑ 升序' }}
          </button>
        </div>
      </div>

      <p v-if="filterError" class="filter-error">{{ filterError }}</p>
    </div>

    <div v-loading="loading" class="grid-wrap">
      <div v-if="!loading && products.length" class="product-grid">
        <ProductCard v-for="product in products" :key="product.id" :product="product" />
      </div>
      <el-empty v-else-if="!loading && !loadError" description="没有找到符合条件的商品" />
      <div v-else-if="!loading && loadError" class="load-error">
        <p>{{ loadError }}</p>
        <el-button type="primary" @click="loadProducts">重新加载</el-button>
      </div>
    </div>

    <div v-if="total > 0" class="pagination">
      <el-pagination
        v-model:current-page="filters.pageNum"
        v-model:page-size="filters.pageSize"
        :total="total"
        :page-sizes="[8, 12, 20]"
        layout="total, sizes, prev, pager, next, jumper"
        background
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getCategories } from '@/api/category'
import { getProducts } from '@/api/product'
import ProductCard from '@/components/ProductCard.vue'
import type { CategoryVO, ProductListItem, ProductQuery, ProductSort } from '@/types/catalog'

const route = useRoute()

const categories = ref<CategoryVO[]>([])
const products = ref<ProductListItem[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const filterError = ref('')

const keywordInput = ref('')
const minPrice = ref<number | undefined>()
const maxPrice = ref<number | undefined>()

const filters = reactive<ProductQuery>({
  keyword: undefined,
  categoryId: undefined,
  minPrice: undefined,
  maxPrice: undefined,
  sort: 'default',
  order: 'desc',
  pageNum: 1,
  pageSize: 12,
})

const sortOptions: Array<{ value: ProductSort; label: string }> = [
  { value: 'default', label: '综合' },
  { value: 'price', label: '价格' },
  { value: 'sales', label: '销量' },
  { value: 'newest', label: '上新' },
]

/** route query 同步期间暂停本地 watch，避免一次导航触发两次请求 */
let routeSyncing = false

/** 一级 + 二级分类展平为筛选按钮，二级按钮保留父分类前缀，避免同名混淆。 */
const categoryOptions = computed(() => categories.value.flatMap((parent) => [
  { id: parent.id, name: parent.name },
  ...parent.children.map((child) => ({ id: child.id, name: `${parent.name} / ${child.name}` })),
]))

const selectedCategoryName = computed(() => {
  if (filters.categoryId === undefined) return '全部商品'
  return categoryOptions.value.find((item) => item.id === filters.categoryId)?.name ?? '商品列表'
})

function selectCategory(id: number | null): void {
  filters.categoryId = id ?? undefined
  filters.pageNum = 1
}

function changeSort(value: ProductSort): void {
  filters.sort = value
  filters.pageNum = 1
}

function toggleOrder(): void {
  filters.order = filters.order === 'desc' ? 'asc' : 'desc'
  filters.pageNum = 1
}

function applyKeyword(): void {
  filters.keyword = keywordInput.value.trim() || undefined
  filters.pageNum = 1
}

function applyPrice(): void {
  if (minPrice.value !== undefined && maxPrice.value !== undefined && minPrice.value > maxPrice.value) {
    filterError.value = '最低价不能大于最高价'
    return
  }
  filterError.value = ''
  filters.minPrice = minPrice.value
  filters.maxPrice = maxPrice.value
  filters.pageNum = 1
}

function resetFilters(): void {
  keywordInput.value = ''
  minPrice.value = undefined
  maxPrice.value = undefined
  Object.assign(filters, {
    keyword: undefined,
    categoryId: undefined,
    minPrice: undefined,
    maxPrice: undefined,
    sort: 'default',
    order: 'desc',
    pageNum: 1,
    pageSize: 12,
  } as ProductQuery)
}

async function loadCategories(): Promise<void> {
  try {
    const { data } = await getCategories()
    categories.value = data.data
  } catch {
    categories.value = []
  }
}

async function loadProducts(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await getProducts({ ...filters })
    products.value = data.data.list
    total.value = data.data.total
  } catch (error) {
    products.value = []
    total.value = 0
    loadError.value = error instanceof Error ? error.message : '商品加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/** 顶部搜索框或分类入口通过 query 进入本页面时，同步到本地筛选状态。 */
watch(
  () => ({ ...route.query }),
  (query) => {
    routeSyncing = true
    const keyword = typeof query.keyword === 'string' ? query.keyword : ''
    keywordInput.value = keyword
    filters.keyword = keyword || undefined
    const categoryId = typeof query.categoryId === 'string' ? Number(query.categoryId) : NaN
    filters.categoryId = Number.isFinite(categoryId) && categoryId > 0 ? categoryId : undefined
    filters.pageNum = 1
    loadProducts()
    void nextTick(() => { routeSyncing = false })
  },
)

watch(
  () => [filters.categoryId, filters.sort, filters.order, filters.pageNum, filters.pageSize],
  () => {
    if (!routeSyncing) loadProducts()
  },
)

onMounted(async () => {
  const keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  const categoryId = typeof route.query.categoryId === 'string' ? Number(route.query.categoryId) : NaN
  keywordInput.value = keyword
  filters.keyword = keyword || undefined
  filters.categoryId = Number.isFinite(categoryId) && categoryId > 0 ? categoryId : undefined
  await Promise.all([loadCategories(), loadProducts()])
})
</script>

<style scoped lang="scss">
.product-list-page { padding-top: 24px; padding-bottom: 56px; }

.page-head {
  margin-bottom: 18px;

  h1 { margin: 0; font-size: 24px; }
  p { margin: 6px 0 0; color: var(--pd-muted); font-size: 13px; }
}

.filter-bar { padding: 14px 18px; margin-bottom: 20px; }

.filter-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 0;

  & + & { border-top: 1px dashed var(--pd-border); }
}

.search-row .keyword-input { max-width: 420px; }

.filter-label {
  flex: none;
  width: 36px;
  color: var(--pd-muted);
  font-size: 13px;
}

.chips { display: flex; flex-wrap: wrap; gap: 8px; }

.chip {
  padding: 5px 14px;
  border: 1px solid var(--pd-border);
  border-radius: 999px;
  background: #fff;
  color: var(--pd-ink-soft);
  font-size: 13px;
  cursor: pointer;

  &:hover { border-color: var(--pd-primary); color: var(--pd-primary); }

  &.active {
    border-color: var(--pd-primary);
    background: var(--pd-primary);
    color: #fff;
  }
}

.chip.order { font-variant-numeric: tabular-nums; }

.price-range { display: flex; align-items: center; gap: 10px; }
.price-range :deep(.el-input-number) { width: 150px; }

.filter-error {
  margin: 8px 0 0;
  color: #dc2626;
  font-size: 12px;
}

.grid-wrap { min-height: 320px; }

.load-error {
  padding: 60px 20px;
  color: var(--pd-muted);
  text-align: center;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.pagination { display: flex; justify-content: center; margin-top: 28px; }

@media (max-width: 1024px) { .product-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 768px) {
  .product-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filter-row { align-items: flex-start; flex-wrap: wrap; }
  .price-range { flex-wrap: wrap; }
}
</style>
