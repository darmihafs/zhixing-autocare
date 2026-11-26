import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api',
  timeout: 12000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('hc_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code !== 'undefined') {
      if (body.code === 200) return body.data
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || `业务码 ${body.code}`))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const method = error.config?.method?.toUpperCase() || 'REQUEST'
    const url = error.config?.url || ''
    console.error(`[HTTP] ${method} ${url}`, {
      status,
      code: error.code,
      message: error.message,
      response: error.response?.data
    })
    if (status === 401) {
      // 模拟视角 Token 过期：恢复原管理员会话回后台，无需重新登录
      let snap = null
      try {
        snap = JSON.parse(sessionStorage.getItem('hc_impersonation') || 'null')
      } catch {
        snap = null
      }
      localStorage.removeItem('hc_token')
      localStorage.removeItem('hc_user')
      sessionStorage.removeItem('hc_impersonation')
      if (snap && snap.adminToken) {
        localStorage.setItem('hc_token', snap.adminToken)
        if (snap.adminUser) localStorage.setItem('hc_user', JSON.stringify(snap.adminUser))
        window.location.href = '/admin'
      } else {
        const current = `${window.location.pathname}${window.location.search}`
        if (!window.location.pathname.startsWith('/login')) {
          window.location.href = `/login?redirect=${encodeURIComponent(current)}`
        }
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else if (status === 403) {
      ElMessage.error('没有权限执行此操作')
    } else if (status === 429) {
      ElMessage.error('操作太频繁，请稍后再试')
    } else if (status && status >= 500) {
      ElMessage.error('服务暂时不可用，请稍后重试')
    } else if (status) {
      const message = error.response?.data?.code ? error.response?.data?.message : ''
      ElMessage.error(message || '请求未完成，请稍后重试')
    } else {
      ElMessage.error('网络连接失败，请检查网络后重试')
    }
    return Promise.reject(error)
  }
)

export default http
