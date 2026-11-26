<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const leaving = ref(false)

// 退出模拟：先调退出接口写审计日志（失败不阻塞恢复），再原样恢复管理员 Token 切回后台
async function exit() {
  if (leaving.value) return
  leaving.value = true
  try {
    await authApi.exitImpersonation().catch(() => {})
    auth.exitImpersonation()
    ElMessage.success('已退出模拟，恢复管理员身份')
    router.push('/admin')
  } finally {
    leaving.value = false
  }
}
</script>

<template>
  <!-- 模拟视角常驻横幅：不可关闭，仅能通过退出模拟移除 -->
  <div v-if="auth.isImpersonating" class="impersonation-banner" role="alert">
    <span class="banner-text">
      当前正在以
      <strong>{{ auth.user?.username }}</strong>
      <template v-if="auth.user?.nickname">（{{ auth.user.nickname }}）</template>
      视角操作 · 操作归属该用户并记录审计日志
    </span>
    <button type="button" class="banner-exit" :disabled="leaving" @click="exit">
      返回管理端 / 退出模拟
    </button>
  </div>
</template>

<style scoped>
.impersonation-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 3000;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 0 16px;
  background: var(--brand-deep, #0f766e);
  color: #fff;
  font-size: 13px;
}

.banner-text strong {
  font-weight: 700;
  margin: 0 2px;
}

.banner-exit {
  flex: none;
  border: 1px solid rgba(255, 255, 255, 0.7);
  background: transparent;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  padding: 5px 12px;
  border-radius: 6px;
  cursor: pointer;
}

.banner-exit:hover {
  background: rgba(255, 255, 255, 0.14);
}

.banner-exit:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>