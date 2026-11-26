<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { productApi, shopApi } from '../api'
import { normalizePage, resolveServiceImage } from '../utils/format'
import ProductCard from '../components/ProductCard.vue'
import ShopCard from '../components/ShopCard.vue'
import PriceText from '../components/PriceText.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const keywordDraft = ref('')
const keyword = ref('')
const products = ref([])
const productTotal = ref(0)
const shops = ref([])
const shopTotal = ref(0)
const services = ref([])
const serviceTotal = ref(0)

const hasKeyword = computed(() => Boolean(keyword.value))
const resultCount = computed(
  () => products.value.length + shops.value.length + services.value.length
)
const hotWords = ['全合成机油', '轮胎', '空调滤清器', '洗车', '保养']

async function load() {
  keyword.value = String(route.query.keyword || '').trim()
  keywordDraft.value = keyword.value
  if (!keyword.value) {
    products.value = []
    shops.value = []
    services.value = []
    productTotal.value = 0
    shopTotal.value = 0
    serviceTotal.value = 0
    loading.value = false
    return
  }
  loading.value = true
  const [productResult, shopResult, serviceResult] = await Promise.allSettled([
    productApi.page({ pageNum: 1, pageSize: 8, keyword: keyword.value }),
    shopApi.search({ pageNum: 1, pageSize: 4, keyword: keyword.value }),
    shopApi.serviceSearch({ pageNum: 1, pageSize: 4, keyword: keyword.value })
  ])
  if (productResult.status === 'fulfilled') {
    const page = normalizePage(productResult.value, 8)
    products.value = page.records
    productTotal.value = page.total
  } else {
    products.value = []
    productTotal.value = 0
  }
  if (shopResult.status === 'fulfilled') {
    const page = normalizePage(shopResult.value, 4)
    shops.value = page.records
    shopTotal.value = page.total
  } else {
    shops.value = []
    shopTotal.value = 0
  }
  if (serviceResult.status === 'fulfilled') {
    const page = normalizePage(serviceResult.value, 4)
    services.value = page.records
    serviceTotal.value = page.total
  } else {
    services.value = []
    serviceTotal.value = 0
  }
  loading.value = false
}

watch(() => route.query.keyword, load, { immediate: true })

function submit() {
  const text = keywordDraft.value.trim()
  router.push({ path: '/search', query: text ? { keyword: text } : {} })
}

function searchWord(word) {
  router.push({ path: '/search', query: { keyword: word } })
}

function serviceLink(service) {
  return {
    path: `/shops/${service.shopId}`,
    query: { serviceId: service.id, book: '1' }
  }
}

function fallback(event) {
  event.target.src = '/images/shop/service-maintenance.jpg'
}
</script>

<template>
  <div class="page">
    <div class="container">
      <header class="search-head">
        <h1>搜商品，也搜门店服务</h1>
        <form class="search-form" @submit.prevent="submit">
          <el-icon><Search /></el-icon>
          <input v-model="keywordDraft" type="search" placeholder="试试「全合成机油」「洗车」或门店名称" />
          <button type="submit">搜索</button>
        </form>
        <div class="hot-words">
          <span>热门：</span>
          <button v-for="word in hotWords" :key="word" type="button" @click="searchWord(word)">
            {{ word }}
          </button>
        </div>
      </header>

      <div v-if="loading" class="result-block">
        <div v-for="n in 4" :key="n" class="skeleton-block result-skeleton"></div>
      </div>

      <template v-else-if="hasKeyword">
        <p class="result-summary">
          「{{ keyword }}」共找到 {{ resultCount }} 条结果
        </p>

        <el-empty v-if="!resultCount" description="没有找到匹配的内容，换个关键词试试" :image-size="120" />

        <template v-else>
          <section v-if="products.length" class="result-section">
            <div class="section-head">
              <h2>商品 <small>{{ productTotal }}</small></h2>
              <router-link :to="{ path: '/products', query: { keyword } }" class="section-link">
                查看全部商品 →
              </router-link>
            </div>
            <div class="product-grid">
              <ProductCard v-for="product in products" :key="product.id" :product="product" />
            </div>
          </section>

          <section v-if="shops.length" class="result-section">
            <div class="section-head">
              <h2>门店 <small>{{ shopTotal }}</small></h2>
              <router-link :to="{ path: '/shops', query: { keyword } }" class="section-link">
                查看全部门店 →
              </router-link>
            </div>
            <div class="shop-grid">
              <ShopCard v-for="shop in shops" :key="shop.id" :shop="shop" />
            </div>
          </section>

          <section v-if="services.length" class="result-section">
            <div class="section-head">
              <h2>服务项目 <small>{{ serviceTotal }}</small></h2>
              <router-link
                :to="{ path: '/shops', query: { mode: 'service', keyword } }"
                class="section-link"
              >
                查看全部服务 →
              </router-link>
            </div>
            <div class="service-grid">
              <router-link
                v-for="service in services"
                :key="service.id"
                class="service-card"
                :to="serviceLink(service)"
              >
                <img :src="resolveServiceImage(service)" :alt="service.itemName" @error="fallback" />
                <div class="service-info">
                  <strong>{{ service.itemName }}</strong>
                  <span>{{ service.shopName }} · {{ service.durationMin }} 分钟</span>
                  <PriceText :value="service.price" class="service-price" />
                </div>
              </router-link>
            </div>
          </section>
        </template>
      </template>

      <div v-else class="search-empty">
        <p>输入关键词，同时搜索商品、门店和服务项目。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.search-head {
  padding: 4px 0 16px;
  border-bottom: 1px solid var(--line);
}

