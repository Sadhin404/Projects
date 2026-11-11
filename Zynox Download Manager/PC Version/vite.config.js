import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// base './' so the built files load from file:// inside the packaged app
export default defineConfig({
  plugins: [react()],
  base: './',
  build: { outDir: 'dist', emptyOutDir: true },
});
