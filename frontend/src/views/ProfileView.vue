<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { StarFilled } from '@element-plus/icons-vue'
import { authApi, followApi, likeApi, productApi, reviewApi, shopApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { dateTime, resolveImage } from '../utils/format'
import ShopCard from '../components/ShopCard.vue'
import ProductCard from '../components/ProductCard.vue'

const auth = useAuthStore()

const form = reactive({
  nickname: '',
  phone: '',
  gender: 0
})
const saving = ref(false)

const tab = ref('shops')

const shops = ref([])
const products = ref([])
const reviews = ref([])
const shopsLoading = ref(false)
const productsLoading = ref(false)
const reviewsLoading = ref(false)

const monogram = computed(() => {
  const name = form.nickname || auth.user?.username || '养车'
  return name.slice(0, 2).toUpperCase()
})

const stats = computed(() => [
  { label: '关注门店', value: shops.value.length },
  { label: '点赞商品', value: products.value.length },
  { label: '点赞评价', value: reviews.value.length }
])

function syncForm(user) {
  if (!user) return
  form.nickname = user.nickname || ''
  form.phone = user.phone || ''
  form.gender = Number(user.gender ?? 0)
}

async function loadShops() {
  shopsLoading.value = true
  try {
    const ids = (await followApi.list(1)) || []
    const results = await Promise.allSettled(ids.map((id) => shopApi.detail(id)))
    shops.value = results
      .filter((item) => item.status === 'fulfilled' && item.value)
      .map((item) => item.value)
  } catch {
    shops.value = []
  } finally {
    shopsLoading.value = false
  }
}

async function loadProducts() {
  productsLoading.value = true
  try {
    const ids = (await likeApi.list(3)) || []
    const results = await Promise.allSettled(ids.map((id) => productApi.detail(id)))
    products.value = results
      .filter((item) => item.status === 'fulfilled' && item.value)
      .map((item) => item.value)
  } catch {
    products.value = []
  } finally {
    productsLoading.value = false
  }
}

async function loadReviews() {
  reviewsLoading.value = true
  try {
    const ids = (await likeApi.list(1)) || []
    const results = await Promise.allSettled(ids.map((id) => reviewApi.detail(id)))
    reviews.value = results
      .filter((item) => item.status === 'fulfilled' && item.value)
      .map((item) => item.value)
  } catch {
    reviews.value = []
  } finally {
    reviewsLoading.value = false
  }
}

async function save() {
  const nickname = form.nickname.trim()
  if (!nickname) {
    ElMessage.warning('昵称不能为空')
    return
  }
  if (form.phone && !/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('请填写正确的手机号')
    return
  }
  saving.value = true
  try {
    const user = await authApi.updateProfile({
      nickname,
      phone: form.phone.trim(),
      avatar: auth.user?.avatar || '',
      gender: form.gender
    })
    auth.user = user
    auth.persist()
    syncForm(user)
    ElMessage.success('资料已保存')
  } catch {
    /* 拦截器已提示具体原因 */
  } finally {
    saving.value = false
  }
}

async function unfollow(shop) {
  try {
    await followApi.unfollow(1, shop.id)
    shops.value = shops.value.filter((item) => item.id !== shop.id)
    ElMessage.success('已取消关注')
  } catch {
    /* 拦截器已提示具体原因 */
  }
}

async function unlikeProduct(product) {
  try {
    await likeApi.unlike(3, product.id)
    products.value = products.value.filter((item) => item.id !== product.id)
    ElMessage.success('已取消点赞')
  } catch {
    /* 拦截器已提示具体原因 */
  }
}

async function unlikeReview(review) {
  try {
    await likeApi.unlike(1, review.id)
    reviews.value = reviews.value.filter((item) => item.id !== review.id)
    ElMessage.success('已取消点赞')
  } catch {
    /* 拦截器已提示具体原因 */
  }
}

function reviewLink(review) {
  if (review.itemType === 1) return `/products/${review.itemId}`
  if (review.shopId) return `/shops/${review.shopId}#shop-reviews`
  return '/shops'
}

function reviewTarget(review) {
  if (review.itemType === 1) return '商品评价'
  if (review.itemType === 2) return '服务评价'
  return '评价'
}

function fallback(event) {
  event.target.src = '/images/product/placeholder.jpg'
}

onMounted(async () => {
  syncForm(auth.user)
  loadShops()
  loadProducts()
  loadReviews()
  try {
    const user = await auth.refreshMe()
    syncForm(user)
  } catch {
    /* 拦截器已提示具体原因 */
  }
})
</script>

<template>
  <div class="page">
    <div class="container">
      <header class="page-head">
        <div>
          <h1>个人中心</h1>
          <p>维护基础资料，集中查看关注的门店和点赞过的内容。</p>
        </div>
        <router-link to="/orders" class="section-link">我的订单 →</router-link>
      </header>

      <div class="profile-grid">
        <aside class="surface identity-card">
          <div class="avatar">{{ monogram }}</div>
          <h2>{{ form.nickname || auth.user?.username || '养车用户' }}</h2>
          <p class="username mono">@{{ auth.user?.username || '—' }}</p>
          <p class="joined">注册于 {{ dateTime(auth.user?.createTime) }}</p>

          <p class="stats-line">
            <span v-for="item in stats" :key="item.label">
              {{ item.label }} <b class="num">{{ item.value }}</b>
            </span>
          </p>

          <div class="quick-links">
            <router-link to="/orders">我的订单</router-link>
            <router-link to="/cart">购物车</router-link>
            <router-link to="/products">继续逛逛</router-link>
          </div>
        </aside>

        <div class="main-column">
          <section class="surface edit-panel">
            <div class="panel-head">
              <div>
                <h2>基础资料</h2>
                <p>昵称和手机号会用于订单联系人与售后回访。</p>
              </div>
            </div>

            <el-form label-position="top" class="edit-form" @submit.prevent="save">
              <el-form-item label="昵称">
                <el-input v-model="form.nickname" maxlength="50" show-word-limit placeholder="请输入昵称" />
              </el-form-item>
              <el-form-item label="手机号">
                <el-input v-model="form.phone" maxlength="11" placeholder="用于预约与订单联系" />
              </el-form-item>
              <el-form-item label="性别">
                <el-radio-group v-model="form.gender">
                  <el-radio :value="0">保密</el-radio>
                  <el-radio :value="1">男</el-radio>
                  <el-radio :value="2">女</el-radio>
                </el-radio-group>
              </el-form-item>
              <div class="form-actions">
                <el-button type="primary" :loading="saving" @click="save">保存资料</el-button>
              </div>
            </el-form>
          </section>

          <section class="surface collection-panel">
            <div class="tabs">
              <button type="button" :class="{ active: tab === 'shops' }" @click="tab = 'shops'">
                关注门店
                <span>{{ shops.length }}</span>
              </button>
              <button type="button" :class="{ active: tab === 'products' }" @click="tab = 'products'">
                点赞商品
                <span>{{ products.length }}</span>
              </button>
              <button type="button" :class="{ active: tab === 'reviews' }" @click="tab = 'reviews'">
                点赞评价
                <span>{{ reviews.length }}</span>
              </button>
            </div>

            <div v-if="tab === 'shops'">
              <div v-if="shopsLoading" class="list-loading">
                <div v-for="n in 2" :key="n" class="skeleton-block row-skeleton"></div>
              </div>
              <el-empty v-else-if="!shops.length" description="还没有关注门店" :image-size="100">
                <el-button type="primary" @click="$router.push('/shops')">去逛门店</el-button>
              </el-empty>
              <div v-else class="shop-list">
                <div v-for="shop in shops" :key="shop.id" class="collection-row">
                  <ShopCard :shop="shop" compact />
                  <button type="button" class="remove-link" @click="unfollow(shop)">取消关注</button>
                </div>
              </div>
            </div>

            <div v-else-if="tab === 'products'">
              <div v-if="productsLoading" class="product-loading">
                <div v-for="n in 3" :key="n" class="skeleton-block card-skeleton"></div>
              </div>
              <el-empty v-else-if="!products.length" description="还没有点赞商品" :image-size="100">
                <el-button type="primary" @click="$router.push('/products')">去挑好物</el-button>
              </el-empty>
              <div v-else class="product-grid">
                <div v-for="product in products" :key="product.id" class="product-wrap">
                  <ProductCard :product="product" />
                  <button type="button" class="remove-link" @click="unlikeProduct(product)">取消点赞</button>
                </div>
              </div>
            </div>

            <div v-else>
              <div v-if="reviewsLoading" class="list-loading">
                <div v-for="n in 2" :key="n" class="skeleton-block row-skeleton"></div>
              </div>
              <el-empty v-else-if="!reviews.length" description="还没有点赞评价" :image-size="100">
                <el-button type="primary" @click="$router.push('/reviews')">去看看评价</el-button>
              </el-empty>
              <div v-else class="review-list">
                <article v-for="review in reviews" :key="review.id" class="review-card">
                  <header>
                    <div class="review-meta">
                      <span class="chip">{{ reviewTarget(review) }}</span>
                      <span class="stars">
                        <el-icon v-for="n in review.score" :key="n"><StarFilled /></el-icon>
                      </span>
                    </div>
                    <button type="button" class="remove-link" @click="unlikeReview(review)">取消点赞</button>
                  </header>
                  <p class="review-content">{{ review.content || '该用户未填写评价内容' }}</p>
                  <div v-if="review.images?.length" class="review-images">
                    <img
                      v-for="(img, index) in review.images"
                      :key="index"
                      :src="resolveImage(img)"
                      alt="评价图片"
                      @error="fallback"
                    />
                  </div>
                  <footer>
                    <span>{{ dateTime(review.createTime) }}</span>
                    <span>{{ review.likeCount || 0 }} 人点赞</span>
                    <router-link :to="reviewLink(review)">查看来源 →</router-link>
                  </footer>
                </article>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}

