<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, Clock, Warning } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import { cartApi, orderApi, payApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { dateTime, money, orderStatus, resolveImage, shortDate } from '../utils/format'
import { createReconnectingSocket } from '../utils/ws'
import PriceText from '../components/PriceText.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)
const order = ref(null)
const pay = ref(null)
const creating = ref(false)
const mocking = ref(false)
const rebuying = ref(false)
const wsState = ref('idle')
const wsMessage = ref('')
const errorText = ref('')
const expired = ref(false)
const qrDataUrl = ref('')
const qrDialog = ref(false)
const remaining = ref(null)

let socket = null
let pollTimer = null
let pollCount = 0
let countdownTimer = null
let serverOffset = 0
let lastQrPayload = ''

const settled = computed(() => pay.value?.status === 1 || pay.value?.status === 2)
const paid = computed(() => pay.value?.status === 1 || order.value?.status === 10)
const failed = computed(() => pay.value?.status === 2)
const canPay = computed(() => order.value?.status === 0 && !paid.value && !expired.value)

const countdownText = computed(() => {
  if (remaining.value === null) return '--:--'
  const minutes = Math.floor(remaining.value / 60)
  const seconds = remaining.value % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})

function fallback(event) {
  event.target.src = '/images/product/placeholder.jpg'
}

function applyPay(next) {
  if (!next) return
  const settledAlready = pay.value?.status === 1 || pay.value?.status === 2
  pay.value = { ...pay.value, ...next }
  if (next.status === 1) {
    stopPolling()
    stopCountdown()
    closeSocket()
    if (!settledAlready) {
      wsMessage.value = '支付成功，订单状态已同步'
      ElMessage.success('支付成功')
    }
    qrDialog.value = false
    refreshOrder()
  }
  if (next.status === 2) {
    stopPolling()
    stopCountdown()
    closeSocket()
    if (!settledAlready) wsMessage.value = '支付未完成，可重试'
  }
}

async function refreshOrder() {
  try {
    order.value = await orderApi.detail(route.params.orderNo)
  } catch {
    /* 保持当前页面状态 */
  }
}

function connectSocket() {
  if (!auth.token || socket) return
  wsState.value = 'connecting'
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const url = `${protocol}://${window.location.host}/ws/pay?token=${encodeURIComponent(auth.token)}`
  socket = createReconnectingSocket(url, {
    onState(state) {
      if (wsState.value !== 'done') wsState.value = state
    },
    onMessage(data) {
      try {
        const message = JSON.parse(data)
        if (message.orderNo !== order.value?.orderNo) return
        wsState.value = 'done'
        wsMessage.value = message.type === 'PAY_SUCCESS' ? '支付成功，订单状态已同步' : '支付未完成，可重试'
        applyPay({
          status: message.status,
          outTradeNo: message.outTradeNo,
          payTime: message.payTime,
          statusDesc: message.status === 1 ? '支付成功' : '支付失败'
        })
        closeSocket()
      } catch {
        /* 忽略非 JSON 消息 */
      }
    }
  })
}

function closeSocket() {
  if (socket) {
    socket.stop()
    socket = null
  }
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPolling() {
  stopPolling()
  pollCount = 0
  pollTimer = setInterval(async () => {
    if (!pay.value?.outTradeNo || settled.value || pollCount >= 20) {
      stopPolling()
      return
    }
    pollCount += 1
    try {
      const detail = await payApi.detail(pay.value.outTradeNo)
      if (detail.status !== 0) applyPay(detail)
    } catch {
      stopPolling()
    }
  }, 2000)
}

function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

function markExpired() {
  if (expired.value) return
  expired.value = true
  qrDialog.value = false
  stopPolling()
  stopCountdown()
  closeSocket()
  refreshOrder()
}

function updateCountdown() {
  if (!order.value?.payDeadline) {
    remaining.value = null
    return
  }
  const deadline = new Date(order.value.payDeadline).getTime()
  if (Number.isNaN(deadline)) {
    remaining.value = null
    return
  }
  const left = Math.floor((deadline - (Date.now() - serverOffset)) / 1000)
  remaining.value = Math.max(0, left)
  if (left <= 0 && canPay.value) markExpired()
}

function startCountdown() {
  stopCountdown()
  updateCountdown()
  countdownTimer = setInterval(updateCountdown, 1000)
}

async function renderQr() {
  if (!order.value?.orderNo || !pay.value?.outTradeNo) return
  const payload = `hongcheng-demo://pay?orderNo=${order.value.orderNo}&outTradeNo=${pay.value.outTradeNo}&amount=${money(order.value.payAmount)}`
  if (payload === lastQrPayload) return
  lastQrPayload = payload
  try {
    qrDataUrl.value = await QRCode.toDataURL(payload, {
      width: 360,
      margin: 1,
      color: { dark: '#101828', light: '#ffffff' }
    })
  } catch {
    qrDataUrl.value = ''
  }
}

function openQr() {
  if (!pay.value) {
    ElMessage.info('支付单正在创建，请稍候')
    return
  }
  qrDialog.value = true
}

async function init() {
  loading.value = true
  errorText.value = ''
  try {
    order.value = await orderApi.detail(route.params.orderNo)
    serverOffset = order.value.serverTime
      ? Date.now() - new Date(order.value.serverTime).getTime()
      : 0
    if (order.value.status === 10) {
      pay.value = { status: 1, statusDesc: '支付成功', amount: order.value.payAmount }
      wsState.value = 'done'
      return
    }
    if (order.value.status !== 0) return

    startCountdown()
    if (expired.value) return

    connectSocket()
    creating.value = true
    pay.value = await payApi.create(order.value.orderNo)
    await renderQr()
    startPolling()
  } catch (error) {
    errorText.value = error?.message || '支付单创建失败'
  } finally {
    creating.value = false
    loading.value = false
  }
}

onMounted(init)

onBeforeUnmount(() => {
  stopPolling()
  stopCountdown()
  closeSocket()
})

async function mockPay() {
  if (!pay.value?.outTradeNo) return
  mocking.value = true
  try {
    const result = await payApi.mock(pay.value.outTradeNo)
    applyPay(result)
  } finally {
    mocking.value = false
  }
}

async function mockPayFromDialog() {
  await mockPay()
  if (paid.value) qrDialog.value = false
}

async function againBuy() {
  if (!order.value) return
  if (order.value.orderType === 2) {
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
    <div class="container narrow">
      <button class="back-button" type="button" @click="router.push(`/orders/${route.params.orderNo}`)">
        <el-icon><ArrowLeft /></el-icon> 返回订单详情
      </button>

      <div v-if="loading" class="loading">
        <div class="skeleton-block pay-skeleton"></div>
      </div>

      <el-empty v-else-if="!order" description="订单不存在或无权查看" :image-size="120">
        <el-button type="primary" @click="router.push('/orders')">返回订单列表</el-button>
      </el-empty>

      <div v-else class="pay-layout">
        <section class="order-summary surface">
          <div class="summary-head">
            <div>
              <h2>订单摘要</h2>
            </div>
            <el-tag :type="orderStatus(order.status).tone" effect="plain" size="small">
              {{ order.statusDesc || orderStatus(order.status).text }}
            </el-tag>
          </div>

          <div class="summary-items">
            <div v-for="item in order.items" :key="item.itemId" class="summary-item">
              <img :src="resolveImage(item.itemImg)" :alt="item.itemName" @error="fallback" />
              <div>
                <strong>{{ item.itemName }}</strong>
                <span>¥{{ money(item.price) }} × {{ item.quantity }}</span>
              </div>
              <b>¥{{ money(item.totalAmount) }}</b>
            </div>
          </div>

          <div class="summary-row">
            <span>订单号</span>
            <b class="mono">{{ order.orderNo }}</b>
          </div>
          <div class="summary-row">
            <span>下单时间</span>
            <b>{{ shortDate(order.createTime) }}</b>
          </div>
          <div class="summary-row">
            <span>订单备注</span>
            <b>{{ order.remark || '无' }}</b>
          </div>

          <div class="summary-total">
            <span>应付金额</span>
            <PriceText :value="order.payAmount" class="summary-amount" />
          </div>
        </section>

        <section class="pay-card">
          <template v-if="paid">
            <div class="result-box success">
              <div class="result-icon"><el-icon><Check /></el-icon></div>
              <h2>支付成功</h2>
              <p>订单已同步为已支付状态，商家将尽快处理。</p>
              <PriceText :value="order.payAmount" class="result-amount" />
              <div class="result-meta">
                <span v-if="pay?.payTime">支付时间 {{ dateTime(pay.payTime) }}</span>
                <span v-if="pay?.outTradeNo" class="mono">{{ pay.outTradeNo }}</span>
              </div>
              <div class="result-actions">
                <el-button type="primary" size="large" @click="router.push(`/orders/${order.orderNo}`)">
                  查看订单
                </el-button>
                <el-button size="large" @click="router.push('/products')">继续逛逛</el-button>
              </div>
            </div>
          </template>

          <template v-else-if="expired">
            <div class="result-box expired">
              <div class="result-icon warning-icon"><el-icon><Warning /></el-icon></div>
              <h2>支付已超时</h2>
              <p>已超过支付时限，系统正在关闭该订单。可以重新下单继续购买。</p>
              <div class="result-actions">
                <el-button type="primary" size="large" :loading="rebuying" @click="againBuy">
                  重新下单
                </el-button>
                <el-button size="large" @click="router.push(`/orders/${order.orderNo}`)">查看订单</el-button>
              </div>
            </div>
          </template>

          <template v-else-if="failed">
            <div class="result-box failed">
              <div class="result-icon"><el-icon><Warning /></el-icon></div>
              <h2>支付未完成</h2>
              <p>{{ wsMessage || '支付通道返回失败，可重新发起支付。' }}</p>
              <div class="result-actions">
                <el-button type="primary" size="large" :loading="mocking" @click="mockPay">
                  重试支付
                </el-button>
                <el-button size="large" @click="router.push(`/orders/${order.orderNo}`)">查看订单</el-button>
              </div>
            </div>
          </template>

          <template v-else-if="order.status !== 0">
            <div class="result-box">
              <div class="result-icon muted-icon">i</div>
              <h2>{{ order.status === 40 ? '订单已关闭' : '当前不可支付' }}</h2>
              <p>订单状态为「{{ order.statusDesc || orderStatus(order.status).text }}」，无需再次支付。</p>
              <div class="result-actions">
                <el-button
                  v-if="order.status === 40 || order.status === 60"
                  type="primary"
                  size="large"
                  :loading="rebuying"
                  @click="againBuy"
                >
                  重新下单
                </el-button>
                <el-button type="primary" size="large" @click="router.push(`/orders/${order.orderNo}`)">
                  查看订单
                </el-button>
              </div>
            </div>
          </template>

          <template v-else>
            <div class="pay-head">
              <h2>收银台</h2>
              <span class="pay-env">演示环境</span>
            </div>

            <div class="pay-amount">
              <span>待支付金额</span>
              <PriceText :value="order.payAmount" class="pay-amount-value" />
              <small>订单号 <i class="mono">{{ order.orderNo }}</i></small>
            </div>

            <div class="qr-zone">
              <button type="button" class="qr-tile" :disabled="!qrDataUrl" @click="openQr">
                <img v-if="qrDataUrl" :src="qrDataUrl" alt="支付二维码" />
                <span v-else class="qr-loading">{{ creating ? '支付单创建中…' : '二维码生成中…' }}</span>
              </button>
              <div class="qr-side">
                <p class="qr-title">扫码支付</p>
                <p class="qr-desc">微信 / 支付宝扫码支付<br />演示环境不会产生真实扣款</p>
                <div class="countdown" :class="{ urgent: remaining !== null && remaining <= 10 }">
                  <el-icon><Clock /></el-icon>
                  <span v-if="remaining === null">支付时限以订单为准</span>
                  <span v-else>支付剩余 <b>{{ countdownText }}</b></span>
                </div>
                <el-button class="scan-button" type="primary" :disabled="!pay || creating" @click="openQr">
                  打开扫码窗口
                </el-button>
              </div>
            </div>

            <p v-if="errorText" class="error-box">{{ errorText }}</p>

          </template>
        </section>
      </div>
    </div>

    <el-dialog
      v-model="qrDialog"
      title="扫码支付"
      width="360px"
      append-to-body
      class="qr-dialog"
      :close-on-click-modal="false"
    >
      <div class="qr-dialog-body">
        <img v-if="qrDataUrl" :src="qrDataUrl" class="qr-large" alt="支付二维码" />
        <div v-else class="qr-large qr-large-empty">二维码生成中…</div>
        <p class="qr-dialog-amount">¥{{ money(order?.payAmount) }}</p>
        <p class="qr-dialog-no mono">{{ order?.orderNo }}</p>
        <p class="qr-dialog-expire" :class="{ urgent: remaining !== null && remaining <= 10 }">
          支付剩余 {{ countdownText }}
        </p>
        <p class="qr-dialog-tip">当前为演示环境，点击下方按钮即可完成支付，不会产生真实扣款。</p>
        <el-button
          type="primary"
          size="large"
          class="qr-dialog-btn"
          :loading="mocking"
          :disabled="!canPay"
          @click="mockPayFromDialog"
        >
          完成支付
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.narrow {
  width: min(980px, calc(100% - 48px));
}

.back-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  padding: 4px 0;
  cursor: pointer;
}

.back-button:hover {
  color: var(--brand);
}

.loading {
  margin-top: 18px;
}

.pay-skeleton {
  height: 420px;
}

.pay-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.9fr);
  gap: 16px;
  margin-top: 18px;
  align-items: start;
}

.order-summary {
  padding: 22px;
}

.summary-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.summary-head h2 {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.summary-items {
  margin-top: 12px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 0;
  border-bottom: 1px dashed var(--line);
}

.summary-item img {
  width: 54px;
  height: 54px;
  border-radius: 6px;
  object-fit: cover;
  background: #eee;
}

.summary-item div {
  flex: 1;
  min-width: 0;
}

.summary-item strong {
  display: block;
  font-size: 12px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.summary-item span {
  display: block;
  margin-top: 3px;
  font-size: 10px;
  color: var(--muted);
}

.summary-item b {
  flex: none;
  font-size: 12px;
  font-weight: 600;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 0;
  font-size: 11px;
  border-bottom: 1px dashed var(--line);
}

.summary-row span {
  color: var(--muted);
  flex: none;
}

.summary-row b {
  text-align: right;
  color: var(--ink-soft);
  font-weight: 500;
  overflow-wrap: anywhere;
}

.summary-total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 14px;
  font-size: 12px;
  color: var(--muted);
}

.summary-amount {
  font-size: 24px;
}

.pay-card {
  border-radius: var(--radius);
  background: #fff;
  border: 1px solid var(--line);
  color: var(--ink);
  padding: 24px;
  min-height: 380px;
}

.pay-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
}

.pay-head h2 {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.pay-env {
  font-size: 10px;
  color: #b4bac3;
}

.pay-amount {
  margin-top: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}

.pay-amount > span:not(.price-text) {
  font-size: 11px;
  color: var(--muted);
}

.pay-amount-value {
  display: flex;
  margin-top: 4px;
  font-size: 38px;
  line-height: 1.1;
}

.pay-amount small {
  display: block;
  margin-top: 6px;
  font-size: 10px;
  color: var(--muted);
}

.pay-amount small i {
  font-style: normal;
  color: var(--ink-soft);
}

.qr-zone {
  display: grid;
  grid-template-columns: 142px minmax(0, 1fr);
  gap: 16px;
  margin-top: 18px;
}

.qr-tile {
  width: 142px;
  height: 142px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  display: grid;
  place-items: center;
}

.qr-tile:hover:not(:disabled) {
  border-color: var(--line-strong);
}

.qr-tile:disabled {
  cursor: default;
}

.qr-tile img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.qr-loading {
  font-size: 10px;
  line-height: 1.6;
  color: var(--muted);
  text-align: center;
}

.qr-side {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 7px;
  min-width: 0;
}

.qr-title {
  font-size: 14px;
  font-weight: 600;
}

.qr-desc {
  font-size: 10px;
  line-height: 1.7;
  color: var(--muted);
}

.countdown {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: var(--ink-soft);
}

.countdown b {
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: 0.04em;
  font-variant-numeric: tabular-nums;
  color: var(--brand);
}

.countdown.urgent,
.countdown.urgent b {
  color: #d92d20;
}

.scan-button {
  align-self: flex-start;
  margin-top: 2px;
  height: 34px;
  padding: 0 16px;
  border-radius: 6px;
  font-size: 12px;
}

.error-box {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #fbefed;
  border: 1px solid #f0d4d0;
  color: #c44536;
  font-size: 11px;
}

.result-box {
  min-height: 330px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
}

.result-icon {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--teal);
  color: #fff;
  font-size: 22px;
  margin-bottom: 14px;
}

.result-box.failed .result-icon,
.result-icon.warning-icon {
  background: #d92d20;
}

.result-icon.muted-icon {
  background: #f2f3f5;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 20px;
}

.result-box h2 {
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.result-box p {
  margin-top: 7px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--muted);
}

.result-amount {
  margin-top: 14px;
  font-size: 30px;
}

.result-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 10px;
  font-size: 9px;
  color: var(--muted);
}

