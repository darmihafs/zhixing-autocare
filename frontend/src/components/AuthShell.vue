<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' }
})
</script>

<template>
  <div class="auth-shell">
    <section class="auth-visual">
      <router-link to="/" class="brand">
        <span class="mark">洪</span>
        <span class="brand-copy">
          <strong>洪兴洗车</strong>
          <small>汽车用品 · 到店服务</small>
        </span>
      </router-link>

      <div class="visual-copy">
        <h1>养车不用请假，<br />预约好再到店。</h1>
        <p>机油、轮胎与保养项目在线选好，附近门店到店施工，省去排队和反复询价。</p>
        <ul>
          <li><span>01</span> 正品商品，假一赔十</li>
          <li><span>02</span> 到店服务明码标价，预约时间可控</li>
          <li><span>03</span> 完成服务后可发布图文评价</li>
        </ul>
      </div>

      <div class="visual-photo">
        <img src="/images/shop/shop-2.jpg" alt="门店保养工位" />
      </div>

      <div class="visual-foot">
        <span>正品保障</span>
        <span>到店免费安装</span>
        <span>专业技师施工</span>
      </div>
    </section>

    <main class="auth-main">
      <router-link to="/" class="back-home">← 返回首页</router-link>
      <div class="form-card">
        <h2>{{ title }}</h2>
        <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
        <slot />
      </div>
      <p class="legal">支付为演示通道，不会产生真实扣款</p>
    </main>
  </div>
</template>

<style scoped>
.auth-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(460px, 0.95fr);
  background: var(--paper);
}

.auth-visual {
  display: flex;
  flex-direction: column;
  padding: 36px 48px 30px;
  background: var(--paper);
  border-right: 1px solid var(--line);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  width: fit-content;
}

.mark {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: var(--brand);
  color: #fff;
  font-weight: 600;
  font-size: 17px;
}

.brand-copy strong {
  display: block;
  font-size: 16px;
  color: var(--ink);
}

.brand-copy small {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  color: var(--muted);
}

.visual-copy {
  max-width: 480px;
  margin-top: 48px;
}

.visual-copy h1 {
  font-size: clamp(34px, 3.6vw, 48px);
  line-height: 1.25;
  letter-spacing: -0.03em;
  font-weight: 600;
  color: var(--ink);
}

.visual-copy > p {
  margin-top: 16px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--ink-soft);
}

.visual-copy ul {
  margin: 24px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.visual-copy li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--ink-soft);
}

.visual-copy li span {
  font-size: 10px;
  color: var(--brand);
}

.visual-photo {
  margin-top: 28px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--line);
  max-height: 240px;
}

.visual-photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.visual-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: auto;
  padding-top: 20px;
  font-size: 11px;
  color: var(--muted);
}

.auth-main {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 40px;
  position: relative;
  background: #fff;
}

.back-home {
  position: absolute;
  top: 28px;
  right: 34px;
  font-size: 12px;
  color: var(--muted);
}

.back-home:hover {
  color: var(--brand);
}

.form-card {
  width: min(380px, 100%);
  margin: 0 auto;
}

.form-card h2 {
  font-size: 26px;
  letter-spacing: -0.02em;
  font-weight: 600;
}

.subtitle {
  margin-top: 7px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--muted);
  margin-bottom: 22px;
}

.legal {
  margin-top: 26px;
  text-align: center;
  font-size: 10px;
  color: #b0b6bf;
}

@media (max-width: 980px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-visual {
    min-height: 0;
    padding: 24px 24px 20px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .visual-copy {
    margin-top: 24px;
  }

  .visual-copy h1 {
    font-size: 30px;
  }

  .visual-copy > p,
  .visual-copy ul,
  .visual-photo,
  .visual-foot {
    display: none;
  }

  .auth-main {
    padding: 30px 22px 44px;
  }

  .back-home {
    position: static;
    display: inline-block;
    margin-bottom: 20px;
    text-align: right;
    align-self: flex-end;
  }
}

@media (max-width: 560px) {
  .visual-copy h1 {
    font-size: 26px;
  }

  .form-card h2 {
    font-size: 23px;
  }
}
</style>
