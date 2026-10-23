import { build } from 'vite'
import { fileURLToPath } from 'node:url'

const phase=['u2','u3','u4','u5','u6','m1'].includes(process.argv[2]) ? process.argv[2] : 'u1'
await build({
  root: fileURLToPath(new URL(phase!=='u1'?'../customer-console-preview/u2/':'../customer-console-preview/', import.meta.url)),
  configFile: false, envFile: false, base: './',
  esbuild: { jsx: 'automatic' },
  define: { __CUSTOMER_PREVIEW_PHASE__: JSON.stringify(phase.toUpperCase()) },
  build: {
    outDir: fileURLToPath(new URL(`../../../doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/designs/010_45_customer_console/phase-${phase}/`, import.meta.url)),
    emptyOutDir: true, sourcemap: false,
  },
})
