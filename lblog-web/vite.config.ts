import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8099/iblogserver',
        changeOrigin: true,
        headers: {
          Origin: 'http://localhost:4200',
        },
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.setHeader('Connection', 'keep-alive')
          })
        },
      },
      '/uploads': {
        target: 'http://localhost:8099/iblogserver',
        changeOrigin: true,
      },
    },
  },
})
