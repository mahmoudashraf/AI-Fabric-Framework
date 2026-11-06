import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'

const root = fileURLToPath(new URL('../../..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'customer-access-vite-'))
const screenshots = await mkdtemp(join(tmpdir(), 'customer-access-screenshots-'))
const server = await createServer({ root, cacheDir, configFile: false, envFile: false,
  server: { host: '127.0.0.1', port: 0 },
  plugins: [{ name: 'customer-access-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === '/__access-entry.jsx') return '\0access-entry.jsx'
      if (id === '/__access-auth' || id.endsWith('/auth/PlatformAuthProvider')) return '\0access-auth.js'
    },
    transform(code, id) {
      if (id === '\0access-entry.jsx') return transformWithEsbuild(code, 'access-entry.jsx', { loader: 'jsx', jsx: 'automatic' })
    },
    load(id) {
      if (id === '\0access-auth.js') return `import { createContext, useContext } from 'react';
        export const Auth = createContext(null); export const usePlatformAuth = () => useContext(Auth);`
      if (id === '\0access-entry.jsx') return `
        import React, { useState } from 'react'; import { createRoot } from 'react-dom/client';
        import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
        import { Auth } from '/__access-auth';
        import { CustomerAccessDialog } from '/src/components/customer/CustomerAccessDialog.tsx';
        function App() {
          const [state, setState] = useState({ customerId: 'customer-a', customerName: 'Customer A', open: true,
            actorId: 'staff-a', role: 'PLATFORM_ADMIN', authenticationMode: 'SESSION', authenticated: true });
          window.__scope = patch => setState(current => ({ ...current, ...patch }));
          window.__prepared ??= [];
          return <Auth.Provider value={{ session: state }}><CustomerAccessDialog customerId={state.customerId}
            customerName={state.customerName} open={state.open} onClose={() => setState(current => ({ ...current, open: false }))}
            onPrepared={result => window.__prepared.push(result)} /></Auth.Provider>;
        }
        createRoot(document.getElementById('root')).render(<ThemeProvider theme={createTheme()}><CssBaseline /><App /></ThemeProvider>);
      `
    },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (new URL(req.url, 'http://fixture').pathname !== '/__access') return next()
      res.setHeader('Content-Type', 'text/html')
      res.end(await vite.transformIndexHtml('/__access', `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div>
        <script>window.__PLATFORM_RUNTIME_CONFIG__ = { apiBaseUrl: location.origin };</script><script type="module" src="/__access-entry.jsx"></script></body></html>`))
    }) },
  }],
})
await server.listen()
const base = `http://127.0.0.1:${server.httpServer.address().port}`
const browser = await chromium.launch({ headless: true })
let passed = 0

async function run(name, test, options = {}) {
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
  page.setDefaultTimeout(10000)
  const requests = [], errors = [], external = []
  let started, release
  const gate = options.hold ? new Promise(resolve => { release = resolve }) : null
  const began = new Promise(resolve => { started = resolve })
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => Object.defineProperty(navigator, 'clipboard', { value: {
    writeText: async value => { if (window.__copyFail) throw new Error('Unavailable'); window.__copied = value },
  } }))
  await page.route('**/*', route => {
    if (new URL(route.request().url()).origin === base) return route.continue()
    external.push(route.request().url()); return route.abort()
  })
  await page.route(`${base}/api/**`, async route => {
    const body = route.request().postDataJSON()
    const path = new URL(route.request().url()).pathname
    requests.push({ path, body, method: route.request().method() }); started()
    if (gate) await gate
    const response = options.error ? { message: 'This email already belongs to another workspace. No access was changed.' }
      : { userId: 'user-a', email: body.email.toLowerCase(), displayName: body.displayName,
        customerId: options.mismatch ? 'customer-b' : path.split('/')[4], identityLinked: Boolean(options.linked), created: !options.existing }
    await route.fulfill({ status: options.error ? 409 : 200, contentType: 'application/json', body: JSON.stringify(response) }).catch(() => {})
  })
  const submit = async (email = 'recipient@example.test') => {
    await page.getByRole('textbox', { name: /^Customer email/ }).fill(email)
    await page.getByRole('textbox', { name: /^Customer name/ }).fill('Recipient Name')
    await page.getByRole('button', { name: 'Prepare access', exact: true }).click()
  }
  try {
    await page.goto(`${base}/__access`)
    await page.getByRole('dialog').waitFor()
    await test({ page, requests, submit, began, release, scope: patch => page.evaluate(patch => window.__scope(patch), patch) })
    assert.deepEqual(errors, [], `${name}: browser errors`)
    assert.deepEqual(external, [], `${name}: no provider or external requests`)
    passed++; console.log(`PASS ${name}`)
  } finally { release?.(); await page.close() }
}

