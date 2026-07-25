import { defineConfig } from 'astro/config'

export default defineConfig({
  site: 'https://loomai.pro',
  output: 'static',
  build: {
    format: 'directory',
  },
  compressHTML: true,
  prefetch: {
    prefetchAll: true,
    defaultStrategy: 'viewport',
  },
  vite: {
    build: {
      cssMinify: 'lightningcss',
    },
  },
})
