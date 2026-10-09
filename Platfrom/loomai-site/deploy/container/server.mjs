import { createReadStream, existsSync, readFileSync, statSync } from 'node:fs'
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
const dealershipDemoApiBaseUrl = normalizeHttpUrl(process.env.DEALERSHIP_DEMO_API_BASE_URL)
const aiWorkspacePlatformBaseUrl = normalizeHttpUrl(process.env.AI_WORKSPACE_PLATFORM_BASE_URL)
const aiWorkspaceInstallationId = normalizeInstallationId(process.env.AI_WORKSPACE_INSTALLATION_ID)
const aiWorkspaceConnectOrigins = normalizeHttpOrigins(process.env.AI_WORKSPACE_CONNECT_ORIGINS)
const publicImageOrigins = normalizeHttpsOrigins(process.env.PUBLIC_IMAGE_ORIGINS)
const aiWorkspaceReady = Boolean(aiWorkspacePlatformBaseUrl && aiWorkspaceInstallationId)
const aiWorkspaceInstallUrl = aiWorkspaceReady
  ? `${aiWorkspacePlatformBaseUrl}/api/public/ai-workspace/install.js`
  : '/ai-workspace-unavailable.js'

function normalizeHttpUrl(value) {
  const candidate = value?.trim()
  if (!candidate) return ''
  try {
    const parsed = new URL(candidate)
    if (!['http:', 'https:'].includes(parsed.protocol)) return ''
    return parsed.toString().replace(/\/$/, '')
  } catch {
    return ''
  }
}

function normalizeHttpsOrigins(value) {
  const origins = new Set()
  for (const candidate of (value || '').split(',')) {
    try {
      const parsed = new URL(candidate.trim())
      if (parsed.protocol === 'https:') origins.add(parsed.origin)
    } catch {
      // Invalid or non-HTTPS entries remain outside the CSP allowlist.
    }
  }
  return [...origins]
}

function normalizeHttpOrigins(value) {
  const origins = new Set()
  for (const candidate of (value || '').split(',')) {
    const normalized = normalizeHttpUrl(candidate)
    if (normalized) origins.add(new URL(normalized).origin)
  }
  return [...origins]
}

function normalizeInstallationId(value) {
  const candidate = value?.trim() || ''
  return /^awi_pub_[a-f0-9]{32}$/.test(candidate) ? candidate : ''
}

function configuredConnectSources() {
  const sources = new Set(["'self'"])
  for (const candidate of [dealershipDemoApiBaseUrl, aiWorkspacePlatformBaseUrl, ...aiWorkspaceConnectOrigins]) {
    if (!candidate) continue
    try {
      sources.add(new URL(candidate).origin)
    } catch {
      // Invalid values are excluded and surfaced as an unavailable integration.
    }
  }
  return [...sources].join(' ')
}

function configuredScriptSources() {
  const sources = new Set(["'self'", "'unsafe-inline'"])
  if (aiWorkspacePlatformBaseUrl) sources.add(new URL(aiWorkspacePlatformBaseUrl).origin)
  return [...sources].join(' ')
}

function configuredImageSources() {
  return ["'self'", 'data:', ...publicImageOrigins].join(' ')
}

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
      `script-src ${configuredScriptSources()}`,
      "style-src 'self' 'unsafe-inline'",
      `img-src ${configuredImageSources()}`,
      "font-src 'self' data:",
      `connect-src ${configuredConnectSources()}`,
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

function contentType(targetPath, extension) {
  const researchFeedSuffix = `${path.sep}research${path.sep}feed.xml`
  if (targetPath.endsWith(researchFeedSuffix)) {
    return 'application/rss+xml; charset=utf-8'
  }
  return contentTypes.get(extension) || 'application/octet-stream'
}

function serveFile(request, response, targetPath, statusCode = 200) {
  const extension = path.extname(targetPath).toLowerCase()
  response.writeHead(statusCode, {
    'Cache-Control': cacheControl(targetPath),
    'Content-Type': contentType(targetPath, extension),
    ...securityHeaders(extension !== '.html'),
  })
  if (request.method === 'HEAD') {
    response.end()
    return
  }
  if (extension === '.html') {
    const html = readFileSync(targetPath, 'utf8')
      .replaceAll('__LOOMAI_AI_WORKSPACE_INSTALL_URL__', aiWorkspaceInstallUrl)
      .replaceAll(
        '__LOOMAI_AI_WORKSPACE_INSTALLATION_ID__',
        aiWorkspaceInstallationId || 'awi_pub_00000000000000000000000000000000',
      )
    response.end(html)
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
      aiWorkspaceConfigured: aiWorkspaceReady,
      checkedAt: new Date().toISOString(),
    }, headOnly)
    return
  }


  if (requestUrl.pathname === '/ai-workspace-unavailable.js') {
    response.writeHead(200, {
      'Cache-Control': 'no-store',
      'Content-Type': 'application/javascript; charset=utf-8',
      ...securityHeaders(false),
    })
    response.end(headOnly ? undefined : `window.dispatchEvent(new CustomEvent('loomai:workspace-error',{detail:{code:'INSTALLATION_NOT_CONFIGURED',message:'This site has not been assigned an AI Workspace installation.'}}));`)
    return
  }

  if (requestUrl.pathname === '/runtime-config/dealership-demo.json') {
    writeJson(response, 200, {
      ready: Boolean(dealershipDemoApiBaseUrl),
      apiBaseUrl: dealershipDemoApiBaseUrl || null,
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
