import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 允许 Cloudflare Tunnel 域名访问，否则 Vite 会拦截非 localhost 的请求
    allowedHosts: ['ths.thsflow.xyz'],
    proxy: {
      '/api': {
        target: 'http://localhost:8100',
        changeOrigin: true,
      },
    },
  },
});
