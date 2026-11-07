import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'

const target = process.env.PLATFORM_PREVIEW_API_URL ?? 'https://api.loomai.pro'
if (new URL(target).protocol !== 'https:') throw new Error('Preview requires an HTTPS Platform API.')
const port = Number(process.env.PLATFORM_PREVIEW_PORT ?? 4398)
const readOnly = process.env.PLATFORM_PREVIEW_ALLOW_WRITES !== 'true'
const server = await createServer({
  root: fileURLToPath(new URL('..', import.meta.url)),
  define: { 'import.meta.env.VITE_PLATFORM_API_BASE_URL': JSON.stringify(''),
    'import.meta.env.VITE_SMART_BRAIN_REVIEW': JSON.stringify(readOnly ? 'true' : 'false') },
  plugins: [{ name: 'read-only-review', configureServer(vite) {
    vite.middlewares.use((request, response, next) => {
      const path = new URL(request.url ?? '/', 'http://localhost').pathname
      const read = ['GET', 'HEAD', 'OPTIONS'].includes(request.method ?? 'GET')
      const authentication = /^\/api\/platform\/auth\//.test(path)
      const preferences = path === '/api/platform/preferences'
      const composition = /^\/api\/deployments\/[^/]+\/ai-behaviour\/compose$/.test(path)
      if (readOnly && path.startsWith('/api/') && !read && !authentication && !preferences && !composition) {
        response.statusCode = 403
        response.setHeader('Content-Type', 'application/json')
        response.end(JSON.stringify({ errorCode: 'LOCAL_PREVIEW_READ_ONLY', message: 'This local review is read-only. No live configuration was changed.' }))
        return
      }
      next()
    })
  } }],
  server: { host: '127.0.0.1', port, strictPort: true,
    proxy: { '/api': { target, changeOrigin: true, configure(proxy) {
      proxy.on('proxyReq', request => request.removeHeader('origin'))
    } } },
  },
})
await server.listen()
server.printUrls()
console.log(`Smart Brain review: ${readOnly ? 'READ ONLY; live configuration writes are blocked' : 'writes enabled explicitly'}.`)
