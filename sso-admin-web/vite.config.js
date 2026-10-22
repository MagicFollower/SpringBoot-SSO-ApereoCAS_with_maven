import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: 'localhost',
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8084',
        changeOrigin: true
      }
    }
  },
  preview: {
    host: 'localhost',
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8084',
        changeOrigin: true
      }
    }
  }
})