.result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  margin-top: 22px;
}

@media (max-width: 880px) {
  .pay-layout {
    grid-template-columns: 1fr;
  }

  .pay-card {
    min-height: 0;
  }
}

@media (max-width: 560px) {
  .narrow {
    width: calc(100% - 32px);
  }

  .pay-amount-value {
    font-size: 32px;
  }

  .pay-head {
    flex-direction: row;
  }

  .qr-zone {
    grid-template-columns: 1fr;
  }

  .qr-tile {
    justify-self: start;
  }

  .result-box h2 {
    font-size: 22px;
  }
}
</style>

<style>
.qr-dialog-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding-top: 4px;
}

.qr-dialog-body .qr-large {
  width: 220px;
  height: 220px;
  object-fit: contain;
  border-radius: 6px;
  background: #fff;
  border: 1px solid var(--line);
}

.qr-dialog-body .qr-large-empty {
  display: grid;
  place-items: center;
  font-size: 12px;
  color: var(--muted);
}

.qr-dialog-body .qr-dialog-amount {
  margin-top: 12px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--price);
}

.qr-dialog-body .qr-dialog-no {
  font-size: 11px;
  color: var(--muted);
  word-break: break-all;
  text-align: center;
}

.qr-dialog-body .qr-dialog-expire {
  margin-top: 6px;
  font-size: 12px;
  color: var(--ink-soft);
  font-variant-numeric: tabular-nums;
}

.qr-dialog-body .qr-dialog-expire.urgent {
  color: #d92d20;
}

.qr-dialog-body .qr-dialog-tip {
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.7;
  color: var(--muted);
  text-align: center;
}

.qr-dialog-body .qr-dialog-btn {
  width: 100%;
  height: 42px;
  margin-top: 14px;
  border-radius: 6px;
}
</style>
