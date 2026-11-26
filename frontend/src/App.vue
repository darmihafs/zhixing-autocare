<script setup>
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from './components/AppHeader.vue'
import AppFooter from './components/AppFooter.vue'
import AiAdvisor from './components/AiAdvisor.vue'
import ImpersonationBanner from './components/ImpersonationBanner.vue'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const auth = useAuthStore()

onMounted(() => {
  if (auth.isLoggedIn) auth.refreshMe().catch(() => {})
})
</script>

<template>
  <div class="app-shell" :class="{ 'has-impersonation-banner': auth.isImpersonating }">
    <ImpersonationBanner />
    <AppHeader v-if="!route.meta.bare" />
    <main :class="{ 'app-main': !route.meta.bare }">
      <router-view />
    </main>
    <AppFooter v-if="!route.meta.bare" />
    <AiAdvisor v-if="!route.meta.bare" />
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* 模拟横幅常驻顶部：整体下移 44px，吸顶顶栏同步避让 */
.has-impersonation-banner {
  padding-top: 44px;
}

.has-impersonation-banner :deep(.site-header) {
  top: 44px;
}

.app-main {
  flex: 1;
}
</style>
