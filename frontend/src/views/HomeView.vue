<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Location, Search } from '@element-plus/icons-vue'
import { productApi, shopApi } from '../api'
import { normalizePage, resolveServiceImage } from '../utils/format'
import ProductCard from '../components/ProductCard.vue'
import PriceText from '../components/PriceText.vue'

const router = useRouter()
const loading = ref(true)
const categories = ref([])
const products = ref([])
const services = ref([])
const shops = ref([])
const shopTotal = ref(0)
const productTotal = ref(0)
const guideShop = ref(null)
const keyword = ref('')

const hotWords = ['全合成机油', '轮胎', '空调滤清器', '行车记录仪']

// 热销楼层取真实 sales 降序 TOP4；精选楼层取其余 4 件，两楼层不重复展示同一商品
const salesRanked = computed(() =>
  [...products.value].sort((a, b) => Number(b.sales || 0) - Number(a.sales || 0))
)
const hotProducts = computed(() => salesRanked.value.slice(0, 4))
const pickProducts = computed(() => salesRanked.value.slice(4, 8))

// 快速预约入口：从已加载的真实服务项目里派生分类，不引入任何新接口
const quickCategories = computed(() => {
  const seen = []
  for (const service of services.value) {
    if (service.category && !seen.includes(service.category)) seen.push(service.category)
  }
  return seen.slice(0, 5)
})

const guarantees = [
  { icon: 'shield', title: '正品保障', desc: '机油轮胎品牌直供' },
  { icon: 'wrench', title: '到店免费安装', desc: '专业技师施工' },
  { icon: 'tag', title: '明码标价', desc: '项目价格提前可见' },
  { icon: 'camera', title: '车主实拍评价', desc: '完成服务后再评价' }
]

onMounted(async () => {
  try {
    const [cats, productPage, servicePage, shopPage] = await Promise.all([
      productApi.categories(),
      productApi.page({ pageNum: 1, pageSize: 8 }),
      shopApi.services({ pageNum: 1, pageSize: 4 }),
      shopApi.page({ pageNum: 1, pageSize: 3 })
    ])
    categories.value = cats || []
    const productPg = normalizePage(productPage, 8)
    products.value = productPg.records
    productTotal.value = productPg.total
    services.value = normalizePage(servicePage, 4).records
    const shopPg = normalizePage(shopPage, 3)
    shops.value = shopPg.records
    shopTotal.value = shopPg.total
    guideShop.value = shopPg.records[0] || null
  } finally {
    loading.value = false
  }
})

function search() {
  const text = keyword.value.trim()
  router.push({ path: '/search', query: text ? { keyword: text } : {} })
}

function searchWord(word) {
  router.push({ path: '/search', query: { keyword: word } })
}
</script>

