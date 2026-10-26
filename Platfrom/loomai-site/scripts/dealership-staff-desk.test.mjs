import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { mkdtemp } from 'node:fs/promises'
import { createServer } from 'node:net'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const siteDirectory = fileURLToPath(new URL('../', import.meta.url))
const expectedDeskUrl = 'https://loomai-desk.46.225.162.106.sslip.io/?bindingId=desk-89708bdf6dc9ab13264aab396a0bec8a'

test('staff Desk cutover preserves dealership operations without legacy inbox calls', { timeout: 120_000 }, async (t) => {
  const reservation = createServer()
  reservation.listen(0, '127.0.0.1')
  await once(reservation, 'listening')
  const port = reservation.address().port
  await new Promise((resolve) => reservation.close(resolve))
  const baseUrl = `http://127.0.0.1:${port}`
  const server = spawn(process.execPath, ['node_modules/astro/bin/astro.mjs', 'dev', '--ignore-lock', '--host', '127.0.0.1', '--port', String(port)], {
    cwd: siteDirectory,
    env: { ...process.env, PUBLIC_LOOMAI_DESK_URL: '', ASTRO_TELEMETRY_DISABLED: '1' },
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  let serverOutput = ''
  for (const stream of [server.stdout, server.stderr]) {
    stream.on('data', (data) => { serverOutput = (serverOutput + data).slice(-8000) })
  }
  t.after(async () => {
    if (server.exitCode !== null) return
    const closed = once(server, 'close')
    server.kill('SIGTERM')
    await closed
  })
  let ready = false
  for (let attempt = 0; attempt < 150; attempt += 1) {
    try {
      ready = (await fetch(`${baseUrl}/demos/dealership-ai/staff`, { signal: AbortSignal.timeout(2000) })).ok
    } catch {}
    if (ready || server.exitCode !== null) break
    await new Promise((resolve) => setTimeout(resolve, 200))
  }
  assert.ok(ready, `Staff page failed to start: ${serverOutput}`)

  const browser = await chromium.launch({ headless: true })
  t.after(() => browser.close())
  const context = await browser.newContext({ viewport: { width: 1280, height: 960 } })
  const page = await context.newPage()
  const errors = []
  const requests = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.clock.install()
  const lead = {
    id: 'staff-cutover-lead', receiptCode: 'NF-TEST-001', actionType: 'dealership_request_test_drive',
    status: 'NEW', vehicleId: 'DEMO-1001', vehicle: '2025 Aster E1',
    createdAt: '2026-10-26T10:00:00Z', updatedAt: '2026-10-26T10:00:00Z',
  }
  await context.route('**/*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (url.origin === new URL(expectedDeskUrl).origin) {
      requests.push({ method: request.method(), path: url.href })
      return route.fulfill({ contentType: 'text/html', body: '<title>Desk native sign-in fixture</title>' })
    }
    if (url.origin !== baseUrl) return route.abort()
    const respond = (value) => route.fulfill({ json: value })
    if (url.pathname === '/runtime-config/dealership-demo.json') return respond({ ready: true, apiBaseUrl: baseUrl })
    if (!url.pathname.startsWith('/api/')) return route.continue()
    requests.push({ method: request.method(), path: url.pathname, body: request.postDataJSON(), headers: request.headers() })
    switch (url.pathname) {
      case '/api/staff/session':
        return respond({ success: true, authenticated: request.method() === 'POST', username: 'fixture-staff' })
      case '/api/public/status': return respond({ inventoryCount: 6, runtimeConfigured: true })
      case '/api/staff/integration/status':
        return respond({ integration: { available: true, source: { freshnessState: 'FRESH', targetedRecordFetchEnabled: true, state: { counts: { indexedCount: 6 } } } } })
      case '/api/staff/leads': return respond({ items: [lead] })
      case '/api/staff/provider-simulator/status':
        return respond({ simulator: { enabled: true, availableScenarios: ['add-stock', 'update-price', 'mark-sold', 'delete-stock'] } })
      case '/api/public/security/csrf': return respond({ headerName: 'X-CSRF-Token', token: 'fixture-csrf' })
      case '/api/staff/integration/reconcile': return respond({ success: true })
      case '/api/staff/provider-simulator/scenarios/add-stock':
        return respond({ receipt: { scenario: 'add-stock', stockId: 'DEMO-1099', mutationOperation: 'CREATE', eventId: 'fixture-event', deliveryStatus: 200, reconciliationStatus: 'COMPLETED', reconciliationAttempts: 1 } })
      case '/api/staff/leads/staff-cutover-lead':
        return respond({ item: { lead, contact: { name: 'Fixture Customer', email: 'fixture@example.test' } } })
      case '/api/staff/leads/staff-cutover-lead/status':
        lead.status = request.postDataJSON().status
        return respond({ success: true })
      default: return route.fulfill({ status: 404, json: { message: 'Unexpected staff API' } })
    }
  })

  await page.goto(`${baseUrl}/demos/dealership-ai/staff`)
  await page.locator('[data-login-panel]').waitFor({ state: 'visible' })
  assert.equal(await page.locator('[data-staff-workspace]').isVisible(), false)
  const desk = page.getByRole('link', { name: 'Open LoomAI Desk' })
  await desk.waitFor({ state: 'visible' })
  assert.equal(await desk.getAttribute('href'), expectedDeskUrl)
  assert.equal(await desk.getAttribute('rel'), 'noopener noreferrer')
  const popupOpened = page.waitForEvent('popup')
  await desk.click()
  const popup = await popupOpened
  await popup.waitForLoadState('domcontentloaded')
  assert.equal(popup.url(), expectedDeskUrl)
  await popup.close()
  assert.ok(!requests.some((request) => request.path.startsWith('/api/') && request.path !== '/api/staff/session'),
    'Opening Desk must not load protected dealership operations or require a dealership login')
  await page.locator('input[name="username"]').fill('fixture-staff')
  await page.locator('input[name="password"]').fill('fixture-password')
  await page.locator('[data-login-form] button[type="submit"]').click()
  await page.locator('[data-open-lead]').waitFor({ state: 'visible' })
  assert.equal(await page.locator('[data-staff-inventory-count]').textContent(), '6')
  assert.equal(await page.locator('[data-staff-sync-status]').textContent(), 'FRESH')
  assert.equal(await page.locator('[data-provider-scenario]').count(), 4)
  assert.equal(await page.locator('[data-human-inbox], [data-human-reply-form]').count(), 0)

  await page.locator('[data-open-lead]').click()
  await page.locator('[data-lead-dialog]').waitFor({ state: 'visible' })
  assert.match(await page.locator('[data-lead-contact]').textContent(), /fixture@example\.test/)
  await page.locator('[data-lead-status-form] select').selectOption('CONTACTED')
  await page.locator('[data-lead-status-form] button[type="submit"]').click()
  await page.locator('[data-lead-dialog]').waitFor({ state: 'hidden' })
  await page.locator('[data-lead-status][data-status="CONTACTED"]').waitFor()
  await page.locator('[data-run-reconcile]').click()
  await page.waitForFunction(() => !document.querySelector('[data-run-reconcile]').disabled)
  await page.locator('[data-provider-scenario="add-stock"]').click()
  await page.waitForFunction(() => document.querySelector('[data-provider-simulator-receipt]').textContent.includes('DEMO-1099'))
  await page.locator('[data-refresh-workspace]').click()
  await page.clock.runFor(8500)
  const screenshots = await mkdtemp(join(tmpdir(), 'dealership-staff-desk-'))
  await page.screenshot({ path: join(screenshots, 'desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: join(screenshots, 'mobile.png'), fullPage: true })
  const linkBounds = await desk.boundingBox()
  assert.ok(linkBounds && linkBounds.x >= 0 && linkBounds.x + linkBounds.width <= 390)
  await page.locator('[data-logout]').click()
  await page.locator('[data-login-panel]').waitFor({ state: 'visible' })
  assert.equal(await page.locator('[data-staff-workspace]').isVisible(), false)
  assert.equal(await desk.isVisible(), true)

  assert.deepEqual(errors, [])
  assert.ok(!requests.some((request) => request.path.includes('human-conversations')))
  for (const [method, path] of [
    ['PATCH', '/api/staff/leads/staff-cutover-lead/status'],
    ['POST', '/api/staff/integration/reconcile'],
    ['POST', '/api/staff/provider-simulator/scenarios/add-stock'],
    ['DELETE', '/api/staff/session'],
  ]) {
    const command = requests.find((request) => request.method === method && request.path === path)
    assert.ok(command, `Missing retained operation: ${method} ${path}`)
    assert.equal(command.headers['x-csrf-token'], 'fixture-csrf')
  }
  assert.equal(requests.filter((request) => request.path === expectedDeskUrl).length, 1)
  t.diagnostic(`Local mocked-browser screenshots: ${screenshots}`)
})
