import { defineConfig } from 'vite'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  build: {
    lib: {
      entry: path.resolve(root, 'src/index.ts'),
      formats: ['es'],
      fileName: () => 'dealership-experience.esm.js',
    },
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: true,
  },
})
