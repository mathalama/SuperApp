import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    host: true,
    allowedHosts: true,
    cors: true,
    proxy: {
      '/auth': {
        target: 'http://localhost:8700',
        changeOrigin: true,
        secure: false,
      },
      '/api': {
        target: 'http://localhost:8700',
        changeOrigin: true,
        secure: false,
      },
    },
  },
});
