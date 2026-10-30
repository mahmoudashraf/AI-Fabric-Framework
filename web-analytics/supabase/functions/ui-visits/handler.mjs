import { pageCategory, siteForOrigin, viewportCategories } from './policy.mjs'

async function readSmallBody(request) {
  const reader = request.body?.getReader()
  if (!reader) throw new Error('EMPTY_BODY')
  const parts = []; let length = 0
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      length += value.byteLength
      if (length > 1024) { await reader.cancel(); throw new Error('BODY_TOO_LARGE') }
      parts.push(value)
    }
  } finally { reader.releaseLock() }
  const bytes = new Uint8Array(length); let offset = 0
  for (const part of parts) { bytes.set(part, offset); offset += part.byteLength }
  return JSON.parse(new TextDecoder().decode(bytes))
}

export function createHandler({ supabaseUrl, serviceKey, fetch: send = fetch }) {
  return async request => {
    const origin = request.headers.get('origin')
    const site = siteForOrigin(origin)
    const headers = { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', Vary: 'Origin' }
    if (!site) return new Response(null, { status: 403, headers })
    Object.assign(headers, { 'Access-Control-Allow-Origin': origin, 'Access-Control-Allow-Methods': 'POST, OPTIONS',
      'Access-Control-Allow-Headers': 'content-type', 'Access-Control-Max-Age': '600' })
    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers })
    if (request.method !== 'POST') return new Response(null, { status: 405, headers })
    let body
    try {
      body = await readSmallBody(request)
      if (!body || Array.isArray(body) || Object.keys(body).sort().join(',') !== 'page,viewport'
        || typeof body.page !== 'string' || body.page.length > 40 || pageCategory(site.site, body.page) !== body.page
        || !viewportCategories.has(body.viewport)) throw new Error('INVALID_EVENT')
    } catch { return new Response(null, { status: 400, headers }) }
    if (!supabaseUrl || !serviceKey) return new Response(null, { status: 503, headers })
    try {
      const response = await send(supabaseUrl + '/rest/v1/rpc/record_loomai_ui_visit', {
        method: 'POST', headers: { apikey: serviceKey, Authorization: 'Bearer ' + serviceKey, 'Content-Type': 'application/json' },
        body: JSON.stringify({ p_site: site.site, p_hostname: site.hostname, p_page: body.page, p_viewport: body.viewport }),
        signal: AbortSignal.timeout(5000),
      })
      return new Response(null, { status: response.ok ? 204 : 503, headers })
    } catch { return new Response(null, { status: 503, headers }) }
  }
}