.page-head h1 {
  font-size: 20px;
  line-height: 1.3;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.page-head p {
  margin-top: 5px;
  font-size: 12px;
  color: var(--muted);
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(230px, 0.34fr) minmax(0, 1fr);
  gap: 16px;
  margin-top: 24px;
  align-items: start;
}

.identity-card {
  padding: 28px 22px;
  text-align: center;
  position: sticky;
  top: 20px;
}

.avatar {
  width: 64px;
  height: 64px;
  margin: 0 auto;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.identity-card h2 {
  margin-top: 12px;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -0.01em;
  word-break: break-all;
}

.username {
  margin-top: 5px;
  font-size: 11px;
  color: var(--muted);
}

.joined {
  margin-top: 4px;
  font-size: 10px;
  color: var(--muted);
}

.stats-line {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px 14px;
  margin-top: 16px;
  padding: 10px 0;
  border-top: 1px dashed var(--line);
  border-bottom: 1px dashed var(--line);
  font-size: 11px;
  color: var(--muted);
}

.stats-line b {
  font-size: 12px;
  font-weight: 600;
  color: var(--ink);
}

.quick-links {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 18px;
}

.quick-links a {
  padding: 9px 12px;
  border-radius: 6px;
  background: var(--surface-soft);
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-soft);
  transition: all 0.2s ease;
}

.quick-links a:hover {
  background: var(--brand-soft);
  color: var(--brand-deep);
}

.main-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.edit-panel {
  padding: 24px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.panel-head h2 {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.panel-head p {
  margin-top: 5px;
  font-size: 11px;
  color: var(--muted);
}

.edit-form {
  margin-top: 18px;
}

.edit-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

.collection-panel {
  padding: 22px 24px 26px;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}

.tabs button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 14px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  font-weight: 500;
  color: var(--ink-soft);
  cursor: pointer;
  transition: all 0.2s ease;
}

.tabs button span {
  min-width: 18px;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--surface-soft);
  font-size: 10px;
  text-align: center;
}

.tabs button.active {
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
}

.tabs button.active span {
  background: rgba(255, 255, 255, 0.18);
}

.list-loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 18px;
}

