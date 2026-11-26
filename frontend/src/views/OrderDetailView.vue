<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, Star } from '@element-plus/icons-vue'
import { cartApi, orderApi, reviewApi, shopApi } from '../api'
import { dateTime, money, orderStatus, resolveOrderItemImage } from '../utils/format'
import { createReconnectingSocket } from '../utils/ws'
import { useAuthStore } from '../stores/auth'
import ReviewDialog from '../components/ReviewDialog.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(true)
const order = ref(null)
const shopInfo = ref(null)
const loadError = ref(false)
const reviewedKeys = ref(new Set())
const reviewOpen = ref(false)
const activeItem = ref(null)
const acting = ref(false)
const rebuying = ref(false)
const wsState = ref('idle')

let socket = null

const steps = ['待支付', '已支付', '服务中', '已完成']

const activeStep = computed(() => {
  if (!order.value) return 0
  const map = { 0: 0, 10: 1, 20: 2, 30: 3 }
  return map[order.value.status] ?? 0
})

const isCancelled = computed(() => [40, 50, 60].includes(order.value?.status))

const isService = computed(() => order.value?.orderType === 2)

function keyOf(orderNo, itemId) {
  return `${orderNo}:${itemId}`
}

async function load(silent = false) {
  if (!silent) loading.value = true
  loadError.value = false
  try {
    const [orderData, reviewPage] = await Promise.all([
      orderApi.detail(route.params.orderNo),
      reviewApi.mine({ pageNum: 1, pageSize: 50 })
    ])
    order.value = orderData
    if (orderData?.orderType === 2 && orderData.shopId) {
      try {
        shopInfo.value = await shopApi.detail(orderData.shopId)
      } catch {
        shopInfo.value = null
      }
    } else {
      shopInfo.value = null
    }
    const keys = new Set()
    ;(reviewPage?.records || []).forEach((review) => {
      keys.add(keyOf(review.orderNo, review.itemId))
    })
    reviewedKeys.value = keys
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function connectSocket() {
  if (!auth.token || socket) return
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const url = `${protocol}://${window.location.host}/ws/order?token=${encodeURIComponent(auth.token)}`
  socket = createReconnectingSocket(url, {
    onState(state) {
      wsState.value = state
    },
    onMessage(data) {
      try {
        const message = JSON.parse(data)
        if (message.orderNo && message.orderNo !== order.value?.orderNo) return
        ElMessage.info(message.message || '订单状态已更新')
        load(true)
      } catch {
        /* 忽略非 JSON 消息 */
      }
    }
  })
}

onMounted(() => {
  load()
  connectSocket()
})

onBeforeUnmount(() => {
  if (socket) {
    socket.stop()
    socket = null
  }
})

function fallback(event) {
  event.target.src = '/images/product/placeholder.jpg'
}

function openReview(item) {
  activeItem.value = item
  reviewOpen.value = true
}

function onReviewed(result) {
  const next = new Set(reviewedKeys.value)
  next.add(keyOf(result.orderNo, result.itemId))
  reviewedKeys.value = next
}

async function cancelOrder() {
  try {
    await ElMessageBox.confirm('确定取消该订单吗？取消后库存会释放。', '取消订单', {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '再想想'
    })
  } catch {
    return
  }
  acting.value = true
  try {
    await orderApi.cancel(order.value.orderNo)
    ElMessage.success('订单已取消')
    load()
  } finally {
    acting.value = false
  }
}

async function completeOrder() {
  try {
    await ElMessageBox.confirm('确认服务已完成并收货吗？确认后可发布评价。', '确认完成', {
      confirmButtonText: '确认完成',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  acting.value = true
  try {
    await orderApi.complete(order.value.orderNo)
    ElMessage.success('订单已完成')
    load()
  } finally {
    acting.value = false
  }
}

async function demoDeliver() {
  acting.value = true
  try {
    await orderApi.demoDeliver(order.value.orderNo)
    ElMessage.success(isService.value ? '门店已接单，服务进行中' : '商家已发货，订单进入服务中')
    load()
  } finally {
    acting.value = false
  }
}

async function againBuy() {
  if (!order.value) return
  if (isService.value) {
    const first = order.value.items?.[0]
    router.push({
      path: order.value.shopId ? `/shops/${order.value.shopId}` : '/shops',
      query: first ? { serviceId: first.itemId, book: '1' } : { book: '1' }
    })
    return
  }
  const items = order.value.items || []
  if (!items.length) {
    router.push('/products')
    return
  }
  rebuying.value = true
  try {
    for (const item of items) {
      await cartApi.add({ productId: item.itemId, quantity: item.quantity })
    }
    ElMessage.success('已加入购物车，请重新结算')
    router.push('/cart')
  } catch {
    /* 拦截器已提示具体原因 */
  } finally {
    rebuying.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="container">
      <button class="back-button" type="button" @click="router.push('/orders')">
        <el-icon><ArrowLeft /></el-icon> 返回订单列表
      </button>

      <div v-if="loading" class="detail-loading">
        <div class="skeleton-block head-skeleton"></div>
        <div class="skeleton-block body-skeleton"></div>
      </div>

      <el-empty v-else-if="!order" description="订单不存在或无权查看" :image-size="120">
        <el-button type="primary" @click="router.push('/orders')">返回订单列表</el-button>
      </el-empty>

      <template v-else>
        <section class="order-hero">
          <div class="hero-top">
            <div>
              <h1>{{ order.statusDesc || orderStatus(order.status).text }}</h1>
              <p class="order-no mono">{{ order.orderNo }}</p>
            </div>
            <div class="hero-side">
              <el-tag :type="orderStatus(order.status).tone" effect="plain" size="small">
                {{ order.statusDesc || orderStatus(order.status).text }}
              </el-tag>
              <el-popover
                v-if="order.status === 10"
                placement="bottom-end"
                :width="280"
                trigger="click"
                popper-class="demo-tools-popper"
              >
                <template #reference>
                  <button class="demo-tools-btn" type="button">
                    订单操作
                  </button>
                </template>
                <div class="demo-tools">
                  <p class="demo-title">订单操作</p>
                  <p class="demo-desc">
                    {{ isService ? '门店确认接单后，订单将进入服务中。' : '商家确认发货后，订单将进入服务中。' }}
                  </p>
                  <el-button type="primary" size="small" :loading="acting" @click="demoDeliver">
                    {{ isService ? '确认接单' : '确认发货' }}
                  </el-button>
                </div>
              </el-popover>
            </div>
          </div>

          <div v-if="!isCancelled" class="steps">
            <el-steps :active="activeStep" finish-status="success" process-status="process" align-center>
              <el-step v-for="step in steps" :key="step" :title="step" />
            </el-steps>
          </div>
          <div v-else class="cancel-note">
            订单已取消<template v-if="order.cancelReason">：{{ order.cancelReason }}</template>
            <span v-if="order.cancelTime"> · {{ dateTime(order.cancelTime) }}</span>
          </div>

          <div class="hero-actions">
            <el-button
              v-if="order.status === 0"
              type="primary"
              size="large"
              @click="router.push(`/pay/${order.orderNo}`)"
            >
              立即支付 ¥{{ money(order.payAmount) }}
            </el-button>
            <el-button v-if="order.status === 0" size="large" :loading="acting" @click="cancelOrder">
              取消订单
            </el-button>
            <el-button
              v-if="order.status === 20"
              type="primary"
              size="large"
              :loading="acting"
              @click="completeOrder"
            >
              确认完成
            </el-button>
            <span v-if="order.status === 10" class="status-hint">
              {{ isService ? '已支付，门店将按预约时间提供服务' : '已支付，等待门店接单或发货' }}
            </span>
            <span v-if="order.status === 30" class="status-hint">
              {{ isService ? '服务已完成，可对本次服务发布一次评价' : '交易已完成，可对每件商品发布一次评价' }}
            </span>
            <el-button
              v-if="[30, 40, 50, 60].includes(order.status)"
              size="large"
              :loading="rebuying"
              @click="againBuy"
            >
              {{ isService ? '再次预约' : '再次购买' }}
            </el-button>
          </div>
        </section>

        <div class="detail-grid">
          <section class="surface items-panel">
            <div class="panel-title">
              <h2>{{ isService ? '服务明细' : '商品明细' }}</h2>
              <span class="chip">{{ order.orderTypeDesc }}</span>
            </div>

            <div class="item-list">
              <div v-for="item in order.items" :key="item.itemId" class="item-row">
                <router-link v-if="order.orderType === 1" :to="`/products/${item.itemId}`" class="item-imglink">
                  <img :src="resolveOrderItemImage(order, item)" :alt="item.itemName" @error="fallback" />
                </router-link>
                <img v-else :src="resolveOrderItemImage(order, item)" :alt="item.itemName" @error="fallback" />
                <div class="item-info">
                  <router-link v-if="order.orderType === 1" :to="`/products/${item.itemId}`" class="item-name">
                    {{ item.itemName }}
                  </router-link>
                  <strong v-else>{{ item.itemName }}</strong>
                  <span>¥{{ money(item.price) }} × {{ item.quantity }}</span>
                </div>
                <div class="item-total">¥{{ money(item.totalAmount) }}</div>
                <div class="item-action">
                  <template v-if="order.status === 30">
                    <el-tag
                      v-if="reviewedKeys.has(keyOf(order.orderNo, item.itemId))"
                      type="success"
                      effect="plain"
                     
                    >
                      已评价
                    </el-tag>
                    <el-button
                      v-else
                      type="primary"
                      plain
                     
                      size="small"
                      :icon="Star"
                      @click="openReview(item)"
                    >
                      评价
                    </el-button>
                  </template>
                </div>
              </div>
            </div>

            <div class="amount-summary">
              <div><span>{{ isService ? '服务金额' : '商品总额' }}</span><b>¥{{ money(order.totalAmount) }}</b></div>
              <div><span>应付 / 实付</span><b class="pay-amount">¥{{ money(order.payAmount) }}</b></div>
            </div>
          </section>

          <aside class="side-column">
            <section class="surface info-panel">
              <h2>订单信息</h2>
              <dl>
                <div><dt>订单类型</dt><dd>{{ order.orderTypeDesc }}</dd></div>
                <template v-if="isService">
                  <div>
                    <dt>服务门店</dt>
                    <dd>
                      <router-link v-if="shopInfo" :to="`/shops/${shopInfo.id}`" class="shop-link">
                        {{ shopInfo.shopName }}
                      </router-link>
                      <span v-else>门店 {{ order.shopId }}</span>
                    </dd>
                  </div>
                  <div><dt>预约时间</dt><dd>{{ dateTime(order.appointmentTime) }}</dd></div>
                  <div><dt>预约联系人</dt><dd>{{ order.appointmentContact || '—' }}</dd></div>
                  <div><dt>联系电话</dt><dd>{{ order.appointmentPhone || '—' }}</dd></div>
                </template>
                <div><dt>下单时间</dt><dd>{{ dateTime(order.createTime) }}</dd></div>
                <div><dt>支付时间</dt><dd>{{ dateTime(order.payTime) }}</dd></div>
                <div><dt>完成时间</dt><dd>{{ dateTime(order.finishTime) }}</dd></div>
                <div v-if="order.remark"><dt>订单备注</dt><dd>{{ order.remark }}</dd></div>
              </dl>
            </section>

            <section class="surface note-panel">
              <div class="note-icon"><el-icon><Check /></el-icon></div>
              <div>
                <strong>评价规则</strong>
                <p v-if="isService">
                  仅已完成的服务订单可评价，且每项服务只能评价一次。评价会更新门店的口碑评分。
                </p>
                <p v-else>
                  仅已完成订单可评价，且每件商品只能评价一次。评价会更新商品的评分与星级分布。
                </p>
              </div>
            </section>
          </aside>
        </div>

        <ReviewDialog
          v-model="reviewOpen"
          :order="order"
          :item="activeItem"
          @submitted="onReviewed"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
.back-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  padding: 4px 0;
}

.back-button:hover {
  color: var(--brand);
}

.order-hero {
  margin-top: 16px;
  padding: 24px 28px;
  border-radius: var(--radius);
  background: #fff;
  border: 1px solid var(--line);
  color: var(--ink);
}

.hero-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.hero-top h1 {
  font-size: 24px;
  line-height: 1.35;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.order-no {
  margin-top: 6px;
  font-size: 10px;
  color: var(--muted);
}

.hero-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.demo-tools-btn {
  padding: 0;
  border: 0;
  background: transparent;
  color: #b4bac3;
  font-size: 10px;
  cursor: pointer;
  transition: color 0.15s ease;
}

.demo-tools-btn:hover {
  color: var(--ink-soft);
}

.steps {
  margin-top: 20px;
  padding: 16px 8px;
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  border: 1px solid var(--line);
}

.steps :deep(.el-step__title) {
  font-size: 12px;
  color: var(--muted);
}

.steps :deep(.el-step__title.is-finish),
.steps :deep(.el-step__title.is-process) {
  color: var(--ink);
}

.steps :deep(.el-step__head.is-finish .el-step__line) {
  background: var(--brand);
}

.steps :deep(.el-step__head.is-finish .el-step__icon) {
  background: var(--brand);
  border-color: var(--brand);
}

.cancel-note {
  margin-top: 18px;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  border: 1px solid var(--line);
  font-size: 12px;
  color: var(--ink-soft);
}

.hero-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.status-hint {
  font-size: 12px;
  color: var(--muted);
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.55fr);
  gap: 16px;
  margin-top: 18px;
  align-items: start;
}

.items-panel {
  padding: 24px;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title h2,
.info-panel h2 {
  font-size: 18px;
  letter-spacing: -0.02em;
}

.item-list {
  margin-top: 10px;
}

.item-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 0;
  border-bottom: 1px dashed var(--line);
}

.item-row img,
.item-imglink img {
  width: 66px;
  height: 66px;
  border-radius: 6px;
  object-fit: cover;
  background: #eee;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name,
.item-info strong {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
}

.item-name:hover {
  color: var(--brand);
}

.item-info span {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: var(--muted);
}

.item-total {
  flex: none;
  font-size: 14px;
  font-weight: 700;
}

.item-action {
  flex: none;
  width: 74px;
  display: flex;
  justify-content: flex-end;
}

.amount-summary {
  margin-top: 16px;
  padding-top: 14px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.amount-summary div {
  display: flex;
  align-items: baseline;
  gap: 20px;
  font-size: 12px;
  color: var(--muted);
}

.amount-summary b {
  min-width: 90px;
  text-align: right;
  color: var(--ink);
}

.amount-summary .pay-amount {
  font-size: 22px;
  color: var(--price);
  letter-spacing: -0.02em;
}

.side-column {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.info-panel {
  padding: 24px;
}

.info-panel dl {
  margin: 16px 0 0;
}

.info-panel dl div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px dashed var(--line);
  font-size: 11px;
}

.info-panel dl div:last-child {
  border-bottom: 0;
}

.info-panel dt {
  color: var(--muted);
  flex: none;
}

.info-panel dd {
  margin: 0;
  text-align: right;
  color: var(--ink-soft);
  line-height: 1.5;
}

.info-panel .shop-link {
  font-weight: 600;
  color: var(--brand-deep);
}

.info-panel .shop-link:hover {
  text-decoration: underline;
}

.note-panel {
  display: flex;
  gap: 12px;
  padding: 18px;
  background: #f3f7f4;
  border-color: #e2ebe5;
}

.note-icon {
  flex: none;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #e3efe7;
  color: var(--teal);
  font-size: 12px;
}

.note-panel strong {
  font-size: 12px;
  color: var(--ink);
}

.note-panel p {
  margin-top: 5px;
  font-size: 11px;
  line-height: 1.7;
  color: var(--muted);
}

.head-skeleton {
  height: 260px;
}

.body-skeleton {
  height: 300px;
  margin-top: 16px;
}

@media (max-width: 940px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .side-column {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 700px) {
  .order-hero {
    padding: 22px 18px;
  }

  .hero-top {
    flex-direction: column;
    gap: 10px;
  }

  .steps {
    padding: 16px 0;
  }

  .items-panel {
    padding: 16px;
  }

  .item-row {
    flex-wrap: wrap;
  }

  .item-action {
    width: auto;
  }

  .side-column {
    grid-template-columns: 1fr;
  }

  .hero-side {
    align-items: flex-start;
  }
}
</style>

<style>
.demo-tools-popper {
  border-radius: 6px;
}

.demo-tools-popper .demo-tools {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.demo-tools-popper .demo-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--ink);
}

.demo-tools-popper .demo-desc {
  font-size: 11px;
  line-height: 1.7;
  color: var(--muted);
}

</style>