<template>
  <div class="home">
    <div class="guide-bar">
      <div class="container guide-inner">
        <span class="guide-city">
          <el-icon><Location /></el-icon>
          南昌市
        </span>
        <span class="guide-promise">正品保障 · 到店免费安装 · 满 99 元包邮</span>
        <nav class="guide-links">
          <router-link to="/shops?mode=service" class="guide-book">预约到店服务</router-link>
          <span v-if="guideShop?.contactPhone" class="guide-info">客服热线 {{ guideShop.contactPhone }}</span>
          <span v-if="guideShop?.businessHours" class="guide-info">营业时间 {{ guideShop.businessHours }}</span>
        </nav>
      </div>
    </div>

    <div class="container">
      <section class="home-hero">
        <aside class="category-nav">
          <h2>养车分类</h2>
          <div v-if="loading" class="category-skeleton">
            <span v-for="n in 6" :key="n" class="skeleton-block"></span>
          </div>
          <template v-else>
            <router-link
              v-for="cat in categories"
              :key="cat.id"
              :to="{ path: '/products', query: { categoryId: cat.id } }"
              class="category-row"
            >
              <span>{{ cat.categoryName }}</span>
              <el-icon><ArrowRight /></el-icon>
            </router-link>
          </template>
          <router-link to="/shops" class="category-more">附近门店与到店服务</router-link>
        </aside>

        <div class="banner">
          <div class="banner-main">
            <div class="banner-copy">
              <span class="banner-tag">秋冬保养季</span>
              <h1>全合成机油套餐<br />到店免工时</h1>
              <p>精选全合成机油 + 机滤 + 工时，一次到店全部搞定。</p>
              <div class="banner-actions">
                <router-link
                  :to="{ path: '/shops/1', query: { serviceId: 1, book: '1' } }"
                  class="banner-primary"
                >
                  立即预约
                </router-link>
                <router-link to="/shops?mode=service" class="banner-secondary">
                  查看全部服务
                </router-link>
              </div>
            </div>
            <div class="banner-media">
              <img src="/images/shop/banner-maintenance.jpg" alt="门店保养工位" />
            </div>
          </div>
          <div class="banner-stats">
            <div class="stat-cell">
              <b class="num stat-num">{{ shopTotal }}</b>
              <span>直营门店 · 南昌全城就近施工</span>
            </div>
            <div class="stat-cell">
              <b>正品溯源</b>
              <span>机油轮胎品牌直供 · 假一赔十</span>
            </div>
            <div class="stat-cell">
              <b>免工时费</b>
              <span>套餐项目含工时 · 到店不加价</span>
            </div>
          </div>
        </div>
      </section>

      <form class="home-search" @submit.prevent="search">
        <el-icon><Search /></el-icon>
        <input v-model="keyword" type="search" placeholder="搜索机油、轮胎、洗车或门店" />
        <button type="submit">搜索</button>
      </form>
      <div class="hot-words">
        <span>大家在搜：</span>
        <button v-for="word in hotWords" :key="word" type="button" @click="searchWord(word)">
          {{ word }}
        </button>
      </div>
      <div v-if="quickCategories.length" class="quick-match">
        <span>按服务快速预约：</span>
        <router-link
          v-for="cat in quickCategories"
          :key="cat"
          :to="{ path: '/shops', query: { mode: 'service', keyword: cat } }"
        >
          {{ cat }}
        </router-link>
      </div>

      <section class="guarantee-strip">
        <div v-for="item in guarantees" :key="item.title" class="guarantee-item">
          <svg v-if="item.icon === 'shield'" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M8 1.6 13.4 3.5v4.1c0 3.3-2.2 5.7-5.4 6.8-3.2-1.1-5.4-3.5-5.4-6.8V3.5Z" />
            <path d="m5.6 8 1.7 1.7 3.1-3.4" />
          </svg>
          <svg v-else-if="item.icon === 'wrench'" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M10.2 2.2a3.6 3.6 0 0 0-4.5 4.5L2.2 10.2a1.6 1.6 0 0 0 2.3 2.3l3.5-3.5a3.6 3.6 0 0 0 4.5-4.5L10.3 6.7 8.5 4.9Z" />
          </svg>
          <svg v-else-if="item.icon === 'tag'" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M2.2 2.2h5.2l6.4 6.4-5.2 5.2-6.4-6.4Z" />
            <circle cx="5" cy="5" r="1" />
          </svg>
          <svg v-else viewBox="0 0 16 16" aria-hidden="true">
            <path d="M2 4.6h3l1.2-1.8h3.6L11 4.6h3v8.2H2Z" />
            <circle cx="8" cy="8.6" r="2.3" />
          </svg>
          <strong>{{ item.title }}</strong>
          <span>{{ item.desc }}</span>
        </div>
      </section>

      <section class="section">
        <div class="section-head">
          <div>
            <h2>热门养车服务</h2>
            <p>就近预约，到店直接施工，不用排队等位。</p>
          </div>
          <router-link to="/shops?mode=service" class="section-link">
            全部服务 <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>

        <div v-if="loading" class="service-grid">
          <div v-for="n in 4" :key="n" class="skeleton-block service-skeleton"></div>
        </div>
        <div v-else class="service-grid">
          <router-link
            v-for="service in services"
            :key="service.id"
            class="service-card"
            :to="{ path: `/shops/${service.shopId}`, query: { serviceId: service.id, book: '1' } }"
          >
            <div class="service-media">
              <img :src="resolveServiceImage(service)" :alt="service.itemName" loading="lazy" />
              <span class="service-cat">{{ service.category }}</span>
            </div>
            <div class="service-body">
              <h3>{{ service.itemName }}</h3>
              <p class="service-shop">{{ service.shopName }}</p>
              <p class="service-specs">
                <span class="num">施工约 {{ service.durationMin }} 分钟</span>
                <span v-if="service.description" class="service-desc">{{ service.description }}</span>
              </p>
              <div class="service-foot">
                <PriceText :value="service.price" class="service-price" />
                <span class="price-tag">含工时</span>
                <span class="service-sales num">已售 {{ service.sales }}</span>
                <span class="service-book">预约</span>
              </div>
            </div>
          </router-link>
        </div>
      </section>

      <section class="section">
        <div class="section-head">
          <div>
            <h2>热销汽车好物</h2>
            <p>按车主真实销量排序的常购清单，下单后可到店安装。</p>
          </div>
          <router-link to="/products" class="section-link">
            进入商城 <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>

        <div v-if="loading" class="product-grid">
          <div v-for="n in 4" :key="n" class="skeleton-block product-skeleton"></div>
        </div>
        <div v-else class="product-grid">
          <ProductCard v-for="product in hotProducts" :key="product.id" :product="product" />
        </div>
      </section>

      <section class="section">
        <div class="section-head">
          <div>
            <h2>精选养车用品</h2>
            <p>机油、滤清器、轮胎与配件专场，正品直供，下单后可到店安装。</p>
          </div>
          <router-link to="/products" class="section-link">
            进入商城 <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>

        <div class="pick-floor">
          <aside class="pick-theme">
            <h3>养车用品专场</h3>
            <p>机油 · 滤芯 · 轮胎 · 配件</p>
            <div class="pick-total">
              <b class="num">{{ productTotal }}</b>
              <span>件正品现货</span>
            </div>
            <nav class="pick-links">
              <router-link
                v-for="cat in categories.slice(0, 4)"
                :key="cat.id"
                :to="{ path: '/products', query: { categoryId: cat.id } }"
              >
                <span>{{ cat.categoryName }}</span>
                <el-icon><ArrowRight /></el-icon>
              </router-link>
            </nav>
            <router-link to="/products" class="pick-all">查看全部用品 →</router-link>
          </aside>
          <div v-if="loading" class="pick-grid">
            <div v-for="n in 4" :key="n" class="skeleton-block product-skeleton"></div>
          </div>
          <div v-else class="pick-grid">
            <ProductCard v-for="product in pickProducts" :key="product.id" :product="product" />
          </div>
        </div>
      </section>

      <section class="section">
        <div class="section-head">
          <div>
            <h2>附近直营门店</h2>
            <p>真实营业时间与地址，就近选择，到店直接施工。</p>
          </div>
          <router-link to="/shops" class="section-link">
            全部门店 <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>

        <div v-if="loading" class="shop-floor">
          <div v-for="n in 3" :key="n" class="skeleton-block floor-shop-skeleton"></div>
        </div>
        <div v-else class="shop-floor">
          <article v-for="shop in shops" :key="shop.id" class="floor-shop-card">
            <div class="floor-shop-head">
              <h3>{{ shop.shopName }}</h3>
              <span class="score num">{{ shop.score }} 分</span>
            </div>
            <p class="floor-shop-line"><span class="lab">营业</span>{{ shop.businessHours }}</p>
            <p class="floor-shop-line"><span class="lab">地址</span>{{ shop.district }}{{ shop.address }}</p>
            <div class="floor-shop-foot">
              <span class="sales num">已服务 {{ shop.sales }} 次</span>
              <router-link :to="`/shops/${shop.id}`" class="floor-book">去预约</router-link>
            </div>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.home {
  padding-bottom: 48px;
}

