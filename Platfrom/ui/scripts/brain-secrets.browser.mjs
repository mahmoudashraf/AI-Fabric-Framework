import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'

const root = fileURLToPath(new URL('..', import.meta.url))
const cache = await mkdtemp(join(tmpdir(), 'brain-secrets-vite-'))
const output = await mkdtemp(join(tmpdir(), 'brain-secrets-evidence-'))
const server = await createServer({ root, cacheDir: cache, configFile: false, envFile: false,
  resolve: { dedupe: ['react', 'react-dom'] }, server: { host: '127.0.0.1', port: 0, hmr: false },
  plugins: [{ name: 'brain-secrets-memory-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === '/__secrets-entry.jsx') return '\0secrets-entry.jsx'
      if (id === '../workspace/DeploymentWorkspaceContext') return '\0secrets-workspace.js'
    },
    async load(id) {
      if (id === '\0secrets-workspace.js') return 'export function useDeploymentWorkspace(){return {workspace:{access:{canAdmin:!new URLSearchParams(location.search).has("readonly")}}}}'
      if (id !== '\0secrets-entry.jsx') return
      return (await transformWithEsbuild(`
        import React from 'react';import {createRoot} from 'react-dom/client';
        import {BrowserRouter} from 'react-router-dom';import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
        import {ThemeProvider,CssBaseline,Box} from '@mui/material';import {createConsoleTheme} from '/src/theme/consoleTheme.ts';
        import {setPlatformAccessTokenProvider} from '/src/api/platformApi.ts';import {BrainWorkspaceContext} from '/src/brain/BrainWorkspaceContext.tsx';
        import {BrainSecretsPage} from '/src/brain/BrainSecretsPage.tsx';setPlatformAccessTokenProvider(async()=> 'memory-fixture');
        const client=new QueryClient({defaultOptions:{queries:{retry:false,refetchOnWindowFocus:false},mutations:{retry:false}}});
        createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}><ThemeProvider theme={createConsoleTheme(new URLSearchParams(location.search).get('theme')==='light'?'light':'dark')}><CssBaseline/><BrowserRouter><BrainWorkspaceContext.Provider value={{brainId:'brain',targetProfileId:'target',href:section=>'/smart-brains/brain/'+section,release:null,target:null,targetProfile:null}}><Box sx={{height:'100dvh',display:'flex',flexDirection:'column',p:2,minWidth:0}}><BrainSecretsPage/></Box></BrainWorkspaceContext.Provider></BrowserRouter></ThemeProvider></QueryClientProvider>);
      `, 'secrets-entry.jsx', { loader: 'jsx', jsx: 'automatic' })).code
    },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (!new URL(req.url, 'http://fixture').pathname.startsWith('/smart-brains/')) return next()
      res.setHeader('Content-Type', 'text/html')
      res.end(await vite.transformIndexHtml('/__secrets', '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin};</script><script type="module" src="/__secrets-entry.jsx"></script></body></html>'))
    }) },
  }], logLevel: 'error' })
const purposes = [{ id: 'INTEGRATION_CREDENTIAL', label: 'Integration or security key', paired: false },
  { id: 'OPENAI_API_KEY', label: 'OpenAI API key', paired: false },
  { id: 'MILVUS_RUNTIME_CREDENTIALS', label: 'Milvus runtime credentials', paired: true }]
