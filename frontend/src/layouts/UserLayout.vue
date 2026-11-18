<template>
  <div class="user-layout">
    <div class="top-strip">
      <div class="container top-strip-inner">
        <span>精选好物 · 放心购 · PandaGo 与你同行</span>
        <span>全场包邮 · 7 天无理由退换 · 模拟支付教学演示</span>
      </div>
    </div>

    <header class="site-header">
      <div class="container header-inner">
        <BrandLogo mode="full" />

        <form class="search-box" @submit.prevent="handleSearch">
          <el-input v-model="keyword" placeholder="搜索好物，例如：蓝牙耳机" clearable>
            <template #append>
              <el-button type="primary" native-type="submit">搜索</el-button>
            </template>
          </el-input>
        </form>

        <nav class="header-links">
          <template v-if="userStore.isLoggedIn">
            <el-dropdown trigger="click" @command="handleUserCommand">
              <span class="user-trigger">
                <UserAvatar :size="30" />
                <span class="user-greeting">{{ userStore.nickname || userStore.username }}</span>
                <span class="caret">▾</span>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                  <el-dropdown-item command="orders">我的订单</el-dropdown-item>
                  <el-dropdown-item command="reviews">我的评价</el-dropdown-item>
                  <el-dropdown-item command="favorites">我的收藏</el-dropdown-item>
                  <el-dropdown-item command="addresses">收货地址</el-dropdown-item>
                  <el-dropdown-item command="cart">购物车</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <router-link to="/cart" class="cart-link">🛒 购物车</router-link>
            <button class="logout-btn" type="button" @click="handleLogout">退出</button>
          </template>
          <template v-else>
            <router-link to="/login">登录</router-link>
            <router-link to="/register">注册</router-link>
            <router-link to="/cart">购物车</router-link>
          </template>
        </nav>
      </div>
    </header>

    <div class="category-bar">
      <div class="container category-inner">
        <router-link to="/products" class="all-category">
          <span class="all-category-icon">☰</span> 全部商品分类
        </router-link>
        <router-link to="/" class="nav-home">首页</router-link>
        <router-link v-for="item in categoryNav" :key="item.label" :to="item.path">
          {{ item.label }}
        </router-link>
      </div>
    </div>

    <main class="site-main">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <footer class="site-footer">
      <div class="container footer-inner">
        <div>
          <strong>PandaGo 熊猫购</strong>
          <p>现代简约电商体验 · 教学演示项目（模拟支付，不涉及真实交易）</p>
        </div>
        <div class="footer-links">
          <router-link to="/">首页</router-link>
          <router-link to="/products">全部商品</router-link>
          <router-link to="/orders">我的订单</router-link>
          <router-link to="/profile">个人中心</router-link>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import BrandLogo from '@/components/BrandLogo.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { getCategories } from '@/api/category'
import type { CategoryVO } from '@/types/catalog'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const keyword = ref('')
const categories = ref<CategoryVO[]>([])

/** 顶部第二行固定分类导航：名称与初始化数据一致，链接用真实分类 id */
const NAV_CATEGORY_NAMES = ['手机数码', '家用电器', '服装鞋包', '食品生鲜', '家居生活', '美妆护肤']
const categoryNav = computed(() =>
  NAV_CATEGORY_NAMES.map((name) => {
    const found = categories.value.find((item) => item.name === name)
    return { label: name, path: found ? `/products?categoryId=${found.id}` : '/products' }
  }),
)

function handleSearch(): void {
  const value = keyword.value.trim()
  router.push({ name: 'product-list', query: value ? { keyword: value } : {} })
}

const userCommandRoutes: Record<string, string> = {
  profile: '/profile',
  orders: '/orders',
  reviews: '/reviews',
  favorites: '/favorites',
  addresses: '/addresses',
  cart: '/cart',
}

function handleUserCommand(command: string): void {
  if (command === 'logout') {
    void handleLogout()
    return
  }
  const path = userCommandRoutes[command]
  if (path) void router.push(path)
}

async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  userStore.logout()
  router.push({ name: 'home' })
}

onMounted(async () => {
  try {
    const { data } = await getCategories()
    categories.value = data.data
  } catch {
    categories.value = []
  }
})
</script>

<style scoped lang="scss">
.user-layout {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
}

.top-strip {
  background: var(--pd-ink);
  color: #d1d5db;
  font-size: 12px;

  .top-strip-inner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 32px;
  }
}

.site-header {
  background: var(--pd-paper);
  border-bottom: 1px solid var(--pd-border);

  .header-inner {
    display: flex;
    align-items: center;
    gap: 36px;
    height: 92px;
  }
}

.search-box {
  flex: 1;
  max-width: 520px;
}

.header-links {
  display: flex;
  align-items: center;
  gap: 18px;
  font-size: 13px;
  color: var(--pd-ink-soft);

  a:hover { color: var(--pd-primary); }

  .user-trigger {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    outline: none;

    &:hover .user-greeting { color: var(--pd-primary); }
  }

  .user-greeting {
    color: var(--pd-ink);
    font-weight: 600;
  }

  .caret {
    color: var(--pd-muted);
    font-size: 10px;
  }

  .cart-link { white-space: nowrap; }

  .logout-btn {
    padding: 3px 10px;
    border: 1px solid var(--pd-border);
    border-radius: 4px;
    background: transparent;
    color: var(--pd-muted);
    font-size: 12px;
    cursor: pointer;

    &:hover { color: var(--pd-primary); border-color: var(--pd-primary); }
  }
}

.category-bar {
  background: var(--pd-paper);
  box-shadow: 0 2px 6px rgb(0 0 0 / 4%);

  .category-inner {
    display: flex;
    align-items: center;
    gap: 26px;
    height: 46px;
    font-size: 13px;
    white-space: nowrap;
  }

  a:hover { color: var(--pd-primary); }
}

.all-category {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  color: #fff;
  background: var(--pd-primary);
  padding: 0 14px;
  height: 46px;

  &:hover { color: #fff; }

  .all-category-icon { font-size: 14px; }
}

.nav-home { font-weight: 600; color: var(--pd-ink); }

.site-main {
  flex: 1;
  padding: 28px 0 56px;
}

.site-footer {
  margin-top: auto;
  padding: 32px 0;
  background: var(--pd-ink);
  color: #d1d5db;

  .footer-inner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
  }

  strong {
    color: #fff;
    font-size: 16px;
  }

  p {
    margin: 6px 0 0;
    font-size: 12px;
  }
}

.footer-links {
  display: flex;
  gap: 20px;
  font-size: 13px;
}

@media (max-width: 900px) {
  .top-strip-inner span:last-child { display: none; }
  .header-inner { gap: 12px; height: auto; padding: 14px 0; flex-wrap: wrap; }
  .search-box { order: 3; max-width: none; width: 100%; }
  .header-links { gap: 10px; font-size: 12px; }
  .header-links .user-greeting { display: none; }
  .category-inner { overflow-x: auto; gap: 18px; height: 44px; }
  .all-category { height: 44px; }
}
</style>
