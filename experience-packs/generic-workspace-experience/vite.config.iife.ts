import { defineConfig } from 'vite'
import { resolve } from 'node:path'

export default defineConfig({
  build: {
    lib: {
      entry: resolve(__dirname, 'src/iife.ts'),
      name: 'LoomAIGenericWorkspaceExperience',
      formats: ['iife'],
      fileName: () => 'generic-workspace-experience.iife.js',
    },
    sourcemap: true,
    minify: 'esbuild',
    emptyOutDir: false,
  },
})