let browser, passed = 0
async function run(name, test, options = {}) {
  const page = await browser.newPage({ viewport: options.viewport ?? { width: 1360, height: 840 } })
  page.setDefaultTimeout(15000)
  const calls = [], errors = [], unexpected = []
  let rows = options.rows ?? [], rejectSave = false
  page.on('pageerror', e => errors.push(e.message))
  await page.route('**/*', route => new URL(route.request().url()).origin === base ? route.continue() : (unexpected.push(route.request().url()), route.abort()))
  await page.route(`${base}/api/**`, async route => {
    const req = route.request(), path = new URL(req.url()).pathname, method = req.method(), payload = req.postDataJSON()
    calls.push({ path, method, payload })
    const send = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: status === 204 ? undefined : JSON.stringify(body) })
    if (path === '/api/deployments/brain/secrets' && method === 'GET') return send({ deploymentId: 'brain', purposes, secrets: rows })
    if (!path.startsWith('/api/deployments/brain/secrets')) { unexpected.push(`${method} ${path}`); return send({ message: 'Unexpected API' }, 404) }
    if (rejectSave) { rejectSave = false; return send({ message: 'Delegated access was revoked.' }, 403) }
    if (method === 'POST') {
      const row = { name: 'CUSTOMER_REFERENCE_' + payload.name, label: payload.name, purpose: payload.purpose, present: true,
        bindingCount: payload.purpose === 'INTEGRATION_CREDENTIAL' ? 0 : 1, updatedAt: '2026-11-10T10:00:00Z' }
      rows.push(row); return send([row], 201)
    }
    if (method === 'PUT') return send(rows.find(row => path.endsWith('/' + row.name)))
    if (method === 'DELETE') { rows = rows.filter(row => !path.endsWith('/' + row.name)); return send(null, 204) }
    unexpected.push(`${method} ${path}`); return send({ message: 'Unexpected request' }, 404)
  })
  try {
    await page.goto(`${base}/smart-brains/brain/secrets?${options.readonly ? 'readonly&' : ''}theme=${options.theme ?? 'dark'}`)
    await page.getByRole('heading', { name: 'Keys & secrets', exact: true }).waitFor()
    await test({ page, calls, reject: () => { rejectSave = true } })
    assert.deepEqual(errors, []); assert.deepEqual(unexpected, [])
    passed++; console.log('PASS ' + name)
  } catch (e) { await page.screenshot({ path: join(output, 'failure.png'), fullPage: true }); console.error({ name, errors, unexpected, output }); throw e }
  finally { await page.close() }
}
const row = { name: 'CUSTOMER_REFERENCE_EXISTING_API', label: 'EXISTING_API', purpose: 'INTEGRATION_CREDENTIAL', present: true, bindingCount: 0, updatedAt: '2026-11-10T10:00:00Z' }
await server.listen()
const base = server.resolvedUrls.local[0].replace(/\/$/, '')
try {
  browser = await chromium.launch()
  await run('custom key creation, write-only replacement, cancellation and confirmed deletion', async ({ page, calls }) => {
    await page.getByText('No customer keys yet', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Add key', exact: true }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByLabel('Key name', { exact: false }).fill('stock_api')
    assert.equal(await dialog.getByLabel('Key name', { exact: false }).inputValue(), 'STOCK_API')
    await dialog.getByLabel('Key value', { exact: false }).fill('fictional-private-test-value')
    await dialog.getByRole('button', { name: 'Save key', exact: true }).click()
    await page.getByText('STOCK_API', { exact: true }).waitFor()
    assert.equal(calls.find(c => c.method === 'POST').payload.value, 'fictional-private-test-value')
    assert.ok(!(await page.locator('body').innerText()).includes('fictional-private-test-value'))
    await page.getByRole('button', { name: 'Replace STOCK_API', exact: true }).click()
    assert.equal(await dialog.getByLabel('Key value', { exact: false }).inputValue(), '')
    await dialog.getByLabel('Key value', { exact: false }).fill('fictional-rotated-value')
    await dialog.getByRole('button', { name: 'Replace value', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
    assert.equal(calls.filter(c => c.method === 'PUT').length, 1)
    await page.getByRole('button', { name: 'Delete STOCK_API', exact: true }).click()
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    assert.equal(calls.filter(c => c.method === 'DELETE').length, 0)
    await page.getByRole('button', { name: 'Delete STOCK_API', exact: true }).click()
    await dialog.getByRole('button', { name: 'Delete key', exact: true }).click()
    await page.getByText('No customer keys yet', { exact: true }).waitFor()
    assert.equal(calls.filter(c => c.method === 'DELETE').length, 1)
  })
  await run('provider creation is explicit and bound key deletion stays disabled', async ({ page, calls }) => {
    await page.getByRole('button', { name: 'Add key', exact: true }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByRole('combobox', { name: 'Used for' }).click()
    await page.getByRole('option', { name: 'OpenAI API key', exact: true }).click()
    await dialog.getByLabel('Key name', { exact: false }).fill('MY_MODEL')
    await dialog.getByLabel('Key value', { exact: false }).fill('fictional-provider-value')
    await dialog.getByRole('button', { name: 'Save & use key', exact: true }).click()
    await page.getByText('Provider selected', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Delete MY_MODEL', exact: true }).isDisabled(), true)
    assert.equal(calls.find(c => c.method === 'POST').payload.purpose, 'OPENAI_API_KEY')
    await page.screenshot({ path: join(output, 'desktop-dark.png'), fullPage: true })
  })
  await run('paired values are required and rejected save keeps error and permits cancel', async ({ page, calls, reject }) => {
    await page.getByRole('button', { name: 'Add key', exact: true }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByRole('combobox', { name: 'Used for' }).click()
    await page.getByRole('option', { name: 'Milvus runtime credentials', exact: true }).click()
    await dialog.getByLabel('Key name', { exact: false }).fill('VECTOR')
    await dialog.getByLabel('Username', { exact: false }).fill('test-user')
    assert.equal(await dialog.getByRole('button', { name: 'Save & use key', exact: true }).isDisabled(), true)
    await dialog.getByLabel('Password', { exact: false }).fill('test-password')
    reject(); await dialog.getByRole('button', { name: 'Save & use key', exact: true }).click()
    await dialog.getByText('Delegated access was revoked.', { exact: true }).waitFor()
    assert.equal(calls.find(c => c.method === 'POST').payload.secondaryValue, 'test-password')
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    await page.getByRole('button', { name: 'Add key', exact: true }).click()
    assert.equal(await dialog.getByLabel('Key value', { exact: false }).inputValue(), '')
  })
  await run('non-admin cannot read key catalogue or submit a mutation', async ({ page, calls }) => {
    await page.getByText('A workspace owner or delegated administrator can manage these keys.', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Add key', exact: true }).isDisabled(), true)
    assert.equal(calls.length, 0)
  }, { readonly: true })
  for (const theme of ['light', 'dark']) await run('mobile ' + theme + ' has no document overflow and usable form', async ({ page }) => {
    await page.getByText(row.label, { exact: true }).waitFor()
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await page.screenshot({ path: join(output, `mobile-${theme}.png`), fullPage: true })
    await page.getByRole('button', { name: 'Add key', exact: true }).click()
    await page.getByRole('dialog').getByLabel('Key name', { exact: false }).fill('TEST_KEY')
    await page.getByRole('dialog').evaluate(node => Promise.all(node.getAnimations({ subtree: true }).filter(animation => animation.playState === 'running').map(animation => animation.finished.catch(() => {}))))
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await page.screenshot({ path: join(output, `mobile-${theme}-form.png`), fullPage: true })
  }, { rows: [structuredClone(row)], viewport: { width: 390, height: 844 }, theme })
  console.log(JSON.stringify({ passed, output, realProviderCalls: 0, realSecretWrites: 0 }))
} finally { await browser?.close(); await server.close(); await rm(cache, { recursive: true, force: true }) }
