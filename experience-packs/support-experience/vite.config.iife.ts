import { defineConfig } from 'vite'
import { fileURLToPath } from 'node:url'
export default defineConfig({ build: { lib: {
  entry: fileURLToPath(new URL('src/iife.ts', import.meta.url)), formats: ['iife'],
  name: 'LoomAISupportExperience', fileName: () => 'support-experience.iife.js',
}, emptyOutDir: false, sourcemap: false } })
