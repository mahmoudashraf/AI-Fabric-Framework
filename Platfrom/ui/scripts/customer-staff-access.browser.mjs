import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'

const root = fileURLToPath(new URL('..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'customer-staff-access-vite-'))
const screenshots = await mkdtemp(join(tmpdir(), 'customer-staff-access-screenshots-'))
const server = await createServer({ root, cacheDir, configFile: false, envFile: false,
  server: { host: '127.0.0.1', port: 0 },
  plugins: [{ name: 'customer-staff-access-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === '/__staff-access-entry.jsx') return '\0staff-access-entry.jsx'
      if (id === '/__staff-access-auth' || id.endsWith('/auth/PlatformAuthProvider')) return '\0staff-access-auth.js'
    },
    transform(code, id) {
      if (id === '\0staff-access-entry.jsx') return transformWithEsbuild(code, 'staff-access-entry.jsx', { loader: 'jsx', jsx: 'automatic' })
    },
    load(id) {
      if (id === '\0staff-access-auth.js') return `import { createContext, useContext } from 'react';
        export const Auth = createContext(null); export const usePlatformAuth = () => useContext(Auth);`
      if (id === '\0staff-access-entry.jsx') return `
        import React, { useState } from 'react'; import { createRoot } from 'react-dom/client';
        import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
        import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
        import { Auth } from '/__staff-access-auth';
        import { setPlatformAccessTokenProvider } from '/src/api/platformApi.ts';
        import { CustomerStaffAccessPanel } from '/src/components/customer/CustomerStaffAccessPanel.tsx';
        const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } });
        window.__queryClient = client;
        function App() {
          const [session, setSession] = useState({ authenticated: true, actorId: 'actor-a', customerId: 'customer-a', role: 'CUSTOMER_ADMIN', authenticationMode: 'SUPABASE' });
          const [loading, setLoading] = useState(false);
          window.__scope = patch => setSession(value => ({ ...value, ...patch })); window.__loading = setLoading;
          window.__token = session.customerId + ':' + session.actorId;
          setPlatformAccessTokenProvider(async () => {
            if (window.__credentialWait) { window.__credentialStarted = true; await window.__credentialWait; }
            return window.__token;
          });
          return <Auth.Provider value={{ session, isLoading: loading }}><CustomerStaffAccessPanel /></Auth.Provider>;
        }
        createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}>
          <ThemeProvider theme={createTheme()}><CssBaseline /><main style={{ maxWidth: 1000, padding: 16, margin: 'auto' }}><App /></main></ThemeProvider>
        </QueryClientProvider>);
      `
    },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (new URL(req.url, 'http://fixture').pathname !== '/__staff-access') return next()
      res.setHeader('Content-Type', 'text/html')
      res.end(await vite.transformIndexHtml('/__staff-access', `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div>
        <script>window.__PLATFORM_RUNTIME_CONFIG__ = { apiBaseUrl: location.origin };</script><script type="module" src="/__staff-access-entry.jsx"></script></body></html>`))
    }) },
  }],
})

const row = (customerId = 'customer-a', overrides = {}) => ({ id: `grant-${customerId}`, customerId, userId: 'operator-one',
  email: 'engineer@loomai.example', displayName: `Engineer for ${customerId}`, status: 'ACTIVE',
  grantedAt: '2026-11-06T12:00:00Z', revokedAt: null, ...overrides })
