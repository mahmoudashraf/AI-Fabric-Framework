import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdirSync, writeFileSync } from 'node:fs'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const output = process.env.ENGAGE_SAVE_OUTPUT ?? `/private/tmp/01045-engage-save-ui-${Date.now()}`
mkdirSync(output, { recursive: true })
const hash = (kind, value) => {
  const canonical = item => Array.isArray(item) ? `[${item.map(canonical).join(',')}]`
    : item !== null && typeof item === 'object' ? `{${Object.keys(item).sort().map(key => JSON.stringify(key) + ':' + canonical(item[key])).join(',')}}`
      : JSON.stringify(item)
  return `${kind}:${createHash('sha256').update(canonical(value)).digest('hex')}`
}
const interaction = { packCode: 'generic-workspace', packVersion: '1.0.0', connectionMode: 'PUBLIC_RUNTIME_ANONYMOUS',
  connectionProfileCode: 'runtime-anonymous-direct', connectionProfileVersion: '1.0.0', deliveryMode: 'STANDALONE',
  connectionConfiguration: {}, configuration: { app: { assistantLabel: 'Support' }, capabilities: { currentPage: true } } }
const theme = { primaryColor: '#145d54' }
const authentication = { profileCode: 'none', profileVersion: '1.0.0', interactionPolicy: 'PUBLIC', configuration: {} }
const offer = { id: 'offer', fingerprint: 'reviewed', entitlement: { customerId: 'customer', status: 'ACTIVE', revision: 1 },
  settings: [{ setting: 'APP_TITLE', type: 'TEXT', maxLength: 120, allowedValues: [] },
    ...[['INTERACTION_PROFILE_ID', hash('workspace', interaction)], ['THEME_PROFILE_ID', hash('theme', theme)],
      ['AUTH_PROFILE_ID', hash('auth', authentication)]].map(([setting, id]) => ({ setting, type: 'CHOICE', maxLength: 100, allowedValues: [id] }))],
  workspaceProfiles: [{ id: 'public', label: 'Public support', interaction, theme, authentication }],
  targets: [{ id: 'production', capabilities: ['APP_CONFIGURE', 'AUTH_CONFIGURE', 'DOMAIN_CONFIGURE'] }] }
const consumer = { consumerId: 'chosen', customerId: 'customer', status: 'ACTIVE', displayName: 'Support assignment',
  boundDeploymentId: 'deployment', boundTargetProfileId: 'production', boundReleaseId: 'release', bindingRevision: 1 }
const state = { calls: [], errors: [], probeDenied: true, saveDenied: false, apps: [] }
const report = { status: 'RUNNING', checks: [] }
const collection = '/api/platform/customers/customer/ai-workspace-installations'
let base, browser, page
const server = await createServer({ configFile: false, envFile: false, esbuild: { jsx: 'automatic' },
  server: { host: '127.0.0.1', port: 0 }, plugins: [{ name: 'engage-save-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === '/engage-entry.jsx') return '\0engage-entry.jsx'
      if (id.endsWith('/auth/PlatformAuthProvider')) return '\0engage-auth.js'
    },
    load(id) {
      if (id === '\0engage-auth.js') return `export function usePlatformAuth(){return {session:{actorId:'owner',role:'CUSTOMER_ADMIN',customerId:'customer'}}}`
      if (id === '\0engage-entry.jsx') return `import React from 'react';import{createRoot}from'react-dom/client';
        import{QueryClient,QueryClientProvider}from'@tanstack/react-query';import{AIWorkspacesPage}from'/src/pages/AIWorkspacesPage.tsx';
        const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
        createRoot(document.getElementById('root')).render(React.createElement(QueryClientProvider,{client},React.createElement(AIWorkspacesPage,{initialConsumerId:'chosen'})));`
    },
    configureServer(vite) { vite.middlewares.use((req, res, next) => {
      if (req.url !== '/engage-fixture') return next()
      res.setHeader('Content-Type', 'text/html')
      res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1" /></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/engage-entry.jsx"></script></body></html>')
    }) },
  }] })
