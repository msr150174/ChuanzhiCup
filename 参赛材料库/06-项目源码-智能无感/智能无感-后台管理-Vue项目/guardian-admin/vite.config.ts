import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

/**
 * 开发配置
 *
 * ★ 关键改动：不再使用自带的 Express mock 服务器，
 *   改为把 /api 代理到真实的 Spring Boot 后端（guardian-server，端口 3000）。
 *
 *   原方案（server/index.mjs）用内存假数据，改完后端接口也看不到效果。
 *   代理模式下前端拿到的就是 MySQL(znwg) 里的真实数据。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5177,
    // 端口被占用时直接报错，不要偷偷换端口（避免和后端 3000 混淆）
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:3000',
        changeOrigin: true,
      },
    },
  },
})
