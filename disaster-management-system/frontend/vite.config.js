import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  define: {
    global: 'globalThis',
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
      },
      '/nlp': {
        target: 'http://localhost:8000',
        rewrite: (path) => path.replace(/^\/nlp/, '/api'),
      },
    },
  },
})