try {
  await server.listen(); base = server.resolvedUrls.local[0]
  browser = await chromium.launch(); page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  page.setDefaultTimeout(15000)
  page.on('pageerror', error => state.errors.push(error.message))
  await page.route('**/*', route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname, method = request.method()
    if (url.origin !== new URL(base).origin) { state.errors.push('Escaped fixture'); return route.abort() }
    if (!path.startsWith('/api/')) return route.continue()
    state.calls.push({ path, method, key: request.headers()['idempotency-key'] })
    const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/platform/customers') return json([{ id: 'customer', name: 'Customer', consumers: [consumer] }])
    if (path === '/api/customer-self-service/catalogue') return json([offer])
    if (path === '/api/customer-self-service/deployments') return json([{ deploymentId: 'deployment', offerId: 'offer', offerFingerprint: 'reviewed' }])
    if (path === '/api/platform/ai-workspaces/catalog') return json({ assetsReady: true, connectionProfiles: [], experiencePacks: [], authenticationProfiles: [] })
    if (path.endsWith('/placement-evidence') && method === 'POST') {
      assert.equal(path, '/api/customer-self-service/catalogue/offer/targets/production/placement-evidence')
      return state.probeDenied ? json({ message: 'Placement check denied' }, 409) : json({ id: 'fresh' })
    }
    if (path === collection && method === 'GET') return json(state.apps)
    if (path === collection && method === 'POST') {
      if (state.saveDenied) return json({ message: 'Save blocked by current entitlement' }, 409)
      const payload = request.postDataJSON()
      state.apps.push({ ...payload, installationId: 'created', customerId: 'customer', status: 'DRAFT',
        rowVersion: 0, domains: [], ready: false, readinessChecks: [], appUrl: 'https://support.apps.example' })
      return json(state.apps[0], 201)
    }
    state.errors.push(`Unhandled ${method} ${path}`)
    return json({ message: 'Unhandled fixture request' }, 500)
  })
  await page.goto(base + 'engage-fixture')
  await page.getByRole('button', { name: 'Create AI App', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Create AI App', exact: true })
  await dialog.getByLabel('Workspace name', { exact: true }).fill('My support workspace')
  await dialog.getByLabel('App slug', { exact: true }).fill('my-support')
  assert.equal(state.calls.filter(call => call.method === 'POST').length, 0)
  const save = () => dialog.getByRole('button', { name: 'Save draft', exact: true }).click()
  await save()
  await dialog.getByRole('alert').filter({ hasText: 'Placement check denied' }).waitFor()
  assert.equal(await dialog.getByLabel('Workspace name', { exact: true }).inputValue(), 'My support workspace')
  assert.equal(state.calls.filter(call => call.path === collection && call.method === 'POST').length, 0)
  report.checks.push({ name: 'NO_AUTOMATIC_PROBE_DENIED_CHECK_NO_WRITE_VISIBLE_ERROR_DRAFT_RETAINED', status: 'PASS' })
  state.probeDenied = false; state.saveDenied = true
  await save()
  await dialog.getByRole('alert').filter({ hasText: 'Save blocked by current entitlement' }).waitFor()
  await page.screenshot({ path: `${output}/save-error-in-dialog.png`, fullPage: true })
  const firstKey = state.calls.find(call => call.path === collection && call.method === 'POST').key
  state.saveDenied = false
  await save()
  await dialog.waitFor({ state: 'hidden' })
  await page.getByText('AI Workspace My support workspace saved.', { exact: true }).waitFor()
  const saveCalls = state.calls.filter(call => call.path === collection && call.method === 'POST')
  assert.equal(saveCalls.length, 2); assert.equal(saveCalls[1].key, firstKey)
  for (const call of saveCalls) {
    const index = state.calls.indexOf(call)
    assert.equal(state.calls[index - 1].path.endsWith('/placement-evidence'), true)
  }
  assert.equal(state.calls.some(call => /admission|production-apply|staging-releases/.test(call.path)), false)
  await page.getByRole('button', { name: 'Create AI App', exact: true }).click()
  assert.equal(await dialog.getByRole('alert').filter({ hasText: 'Save blocked' }).count(), 0)
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  assert.deepEqual(state.errors, [])
  report.checks.push({ name: 'EXPLICIT_RETRY_SAME_KEY_EXACT_TARGET_NO_ADMISSION_OR_DEPLOYMENT', status: 'PASS' })
  report.status = 'PASS'
} catch (error) {
  report.status = 'FAIL'; report.error = error.stack; process.exitCode = 1
  await page?.screenshot({ path: `${output}/failure.png`, fullPage: true }).catch(() => {})
} finally {
  await browser?.close(); await server.close()
  writeFileSync(`${output}/report.actual.json`, JSON.stringify({ ...report, calls: state.calls, errors: state.errors }, null, 2))
  console.log(JSON.stringify({ ...report, evidenceRoot: output }))
}
