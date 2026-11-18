<template>
  <div class="home-page">
    <section class="hero">
      <div class="container hero-inner">
        <div class="hero-copy">
          <span class="hero-tag">PANDA GO · 熊猫购</span>
          <h1>一只靠谱的购物熊猫<br />把好物送到你面前</h1>
          <p>清晰的商品信息、顺畅的下单流程。现代简约电商体验，从 PandaGo 开始。</p>
          <div class="hero-actions">
            <el-button type="primary" size="large" @click="router.push('/products')">开始逛逛</el-button>
            <el-button size="large" @click="scrollToCategories">查看分类</el-button>
          </div>
          <div class="hero-points">
            <span>✓ 精选好物</span>
            <span>✓ 下单顺畅</span>
            <span>✓ 安心售后</span>
          </div>
        </div>

        <div class="hero-art" aria-hidden="true">
          <div class="hero-circle">
            <div class="hero-brand-mark">
              <BrandLogo mode="icon" :size="150" />
            </div>
          </div>
        </div>
      </div>
    </section>

    <section id="categories" class="container section">
      <div class="section-title">
        <h2>商品分类</h2>
        <span>六大类目，覆盖日常所需</span>
      </div>
      <div v-loading="categoryLoading" class="category-grid">
        <template v-if="!categoryLoading && categories.length">
          <router-link
            v-for="(item, index) in categories"
            :key="item.id"
            :to="{ name: 'product-list', query: { categoryId: item.id } }"
            class="category-card card"
          >
            <span class="category-icon" :style="{ background: categoryColors[index % categoryColors.length] }">
              {{ item.name.slice(0, 2) }}
            </span>
            <strong>{{ item.name }}</strong>
          </router-link>
        </template>
        <el-empty v-else-if="!categoryLoading" description="暂无可用分类" />
      </div>
      <p v-if="categoryError" class="section-error">{{ categoryError }}</p>
    </section>

    <section class="container section">
      <div class="section-title">
        <h2>热门推荐</h2>
        <router-link to="/products" class="more">查看全部 →</router-link>
      </div>
      <div v-loading="hotLoading" class="shelf-grid">
        <template v-if="!hotLoading && hotProducts.length">
          <ProductCard v-for="product in hotProducts" :key="product.id" :product="product" />
        </template>
        <el-empty v-else-if="!hotLoading" description="暂无热门商品，请稍后再来" />
      </div>
      <p v-if="hotError" class="section-error">{{ hotError }}</p>
    </section>

    <section class="container section">
      <div class="section-title">
        <h2>为你推荐</h2>
        <router-link to="/products" class="more">发现更多好物 →</router-link>
      </div>
      <div v-loading="recommendLoading" class="shelf-grid">
        <template v-if="!recommendLoading && recommendProducts.length">
          <ProductCard v-for="product in recommendProducts" :key="product.id" :product="product" />
        </template>
        <el-empty v-else-if="!recommendLoading" description="暂无推荐商品" />
      </div>
      <p v-if="recommendError" class="section-error">{{ recommendError }}</p>
    </section>

    <section class="container promises">
      <div v-for="item in promises" :key="item.title" class="promise card">
        <span class="promise-icon">✓</span>
        <div>
          <strong>{{ item.title }}</strong>
          <small>{{ item.desc }}</small>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getCategories } from '@/api/category'
import { getHotProducts, getProducts } from '@/api/product'
import BrandLogo from '@/components/BrandLogo.vue'
import ProductCard from '@/components/ProductCard.vue'
import type { CategoryVO, ProductListItem } from '@/types/catalog'

const router = useRouter()
const categories = ref<CategoryVO[]>([])
const categoryLoading = ref(true)
const categoryError = ref('')
const hotProducts = ref<ProductListItem[]>([])
const hotLoading = ref(true)
const hotError = ref('')
const recommendProducts = ref<ProductListItem[]>([])
const recommendLoading = ref(true)
const recommendError = ref('')

const categoryColors = ['#e1251b', '#2563eb', '#7c3aed', '#16a34a', '#d97706', '#db2777']

const promises = [
  { title: '正品保障', desc: '商品信息清晰透明' },
  { title: '极速配送', desc: '物流状态随时可查' },
  { title: '安心售后', desc: '评价与售后流程完整' },
  { title: '模拟支付', desc: '教学演示，不涉及真实资金' },
]

