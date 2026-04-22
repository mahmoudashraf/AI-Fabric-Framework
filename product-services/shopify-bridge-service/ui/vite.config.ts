import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const frontendPort = Number(process.env.PORT ?? process.env.FRONTEND_PORT ?? 4175)
const backendPort = process.env.BACKEND_PORT ?? process.env.SHOPIFY_BRIDGE_BACKEND_PORT ?? '8080'
const backendTarget = process.env.SHOPIFY_BRIDGE_PROXY_TARGET ?? `http://127.0.0.1:${backendPort}`

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: frontendPort,
    proxy: {
      '/api': backendTarget,
      '/auth': backendTarget,
    },
  },
})
