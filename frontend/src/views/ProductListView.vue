<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { productApi } from '../api'
import { normalizePage } from '../utils/format'
import ProductCard from '../components/ProductCard.vue'

const route = useRoute()
const router = useRouter()
const categories = ref([])
const records = ref([])
const total = ref(0)
const loading = ref(true)
const keywordDraft = ref('')
const activeCategory = ref(null)
const activeKeyword = ref('')
const pageNum = ref(1)
const pageSize = 12
const sortKey = ref('')

// 商品分页接口不支持排序参数，这里只对当前页已加载结果做本地排序，并在界面上如实标注
const sortedRecords = computed(() => {
  if (!sortKey.value) return records.value
  const list = [...records.value]
  if (sortKey.value === 'price-asc') list.sort((a, b) => Number(a.price) - Number(b.price))
  if (sortKey.value === 'price-desc') list.sort((a, b) => Number(b.price) - Number(a.price))
  if (sortKey.value === 'sales') list.sort((a, b) => Number(b.sales || 0) - Number(a.sales || 0))
  return list
})

onMounted(async () => {
  categories.value = (await productApi.categories()) || []
})

async function load() {
  loading.value = true
  try {
    const data = await productApi.page({
      pageNum: pageNum.value,
      pageSize,
      categoryId: activeCategory.value || undefined,
      keyword: activeKeyword.value || undefined
    })
    const page = normalizePage(data, pageSize)
    records.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function applyQuery() {
  const query = route.query
  activeCategory.value = query.categoryId ? String(query.categoryId) : null
  activeKeyword.value = query.keyword ? String(query.keyword) : ''
  keywordDraft.value = activeKeyword.value
  pageNum.value = query.pageNum ? Math.max(1, Number(query.pageNum)) : 1
  load()
}

watch(() => route.query, applyQuery, { immediate: true })

function pushQuery(patch) {
  const next = {
    ...(activeCategory.value ? { categoryId: activeCategory.value } : {}),
    ...(activeKeyword.value ? { keyword: activeKeyword.value } : {}),
    ...(pageNum.value > 1 ? { pageNum: pageNum.value } : {}),
    ...patch
  }
  Object.keys(next).forEach((key) => {
    if (next[key] === undefined || next[key] === null || next[key] === '') delete next[key]
  })
  router.push({ path: '/products', query: next })
}

function selectCategory(id) {
  activeCategory.value = id
  pageNum.value = 1
  pushQuery({ categoryId: id || undefined, pageNum: undefined })
}

function search() {
  activeKeyword.value = keywordDraft.value.trim()
  pageNum.value = 1
  pushQuery({ keyword: activeKeyword.value || undefined, pageNum: undefined })
}

function clearKeyword() {
  keywordDraft.value = ''
  activeKeyword.value = ''
  pageNum.value = 1
  pushQuery({ keyword: undefined, pageNum: undefined })
}

function changePage(page) {
  pageNum.value = page
  pushQuery({ pageNum: page })
}
</script>

<template>
  <div class="page">
    <div class="container">
      <header class="page-head">
        <div>
          <h1>养车商城</h1>
          <p>机油、滤清器、轮胎与车载好物，正品直供，下单后可到店安装。</p>
        </div>
        <form class="head-search" @submit.prevent="search">
          <el-icon><Search /></el-icon>
          <input v-model="keywordDraft" type="search" placeholder="搜索商品名称或品牌" />
          <button type="submit">搜索</button>
        </form>
      </header>

      <div class="filter-bar">
        <span class="filter-label">分类</span>
        <div class="filter-options">
          <button
            class="category-button"
            :class="{ active: !activeCategory }"
            type="button"
            @click="selectCategory(null)"
          >
            全部商品
          </button>
          <button
            v-for="cat in categories"
            :key="cat.id"
            class="category-button"
            :class="{ active: activeCategory === cat.id }"
            type="button"
            @click="selectCategory(cat.id)"
          >
            {{ cat.categoryName }}
          </button>
        </div>
        <router-link to="/shops" class="filter-side-link">到店服务 →</router-link>
      </div>

      <section class="results">
        <div class="result-bar">
          <div>
            <strong class="num">{{ total }}</strong> 件商品
            <span v-if="activeKeyword" class="filter-chip">
              关键词：{{ activeKeyword }}
              <button type="button" @click="clearKeyword">×</button>
            </span>
            <span v-if="activeCategory" class="filter-chip">
              {{ categories.find((c) => c.id === activeCategory)?.categoryName || '分类' }}
              <button type="button" @click="selectCategory(null)">×</button>
            </span>
          </div>
          <div class="sort-control">
            <el-select v-model="sortKey" size="small" style="width: 132px">
              <el-option label="默认顺序" value="" />
              <el-option label="价格从低到高" value="price-asc" />
              <el-option label="价格从高到低" value="price-desc" />
              <el-option label="销量优先" value="sales" />
            </el-select>
            <span v-if="sortKey" class="sort-hint">仅对当前页排序</span>
          </div>
        </div>

        <div v-if="loading" class="product-grid">
          <div v-for="n in 8" :key="n" class="skeleton-block card-skeleton"></div>
        </div>

        <el-empty
          v-else-if="!records.length"
          description="没有找到匹配的商品，换个关键词试试"
          :image-size="110"
        />

        <div v-else class="product-grid">
          <ProductCard v-for="item in sortedRecords" :key="item.id" :product="item" />
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
      </section>
    </div>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 30px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}

.page-head h1 {
  margin-top: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.page-head p {
  margin-top: 6px;
  font-size: 12px;
  color: var(--muted);
}

.head-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: min(360px, 38vw);
  height: 40px;
  padding: 0 5px 0 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: #fff;
}

.head-search .el-icon {
  color: var(--muted);
}

.head-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  font-size: 13px;
  background: transparent;
}

.head-search button {
  border: 0;
  height: 32px;
  padding: 0 15px;
  border-radius: 4px;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.filter-label {
  flex: none;
  font-size: 12px;
  color: var(--muted);
}

.filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.filter-side-link {
  flex: none;
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  color: var(--brand-deep);
}

.filter-side-link:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.category-button {
  border: 1px solid transparent;
  background: transparent;
  padding: 5px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--ink-soft);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.category-button:hover {
  background: var(--surface-soft);
  color: var(--ink);
}

.category-button.active {
  background: var(--brand-soft);
  border-color: #f8d3c8;
  color: var(--brand-deep);
  font-weight: 600;
}

.results {
  margin-top: 14px;
}

.result-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  font-size: 12px;
  color: var(--muted);
}

.result-bar strong {
  color: var(--ink);
  font-size: 15px;
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: 8px;
  padding: 3px 8px;
  border-radius: 4px;
  background: #fff;
  border: 1px solid var(--line);
  color: var(--ink-soft);
}

.filter-chip button {
  border: 0;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0;
}

.sort-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sort-hint {
  font-size: 10px;
  color: var(--muted);
  white-space: nowrap;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.card-skeleton {
  aspect-ratio: 0.72;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

@media (max-width: 980px) {
  .filter-bar {
    flex-wrap: wrap;
  }

  .filter-side-link {
    display: none;
  }

  .product-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }

  .head-search {
    width: 100%;
  }

  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .result-bar {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }
}
</style>
