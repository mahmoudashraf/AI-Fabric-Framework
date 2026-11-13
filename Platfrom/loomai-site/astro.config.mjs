import { defineConfig } from 'astro/config'

export default defineConfig({
  site: 'https://loomai.pro',
  output: 'static',
  build: {
    format: 'directory',
    inlineStylesheets: 'never',
  },
  compressHTML: true,
  prefetch: {
    prefetchAll: true,
    defaultStrategy: 'viewport',
  },
  vite: {
    build: {
      assetsInlineLimit: 0,
      cssMinify: 'lightningcss',
    },
  },
})
