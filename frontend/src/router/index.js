import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
  { path: '/products', name: 'products', component: () => import('../views/ProductListView.vue') },
  { path: '/search', name: 'search', component: () => import('../views/SearchView.vue') },
  {
    path: '/products/:id',
    name: 'product-detail',
    component: () => import('../views/ProductDetailView.vue')
  },
  { path: '/shops', name: 'shops', component: () => import('../views/ShopListView.vue') },
  {
    path: '/shops/:id',
    name: 'shop-detail',
    component: () => import('../views/ShopDetailView.vue')
  },
  {
    path: '/cart',
    name: 'cart',
    component: () => import('../views/CartView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/ProfileView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/orders',
    name: 'orders',
    component: () => import('../views/OrdersView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/orders/:orderNo',
    name: 'order-detail',
    component: () => import('../views/OrderDetailView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/pay/:orderNo',
    name: 'pay',
    component: () => import('../views/PayView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/reviews',
    name: 'reviews',
    component: () => import('../views/ReviewsView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('../views/AdminView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { bare: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue'),
    meta: { bare: true }
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, behavior: 'smooth' }
    return { top: 0 }
  }
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin) {
    // 旧缓存可能没有 role 字段，先刷新一次个人信息再判定
    if (!auth.isAdmin && !auth.user?.role) {
      await auth.refreshMe().catch(() => {})
    }
    if (!auth.isAdmin) return { path: '/' }
  }
  if ((to.path === '/login' || to.path === '/register') && auth.isLoggedIn) {
    return { path: '/' }
  }
  return true
})

export default router
