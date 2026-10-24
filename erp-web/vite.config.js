import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端前缀清单：只有这些路径转发到 erp-server(8081)。
// 注意 /erp/board、/erp/* 等 SPA 路由【不能】进代理——否则地址栏直接打开会被
// 转给后端并命中 NoResourceFound → 500。SPA 路由由 index.html + vue-router 接管。
const backendPaths = ['/erp/api', '/erp/login', '/erp/logout']

export default defineConfig({
  plugins: [vue()],
  server: {
    host: 'localhost',
    port: 5173,
    proxy: Object.fromEntries(
      backendPaths.map((p) => [
        p,
        { target: 'http://localhost:8081', changeOrigin: true },
      ])
    ),
  },
})