function scrollToCategories(): void {
  document.getElementById('categories')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(async () => {
  try {
    const { data } = await getCategories()
    categories.value = data.data
    categoryError.value = ''
  } catch (error) {
    categories.value = []
    categoryError.value = error instanceof Error ? error.message : '分类加载失败'
  } finally {
    categoryLoading.value = false
  }

  try {
    const { data } = await getHotProducts(4)
    hotProducts.value = data.data
    hotError.value = ''
  } catch (error) {
    hotProducts.value = []
    hotError.value = error instanceof Error ? error.message : '热门商品加载失败'
  } finally {
    hotLoading.value = false
  }

  try {
    const { data } = await getProducts({ pageNum: 1, pageSize: 8, sort: 'sales', order: 'desc' })
    recommendProducts.value = data.data.list
    recommendError.value = ''
  } catch (error) {
    recommendProducts.value = []
    recommendError.value = error instanceof Error ? error.message : '推荐商品加载失败'
  } finally {
    recommendLoading.value = false
  }
})
</script>

<style scoped lang="scss">
.hero {
  overflow: hidden;
  background: linear-gradient(120deg, #1f2329 0%, #2f3640 58%, #3b302c 100%);
  color: #fff;
}

.hero-inner {
  display: flex;
  min-height: 380px;
  align-items: center;
  justify-content: space-between;
  gap: 40px;
}

.hero-copy {
  max-width: 620px;
  padding: 48px 0;
}

.hero-tag {
  display: inline-block;
  padding: 4px 12px;
  border: 1px solid rgb(255 255 255 / 25%);
  border-radius: 999px;
  color: #fca5a5;
  font-size: 12px;
  letter-spacing: 2px;
}

h1 {
  margin: 20px 0 14px;
  font-size: clamp(30px, 4vw, 46px);
  line-height: 1.25;
  letter-spacing: -1px;
}

p {
  max-width: 500px;
  margin: 0;
  color: #d1d5db;
  font-size: 15px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  margin-top: 30px;
}

.hero-points {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 24px;
  color: #d1d5db;
  font-size: 13px;

  span { display: inline-flex; align-items: center; gap: 4px; }
}

.hero-art {
  display: grid;
  flex: 0 0 320px;
  place-items: center;
}

.hero-circle {
  position: relative;
  width: 280px;
  height: 280px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #fff 0%, #f3f4f6 60%, #d1d5db 100%);
  box-shadow: 0 20px 60px rgb(0 0 0 / 35%);
}

.hero-brand-mark {
  position: absolute;
  top: 50%;
  left: 50%;
  display: grid;
  width: 100%;
  height: 100%;
  transform: translate(-50%, -50%);
  place-items: center;

  // BrandLogo 默认链接到首页，此处纯展示：去掉交互与指针
  :deep(.brand-logo) { pointer-events: none; }
}

.section { padding-top: 44px; scroll-margin-top: 24px; }

// 分类区：标题与卡片更紧凑，避免删除数量文字后留下过多空白
#categories.section { padding-top: 32px; }
#categories .section-title { margin-bottom: 14px; }

.category-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 14px;
}

.category-card {
  display: flex;
  min-height: 132px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 18px 10px;
  text-align: center;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;

  &:hover {
    transform: translateY(-3px);
    border-color: var(--pd-primary);
    box-shadow: 0 10px 24px rgb(225 37 27 / 12%);
  }

  strong {
    margin-top: 14px;
    color: var(--pd-ink);
    font-size: 15px;
    line-height: 1.4;
  }
}

.category-icon {
  display: grid;
  width: 52px;
  height: 52px;
  flex: none;
  place-items: center;
  border-radius: 14px;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 1px;
}

.shelf-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.more {
  color: var(--pd-primary);
  font-size: 13px;
}

.more:hover { text-decoration: underline; }

.section-error {
  margin: 10px 0 0;
  color: var(--pd-danger, #dc2626);
  font-size: 12px;
}

.promises {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin: 44px auto 0;
}

.promise {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px;

  strong { display: block; font-size: 14px; }
  small { color: var(--pd-muted); font-size: 12px; }
}

.promise-icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: none;
  place-items: center;
  border-radius: 50%;
  background: rgb(225 37 27 / 10%);
  color: var(--pd-primary);
  font-weight: 700;
}

@media (max-width: 1024px) {
  .category-grid { grid-template-columns: repeat(3, 1fr); }
  .shelf-grid { grid-template-columns: repeat(2, 1fr); }
  .promises { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .hero-inner { flex-direction: column; padding-bottom: 32px; }
  .hero-art { display: none; }
  .category-grid { grid-template-columns: repeat(2, 1fr); }
  .shelf-grid { grid-template-columns: repeat(2, 1fr); }
  .promises { grid-template-columns: 1fr; }
}
</style>
