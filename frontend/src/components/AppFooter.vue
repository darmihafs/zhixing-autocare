<script setup>
import { ref } from 'vue'

const year = new Date().getFullYear()
const showDev = ref(false)
</script>

<template>
  <footer class="site-footer">
    <div class="container footer-main">
      <div class="footer-brand">
        <div class="brand-line">
          <span class="mark">洪</span>
          <strong>洪兴洗车</strong>
        </div>
        <p>正品机油、轮胎与车载好物，附近门店保养洗车，到店免费安装。</p>
        <p class="fine">正品保障 · 明码标价 · 完成服务后可评价</p>
      </div>

      <div class="footer-col">
        <h4>逛一逛</h4>
        <router-link to="/products">养车商城</router-link>
        <router-link to="/shops">门店服务</router-link>
        <router-link to="/search">搜索商品与门店</router-link>
      </div>

      <div class="footer-col">
        <h4>我的养车</h4>
        <router-link to="/orders">我的订单</router-link>
        <router-link to="/reviews">我的评价</router-link>
        <router-link to="/profile">个人中心</router-link>
        <router-link to="/cart">购物车</router-link>
      </div>

      <div class="footer-col">
        <h4>购物保障</h4>
        <span>正品商品，现货当天发出</span>
        <span>到店服务项目明码标价</span>
        <span>专业技师施工，完工后确认</span>
        <span>完成订单后可发布图文评价</span>
      </div>
    </div>

    <div class="container footer-bottom">
      <span>© {{ year }} 洪兴洗车</span>
      <button type="button" class="dev-toggle" @click="showDev = !showDev">
        {{ showDev ? '收起开发者信息' : '开发者信息' }}
      </button>
    </div>

    <div v-if="showDev" class="container dev-panel">
      Spring Cloud Alibaba · Nacos · RocketMQ · Redis · Elasticsearch · WebSocket ·
      Gateway :8080 / Frontend :5173
    </div>
  </footer>
</template>

<style scoped>
.site-footer {
  margin-top: 34px;
  padding: 34px 0 18px;
  background: #fff;
  border-top: 1px solid var(--line);
}

.footer-main {
  display: grid;
  grid-template-columns: 1.6fr 0.7fr 0.8fr 1fr;
  gap: 34px;
}

.brand-line {
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--ink);
  font-size: 16px;
  font-weight: 600;
}

.mark {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 5px;
  background: var(--brand);
  color: #fff;
  font-weight: 600;
  font-size: 15px;
}

.footer-brand p {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.75;
  color: var(--muted);
  max-width: 320px;
}

.footer-brand .fine {
  margin-top: 4px;
  font-size: 11px;
  color: #a9b0ba;
}

.footer-col {
  display: flex;
  flex-direction: column;
  gap: 7px;
  font-size: 12px;
  color: var(--muted);
}

.footer-col h4 {
  margin: 0 0 3px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ink);
}

.footer-col a:hover {
  color: var(--brand);
}

.footer-bottom {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 26px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
  font-size: 11px;
  color: #a9b0ba;
}

.dev-toggle {
  margin-left: auto;
  border: 0;
  padding: 0;
  background: transparent;
  color: #c2c7cf;
  font-size: 11px;
  cursor: pointer;
}

.dev-toggle:hover {
  color: var(--muted);
}

.dev-panel {
  padding-top: 8px;
  font-size: 10px;
  line-height: 1.7;
  color: #c2c7cf;
}

@media (max-width: 900px) {
  .footer-main {
    grid-template-columns: 1fr 1fr;
    gap: 24px;
  }

  .footer-brand {
    grid-column: 1 / -1;
  }
}

@media (max-width: 560px) {
  .footer-bottom {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }

  .dev-toggle {
    margin-left: 0;
  }
}
</style>
