<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock, Location, Phone, Star, StarFilled } from '@element-plus/icons-vue'
import { followApi, orderApi, shopApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { normalizePage, resolveServiceImage, resolveShopImage } from '../utils/format'
import { waitForOrderCreated } from '../utils/order'
import ReviewPanel from '../components/ReviewPanel.vue'
import PriceText from '../components/PriceText.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(true)
const shop = ref(null)
const services = ref([])

const followed = ref(false)
const followBusy = ref(false)

const bookingOpen = ref(false)
const bookingService = ref(null)
const bookingSubmitting = ref(false)
const bookingCreatingOpen = ref(false)
const bookingOrderNo = ref('')
const bookingState = ref({ state: 'QUEUED', message: '' })
const bookingForm = reactive({
  date: '',
  time: '',
  contact: '',
  phone: '',
  remark: ''
})

function fallbackCover(event) {
  event.target.src = '/images/shop/shop-1.jpg'
}

const timeSlots = computed(() => {
  const fallback = ['09:00', '09:30', '10:00', '10:30', '11:00', '14:00', '14:30', '15:00', '15:30', '16:00', '16:30']
  const match = String(shop.value?.businessHours || '').match(/(\d{1,2}):(\d{2})\s*[-~至]\s*(\d{1,2}):(\d{2})/)
  if (!match) return fallback
  const start = Number(match[1]) * 60 + Number(match[2])
  const end = Number(match[3]) * 60 + Number(match[4])
  const slots = []
  for (let minute = start; minute + 30 <= end; minute += 30) {
    slots.push(`${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`)
  }
  return slots.length ? slots : fallback
})

onMounted(async () => {
  try {
    const [shopData, servicePage] = await Promise.all([
      shopApi.detail(route.params.id),
      shopApi.services({ pageNum: 1, pageSize: 20, shopId: route.params.id })
    ])
    shop.value = shopData
    services.value = normalizePage(servicePage, 20).records
    if (auth.isLoggedIn) {
      try {
        followed.value = Boolean(await followApi.status(1, shop.value.id))
      } catch {
        followed.value = false
      }
    }
  } finally {
    loading.value = false
  }
  await nextTick()
  handleQueryAction()
})

watch(
  () => route.fullPath,
  async () => {
    if (loading.value || !shop.value) return
    await nextTick()
    handleQueryAction()
  }
)

function handleQueryAction() {
  if (route.hash === '#shop-reviews') {
    document.getElementById('shop-reviews')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
  if (route.query.book !== '1') return
  const targetId = route.query.serviceId
  const target = targetId
    ? services.value.find((service) => String(service.id) === String(targetId))
    : services.value[0]
  if (!target) return
  if (shop.value?.status === 0) {
    ElMessage.info('门店当前休息中，可先浏览服务，营业后再预约')
    return
  }
  openBooking(target)
}

function todayString() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function disabledDate(date) {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  const max = new Date(start)
  max.setDate(max.getDate() + 30)
  return date < start || date > max
}

function openBooking(service) {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  bookingService.value = service
  const today = todayString()
  const now = new Date()
  const nextSlot = timeSlots.value.find((slot) => new Date(`${today}T${slot}:00`) > now)
  if (nextSlot) {
    bookingForm.date = today
    bookingForm.time = nextSlot
  } else {
    const tomorrow = new Date(now)
    tomorrow.setDate(tomorrow.getDate() + 1)
    bookingForm.date = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, '0')}-${String(tomorrow.getDate()).padStart(2, '0')}`
    bookingForm.time = timeSlots.value[0] || '09:00'
  }
  bookingForm.contact = auth.user?.nickname || auth.displayName || ''
  bookingForm.phone = auth.user?.phone || ''
  bookingForm.remark = ''
  bookingOpen.value = true
}

async function submitBooking() {
  if (!bookingService.value || !shop.value) return
  if (!bookingForm.date || !bookingForm.time) {
    ElMessage.warning('请选择到店日期和时间')
    return
  }
  const appointmentTime = `${bookingForm.date}T${bookingForm.time}:00`
  if (new Date(appointmentTime) <= new Date()) {
    ElMessage.warning('预约时间必须晚于当前时间')
    return
  }
  if (!bookingForm.contact.trim()) {
    ElMessage.warning('请填写预约联系人')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(bookingForm.phone)) {
    ElMessage.warning('请填写正确的联系电话')
    return
  }
  bookingSubmitting.value = true
  try {
    const result = await orderApi.create({
      orderType: 2,
      shopId: shop.value.id,
      appointmentTime,
      appointmentContact: bookingForm.contact.trim(),
      appointmentPhone: bookingForm.phone,
      items: [{ serviceItemId: bookingService.value.id, quantity: 1 }],
      remark: bookingForm.remark.trim() || undefined
    })
    bookingOrderNo.value = result.orderNo
    bookingState.value = { state: result.state || 'QUEUED', message: result.message || '' }
    bookingOpen.value = false
    bookingCreatingOpen.value = true
    const finalState = await waitForOrderCreated(result.orderNo, (state) => {
      bookingState.value = state
    })
    if (finalState?.state === 'CREATED') {
      ElMessage.success('预约订单创建成功，正在进入支付')
      bookingCreatingOpen.value = false
      router.push(`/pay/${result.orderNo}`)
    }
  } catch {
    /* 拦截器已提示具体原因 */
  } finally {
    bookingSubmitting.value = false
  }
}

function goBookingOrder() {
  bookingCreatingOpen.value = false
  if (bookingOrderNo.value) router.push(`/orders/${bookingOrderNo.value}`)
}

async function toggleFollow() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!shop.value || followBusy.value) return
  followBusy.value = true
  try {
    if (followed.value) {
      await followApi.unfollow(1, shop.value.id)
      followed.value = false
      ElMessage.success('已取消关注门店')
    } else {
      await followApi.follow(1, shop.value.id)
      followed.value = true
      ElMessage.success('已关注门店，可在个人中心查看')
    }
  } catch {
    /* 拦截器已提示具体原因 */
  } finally {
    followBusy.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="container">
      <div v-if="loading" class="loading">
        <div class="skeleton-block hero-skeleton"></div>
        <div class="skeleton-block list-skeleton"></div>
      </div>

      <template v-else-if="shop">
        <nav class="breadcrumb">
          <router-link to="/">首页</router-link>
          <span>/</span>
          <router-link to="/shops">门店服务</router-link>
          <span>/</span>
          <span>{{ shop.shopName }}</span>
        </nav>

        <section class="shop-hero">
          <div class="shop-cover">
            <img :src="resolveShopImage(shop)" :alt="shop.shopName" @error="fallbackCover" />
            <span class="cover-status" :class="{ closed: shop.status === 0 }">
              {{ shop.status === 0 ? '休息中' : '营业中' }}
            </span>
          </div>
          <div class="hero-body">
            <div class="hero-main">
              <div class="tags">
                <span class="chip">{{ shop.city }} · {{ shop.district }}</span>
                <span class="chip">{{ shop.businessHours }}</span>
              </div>
              <h1>{{ shop.shopName }}</h1>
              <p>{{ shop.description }}</p>
              <div class="rating-row">
                <span class="rating-big">
                  <el-icon><StarFilled /></el-icon>
                  {{ shop.score }}
                </span>
                <span class="rating-label">门店评分</span>
                <span class="divider"></span>
                <span class="rating-label">已服务 {{ shop.sales }} 次</span>
              </div>
            </div>

            <aside class="hero-side">
              <div class="contact-row">
                <el-icon><Location /></el-icon>
                <div>
                  <span>门店地址</span>
                  <strong>{{ shop.province }}{{ shop.city }}{{ shop.district }}{{ shop.address }}</strong>
                </div>
              </div>
              <div class="contact-row">
                <el-icon><Clock /></el-icon>
                <div>
                  <span>营业时间</span>
                  <strong>{{ shop.businessHours }}</strong>
                </div>
              </div>
              <div class="contact-row">
                <el-icon><Phone /></el-icon>
                <div>
                  <span>联系电话</span>
                  <strong>{{ shop.contactPhone }}</strong>
                </div>
              </div>
              <div class="hero-actions">
                <button
                  type="button"
                  class="follow-button"
                  :class="{ active: followed }"
                  :disabled="followBusy"
                  @click="toggleFollow"
                >
                  <el-icon>
                    <StarFilled v-if="followed" />
                    <Star v-else />
                  </el-icon>
                  {{ followed ? '已关注' : '关注门店' }}
                </button>
                <a :href="`tel:${shop.contactPhone}`" class="call-button">
                  <el-icon><Phone /></el-icon> 电话咨询
                </a>
              </div>
            </aside>
          </div>
        </section>

        <div v-if="shop.status === 0" class="closed-notice">
          门店当前休息中，服务项目暂不可预约，可先浏览或电话咨询。
        </div>

        <section class="service-section">
          <div class="section-head">
            <div>
              <h2>到店服务项目</h2>
              <p>选好项目与到店时间，在线提交预约订单并完成支付，到店直接服务。</p>
            </div>
            <span class="chip">{{ services.length }} 个项目</span>
          </div>

          <div v-if="!services.length" class="empty-services">
            该门店暂未上架服务项目。
          </div>
          <div v-else class="service-grid">
            <article
              v-for="service in services"
              :key="service.id"
              class="service-card"
              :class="{ highlighted: String(service.id) === String(route.query.serviceId) }"
            >
              <div class="service-media">
                <img :src="resolveServiceImage(service)" :alt="service.itemName" loading="lazy" />
                <span class="service-cat">{{ service.category }}</span>
              </div>
              <div class="service-content">
                <div class="service-top">
                  <h3>{{ service.itemName }}</h3>
                  <span class="duration">{{ service.durationMin }} 分钟</span>
                </div>
                <p>{{ service.description }}</p>
                <div class="service-foot">
                  <div class="service-price">
                    <PriceText :value="service.price" />
                    <s v-if="service.originalPrice">¥{{ service.originalPrice }}</s>
                  </div>
                  <div class="service-actions">
                    <span class="service-sales">已售 {{ service.sales }}</span>
                    <el-button
                      type="primary"
                      size="small"
                      :disabled="shop.status === 0"
                      @click="openBooking(service)"
                    >
                      预约
                    </el-button>
                  </div>
                </div>
              </div>
            </article>
          </div>
        </section>

        <div id="shop-reviews">
          <ReviewPanel
            v-if="shop.id"
            :item-id="shop.id"
            :shop-id="shop.id"
            title="门店评价"
          />
        </div>

        <el-dialog
          v-model="bookingOpen"
          :title="`预约服务 · ${bookingService?.itemName || ''}`"
          width="560px"
          :close-on-click-modal="false"
        >
          <div v-if="bookingService" class="booking-summary">
            <img :src="resolveServiceImage(bookingService)" :alt="bookingService.itemName" />
            <div>
              <strong>{{ bookingService.itemName }}</strong>
              <span>{{ bookingService.category }} · 约 {{ bookingService.durationMin }} 分钟</span>
              <PriceText :value="bookingService.price" class="booking-price" />
            </div>
          </div>

          <el-form label-position="top" class="booking-form">
            <div class="booking-grid">
              <el-form-item label="到店日期">
                <el-date-picker
                  v-model="bookingForm.date"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="选择日期"
                  :disabled-date="disabledDate"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item label="到店时间">
                <el-select v-model="bookingForm.time" placeholder="选择时间" style="width: 100%">
                  <el-option v-for="slot in timeSlots" :key="slot" :label="slot" :value="slot" />
                </el-select>
              </el-form-item>
              <el-form-item label="联系人">
                <el-input v-model="bookingForm.contact" maxlength="50" placeholder="到店联系人" />
              </el-form-item>
              <el-form-item label="联系电话">
                <el-input v-model="bookingForm.phone" maxlength="11" placeholder="用于门店联系确认" />
              </el-form-item>
            </div>
            <el-form-item label="给门店的备注（选填）">
              <el-input
                v-model="bookingForm.remark"
                type="textarea"
                :rows="2"
                maxlength="255"
                show-word-limit
                placeholder="例如：2019 款大众朗逸 1.4T，需要更换机油机滤"
              />
            </el-form-item>
          </el-form>

          <div class="booking-total">
            <span>预约金额</span>
            <strong>¥{{ bookingService?.price || '0.00' }}</strong>
          </div>
          <template #footer>
            <el-button @click="bookingOpen = false">取消</el-button>
            <el-button type="primary" :loading="bookingSubmitting" @click="submitBooking">
              提交预约并支付
            </el-button>
          </template>
        </el-dialog>

        <el-dialog
          v-model="bookingCreatingOpen"
          title="预约订单处理中"
          width="440px"
          :close-on-click-modal="false"
          :show-close="bookingState.state !== 'QUEUED'"
        >
          <div class="creating-state">
            <div v-if="bookingState.state === 'QUEUED'" class="pulse"><span></span><span></span><span></span></div>
            <div v-else-if="bookingState.state === 'CREATED'" class="state-icon ok">✓</div>
            <div v-else class="state-icon fail">!</div>
            <div>
              <strong>
                {{
                  bookingState.state === 'QUEUED'
                    ? '预约请求已入队'
                    : bookingState.state === 'CREATED'
                      ? '预约订单创建成功'
                      : '预约订单创建失败'
                }}
              </strong>
              <p>
                {{
                  bookingState.state === 'QUEUED'
                    ? '正在创建订单，请稍候…'
                    : bookingState.message || '可在订单列表查看处理结果'
                }}
              </p>
              <span class="mono order-no">{{ bookingOrderNo }}</span>
            </div>
          </div>
          <template #footer>
            <el-button v-if="bookingState.state !== 'QUEUED'" @click="goBookingOrder">查看订单</el-button>
          </template>
        </el-dialog>
      </template>

      <el-empty v-else description="门店不存在或已下线" :image-size="120" />
    </div>
  </div>
</template>

<style scoped>
.breadcrumb {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
  font-size: 12px;
  color: var(--muted);
}

.breadcrumb a:hover {
  color: var(--brand);
}

.shop-hero {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  overflow: hidden;
}

.shop-cover {
  position: relative;
  height: 190px;
  background: var(--surface-soft);
}

.shop-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-status {
  position: absolute;
  left: 14px;
  top: 14px;
  padding: 2px 8px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.94);
  color: var(--success);
  font-size: 11px;
}

.cover-status.closed {
  color: var(--muted);
}

.hero-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
}

.hero-main {
  padding: 20px 24px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.hero-main h1 {
  margin-top: 10px;
  font-size: 24px;
  line-height: 1.35;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.hero-main > p {
  margin-top: 7px;
  max-width: 640px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--muted);
}

.rating-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.rating-big {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 19px;
  font-weight: 600;
  color: var(--brand);
}

.rating-label {
  font-size: 11px;
  color: var(--muted);
}

.divider {
  width: 1px;
  height: 11px;
  background: var(--line);
}

.hero-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px 22px;
  border-left: 1px solid var(--line);
  background: var(--surface-soft);
}

.contact-row {
  display: flex;
  gap: 9px;
  align-items: flex-start;
}

.contact-row .el-icon {
  flex: none;
  margin-top: 2px;
  color: var(--muted);
  font-size: 13px;
}

.contact-row span {
  display: block;
  font-size: 10px;
  color: var(--muted);
}

.contact-row strong {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.55;
  color: var(--ink-soft);
}

.hero-actions {
  display: flex;
  gap: 8px;
  margin-top: auto;
  padding-top: 10px;
}

.follow-button {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 34px;
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  background: #fff;
  color: var(--ink-soft);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}

.follow-button:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.follow-button.active {
  border-color: #f8d3c8;
  background: var(--brand-soft);
  color: var(--brand-deep);
}

.call-button {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 34px;
  border-radius: 6px;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
}

.call-button:hover {
  background: #ef5a35;
}

.service-section {
  margin-top: 28px;
}

.closed-notice {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 6px;
  border: 1px solid #f2ddc4;
  background: #fdf7ef;
  color: #8a5a12;
  font-size: 12px;
  line-height: 1.7;
}

#shop-reviews {
  scroll-margin-top: 80px;
}

.section-head {
  margin-bottom: 14px;
}

.section-head h2 {
  margin-top: 0;
  font-size: 21px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.section-head p {
  margin-top: 5px;
  font-size: 12px;
  color: var(--muted);
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.service-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: #fff;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.service-card:hover {
  border-color: var(--line-strong);
  box-shadow: var(--shadow-sm);
}

.service-card.highlighted {
  border-color: #f2b9aa;
  box-shadow: 0 0 0 2px var(--brand-soft);
}

.service-media {
  position: relative;
  aspect-ratio: 16 / 10;
  background: var(--surface-soft);
}

.service-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.service-cat {
  position: absolute;
  left: 8px;
  top: 8px;
  padding: 2px 6px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--ink-soft);
  font-size: 10px;
}

.service-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 11px 13px 13px;
}

.service-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.duration {
  flex: none;
  padding-top: 3px;
  font-size: 10px;
  color: var(--muted);
}

.service-card h3 {
  min-height: 40px;
  font-size: 14px;
  line-height: 1.45;
  font-weight: 500;
  color: var(--ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.service-card p {
  margin-top: 4px;
  min-height: 34px;
  font-size: 11px;
  line-height: 1.65;
  color: var(--muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.service-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
  padding-top: 9px;
  border-top: 1px solid var(--line);
}

.service-price {
  display: flex;
  align-items: baseline;
  gap: 5px;
  font-size: 19px;
}

.service-price s {
  font-size: 10px;
  color: #b0b6bf;
}

.service-sales {
  font-size: 10px;
  color: var(--muted);
}

.service-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.booking-summary {
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 6px;
  background: var(--surface-soft);
  border: 1px solid var(--line);
}

.booking-summary img {
  width: 68px;
  height: 68px;
  border-radius: 6px;
  object-fit: cover;
  background: #eee;
}

.booking-summary strong {
  display: block;
  font-size: 13px;
  line-height: 1.45;
  font-weight: 500;
}

.booking-summary span {
  display: block;
  margin-top: 3px;
  font-size: 11px;
  color: var(--muted);
}

.booking-price {
  display: block;
  margin-top: 6px;
  font-size: 16px;
}

.booking-form {
  margin-top: 18px;
}

.booking-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;
}

.booking-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 14px;
  border-top: 1px dashed var(--line);
  font-size: 12px;
  color: var(--muted);
}

.booking-total strong {
  font-size: 24px;
  color: var(--brand-deep);
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

.empty-services {
  padding: 30px;
  text-align: center;
  color: var(--muted);
  font-size: 13px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.hero-skeleton {
  height: 260px;
}

.list-skeleton {
  height: 220px;
  margin-top: 20px;
}

@media (max-width: 960px) {
  .hero-body {
    grid-template-columns: 1fr;
  }

  .hero-side {
    border-left: 0;
    border-top: 1px solid var(--line);
  }

  .service-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .shop-cover {
    height: 150px;
  }

  .hero-main {
    padding: 16px;
  }

  .hero-side {
    padding: 16px;
  }

  .service-grid {
    grid-template-columns: 1fr;
  }

  .booking-grid {
    grid-template-columns: 1fr;
  }
}
</style>
