// Disposable protocol/receiver fixture, not a production MCP or authorization server.
import { createServer } from 'node:http'
import { createHash, randomBytes, timingSafeEqual } from 'node:crypto'

const origin = process.env.FIXTURE_ORIGIN
if (!origin || !/^https:\/\/[a-z0-9.-]+$/.test(origin)) throw new Error('An exact HTTPS fixture origin is required')
const callback = process.env.FIXTURE_OAUTH_CALLBACK
const adminKey = process.env.FIXTURE_ADMIN_KEY
if (!callback || !adminKey) throw new Error('Fixture callback and administrative key are required')
const actors = JSON.parse(process.env.FIXTURE_ACTORS_JSON ?? '{"fixture-a":"sub-a","fixture-b":"sub-b"}')
const subscriptions = new Map(Object.entries(actors).map(([actorId, id]) => [id, { id, actorId, plan: 'Example monthly plan', status: 'ACTIVE', cancellationEffective: 'end-of-current-period' }]))
const clients = new Map(), codes = new Map(), access = new Map(), refresh = new Map()
const counts = { initializations: 0, listCalls: 0, publicReads: 0, businessReads: 0, cancellations: 0, deniedMutations: 0, tokenExchanges: 0, tokenRefreshes: 0, revocations: 0 }
const secret = () => randomBytes(32).toString('base64url')
const equal = (a, b) => typeof a === 'string' && typeof b === 'string' && a.length === b.length && timingSafeEqual(Buffer.from(a), Buffer.from(b))
const send = (response, status, body, headers = {}) => {
  response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', ...headers })
  response.end(body === undefined ? undefined : JSON.stringify(body))
}
const input = async request => {
  const parts = []; let size = 0
  for await (const chunk of request) { size += chunk.length; if (size > 262144) throw new Error('REQUEST_LIMIT'); parts.push(chunk) }
  const text = Buffer.concat(parts).toString('utf8')
  return request.headers['content-type']?.startsWith('application/x-www-form-urlencoded') ? Object.fromEntries(new URLSearchParams(text)) : text ? JSON.parse(text) : {}
}
const tool = (name, description, properties, required = []) => ({ name, title: name, description,
  inputSchema: { type: 'object', properties, required, additionalProperties: false },
  outputSchema: { type: 'object' }, annotations: { readOnlyHint: name !== 'cancel_subscription' } })
