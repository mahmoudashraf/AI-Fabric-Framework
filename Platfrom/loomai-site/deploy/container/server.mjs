import { createReadStream, existsSync, statSync } from 'node:fs'
import { createServer } from 'node:http'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const distDir = process.env.LOOMAI_SITE_DIST_DIR
  ? path.resolve(process.env.LOOMAI_SITE_DIST_DIR)
  : path.join(__dirname, 'dist')
const port = Number(process.env.PORT || 3000)
const buildCommit = (
  process.env.APP_BUILD_COMMIT ||
  process.env.SOURCE_COMMIT ||
  process.env.BUILD_COMMIT ||
  'unknown'
).trim()
const buildTime = (process.env.APP_BUILD_TIME || process.env.BUILD_TIME || 'unknown').trim()

const contentTypes = new Map([
  ['.avif', 'image/avif'],
  ['.css', 'text/css; charset=utf-8'],
  ['.gif', 'image/gif'],
  ['.html', 'text/html; charset=utf-8'],
  ['.ico', 'image/x-icon'],
  ['.jpeg', 'image/jpeg'],
  ['.jpg', 'image/jpeg'],
  ['.js', 'application/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.map', 'application/json; charset=utf-8'],
  ['.png', 'image/png'],
  ['.rss', 'application/rss+xml; charset=utf-8'],
  ['.svg', 'image/svg+xml; charset=utf-8'],
  ['.txt', 'text/plain; charset=utf-8'],
  ['.webmanifest', 'application/manifest+json; charset=utf-8'],
  ['.webp', 'image/webp'],
  ['.woff', 'font/woff'],
  ['.woff2', 'font/woff2'],
  ['.xml', 'application/xml; charset=utf-8'],
])

function securityHeaders(isStaticAsset = false) {
  const headers = {
    'Content-Security-Policy': [
      "default-src 'self'",
      "script-src 'self' 'unsafe-inline'",
      "style-src 'self' 'unsafe-inline'",
      "img-src 'self' data:",
      "font-src 'self' data:",
      "connect-src 'self'",
      "frame-ancestors 'none'",
      "base-uri 'self'",
      "form-action 'self' mailto:",
      "object-src 'none'",
      'upgrade-insecure-requests',
    ].join('; '),
    'Cross-Origin-Opener-Policy': 'same-origin',
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=()',
    'Referrer-Policy': 'strict-origin-when-cross-origin',
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
  }

  if (process.env.NODE_ENV === 'production') {
    headers['Strict-Transport-Security'] = 'max-age=31536000; includeSubDomains'
  }
  if (isStaticAsset) {
    headers['Cross-Origin-Resource-Policy'] = 'same-origin'
  }
  return headers
}

function writeJson(response, statusCode, payload, headOnly = false) {
  response.writeHead(statusCode, {
    'Cache-Control': 'no-store',
    'Content-Type': 'application/json; charset=utf-8',
    ...securityHeaders(false),
  })
  response.end(headOnly ? undefined : JSON.stringify(payload))
}

function isFile(candidate) {
  return existsSync(candidate) && statSync(candidate).isFile()
}

function resolveRequestPath(pathname) {
  let decoded
  try {
    decoded = decodeURIComponent(pathname)
  } catch {
    return null
  }

  if (decoded.includes('\0')) {
    return null
  }

  const normalized = path.posix.normalize(decoded).replace(/^\/+/, '')
  if (normalized.startsWith('..')) {
    return null
  }

  const baseCandidate = path.resolve(distDir, normalized)
  if (!baseCandidate.startsWith(`${distDir}${path.sep}`) && baseCandidate !== distDir) {
    return null
  }

  const candidates = []
  if (pathname === '/') {
    candidates.push(path.join(distDir, 'index.html'))
  } else {
    candidates.push(
      baseCandidate,
      path.join(baseCandidate, 'index.html'),
      `${baseCandidate}.html`,
    )
  }

  return candidates.find(isFile) || null
}

function cacheControl(targetPath) {
  if (targetPath.endsWith('.html') || targetPath.endsWith('.xml') || targetPath.endsWith('.txt')) {
    return 'public, max-age=0, must-revalidate'
  }
  if (targetPath.includes(`${path.sep}_astro${path.sep}`)) {
    return 'public, max-age=31536000, immutable'
  }
  return 'public, max-age=86400, stale-while-revalidate=604800'
}

function serveFile(request, response, targetPath, statusCode = 200) {
  const extension = path.extname(targetPath).toLowerCase()
  response.writeHead(statusCode, {
    'Cache-Control': cacheControl(targetPath),
    'Content-Type': contentTypes.get(extension) || 'application/octet-stream',
    ...securityHeaders(extension !== '.html'),
  })
  if (request.method === 'HEAD') {
    response.end()
    return
  }
  createReadStream(targetPath).pipe(response)
}

const server = createServer((request, response) => {
  const requestUrl = new URL(request.url || '/', 'http://127.0.0.1')
  const headOnly = request.method === 'HEAD'

  if (!['GET', 'HEAD'].includes(request.method || 'GET')) {
    response.writeHead(405, { Allow: 'GET, HEAD', ...securityHeaders(false) })
    response.end()
    return
  }

  if (requestUrl.pathname === '/health') {
    writeJson(response, 200, {
      status: 'UP',
      service: 'loomai-public-site',
      commit: buildCommit,
      buildTime,
      checkedAt: new Date().toISOString(),
    }, headOnly)
    return
  }

  const targetPath = resolveRequestPath(requestUrl.pathname)
  if (targetPath) {
    serveFile(request, response, targetPath)
    return
  }

  const notFoundPath = path.join(distDir, '404.html')
  if (isFile(notFoundPath)) {
    serveFile(request, response, notFoundPath, 404)
    return
  }

  writeJson(response, 404, { status: 'NOT_FOUND' }, headOnly)
})

server.listen(port, '0.0.0.0', () => {
  console.log(`Loom AI Labs public site listening on port ${port}`)
  console.log(`Serving static output from ${distDir}`)
})
