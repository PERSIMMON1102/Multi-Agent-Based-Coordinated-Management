import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    target: 'es2019',
    minify: 'esbuild',
    cssCodeSplit: true,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return

          const normalizedId = id.replace(/\\/g, '/')

          if (normalizedId.includes('html2canvas') || normalizedId.includes('jspdf')) {
            return 'vendor-pdf'
          }
          if (normalizedId.includes('/node_modules/@floating-ui/')) {
            return 'vendor-element-plus-floating'
          }
          if (normalizedId.includes('/node_modules/@vueuse/')) {
            return 'vendor-element-plus-utils'
          }
          if (normalizedId.includes('/node_modules/element-plus/')) {
            return 'vendor-element-plus'
          }
          if (normalizedId.includes('@stomp') || normalizedId.includes('sockjs-client')) {
            return 'vendor-realtime'
          }
        }
      }
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
