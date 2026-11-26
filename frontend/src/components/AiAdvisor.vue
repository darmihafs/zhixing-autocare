<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ChatDotRound, Close, Promotion } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { orderStatus, resolveServiceImage, shortDate } from '../utils/format'
import ProductCard from './ProductCard.vue'
import ShopCard from './ShopCard.vue'
import PriceText from './PriceText.vue'

const auth = useAuthStore()

const open = ref(false)
const input = ref('')
const sending = ref(false)
const messages = ref([])
const sessionId = ref(sessionStorage.getItem('hc_ai_session') || '')
const listRef = ref(null)
let controller = null
let uid = 0

const quickTags = ['5万公里换什么机油？', '附近哪里可以洗车？', '查一下我的订单']
const isEmpty = computed(() => messages.value.length === 0)

function toggle() {
  open.value = !open.value
  if (open.value) scrollBottom()
}

function close() {
  open.value = false
}

function onKey(e) {
  if (e.key === 'Escape' && open.value) close()
}

function scrollBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function serviceLink(service) {
  return { path: `/shops/${service.shopId}`, query: { serviceId: service.id, book: '1' } }
}

function svcFallback(event) {
  event.target.src = '/images/shop/service-maintenance.jpg'
}

function onCardClick(event) {
  if (event.target.closest('a')) close()
}

function rememberSession(id) {
  if (!id) return
  sessionId.value = id
  sessionStorage.setItem('hc_ai_session', id)
}

function handleFrame(frame, assistant) {
  let event = 'message'
  let data = ''
  for (const raw of frame.split('\n')) {
    const line = raw.replace(/\r$/, '')
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data += line.slice(5).trim()
  }
  if (!data) return
  let payload
  try {
    payload = JSON.parse(data)
  } catch {
    return
  }
  switch (event) {
    case 'meta':
      rememberSession(payload.sessionId)
      break
    case 'status':
      assistant.status = payload.text || ''
      scrollBottom()
      break
    case 'delta':
      assistant.text += payload.text || ''
      assistant.status = ''
      scrollBottom()
      break
    case 'cards':
      assistant.cards = payload
      scrollBottom()
      break
    case 'done':
      assistant.streaming = false
      assistant.degraded = Boolean(payload.degraded)
      assistant.status = ''
      rememberSession(payload.sessionId)
      break
    case 'error':
      assistant.text = assistant.text || payload.text || '服务繁忙，请稍后重试。'
      assistant.status = ''
      break
    default:
      break
  }
}

async function send(text) {
  const msg = (text ?? input.value).trim()
  if (!msg || sending.value) return
  input.value = ''
  messages.value.push({ id: ++uid, role: 'user', text: msg })
  const assistant = reactive({
    id: ++uid,
    role: 'assistant',
    text: '',
    cards: null,
    status: '正在思考…',
    streaming: true,
    degraded: false
  })
  messages.value.push(assistant)
  sending.value = true
  scrollBottom()

  controller = new AbortController()
  try {
    const headers = { 'Content-Type': 'application/json', Accept: 'text/event-stream' }
    const token = localStorage.getItem('hc_token')
    if (token) headers.Authorization = `Bearer ${token}`
    const resp = await fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers,
      body: JSON.stringify({ sessionId: sessionId.value || undefined, message: msg }),
      signal: controller.signal
    })
    if (!resp.ok || !resp.body) throw new Error(`HTTP ${resp.status}`)
    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buf = ''
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      buf = buf.replace(/\r\n/g, '\n')
      let idx
      while ((idx = buf.indexOf('\n\n')) >= 0) {
        const frame = buf.slice(0, idx)
        buf = buf.slice(idx + 2)
        handleFrame(frame, assistant)
      }
    }
    if (buf.trim()) handleFrame(buf, assistant)
  } catch (err) {
    if (err.name !== 'AbortError') {
      assistant.text = assistant.text || '网络异常，请稍后重试。'
      assistant.status = ''
    }
  } finally {
    assistant.streaming = false
    assistant.status = ''
    sending.value = false
    controller = null
    scrollBottom()
  }
}

onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  if (controller) controller.abort()
})
</script>

