<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Goods,
  Management,
  Search,
  Shop,
  ShoppingCart,
  Star,
  Tickets,
  User
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useCartStore } from '../stores/cart'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()
const keyword = ref('')

// 管理后台顶栏模式：右上角仅保留「进入前台」安全跳转（以当前管理员自身账号查看，不切换/顶替任何用户身份）
const inAdmin = computed(() => route.path.startsWith('/admin'))

// 首页已有主搜索框，顶栏小搜索在首页隐藏，避免同屏双搜索入口
const showHeaderSearch = computed(() => !inAdmin.value && route.path !== '/')

watch(
  () => auth.token,
  (token) => {
    if (token) cart.refreshCount()
    else cart.reset()
  },
  { immediate: true }
)

const navs = [
  { path: '/', label: '首页', icon: Shop, exact: true },
  { path: '/products', label: '养车商城', icon: Goods },
  { path: '/shops', label: '门店服务', icon: Shop },
  { path: '/orders', label: '我的订单', icon: Tickets, auth: true },
  { path: '/reviews', label: '我的评价', icon: Star, auth: true },
  { path: '/admin', label: '商家工作台', icon: Management, admin: true }
]

function goSearch() {
  const text = keyword.value.trim()
  router.push({ path: '/search', query: text ? { keyword: text } : {} })
}

function handleCommand(command) {
  if (command === 'logout') {
    auth.logout()
    cart.reset()
    ElMessage.success('已退出登录')
    if (route.meta.requiresAuth) router.push('/login')
    return
  }
  router.push(command)
}
</script>

<template>
  <header class="site-header">
    <div class="container header-inner">
      <router-link to="/" class="brand">
        <span class="brand-mark">洪</span>
        <span class="brand-copy">
          <strong>洪兴洗车</strong>
          <small>汽车用品 · 到店服务</small>
        </span>
      </router-link>

      <nav v-if="!inAdmin" class="main-nav">
        <router-link
          v-for="item in navs"
          :key="item.path"
          v-show="(!item.auth || auth.isLoggedIn) && (!item.admin || auth.isAdmin)"
          :to="item.path"
          class="nav-link"
          :class="{ active: item.exact ? route.path === '/' : route.path.startsWith(item.path) }"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <form v-if="showHeaderSearch" class="header-search" @submit.prevent="goSearch">
        <el-icon><Search /></el-icon>
        <input v-model="keyword" type="search" placeholder="搜索商品、门店或服务" />
      </form>

      <div v-if="!inAdmin" class="header-actions">
        <template v-if="auth.isLoggedIn">
          <router-link to="/cart" class="cart-entry" aria-label="购物车" title="购物车">
            <el-badge :value="cart.count" :max="99" :hidden="!cart.count">
              <el-icon><ShoppingCart /></el-icon>
            </el-badge>
          </router-link>
          <el-dropdown trigger="click" @command="handleCommand">
            <button class="user-button" type="button">
              <span class="avatar">{{ auth.displayName.slice(0, 1) }}</span>
              <span class="user-name">{{ auth.displayName }}</span>
              <el-icon class="caret"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="/profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="/cart">购物车</el-dropdown-item>
                <el-dropdown-item command="/orders">我的订单</el-dropdown-item>
                <el-dropdown-item command="/reviews">我的评价</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <router-link to="/login" class="ghost-link">登录</router-link>
          <router-link to="/register" class="primary-link">
            <el-icon><User /></el-icon>
            注册
          </router-link>
        </template>
      </div>
      <div v-else class="header-actions">
        <router-link
          to="/"
          class="ghost-link"
          title="以当前管理员自身账号查看前台，不切换或顶替任何用户身份"
        >
          进入前台
        </router-link>
      </div>
    </div>
  </header>
</template>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: #fff;
  border-bottom: 1px solid var(--line);
  box-shadow: 0 2px 10px rgba(20, 23, 31, 0.06);
}

.header-inner {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 22px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  flex: none;
}

.brand-mark {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: var(--brand);
  color: #fff;
  font-weight: 700;
  font-size: 17px;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand-copy strong {
  font-size: 16px;
  letter-spacing: 0.01em;
}

.brand-copy small {
  margin-top: 2px;
  font-size: 10px;
  color: var(--muted);
  letter-spacing: 0.02em;
}

.main-nav {
  display: flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
}

.nav-link {
  position: relative;
  padding: 8px 10px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-soft);
  white-space: nowrap;
  transition: color 0.15s ease;
}

.nav-link:hover {
  color: var(--brand);
}

.nav-link.active {
  color: var(--brand);
  font-weight: 700;
}

.nav-link.active::after {
  content: "";
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: 0;
  height: 3px;
  border-radius: 2px 2px 0 0;
  background: var(--brand);
}

.header-search {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
  width: min(250px, 24vw);
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface-soft);
  color: var(--muted);
  transition: border-color 0.15s ease, background 0.15s ease;
}

.header-search:focus-within {
  border-color: var(--brand);
  background: #fff;
}

.header-search input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 13px;
  color: var(--ink);
}

.header-search input::placeholder {
  color: var(--muted);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
  margin-left: auto;
}

.ghost-link {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-soft);
  padding: 8px 10px;
}

.cart-entry {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 6px;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink-soft);
  transition: color 0.15s ease, border-color 0.15s ease;
}

.cart-entry:hover {
  color: var(--brand);
  border-color: var(--brand);
}

.cart-entry .el-icon {
  font-size: 18px;
}

.cart-entry :deep(.el-badge__content) {
  border: 0;
  background: var(--brand);
  font-size: 10px;
}

.ghost-link:hover {
  color: var(--brand);
}

.primary-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 15px;
  border-radius: 6px;
  background: var(--brand);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  transition: background 0.15s ease;
}

.primary-link:hover {
  background: #ef5a35;
}

.user-button {
  display: flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 6px;
  padding: 3px 8px 3px 3px;
  cursor: pointer;
  color: var(--ink);
}

.avatar {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.user-name {
  max-width: 76px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
}

.caret {
  color: var(--muted);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .header-search {
    display: none;
  }

  .main-nav {
    margin-left: auto;
  }
}

@media (max-width: 860px) {
  .header-inner {
    gap: 12px;
    height: 64px;
  }

  .brand-copy small {
    display: none;
  }

  .brand-copy strong {
    font-size: 15px;
  }

  .main-nav {
    overflow-x: auto;
    scrollbar-width: none;
  }

  .main-nav::-webkit-scrollbar {
    display: none;
  }

  .nav-link {
    padding: 8px;
    font-size: 13px;
  }

  .nav-link:nth-child(4),
  .nav-link:nth-child(5),
  .nav-link:nth-child(6) {
    display: none;
  }

  .user-name {
    display: none;
  }
}

@media (max-width: 560px) {
  .brand-copy {
    display: none;
  }

  .primary-link {
    padding: 0 12px;
  }

  .ghost-link {
    padding: 6px;
  }
}
</style>
