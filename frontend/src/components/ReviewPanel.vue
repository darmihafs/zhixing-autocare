<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CaretTop } from '@element-plus/icons-vue'
import { likeApi, reviewApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { maskUser, normalizePage, shortDate } from '../utils/format'

const props = defineProps({
  itemType: { type: Number, default: 1 },
  itemId: { type: [Number, String], required: true },
  shopId: { type: [Number, String], default: null },
  title: { type: String, default: '商品评价' }
})

const router = useRouter()
const auth = useAuthStore()
const loading = ref(true)
const stats = ref(null)
const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 5
const scoreFilter = ref(null)
const likedIds = ref(new Set())
const likeBusy = ref(new Set())

const stars = [5, 4, 3, 2, 1]
const isShop = computed(() => Boolean(props.shopId))

function percent(star) {
  const value = Number(stats.value?.distribution?.[star] ?? stats.value?.distribution?.[String(star)] ?? 0)
  const total = Number(stats.value?.total ?? 0)
  if (!total) return 0
  return Math.max(0, Math.min(100, (value / total) * 100))
}

function countOf(star) {
  return Number(stats.value?.distribution?.[star] ?? stats.value?.distribution?.[String(star)] ?? 0)
}

async function load() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize }
    if (!isShop.value && scoreFilter.value) params.score = scoreFilter.value
    const pageRequest = isShop.value
      ? reviewApi.shopPage(props.shopId, params)
      : reviewApi.itemPage(props.itemType, props.itemId, params)
    const statsRequest = isShop.value
      ? reviewApi.shopStats(props.shopId)
      : reviewApi.itemStats(props.itemType, props.itemId)
    const [pageData, statsData] = await Promise.all([pageRequest, statsRequest])
    const page = normalizePage(pageData, pageSize)
    records.value = page.records
    total.value = page.total
    stats.value = statsData
    await loadLikes()
  } finally {
    loading.value = false
  }
}

async function loadLikes() {
  if (!auth.token) {
    likedIds.value = new Set()
    return
  }
  try {
    const ids = await likeApi.list(1)
    likedIds.value = new Set((ids || []).map(String))
  } catch {
    likedIds.value = new Set()
  }
}

async function toggleLike(review) {
  if (!auth.token) {
    ElMessage.info('登录后可以为有用评价点赞')
    router.push(`/login?redirect=${encodeURIComponent(window.location.pathname)}`)
    return
  }
  if (likeBusy.value.has(review.id)) return
  likeBusy.value = new Set(likeBusy.value).add(review.id)
  const liked = likedIds.value.has(review.id)
  try {
    if (liked) await likeApi.unlike(1, review.id)
    else await likeApi.like(1, review.id)
    const next = new Set(likedIds.value)
    if (liked) next.delete(review.id)
    else next.add(review.id)
    likedIds.value = next
    review.likeCount = Math.max(0, Number(review.likeCount || 0) + (liked ? -1 : 1))
  } catch {
    /* 失败提示由 axios 拦截器统一处理 */
  } finally {
    const busy = new Set(likeBusy.value)
    busy.delete(review.id)
    likeBusy.value = busy
  }
}

watch(
  () => [props.itemType, props.itemId, props.shopId],
  () => {
    pageNum.value = 1
    scoreFilter.value = null
    load()
  },
  { immediate: true }
)

function filterBy(star) {
  scoreFilter.value = scoreFilter.value === star ? null : star
  pageNum.value = 1
  load()
}

function changePage(page) {
  pageNum.value = page
  load()
}

const previewList = (review) => review.images || []
</script>

<template>
  <section class="review-panel">
    <div class="panel-head">
      <div>
        <h2>{{ title }}</h2>
      </div>
      <span v-if="!loading" class="total-chip">共 {{ total }} 条</span>
    </div>

    <div v-if="isShop" class="shop-intro">
      <p>门店评价来自真实完成的服务订单。服务完成后，可在「我的订单 → 已完成订单 → 订单详情」对本次服务发布评价。</p>
    </div>

    <div v-if="!loading" class="review-summary">
      <div class="score-board">
        <strong>{{ Number(stats?.avgScore ?? 0).toFixed(1) }}</strong>
        <el-rate :model-value="Number(stats?.avgScore ?? 0)" disabled allow-half />
        <span>综合评分 · {{ stats?.total ?? 0 }} 条评价</span>
      </div>
      <div class="distribution">
        <button
          v-for="star in stars"
          :key="star"
          type="button"
          class="dist-row"
          :class="{ active: scoreFilter === star, static: isShop }"
          @click="isShop ? undefined : filterBy(star)"
        >
          <span class="dist-label">{{ star }} 星</span>
          <span class="dist-track"><i :style="{ width: `${percent(star)}%` }"></i></span>
          <span class="dist-count">{{ countOf(star) }}</span>
        </button>
        <button v-if="scoreFilter && !isShop" type="button" class="clear-filter" @click="filterBy(scoreFilter)">
          清除 {{ scoreFilter }} 星筛选
        </button>
      </div>
    </div>

    <div v-if="loading" class="review-loading">
      <div v-for="n in 2" :key="n" class="skeleton-block review-skeleton"></div>
    </div>

    <el-empty
      v-else-if="!records.length"
      :description="scoreFilter ? '该星级还没有评价' : '还没有评价，等你来写第一条'"
      :image-size="88"
    />

    <div v-else class="review-list">
      <article v-for="review in records" :key="review.id" class="review-card">
        <div class="review-side">
          <span class="avatar">{{ String(review.userId || 'U').slice(-1) }}</span>
        </div>
        <div class="review-main">
          <div class="review-top">
            <div>
              <strong>{{ maskUser(review.userId) }}</strong>
              <span class="order-ref mono">订单 {{ review.orderNo }}</span>
            </div>
            <span class="review-date">{{ shortDate(review.createTime) }}</span>
          </div>
          <el-rate :model-value="review.score" disabled size="small" />
          <p class="review-content">{{ review.content || '用户未填写文字评价' }}</p>
          <div v-if="review.images?.length" class="review-images">
            <el-image
              v-for="(img, index) in review.images"
              :key="img"
              :src="img"
              :preview-src-list="previewList(review)"
              :initial-index="index"
              fit="cover"
              preview-teleported
              class="review-image"
            />
          </div>
          <div v-if="review.reply" class="merchant-reply">
            <span class="reply-label">商家回复</span>
            <p>{{ review.reply }}</p>
            <span class="reply-time">{{ shortDate(review.replyTime) }}</span>
          </div>
          <div class="review-foot">
            <button
              type="button"
              class="like-btn"
              :class="{ active: likedIds.has(review.id) }"
              @click="toggleLike(review)"
            >
              <el-icon><CaretTop /></el-icon>
              有用 {{ Number(review.likeCount || 0) }}
            </button>
          </div>
        </div>
      </article>
    </div>

    <div v-if="total > pageSize" class="review-pager">
      <el-pagination
        background
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        @current-change="changePage"
      />
    </div>
  </section>
