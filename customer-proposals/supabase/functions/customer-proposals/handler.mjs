const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/
const secretPattern = /^[A-Za-z0-9_-]{32,128}$/
const encoder = new TextEncoder()
const headers = { 'Content-Type': 'application/json', 'Cache-Control': 'private, no-store', 'X-Content-Type-Options': 'nosniff' }
const reply = (status, value) => new Response(JSON.stringify(value), { status, headers })
const hex = bytes => Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
const digest = async value => hex(new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(value))))

async function equalSecrets(first, second) {
  if (!first || !second) return false
  const a = await digest(first), b = await digest(second)
  let difference = 0
  for (let i = 0; i < a.length; i++) difference |= a.charCodeAt(i) ^ b.charCodeAt(i)
  return difference === 0
}

async function boundedBody(request) {
  const reader = request.body?.getReader()
  if (!reader) throw new Error('INVALID_BODY')
  const chunks = []
  let size = 0
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    size += value.byteLength
    if (size > 2048) { await reader.cancel(); throw new Error('INVALID_BODY') }
    chunks.push(value)
  }
  const body = new Uint8Array(size)
  let offset = 0
  for (const chunk of chunks) { body.set(chunk, offset); offset += chunk.length }
  return JSON.parse(new TextDecoder().decode(body))
}

export function createHandler({ supabaseUrl, serviceKey, gatewayKey, fetchImpl = fetch }) {
  return async request => {
    if (request.method !== 'POST') return reply(405, { error: 'METHOD_NOT_ALLOWED' })
    if (!gatewayKey || !serviceKey || !supabaseUrl) return reply(503, { error: 'UNAVAILABLE' })
    if (!await equalSecrets(request.headers.get('x-proposal-gateway-key'), gatewayKey)) return reply(403, { error: 'ACCESS_DENIED' })
    let input
    try { input = await boundedBody(request) } catch { return reply(400, { error: 'INVALID_REQUEST' }) }
    if (!input || typeof input !== 'object' || typeof input.slug !== 'string' || input.slug.length > 64 || !slugPattern.test(input.slug)) return reply(400, { error: 'INVALID_REQUEST' })
    const rpc = async (name, payload) => {
      const response = await fetchImpl(`${supabaseUrl}/rest/v1/rpc/${name}`, {
        method: 'POST', headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, 'Content-Type': 'application/json' },
        body: JSON.stringify(payload), signal: AbortSignal.timeout(15000), redirect: 'error',
      })
      if (!response.ok) throw new Error('UNAVAILABLE')
      return response.json()
    }
    try {
      if (input.action === 'exchange') {
        if (!secretPattern.test(input.code || '') || !/^[a-f0-9]{64}$/.test(input.clientDigest || '')) return reply(401, { error: 'ACCESS_DENIED' })
        const token = btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32)))).replaceAll('+','-').replaceAll('/','_').replaceAll('=','')
        const result = await rpc('exchange_customer_proposal_code', { p_slug: input.slug, p_code_digest: await digest(input.code), p_token_digest: await digest(token), p_client_digest: input.clientDigest })
        if (result.error) return reply(result.error === 'RATE_LIMITED' ? 429 : 401, { error: result.error })
        return reply(200, { token, maxAge: result.maxAge })
      }
      if (!['content','download','logout'].includes(input.action)) return reply(400, { error: 'INVALID_REQUEST' })
      if (!secretPattern.test(input.token || '')) return reply(401, { error: 'ACCESS_DENIED' })
      const tokenDigest = await digest(input.token)
      const result = input.action === 'logout'
        ? await rpc('close_customer_proposal_session', { p_slug: input.slug, p_token_digest: tokenDigest })
        : await rpc('read_customer_proposal', { p_slug: input.slug, p_token_digest: tokenDigest, p_download: input.action === 'download' })
      return result.error ? reply(result.error === 'NOT_FOUND' ? 404 : 401, { error: result.error }) : reply(200, result)
    } catch { return reply(503, { error: 'UNAVAILABLE' }) }
  }
}
