<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, ShoppingCart } from '@element-plus/icons-vue'
import { orderApi } from '../api'
import { useCartStore } from '../stores/cart'
import { money, resolveImage } from '../utils/format'
import PriceText from '../components/PriceText.vue'
import { waitForOrderCreated } from '../utils/order'

const router = useRouter()
const cart = useCartStore()
const loading = ref(true)
const submitting = ref(false)
const creatingOpen = ref(false)
const currentOrderNo = ref('')
const createState = ref({ state: 'QUEUED', message: '' })

const selectedCount = computed(() => cart.selectedCount)
const selectedTotal = computed(() => cart.selectedTotal)

onMounted(async () => {
  try {
    await cart.load()
  } finally {
    loading.value = false
  }
})

function fallback(event) {
  event.target.src = '/images/product/placeholder.jpg'
}

async function toggleItem(item, value) {
  try {
    await cart.updateItem(item.id, { selected: value ? 1 : 0 })
  } catch {
    await cart.load()
  }
}

async function changeQuantity(item, value) {
  if (!value || value === item.quantity) return
  try {
    await cart.updateItem(item.id, { quantity: value })
  } catch {
    await cart.load()
  }
}

async function removeItem(item) {
  try {
    await ElMessageBox.confirm(`确定从购物车删除「${item.productName}」吗？`, '删除商品', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '保留'
    })
  } catch {
    return
  }
  try {
    await cart.remove(item.id)
    ElMessage.success('已从购物车移除')
  } catch {
    /* 拦截器已提示具体原因 */
  }
}

