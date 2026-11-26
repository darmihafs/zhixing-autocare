<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Iphone, Lock, User } from '@element-plus/icons-vue'
import AuthShell from '../components/AuthShell.vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  phone: ''
})

function validateConfirm(rule, value, callback) {
  if (!value) callback(new Error('请再次输入密码'))
  else if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]{4,20}$/,
      message: '用户名须为 4~20 位字母、数字或下划线',
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度须为 6~20 位', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirm, trigger: 'blur' }],
  phone: [
    {
      pattern: /^$|^1[3-9]\d{9}$/,
      message: '手机号格式不正确',
      trigger: 'blur'
    }
  ]
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.register({
      username: form.username.trim(),
      password: form.password,
      nickname: form.nickname.trim() || undefined,
      phone: form.phone.trim() || undefined
    })
    router.push({
      path: '/login',
      query: {
        username: form.username.trim(),
        registered: 1,
        ...(route.query.redirect ? { redirect: route.query.redirect } : {})
      }
    })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell
    title="创建养车账号"
    subtitle="一个账号，完成商品购买、门店服务与评价记录。"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @submit.prevent="submit"
    >
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" :prefix-icon="User" placeholder="4~20 位字母、数字或下划线" />
      </el-form-item>

      <div class="two-cols">
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" :prefix-icon="Lock" type="password" show-password placeholder="6~20 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            :prefix-icon="Lock"
            type="password"
            show-password
            placeholder="再次输入密码"
          />
        </el-form-item>
      </div>

      <div class="two-cols">
        <el-form-item label="昵称（选填）" prop="nickname">
          <el-input v-model="form.nickname" :prefix-icon="User" placeholder="最多 50 字" maxlength="50" />
        </el-form-item>
        <el-form-item label="手机号（选填）" prop="phone">
          <el-input v-model="form.phone" :prefix-icon="Iphone" placeholder="11 位手机号" maxlength="11" />
        </el-form-item>
      </div>

      <el-button type="primary" class="submit-button" :loading="loading" @click="submit">
        注册并返回登录
      </el-button>
    </el-form>

    <p class="hint">注册后即可下单、预约门店服务并发表评价。</p>

    <p class="switch-line">
      已有账号？
      <router-link :to="{ path: '/login', query: route.query.redirect ? { redirect: route.query.redirect } : {} }">
        返回登录
      </router-link>
    </p>
  </AuthShell>
</template>

<style scoped>
.two-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.submit-button {
  width: 100%;
  height: 44px;
  margin-top: 8px;
  border-radius: 6px;
  font-size: 14px;
}

.hint {
  margin-top: 14px;
  font-size: 10px;
  line-height: 1.6;
  color: var(--muted);
}

.switch-line {
  margin-top: 20px;
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

@media (max-width: 460px) {
  .two-cols {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
