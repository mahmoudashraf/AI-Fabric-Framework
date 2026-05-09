import { createReadStream, existsSync } from 'node:fs'
import { appendFile, mkdir, readFile } from 'node:fs/promises'
import { createServer } from 'node:http'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const publicDir = path.join(__dirname, 'public')
const port = Number(process.env.PORT || 4177)
const leadsFile = (process.env.LOOMAI_LEADS_FILE || '/tmp/loomai-landing-leads.jsonl').trim()
const leadWebhookUrl = (process.env.LOOMAI_LEAD_WEBHOOK_URL || '').trim()
const leadWebhookToken = (process.env.LOOMAI_LEAD_WEBHOOK_TOKEN || '').trim()

const publicConfig = Object.freeze({
  demoUrl: (process.env.LOOMAI_DEMO_URL || 'https://shopping-companion-test.myshopify.com/apps/loom-companion').trim(),
  privateInstallUrl: (process.env.LOOMAI_PRIVATE_INSTALL_URL || 'mailto:hello@loomai.pro?subject=Request%20Loom%20Companion%20private%20install').trim(),
  partnerSignInUrl: (process.env.LOOMAI_PARTNER_SIGN_IN_URL || 'https://loomai-partner-ui.46.224.145.148.sslip.io/login').trim(),
  docsUrl: (process.env.LOOMAI_DOCS_URL || '/#private-install').trim(),
  statusUrl: (process.env.LOOMAI_STATUS_URL || '/health').trim()
})

const contentTypes = new Map([
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'application/javascript; charset=utf-8'],
  ['.css', 'text/css; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.svg', 'image/svg+xml; charset=utf-8'],
  ['.png', 'image/png'],
  ['.jpg', 'image/jpeg'],
  ['.jpeg', 'image/jpeg'],
  ['.webp', 'image/webp'],
  ['.ico', 'image/x-icon'],
  ['.txt', 'text/plain; charset=utf-8']
])

function securityHeaders(isStaticAsset = false) {
  const headers = {
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    'Referrer-Policy': 'strict-origin-when-cross-origin',
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=()',
    'Content-Security-Policy': [
      "default-src 'self'",
      "script-src 'self'",
      "style-src 'self'",
      "img-src 'self' data:",
      "font-src 'self' data:",
      "connect-src 'self'",
      "frame-ancestors 'none'",
      "base-uri 'self'",
      "form-action 'self' mailto:",
      "object-src 'none'",
      "upgrade-insecure-requests"
    ].join('; ')
  }
  if (process.env.NODE_ENV === 'production' || process.env.COOLIFY_FQDN || process.env.RAILWAY_PUBLIC_DOMAIN) {
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
    'Cache-Control': 'no-store',
    ...securityHeaders(false)
  })
  response.end(JSON.stringify(payload))
}

function runtimeConfigScript() {
  return `window.__LOOMAI_LANDING_CONFIG__ = Object.freeze(${JSON.stringify(publicConfig)});\n`
}

function resolveStaticPath(urlPathname) {
  const normalized = path.normalize(urlPathname).replace(/^(\.\.[/\\])+/, '')
  const candidate = path.join(publicDir, normalized)
  if (!candidate.startsWith(publicDir)) {
    return null
  }
  return candidate
}

function pageFor(requestUrl, hostHeader) {
  const host = (hostHeader || '').toLowerCase()
  const pathname = requestUrl.pathname
  if (host.startsWith('partners.') && (pathname === '/' || pathname === '/index.html')) {
    return path.join(publicDir, 'partners.html')
  }
  if (pathname === '/partners' || pathname === '/partners/' || pathname === '/partners.html') {
    return path.join(publicDir, 'partners.html')
  }
  if (pathname === '/' || pathname === '/index.html') {
    return path.join(publicDir, 'index.html')
  }
  return null
}

function requestHost(request) {
  return String(request.headers['x-forwarded-host'] || request.headers.host || '')
}

function sanitizeString(value, maxLength = 600) {
  return String(value || '')
    .replace(/[\u0000-\u001F\u007F]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, maxLength)
}

function isEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

function clientIp(request) {
  return sanitizeString(
    request.headers['x-forwarded-for'] || request.socket.remoteAddress || '',
    180
  )
}

async function readBody(request) {
  const chunks = []
  let size = 0
  for await (const chunk of request) {
    size += chunk.length
    if (size > 32 * 1024) {
      const error = new Error('Request body is too large.')
      error.statusCode = 413
      throw error
    }
    chunks.push(chunk)
  }
  return Buffer.concat(chunks).toString('utf8')
}

