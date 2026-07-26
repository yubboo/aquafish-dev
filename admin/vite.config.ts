import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  server: {
    host: '127.0.0.1',
    port: 18520,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      },
      '/site': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      },
      '/content': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      },
      '/forum': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      },
      '/login': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      },
      '/register': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      },
      '/member': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      },
      '/theme-assets': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      }
    }
  },
  /*
   * preview 用生产构建产物验证“静态站点 + Java 后端”分离部署。
   * 正式服务器使用发行包内的 Nginx 同源反向代理示例；这里固定代理到
   * 本地开发后端 8520，避免为了验收而放宽后端 CORS 或 Cookie 安全策略。
   */
  preview: {
    host: '127.0.0.1',
    port: 18520,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      },
      '/site': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      },
      '/content': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      },
      '/forum': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      },
      '/login': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      },
      '/register': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      },
      '/member': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      },
      '/theme-assets': {
        target: 'http://127.0.0.1:8520',
        changeOrigin: true
      }
    }
  }
})
