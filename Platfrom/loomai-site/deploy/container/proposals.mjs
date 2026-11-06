import { createHmac } from 'node:crypto'
import { readFileSync } from 'node:fs'

const cookieToken = /^[A-Za-z0-9_-]{32,128}$/
const assets = new Map([['client.js','text/javascript; charset=utf-8'],['presentation.js','text/javascript; charset=utf-8'],['styles.css','text/css; charset=utf-8'],['presentation.css','text/css; charset=utf-8'],['integration.css','text/css; charset=utf-8']])
const protectedHeaders = {
  'Cache-Control': 'private, no-store',
  'Referrer-Policy': 'no-referrer',
  'X-Robots-Tag': 'noindex, nofollow, noarchive',
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-src https://calendly.com https://www.youtube-nocookie.com; frame-ancestors 'none'; form-action 'self'; base-uri 'none'; object-src 'none'",
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=()',
  'Cross-Origin-Opener-Policy': 'same-origin',
}

function json(response, status, value, extra = {}) {
  response.writeHead(status, { ...protectedHeaders, 'Content-Type': 'application/json; charset=utf-8', ...extra })
  response.end(value == null ? undefined : JSON.stringify(value))
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = []
    let size = 0
    request.on('data', chunk => {
      size += chunk.length
      if (size > 2048) { reject(Object.assign(new Error('BODY_TOO_LARGE'), { status: 413 })); return }
      chunks.push(chunk)
    })
    request.on('end', () => {
      if (size > 2048) return
      try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8'))) }
      catch { reject(Object.assign(new Error('INVALID_REQUEST'), { status: 400 })) }
    })
    request.on('error', reject)
  })
}

