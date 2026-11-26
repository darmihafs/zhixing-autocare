<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import AuthShell from '../components/AuthShell.vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(() => {
  if (route.query.registered) {
    form.username = String(route.query.username || '')
    ElMessage.success('注册成功，请使用新账号登录')
  }
})

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.login({ username: form.username.trim(), password: form.password })
    ElMessage.success(`欢迎回来，${auth.displayName}`)
    // 统一登录入口：按角色自动分流（管理员进后台，普通用户进首页）；redirect 仅接受站内安全路径
    const raw = String(route.query.redirect || '')
    const safe =
      raw.startsWith('/') && !raw.startsWith('//') && !raw.startsWith('/login') && !raw.startsWith('/register')
        ? raw
        : ''
    router.replace(auth.isAdmin ? safe || '/admin' : safe || '/')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell
    title="登录洪兴洗车"
    subtitle="登录后即可下单、预约门店服务与发表评价。"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="submit">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" :prefix-icon="User" placeholder="请输入用户名" autocomplete="username" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          :prefix-icon="Lock"
          type="password"
          show-password
          placeholder="请输入密码"
          autocomplete="current-password"
          @keyup.enter="submit"
        />
      </el-form-item>

      <el-button type="primary" class="submit-button" :loading="loading" @click="submit">
        登录
      </el-button>
    </el-form>

    <p class="switch-line">
      还没有账号？
      <router-link :to="{ path: '/register', query: route.query.redirect ? { redirect: route.query.redirect } : {} }">
        立即注册
      </router-link>
    </p>
  </AuthShell>
</template>

<style scoped>
.submit-button {
  width: 100%;
  height: 44px;
  margin-top: 4px;
  border-radius: 6px;
  font-size: 14px;
}

.switch-line {
  margin-top: 22px;
  text-align: center;
  font-size: 12px;
  color: var(--muted);
}

.switch-line a {
  font-weight: 700;
  color: var(--brand-deep);
}

.switch-line a:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
