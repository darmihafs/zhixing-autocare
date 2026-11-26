<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, ShoppingCart, Star } from '@element-plus/icons-vue'
import { likeApi, orderApi, productApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { useCartStore } from '../stores/cart'
import { money, resolveImage } from '../utils/format'
import ReviewPanel from '../components/ReviewPanel.vue'
import PriceText from '../components/PriceText.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()

const loading = ref(true)
const product = ref(null)
const activeImage = ref(0)
const quantity = ref(1)
const buyOpen = ref(false)
const submitting = ref(false)
const addingCart = ref(false)
const liked = ref(false)
const likeBusy = ref(false)
const creatingOpen = ref(false)
const createState = ref({ state: 'QUEUED', message: '' })
const currentOrderNo = ref('')
const remark = ref('')

const gallery = computed(() => {
  if (!product.value) return []
  const list = [product.value.coverImg, ...(product.value.images || [])].filter(Boolean)
  return [...new Set(list)]
})

const totalAmount = computed(() => Number(product.value?.price || 0) * quantity.value)

onMounted(async () => {
  try {
    product.value = await productApi.detail(route.params.id)
    if (auth.isLoggedIn) {
      try {
        liked.value = Boolean(await likeApi.status(3, product.value.id))
      } catch {
        liked.value = false
      }
    }
  } finally {
    loading.value = false
  }
})

function fallback(event) {
  event.target.src = '/images/product/placeholder.jpg'
}

function openBuy() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!product.value?.stock) {
    ElMessage.warning('该商品暂时缺货')
    return
  }
  quantity.value = Math.min(Math.max(1, quantity.value), product.value.stock)
  buyOpen.value = true
}

async function addToCart() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!product.value?.stock) {
    ElMessage.warning('该商品暂时缺货')
    return
  }
  addingCart.value = true
  try {
    await cart.add(product.value.id, Math.min(Math.max(1, quantity.value), product.value.stock))
    ElMessage.success('已加入购物车')
  } catch {
    /* 拦截器已提示具体原因 */
  } finally {
    addingCart.value = false
  }
}

async function toggleLike() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!product.value || likeBusy.value) return
  likeBusy.value = true
  try {
    if (liked.value) {
      await likeApi.unlike(3, product.value.id)
      liked.value = false
      ElMessage.success('已取消点赞')
    } else {
      await likeApi.like(3, product.value.id)
      liked.value = true
      ElMessage.success('已点赞，可在个人中心查看')
    }
  } catch {
    /* 拦截器已提示具体原因 */
  } finally {
    likeBusy.value = false
  }
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function pollCreate(orderNo) {
  for (let i = 0; i < 40; i += 1) {
    try {
      const state = await orderApi.state(orderNo)
      createState.value = state
      if (state.state === 'CREATED') {
        ElMessage.success('订单创建成功')
        await sleep(650)
        router.push(`/orders/${orderNo}`)
        return
      }
      if (state.state === 'FAILED' || state.state === 'NOT_FOUND') return
    } catch {
      return
    }
    await sleep(850)
  }
}

async function submitOrder() {
  if (!product.value) return
  submitting.value = true
  try {
    const result = await orderApi.create({
      orderType: 1,
      items: [{ productId: product.value.id, quantity: quantity.value }],
      remark: remark.value.trim() || undefined
    })
    currentOrderNo.value = result.orderNo
    createState.value = { state: result.state || 'QUEUED', message: result.message || '' }
    buyOpen.value = false
    creatingOpen.value = true
    await pollCreate(result.orderNo)
  } finally {
    submitting.value = false
  }
}

function closeCreating() {
  if (createState.value.state === 'QUEUED') return
  creatingOpen.value = false
  remark.value = ''
}
</script>