async function removeSelected() {
  if (!cart.items.some((item) => item.selected)) {
    ElMessage.warning('请先勾选要删除的商品')
    return
  }
  try {
    await ElMessageBox.confirm('确定删除全部已勾选商品吗？', '批量删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await cart.clearSelected()
  ElMessage.success('已删除勾选商品')
}

async function toggleAll(value) {
  try {
    await cart.selectAll(value)
  } catch {
    await cart.load()
  }
}

async function checkout() {
  if (!selectedCount.value) {
    ElMessage.warning('请先勾选要结算的商品')
    return
  }
  submitting.value = true
  try {
    const result = await orderApi.create({
      orderType: 1,
      items: cart.selectedItems.map((item) => ({
        productId: item.productId,
        quantity: item.quantity
      }))
    })
    currentOrderNo.value = result.orderNo
    createState.value = { state: result.state || 'QUEUED', message: result.message || '' }
    creatingOpen.value = true
    const finalState = await waitForOrderCreated(result.orderNo, (state) => {
      createState.value = state
    })
    if (finalState?.state === 'CREATED') {
      await cart.clearSelected()
      ElMessage.success('订单创建成功，正在进入支付')
      creatingOpen.value = false
      router.push(`/pay/${result.orderNo}`)
    }
  } catch {
    /* 拦截器已提示具体原因 */
  } finally {
    submitting.value = false
  }
}

function goOrder() {
  creatingOpen.value = false
  if (currentOrderNo.value) router.push(`/orders/${currentOrderNo.value}`)
}
</script>

<template>
  <div class="page">
    <div class="container">
      <header class="page-head">
        <div>
          <h1>购物车</h1>
          <p>勾选多件商品一起下单，支持到店安装或配送到家。</p>
        </div>
        <router-link to="/products" class="section-link">继续选购 →</router-link>
      </header>

      <div v-if="loading" class="cart-loading">
        <div v-for="n in 3" :key="n" class="skeleton-block cart-skeleton"></div>
      </div>

      <el-empty v-else-if="!cart.items.length" description="购物车还是空的" :image-size="120">
        <el-button type="primary" @click="router.push('/products')">去挑养车好物</el-button>
      </el-empty>

      <div v-else class="cart-layout">
        <section class="surface cart-main">
          <div class="select-bar">
            <el-checkbox
              :model-value="cart.allSelected"
              :indeterminate="cart.selectedItems.length > 0 && !cart.allSelected"
              @change="toggleAll"
            >
              全选
            </el-checkbox>
            <span class="select-count num">已选 {{ selectedCount }} 件</span>
            <button type="button" class="text-danger" @click="removeSelected">删除已选</button>
          </div>

          <div class="cart-cols" aria-hidden="true">
            <span></span>
            <span class="col-product">商品</span>
            <span class="col-qty">数量</span>
            <span class="col-subtotal">小计</span>
            <span></span>
          </div>

          <article v-for="item in cart.items" :key="item.id" class="cart-item">
            <el-checkbox
              class="item-check"
              :model-value="item.selected"
              :disabled="!item.available"
              @change="(value) => toggleItem(item, value)"
            />

            <router-link v-if="item.available" :to="`/products/${item.productId}`" class="item-link">
              <img :src="resolveImage(item.coverImg)" :alt="item.productName" @error="fallback" />
            </router-link>
            <img v-else class="item-image" :src="resolveImage(item.coverImg)" :alt="item.productName" @error="fallback" />

            <div class="item-info">
              <router-link v-if="item.available" :to="`/products/${item.productId}`" class="item-name">
                {{ item.productName }}
              </router-link>
              <strong v-else class="item-name muted">{{ item.productName }}</strong>
              <div class="item-meta">
                <span>单价 ¥{{ money(item.price) }}</span>
                <el-tag v-if="!item.available" type="info" effect="plain" size="small">已失效</el-tag>
                <span v-else :class="{ 'stock-low': item.stock <= 10 }">库存 {{ item.stock }}</span>
              </div>
            </div>

            <el-input-number
              class="quantity"
              :model-value="item.quantity"
              :min="1"
              :max="Math.max(1, item.stock)"
              :disabled="!item.available"
              size="small"
              @change="(value) => changeQuantity(item, value)"
            />

            <b class="subtotal num">¥{{ money(item.totalAmount) }}</b>

            <button type="button" class="remove-button" title="删除" @click="removeItem(item)">
              <el-icon><Delete /></el-icon>
            </button>
          </article>
        </section>

        <aside class="surface cart-summary">
          <div class="summary-head">
            <el-icon><ShoppingCart /></el-icon>
            <h2>结算清单</h2>
          </div>
          <div class="summary-row">
            <span>已选商品</span>
            <b class="num">{{ selectedCount }} 件</b>
          </div>
          <div class="summary-row">
            <span>商品金额</span>
            <b class="num">¥{{ money(selectedTotal) }}</b>
          </div>
          <div class="summary-row">
            <span>配送 / 到店</span>
            <b class="muted">结算后确认</b>
          </div>
          <div class="summary-total">
            <span>应付金额</span>
            <PriceText :value="selectedTotal" class="summary-amount" />
          </div>
          <el-button
            type="primary"
            size="large"
            class="checkout-button"
            :loading="submitting"
            :disabled="!selectedCount"
            @click="checkout"
          >
            去结算（{{ selectedCount }}）
          </el-button>
          <p class="summary-note">下单后可选择到店安装或配送，具体时间以门店确认为准。</p>
        </aside>
      </div>

      <el-dialog
        v-model="creatingOpen"
        title="订单处理中"
        width="430px"
        :close-on-click-modal="false"
        :show-close="createState.state !== 'QUEUED'"
      >
        <div class="creating-state">
          <div v-if="createState.state === 'QUEUED'" class="pulse"><span></span><span></span><span></span></div>
          <div v-else-if="createState.state === 'CREATED'" class="state-icon ok">✓</div>
          <div v-else class="state-icon fail">!</div>
          <div>
            <strong>
              {{
                createState.state === 'QUEUED'
                  ? '请求已入队，正在创建订单'
                  : createState.state === 'CREATED'
                    ? '订单创建成功'
                    : '订单创建失败'
              }}
            </strong>
            <p>
              {{
                createState.state === 'QUEUED'
                  ? '正在扣减库存并合并多件商品，请稍候…'
                  : createState.message || '可在订单列表查看处理结果'
              }}
            </p>
            <span class="mono order-no">{{ currentOrderNo }}</span>
          </div>
        </div>
        <template #footer>
          <el-button v-if="createState.state !== 'QUEUED'" @click="goOrder">查看订单</el-button>
        </template>
      </el-dialog>
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

.cart-loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 22px;
}

.cart-skeleton {
  height: 108px;
}

.cart-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 18px;
  margin-top: 22px;
  align-items: start;
}

.cart-main {
  overflow: hidden;
}

