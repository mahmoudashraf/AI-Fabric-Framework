import { createReadStream, existsSync } from 'node:fs'
import { readFile } from 'node:fs/promises'
import { createServer } from 'node:http'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const distDir = process.env.PARTNER_UI_DIST_DIR
  ? path.resolve(process.env.PARTNER_UI_DIST_DIR)
  : path.join(__dirname, 'dist')
const indexPath = path.join(distDir, 'index.html')
const port = Number(process.env.PORT || 3000)

const platformApiBaseUrl = (
  process.env.PARTNER_UI_PLATFORM_API_BASE_URL ||
  process.env.VITE_PLATFORM_API_BASE_URL ||
  ''
).trim()
const supabaseUrl = (
  process.env.PARTNER_UI_SUPABASE_URL ||
  process.env.VITE_SUPABASE_URL ||
  ''
).trim()
const supabaseAnonKey = (
  process.env.PARTNER_UI_SUPABASE_ANON_KEY ||
  process.env.VITE_SUPABASE_ANON_KEY ||
  ''
).trim()

const contentTypes = new Map([
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'application/javascript; charset=utf-8'],
  ['.css', 'text/css; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.svg', 'image/svg+xml'],
  ['.png', 'image/png'],
  ['.jpg', 'image/jpeg'],
  ['.jpeg', 'image/jpeg'],
  ['.gif', 'image/gif'],
  ['.ico', 'image/x-icon'],
  ['.txt', 'text/plain; charset=utf-8'],
  ['.woff', 'font/woff'],
  ['.woff2', 'font/woff2'],
])

function securityHeaders(isStaticAsset) {
  const headers = {
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    'Referrer-Policy': 'strict-origin-when-cross-origin',
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
    'Content-Security-Policy': [
      "default-src 'self'",
      "script-src 'self'",
      "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
      "img-src 'self' data: https:",
      "font-src 'self' data: https://fonts.gstatic.com",
      "connect-src 'self' https:",
      "frame-ancestors 'none'",
      "base-uri 'self'",
      "object-src 'none'",
    ].join('; '),
  }
  if (process.env.NODE_ENV === 'production') {
    headers['Strict-Transport-Security'] = 'max-age=31536000; includeSubDomains'
  }
  if (isStaticAsset) {
    headers['Cross-Origin-Resource-Policy'] = 'same-origin'
  }
  return headers
}

function writeJson(response, statusCode, payload) {
  response.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    ...securityHeaders(false),
  })
  response.end(JSON.stringify(payload))
}

function runtimeConfigScript() {
  return `window.__PARTNER_RUNTIME_CONFIG__ = Object.freeze(${JSON.stringify({
    platformApiBaseUrl,
    supabaseUrl,
    supabaseAnonKey,
  })});\n`
}

function resolveStaticPath(urlPathname) {
  const normalized = path.normalize(urlPathname).replace(/^(\.\.[/\\])+/, '')
  const candidate = path.join(distDir, normalized)
  if (!candidate.startsWith(distDir)) {
    return null
  }
  return candidate
}

const server = createServer(async (request, response) => {
  const requestUrl = new URL(request.url || '/', 'http://127.0.0.1')

  if (requestUrl.pathname === '/health') {
    writeJson(response, 200, { status: 'UP' })
    return
  }

  if (requestUrl.pathname === '/runtime-config.js') {
    response.writeHead(200, {
      'Content-Type': 'application/javascript; charset=utf-8',
      'Cache-Control': 'no-store',
      ...securityHeaders(true),
    })
    response.end(runtimeConfigScript())
    return
  }

  const targetPath = requestUrl.pathname === '/' ? indexPath : resolveStaticPath(requestUrl.pathname)
  if (!targetPath) {
    writeJson(response, 400, { success: false, message: 'Invalid path.' })
    return
  }

  if (existsSync(targetPath) && !targetPath.endsWith(path.sep)) {
    const extension = path.extname(targetPath)
    response.writeHead(200, {
      'Content-Type': contentTypes.get(extension) || 'application/octet-stream',
      'Cache-Control': extension === '.html' ? 'no-cache' : 'public, max-age=31536000, immutable',
      ...securityHeaders(extension !== '.html'),
    })
    createReadStream(targetPath).pipe(response)
    return
  }

  try {
    const indexHtml = await readFile(indexPath)
    response.writeHead(200, {
      'Content-Type': 'text/html; charset=utf-8',
      'Cache-Control': 'no-cache',
      ...securityHeaders(false),
    })
    response.end(indexHtml)
  } catch (error) {
    writeJson(response, 500, {
      success: false,
      message: 'Failed to serve Partner UI index.',
      error: error instanceof Error ? error.message : 'UNKNOWN',
    })
  }
})

server.listen(port, '0.0.0.0', () => {
  console.log(`Partner UI listening on port ${port}`)
  console.log(`Partner UI Platform API base URL: ${platformApiBaseUrl || '(empty)'}`)
  console.log(`Partner UI Supabase URL configured: ${supabaseUrl ? 'yes' : 'no'}`)
})