.guide-bar {
  background: #14171f;
  margin-bottom: 14px;
}

.guide-inner {
  display: flex;
  align-items: center;
  gap: 22px;
  height: 34px;
  font-size: 12px;
  color: #aeb6c2;
}

.guide-city {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #e5e6eb;
}

.guide-promise {
  color: #aeb6c2;
}

.guide-links {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-left: auto;
}

.guide-book {
  color: #ff7a45;
  font-weight: 600;
}

.guide-book:hover {
  color: #ffa07a;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.guide-info {
  color: #8b93a1;
  font-variant-numeric: tabular-nums;
}

.guide-links a:hover {
  color: #ff7a45;
}

.home-hero {
  display: grid;
  grid-template-columns: 206px minmax(0, 1fr);
  gap: 12px;
}

.category-nav {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 12px 0 10px;
}

.category-nav h2 {
  padding: 0 16px 9px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
}

.category-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 16px;
  font-size: 13px;
  color: var(--ink-soft);
  transition: background 0.15s ease, color 0.15s ease;
}

.category-row .el-icon {
  font-size: 11px;
  color: #c2c7cf;
}

.category-row:hover {
  background: var(--brand-soft);
  color: var(--brand-deep);
}

.category-more {
  display: block;
  margin: auto 16px 0;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  font-size: 12px;
  color: var(--brand-deep);
}

