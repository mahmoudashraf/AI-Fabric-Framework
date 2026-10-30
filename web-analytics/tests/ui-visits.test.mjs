import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { createHandler } from '../supabase/functions/ui-visits/handler.mjs'
import { pageCategory, siteForOrigin } from '../supabase/functions/ui-visits/policy.mjs'

const origin = 'https://console.loomai.pro'
const event = { page: '/deployments', viewport: 'desktop' }
const request = (body = event, options = {}) => new Request('https://collector.test/ui-visits', {
  method: 'POST', headers: { Origin: origin }, body: JSON.stringify(body), ...options,
})

test('only known production hosts and one-level first-party app hosts are counted', () => {
  for (const host of ['loomai.pro', 'www.loomai.pro', 'console.loomai.pro', 'desk.loomai.pro', 'partners.loomai.pro',
    'engage.loomai.pro', 'engage-canary.loomai.pro', 'shopify-bridge.loomai.pro', 'ai-fabric.dev']) {
    assert.ok(siteForOrigin('https://' + host), host)
  }
  assert.deepEqual(siteForOrigin('https://customer.apps.loomai.pro'), { site: 'engage', hostname: 'apps.loomai.pro' })
  for (const host of ['http://console.loomai.pro', 'https://console.loomai.pro:8443', 'https://console.loomai.pro.evil.test',
    'https://example.com', 'https://deep.customer.apps.loomai.pro', 'https://api.loomai.pro', 'https://localhost', 'null', null]) {
    assert.equal(siteForOrigin(host), null, host)
  }
})

test('page categories never contain resource IDs, account names or unrecognized path text', () => {
  assert.equal(pageCategory('console', '/deployments/customer-secret'), '/deployments')
  assert.equal(pageCategory('ai-fabric', '/docs/private-person@example.com'), '/docs')
  assert.equal(pageCategory('loomai-site', '/arbitrary-person@example.com'), '/other')
  assert.equal(pageCategory('engage', '/arbitrary-person@example.com'), '/workspace')
})

test('public ingestion uses only fixed aggregate arguments and server-only credentials', async () => {
  let forwarded
  const handler = createHandler({ supabaseUrl: 'https://project.test', serviceKey: 'server-secret', fetch: async (url, init) => {
    forwarded = { url, init }; return new Response(null, { status: 204 })
  } })
  const response = await handler(request())
  assert.equal(response.status, 204)
  assert.equal(response.headers.get('access-control-allow-origin'), origin)
  assert.equal(response.headers.get('access-control-allow-credentials'), null)
  assert.deepEqual(JSON.parse(forwarded.init.body), { p_site: 'console', p_hostname: 'console.loomai.pro', p_page: '/deployments', p_viewport: 'desktop' })
  assert.equal(forwarded.init.headers.apikey, 'server-secret')
  assert.equal(await response.text(), '')
})

test('unknown origin, GET statistics, extra data, arbitrary paths and oversized bodies are denied', async () => {
  let writes = 0
  const handler = createHandler({ supabaseUrl: 'https://project.test', serviceKey: 'server-secret', fetch: async () => { writes++; return new Response() } })
  assert.equal((await handler(request(event, { headers: { Origin: 'https://evil.test' } }))).status, 403)
  assert.equal((await handler(new Request('https://collector.test', { headers: { Origin: origin } }))).status, 405)
  for (const body of [{ ...event, email: 'private@example.test' }, { ...event, page: '/deployments/secret' },
    { ...event, viewport: '1920x1080' }, [], null, { page: 'x'.repeat(2000), viewport: 'desktop' }]) {
    assert.equal((await handler(request(body))).status, 400)
  }
  assert.equal(writes, 0)
})

test('preflight has no write; database/network failures are generic and contain no credentials', async () => {
  const handler = createHandler({ supabaseUrl: 'https://project.test', serviceKey: 'server-secret', fetch: async () => { throw new Error('server-secret') } })
  const preflight = await handler(new Request('https://collector.test', { method: 'OPTIONS', headers: { Origin: origin } }))
  assert.equal(preflight.status, 204)
  const failure = await handler(request()); assert.equal(failure.status, 503); assert.equal(await failure.text(), '')
  assert.equal((await createHandler({})(request())).status, 503)
})

async function browserFixture({ dnt = false, gpc = false, hidden = false, host = 'console.loomai.pro', failing = false } = {}) {
  const listeners = new Map(), calls = []
  const location = new URL('https://' + host + '/deployments?email=private@example.test#secret')
  const window = { location, innerWidth: 1280, history: {}, addEventListener: (type, listener) => listeners.set(type, listener) }
  for (const name of ['pushState', 'replaceState']) window.history[name] = (_state, _unused, path) => { if (path) location.href = new URL(path, location).href; return 'original-result' }
  const document = { visibilityState: hidden ? 'hidden' : 'visible', addEventListener: (type, listener) => listeners.set(type, listener) }
  const sandbox = { window, document, navigator: { doNotTrack: dnt ? '1' : '0', globalPrivacyControl: gpc }, URL,
    fetch: (url, init) => { calls.push({ url, init }); return failing ? Promise.reject(new Error('offline')) : Promise.resolve(new Response()) } }
  vm.runInNewContext(await readFile(new URL('../../Platfrom/ui/public/assets/loomai-ui-visits.js', import.meta.url), 'utf8'), sandbox)
  return { window, document, listeners, calls }
}

test('browser emits only two safe fields; no query/hash/referrer, credentials, IDs or storage', async () => {
  const { calls } = await browserFixture()
  assert.equal(calls.length, 1)
  assert.deepEqual(JSON.parse(calls[0].init.body), event)
  assert.equal(calls[0].init.credentials, 'omit'); assert.equal(calls[0].init.referrerPolicy, 'no-referrer')
  assert.ok(!JSON.stringify(calls).includes('private@example.test'))
})

test('SPA navigation and visibility are counted without duplicate same-path events or query-only changes', async () => {
  const { window, document, listeners, calls } = await browserFixture({ hidden: true })
  assert.equal(calls.length, 0)
  document.visibilityState = 'visible'; listeners.get('visibilitychange')(); assert.equal(calls.length, 1)
  listeners.get('pageshow')(); assert.equal(calls.length, 1)
  assert.equal(window.history.pushState({}, '', '/ai-workspaces?customer=secret'), 'original-result')
  assert.equal(calls.length, 2); assert.deepEqual(JSON.parse(calls[1].init.body), { page: '/ai-workspaces', viewport: 'desktop' })
  window.history.replaceState({}, '', '/ai-workspaces?other=secret'); assert.equal(calls.length, 2)
  window.location.pathname = '/home'; listeners.get('popstate')(); assert.equal(calls.length, 3)
})

test('DNT/GPC, local/dev/foreign domains and failed telemetry never affect UI navigation', async () => {
  for (const options of [{ dnt: true }, { gpc: true }, { host: 'localhost' }, { host: 'foreign.test' }]) {
    assert.equal((await browserFixture(options)).calls.length, 0)
  }
  const { window, calls } = await browserFixture({ failing: true })
  assert.equal(window.history.pushState({}, '', '/home'), 'original-result'); assert.equal(calls.length, 2)
  await new Promise(resolve => setImmediate(resolve))
})
