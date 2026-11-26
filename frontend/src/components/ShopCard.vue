<script setup>
import { Location, Phone, StarFilled } from '@element-plus/icons-vue'
import { resolveShopImage } from '../utils/format'

defineProps({
  shop: { type: Object, required: true },
  compact: { type: Boolean, default: false }
})

function safeHighlight(text) {
  return String(text || '').replace(/<(?!\/?em>)[^>]*>/g, '')
}

function fallback(event) {
  event.target.src = '/images/shop/shop-1.jpg'
}
</script>

<template>
  <article class="shop-card" :class="{ compact }">
    <router-link :to="`/shops/${shop.id}`" class="shop-link">
      <div class="cover">
        <img :src="resolveShopImage(shop)" :alt="shop.shopName" loading="lazy" @error="fallback" />
        <span class="status" :class="{ closed: shop.status === 0 }">
          {{ shop.status === 0 ? '休息中' : '营业中' }}
        </span>
      </div>
      <div class="content">
        <div class="head">
          <h3 v-if="shop.highlightName" v-html="safeHighlight(shop.highlightName)"></h3>
          <h3 v-else>{{ shop.shopName }}</h3>
          <span class="rating">
            <el-icon><StarFilled /></el-icon>
            {{ shop.score }}
          </span>
        </div>
        <p class="address">
          <el-icon><Location /></el-icon>
          <span>{{ shop.district }} · {{ shop.address }}</span>
        </p>
        <p v-if="shop.highlightDescription" class="desc" v-html="safeHighlight(shop.highlightDescription)"></p>
        <p v-else class="desc">{{ shop.description }}</p>
        <div class="foot">
          <span class="hours">{{ shop.businessHours }}</span>
          <span class="phone">
            <el-icon><Phone /></el-icon>
            {{ shop.contactPhone }}
          </span>
          <span class="sales">已服务 {{ shop.sales }} 次</span>
        </div>
      </div>
    </router-link>
  </article>
</template>

<style scoped>
.shop-card {
  /* 作为网格子项时允许收缩，避免 nowrap 描述文本把卡片撑出视口 */
  min-width: 0;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.shop-card:hover {
  border-color: var(--line-strong);
  box-shadow: var(--shadow-sm);
}

.shop-link {
  display: flex;
  gap: 14px;
  padding: 12px;
}

.cover {
  position: relative;
  flex: none;
  width: 136px;
  height: 104px;
  border-radius: 6px;
  overflow: hidden;
  background: var(--surface-soft);
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.status {
  position: absolute;
  left: 6px;
  top: 6px;
  padding: 1px 5px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--success);
  font-size: 10px;
}

.status.closed {
  color: var(--muted);
}

.content {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}

h3 {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  line-height: 1.45;
  font-weight: 600;
  color: var(--ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

h3 :deep(em) {
  font-style: normal;
  color: var(--brand);
}

.rating {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}

.rating .el-icon {
  color: var(--brand);
  font-size: 12px;
}

.address,
.desc {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 11px;
  color: var(--muted);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.address span {
  overflow: hidden;
  text-overflow: ellipsis;
}

.address .el-icon {
  flex: none;
  font-size: 12px;
}

.desc :deep(em) {
  font-style: normal;
  color: var(--brand-deep);
}

.foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: auto;
  padding-top: 7px;
  font-size: 11px;
  color: var(--muted);
}

.hours {
  color: var(--ink-soft);
}

.phone {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.sales {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.compact {
  border: 0;
  border-top: 1px solid var(--line);
  border-radius: 0;
}

.compact:hover {
  box-shadow: none;
  background: var(--surface-soft);
}

.compact .shop-link {
  padding: 10px 12px;
}

.compact .cover {
  width: 88px;
  height: 72px;
}

.compact .desc,
.compact .foot {
  display: none;
}

.compact h3 {
  -webkit-line-clamp: 1;
}

@media (max-width: 560px) {
  .shop-link {
    gap: 10px;
    padding: 10px;
  }

  .cover {
    width: 104px;
    height: 88px;
  }

  .foot {
    gap: 8px;
  }

  .sales {
    display: none;
  }
}
</style>