<template>
  <div class="ai-advisor">
    <button
      v-if="!open"
      class="ai-fab"
      type="button"
      aria-label="打开养车顾问"
      @click="toggle"
    >
      <el-icon><ChatDotRound /></el-icon>
      <span class="fab-label">养车顾问</span>
    </button>

    <transition name="ai-slide">
      <section v-if="open" class="ai-drawer" role="dialog" aria-label="洪兴养车顾问">
        <header class="ai-head">
          <div class="ai-title">
            <span class="dot"></span>
            <div class="ai-title-text">
              <strong>洪兴养车顾问</strong>
              <small>选品 · 找店 · 查订单</small>
            </div>
          </div>
          <button class="ai-close" type="button" aria-label="关闭" @click="close">
            <el-icon><Close /></el-icon>
          </button>
        </header>

        <div ref="listRef" class="ai-body">
          <div v-if="isEmpty" class="ai-welcome">
            <p>你好，我是洪兴养车顾问。可以帮你挑养护商品、找附近门店与服务，登录后还能查订单状态。</p>
            <div class="ai-tags">
              <button v-for="t in quickTags" :key="t" class="tag" type="button" @click="send(t)">
                {{ t }}
              </button>
            </div>
          </div>

          <div v-for="m in messages" :key="m.id" class="msg" :class="m.role">
            <div class="bubble">
              <span v-if="m.status" class="status"><i class="pulse"></i>{{ m.status }}</span>
              <p v-if="m.text" class="text">{{ m.text }}<span v-if="m.streaming" class="caret"></span></p>
              <span v-if="m.degraded" class="chip chip--brand degraded-chip">本地检索结果</span>

              <div v-if="m.cards" class="cards" @click="onCardClick">
                <template v-if="m.cards.products && m.cards.products.length">
                  <div class="cards-label">相关商品</div>
                  <div class="prod-grid">
                    <ProductCard v-for="p in m.cards.products" :key="p.id" :product="p" />
                  </div>
                </template>

                <template v-if="m.cards.shops && m.cards.shops.length">
                  <div class="cards-label">附近门店</div>
                  <ShopCard v-for="s in m.cards.shops" :key="s.id" :shop="s" compact />
                </template>

                <template v-if="m.cards.services && m.cards.services.length">
                  <div class="cards-label">服务项目</div>
                  <router-link
                    v-for="sv in m.cards.services"
                    :key="sv.id"
                    class="svc-card"
                    :to="serviceLink(sv)"
                  >
                    <img :src="resolveServiceImage(sv)" :alt="sv.itemName" @error="svcFallback" />
                    <div class="svc-info">
                      <strong>{{ sv.itemName }}</strong>
                      <span>{{ sv.shopName }} · {{ sv.durationMin }} 分钟</span>
                      <PriceText :value="sv.price" />
                    </div>
                  </router-link>
                </template>

                <template v-if="m.cards.orders && m.cards.orders.length">
                  <div class="cards-label">我的订单</div>
                  <router-link
                    v-for="o in m.cards.orders"
                    :key="o.orderNo"
                    class="ord-card"
                    :to="`/orders/${o.orderNo}`"
                  >
                    <div class="ord-top">
                      <span class="ord-no">{{ o.orderNo }}</span>
                      <span class="ord-status" :class="orderStatus(o.status).tone">
                        {{ o.statusDesc || orderStatus(o.status).text }}
                      </span>
                    </div>
                    <div class="ord-bottom">
                      <span>{{ o.orderTypeDesc }} · {{ shortDate(o.createTime) }}</span>
                      <PriceText :value="o.payAmount ?? o.totalAmount" />
                    </div>
                  </router-link>
                </template>
              </div>
            </div>
          </div>
        </div>

        <footer class="ai-foot">
          <div v-if="!isEmpty" class="ai-tags compact">
            <button
              v-for="t in quickTags"
              :key="t"
              class="tag"
              type="button"
              :disabled="sending"
              @click="send(t)"
            >
              {{ t }}
            </button>
          </div>
          <form class="ai-input" @submit.prevent="send()">
            <input
              v-model="input"
              type="text"
              maxlength="500"
              :disabled="sending"
              placeholder="问问机油、洗车、保养，或查订单…"
            />
            <button class="send" type="submit" :disabled="sending || !input.trim()" aria-label="发送">
              <el-icon><Promotion /></el-icon>
            </button>
          </form>
          <p v-if="!auth.isLoggedIn" class="login-hint">登录后可查询你的订单状态</p>
        </footer>
      </section>
    </transition>
  </div>
</template>

<style scoped>
.ai-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1200;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 48px;
  padding: 0 18px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  color: var(--brand-deep);
  box-shadow: var(--shadow);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}

.ai-fab:hover {
  background: var(--brand-soft);
  border-color: #f8d3c8;
  transform: translateY(-1px);
}

.ai-fab .el-icon {
  font-size: 20px;
}

.fab-label {
  font-size: 13px;
  font-weight: 600;
}

