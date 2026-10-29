import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react-swc';
import cssInjectedByJsPlugin from 'vite-plugin-css-injected-by-js';
import path from 'node:path';

export default defineConfig({
  plugins: [react(), cssInjectedByJsPlugin()],
  define: { 'process.env.NODE_ENV': JSON.stringify('production') },
  resolve: { alias: { '@': path.resolve(__dirname, './src') } },
  build: {
    lib: { entry: path.resolve(__dirname, 'src/entries/preview.tsx'), name: 'LoomAIEngagePreview',
      formats: ['iife'], fileName: () => 'engage-preview.iife.js' },
    rollupOptions: { output: { inlineDynamicImports: true } },
    cssCodeSplit: false, sourcemap: false, minify: 'terser', outDir: 'dist', emptyOutDir: false,
  },
});
