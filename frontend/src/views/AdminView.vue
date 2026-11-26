<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, reviewApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { money, normalizePage, orderStatus, shortDate } from '../utils/format'

const router = useRouter()
const auth = useAuthStore()
const activeTab = ref('shops')
const pageSize = 10

/* ---------- 门店列表 ---------- */
const shopLoading = ref(false)
const shops = ref([])
const shopTotal = ref(0)
const shopPageNum = ref(1)
const shopKeyword = ref('')
const shopOptions = ref([])

async function loadShops() {
  shopLoading.value = true
  try {
    const data = await adminApi.shopPage({
      pageNum: shopPageNum.value,
      pageSize,
      keyword: shopKeyword.value.trim() || undefined
    })
    const page = normalizePage(data, pageSize)
    shops.value = page.records
    shopTotal.value = page.total
  } finally {
    shopLoading.value = false
  }
}

function searchShops() {
  shopPageNum.value = 1
  loadShops()
}

async function loadShopOptions() {
  const data = await adminApi.shopPage({ pageNum: 1, pageSize: 50 })
  shopOptions.value = normalizePage(data, 50).records
  if (!serviceShopId.value && shopOptions.value.length) {
    serviceShopId.value = shopOptions.value[0].id
  }
  if (!reviewShopId.value && shopOptions.value.length) {
    reviewShopId.value = shopOptions.value[0].id
  }
}

const shopDialog = reactive({ visible: false, saving: false, editingId: null })
const shopForm = reactive(emptyShopForm())

function emptyShopForm() {
  return {
    shopName: '',
    city: '',
    district: '',
    address: '',
    contactPhone: '',
    businessHours: '',
    description: ''
  }
}

function openShopDialog(shop) {
  shopDialog.editingId = shop?.id ?? null
  Object.assign(
    shopForm,
    emptyShopForm(),
    shop
      ? {
          shopName: shop.shopName,
          city: shop.city,
          district: shop.district,
          address: shop.address,
          contactPhone: shop.contactPhone,
          businessHours: shop.businessHours,
          description: shop.description
        }
      : {}
  )
  shopDialog.visible = true
}

async function saveShop() {
  if (!shopForm.shopName.trim() || !shopForm.city.trim()) {
    ElMessage.warning('请填写门店名称与所在城市')
    return
  }
  shopDialog.saving = true
  try {
    const payload = { ...shopForm }
    if (shopDialog.editingId) await adminApi.updateShop(shopDialog.editingId, payload)
    else await adminApi.createShop(payload)
    ElMessage.success(shopDialog.editingId ? '门店已更新，搜索索引同步中' : '门店已创建，搜索索引同步中')
    shopDialog.visible = false
    loadShops()
    loadShopOptions()
  } finally {
    shopDialog.saving = false
  }
}

async function toggleShop(shop) {
  const next = shop.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      next === 1
        ? `确定恢复营业「${shop.shopName}」吗？`
        : `确定停业「${shop.shopName}」吗？停业后 C 端列表与搜索不再展示。`,
      '门店状态',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await adminApi.shopStatus(shop.id, next)
  ElMessage.success(next === 1 ? '已恢复营业' : '已停业')
  loadShops()
  loadShopOptions()
}

/* ---------- 服务项目 ---------- */
const serviceShopId = ref(null)
const serviceLoading = ref(false)
const services = ref([])
const serviceTotal = ref(0)
const servicePageNum = ref(1)

async function loadServices() {
  if (!serviceShopId.value) {
    services.value = []
    serviceTotal.value = 0
    return
  }
  serviceLoading.value = true
  try {
    const data = await adminApi.servicePage({
      shopId: serviceShopId.value,
      pageNum: servicePageNum.value,
      pageSize
    })
    const page = normalizePage(data, pageSize)
    services.value = page.records
    serviceTotal.value = page.total
  } finally {
    serviceLoading.value = false
  }
}

function switchServiceShop(id) {
  serviceShopId.value = id
  servicePageNum.value = 1
  loadServices()
}

const serviceDialog = reactive({ visible: false, saving: false, editingId: null })
const serviceForm = reactive(emptyServiceForm())
const categoryOptions = ['洗车', '保养', '美容', '维修']

