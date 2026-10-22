import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: 'localhost',
    port: 5173,
    proxy: {
      '/erp': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})