try {
  await run('new recipient receives accurate independent signup and recovery instructions without credentials or email send', async ({ page, requests, submit }) => {
    assert.equal(await page.getByRole('textbox', { name: /^Customer email/ }).inputValue(), '')
    assert.equal(await page.getByRole('textbox', { name: /^Customer name/ }).inputValue(), '')
    assert.equal(await page.locator('input[type=password]').count(), 0)
    await submit('Recipient@Example.test')
    await page.getByText('No email has been sent.', { exact: false }).waitFor()
    await page.getByText(/must create and verify it first/).waitFor()
    await page.getByText(/No old Console password is needed/).waitFor()
    assert.deepEqual(requests, [{ path: '/api/staff/customers/customer-a/access', method: 'POST',
      body: { email: 'Recipient@Example.test', displayName: 'Recipient Name' } }])
    assert.equal(await page.evaluate(() => window.__prepared.length), 1)
    for (const [label, route] of [['Create sign-in account', '/sign-up'], ['Connect existing workspace', '/recover-account'], ['Sign in', '/sign-in']]) {
      assert.equal(await page.getByLabel(label, { exact: true }).inputValue(), base + route)
    }
    await page.getByRole('button', { name: 'Copy connect existing workspace link', exact: true }).click()
    assert.equal(await page.evaluate(() => window.__copied), base + '/recover-account')
    await page.evaluate(() => { window.__copyFail = true })
    await page.getByRole('button', { name: 'Copy sign in link', exact: true }).click()
    await page.getByRole('status').filter({ hasText: 'Clipboard unavailable' }).waitFor()
  })
  await run('existing linked recipient keeps normal sign-in instructions', async ({ page, submit }) => {
    await submit()
    await page.getByText(/Access already prepared/).waitFor()
    await page.getByText(/can sign in normally with their own account/).waitFor()
    assert.equal(await page.getByText(/must create and verify it first/).count(), 0)
  }, { existing: true, linked: true })
  await run('conflicting membership remains an error and never reports prepared access', async ({ page, submit }) => {
    await submit()
    await page.getByRole('alert').filter({ hasText: 'another workspace' }).waitFor()
    assert.equal(await page.evaluate(() => window.__prepared.length), 0)
    assert.equal(await page.getByLabel('Sign in', { exact: true }).count(), 0)
  }, { error: true })
  await run('unexpected response customer is rejected before notifying the parent', async ({ page, submit }) => {
    await submit()
    await page.getByRole('alert').filter({ hasText: 'does not match this request' }).waitFor()
    assert.equal(await page.evaluate(() => window.__prepared.length), 0)
  }, { mismatch: true })
  await run('switching customer clears personal data and suppresses late completion', async ({ page, submit, began, release, scope }) => {
    await submit(); await began
    await scope({ customerId: 'customer-b', customerName: 'Customer B' }); release()
    await page.getByText('Customer B', { exact: true }).waitFor()
    assert.equal(await page.getByRole('textbox', { name: /^Customer email/ }).inputValue(), '')
    assert.equal(await page.getByRole('textbox', { name: /^Customer name/ }).inputValue(), '')
    assert.equal(await page.evaluate(() => window.__prepared.length), 0)
    assert.equal(await page.getByText('No email has been sent.', { exact: false }).count(), 0)
  }, { hold: true })
  await run('closing and reopening resets prior recipient state', async ({ page, submit, scope }) => {
    await submit(); await page.getByText('No email has been sent.', { exact: false }).waitFor()
    await page.getByRole('button', { name: 'Done', exact: true }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    await scope({ open: true })
    await page.getByRole('textbox', { name: /^Customer email/ }).waitFor()
    assert.equal(await page.getByRole('textbox', { name: /^Customer email/ }).inputValue(), '')
    assert.equal(await page.getByRole('textbox', { name: /^Customer name/ }).inputValue(), '')
  })
  await run('staff role changes clear state and disable further access preparation', async ({ page, requests, scope }) => {
    await page.getByRole('textbox', { name: /^Customer email/ }).fill('private@example.test')
    await scope({ role: 'PLATFORM_OPERATOR' })
    await page.getByText('An active platform administrator session is required.', { exact: true }).waitFor()
    assert.equal(await page.getByRole('textbox', { name: /^Customer email/ }).inputValue(), '')
    assert.equal(await page.getByRole('button', { name: 'Prepare access', exact: true }).isDisabled(), true)
    assert.equal(requests.length, 0)
  })
  await run('desktop and narrow mobile dialog stays inside the viewport', async ({ page, submit }) => {
    await submit('recipient.with.a.long.but.real.email.address@example.test')
    await page.getByText('No email has been sent.', { exact: false }).waitFor()
    for (const width of [1280, 390, 320]) {
      await page.setViewportSize({ width, height: 900 })
      const geometry = await page.getByRole('dialog').evaluate(node => ({ left: node.getBoundingClientRect().left,
        right: node.getBoundingClientRect().right, scroll: node.scrollWidth, client: node.clientWidth, viewport: innerWidth }))
      assert.ok(geometry.left >= 0 && geometry.right <= geometry.viewport, JSON.stringify(geometry))
      assert.ok(geometry.scroll <= geometry.client + 1, JSON.stringify(geometry))
      await page.screenshot({ path: join(screenshots, `access-${width}.png`), fullPage: true })
    }
  })
  console.log(`Customer access browser: ${passed} scenarios passed. Local mocks only. Screenshots: ${screenshots}`)
} finally { await browser.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