<template>
  <div class="page">
    <div class="container">
      <div v-if="loading" class="detail-loading">
        <div class="skeleton-block gallery-skeleton"></div>
        <div class="info-skeleton">
          <div class="skeleton-block line w40"></div>
          <div class="skeleton-block line w80"></div>
          <div class="skeleton-block line w60"></div>
          <div class="skeleton-block box"></div>
        </div>
      </div>

      <template v-else-if="product">
        <nav class="breadcrumb">
          <router-link to="/">首页</router-link>
          <span>/</span>
          <router-link to="/products">养车商城</router-link>
          <span>/</span>
          <span>{{ product.productName }}</span>
        </nav>

        <div class="detail-layout">
          <div class="gallery">
            <div class="main-image">
              <img
                :src="resolveImage(gallery[activeImage])"
                :alt="product.productName"
                @error="fallback"
              />
              <span class="img-index mono">{{ String(activeImage + 1).padStart(2, '0') }} / {{ String(gallery.length).padStart(2, '0') }}</span>
            </div>
            <div v-if="gallery.length > 1" class="thumbs">
              <button
                v-for="(img, index) in gallery"
                :key="img"
                type="button"
                :class="{ active: activeImage === index }"
                @click="activeImage = index"
              >
                <img :src="resolveImage(img)" :alt="`${product.productName} ${index + 1}`" @error="fallback" />
              </button>
            </div>
          </div>

          <div class="info">
            <div class="info-tags">
              <span class="chip chip--brand">{{ product.brand }}</span>
              <span class="chip">{{ product.categoryName }}</span>
              <span class="chip chip--teal">现货 {{ product.stock }}</span>
              <button
                type="button"
                class="like-chip"
                :class="{ active: liked }"
                :disabled="likeBusy"
                @click="toggleLike"
              >
                <el-icon><Star /></el-icon>
                {{ liked ? '已点赞' : '点赞商品' }}
              </button>
            </div>
            <h1>{{ product.productName }}</h1>
            <p class="summary">{{ product.description }}</p>

            <div class="price-panel">
              <span class="price-label">到手价</span>
              <PriceText :value="product.price" class="price big" />
              <span class="sales-note">已售 {{ product.sales }} 件 · 现货 {{ product.stock }} 件</span>
            </div>

            <div class="purchase-row">
              <span class="row-label">数量</span>
              <el-input-number
                v-model="quantity"
                :min="1"
                :max="Math.max(1, product.stock)"
                :disabled="!product.stock"
                size="large"
              />
              <span class="stock-hint">单次最多 {{ product.stock }} 件</span>
            </div>

            <div class="purchase-actions">
              <el-button
                type="primary"
                size="large"
                class="buy-button"
                :disabled="!product.stock"
                :loading="submitting"
                @click="openBuy"
              >
                <el-icon><ShoppingCart /></el-icon>
                {{ product.stock ? '立即购买' : '暂时缺货' }}
              </el-button>
              <el-button
                size="large"
                class="cart-button"
                :disabled="!product.stock"
                :loading="addingCart"
                @click="addToCart"
              >
                加入购物车
              </el-button>
              <router-link
                class="shop-cta"
                :to="{ path: '/shops', query: { mode: 'service', keyword: product.categoryName || '保养' } }"
              >
                搭配门店保养服务 →
              </router-link>
            </div>

            <ul class="assurance-list">
              <li><el-icon><Check /></el-icon> 正品保障，下单即锁定库存</li>
              <li><el-icon><Check /></el-icon> 订单进度随时可查</li>
              <li><el-icon><Check /></el-icon> 订单完成后可发布图文评价</li>
            </ul>
          </div>
        </div>

        <section class="detail-copy">
          <div class="copy-block">
            <h2>商品说明</h2>
            <p>{{ product.description }}</p>
            <div class="spec-grid">
              <div><span>品牌</span><strong>{{ product.brand }}</strong></div>
              <div><span>分类</span><strong>{{ product.categoryName }}</strong></div>
              <div><span>当前库存</span><strong>{{ product.stock }} 件</strong></div>
              <div><span>累计销量</span><strong>{{ product.sales }} 件</strong></div>
            </div>
          </div>
          <div class="copy-note">
            <strong>购买提示</strong>
            <p>下单后可选择到店安装，具体施工时间以门店确认为准。</p>
            <p>如需备注车型年款，可在下单时填写，门店会提前备货。</p>
          </div>
        </section>

        <div id="reviews">
          <ReviewPanel :item-type="1" :item-id="product.id" title="车主评价" />
        </div>

        <el-dialog v-model="buyOpen" title="确认下单" width="460px" :close-on-click-modal="false">
          <div class="buy-summary">
            <img :src="resolveImage(product.coverImg)" :alt="product.productName" @error="fallback" />
            <div>
              <strong>{{ product.productName }}</strong>
              <span>{{ product.brand }} · {{ product.categoryName }}</span>
              <b>¥{{ money(product.price) }} × {{ quantity }}</b>
            </div>
          </div>
          <el-form label-position="top" class="buy-form">
            <el-form-item label="订单备注（选填）">
              <el-input
                v-model="remark"
                type="textarea"
                :rows="2"
                maxlength="255"
                show-word-limit
                placeholder="例如：2019 款大众朗逸 1.4T"
              />
            </el-form-item>
          </el-form>
          <div class="buy-total">
            <span>应付金额</span>
            <strong>¥{{ money(totalAmount) }}</strong>
          </div>
          <template #footer>
            <el-button @click="buyOpen = false">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="submitOrder">
              提交订单
            </el-button>
          </template>
        </el-dialog>

        <el-dialog
          v-model="creatingOpen"
          title="订单处理中"
          width="440px"
          :close-on-click-modal="false"
          :show-close="createState.state !== 'QUEUED'"
          @close="closeCreating"
        >
          <div class="creating-state">
            <div v-if="createState.state === 'QUEUED'" class="pulse">
              <span></span><span></span><span></span>
            </div>
            <div v-else-if="createState.state === 'CREATED'" class="state-icon ok">✓</div>
            <div v-else class="state-icon fail">!</div>
            <div>
              <strong>
                {{
                  createState.state === 'QUEUED'
                    ? '请求已入队，正在创建订单'
                    : createState.state === 'CREATED'
                      ? '订单创建成功'
                      : '订单创建失败'
                }}
              </strong>
              <p>
                {{
                  createState.state === 'QUEUED'
                    ? '正在确认库存并生成订单，请稍候…'
                    : createState.message || '即将跳转到订单详情'
                }}
              </p>
              <span class="mono order-no">{{ currentOrderNo }}</span>
            </div>
          </div>
          <template #footer>
            <el-button v-if="createState.state !== 'QUEUED'" @click="closeCreating">关闭</el-button>
          </template>
        </el-dialog>
      </template>
    </div>
  </div>
