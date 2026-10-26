import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { build } from 'esbuild'
import { chromium } from 'playwright'

const built = await build({ stdin: { loader: 'tsx', resolveDir: process.cwd(), contents: `
import React from 'react'; import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CustomerDeskActivation } from './src/pages/CustomerKnowledgeConnectionsPage';
import { CustomerDeskLink } from './src/components/customer/CustomerDeskLink';
const target = { deploymentId:'deployment-one',targetProfileId:'stage-target',releaseId:'release-stage' };
const client = new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
createRoot(document.getElementById('root')!).render(<MemoryRouter><QueryClientProvider client={client}>
<CustomerDeskActivation target={target} onReady={id=>document.getElementById('ready')!.textContent=id} />
<div id="ready" aria-label="Verified release" />
<CustomerDeskLink target={target} />
</QueryClientProvider></MemoryRouter>);
` }, bundle: true, write: false, format: 'esm', jsx: 'automatic', define: { 'process.env.NODE_ENV': '"production"', 'import.meta.env': '{}' } })
const js = built.outputFiles[0].text
const server = createServer((req, res) => {
  if (req.url === '/app.js') { res.setHeader('Content-Type', 'text/javascript'); res.end(js); return }
  res.setHeader('Content-Type', 'text/html')
  res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/app.js"></script></body></html>')
})
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const origin = `http://127.0.0.1:${server.address().port}`
const browser = await chromium.launch()
try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } })
  let status = 'NOT_ENABLED', posts = 0
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/**', async route => {
    const req = route.request(), url = new URL(req.url())
    assert.ok(url.pathname === '/api/deployments/deployment-one/desk' || url.pathname === '/api/deployments/deployment-one/desk/enable')
    if (req.method() === 'POST') {
      posts += 1
      const body = req.postDataJSON()
      assert.equal(body.targetProfileId, 'stage-target')
      assert.equal(body.releaseId, 'release-stage')
      assert.equal(body.enableReviews, true)
      assert.equal(req.headers()['idempotency-key'], body.operationKey)
      assert.ok(body.operationKey.startsWith('desk-'))
      status = 'ENABLED'
      await route.fulfill({ json: { available: true, status: 'APPLYING', releaseId: 'release-desk', versionId: 'version-desk', portalUrl: 'https://desk.example.test/?bindingId=scope-one' } })
      return
    }
    assert.equal(req.method(), 'GET')
    assert.equal(url.searchParams.get('targetProfileId'), 'stage-target')
    assert.equal(url.searchParams.get('releaseId'), 'release-stage')
    await route.fulfill({ json: { available: true, status, releaseId: status === 'ENABLED' ? 'release-desk' : null,
      portalUrl: status === 'ENABLED' ? 'https://desk.example.test/?bindingId=scope-one' : null } })
  })
  await page.goto(origin)
  await page.getByRole('button', { name: 'Enable LoomAI Desk', exact: true }).waitFor()
  assert.equal(await page.getByRole('link', { name: 'Open LoomAI Desk', exact: true }).count(), 0)
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Enable LoomAI Desk', exact: true }).click()
  await page.getByText('Applying and verifying the support integration.', { exact: false }).waitFor()
  await page.getByRole('link', { name: 'Open LoomAI Desk', exact: true }).first().waitFor({ timeout: 15000 })
  await page.waitForFunction(() => document.getElementById('ready')?.textContent === 'release-desk')
  for (const link of await page.getByRole('link', { name: 'Open LoomAI Desk', exact: true }).all()) {
    assert.equal(await link.getAttribute('href'), 'https://desk.example.test/?bindingId=scope-one')
    assert.equal(await link.getAttribute('rel'), 'noopener noreferrer')
  }
  assert.equal(posts, 1)
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ ok: true, scope: 'offline actual customer activation/link components; synthetic transport only',
    checks: ['explicit target and reviewed release', 'optional review activation', 'single idempotent command',
      'pending is not enabled', 'verified-release notification', 'shared inspector Desk link'] }, null, 2))
} finally {
  await browser.close()
  await new Promise(resolve => server.close(resolve))
}