.search-head h1 {
  margin-top: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.search-form {
  display: flex;
  align-items: center;
  gap: 10px;
  width: min(100%, 720px);
  height: 44px;
  margin-top: 16px;
  padding: 0 6px 0 14px;
  border: 2px solid var(--brand);
  border-radius: 6px;
  background: #fff;
}

.search-form .el-icon {
  color: var(--muted);
  font-size: 17px;
}

.search-form input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 14px;
}

.search-form button {
  flex: none;
  height: 38px;
  padding: 0 20px;
  border: 0;
  border-radius: 4px;
  background: var(--brand);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.search-form button:hover {
  background: #ef6543;
}

.hot-words {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  font-size: 12px;
  color: var(--muted);
}

.hot-words button {
  border: 0;
  background: transparent;
  padding: 0;
  color: var(--ink-soft);
  font-size: 12px;
  cursor: pointer;
  border-bottom: 1px solid transparent;
}

.hot-words button:hover {
  color: var(--brand);
  border-color: var(--brand);
}

.result-summary {
  margin-top: 20px;
  font-size: 12px;
  color: var(--muted);
}

.result-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 22px;
}

.result-skeleton {
  height: 110px;
}

.result-section {
  margin-top: 34px;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-head h2 {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.section-head h2 small {
  margin-left: 5px;
  font-size: 12px;
  font-weight: 500;
  color: var(--muted);
}

.section-link {
  font-size: 12px;
  font-weight: 600;
  color: var(--brand-deep);
}

/* 聚合搜索页商品区用高密度小卡：结果只有 1-2 条时不至于撑出大片空白，
   把门店/服务区挤到屏幕底端。仅在本页覆盖 ProductCard 样式。 */
.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.product-grid :deep(.media) {
  aspect-ratio: 4 / 3;
}

.product-grid :deep(.body) {
  padding: 8px 10px 10px;
}

.product-grid :deep(h3) {
  min-height: 0;
  -webkit-line-clamp: 1;
}

.product-grid :deep(.meta) {
  margin-top: 3px;
}

.product-grid :deep(.foot) {
  margin-top: 6px;
}

.product-grid :deep(.price) {
  font-size: 17px;
}

.shop-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.service-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.service-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: #fff;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.service-card:hover {
  border-color: var(--line-strong);
  box-shadow: var(--shadow-sm);
}

.service-card img {
  flex: none;
  width: 76px;
  height: 76px;
  border-radius: 6px;
  object-fit: cover;
  background: var(--surface-soft);
}

.service-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.service-info strong {
  font-size: 13px;
  line-height: 1.45;
}

.service-info span {
  font-size: 10px;
  color: var(--muted);
}

.service-price {
  margin-top: 2px;
  font-size: 17px;
}

.search-empty {
  margin-top: 60px;
  text-align: center;
  font-size: 13px;
  color: var(--muted);
}

@media (max-width: 800px) {
  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .shop-grid,
  .service-grid {
    grid-template-columns: 1fr;
  }
}
</style>