const publicTools = [tool('search_guides', 'Find public fictional service guides.', { query: { type: 'string' } }, ['query'])]
const businessTools = [
  tool('get_subscription', 'Read the subscription belonging to the verified requesting customer.', { actorId: { type: 'string' } }, ['actorId']),
  tool('cancel_subscription', 'Cancel the selected subscription only when it belongs to the verified requesting customer. Cancellation takes effect at the end of the current period.',
    { actorId: { type: 'string' }, subscriptionId: { type: 'string' } }, ['actorId', 'subscriptionId']),
  tool('schema_example', 'Return synthetic schema qualification data.', { filter: { type: ['string', 'null'] }, limit: { oneOf: [{ type: 'number', minimum: 0.5 }, { type: 'null' }] } }),
]
const metadata = {
  issuer: origin, authorization_endpoint: `${origin}/oauth/authorize`, token_endpoint: `${origin}/oauth/token`,
  registration_endpoint: `${origin}/oauth/register`, revocation_endpoint: `${origin}/oauth/revoke`,
  response_types_supported: ['code'], grant_types_supported: ['authorization_code', 'refresh_token', 'client_credentials'],
  code_challenge_methods_supported: ['S256'], token_endpoint_auth_methods_supported: ['none', 'client_secret_post', 'client_secret_basic'],
  scopes_supported: ['subscriptions:read', 'subscriptions:cancel'],
}
function issueTokens() {
  const accessToken = secret(), refreshToken = secret()
  access.set(accessToken, Date.now() + 60000); refresh.set(refreshToken, true)
  return { access_token: accessToken, refresh_token: refreshToken, token_type: 'Bearer', expires_in: 60, scope: 'subscriptions:read subscriptions:cancel' }
}
function businessAuthorized(request) {
  const header = request.headers.authorization
  const token = header?.startsWith('Bearer ') ? header.slice(7) : null
  return token && ((access.get(token) ?? 0) > Date.now() || equal(token, process.env.FIXTURE_BUSINESS_KEY))
    || equal(request.headers['x-mcp-api-key'], process.env.FIXTURE_BUSINESS_KEY)
}
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, origin)
    if (url.pathname === '/health') return send(response, 200, { status: 'UP', synthetic: true })
    if (url.pathname === '/admin/evidence') {
      if (!equal(request.headers['x-fixture-admin-key'], adminKey)) return send(response, 403, { error: 'denied' })
      return send(response, 200, { counts, subscriptions: [...subscriptions.values()].map(({ actorId, ...value }) => value) })
    }
    if (url.pathname === '/.well-known/oauth-protected-resource/business/mcp') return send(response, 200, {
      resource: `${origin}/business/mcp`, authorization_servers: [origin], scopes_supported: metadata.scopes_supported,
      bearer_methods_supported: ['header'],
    })
    if (url.pathname === '/.well-known/oauth-authorization-server' || url.pathname === '/.well-known/openid-configuration') return send(response, 200, metadata)
    if (url.pathname === '/oauth/register' && request.method === 'POST') {
      const body = await input(request)
      if (!Array.isArray(body.redirect_uris) || body.redirect_uris.some(uri => uri !== callback)) return send(response, 400, { error: 'invalid_redirect_uri' })
      const id = secret(); clients.set(id, { redirectUri: callback, secret: secret() })
      return send(response, 201, { ...body, client_id: id, client_secret: clients.get(id).secret, token_endpoint_auth_method: body.token_endpoint_auth_method ?? 'none' })
    }
    if (url.pathname === '/oauth/authorize') {
      const params = url.searchParams
      const client = clients.get(params.get('client_id'))
      if (!client || params.get('redirect_uri') !== callback || params.get('code_challenge_method') !== 'S256'
        || !params.get('code_challenge') || !params.get('state') || params.get('resource') !== `${origin}/business/mcp`) return send(response, 400, { error: 'invalid_request' })
      if (!params.has('approve')) {
        const approve = new URL(url); approve.searchParams.set('approve', 'yes')
        const deny = new URL(callback); deny.searchParams.set('error', 'access_denied'); deny.searchParams.set('state', params.get('state'))
        response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store', 'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'" })
        return response.end(`<!doctype html><title>Approve synthetic business connection</title><main style="font-family:system-ui;max-width:560px;margin:80px auto;color:#172d29"><h1>Synthetic business integration</h1><p>Connect the fictional subscription service to your Smart Brain. No real subscription or personal account is involved.</p><p><a href="${approve.href.replaceAll('&', '&amp;')}">Approve company connection</a></p><p><a href="${deny.href.replaceAll('&', '&amp;')}">Cancel</a></p></main>`)
      }
      const code = secret(); codes.set(code, { clientId: params.get('client_id'), challenge: params.get('code_challenge'), expiresAt: Date.now() + 60000 })
      const location = new URL(callback); location.searchParams.set('code', code); location.searchParams.set('state', params.get('state'))
      response.writeHead(302, { Location: location.href, 'Cache-Control': 'no-store' }); return response.end()
    }
    if (url.pathname === '/oauth/token' && request.method === 'POST') {
      const body = await input(request)
      if (body.resource !== `${origin}/business/mcp`) return send(response, 400, { error: 'invalid_target' })
      if (body.grant_type === 'authorization_code') {
        const grant = codes.get(body.code); codes.delete(body.code)
        const basic = request.headers.authorization?.startsWith('Basic ') ? Buffer.from(request.headers.authorization.slice(6), 'base64').toString('utf8').split(':')[0] : null
        if (!grant || grant.expiresAt <= Date.now() || grant.clientId !== (body.client_id ?? basic) || body.redirect_uri !== callback
          || createHash('sha256').update(body.code_verifier ?? '').digest('base64url') !== grant.challenge) return send(response, 400, { error: 'invalid_grant' })
        counts.tokenExchanges++; return send(response, 200, issueTokens())
      }
      if (body.grant_type === 'refresh_token') {
        if (!refresh.delete(body.refresh_token)) return send(response, 400, { error: 'invalid_grant' })
        counts.tokenRefreshes++; return send(response, 200, issueTokens())
      }
      if (body.grant_type === 'client_credentials') {
        const basic = request.headers.authorization?.startsWith('Basic ')
          ? Buffer.from(request.headers.authorization.slice(6), 'base64').toString('utf8').split(':') : []
        if (!equal(body.client_id ?? basic[0], process.env.FIXTURE_CLIENT_ID)
          || !equal(body.client_secret ?? basic[1], process.env.FIXTURE_CLIENT_SECRET)) return send(response, 401, { error: 'invalid_client' })
        counts.tokenExchanges++
        const tokens = issueTokens(); refresh.delete(tokens.refresh_token); delete tokens.refresh_token
        return send(response, 200, tokens)
      }
      return send(response, 400, { error: 'unsupported_grant_type' })
    }
    if (url.pathname === '/oauth/revoke' && request.method === 'POST') {
      const body = await input(request); access.delete(body.token); refresh.delete(body.token); counts.revocations++
      return send(response, 200, {})
    }
    const isPublic = url.pathname === '/public/mcp', isBusiness = url.pathname === '/business/mcp'
    if (!isPublic && !isBusiness) return send(response, 404, { error: 'not_found' })
    if (isBusiness && !businessAuthorized(request)) return send(response, 401, { error: 'authorization_required' }, {
      'WWW-Authenticate': `Bearer resource_metadata="${origin}/.well-known/oauth-protected-resource/business/mcp"`,
    })
    if (request.method !== 'POST') return send(response, 405, { error: 'post_required' })
    const body = await input(request), answer = result => send(response, 200, { jsonrpc: '2.0', id: body.id, result })
    if (body.method === 'initialize') { counts.initializations++; return answer({ protocolVersion: body.params?.protocolVersion ?? '2025-11-25', capabilities: { tools: {} }, serverInfo: { name: 'LoomAI synthetic MCP qualification', version: '1.0.0' } }) }
    if (body.method === 'notifications/initialized' || body.method === 'notifications/cancelled') return send(response, 202)
    if (body.method === 'tools/list') {
      counts.listCalls++
      if (isPublic) return answer({ tools: publicTools })
      if (!body.params?.cursor) return answer({ tools: businessTools.slice(0, 2), nextCursor: 'schema-page' })
      if (body.params.cursor === 'schema-page') return answer({ tools: businessTools.slice(2) })
      return send(response, 400, { error: 'invalid_cursor' })
    }
    if (body.method === 'tools/call') {
      const { name, arguments: args = {} } = body.params ?? {}
      let value
      if (isPublic && name === 'search_guides') {
        counts.publicReads++; value = { guides: [{ title: 'Fictional service guide', text: 'A subscription may be cancelled at the end of its current period. An authorized customer can request cancellation from their signed-in support workspace.' }] }
      } else if (isBusiness && name === 'get_subscription') {
        counts.businessReads++; value = [...subscriptions.values()].filter(item => item.actorId === args.actorId).map(({ actorId, ...item }) => item)[0]
      } else if (isBusiness && name === 'cancel_subscription') {
        const subscription = subscriptions.get(args.subscriptionId)
        if (!subscription || subscription.actorId !== args.actorId) {
          counts.deniedMutations++; return answer({ isError: true, content: [{ type: 'text', text: 'No authorized subscription matched.' }], structuredContent: { errorCode: 'SUBSCRIPTION_ACCESS_DENIED' } })
        }
        counts.cancellations++; subscription.status = 'CANCELLATION_SCHEDULED'
        value = { receiptId: `fixture-receipt-${counts.cancellations}`, subscriptionId: subscription.id, status: subscription.status, cancellationEffective: subscription.cancellationEffective }
      } else if (isBusiness && name === 'schema_example') value = { received: args }
      else return answer({ isError: true, content: [{ type: 'text', text: 'Tool not available in this connection.' }] })
      if (!value) return answer({ isError: true, content: [{ type: 'text', text: 'No authorized record matched.' }] })
      return answer({ content: [{ type: 'text', text: JSON.stringify(value) }], structuredContent: value, isError: false })
    }
    return send(response, 200, { jsonrpc: '2.0', id: body.id, error: { code: -32601, message: 'Method not found' } })
  } catch { return send(response, 400, { error: 'invalid_fixture_request' }) }
})
server.listen(Number(process.env.PORT ?? 8080), '0.0.0.0', () => {
  console.log('Synthetic MCP qualification fixture ready')
  process.send?.({ port: server.address().port })
})