.row-skeleton {
  height: 108px;
}

.product-loading {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-top: 18px;
}

.card-skeleton {
  height: 290px;
}

.shop-list,
.review-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 18px;
}

.collection-row,
.product-wrap {
  position: relative;
}

.remove-link {
  position: absolute;
  z-index: 2;
  top: 10px;
  right: 10px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: #fff;
  padding: 4px 8px;
  font-size: 10px;
  font-weight: 500;
  color: var(--muted);
  cursor: pointer;
  transition: all 0.2s ease;
}

.remove-link:hover {
  border-color: #fecdca;
  background: #fef3f2;
  color: #d92d20;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.product-wrap .remove-link {
  top: 8px;
  right: 8px;
  background: #fff;
  color: var(--muted);
}

.product-wrap .remove-link:hover {
  background: #d92d20;
  color: #fff;
}

.review-card {
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface-soft);
}

.review-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.review-card header .remove-link {
  position: static;
}

.review-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stars {
  display: inline-flex;
  gap: 2px;
  color: var(--brand);
  font-size: 13px;
}

.review-content {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-soft);
}

.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.review-images img {
  width: 62px;
  height: 62px;
  border-radius: 6px;
  object-fit: cover;
  background: #eee;
}

.review-card footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
  font-size: 10px;
  color: var(--muted);
}

.review-card footer a {
  margin-left: auto;
  font-weight: 700;
  color: var(--brand-deep);
}

@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }

  .identity-card {
    position: static;
  }

  .product-grid,
  .product-loading {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .edit-panel,
  .collection-panel {
    padding: 18px 16px;
  }

  .product-grid,
  .product-loading {
    grid-template-columns: 1fr;
  }
}
</style>