function emptyServiceForm() {
  return {
    shopId: null,
    itemName: '',
    category: '',
    price: null,
    originalPrice: null,
    durationMin: 30,
    description: ''
  }
}

function openServiceDialog(item) {
  serviceDialog.editingId = item?.id ?? null
  Object.assign(
    serviceForm,
    emptyServiceForm(),
    { shopId: serviceShopId.value },
    item
      ? {
          itemName: item.itemName,
          category: item.category,
          price: item.price,
          originalPrice: item.originalPrice,
          durationMin: item.durationMin,
          description: item.description
        }
      : {}
  )
  serviceDialog.visible = true
}

async function saveService() {
  if (!serviceForm.shopId) {
    ElMessage.warning('请选择所属门店')
    return
  }
  if (!serviceForm.itemName.trim() || !serviceForm.category.trim()) {
    ElMessage.warning('请填写服务名称与分类')
    return
  }
  if (!(serviceForm.price > 0)) {
    ElMessage.warning('请填写正确的服务价格')
    return
  }
  serviceDialog.saving = true
  try {
    const payload = {
      shopId: serviceForm.shopId,
      itemName: serviceForm.itemName,
      category: serviceForm.category,
      price: serviceForm.price,
      originalPrice: serviceForm.originalPrice || undefined,
      durationMin: serviceForm.durationMin,
      description: serviceForm.description
    }
    if (serviceDialog.editingId) await adminApi.updateService(serviceDialog.editingId, payload)
    else await adminApi.createService(payload)
    ElMessage.success(serviceDialog.editingId ? '服务已更新，搜索索引同步中' : '服务已创建，搜索索引同步中')
    serviceDialog.visible = false
    loadServices()
  } finally {
    serviceDialog.saving = false
  }
}

async function toggleService(item) {
  const next = item.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      next === 1 ? `确定上架「${item.itemName}」吗？` : `确定下架「${item.itemName}」吗？下架后 C 端不再展示。`,
      '服务状态',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await adminApi.serviceStatus(item.id, next)
  ElMessage.success(next === 1 ? '已上架' : '已下架')
  loadServices()
}

/* ---------- 订单处理 ---------- */
const orderTabs = [
  { label: '待处理', value: 10 },
  { label: '进行中', value: 20 },
  { label: '全部', value: null }
]
const orderFilter = ref(10)
const orderLoading = ref(false)
const orders = ref([])
const orderTotal = ref(0)
const orderPageNum = ref(1)
const acting = ref('')

async function loadOrders() {
  orderLoading.value = true
  try {
    const data = await adminApi.orderPage({
      status: orderFilter.value ?? undefined,
      pageNum: orderPageNum.value,
      pageSize
    })
    const page = normalizePage(data, pageSize)
    orders.value = page.records
    orderTotal.value = page.total
  } finally {
    orderLoading.value = false
  }
}

function switchOrderFilter(value) {
  orderFilter.value = value
  orderPageNum.value = 1
  loadOrders()
}

function changeOrderPage(page) {
  orderPageNum.value = page
  loadOrders()
}

