<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <span class="code">404</span>
    <h1>这条路暂时走不通</h1>
    <p>页面不存在，或者链接已经失效。</p>
    <div class="actions">
      <el-button type="primary" @click="router.push('/')">回到首页</el-button>
      <el-button @click="router.back()">返回上一页</el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  min-height: calc(100vh - 290px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  text-align: center;
}

.code {
  font-family: var(--font-mono);
  font-size: clamp(72px, 14vw, 140px);
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.08em;
  color: var(--brand);
}

h1 {
  margin-top: 10px;
  font-size: 28px;
  letter-spacing: -0.03em;
}

p {
  margin-top: 8px;
  font-size: 13px;
  color: var(--muted);
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 26px;
}
</style>
