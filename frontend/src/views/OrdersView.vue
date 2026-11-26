<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cartApi, orderApi } from '../api'
import { money, normalizePage, orderStatus, resolveOrderItemImage, shortDate } from '../utils/format'
import PriceText from '../components/PriceText.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 8
const activeStatus = ref(null)
const acting = ref('')
const rebuying = ref('')

const tabs = [
  { label: '全部', value: null },
  { label: '待支付', value: 0 },
  { label: '已支付', value: 10 },
  { label: '服务中', value: 20 },
  { label: '已完成', value: 30 },
  { label: '已取消', value: 40 }
]

function applyQuery() {
  activeStatus.value = route.query.status !== undefined ? Number(route.query.status) : null
  pageNum.value = route.query.pageNum ? Math.max(1, Number(route.query.pageNum)) : 1
  load()
}

watch(() => route.query, applyQuery, { immediate: true })

async function load() {
  loading.value = true
  try {
    const data = await orderApi.page({
      pageNum: pageNum.value,
      pageSize,
      status: activeStatus.value ?? undefined
    })
    const page = normalizePage(data, pageSize)
    records.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function switchStatus(value) {
  router.push({
    path: '/orders',
    query: value === null ? {} : { status: value }
  })
}

function changePage(page) {
  router.push({
    path: '/orders',
    query: {
      ...(activeStatus.value !== null ? { status: activeStatus.value } : {}),
      pageNum: page
    }
  })
}

async function cancel(order) {
  try {
    await ElMessageBox.confirm(
      `确定取消订单 ${order.orderNo} 吗？取消后库存会释放。`,
      '取消订单',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '再想想' }
    )
  } catch {
    return
  }
  acting.value = order.orderNo
  try {
    await orderApi.cancel(order.orderNo)
    ElMessage.success('订单已取消')
    load()
  } finally {
    acting.value = ''
  }
}

async function complete(order) {
  try {
    await ElMessageBox.confirm('确认服务已完成并收货吗？确认后可发布评价。', '确认完成', {
      confirmButtonText: '确认完成',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  acting.value = order.orderNo
  try {
    await orderApi.complete(order.orderNo)
    ElMessage.success('订单已完成，现在可以评价了')
    load()
  } finally {
    acting.value = ''
  }
}

function fallback(event) {
  event.target.src = '/images/product/placeholder.jpg'
}

async function againBuy(order) {
  if (order.orderType === 2) {
    const first = order.items?.[0]
    router.push({
      path: order.shopId ? `/shops/${order.shopId}` : '/shops',
      query: first ? { serviceId: first.itemId, book: '1' } : { book: '1' }
    })
    return
  }
  const items = order.items || []
  if (!items.length) {
    router.push('/products')
    return
  }
  rebuying.value = order.orderNo
  try {
    for (const item of items) {
      await cartApi.add({ productId: item.itemId, quantity: item.quantity })
    }
    ElMessage.success('已加入购物车，请重新结算')
    router.push('/cart')
  } catch {
    /* 拦截器已提示具体原因 */
  } finally {
    rebuying.value = ''
  }
}
</script>

<template>
  <div class="page">
    <div class="container">
      <header class="page-head">
        <div>
          <h1>我的订单</h1>
          <p>下单、支付、服务与评价，每一步状态都在这里。</p>
        </div>
        <router-link to="/products" class="section-link">继续逛逛 →</router-link>
      </header>

      <div class="status-tabs">
        <button
          v-for="tab in tabs"
          :key="String(tab.value)"
          type="button"
          :class="{ active: activeStatus === tab.value }"
          @click="switchStatus(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-if="loading" class="order-list">
        <div v-for="n in 3" :key="n" class="skeleton-block order-skeleton"></div>
      </div>

      <el-empty v-else-if="!records.length" description="这里还没有订单" :image-size="120">
        <div class="empty-actions">
          <el-button type="primary" @click="router.push('/products')">去挑点养车好物</el-button>
          <el-button @click="router.push('/shops')">预约到店服务</el-button>
        </div>
      </el-empty>

      <div v-else class="order-list">
        <article v-for="order in records" :key="order.orderNo" class="order-card">
          <header class="order-head">
            <div class="head-left">
              <span class="order-no mono">{{ order.orderNo }}</span>
              <el-tag size="small" effect="plain">{{ order.orderTypeDesc }}</el-tag>
              <span class="order-time">{{ shortDate(order.createTime) }}</span>
            </div>
            <el-tag :type="orderStatus(order.status).tone" effect="plain">
              {{ order.statusDesc || orderStatus(order.status).text }}
            </el-tag>
          </header>

          <div class="order-body">
            <div class="items">
              <div class="items-head">
                <span class="col-product">商品</span>
                <span class="col-unit">单价 × 数量</span>
                <span class="col-subtotal">小计</span>
              </div>
              <div v-for="item in order.items" :key="`${order.orderNo}-${item.itemId}`" class="order-item">
                <img :src="resolveOrderItemImage(order, item)" :alt="item.itemName" @error="fallback" />
                <strong>{{ item.itemName }}</strong>
                <span class="unit num">¥{{ money(item.price) }} × {{ item.quantity }}</span>
                <b class="num">¥{{ money(item.totalAmount) }}</b>
              </div>
            </div>

            <div class="order-side">
              <div class="amount">
                <span>实付金额</span>
                <PriceText :value="order.payAmount" class="amount-value" />
              </div>
              <div class="actions">
                <el-button size="small" @click="router.push(`/orders/${order.orderNo}`)">订单详情</el-button>
                <el-button
                  v-if="order.status === 0"
                  type="primary"
                  size="small"
                  @click="router.push(`/pay/${order.orderNo}`)"
                >
                  去支付
                </el-button>
                <el-button
                  v-if="order.status === 0"
                  size="small"
                  :loading="acting === order.orderNo"
                  @click="cancel(order)"
                >
                  取消
                </el-button>
                <el-button
                  v-if="order.status === 20"
                  type="primary"
                  size="small"
                  :loading="acting === order.orderNo"
                  @click="complete(order)"
                >
                  确认完成
                </el-button>
                <el-button
                  v-if="order.status === 30"
                  type="primary"
                  size="small"
                  @click="router.push(`/orders/${order.orderNo}`)"
                >
                  去评价
                </el-button>
                <el-button
                  v-if="[30, 40, 50, 60].includes(order.status)"
                  size="small"
                  :loading="rebuying === order.orderNo"
                  @click="againBuy(order)"
                >
                  {{ order.orderType === 2 ? '再次预约' : '再次购买' }}
                </el-button>
              </div>
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

.status-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0;
}

.status-tabs button {
  border: 1px solid var(--line);
  background: #fff;
  padding: 7px 15px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--ink-soft);
  cursor: pointer;
  transition: all 0.2s ease;
}

.status-tabs button:hover {
  border-color: var(--line-strong);
}

.status-tabs button.active {
  background: var(--brand-soft);
  border-color: #f8d3c8;
  color: var(--brand-deep);
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.empty-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.order-skeleton {
  height: 160px;
}

.order-card {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: #fff;
  overflow: hidden;
  transition: box-shadow 0.2s ease;
}

.order-card:hover {
  box-shadow: var(--shadow-sm);
}

.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  background: var(--surface-soft);
  border-bottom: 1px solid var(--line);
}

.head-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.order-no {
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-soft);
}

.order-time {
  font-size: 10px;
  color: var(--muted);
}

.order-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
}

