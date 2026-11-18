import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// Vite 配置：路径别名 + 开发代理
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    host: '127.0.0.1',
    proxy: {
      // 所有 /api 请求代理到后端，避免跨域
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      // 上传后的头像由后端静态资源映射提供，开发环境需要一并代理
      '/uploads': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