.select-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 16px;
  background: var(--surface-soft);
  border-bottom: 1px solid var(--line);
  font-size: 12px;
  color: var(--muted);
}

.cart-cols {
  display: grid;
  grid-template-columns: 22px 78px minmax(0, 1fr) 116px 92px 30px;
  gap: 14px;
  padding: 7px 16px;
  border-bottom: 1px solid var(--line);
  font-size: 10px;
  color: var(--muted);
}

.cart-cols .col-product {
  grid-column: 2 / 4;
}

.cart-cols .col-qty {
  grid-column: 4;
  text-align: center;
}

.cart-cols .col-subtotal {
  grid-column: 5;
  text-align: right;
}

.select-count {
  margin-left: auto;
}

.text-danger {
  border: 0;
  background: transparent;
  color: #d92d20;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 0;
}

.cart-item {
  display: grid;
  grid-template-columns: 22px 78px minmax(0, 1fr) 116px 92px 30px;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border-bottom: 1px dashed var(--line);
}

.cart-item:last-child {
  border-bottom: 0;
}

.item-link img,
.item-image {
  width: 78px;
  height: 78px;
  border-radius: 6px;
  object-fit: cover;
  background: #eee;
}

.item-info {
  min-width: 0;
}

.item-name {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-name:hover {
  color: var(--brand);
}

.item-name.muted {
  color: var(--muted);
}

.item-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 7px;
  font-size: 11px;
  color: var(--muted);
}

.stock-low {
  color: var(--brand-deep);
  font-weight: 600;
}

.quantity {
  width: 110px;
}

.subtotal {
  text-align: right;
  font-size: 15px;
  font-weight: 600;
  color: var(--price);
}

.remove-button {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}

.remove-button:hover {
  background: #fef3f2;
  color: #d92d20;
}

.cart-summary {
  position: sticky;
  top: 96px;
  padding: 22px;
}

.summary-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink);
}

.summary-head .el-icon {
  color: var(--brand);
}

.summary-head h2 {
  font-size: 18px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--line);
  font-size: 12px;
  color: var(--muted);
}

.summary-row b {
  color: var(--ink-soft);
}

.muted {
  color: var(--muted);
}

.summary-total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 16px;
  font-size: 12px;
  color: var(--muted);
}

.summary-amount {
  font-size: 26px;
}

.checkout-button {
  width: 100%;
  height: 44px;
  margin-top: 16px;
  border-radius: 6px;
  font-size: 14px;
}

.summary-note {
  margin-top: 10px;
  font-size: 10px;
  line-height: 1.6;
  color: var(--muted);
}

.creating-state {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 10px 4px;
}

.creating-state strong {
  font-size: 15px;
}

.creating-state p {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--muted);
}

.order-no {
  display: block;
  margin-top: 8px;
  font-size: 10px;
  color: var(--muted);
}

.pulse {
  flex: none;
  display: flex;
  gap: 5px;
  align-items: center;
  height: 46px;
}

.pulse span {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--brand);
  animation: bounce 1s infinite ease-in-out;
}

.pulse span:nth-child(2) {
  animation-delay: 0.14s;
}

.pulse span:nth-child(3) {
  animation-delay: 0.28s;
}

@keyframes bounce {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  40% {
    transform: translateY(-9px);
    opacity: 1;
  }
}

.state-icon {
  flex: none;
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  font-size: 20px;
  font-weight: 800;
}

.state-icon.ok {
  background: var(--teal-soft);
  color: var(--teal);
}

.state-icon.fail {
  background: #fef3f2;
  color: #d92d20;
}

@media (max-width: 900px) {
  .cart-layout {
    grid-template-columns: 1fr;
  }

  .cart-summary {
    position: static;
  }
}

@media (max-width: 700px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .cart-cols {
    display: none;
  }

  .cart-item {
    grid-template-columns: 22px 64px minmax(0, 1fr) 30px;
    gap: 10px;
  }

  .item-link img,
  .item-image {
    width: 64px;
    height: 64px;
  }

  .quantity {
    grid-column: 2 / 4;
    width: 110px;
  }

  .subtotal {
    grid-column: 3 / 4;
    text-align: left;
  }

  .remove-button {
    grid-column: 4;
    grid-row: 1;
  }
}
</style>
