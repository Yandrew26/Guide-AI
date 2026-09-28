import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The gateway (multi-agent-gateway, :8080) routes /system/** to the services. GATEWAY overrides it.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/system': { target: process.env.GATEWAY ?? 'http://localhost:8080', changeOrigin: true },
    },
  },
})
