<template>
  <div class="dashboard-layout">
    <aside class="sidebar">
      <div class="sidebar-logo">
        <BrandLogo mode="icon" :size="32" />
        <span>{{ groupTitle }}</span>
      </div>

      <nav class="side-menu">
        <router-link
          v-for="item in menus"
          :key="item.path"
          :to="item.path"
          class="menu-item"
          :class="{ active: isActive(item.path) }"
        >
          <span class="menu-dot" />
          {{ item.title }}
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <router-link to="/">← 返回商城首页</router-link>
      </div>
    </aside>

    <div class="dashboard-body">
      <header class="dashboard-header">
        <div class="breadcrumb">PandaGo / {{ groupTitle }} / <strong>{{ currentTitle }}</strong></div>
        <div class="header-user">
          <!-- 多角色账号：仅展示该账号真实拥有的角色工作空间，供切换 -->
          <router-link
            v-if="userStore.hasRole('ADMIN') && route.meta.group === 'merchant'"
            to="/admin"
            class="switch-space"
          >切换到平台管理</router-link>
          <router-link
            v-if="userStore.hasRole('MERCHANT') && route.meta.group === 'admin'"
            to="/merchant"
            class="switch-space"
          >切换到商家中心</router-link>
          <!-- 商家中心：右上角头像与店铺资料共用 merchant store 的 logo；平台管理端保持原有用户头像 -->
          <MerchantAvatar v-if="route.meta.group === 'merchant'" :size="32" />
          <span v-else class="avatar">熊</span>
          <span>{{ route.meta.group === 'merchant' && merchantStore.storeName ? merchantStore.storeName : (userStore.nickname || groupTitle) }}</span>
          <button class="logout-btn" type="button" @click="handleLogout">退出登录</button>
        </div>
      </header>

      <main class="dashboard-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandLogo from '@/components/BrandLogo.vue'
import MerchantAvatar from '@/components/MerchantAvatar.vue'
import { useMerchantStore } from '@/stores/merchant'
import { useUserStore } from '@/stores/user'

interface MenuItem {
  path: string
  title: string
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const merchantStore = useMerchantStore()

// 进入商家中心任一页面（工作台/商品/订单/资料）即同步店铺头像；刷新后从后端取最新值
onMounted(() => {
  if (route.meta.group === 'merchant') void merchantStore.fetchProfile()
})
watch(() => route.meta.group, (group) => {
  if (group === 'merchant') void merchantStore.fetchProfile()
})

const merchantMenus: MenuItem[] = [
  { path: '/merchant', title: '工作台' },
  { path: '/merchant/products', title: '我的商品' },
  { path: '/merchant/orders', title: '订单管理' },
  { path: '/merchant/profile', title: '商家资料' },
]

const adminMenus: MenuItem[] = [
  { path: '/admin', title: '工作台' },
  { path: '/admin/users', title: '用户管理' },
  { path: '/admin/categories', title: '分类管理' },
  { path: '/admin/audit', title: '商品审核' },
  { path: '/admin/products', title: '商品管理' },
  { path: '/admin/orders', title: '订单管理' },
]

const menus = computed(() => (route.meta.group === 'admin' ? adminMenus : merchantMenus))
const groupTitle = computed(() => (route.meta.group === 'admin' ? '平台管理' : '商家中心'))
const currentTitle = computed(() => route.meta.title ?? '工作台')

function isActive(path: string): boolean {
  return path.includes('/', 1)
    ? route.path === path || route.path.startsWith(`${path}/`)
    : route.path === path
}

function handleLogout(): void {
  merchantStore.reset()
  userStore.logout()
  router.push({ name: 'home' })
}
</script>

<style scoped lang="scss">
.dashboard-layout {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  display: flex;
  width: 220px;
  flex: none;
  flex-direction: column;
  background: var(--pd-ink);
  color: #e5e7eb;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 68px;
  padding: 0 18px;
  border-bottom: 1px solid rgb(255 255 255 / 10%);
  font-size: 16px;
  font-weight: 700;
}

.side-menu {
  flex: 1;
  padding: 16px 10px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  border-radius: 6px;
  color: #d1d5db;
  font-size: 14px;

  &:hover { background: rgb(255 255 255 / 8%); color: #fff; }

  &.active {
    background: var(--pd-primary);
    color: #fff;
  }
}

.menu-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.8;
}

.sidebar-footer {
  padding: 16px 20px;
  border-top: 1px solid rgb(255 255 255 / 10%);
  font-size: 12px;
  color: #9ca3af;
}

.dashboard-body {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.dashboard-header {
  display: flex;
  height: 68px;
  flex: none;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background: var(--pd-paper);
  border-bottom: 1px solid var(--pd-border);
  font-size: 13px;
}

.breadcrumb { color: var(--pd-muted); }

.header-user {
  display: flex;
  align-items: center;
  gap: 10px;

  .switch-space {
    margin-right: 8px;
    color: var(--pd-primary);
    font-size: 12px;
    text-decoration: none;

    &:hover { text-decoration: underline; }
  }

  .logout-btn {
    margin-left: 8px;
    padding: 4px 10px;
    border: 1px solid var(--pd-border);
    border-radius: 4px;
    background: transparent;
    color: var(--pd-muted);
    font-size: 12px;
    cursor: pointer;

    &:hover { color: var(--pd-primary); border-color: var(--pd-primary); }
  }
}

.avatar {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 50%;
  background: var(--pd-primary);
  color: #fff;
  font-size: 13px;
}

.dashboard-main {
  flex: 1;
}

@media (max-width: 760px) {
  .sidebar { width: 160px; }
  .dashboard-header { padding: 0 14px; }
}
</style>