.category-skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 16px;
}

.category-skeleton span {
  height: 18px;
}

.banner {
  display: flex;
  flex-direction: column;
  min-height: 380px;
  overflow: hidden;
  background: #181c26;
  border: 1px solid #232a38;
  border-radius: var(--radius-lg);
}

.banner-main {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  flex: 1;
}

.banner-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 30px 34px;
}

.banner-tag {
  display: inline-block;
  width: fit-content;
  padding: 3px 9px;
  border-radius: 4px;
  background: var(--brand);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.banner h1 {
  margin-top: 12px;
  font-size: 34px;
  line-height: 1.26;
  letter-spacing: -0.02em;
  font-weight: 700;
  color: #fff;
}

.banner p {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.7;
  color: #b9c0cc;
}

.banner-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 18px;
}

.banner-primary {
  height: 38px;
  padding: 0 22px;
  display: inline-flex;
  align-items: center;
  border-radius: 6px;
  background: var(--brand);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  transition: background 0.15s ease, color 0.15s ease;
}

.banner-primary:hover {
  background: #fff;
  color: #1d2129;
}

.banner-secondary {
  height: 38px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background: transparent;
  color: #e5e6eb;
  font-size: 12px;
  transition: border-color 0.15s ease, color 0.15s ease;
}

.banner-secondary:hover {
  border-color: #ff7a45;
  color: #ff7a45;
}

.banner-media {
  position: relative;
  overflow: hidden;
}

.banner-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.banner-media::before {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, #181c26 0%, rgba(24, 28, 38, 0.4) 24%, rgba(24, 28, 38, 0) 58%);
}

.banner-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  background: rgba(255, 255, 255, 0.06);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(6px);
}

.stat-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  padding: 12px 20px;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
}

.stat-cell:last-child {
  border-right: 0;
}

.stat-cell b {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
}

.stat-cell b.stat-num {
  font-size: 22px;
  line-height: 1;
  color: #ff7a45;
}

.stat-cell span {
  font-size: 11px;
  color: #9aa3b2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-search {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 44px;
  margin-top: 14px;
  padding: 0 6px 0 14px;
  border: 2px solid var(--brand);
  border-radius: 6px;
  background: #fff;
}