async function handleOrder(order) {
  const isService = order.orderType === 2
  const action = isService ? '门店接单' : '商家发货'
  try {
    await ElMessageBox.confirm(
      `确定为订单 ${order.orderNo} 执行「${action}」吗？用户将实时收到进度推送。`,
      action,
      { type: 'warning', confirmButtonText: `确认${action}`, cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  acting.value = order.orderNo
  try {
    if (isService) await adminApi.accept(order.orderNo)
    else await adminApi.deliver(order.orderNo)
    ElMessage.success(`${action}成功，已推送订单进度`)
    loadOrders()
  } finally {
    acting.value = ''
  }
}

/* ---------- 评价回复 ---------- */
const reviewShopId = ref(null)
const reviewLoading = ref(false)
const reviews = ref([])
const reviewTotal = ref(0)
const reviewPageNum = ref(1)
const replyDrafts = reactive({})
const replying = ref(null)

async function loadReviews() {
  if (!reviewShopId.value) {
    reviews.value = []
    reviewTotal.value = 0
    return
  }
  reviewLoading.value = true
  try {
    const data = await adminApi.reviewPage({
      shopId: reviewShopId.value,
      pageNum: reviewPageNum.value,
      pageSize
    })
    const page = normalizePage(data, pageSize)
    reviews.value = page.records
    reviewTotal.value = page.total
    page.records.forEach((review) => {
      if (replyDrafts[review.id] === undefined) replyDrafts[review.id] = review.reply || ''
    })
  } finally {
    reviewLoading.value = false
  }
}

function switchReviewShop(id) {
  reviewShopId.value = id
  reviewPageNum.value = 1
  loadReviews()
}

function changeReviewPage(page) {
  reviewPageNum.value = page
  loadReviews()
}

async function submitReply(review) {
  const text = (replyDrafts[review.id] || '').trim()
  if (!text) {
    ElMessage.warning('请先填写回复内容')
    return
  }
  replying.value = review.id
  try {
    await reviewApi.reply(review.id, text)
    ElMessage.success(review.reply ? '回复已更新' : '回复已提交')
    await loadReviews()
  } finally {
    replying.value = null
  }
}

/* ---------- 用户管理与视角切换（Impersonation） ---------- */
const userLoading = ref(false)
const users = ref([])
const userTotal = ref(0)
const userPageNum = ref(1)
const userKeyword = ref('')
const impersonating = ref(null)

async function loadUsers() {
  userLoading.value = true
  try {
    const data = await adminApi.userPage({
      pageNum: userPageNum.value,
      pageSize,
      keyword: userKeyword.value.trim() || undefined
    })
    const page = normalizePage(data, pageSize)
    users.value = page.records
    userTotal.value = page.total
  } finally {
    userLoading.value = false
  }
}

function searchUsers() {
  userPageNum.value = 1
  loadUsers()
}

function changeUserPage(page) {
  userPageNum.value = page
  loadUsers()
}

/** 仅启用的普通用户可被模拟；管理员行与自身行禁用 */
function canImpersonate(row) {
  return row.role !== 'ADMIN' && row.status === 1 && String(row.id) !== String(auth.user?.id)
}

async function startImpersonation(row) {
  try {
    await ElMessageBox.confirm(
      `将以「${row.username}」${row.nickname ? `（${row.nickname}）` : ''} 的视角操作前台，期间操作归属该用户并记录审计日志，视角 15 分钟后自动失效。`,
      '切换为该用户视角',
      { type: 'warning', confirmButtonText: '切换视角', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  impersonating.value = row.id
  try {
    const data = await adminApi.impersonate(row.id)
    auth.startImpersonation(data)
    ElMessage.success(`已进入 ${data.user.username} 的视角`)
    router.push('/')
  } finally {
    impersonating.value = null
  }
}

onMounted(async () => {
  await loadShopOptions().catch(() => {})
  loadShops()
  loadServices()
  loadReviews()
  loadOrders()
  loadUsers()
})

// 看板指标全部取自已加载列表接口返回的真实 total，随筛选联动，不引入任何新接口
const metrics = computed(() => [
  { label: '门店总数', value: shopTotal.value, note: '含已停业门店' },
  { label: '当前门店服务', value: serviceTotal.value, note: '随门店选择变化' },
  {
    label: orderFilter.value === null ? '全部订单' : orderFilter.value === 10 ? '待处理订单' : '进行中订单',
    value: orderTotal.value,
    note: '随订单筛选变化'
  },
  { label: '当前门店评价', value: reviewTotal.value, note: '随门店选择变化' },
  { label: '注册用户', value: userTotal.value, note: '含管理员账号' }
])
</script>

<template>
  <div class="page">
    <div class="container">
      <header class="page-head">
        <div>
          <h1>商家工作台</h1>
          <p>门店与服务管理、订单处理、评价回复，一站式打理生意。</p>
        </div>
      </header>

      <div class="metric-strip">
        <div v-for="item in metrics" :key="item.note + item.label" class="metric-cell">
          <span class="metric-label">{{ item.label }}</span>
          <b class="metric-value num">{{ item.value }}</b>
          <span class="metric-note">{{ item.note }}</span>
        </div>
      </div>

      <div class="status-tabs admin-tabs">
        <button type="button" :class="{ active: activeTab === 'shops' }" @click="activeTab = 'shops'">
          门店与服务管理
        </button>
        <button type="button" :class="{ active: activeTab === 'orders' }" @click="activeTab = 'orders'">
          订单处理
        </button>
        <button type="button" :class="{ active: activeTab === 'reviews' }" @click="activeTab = 'reviews'">
          评价回复
        </button>
        <button type="button" :class="{ active: activeTab === 'users' }" @click="activeTab = 'users'">
          用户管理
        </button>
      </div>

      <!-- Tab 1：门店与服务管理 -->
      <section v-show="activeTab === 'shops'">
        <div class="admin-panel">
          <div class="panel-head">
            <h2>门店列表</h2>
            <div class="panel-tools">
              <form class="mini-search" @submit.prevent="searchShops">
                <input v-model="shopKeyword" type="search" placeholder="按门店名称搜索" />
                <el-button size="small" native-type="submit">搜索</el-button>
              </form>
              <el-button type="primary" size="small" @click="openShopDialog()">新增门店</el-button>
            </div>
          </div>
          <el-table v-loading="shopLoading" :data="shops" size="small" empty-text="暂无门店">
            <el-table-column label="门店" min-width="180">
              <template #default="{ row }">
                <strong class="cell-name">{{ row.shopName }}</strong>
                <span class="cell-sub">{{ row.city }}{{ row.district ? ` · ${row.district}` : '' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
            <el-table-column prop="contactPhone" label="联系电话" width="130" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain">
                  {{ row.status === 1 ? '营业中' : '已停业' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openShopDialog(row)">编辑</el-button>
                <el-button link type="primary" size="small" @click="switchServiceShop(row.id)">服务</el-button>
                <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small" @click="toggleShop(row)">
                  {{ row.status === 1 ? '停业' : '恢复营业' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="shopTotal > pageSize" class="pager">
            <el-pagination
              background
              layout="prev, pager, next, total"
              :total="shopTotal"
              :page-size="pageSize"
              :current-page="shopPageNum"
              @current-change="(p) => { shopPageNum = p; loadShops() }"
            />
          </div>
        </div>

        <div class="admin-panel">
          <div class="panel-head">
            <h2>服务项目</h2>
            <div class="panel-tools">
              <el-select
                :model-value="serviceShopId"
                size="small"
                placeholder="选择门店"
                style="width: 220px"
                @change="switchServiceShop"
              >
                <el-option v-for="shop in shopOptions" :key="shop.id" :label="shop.shopName" :value="shop.id" />
              </el-select>
              <el-button type="primary" size="small" :disabled="!serviceShopId" @click="openServiceDialog()">
                新增服务
              </el-button>
            </div>
          </div>
          <el-table v-loading="serviceLoading" :data="services" size="small" empty-text="该门店暂无服务项目">
            <el-table-column prop="itemName" label="服务名称" min-width="170" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="90" />
            <el-table-column label="价格" width="110">
              <template #default="{ row }">¥{{ money(row.price) }}</template>
            </el-table-column>
            <el-table-column label="时长" width="90">
              <template #default="{ row }">{{ row.durationMin }} 分钟</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain">
                  {{ row.status === 1 ? '已上架' : '已下架' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openServiceDialog(row)">编辑</el-button>
                <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small" @click="toggleService(row)">
                  {{ row.status === 1 ? '下架' : '上架' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="serviceTotal > pageSize" class="pager">
            <el-pagination
              background
              layout="prev, pager, next, total"
              :total="serviceTotal"
              :page-size="pageSize"
              :current-page="servicePageNum"
              @current-change="(p) => { servicePageNum = p; loadServices() }"
            />
          </div>
        </div>
      </section>

      <!-- Tab 2：订单处理 -->
      <section v-show="activeTab === 'orders'">
        <div class="admin-panel">
          <div class="panel-head">
            <h2>订单处理</h2>
            <div class="status-tabs inline-tabs">
              <button
                v-for="tab in orderTabs"
                :key="String(tab.value)"
                type="button"
                :class="{ active: orderFilter === tab.value }"
                @click="switchOrderFilter(tab.value)"
              >
                {{ tab.label }}
              </button>
            </div>
          </div>
          <div v-if="orderLoading" class="skeleton-block" style="height: 220px"></div>
          <el-empty v-else-if="!orders.length" description="当前没有需要处理的订单" :image-size="100" />
          <div v-else class="admin-order-list">
            <article v-for="order in orders" :key="order.orderNo" class="admin-order-card">
              <header class="admin-order-head">
                <div class="head-left">
                  <span class="order-no mono">{{ order.orderNo }}</span>
                  <el-tag size="small" effect="plain">{{ order.orderTypeDesc }}</el-tag>
                  <span class="order-time">{{ shortDate(order.createTime) }}</span>
                </div>
                <el-tag :type="orderStatus(order.status).tone" effect="plain">
                  {{ order.statusDesc || orderStatus(order.status).text }}
                </el-tag>
              </header>
              <div class="admin-order-body">
                <div class="order-meta">
                  <span>
                    用户：{{ order.username || '—' }}
                    <template v-if="order.nickname">（{{ order.nickname }}）</template>
                  </span>
                  <span>实付：¥{{ money(order.payAmount) }}</span>
                  <span v-if="order.appointmentTime">预约：{{ shortDate(order.appointmentTime) }}</span>
                  <span v-if="order.appointmentContact">
                    联系：{{ order.appointmentContact }} {{ order.appointmentPhone }}
                  </span>
                </div>
                <ul class="order-items">
                  <li v-for="item in order.items" :key="`${order.orderNo}-${item.itemId}`">
                    {{ item.itemName }} × {{ item.quantity }}
                  </li>
                </ul>
                <div class="order-action">
                  <el-button
                    v-if="order.status === 10"
                    type="primary"
                    size="small"
                    :loading="acting === order.orderNo"
                    @click="handleOrder(order)"
                  >
                    {{ order.orderType === 2 ? '门店接单' : '商家发货' }}
                  </el-button>
                  <span v-else class="done-hint">{{ order.orderType === 2 ? '已接单' : '已发货' }}</span>
                </div>
              </div>
            </article>
          </div>
          <div v-if="orderTotal > pageSize" class="pager">
            <el-pagination
              background
              layout="prev, pager, next, total"
              :total="orderTotal"
              :page-size="pageSize"
              :current-page="orderPageNum"
              @current-change="changeOrderPage"
            />
          </div>
        </div>
      </section>

      <!-- Tab 3：评价回复 -->
      <section v-show="activeTab === 'reviews'">
        <div class="admin-panel">
          <div class="panel-head">
            <h2>评价回复</h2>
            <el-select
              :model-value="reviewShopId"
              size="small"
              placeholder="选择门店"
              style="width: 220px"
              @change="switchReviewShop"
            >
              <el-option v-for="shop in shopOptions" :key="shop.id" :label="shop.shopName" :value="shop.id" />
            </el-select>
          </div>
          <div v-if="reviewLoading" class="skeleton-block" style="height: 220px"></div>
          <el-empty v-else-if="!reviews.length" description="该门店还没有评价" :image-size="100" />
          <div v-else class="review-list">
            <article v-for="review in reviews" :key="review.id" class="review-card">
              <header class="review-head">
                <strong class="review-user">
                  {{ review.username || '—' }}
                  <template v-if="review.nickname">（{{ review.nickname }}）</template>
                </strong>
                <el-rate :model-value="review.score" disabled size="small" />
                <span class="order-time">{{ shortDate(review.createTime) }}</span>
                <span v-if="review.orderNo" class="order-no mono">{{ review.orderNo }}</span>
              </header>
              <p class="review-content">{{ review.content }}</p>
              <div v-if="review.images?.length" class="review-images">
                <img v-for="(img, i) in review.images" :key="i" :src="img" alt="评价图片" />
              </div>
              <div v-if="review.reply" class="existing-reply">
                <strong>商家回复</strong>
                <p>{{ review.reply }}</p>
              </div>
              <div class="reply-box">
                <el-input
                  v-model="replyDrafts[review.id]"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-limit
                  :placeholder="review.reply ? '修改商家回复…' : '以商家身份回复这条评价…'"
                />
                <el-button
                  type="primary"
                  size="small"
                  :loading="replying === review.id"
                  @click="submitReply(review)"
                >
                  {{ review.reply ? '更新回复' : '提交回复' }}
                </el-button>
              </div>
            </article>
          </div>
          <div v-if="reviewTotal > pageSize" class="pager">
            <el-pagination
              background
              layout="prev, pager, next, total"
              :total="reviewTotal"
              :page-size="pageSize"
              :current-page="reviewPageNum"
              @current-change="changeReviewPage"
            />
          </div>
        </div>
      </section>

      <!-- Tab 4：用户管理与视角切换 -->
      <section v-show="activeTab === 'users'">
        <div class="admin-panel">
          <div class="panel-head">
            <h2>用户管理</h2>
            <div class="panel-tools">
              <form class="mini-search" @submit.prevent="searchUsers">
                <input v-model="userKeyword" type="search" placeholder="按用户名 / 昵称搜索" />
                <el-button size="small" native-type="submit">搜索</el-button>
              </form>
            </div>
          </div>
          <p class="users-hint">
            真实用户名仅管理端可见，手机号部分打码；「切换为该用户视角」仅对启用的普通用户开放，
            15 分钟有效并记录审计日志，禁止模拟管理员与自身。
          </p>
          <div v-if="userLoading" class="skeleton-block" style="height: 220px"></div>
          <el-empty v-else-if="!users.length" description="没有匹配的用户" :image-size="100" />
          <table v-else class="user-table">
            <thead>
              <tr>
                <th>userId</th>
                <th>用户名</th>
                <th>昵称</th>
                <th>手机号</th>
                <th>角色</th>
                <th>状态</th>
                <th>注册时间</th>
                <th>视角</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in users" :key="row.id">
                <td class="mono">{{ row.id }}</td>
                <td><strong>{{ row.username }}</strong></td>
                <td>{{ row.nickname || '—' }}</td>
                <td class="mono">{{ row.phone || '—' }}</td>
                <td>
                  <el-tag size="small" :type="row.role === 'ADMIN' ? 'warning' : 'info'" effect="plain">
                    {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
                  </el-tag>
                </td>
                <td>
                  <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'" effect="plain">
                    {{ row.status === 1 ? '启用' : '禁用' }}
                  </el-tag>
                </td>
                <td>{{ shortDate(row.createTime) }}</td>
                <td>
                  <el-button
                    v-if="canImpersonate(row)"
                    type="primary"
                    size="small"
                    plain
                    :loading="impersonating === row.id"
                    @click="startImpersonation(row)"
                  >
                    切换为该用户视角
                  </el-button>
                  <span v-else class="done-hint">
                    {{ row.role === 'ADMIN' ? '管理员不可模拟' : String(row.id) === String(auth.user?.id) ? '当前账号' : '已禁用' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="userTotal > pageSize" class="pager">
            <el-pagination
              background
              layout="prev, pager, next, total"
              :total="userTotal"
              :page-size="pageSize"
              :current-page="userPageNum"
              @current-change="changeUserPage"
            />
          </div>
        </div>
      </section>
    </div>

    <!-- 门店新增/编辑弹窗 -->
    <el-dialog
      v-model="shopDialog.visible"
      :title="shopDialog.editingId ? '编辑门店' : '新增门店'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-width="86px" size="default">
        <el-form-item label="门店名称" required>
          <el-input v-model="shopForm.shopName" maxlength="64" placeholder="如：洪兴洗车（花园路店）" />
        </el-form-item>
        <el-form-item label="城市" required>
          <el-input v-model="shopForm.city" maxlength="32" placeholder="如：郑州市" />
        </el-form-item>
        <el-form-item label="区县">
          <el-input v-model="shopForm.district" maxlength="32" placeholder="如：金水区" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="shopForm.address" maxlength="128" placeholder="街道门牌号" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="shopForm.contactPhone" maxlength="20" placeholder="门店电话" />
        </el-form-item>
        <el-form-item label="营业时间">
          <el-input v-model="shopForm.businessHours" maxlength="64" placeholder="如：08:00-20:00" />
        </el-form-item>
        <el-form-item label="门店简介">
          <el-input v-model="shopForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shopDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="shopDialog.saving" @click="saveShop">保存</el-button>
      </template>
    </el-dialog>

    <!-- 服务新增/编辑弹窗 -->
    <el-dialog
      v-model="serviceDialog.visible"
      :title="serviceDialog.editingId ? '编辑服务' : '新增服务'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-width="86px" size="default">
        <el-form-item label="所属门店" required>
          <el-select v-model="serviceForm.shopId" placeholder="选择门店" style="width: 100%">
            <el-option v-for="shop in shopOptions" :key="shop.id" :label="shop.shopName" :value="shop.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务名称" required>
          <el-input v-model="serviceForm.itemName" maxlength="64" placeholder="如：精致洗车（轿车）" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="serviceForm.category" filterable allow-create placeholder="选择或输入分类" style="width: 100%">
            <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格" required>
          <el-input-number v-model="serviceForm.price" :min="0.01" :precision="2" :step="1" style="width: 180px" />
        </el-form-item>
        <el-form-item label="原价">
          <el-input-number v-model="serviceForm.originalPrice" :min="0.01" :precision="2" :step="1" style="width: 180px" />
        </el-form-item>
        <el-form-item label="时长(分钟)">
          <el-input-number v-model="serviceForm.durationMin" :min="1" :step="5" style="width: 180px" />
        </el-form-item>
        <el-form-item label="服务描述">
          <el-input v-model="serviceForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="serviceDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="serviceDialog.saving" @click="saveService">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-head {
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

.metric-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin-top: 16px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.metric-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 10px 16px;
  border-right: 1px solid var(--line);
}

.metric-cell:last-child {
  border-right: 0;
}

.metric-label {
  font-size: 11px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-value {
  font-size: 22px;
  line-height: 1.2;
  font-weight: 600;
  color: var(--ink);
}

.metric-note {
  font-size: 10px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.admin-tabs {
  margin-bottom: 18px;
}

.inline-tabs {
  margin: 0;
}

.admin-panel {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: #fff;
  padding: 16px;
  margin-bottom: 18px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-head h2 {
  font-size: 15px;
  font-weight: 600;
}

.panel-tools {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.mini-search {
  display: flex;
  align-items: center;
  gap: 6px;
}

.mini-search input {
  height: 24px;
  width: 170px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: 4px;
  font-size: 12px;
  outline: none;
  background: var(--surface-soft);
}

.mini-search input:focus {
  border-color: var(--brand);
  background: #fff;
}

.cell-name {
  display: block;
  font-size: 13px;
}

.cell-sub {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--muted);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

.admin-order-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin-order-card {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  overflow: hidden;
}

.admin-order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 16px;
  background: var(--surface-soft);
  border-bottom: 1px solid var(--line);
}

.head-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
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

.admin-order-body {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr) 130px;
  gap: 14px;
  align-items: center;
  padding: 12px 16px;
}

.order-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--ink-soft);
}

.order-items {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 12px;
  color: var(--ink-soft);
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.order-action {
  display: flex;
  justify-content: flex-end;
}

.done-hint {
  font-size: 11px;
  color: var(--muted);
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.review-card {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 14px 16px;
}

.review-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.review-user {
  font-size: 13px;
  color: var(--ink);
}

.users-hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--muted);
}

.user-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.user-table th,
.user-table td {
  text-align: left;
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
  vertical-align: middle;
}

.user-table th {
  font-size: 12px;
  color: var(--muted);
  font-weight: 600;
  background: var(--surface-soft);
}

.user-table td.mono {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
}

.review-content {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink);
}

.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.review-images img {
  width: 72px;
  height: 72px;
  border-radius: 6px;
  object-fit: cover;
  border: 1px solid var(--line);
}

.existing-reply {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--surface-soft);
  border-left: 3px solid var(--brand);
}

.existing-reply strong {
  font-size: 11px;
  color: var(--brand-deep);
}

.existing-reply p {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ink-soft);
}

.reply-box {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-top: 12px;
}

.reply-box .el-input {
  flex: 1;
}

@media (max-width: 980px) {
  .metric-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .metric-cell:nth-child(3) {
    border-right: 0;
  }

  .metric-cell:nth-child(-n + 3) {
    border-bottom: 1px solid var(--line);
  }
}

@media (max-width: 760px) {
  .metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-cell:nth-child(3) {
    border-right: 1px solid var(--line);
  }

  .metric-cell:nth-child(-n + 3) {
    border-bottom: 0;
  }

  .metric-cell:nth-child(2n) {
    border-right: 0;
  }

  .metric-cell:nth-child(-n + 4) {
    border-bottom: 1px solid var(--line);
  }

  .admin-order-body {
    grid-template-columns: 1fr;
  }

  .order-action {
    justify-content: flex-start;
  }

  .reply-box {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>