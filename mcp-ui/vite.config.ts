import { defineConfig } from 'vite'
import { resolve } from 'path'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    proxy: {
      // 代理聊天 API 到 MCP Host 服务
      '/api/chat': {
        target: 'http://localhost:8087',
        changeOrigin: true,
        secure: false
      },
      '/api/chat2Agent': {
        target: 'http://localhost:8087',
        changeOrigin: true,
        secure: false
      },
      // 代理 MCP 服务器管理 API 到 MCP Client 服务
      '/api/mcp': {
        target: 'http://localhost:8086',
        changeOrigin: true,
        secure: false
      },
      // 代理健康检查 API
      '/api/health': {
        target: 'http://localhost:8087',
        changeOrigin: true,
        secure: false
      }
    }
  }
})