.home-search .el-icon {
  color: var(--muted);
  font-size: 16px;
}

.home-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  font-size: 13px;
  color: var(--ink);
}

.home-search button {
  flex: none;
  height: 32px;
  padding: 0 22px;
  border: 0;
  border-radius: 4px;
  background: var(--brand);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.hot-words {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 9px;
  font-size: 12px;
  color: var(--muted);
}

.hot-words button {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--ink-soft);
  font-size: 12px;
  cursor: pointer;
}

.hot-words button:hover {
  color: var(--brand);
}

.quick-match {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--muted);
}

.quick-match a {
  padding: 3px 9px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: #fff;
  color: var(--ink-soft);
  font-size: 12px;
  transition: border-color 0.15s ease, color 0.15s ease;
}

.quick-match a:hover {
  border-color: var(--brand);
  color: var(--brand-deep);
}

.guarantee-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-top: 14px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.guarantee-item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 48px;
  padding: 0 16px;
  border-right: 1px solid var(--line);
  min-width: 0;
}

.guarantee-item:last-child {
  border-right: 0;
}

.guarantee-item svg {
  flex: none;
  width: 16px;
  height: 16px;
  fill: none;
  stroke: var(--ink-soft);
  stroke-width: 1.4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.guarantee-item strong {
  flex: none;
  font-size: 12px;
  font-weight: 600;
}

.guarantee-item span {
  font-size: 11px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.service-skeleton {
  height: 262px;
}

.service-card {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  overflow: hidden;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.service-card:hover {
  border-color: var(--line-strong);
  box-shadow: var(--shadow-sm);
}

.service-media {
  position: relative;
  aspect-ratio: 4 / 3;
  background: var(--surface-soft);
}

.service-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.service-cat {
  position: absolute;
  left: 0;
  top: 8px;
  padding: 2px 7px 2px 6px;
  border-left: 3px solid var(--brand);
  border-radius: 0 4px 4px 0;
  background: rgba(20, 23, 31, 0.72);
  color: #fff;
  font-size: 10px;
  font-weight: 500;
}

.service-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 10px 12px 12px;
}

.service-body h3 {
  min-height: 40px;
  font-size: 14px;
  line-height: 1.45;
  font-weight: 500;
  color: var(--ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.service-shop {
  margin-top: 4px;
  font-size: 11px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-specs {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 5px;
  font-size: 10px;
  color: var(--ink-soft);
  min-width: 0;
}

.service-specs > span:first-child {
  flex: none;
  padding: 1px 5px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--surface-soft);
}

.service-desc {
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
}

.service-price {
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
}

.service-sales {
  font-size: 10px;
  color: var(--muted);
}

.service-book {
  margin-left: auto;
  padding: 6px 14px;
  border: 0;
  border-radius: 6px;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  transition: background 0.15s ease;
}

.service-card:hover .service-book {
  background: #1d2129;
}

.pick-floor {
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  gap: 12px;
}

.pick-theme {
  display: flex;
  flex-direction: column;
  padding: 20px 18px;
  border-radius: var(--radius-lg);
  background: #1d2129;
  color: #e5e6eb;
}

.pick-theme h3 {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}

.pick-theme > p {
  margin-top: 6px;
  font-size: 11px;
  color: #9aa3b2;
}

.pick-total {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 14px;
  padding: 10px 0;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.pick-total b {
  font-size: 24px;
  font-weight: 700;
  color: #ff7a45;
}

.pick-total span {
  font-size: 11px;
  color: #9aa3b2;
}

.pick-links {
  display: flex;
  flex-direction: column;
  margin-top: 10px;
}

.pick-links a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #e5e6eb;
  transition: background 0.15s ease, color 0.15s ease;
}

.pick-links a .el-icon {
  font-size: 11px;
  color: #8b93a1;
}

.pick-links a:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #ff7a45;
}

.pick-all {
  margin-top: auto;
  padding-top: 12px;
  font-size: 12px;
  font-weight: 600;
  color: #ff7a45;
}

.pick-all:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.pick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.shop-floor {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.floor-shop-skeleton {
  height: 150px;
}

.floor-shop-card {
  display: flex;
  flex-direction: column;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.floor-shop-card:hover {
  border-color: var(--line-strong);
  box-shadow: var(--shadow-sm);
}

.floor-shop-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.floor-shop-head h3 {
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.floor-shop-head .score {
  flex: none;
  font-size: 13px;
  font-weight: 700;
  color: var(--brand-deep);
}

.floor-shop-line {
  margin-top: 7px;
  font-size: 11px;
  color: var(--ink-soft);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.floor-shop-line .lab {
  margin-right: 6px;
  color: var(--muted);
}

.floor-shop-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
}

.floor-shop-foot .sales {
  font-size: 10px;
  color: var(--muted);
}

.floor-book {
  flex: none;
  padding: 6px 14px;
  border-radius: 6px;
  background: #1d2129;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  transition: background 0.15s ease;
}

.floor-book:hover {
  background: var(--brand);
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.product-skeleton {
  aspect-ratio: 0.74;
}

@media (max-width: 1000px) {
  .home-hero {
    grid-template-columns: 1fr;
  }

  .category-nav {
    display: flex;
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
    padding: 8px;
  }

  .category-nav h2 {
    width: 100%;
    padding: 2px 8px 8px;
  }

  .category-row {
    padding: 7px 10px;
  }

  .category-more {
    margin: 0 0 0 auto;
    border-top: 0;
    padding: 7px 10px;
  }

  .banner {
    min-height: 300px;
  }

  .service-grid,
  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .pick-floor {
    grid-template-columns: 1fr;
  }

  .pick-theme {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px 16px;
    padding: 14px 16px;
  }

  .pick-theme h3 {
    font-size: 16px;
  }

  .pick-theme > p {
    margin-top: 0;
  }

  .pick-total {
    margin-top: 0;
    padding: 0;
    border: 0;
  }

  .pick-links {
    flex-direction: row;
    flex-wrap: wrap;
    margin-top: 0;
  }

  .pick-links a {
    padding: 6px 8px;
  }

  .pick-all {
    margin-left: auto;
    padding-top: 0;
  }
}

@media (max-width: 760px) {
  .guide-promise {
    display: none;
  }

  .banner-main {
    grid-template-columns: 1fr;
  }

  .banner-media::before {
    background: linear-gradient(180deg, #181c26 0%, rgba(24, 28, 38, 0) 50%);
  }

  .banner-copy {
    padding: 22px;
  }

  .banner h1 {
    font-size: 26px;
  }

  .banner-media {
    height: 180px;
  }

  .stat-cell {
    padding: 10px 12px;
  }

  .stat-cell b {
    font-size: 14px;
  }

  .stat-cell b.stat-num {
    font-size: 18px;
  }

  .pick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .shop-floor {
    grid-template-columns: 1fr;
  }

  .guarantee-strip {
    grid-template-columns: 1fr 1fr;
  }

  .guarantee-item {
    height: 44px;
    padding: 0 12px;
  }

  .guarantee-item:nth-child(2) {
    border-right: 0;
  }

  .guarantee-item:nth-child(-n + 2) {
    border-bottom: 1px solid var(--line);
  }

  .service-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .guide-links {
    gap: 10px;
  }

  .guide-info {
    display: none;
  }

  .banner-stats {
    grid-template-columns: 1fr;
  }

  .stat-cell {
    border-right: 0;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  }

  .stat-cell:last-child {
    border-bottom: 0;
  }

  .guarantee-item span {
    display: none;
  }

  .service-grid {
    grid-template-columns: 1fr;
  }
}
</style>
