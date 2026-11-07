import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'

// Local-only proxy keeps existing sign-in and API contracts intact during visual QA.
const target = process.env.PLATFORM_PREVIEW_API_URL
if (!target || new URL(target).protocol !== 'https:') throw new Error('Set PLATFORM_PREVIEW_API_URL to an existing HTTPS Platform API.')
const server = await createServer({
  root: fileURLToPath(new URL('..', import.meta.url)),
  define: { 'import.meta.env.VITE_PLATFORM_API_BASE_URL': JSON.stringify('') },
  server: { host: '127.0.0.1', port: 4398, strictPort: true,
    proxy: { '/api': { target, changeOrigin: true, configure(proxy) {
      // This loopback-only development proxy is a server client; authorization is unchanged.
      proxy.on('proxyReq', request => request.removeHeader('origin'))
    } } },
  },
})
await server.listen()
server.printUrls()