function normalizeLead(raw, request) {
  const kind = sanitizeString(raw.kind, 80)
  if (!['merchant-private-install', 'partner-application'].includes(kind)) {
    return { error: 'Unsupported lead type.' }
  }

  const lead = {
    id: `lead_${Date.now()}_${Math.random().toString(16).slice(2, 10)}`,
    kind,
    name: sanitizeString(raw.name, 160),
    email: sanitizeString(raw.email, 240).toLowerCase(),
    company: sanitizeString(raw.company, 240),
    website: sanitizeString(raw.website, 240),
    shopDomain: sanitizeString(raw.shopDomain, 240).toLowerCase(),
    storeCount: sanitizeString(raw.storeCount, 80),
    goal: sanitizeString(raw.goal, 1000),
    sourceUrl: sanitizeString(raw.sourceUrl, 600),
    consent: raw.consent === true || raw.consent === 'true',
    userAgent: sanitizeString(request.headers['user-agent'] || '', 600),
    ip: clientIp(request),
    createdAt: new Date().toISOString()
  }

  if (!lead.name || !isEmail(lead.email)) {
    return { error: 'Name and a valid email are required.' }
  }
  if (!lead.consent) {
    return { error: 'Consent is required before submitting.' }
  }
  if (lead.kind === 'merchant-private-install' && !lead.shopDomain) {
    return { error: 'Shopify store domain is required.' }
  }
  if (lead.kind === 'partner-application' && !lead.company) {
    return { error: 'Company or website is required.' }
  }
  return { lead }
}

async function persistLead(lead) {
  if (leadWebhookUrl) {
    const headers = {
      'Content-Type': 'application/json',
      'User-Agent': 'loomai-landing-site/0.1'
    }
    if (leadWebhookToken) {
      headers.Authorization = `Bearer ${leadWebhookToken}`
    }
    const response = await fetch(leadWebhookUrl, {
      method: 'POST',
      headers,
      body: JSON.stringify(lead)
    })
    if (!response.ok) {
      throw new Error(`Lead webhook rejected request with HTTP ${response.status}.`)
    }
    return
  }

  if (leadsFile.toLowerCase() === 'disabled') {
    return
  }
  await mkdir(path.dirname(leadsFile), { recursive: true })
  await appendFile(leadsFile, `${JSON.stringify(lead)}\n`, 'utf8')
}

async function handleLeadRequest(request, response) {
  if (request.method !== 'POST') {
    response.writeHead(405, { Allow: 'POST', ...securityHeaders(false) })
    response.end()
    return
  }
  try {
    const body = await readBody(request)
    const parsed = body ? JSON.parse(body) : {}
    const normalized = normalizeLead(parsed, request)
    if (normalized.error) {
      writeJson(response, 400, { success: false, message: normalized.error })
      return
    }
    await persistLead(normalized.lead)
    writeJson(response, 202, {
      success: true,
      leadId: normalized.lead.id,
      message: 'Request received.'
    })
  } catch (error) {
    const statusCode = Number(error.statusCode || 500)
    writeJson(response, statusCode, {
      success: false,
      message: statusCode === 500 ? 'Lead request could not be recorded.' : error.message
    })
  }
}

async function serveFile(response, targetPath, extension) {
  response.writeHead(200, {
    'Content-Type': contentTypes.get(extension) || 'application/octet-stream',
    'Cache-Control': extension === '.html' ? 'no-cache' : 'public, max-age=31536000, immutable',
    ...securityHeaders(extension !== '.html')
  })
  createReadStream(targetPath).pipe(response)
}

const server = createServer(async (request, response) => {
  const requestUrl = new URL(request.url || '/', 'http://127.0.0.1')

  if (requestUrl.pathname === '/health') {
    writeJson(response, 200, { status: 'UP', service: 'loomai-landing-site' })
    return
  }

  if (requestUrl.pathname === '/runtime-config.js') {
    response.writeHead(200, {
      'Content-Type': 'application/javascript; charset=utf-8',
      'Cache-Control': 'no-store',
      ...securityHeaders(true)
    })
    response.end(runtimeConfigScript())
    return
  }

  if (requestUrl.pathname === '/api/leads') {
    await handleLeadRequest(request, response)
    return
  }

  const routedPage = pageFor(requestUrl, requestHost(request))
  if (routedPage && existsSync(routedPage)) {
    await serveFile(response, routedPage, '.html')
    return
  }

  const targetPath = resolveStaticPath(requestUrl.pathname)
  if (!targetPath) {
    writeJson(response, 400, { success: false, message: 'Invalid path.' })
    return
  }
  if (existsSync(targetPath) && !targetPath.endsWith(path.sep)) {
    await serveFile(response, targetPath, path.extname(targetPath))
    return
  }

  writeJson(response, 404, { success: false, message: 'Not found.' })
})

server.listen(port, '0.0.0.0', () => {
  console.log(`LoomAI landing site listening on port ${port}`)
  console.log(`Lead sink: ${leadWebhookUrl ? 'webhook' : leadsFile}`)
})