.ai-drawer {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 1300;
  display: flex;
  flex-direction: column;
  width: 380px;
  max-width: calc(100vw - 16px);
  height: 100%;
  background: var(--surface);
  border-left: 1px solid var(--line);
  box-shadow: -4px 0 12px rgba(0, 0, 0, 0.05);
}

.ai-slide-enter-active,
.ai-slide-leave-active {
  transition: transform 0.22s ease;
}

.ai-slide-enter-from,
.ai-slide-leave-to {
  transform: translateX(100%);
}

.ai-head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
}

.ai-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-title .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--brand);
}

.ai-title-text strong {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}

.ai-title-text small {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--muted);
}

.ai-close {
  flex: none;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}

.ai-close:hover {
  background: var(--surface-soft);
  color: var(--ink);
}

.ai-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.ai-welcome p {
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-soft);
  margin-bottom: 12px;
}

.ai-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ai-tags.compact {
  margin-bottom: 10px;
}

.tag {
  border: 1px solid #f8d3c8;
  background: var(--brand-soft);
  color: var(--brand-deep);
  border-radius: 4px;
  padding: 5px 11px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.tag:hover:not(:disabled) {
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
}

.tag:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.msg {
  display: flex;
  margin-bottom: 14px;
}

.msg.user {
  justify-content: flex-end;
}

.msg.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 88%;
  padding: 9px 12px;
  border-radius: 6px;
  font-size: 13px;
}

.msg.user .bubble {
  background: var(--brand-soft);
  color: var(--ink);
}

.msg.assistant .bubble {
  width: 100%;
  max-width: 100%;
  background: var(--surface-soft);
  border: 1px solid var(--line);
  color: var(--ink);
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
}

.pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand);
  animation: ai-pulse 1s ease-in-out infinite;
}

@keyframes ai-pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

.text {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.caret {
  display: inline-block;
  width: 6px;
  height: 14px;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--brand);
  animation: ai-blink 1s steps(1) infinite;
}

@keyframes ai-blink {
  50% { opacity: 0; }
}

.degraded-chip {
  margin-top: 8px;
}

.cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

.cards-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--muted);
  margin-top: 4px;
}

.prod-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.prod-grid :deep(.media) {
  aspect-ratio: 4 / 3;
}

.prod-grid :deep(.body) {
  padding: 6px 8px 8px;
}

.prod-grid :deep(h3) {
  min-height: 0;
  -webkit-line-clamp: 1;
}

.prod-grid :deep(.meta) {
  display: none;
}

.prod-grid :deep(.foot) {
  margin-top: 5px;
}

.prod-grid :deep(.price) {
  font-size: 15px;
}

.prod-grid :deep(.cart-add) {
  height: 22px;
  padding: 0 6px;
  font-size: 10px;
}

.svc-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  transition: border-color 0.15s ease;
}

.svc-card:hover {
  border-color: var(--line-strong);
}

.svc-card img {
  flex: none;
  width: 56px;
  height: 56px;
  border-radius: 6px;
  object-fit: cover;
  background: var(--surface-soft);
}

.svc-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.svc-info strong {
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.svc-info span {
  font-size: 10px;
  color: var(--muted);
}

.ord-card {
  display: block;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  transition: border-color 0.15s ease;
}

.ord-card:hover {
  border-color: var(--line-strong);
}

.ord-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ord-no {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--ink-soft);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ord-status {
  flex: none;
  font-size: 11px;
  font-weight: 600;
  color: var(--muted);
}

.ord-status.success { color: var(--success); }
.ord-status.primary { color: var(--brand); }
.ord-status.warning { color: var(--brand-deep); }

.ord-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 5px;
  font-size: 11px;
  color: var(--muted);
}

.ai-foot {
  flex: none;
  padding: 12px 16px;
  border-top: 1px solid var(--line);
  background: var(--surface);
}

.ai-input {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-input input {
  flex: 1;
  min-width: 0;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  outline: none;
  font-size: 13px;
  background: #fff;
}

.ai-input input:focus {
  border-color: var(--brand);
}

.ai-input input:disabled {
  background: var(--surface-soft);
}

.send {
  flex: none;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 8px;
  background: var(--brand);
  color: #fff;
  cursor: pointer;
}

.send:hover:not(:disabled) {
  background: #ef5a35;
}

.send:disabled {
  background: #f2f3f5;
  color: var(--muted);
  cursor: not-allowed;
}

.login-hint {
  margin-top: 8px;
  font-size: 11px;
  color: var(--muted);
  text-align: center;
}

@media (max-width: 560px) {
  .ai-fab {
    right: 16px;
    bottom: 16px;
  }

  .ai-drawer {
    width: 100%;
    max-width: 100%;
    border-left: 0;
  }
}
</style>