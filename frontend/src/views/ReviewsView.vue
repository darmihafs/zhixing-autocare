<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Star } from '@element-plus/icons-vue'
import { reviewApi } from '../api'
import { maskUser, normalizePage, shortDate } from '../utils/format'

const router = useRouter()
const loading = ref(true)
const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 6

async function load() {
  loading.value = true
  try {
    const data = await reviewApi.mine({ pageNum: pageNum.value, pageSize })
    const page = normalizePage(data, pageSize)
    records.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

onMounted(load)

function changePage(page) {
  pageNum.value = page
  load()
}

function itemPath(review) {
  if (review.itemType === 1) return `/products/${review.itemId}`
  if (review.shopId) return `/shops/${review.shopId}#shop-reviews`
  return null
}

function itemLabel(review) {
  return review.itemType === 1 ? '查看商品' : '查看门店评价'
}
</script>

<template>
  <div class="page">
    <div class="container">
      <header class="page-head">
        <div>
          <h1>我的评价</h1>
          <p>完成订单后可以评价，晒图和使用感受都会展示在商品页。</p>
        </div>
        <span class="count-chip">共 {{ total }} 条</span>
      </header>

      <div v-if="loading" class="review-list">
        <div v-for="n in 3" :key="n" class="skeleton-block review-skeleton"></div>
      </div>

      <el-empty v-else-if="!records.length" description="还没有发布过评价" :image-size="120">
        <el-button type="primary" @click="router.push('/orders')">去已完成订单看看</el-button>
      </el-empty>

      <div v-else class="review-list">
        <article v-for="review in records" :key="review.id" class="review-card">
          <header class="review-head">
            <div class="head-left">
              <span class="avatar">{{ String(review.userId || 'U').slice(-1) }}</span>
              <div>
                <strong>{{ maskUser(review.userId) }}</strong>
                <span class="order-line">
                  订单 {{ review.orderNo }} · {{ shortDate(review.createTime) }}
                </span>
              </div>
            </div>
            <el-tag effect="plain" type="warning">
              <el-icon><Star /></el-icon>
              {{ review.score }} 星
            </el-tag>
          </header>

          <div class="review-body">
            <router-link v-if="itemPath(review)" :to="itemPath(review)" class="item-link">
              {{ itemLabel(review) }} →
            </router-link>
            <span v-else class="item-link muted">服务项目 #{{ review.itemId }}</span>

            <p class="content">{{ review.content || '用户未填写文字评价' }}</p>

            <div v-if="review.images?.length" class="images">
              <el-image
                v-for="(img, index) in review.images"
                :key="img"
                :src="img"
                :preview-src-list="review.images"
                :initial-index="index"
                fit="cover"
                preview-teleported
                class="image"
              />
            </div>

            <div v-if="review.reply" class="reply">
              <span>商家回复</span>
              <p>{{ review.reply }}</p>
            </div>
          </div>
        </article>
      </div>

      <div v-if="total > pageSize" class="pager">
        <el-pagination
          background
          layout="prev, pager, next"
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
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
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

.count-chip {
  flex: none;
  padding: 5px 11px;
  border-radius: 4px;
  border: 1px solid var(--line);
  background: #fff;
  font-size: 12px;
  color: var(--muted);
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 22px;
}

.review-skeleton {
  height: 190px;
}

.review-card {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: #fff;
  overflow: hidden;
}

.review-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  background: var(--surface-soft);
  border-bottom: 1px solid var(--line);
}

.head-left {
  display: flex;
  align-items: center;
  gap: 11px;
}

.avatar {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.head-left strong {
  display: block;
  font-size: 12px;
}

.order-line {
  display: block;
  margin-top: 3px;
  font-size: 10px;
  color: var(--muted);
}

.review-body {
  padding: 14px 16px 16px;
}

.item-link {
  font-size: 12px;
  font-weight: 600;
  color: var(--brand-deep);
}

.item-link:hover {
  text-decoration: underline;
}

.item-link.muted {
  color: var(--muted);
  font-weight: 500;
}

.content {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.85;
  color: var(--ink-soft);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.image {
  width: 96px;
  height: 96px;
  border-radius: 6px;
  overflow: hidden;
  cursor: zoom-in;
}

.reply {
  margin-top: 14px;
  padding: 11px 13px;
  border-radius: 6px;
  background: var(--surface-soft);
  border: 1px solid var(--line);
}

.reply span {
  font-size: 10px;
  font-weight: 600;
  color: var(--ink);
}

.reply p {
  margin-top: 5px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-soft);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

@media (max-width: 600px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .review-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .review-body {
    padding: 15px;
  }
}
</style>
