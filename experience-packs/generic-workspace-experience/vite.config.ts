import { defineConfig } from 'vite'
import { resolve } from 'node:path'

export default defineConfig({
  build: {
    lib: {
      entry: resolve(__dirname, 'src/index.ts'),
      formats: ['es'],
      fileName: () => 'generic-workspace-experience.esm.js',
    },
    sourcemap: true,
    minify: 'esbuild',
    emptyOutDir: true,
  },
})