await server.listen()
const baseUrl = `http://127.0.0.1:${server.httpServer.address().port}`
const browser = await chromium.launch({ headless: true })
let passed = 0
async function run(name, test, options = {}) {
  const page = await browser.newPage({ viewport: options.mobile ? { width: 390, height: 844 } : { width: 1280, height: 900 } })
  page.setDefaultTimeout(12000)
  const requests = [], unexpected = [], errors = [], gates = new Map(), releases = []
  const records = new Map(['customer-a', 'customer-b'].map(id => [id, options.empty ? [] : [row(id)]]))
  let failList = options.failList, foreignList = options.foreignList, nextGrant = null, nextRevoke = null, failRevoke = false
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', route => {
    if (new URL(route.request().url()).origin === baseUrl) return route.continue()
    unexpected.push(route.request().url()); return route.abort()
  })
  await page.route(`${baseUrl}/api/**`, async route => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method()
    const body = request.postData() ? JSON.parse(request.postData()) : null
    const token = request.headers().authorization?.replace('Bearer ', '') ?? ''
    const customerId = token.split(':')[0]
    requests.push({ path, method, body, token })
    const send = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) }).catch(() => {})
    if (!path.startsWith('/api/customer/staff-access') || !records.has(customerId)) {
      unexpected.push(`${method} ${path}`); return send({ message: 'Unexpected fixture request' }, 404)
    }
    let value, status = 200
    if (method === 'GET') {
      if (failList) { failList = false; value = { message: 'Staff access is temporarily unavailable.' }; status = 503 }
      else value = foreignList ? [row(customerId), row('foreign-customer', { email: 'foreign@loomai.example', displayName: 'Foreign operator' })] : structuredClone(records.get(customerId))
    } else if (method === 'POST' && path === '/api/customer/staff-access') {
      assert.deepEqual(Object.keys(body), ['email'])
      if (body.email === 'admin@loomai.example') { value = { message: 'Global Platform administrators cannot be granted through customer collaboration access.' }; status = 403 }
      else if (body.email === 'unknown@loomai.example') { value = { message: 'No active LoomAI operator has this exact email.' }; status = 404 }
      else if (body.email === 'inactive@loomai.example') { value = { message: 'This LoomAI operator is not active.' }; status = 409 }
      else {
        const previous = records.get(customerId).find(item => item.email === body.email)
        value = previous ? { ...previous, status: 'ACTIVE', revokedAt: null, grantedAt: '2026-11-06T13:00:00Z' }
          : row(customerId, { email: body.email })
        records.set(customerId, [...records.get(customerId).filter(item => item.id !== value.id), value])
        if (nextGrant) { value = { ...value, ...nextGrant }; nextGrant = null }
      }
    } else if (method === 'DELETE') {
      const id = decodeURIComponent(path.split('/').pop())
      if (failRevoke) { failRevoke = false; value = { message: 'Access changed. Refresh before revoking.' }; status = 409 }
      else {
        const previous = records.get(customerId).find(item => item.id === id)
        assert.ok(previous, 'Revoke targets an existing own-customer grant')
        value = { ...previous, status: 'REVOKED', revokedAt: '2026-11-06T12:30:00Z' }
        records.set(customerId, records.get(customerId).map(item => item.id === id ? value : item))
        if (nextRevoke) { value = { ...value, ...nextRevoke }; nextRevoke = null }
      }
    } else { unexpected.push(`${method} ${path}`); return send({ message: 'Unexpected request' }, 404) }
    const gate = gates.get(`${method} ${customerId}`)
    if (gate) { gates.delete(`${method} ${customerId}`); gate.started(); await gate.wait }
    return send(value, status)
  })
  const hold = (method, customerId) => {
    let started, release
    const result = { started: new Promise(resolve => { started = resolve }), release: () => release() }
    gates.set(`${method} ${customerId}`, { started, wait: new Promise(resolve => { release = resolve }) })
    releases.push(result.release)
    return result
  }
  const scope = patch => page.evaluate(patch => window.__scope(patch), patch)
  const grant = async email => {
    await page.getByRole('textbox', { name: 'LoomAI staff email' }).fill(email)
    await page.getByRole('button', { name: 'Grant access', exact: true }).click()
  }
  const openRevoke = () => page.getByRole('button', { name: 'Revoke access for engineer@loomai.example', exact: true }).click()
  const initialHold = options.hold ? hold(...options.hold) : null
  try {
    await page.goto(`${baseUrl}/__staff-access`)
    await test({ page, requests, scope, grant, openRevoke, hold, initialHold,
      foreignList: value => { foreignList = value }, grantReceipt: value => { nextGrant = value },
      revokeReceipt: value => { nextRevoke = value }, failRevoke: () => { failRevoke = true } })
    assert.deepEqual(errors, [], 'No browser runtime errors')
    assert.deepEqual(unexpected, [], 'No global users, invites, emails or external network requests')
    passed++; console.log(`PASS ${name}`)
  } catch (error) {
    console.error({ name, errors, unexpected, requests, body: await page.locator('body').innerText() })
    throw error
  } finally { for (const release of releases) release(); await page.close() }
}