</template>

<style scoped>
.breadcrumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 22px;
}

.breadcrumb a:hover {
  color: var(--brand);
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 54px;
  align-items: start;
}

.gallery {
  position: sticky;
  top: 94px;
}

.main-image {
  position: relative;
  aspect-ratio: 1 / 1;
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--surface-soft);
  border: 1px solid var(--line);
}

.main-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.img-index {
  position: absolute;
  right: 14px;
  bottom: 14px;
  padding: 5px 9px;
  border-radius: 6px;
  background: rgba(16, 24, 40, 0.72);
  color: #fff;
  font-size: 9px;
  letter-spacing: 0.08em;
}

.thumbs {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.thumbs button {
  width: 68px;
  height: 68px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
  cursor: pointer;
}

.thumbs button.active {
  border-color: var(--brand);
  box-shadow: 0 0 0 2px rgba(232, 74, 39, 0.14);
}

.thumbs img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.info {
  padding-top: 4px;
}

.info-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.like-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: #fff;
  color: var(--muted);
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.like-chip:hover {
  border-color: var(--brand);
  color: var(--brand-deep);
}

.like-chip.active {
  border-color: #f8d3c8;
  background: var(--brand-soft);
  color: var(--brand-deep);
}

.info h1 {
  margin-top: 14px;
  font-size: 26px;
  line-height: 1.35;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.summary {
  margin-top: 14px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--muted);
}

.price-panel {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
  padding: 16px 18px;
  border-radius: 6px;
  background: var(--brand-soft);
  border: 1px solid #f8d3c8;
}

.price-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--brand-deep);
}

.price.big {
  font-size: 36px;
  line-height: 1;
}

.sales-note {
  margin-left: auto;
  font-size: 11px;
  color: var(--muted);
}

.purchase-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 22px;
}

.row-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--ink-soft);
}

.stock-hint {
  font-size: 11px;
  color: var(--muted);
}

.purchase-actions {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 22px;
}

.buy-button {
  height: 44px;
  padding: 0 30px;
  border-radius: 6px;
  font-size: 14px;
}

.cart-button {
  height: 44px;
  padding: 0 22px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
}

.shop-cta {
  font-size: 12px;
  font-weight: 500;
  color: var(--brand-deep);
}

