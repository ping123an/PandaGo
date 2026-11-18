import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import UserLayout from '@/layouts/UserLayout.vue'
import DashboardLayout from '@/layouts/DashboardLayout.vue'
import HomeView from '@/views/user/HomeView.vue'
import IdentitySelectView from '@/views/user/IdentitySelectView.vue'
import LoginView from '@/views/user/LoginView.vue'
import RegisterView from '@/views/user/RegisterView.vue'
import ProductListView from '@/views/user/ProductListView.vue'
import ProductDetailView from '@/views/user/ProductDetailView.vue'
import ProfileView from '@/views/user/ProfileView.vue'
import NotFoundView from '@/views/error/NotFoundView.vue'
import MerchantProductsView from '@/views/merchant/MerchantProductsView.vue'
import AdminAuditView from '@/views/admin/AdminAuditView.vue'
import CartView from '@/views/user/CartView.vue'
import FavoriteListView from '@/views/user/FavoriteListView.vue'
import AddressListView from '@/views/user/AddressListView.vue'
import CheckoutView from '@/views/user/CheckoutView.vue'
import OrderSuccessView from '@/views/user/OrderSuccessView.vue'
import OrderListView from '@/views/user/OrderListView.vue'
import OrderDetailView from '@/views/user/OrderDetailView.vue'
import PaymentView from '@/views/user/PaymentView.vue'
import PaymentSuccessView from '@/views/user/PaymentSuccessView.vue'
import MyReviewsView from '@/views/user/MyReviewsView.vue'
import MerchantOrdersView from '@/views/merchant/MerchantOrdersView.vue'
import MerchantDashboardView from '@/views/merchant/MerchantDashboardView.vue'
import MerchantProfileView from '@/views/merchant/MerchantProfileView.vue'
import AdminUsersView from '@/views/admin/AdminUsersView.vue'
import AdminDashboardView from '@/views/admin/AdminDashboardView.vue'
import AdminCategoriesView from '@/views/admin/AdminCategoriesView.vue'
import AdminProductsView from '@/views/admin/AdminProductsView.vue'
import AdminOrdersView from '@/views/admin/AdminOrdersView.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: UserLayout,
    meta: { group: 'user' },
    children: [
      { path: '', name: 'home', component: HomeView, meta: { title: '首页', group: 'user' } },
      // 身份选择页：/login 展示了 USER/MERCHANT/ADMIN 三个入口
      { path: 'login', name: 'login', component: IdentitySelectView, meta: { title: '选择登录身份', group: 'user' } },
      // 具体身份登录页：/login/user | /login/merchant | /login/admin
      {
        path: 'login/:identity',
        name: 'login-by-identity',
        component: LoginView,
        meta: { title: '登录', group: 'user' },
      },
      { path: 'register', name: 'register', component: RegisterView, meta: { title: '注册', group: 'user' } },
      {
        path: 'products',
        name: 'product-list',
        component: ProductListView,
        meta: { title: '商品列表', group: 'user' },
      },
      {
        path: 'products/:id',
        name: 'product-detail',
        component: ProductDetailView,
        meta: { title: '商品详情', group: 'user' },
      },
      {
        path: 'cart',
        name: 'cart',
        component: CartView,
        meta: { title: '购物车', group: 'user', requiresAuth: true },
      },
      {
        path: 'checkout',
        name: 'checkout',
        component: CheckoutView,
        meta: { title: '确认订单', group: 'user', requiresAuth: true },
      },
      {
        // 立即购买：与购物车结算共用确认订单页，通过路由名区分数据来源（不读写购物车）
        path: 'checkout/buy-now',
        name: 'checkout-buy-now',
        component: CheckoutView,
        meta: { title: '确认订单', group: 'user', requiresAuth: true },
      },
      {
        path: 'order-success/:orderNo',
        name: 'order-success',
        component: OrderSuccessView,
        meta: { title: '下单成功', group: 'user', requiresAuth: true },
      },
      {
        path: 'orders',
        name: 'orders',
        component: OrderListView,
        meta: { title: '我的订单', group: 'user', requiresAuth: true },
      },
      {
        path: 'orders/:id',
        name: 'order-detail',
        component: OrderDetailView,
        meta: { title: '订单详情', group: 'user', requiresAuth: true },
      },
      {
        path: 'pay/:orderNo',
        name: 'payment',
        component: PaymentView,
        meta: { title: '确认支付', group: 'user', requiresAuth: true },
      },
      {
        path: 'payment-success/:orderNo',
        name: 'payment-success',
        component: PaymentSuccessView,
        meta: { title: '支付成功', group: 'user', requiresAuth: true },
      },
      {
        path: 'addresses',
        name: 'addresses',
        component: AddressListView,
        meta: { title: '收货地址', group: 'user', requiresAuth: true },
      },
      {
        path: 'favorites',
        name: 'favorites',
        component: FavoriteListView,
        meta: { title: '我的收藏', group: 'user', requiresAuth: true },
      },
      {
        path: 'reviews',
        name: 'my-reviews',
        component: MyReviewsView,
        meta: { title: '我的评价', group: 'user', requiresAuth: true },
      },
      {
        path: 'profile',
        name: 'profile',
        component: ProfileView,
        meta: { title: '个人中心', group: 'user', requiresAuth: true },
      },
      // /user/* 语义化别名：复用同一组件，避免重复页面（原有路径依然保留，向后兼容）
      {
        path: 'user/profile',
        name: 'user-profile',
        component: ProfileView,
        meta: { title: '个人中心', group: 'user', requiresAuth: true },
      },
      { path: 'user/orders', name: 'user-orders', component: OrderListView, meta: { title: '我的订单', group: 'user', requiresAuth: true } },
      { path: 'user/cart', name: 'user-cart', component: CartView, meta: { title: '购物车', group: 'user', requiresAuth: true } },
      { path: 'user/favorites', name: 'user-favorites', component: FavoriteListView, meta: { title: '我的收藏', group: 'user', requiresAuth: true } },
      { path: 'user/addresses', name: 'user-addresses', component: AddressListView, meta: { title: '收货地址', group: 'user', requiresAuth: true } },
    ],
  },
  {
    path: '/merchant',
    component: DashboardLayout,
    meta: { group: 'merchant', title: '商家中心', requiresAuth: true, requiresRole: 'merchant' },
    children: [
      {
        path: '',
        name: 'merchant-dashboard',
        component: MerchantDashboardView,
        meta: { title: '工作台', group: 'merchant' },
      },
      {
        path: 'products',
        name: 'merchant-products',
        component: MerchantProductsView,
        meta: { title: '我的商品', group: 'merchant' },
      },
      {
        path: 'orders',
        name: 'merchant-orders',
        component: MerchantOrdersView,
        meta: { title: '订单管理', group: 'merchant' },
      },
      {
        path: 'profile',
        name: 'merchant-profile',
        component: MerchantProfileView,
        meta: { title: '商家资料', group: 'merchant' },
      },
    ],
  },
  {
    path: '/admin',
    component: DashboardLayout,
    meta: { group: 'admin', title: '平台管理', requiresAuth: true, requiresRole: 'admin' },
    children: [
      {
        path: '',
        name: 'admin-dashboard',
        component: AdminDashboardView,
        meta: { title: '工作台', group: 'admin' },
      },
      {
        path: 'users',
        name: 'admin-users',
        component: AdminUsersView,
        meta: { title: '用户管理', group: 'admin' },
      },
      {
        path: 'categories',
        name: 'admin-categories',
        component: AdminCategoriesView,
        meta: { title: '分类管理', group: 'admin' },
      },
      {
        path: 'audit',
        name: 'admin-audit',
        component: AdminAuditView,
        meta: { title: '商品审核', group: 'admin' },
      },
      {
        path: 'products',
        name: 'admin-products',
        component: AdminProductsView,
        meta: { title: '商品管理', group: 'admin' },
      },
      {
        path: 'orders',
        name: 'admin-orders',
        component: AdminOrdersView,
        meta: { title: '订单管理', group: 'admin' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView, meta: { title: '页面不存在' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

/**
 * 前端路由守卫（仅为用户体验，不是安全边界）。
 *
 * 作用：未登录访问受保护页面 → 跳转登录并带 redirect；角色不符 → 回首页。
 * 真正的权限校验在后端 Spring Security，前端隐藏入口很容易被绕过，
 * 因此所有写接口、管理接口在后端都必须独立校验身份与角色。
 */
router.beforeEach(async (to) => {
  const userStore = useUserStore()

  // 若本地有 Token 但尚未加载用户信息（刷新页面场景），先尝试恢复登录态
  if (userStore.isLoggedIn && !userStore.userInfo) {
    await userStore.fetchProfile()
  }

  const needsAuth = to.meta.requiresAuth || to.meta.requiresRole
  if (needsAuth && !userStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresRole) {
    const required = to.meta.requiresRole
    // ADMIN 可访问商家端，便于平台代管；ADMIN 端仅 ADMIN
    const allowed = required === 'admin'
      ? userStore.hasRole('ADMIN')
      : userStore.hasRole('MERCHANT') || userStore.hasRole('ADMIN')
    if (!allowed) {
      return { name: 'home' }
    }
  }

  // 已登录用户访问身份选择/具体身份登录/注册页时跳转首页，避免重复登录
  if (
    (to.name === 'login' || to.name === 'login-by-identity' || to.name === 'register')
    && userStore.isLoggedIn
  ) {
    return { name: 'home' }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · PandaGo` : 'PandaGo · 熊猫购'
})

export default router
