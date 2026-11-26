import { defineStore } from 'pinia'
import { authApi } from '../api'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem('hc_user') || 'null')
  } catch {
    return null
  }
}

function readImpersonation() {
  try {
    return JSON.parse(sessionStorage.getItem('hc_impersonation') || 'null')
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('hc_token') || '',
    user: readUser(),
    // 模拟视角快照：{ adminToken, adminUser, expiresAt }；仅 sessionStorage，关标签即失效
    impersonation: readImpersonation()
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    isAdmin: (state) => state.user?.role === 'ADMIN',
    isImpersonating: (state) => Boolean(state.impersonation),
    displayName: (state) => state.user?.nickname || state.user?.username || '养车用户'
  },
  actions: {
    persist() {
      if (this.token) localStorage.setItem('hc_token', this.token)
      else localStorage.removeItem('hc_token')
      if (this.user) localStorage.setItem('hc_user', JSON.stringify(this.user))
      else localStorage.removeItem('hc_user')
    },
    async login(payload) {
      const data = await authApi.login(payload)
      this.impersonation = null
      sessionStorage.removeItem('hc_impersonation')
      this.token = data.token
      this.user = data.user
      this.persist()
      return data
    },
    async register(payload) {
      return authApi.register(payload)
    },
    async refreshMe() {
      if (!this.token) return null
      const user = await authApi.me()
      this.user = user
      this.persist()
      return user
    },
    /** 进入模拟视角：保存原管理员 Token/用户快照后切换为目标用户会话 */
    startImpersonation(data) {
      this.impersonation = {
        adminToken: this.token,
        adminUser: this.user,
        expiresAt: Date.now() + (data.expiresIn || 0) * 1000
      }
      sessionStorage.setItem('hc_impersonation', JSON.stringify(this.impersonation))
      this.token = data.token
      this.user = data.user
      this.persist()
    },
    /** 退出模拟：原样恢复管理员会话，不重新登录、不重新签发 */
    exitImpersonation() {
      const snap = this.impersonation
      this.impersonation = null
      sessionStorage.removeItem('hc_impersonation')
      if (snap) {
        this.token = snap.adminToken
        this.user = snap.adminUser
        this.persist()
      }
    },
    logout() {
      this.token = ''
      this.user = null
      this.impersonation = null
      sessionStorage.removeItem('hc_impersonation')
      this.persist()
    }
  }
})
