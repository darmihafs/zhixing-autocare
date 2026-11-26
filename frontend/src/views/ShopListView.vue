<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { shopApi } from '../api'
import { normalizePage, resolveServiceImage } from '../utils/format'
import ShopCard from '../components/ShopCard.vue'
import PriceText from '../components/PriceText.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const records = ref([])
const services = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 8
const keywordDraft = ref('')
const cityDraft = ref('')
const activeKeyword = ref('')
const activeCity = ref('')
const searchMode = ref('shop')
const searched = ref(false)

const unit = computed(() => (searchMode.value === 'service' ? '项服务' : '家门店'))

async function load() {
  loading.value = true
  try {
    if (searchMode.value === 'service') {
      const hasKeyword = Boolean(activeKeyword.value)
      const data = hasKeyword
        ? await shopApi.serviceSearch({
            pageNum: pageNum.value,
            pageSize,
            keyword: activeKeyword.value
          })
        : await shopApi.services({ pageNum: pageNum.value, pageSize })
      const page = normalizePage(data, pageSize)
      services.value = page.records
      records.value = []
      total.value = page.total
      return
    }
    let data
    if (activeKeyword.value || activeCity.value) {
      data = await shopApi.search({
        pageNum: pageNum.value,
        pageSize,
        keyword: activeKeyword.value || undefined,
        city: activeCity.value || undefined
      })
    } else {
      data = await shopApi.page({ pageNum: pageNum.value, pageSize })
    }
    const page = normalizePage(data, pageSize)
    records.value = page.records
    services.value = []
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function applyQuery() {
  const query = route.query
  searchMode.value = query.mode === 'service' ? 'service' : 'shop'
  activeKeyword.value = query.keyword ? String(query.keyword) : ''
  activeCity.value = query.city ? String(query.city) : ''
  keywordDraft.value = activeKeyword.value
  cityDraft.value = activeCity.value
  pageNum.value = query.pageNum ? Math.max(1, Number(query.pageNum)) : 1
  searched.value = Boolean(activeKeyword.value || activeCity.value || query.pageNum)
  load()
}

watch(() => route.query, applyQuery, { immediate: true })

function pushQuery(patch) {
  const next = {
    ...(activeKeyword.value ? { keyword: activeKeyword.value } : {}),
    ...(activeCity.value && searchMode.value === 'shop' ? { city: activeCity.value } : {}),
    ...(searchMode.value === 'service' ? { mode: 'service' } : {}),
    ...(pageNum.value > 1 ? { pageNum: pageNum.value } : {}),
    ...patch
  }
  Object.keys(next).forEach((key) => {
    if (next[key] === undefined || next[key] === null || next[key] === '') delete next[key]
  })
  router.push({ path: '/shops', query: next })
}

function switchMode(next) {
  if (searchMode.value === next) return
  searchMode.value = next
  pageNum.value = 1
  pushQuery({ mode: next === 'service' ? 'service' : undefined, pageNum: undefined })
}

function search() {
  activeKeyword.value = keywordDraft.value.trim()
  activeCity.value = searchMode.value === 'shop' ? cityDraft.value.trim() : ''
  pageNum.value = 1
  pushQuery({ keyword: activeKeyword.value || undefined, city: activeCity.value || undefined, pageNum: undefined })
}

function reset() {
  keywordDraft.value = ''
  cityDraft.value = ''
  activeKeyword.value = ''
  activeCity.value = ''
  pageNum.value = 1
  router.push({
    path: '/shops',
    query: searchMode.value === 'service' ? { mode: 'service' } : {}
  })
}

function changePage(page) {
  pageNum.value = page
  pushQuery({ pageNum: page })
}

function fallback(event) {
  event.target.src = '/images/shop/shop-1.jpg'
}
</script>

<template>
  <div class="page">
    <div class="container">
      <header class="shop-head">
        <div class="head-row">
          <div>
            <h1>附近门店，保养洗车就近搞定</h1>
            <p>选好门店与服务项目，约好到店时间，到店直接施工。</p>
          </div>
          <span class="result-count"><b class="num">{{ total }}</b> {{ unit }}</span>
        </div>
      </header>

      <div class="mode-switch">
        <button type="button" :class="{ active: searchMode === 'shop' }" @click="switchMode('shop')">
          门店
        </button>
        <button type="button" :class="{ active: searchMode === 'service' }" @click="switchMode('service')">
          服务项目
        </button>
      </div>

      <form class="search-panel" @submit.prevent="search">
        <div class="field">
          <label>关键词</label>
          <input
            v-model="keywordDraft"
            type="search"
            :placeholder="
              searchMode === 'service'
                ? '服务名称、项目类型，如「机油」「空调」'
                : '门店名、服务、区域，如「青山湖」「空调」'
            "
          />
        </div>
        <div v-if="searchMode === 'shop'" class="field city-field">
          <label>城市</label>
          <input v-model="cityDraft" type="search" placeholder="南昌市" />
        </div>
        <button type="submit" class="search-button">
          <el-icon><Search /></el-icon>
          {{ searchMode === 'service' ? '搜索服务' : '搜索门店' }}
        </button>
        <button v-if="searched" type="button" class="reset-button" @click="reset">重置</button>
      </form>

      <div v-if="loading" class="shop-grid">
        <div v-for="n in 4" :key="n" class="skeleton-block shop-skeleton"></div>
      </div>

      <el-empty
        v-else-if="searchMode === 'shop' && !records.length"
        description="没有找到匹配的门店，换个关键词试试"
        :image-size="110"
      />
      <el-empty
        v-else-if="searchMode === 'service' && !services.length"
        description="没有找到匹配的服务项目，换个关键词试试"
        :image-size="110"
      />

      <div v-else-if="searchMode === 'shop'" class="shop-grid">
        <ShopCard v-for="shop in records" :key="shop.id" :shop="shop" />
      </div>

      <div v-else class="service-grid">
        <article v-for="service in services" :key="service.id" class="service-card">
          <router-link
            class="service-cover"
            :to="{ path: `/shops/${service.shopId}`, query: { serviceId: service.id, book: '1' } }"
          >
            <img :src="resolveServiceImage(service)" :alt="service.itemName" @error="fallback" />
            <span class="cover-cat">{{ service.category }}</span>
          </router-link>
          <div class="service-main">
            <div class="service-tags">
              <span class="duration num">施工 {{ service.durationMin }} 分钟</span>
            </div>
            <h3>{{ service.itemName }}</h3>
            <p>{{ service.description }}</p>
            <span class="shop-name">{{ service.shopName }}</span>
            <div class="service-foot">
              <div class="service-price-row">
                <PriceText :value="service.price" class="service-price" />
                <span class="price-tag">含工时</span>
                <s v-if="service.originalPrice">¥{{ service.originalPrice }}</s>
                <span class="sales num">已售 {{ service.sales }}</span>
              </div>
              <el-button
                type="primary"
                size="small"
                @click="router.push({ path: `/shops/${service.shopId}`, query: { serviceId: service.id, book: '1' } })"
              >
                预约
              </el-button>
            </div>
          </div>
        </article>
      </div>

      <div v-if="total > pageSize" class="pager">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="total"
          :page-size="pageSize"
          :current-page="pageNum"
          @current-change="changePage"
        />
      </div>

    </div>
  </div>
</template>

<style scoped>
.shop-head {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
}

.head-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 30px;
}

.shop-head h1 {
  font-size: 20px;
  line-height: 1.3;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.shop-head p {
  max-width: 680px;
  margin-top: 5px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--muted);
}

.result-count {
  flex: none;
  font-size: 12px;
  color: var(--muted);
}

.result-count b {
  font-size: 15px;
  color: var(--ink);
  margin-right: 3px;
}

.mode-switch {
  display: inline-flex;
  gap: 4px;
  margin-top: 14px;
  padding: 3px;
  border-radius: 6px;
  background: #fff;
  border: 1px solid var(--line);
}

.mode-switch button {
  border: 0;
  background: transparent;
  padding: 7px 18px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--muted);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.mode-switch button.active {
  background: var(--brand);
  color: #fff;
}

.search-panel {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-top: 10px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.field {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.city-field {
  flex: 0 0 180px;
}

.field label {
  font-size: 10px;
  font-weight: 500;
  color: var(--muted);
}

.field input {
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  outline: 0;
  font-size: 13px;
  background: var(--surface-soft);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.field input:focus {
  border-color: var(--brand);
  box-shadow: 0 0 0 2px var(--brand-soft);
}

.search-button {
  flex: none;
  height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 20px;
  border: 0;
  border-radius: 6px;
  background: var(--brand);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s ease;
}

.search-button:hover {
  background: #ef6543;
}

.reset-button {
  flex: none;
  height: 34px;
  padding: 0 12px;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
}

.shop-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 16px;
}

.shop-skeleton {
  height: 150px;
}

.service-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 16px;
}

.service-card {
  display: flex;
  gap: 14px;
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

.service-cover {
  position: relative;
  flex: none;
  width: 104px;
  height: 104px;
  border-radius: 6px;
  overflow: hidden;
  background: var(--surface-soft);
}

.service-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-cat {
  position: absolute;
  left: 0;
  top: 6px;
  max-width: 100%;
  padding: 2px 6px;
  background: rgba(20, 23, 31, 0.72);
  color: #fff;
  font-size: 10px;
  line-height: 1.6;
  border-left: 3px solid var(--brand);
  border-radius: 0 4px 4px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.service-tags {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.duration {
  font-size: 10px;
  color: var(--muted);
}

.service-main h3 {
  margin-top: 7px;
  font-size: 14px;
  line-height: 1.45;
  font-weight: 500;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.service-main p {
  margin-top: 5px;
  font-size: 11px;
  line-height: 1.65;
  color: var(--muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.shop-name {
  margin-top: 5px;
  font-size: 10px;
  color: var(--muted);
}

.service-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
}

.service-price-row {
  display: flex;
  align-items: baseline;
  gap: 5px;
  min-width: 0;
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
  line-height: 1.6;
}

.service-foot .el-button {
  height: 30px;
  padding: 0 14px;
  border-radius: 6px;
  font-weight: 600;
}

.service-foot .el-button--primary:hover,
.service-foot .el-button--primary:focus {
  background: #1d2129;
  border-color: #1d2129;
  color: #fff;
}

.service-price-row s {
  font-size: 10px;
  color: #b0b6bf;
}

.sales {
  display: block;
  margin-top: 2px;
  font-size: 9px;
  color: var(--muted);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 30px;
}

@media (max-width: 700px) {
  .shop-grid,
  .service-grid {
    grid-template-columns: 1fr;
  }
}

</style>