export function createProposalGateway({
  endpoint = process.env.CUSTOMER_PROPOSALS_ENDPOINT,
  gatewayKey = process.env.CUSTOMER_PROPOSALS_GATEWAY_KEY,
  origin = process.env.CUSTOMER_PROPOSALS_ORIGIN || 'https://loomai.pro',
  fetchImpl = fetch,
} = {}) {
  let configured = false
  try {
    const target = new URL(endpoint)
    configured = target.protocol === 'https:' && target.pathname === '/functions/v1/customer-proposals'
      && !target.search && !target.hash && !target.username && !target.password && cookieToken.test(gatewayKey || '')
      && new URL(origin).origin === origin
  } catch { /* Missing configuration leaves only the access shell available. */ }
  const attempts = new Map()
  const cookieName = slug => `__Secure-loomai-proposal-${slug}`
  const cookieValue = (slug, token, maxAge = 0) => `${cookieName(slug)}=${token}; Path=/proposals/${slug}; Max-Age=${maxAge}; Secure; HttpOnly; SameSite=Lax`
  const tokenFor = (request, slug) => {
    const matches = (request.headers.cookie || '').split(';').map(value => value.trim()).filter(value => value.startsWith(cookieName(slug) + '='))
    if (matches.length !== 1) return ''
    const token = matches[0].slice(cookieName(slug).length + 1)
    return cookieToken.test(token) ? token : ''
  }
  const call = async (action, slug, values) => {
    const response = await fetchImpl(endpoint, {
      method: 'POST', redirect: 'error', signal: AbortSignal.timeout(20000),
      headers: { 'Content-Type': 'application/json', 'x-proposal-gateway-key': gatewayKey },
      body: JSON.stringify({ action, slug, ...values }),
    })
    if (![200,401,404,429].includes(response.status)) return { status: 503, data: { error: 'UNAVAILABLE' } }
    const text = await response.text()
    if (text.length > 3200000) throw new Error('UNAVAILABLE')
    return { status: response.status, data: JSON.parse(text) }
  }
  const exchange = async (request, slug, code) => {
    const window = Math.floor(Date.now() / 600000)
    // This keyed, rotating digest is only abuse throttling, never an identity claim.
    const address = String(request.headers['x-forwarded-for'] || request.socket.remoteAddress || '').slice(0,256)
    const clientDigest = createHmac('sha256', gatewayKey).update(`${window}:${address}`).digest('hex')
    for (const [key, value] of attempts) if (value.window !== window) attempts.delete(key)
    if (!attempts.has(clientDigest) && attempts.size >= 2000) return { status: 429, data: { error: 'RATE_LIMITED' } }
    const count = (attempts.get(clientDigest)?.count || 0) + 1
    attempts.set(clientDigest, { window, count })
    if (count > 20) return { status: 429, data: { error: 'RATE_LIMITED' } }
    if (typeof code !== 'string' || !cookieToken.test(code.trim())) return { status: 401, data: { error: 'ACCESS_DENIED' } }
    const result = await call('exchange', slug, { code: code.trim(), clientDigest })
    if (result.status === 200 && (!cookieToken.test(result.data.token || '') || !Number.isFinite(result.data.maxAge) || result.data.maxAge < 1 || result.data.maxAge > 14400)) throw new Error('UNAVAILABLE')
    return result
  }

  return async (request, response, url) => {
    // Match before static routing, including malformed or encoded proposal paths.
    if (!/^\/proposals(?:\/|$)/i.test(url.pathname) && !/^\/proposals(?:%|\\)/i.test(url.pathname)) return false
    try {
      if (/^\/proposals\/assets\//.test(url.pathname)) {
        const name = url.pathname.slice('/proposals/assets/'.length)
        if (!assets.has(name) || !['GET','HEAD'].includes(request.method)) { json(response,404,{error:'NOT_FOUND'}); return true }
        const bytes = readFileSync(new URL(`./proposal-ui/${name}`, import.meta.url))
        response.writeHead(200, { ...protectedHeaders, 'Content-Type': assets.get(name) })
        response.end(request.method === 'HEAD' ? undefined : bytes)
        return true
      }
      const match = /^\/proposals\/([a-z0-9]+(?:-[a-z0-9]+)*)(?:\/(access|content|download|logout))?\/?$/.exec(url.pathname)
      if (!match || match[1].length > 64) { json(response,404,{error:'NOT_FOUND'}); return true }
      const [, slug, action] = match
      if (!action && ['GET','HEAD'].includes(request.method)) {
        if (url.searchParams.has('code')) {
          let result = { status: 503, data: { error: 'UNAVAILABLE' } }
          if (configured && request.method === 'GET' && !request.headers['sec-purpose']?.includes('prefetch')) result = await exchange(request, slug, url.searchParams.get('code'))
          const success = result.status === 200
          response.writeHead(303, { ...protectedHeaders,
            Location: `/proposals/${slug}${success ? '' : `?access=${result.status === 503 ? 'unavailable' : result.status === 429 ? 'limited' : 'invalid'}`}`,
            ...(success ? { 'Set-Cookie': cookieValue(slug, result.data.token, result.data.maxAge) } : {}),
          })
          response.end()
          return true
        }
        response.writeHead(200, { ...protectedHeaders, 'Content-Type': 'text/html; charset=utf-8' })
        response.end(request.method === 'HEAD' ? undefined : readFileSync(new URL('./proposal-ui/index.html', import.meta.url)))
        return true
      }
      const method = action === 'access' || action === 'logout' ? 'POST' : 'GET'
      if (request.method !== method && !(method === 'GET' && request.method === 'HEAD')) {
        json(response,405,{error:'METHOD_NOT_ALLOWED'},{Allow:method === 'GET' ? 'GET, HEAD' : method}); return true
      }
      if (method === 'POST' && (request.headers.origin !== origin || (request.headers['sec-fetch-site'] && request.headers['sec-fetch-site'] !== 'same-origin'))) {
        json(response,403,{error:'ACCESS_DENIED'}); return true
      }
      if (!configured) { json(response,503,{error:'UNAVAILABLE'}); return true }
      if (action === 'access') {
        if (!(request.headers['content-type'] || '').startsWith('application/json')) { json(response,415,{error:'INVALID_REQUEST'}); return true }
        const body = await readBody(request)
        const result = await exchange(request, slug, body?.code)
        json(response, result.status === 200 ? 204 : result.status, result.status === 200 ? null : result.data,
          result.status === 200 ? { 'Set-Cookie': cookieValue(slug,result.data.token,result.data.maxAge) } : {})
        return true
      }
      const token = tokenFor(request,slug)
      if (!token) { json(response,401,{error:'ACCESS_DENIED'}); return true }
      const result = await call(action,slug,{token})
      if (action === 'logout') {
        json(response,result.status === 200 ? 204 : result.status,result.status === 200 ? null : result.data,
          result.status === 200 ? { 'Set-Cookie': cookieValue(slug,'',0) } : {})
      } else if (result.status !== 200) json(response,result.status,result.data)
      else if (action === 'download') {
        if (typeof result.data.base64 !== 'string' || result.data.base64.length > 3000000 || !/^[A-Za-z0-9+/]*={0,2}$/.test(result.data.base64)) throw new Error('UNAVAILABLE')
        response.writeHead(200, { ...protectedHeaders,
          'Content-Type': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
          'Content-Disposition': `attachment; filename="${slug}-loomai-proposal.docx"`,
        })
        response.end(request.method === 'HEAD' ? undefined : Buffer.from(result.data.base64,'base64'))
      } else json(response,200,request.method === 'HEAD' ? null : result.data)
    } catch (error) {
      json(response,error.status === 413 || error.status === 400 ? error.status : 503,{error:error.status === 413 || error.status === 400 ? 'INVALID_REQUEST' : 'UNAVAILABLE'})
    }
    return true
  }
}