.items {
  padding: 4px 16px 6px;
}

.items-head,
.order-item {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) 110px 90px;
  align-items: center;
  gap: 12px;
}

.items-head {
  padding: 7px 0;
  border-bottom: 1px solid var(--line);
  font-size: 10px;
  color: var(--muted);
}

.items-head .col-product {
  grid-column: 1 / 3;
}

.items-head .col-unit {
  text-align: left;
}

.items-head .col-subtotal {
  text-align: right;
}

.order-item {
  padding: 8px 0;
  border-bottom: 1px dashed var(--line);
}

.order-item:last-child {
  border-bottom: 0;
}

.order-item img {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  object-fit: cover;
  background: #eee;
}

.order-item strong {
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.order-item .unit {
  font-size: 11px;
  color: var(--muted);
}

.order-item b {
  text-align: right;
  font-size: 13px;
}

.order-side {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-end;
  gap: 12px;
  padding: 14px 16px;
  border-left: 1px solid var(--line);
}

.amount {
  text-align: right;
}

.amount > span:not(.price-text) {
  display: block;
  font-size: 10px;
  color: var(--muted);
}

.amount-value {
  margin-top: 2px;
  font-size: 21px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

@media (max-width: 760px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .order-body {
    grid-template-columns: 1fr;
  }

  .order-side {
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    border-left: 0;
    border-top: 1px solid var(--line);
  }
}

@media (max-width: 480px) {
  .order-side {
    align-items: flex-end;
    flex-direction: column;
  }

  .items-head,
  .order-item {
    grid-template-columns: 40px minmax(0, 1fr) 84px;
    gap: 8px;
  }

  .items-head .col-unit {
    display: none;
  }

  .items-head .col-subtotal {
    grid-column: 3;
  }

  .order-item img {
    width: 40px;
    height: 40px;
    grid-row: 1 / 3;
  }

  .order-item strong {
    grid-column: 2;
    grid-row: 1;
  }

  .order-item .unit {
    grid-column: 2;
    grid-row: 2;
  }

  .order-item b {
    grid-column: 3;
    grid-row: 1 / 3;
  }
}
</style>