try {
  await run('grant, explicit revoke confirmation, cancel and regrant preserve scope and show dates', async t => {
    await t.page.getByText('No customer-granted LoomAI staff access.', { exact: true }).waitFor()
    assert.equal(await t.page.getByRole('textbox', { name: 'LoomAI staff email' }).inputValue(), '')
    await t.grant('engineer@loomai.example')
    await t.page.getByText('Access granted to engineer@loomai.example. No invitation email was sent.', { exact: true }).waitFor()
    await t.page.getByRole('listitem').filter({ hasText: 'Engineer for customer-a' }).waitFor()
    await t.openRevoke()
    const dialog = t.page.getByRole('dialog', { name: 'Revoke LoomAI staff access?' })
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    assert.equal(t.requests.filter(item => item.method === 'DELETE').length, 0)
    await t.openRevoke()
    await dialog.getByRole('button', { name: 'Revoke access', exact: true }).click()
    await t.page.getByText('Collaboration access revoked for engineer@loomai.example.', { exact: true }).waitFor()
    const record = t.page.getByRole('listitem')
    await record.getByText('Revoked', { exact: true }).waitFor()
    assert.equal(await record.locator('time').count(), 2)
    assert.equal(await t.page.getByRole('button', { name: 'Revoke access for engineer@loomai.example' }).count(), 0)
    await t.grant('engineer@loomai.example')
    await record.getByText('Active', { exact: true }).waitFor()
    assert.equal(await record.locator('time').count(), 1)
    assert.equal(await t.page.getByRole('listitem').count(), 1)
    assert.deepEqual(t.requests.filter(item => item.method !== 'GET').map(item => [item.method, item.path]), [
      ['POST', '/api/customer/staff-access'], ['DELETE', '/api/customer/staff-access/grant-customer-a'], ['POST', '/api/customer/staff-access'],
    ])
    assert.ok(t.requests.every(item => item.token === 'customer-a:actor-a'))
    await t.page.screenshot({ path: join(screenshots, 'desktop-grants.png'), fullPage: true })
  }, { empty: true })

  await run('load failures and backend eligibility errors remain visible without successful grant claims', async t => {
    await t.page.getByText('Staff access is temporarily unavailable.', { exact: true }).waitFor()
    await t.page.getByRole('button', { name: 'Retry', exact: true }).click()
    await t.page.getByRole('listitem').waitFor()
    for (const [email, message] of [
      ['admin@loomai.example', 'Global Platform administrators cannot be granted through customer collaboration access.'],
      ['unknown@loomai.example', 'No active LoomAI operator has this exact email.'],
      ['inactive@loomai.example', 'This LoomAI operator is not active.'],
    ]) {
      await t.grant(email)
      await t.page.getByText(message, { exact: true }).waitFor()
      assert.equal(await t.page.getByRole('textbox', { name: 'LoomAI staff email' }).inputValue(), email)
      assert.equal(await t.page.getByText(/Access granted to/).count(), 0)
    }
    assert.equal(await t.page.getByRole('listitem').count(), 1)
  }, { failList: true })

  await run('mixed-tenant reads reject all rows and can be explicitly refreshed', async t => {
    await t.page.getByText('Staff access does not match this customer workspace. Refresh before continuing.', { exact: true }).waitFor()
    assert.equal(await t.page.getByRole('listitem').count(), 0)
    assert.equal(await t.page.getByText('Foreign operator', { exact: true }).count(), 0)
    t.foreignList(false)
    await t.page.getByRole('button', { name: 'Retry', exact: true }).click()
    await t.page.getByText('Engineer for customer-a', { exact: true }).waitFor()
  }, { foreignList: true })

  await run('foreign grant receipts and wrong-user revoke receipts never become success state', async t => {
    await t.page.getByRole('listitem').waitFor()
    t.grantReceipt({ customerId: 'customer-b', displayName: 'Foreign operator' })
    await t.grant('engineer@loomai.example')
    await t.page.getByText('Staff access does not match this customer workspace. Refresh before continuing.', { exact: true }).waitFor()
    assert.equal(await t.page.getByText('Foreign operator', { exact: true }).count(), 0)
    assert.equal(await t.page.getByText(/Access granted to/).count(), 0)
    t.revokeReceipt({ userId: 'different-user' })
    await t.openRevoke()
    const dialog = t.page.getByRole('dialog')
    await dialog.getByRole('button', { name: 'Revoke access', exact: true }).click()
    await dialog.getByText('The returned staff member does not match the revoke request. Refresh before continuing.', { exact: true }).waitFor()
    assert.equal(await t.page.getByText(/Collaboration access revoked for/).count(), 0)
  })

  await run('revoke errors keep confirmation visible and require an explicit retry', async t => {
    await t.page.getByRole('listitem').waitFor()
    t.failRevoke()
    await t.openRevoke()
    const dialog = t.page.getByRole('dialog')
    await dialog.getByRole('button', { name: 'Revoke access', exact: true }).click()
    await dialog.getByText('Access changed. Refresh before revoking.', { exact: true }).waitFor()
    assert.equal(t.requests.filter(item => item.method === 'DELETE').length, 1)
    await dialog.getByRole('button', { name: 'Revoke access', exact: true }).click()
    await t.page.getByText('Collaboration access revoked for engineer@loomai.example.', { exact: true }).waitFor()
    assert.equal(t.requests.filter(item => item.method === 'DELETE').length, 2)
  })

  await run('retained handlers and pending grants cannot act on a newly authenticated customer', async t => {
    await t.page.getByRole('listitem').waitFor()
    await t.page.getByRole('textbox', { name: 'LoomAI staff email' }).fill('second@loomai.example')
    await t.page.evaluate(() => {
      const form = document.querySelector('form')
      window.__oldSubmit = form[Object.keys(form).find(key => key.startsWith('__reactProps'))].onSubmit
    })
    const pending = t.hold('POST', 'customer-a')
    await t.page.getByRole('button', { name: 'Grant access', exact: true }).click()
    await pending.started
    await t.scope({ actorId: 'actor-b', customerId: 'customer-b' })
    await t.page.getByText('Engineer for customer-b', { exact: true }).waitFor()
    await t.page.evaluate(() => window.__oldSubmit({ preventDefault() {} }))
    pending.release()
    assert.equal(await t.page.getByRole('textbox', { name: 'LoomAI staff email' }).inputValue(), '')
    assert.equal(t.requests.filter(item => item.method === 'POST').length, 1)
    assert.equal(await t.page.getByText(/Access granted to/).count(), 0)
    assert.equal(await t.page.getByText('Engineer for customer-a', { exact: true }).count(), 0)
    const keys = await t.page.evaluate(() => window.__queryClient.getQueryCache().getAll().map(query => query.queryKey))
    assert.ok(keys.some(key => key[0] === 'customer-staff-access' && key[1] === 'actor-b' && key[2] === 'customer-b'))
  })

  await run('a delayed old list and open revoke dialog do not survive account changes', async t => {
    await t.initialHold.started
    await t.scope({ actorId: 'actor-b', customerId: 'customer-b' })
    await t.page.getByText('Engineer for customer-b', { exact: true }).waitFor()
    t.initialHold.release()
    assert.equal(await t.page.getByText('Engineer for customer-a', { exact: true }).count(), 0)
    await t.openRevoke()
    await t.scope({ actorId: 'actor-a', customerId: 'customer-a' })
    await t.page.getByText('Engineer for customer-a', { exact: true }).waitFor()
    assert.equal(await t.page.getByRole('dialog').count(), 0)
    assert.equal(t.requests.filter(item => item.method === 'DELETE').length, 0)
  }, { hold: ['GET', 'customer-a'] })

  await run('returning to the same account does not reactivate its unmounted grant handler', async t => {
    await t.page.getByRole('listitem').waitFor()
    await t.page.getByRole('textbox', { name: 'LoomAI staff email' }).fill('second@loomai.example')
    await t.page.evaluate(() => {
      const form = document.querySelector('form')
      window.__oldSubmit = form[Object.keys(form).find(key => key.startsWith('__reactProps'))].onSubmit
    })
    await t.scope({ actorId: 'actor-b', customerId: 'customer-b' })
    await t.page.getByText('Engineer for customer-b', { exact: true }).waitFor()
    await t.scope({ actorId: 'actor-a', customerId: 'customer-a' })
    await t.page.getByText('Engineer for customer-a', { exact: true }).waitFor()
    await t.page.evaluate(() => window.__oldSubmit({ preventDefault() {} }))
    assert.equal(await t.page.getByRole('textbox', { name: 'LoomAI staff email' }).inputValue(), '')
    assert.equal(t.requests.filter(item => item.method !== 'GET').length, 0)
  })

  await run('staff roles, anonymous and API-key sessions cannot use the customer panel', async t => {
    await t.page.getByRole('listitem').waitFor()
    const count = t.requests.length
    for (const patch of [{ role: 'PLATFORM_ADMIN' }, { role: 'PLATFORM_OPERATOR' },
      { role: 'CUSTOMER_ADMIN', authenticationMode: 'API_KEY' }, { authenticated: false, authenticationMode: 'SUPABASE' }]) {
      await t.scope(patch)
      await t.page.getByText('A customer administrator session is required to manage LoomAI staff access.', { exact: true }).waitFor()
      assert.equal(await t.page.getByRole('textbox', { name: 'LoomAI staff email' }).count(), 0)
    }
    assert.equal(t.requests.length, count)
    await t.scope({ authenticated: true, authenticationMode: 'SESSION' })
    await t.page.getByRole('listitem').waitFor()
    const keys = await t.page.evaluate(() => window.__queryClient.getQueryCache().getAll().map(query => query.queryKey))
    assert.ok(keys.some(key => key[3] === 'SESSION'))
    assert.ok(keys.some(key => key[3] === 'SUPABASE'))
  })

  await run('mobile panel and confirmation fit the viewport and preserve separate admin wording', async t => {
    await t.page.getByRole('listitem').waitFor()
    assert.ok(await t.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
    await t.page.getByText("Allow an existing LoomAI operator to manage this workspace's Kits, Smart Brains and Engage configuration, including creating from templates and release operations permitted for their operator role. This does not grant access to staff or customer accounts, user directories, global infrastructure or secrets, or Platform-admin powers.", { exact: true }).waitFor()
    await t.page.getByText('Use the exact email of an existing LoomAI operator. No account is created and no invitation email is sent.', { exact: true }).waitFor()
    await t.page.getByText('Revocation blocks future collaboration requests, even through earlier deployment assignments. Work already in flight may finish. Global Platform administrator access is separate and cannot be revoked here.', { exact: true }).waitFor()
    await t.page.screenshot({ path: join(screenshots, 'mobile-panel.png'), fullPage: true })
    await t.openRevoke()
    const dialog = t.page.getByRole('dialog')
    await dialog.getByText('Future collaboration requests for this workspace will be blocked, including access through earlier deployment assignments. Work already in flight may finish. Global Platform administrator access is unchanged.', { exact: true }).waitFor()
    await t.page.locator('.MuiDialog-root').evaluate(async element => {
      await Promise.all(element.getAnimations({ subtree: true }).map(animation => animation.finished.catch(() => {})))
    })
    const bounds = await dialog.boundingBox()
    assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= 390)
    await t.page.screenshot({ path: join(screenshots, 'mobile-confirmation.png'), fullPage: true })
  }, { mobile: true })

  await run('a command cancelled during credential resolution never dispatches under the next customer', async t => {
    await t.page.getByRole('listitem').waitFor()
    await t.page.evaluate(() => { window.__credentialStarted = false; window.__credentialWait = new Promise(resolve => { window.__releaseCredentials = resolve }) })
    await t.grant('second@loomai.example')
    await t.page.waitForFunction(() => window.__credentialStarted)
    await t.scope({ actorId: 'actor-b', customerId: 'customer-b' })
    await t.page.evaluate(() => { window.__credentialWait = null; window.__releaseCredentials() })
    await t.page.getByText('Engineer for customer-b', { exact: true }).waitFor()
    assert.equal(t.requests.filter(item => item.method === 'POST').length, 0)
  })
  console.log(JSON.stringify({ passed, screenshots }))
} finally {
  await browser.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true })
}
