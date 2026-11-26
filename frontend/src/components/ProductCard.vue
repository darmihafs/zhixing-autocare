<script setup>
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { ShoppingCart } from '@element-plus/icons-vue'
import { resolveImage } from '../utils/format'
import { useAuthStore } from '../stores/auth'
import { useCartStore } from '../stores/cart'
import PriceText from './PriceText.vue'

const props = defineProps({
  product: { type: Object, required: true }
})

const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()

function fallback(event) {
  event.target.src = '/images/product/placeholder.jpg'
}

async function addToCart() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  if (!props.product.stock) {
    ElMessage.warning('该商品暂时缺货')
    return
  }
  try {
    await cart.add(props.product.id, 1)
    ElMessage.success('已加入购物车')
  } catch {
    /* 拦截器已提示具体原因 */
  }
}
</script>

<template>
  <article class="product-card">
    <router-link :to="`/products/${product.id}`" class="card-link">
      <div class="media">
        <img
          :src="resolveImage(product.coverImg)"
          :alt="product.productName"
          loading="lazy"
          @error="fallback"
        />
        <span class="brand-badge">{{ product.brand }}</span>
        <span v-if="product.stock <= 10" class="stock-badge">仅剩 {{ product.stock }} 件</span>
      </div>
      <div class="body">
        <h3>{{ product.productName }}</h3>
        <p class="meta">
          <span class="meta-main">{{ product.categoryName }} · 已售 <i class="num">{{ product.sales }}</i></span>
          <span class="meta-stock num" :class="{ low: product.stock <= 10 }">库存 {{ product.stock }}</span>
        </p>
        <div class="foot">
          <div class="price-wrap">
            <PriceText :value="product.price" class="price" />
            <span class="price-tag">门店直供</span>
          </div>
          <button
            class="cart-add"
            type="button"
            title="加入购物车"
            @click.prevent.stop="addToCart"
          >
            <el-icon><ShoppingCart /></el-icon>
            加购
          </button>
        </div>
      </div>
    </router-link>
  </article>
</template>

<style scoped>
.product-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  overflow: hidden;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.product-card:hover {
  border-color: var(--line-strong);
  box-shadow: var(--shadow-sm);
}

.card-link {
  display: block;
}

.media {
  position: relative;
  aspect-ratio: 1 / 1;
  background: var(--surface-soft);
  overflow: hidden;
}

.media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s ease;
}

.product-card:hover .media img {
  transform: scale(1.03);
}

.brand-badge,
.stock-badge {
  position: absolute;
  top: 8px;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  line-height: 1.6;
}

.brand-badge {
  left: 0;
  padding: 2px 7px;
  background: rgba(20, 23, 31, 0.72);
  color: #fff;
  border-left: 3px solid var(--brand);
  border-radius: 0 4px 4px 0;
}

.stock-badge {
  right: 8px;
  background: #fff;
  color: var(--brand-deep);
}

.body {
  padding: 10px 12px 12px;
}

h3 {
  min-height: 40px;
  font-size: 13px;
  line-height: 1.55;
  font-weight: 400;
  color: var(--ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 5px;
  font-size: 11px;
  color: var(--muted);
}

.meta-main {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-main i {
  font-style: normal;
}

.meta-stock {
  flex: none;
}

.meta-stock.low {
  color: var(--brand-deep);
  font-weight: 600;
}

.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px dashed var(--line);
}

.price-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.price {
  font-size: 23px;
}

.price-tag {
  flex: none;
  padding: 1px 5px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--surface-soft);
  color: var(--ink-soft);
  font-size: 10px;
  line-height: 1.6;
}

.cart-add {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 28px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  background: #1d2129;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}

.cart-add:hover {
  background: var(--brand);
}
</style>
