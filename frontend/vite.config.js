import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 所有 HTTP 与 WebSocket 都只经 8080 网关：
// 浏览器访问 5173 同源地址，由 Vite 开发代理转发，避免跨域与直连微服务。
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        ws: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        // 按依赖来源拆包：Element Plus 及其运行时依赖分开，避免单个 chunk 超过 900KB
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus'
          if (id.includes('/vue/') || id.includes('/@vue/') || id.includes('vue-router') || id.includes('pinia')) {
            return 'vue-vendor'
          }
          if (id.includes('/axios/')) return 'axios'
          return 'vendor'
        }
      }
    }
  }
})