.shop-cta:hover {
  text-decoration: underline;
  text-underline-offset: 4px;
}

.assurance-list {
  margin: 26px 0 0;
  padding: 20px 0 0;
  border-top: 1px dashed var(--line);
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.assurance-list li {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 12px;
  color: var(--muted);
}

.assurance-list .el-icon {
  flex: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--teal-soft);
  color: var(--teal);
  font-size: 11px;
}

.detail-copy {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(260px, 0.7fr);
  gap: 20px;
  margin-top: 52px;
}

.copy-block {
  padding: 30px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.copy-block h2 {
  margin-top: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.copy-block > p {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--muted);
}

.spec-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  margin-top: 24px;
  background: var(--line);
  border: 1px solid var(--line);
  border-radius: 6px;
  overflow: hidden;
}

.spec-grid > div {
  padding: 14px;
  background: var(--surface-soft);
}

.spec-grid span {
  display: block;
  font-size: 10px;
  color: var(--muted);
}

.spec-grid strong {
  display: block;
  margin-top: 5px;
  font-size: 13px;
}

.copy-note {
  padding: 24px;
  border-radius: var(--radius);
  background: var(--surface-soft);
  border: 1px solid var(--line);
  color: var(--ink-soft);
}

.copy-note strong {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
}

.copy-note p {
  margin-top: 9px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--muted);
}

.buy-summary {
  display: flex;
  gap: 14px;
  padding: 14px;
  border-radius: 6px;
  background: var(--surface-soft);
  border: 1px solid var(--line);
}

.buy-summary img {
  width: 74px;
  height: 74px;
  border-radius: 6px;
  object-fit: cover;
}

.buy-summary strong {
  display: block;
  font-size: 13px;
  line-height: 1.45;
}

.buy-summary span {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: var(--muted);
}

.buy-summary b {
  display: block;
  margin-top: 8px;
  color: var(--brand-deep);
  font-size: 13px;
}

.buy-form {
  margin-top: 18px;
}

.buy-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 14px;
  border-top: 1px dashed var(--line);
  font-size: 12px;
  color: var(--muted);
}

.buy-total strong {
  font-size: 24px;
  color: var(--brand-deep);
}

.creating-state {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 10px 4px;
}

.creating-state strong {
  font-size: 15px;
}

.creating-state p {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--muted);
}

.order-no {
  display: block;
  margin-top: 8px;
  font-size: 10px;
  color: var(--muted);
}

.pulse {
  flex: none;
  display: flex;
  gap: 5px;
  align-items: center;
  height: 46px;
}

.pulse span {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--brand);
  animation: bounce 1s infinite ease-in-out;
}

.pulse span:nth-child(2) {
  animation-delay: 0.14s;
}

.pulse span:nth-child(3) {
  animation-delay: 0.28s;
}

@keyframes bounce {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  40% {
    transform: translateY(-9px);
    opacity: 1;
  }
}

.state-icon {
  flex: none;
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  font-size: 20px;
  font-weight: 800;
}

.state-icon.ok {
  background: var(--teal-soft);
  color: var(--teal);
}

.state-icon.fail {
  background: #fef3f2;
  color: #d92d20;
}

.detail-loading {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 54px;
}

.gallery-skeleton {
  aspect-ratio: 1;
}

.info-skeleton {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 20px;
}

.line {
  height: 18px;
}

.w40 {
  width: 40%;
}

.w60 {
  width: 60%;
}

.w80 {
  width: 80%;
  height: 34px;
}

.box {
  height: 120px;
  margin-top: 20px;
}

@media (max-width: 900px) {
  .detail-layout {
    grid-template-columns: 1fr;
    gap: 28px;
  }

  .gallery {
    position: static;
  }

  .detail-copy {
    grid-template-columns: 1fr;
    margin-top: 36px;
  }

  .spec-grid {
    grid-template-columns: 1fr 1fr;
  }

  .detail-loading {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .price-panel {
    gap: 8px;
  }

  .price.big {
    font-size: 34px;
  }

  .sales-note {
    width: 100%;
    margin-left: 0;
  }

  .purchase-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .buy-button {
    width: 100%;
  }

  .copy-block,
  .copy-note {
    padding: 22px;
  }
}
</style>
