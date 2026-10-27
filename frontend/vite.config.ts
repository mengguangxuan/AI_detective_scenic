import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
const proxy = { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: false } };
export default defineConfig({
  plugins: [react(), tailwindcss()],
  // Runtime API class names determine message names, including in production.
  esbuild: { keepNames: true },
  server: { port: 5173, strictPort: true, proxy },
  preview: { port: 5173, strictPort: true, proxy },
});