</template>

<style scoped>
.review-panel {
  margin-top: 52px;
  padding-top: 34px;
  border-top: 1px solid var(--line);
}

.panel-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.panel-head h2 {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.total-chip {
  font-size: 12px;
  color: var(--muted);
  padding: 5px 10px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: #fff;
}

.shop-intro {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  border: 1px solid var(--line);
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.7;
}

.review-summary {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 35px;
  margin-top: 24px;
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: #fff;
}

.score-board {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding-right: 30px;
  border-right: 1px dashed var(--line);
}

.score-board strong {
  font-size: 40px;
  line-height: 1;
  font-weight: 600;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.score-board span {
  font-size: 11px;
  color: var(--muted);
}

.distribution {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.dist-row {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) 26px;
  align-items: center;
  gap: 10px;
  border: 0;
  background: transparent;
  padding: 2px 6px;
  border-radius: 6px;
  cursor: pointer;
  text-align: left;
}

.dist-row:hover,
.dist-row.active {
  background: var(--brand-soft);
}

.dist-row.static {
  cursor: default;
}

.dist-row.static:hover {
  background: transparent;
}

.dist-label,
.dist-count {
  font-size: 11px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

.dist-row.active .dist-label {
  color: var(--brand-deep);
  font-weight: 700;
}

.dist-track {
  height: 6px;
  border-radius: 3px;
  background: #f0eee8;
  overflow: hidden;
}

.dist-track i {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: var(--brand);
}

.clear-filter {
  align-self: flex-start;
  margin-top: 4px;
  border: 0;
  background: transparent;
  color: var(--brand-deep);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
}

.review-loading,
.review-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 18px;
}

.review-skeleton {
  height: 130px;
}

.review-card {
  display: flex;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: #fff;
}

.review-side {
  flex: none;
}

.avatar {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #f2f3f5;
  color: var(--ink-soft);
  font-size: 13px;
  font-weight: 600;
}

.review-main {
  min-width: 0;
  flex: 1;
}

.review-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.review-top strong {
  display: block;
  font-size: 13px;
}

.order-ref {
  display: block;
  margin-top: 2px;
  font-size: 9px;
  color: var(--muted);
}

.review-date {
  flex: none;
  font-size: 10px;
  color: var(--muted);
}

.review-content {
  margin: 9px 0 10px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--ink-soft);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.review-image {
  width: 76px;
  height: 76px;
  border-radius: 6px;
  overflow: hidden;
  cursor: zoom-in;
}

.merchant-reply {
  position: relative;
  margin-top: 12px;
  padding: 11px 13px;
  border-radius: 8px;
  background: var(--surface-soft);
  border: 1px solid var(--line);
}

.reply-label {
  display: inline-block;
  font-size: 10px;
  font-weight: 600;
  color: var(--ink);
}

.merchant-reply p {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--ink-soft);
}

.reply-time {
  display: block;
  margin-top: 5px;
  font-size: 9px;
  color: var(--muted);
}

.review-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--muted);
  font-size: 11px;
  padding: 4px 9px;
  border-radius: 4px;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}

.like-btn:hover {
  color: var(--brand-deep);
  border-color: var(--brand);
}

.like-btn.active {
  color: var(--brand-deep);
  border-color: var(--brand);
  background: var(--brand-soft);
  font-weight: 600;
}

.review-pager {
  display: flex;
  justify-content: center;
  margin-top: 22px;
}

@media (max-width: 720px) {
  .review-summary {
    grid-template-columns: 1fr;
    gap: 18px;
    padding: 18px;
  }

  .score-board {
    flex-direction: row;
    align-items: center;
    flex-wrap: wrap;
    border-right: 0;
    border-bottom: 1px dashed var(--line);
    padding: 0 0 14px;
  }

  .score-board strong {
    font-size: 38px;
  }

  .review-card {
    padding: 15px;
    gap: 10px;
  }

  .avatar {
    width: 34px;
    height: 34px;
  }
}
</style>
