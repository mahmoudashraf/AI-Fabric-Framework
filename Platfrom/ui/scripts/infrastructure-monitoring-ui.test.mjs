import test, { before, after } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, mkdir, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const entry = '\0infrastructure-monitoring-fixture.jsx'
const prefix = '/api/admin/infrastructure/monitoring/servers'
const evidence = process.env.MONITORING_UI_EVIDENCE_DIR || '/private/tmp/loomai-infrastructure-monitoring-01060'
let vite, browser, cache, base
before(async () => {
  cache = await mkdtemp(join(tmpdir(), 'monitoring-ui-'))
  await mkdir(evidence, { recursive: true })
  vite = await createServer({ root, configFile: false, envFile: false, cacheDir: cache, logLevel: 'error',
    esbuild: { jsx: 'automatic' }, resolve: { dedupe: ['react', 'react-dom'] }, server: { host: '127.0.0.1', port: 0 },
    plugins: [{ name: 'infrastructure-monitoring-fixture',
      resolveId(id) { if (id === '/monitoring-fixture.jsx') return entry },
      load(id) {
        if (id !== entry) return
        return `import React from 'react';import{createRoot}from'react-dom/client';
          import{BrowserRouter}from'react-router-dom';import{QueryClient,QueryClientProvider}from'@tanstack/react-query';
          import{ThemeProvider,createTheme,CssBaseline,Box}from'@mui/material';
          import{PlatformAuthProvider}from'/src/auth/PlatformAuthProvider.tsx';
          import{InfrastructureMonitoringPage}from'/src/pages/InfrastructureMonitoringPage.tsx';
          import{platformTheme}from'/src/theme/platformTheme.ts';
          const client=new QueryClient({defaultOptions:{queries:{retry:false}}});window.fixtureClient=client;
          window.setFixtureSession=s=>client.setQueriesData({queryKey:['platform-auth-session']},s);
          const dark=new URLSearchParams(location.search).has('dark');
          const theme=dark?createTheme({palette:{mode:'dark'},shape:{borderRadius:8}}):platformTheme;
          createRoot(document.getElementById('root')).render(React.createElement(QueryClientProvider,{client},
            React.createElement(PlatformAuthProvider,null,React.createElement(BrowserRouter,null,
              React.createElement(ThemeProvider,{theme},React.createElement(CssBaseline),React.createElement(Box,{sx:{p:{xs:2,md:3},maxWidth:1600,mx:'auto'}},React.createElement(InfrastructureMonitoringPage)))))));`
      },
      configureServer(server) { server.middlewares.use((req, res, next) => {
        if (new URL(req.url, 'http://fixture.local').pathname !== '/monitoring-fixture') return next()
        res.setHeader('Content-Type', 'text/html')
        res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/monitoring-fixture.jsx"></script></body></html>')
      }) },
    }],
  })
  await vite.listen(); base = vite.resolvedUrls.local[0]
  browser = await chromium.launch({ headless: true })
})
after(async () => { await browser?.close(); await vite?.close(); if (cache) await rm(cache, { recursive: true, force: true }) })

const admin = { enabled: true, authenticated: true, role: 'PLATFORM_ADMIN', actorId: 'admin-one', customerId: null, authenticationMode: 'SESSION', canManageUsers: true }
const at = offset => new Date(Date.now() - offset).toISOString()
const GiB = 1024 ** 3
function data() {
  const runtime = { resourceId: 'a'.repeat(64), name: 'Runtime alpha', image: 'registry.example/runtime@sha256:immutable', providerResourceId: 'app-alpha',
    providerKind: 'APPLICATION', role: 'runtime', startedAt: at(86_400_000), state: 'running', health: 'healthy',
    memoryWorkingSetBytes: GiB, memoryLimitBytes: 2 * GiB, memoryReservationBytes: 0, memorySwapLimitBytes: 3 * GiB,
    cpuCoresUsed: 1.25, cpuQuota: 2, cpusetCpus: '0-3', cpuThrottledPeriodsPercent: null, restartCount: 1, oomKilled: false,
    deploymentId: 'deployment/alpha', deploymentName: 'Alpha deployment', targetProfileId: 'production/target' }
  const connector = { ...runtime, resourceId: 'b'.repeat(64), name: 'Unmapped connector', role: 'connector', providerKind: 'UNMAPPED',
    deploymentId: undefined, deploymentName: undefined, targetProfileId: undefined, memoryWorkingSetBytes: 0.25 * GiB,
    cpuCoresUsed: null, health: 'unhealthy', restartCount: 5, oomKilled: true }
  const host = { cpuThreads: 8, cpuUsagePercent: 33.5, load1: 2.3, load5: 1.5, load15: 1.1,
    memoryTotalBytes: 16 * GiB, memoryAvailableBytes: 4 * GiB, swapTotalBytes: 8 * GiB, swapUsedBytes: 2 * GiB,
    swapInBytesPerSecond: null, swapOutBytesPerSecond: 0, diskTotalBytes: 100 * GiB, diskFreeBytes: 50 * GiB,
    inodesTotal: 100000, inodesFree: 40000, networkRxBytesPerSecond: null, networkTxBytesPerSecond: 1024, cpuStealPercent: null }
  const staging = { serverId: 'host/staging', displayName: 'Staging host', environment: 'staging', status: 'FRESH', observedAt: at(60_000),
    receivedAt: at(58_000), complete: false, source: 'HOST_NATIVE', host, resources: [runtime, connector],
    warnings: ['CPU affinity overlap: 2 runtimes share CPUs 0-3; sampled evidence only.'] }
  return { sources: [{ id: 'staging-source', status: 'AVAILABLE', message: null }, { id: 'production-source', status: 'UNAVAILABLE', message: 'Read timed out' },
    { id: 'paused-source', status: 'DISABLED', message: null }],
  servers: [staging, { ...staging, serverId: 'host-production', displayName: 'Production host', environment: 'production', status: 'STALE',
    observedAt: at(600_000), receivedAt: at(590_000), complete: true, host: null, resources: [], warnings: [] },
  { ...staging, serverId: 'host-never', displayName: 'New host', environment: 'staging', status: 'NEVER_RECEIVED', observedAt: null, receivedAt: null,
    complete: false, source: null, host: null, resources: [], warnings: [] }] }
}

async function fixture(t, options = {}) {
  const state = { session: { ...admin }, fleet: data(), calls: [], errors: [], historyEmpty: false, historyError: false, fleetError: 0, detailError: false, ...options }
  const page = await browser.newPage({ viewport: options.viewport || { width: 1440, height: 1100 } })
  t.after(() => page.close()); page.setDefaultTimeout(20_000)
  if (options.clock) await page.clock.install()
  page.on('pageerror', error => state.errors.push(error.message))
  await page.route(`${base}api/**`, async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname
    state.calls.push({ path, method: request.method(), query: Object.fromEntries(url.searchParams) })
    const json = (body, status = 200) => route.fulfill({ status, json: body })
    if (path === '/api/platform/auth/customer-config') return json({ enabled: false })
    if (path === '/api/platform/auth/session') return json(state.session)
    if (path === prefix) return state.fleetError ? json({ message: 'Fixture source unavailable' }, state.fleetError) : json(state.fleet)
    const match = path.match(/^\/api\/admin\/infrastructure\/monitoring\/servers\/([^/]+)(\/history)?$/)
    if (match) {
      const serverId = decodeURIComponent(match[1]), resourceId = url.searchParams.get('resourceId')
      if (!match[2]) return state.detailError ? json({ message: 'Fixture details unavailable' }, 503) : json(state.fleet.servers.find(server => server.serverId === serverId))
      if (state.holdHistory) await state.holdHistory
      if (state.historyError) return json({ message: 'Fixture history unavailable' }, 503)
      const points = [55, 54, 53, 51, 50].map((minutes, index) => {
        const metricValues = resourceId ? { memoryWorkingSetBytes: GiB / (index + 1), cpuCoresUsed: index / 4, cpuThrottledPeriodsPercent: null, restartCount: index }
          : { cpuUsagePercent: 20 + index * 2, memoryAvailableBytes: (4 + index / 4) * GiB, swapInBytesPerSecond: null, diskFreeBytes: (50 - index) * GiB }
        const values = Object.fromEntries(Object.entries(metricValues).map(([key, value]) => [key, index === 2 ? null : value]))
        return { observedAt: at(minutes * 60_000), values, maxValues: Object.fromEntries(Object.entries(values).map(([key, value]) => [key, value === null ? null : value * 1.1])) }
      })
      return json({ serverId, resourceId, range: url.searchParams.get('range'), bucketSeconds: 60, points: state.historyEmpty ? [] : points })
    }
    throw new Error(`Unexpected fixture API ${path}`)
  })
  await page.goto(`${base}monitoring-fixture${options.dark ? '?dark' : ''}`)
  return { page, state }
}
const monitoringCalls = state => state.calls.filter(call => call.path.startsWith(prefix))
const changeSession = async (page, state, session) => {
  state.session = session
  await page.evaluate(value => window.setFixtureSession(value), session)
}
const sensitiveCache = page => page.evaluate(() => window.fixtureClient.getQueryCache().findAll({ queryKey: ['infrastructure-monitoring'] }).map(query => ({ key: query.queryKey, data: query.state.data })))

test('admin sees actual freshness, partial sources, history gaps and a read-only resource inspector', async t => {
  const { page, state } = await fixture(t)
  await page.getByRole('table', { name: 'Server overview', exact: true }).waitFor()
  assert.equal(await page.getByText('Partial', { exact: true }).count(), 3)
  await page.getByText('Read timed out', { exact: false }).waitFor()
  await page.getByText('Never received', { exact: true }).waitFor()
  await page.getByText('Stale', { exact: true }).waitFor()
  await page.getByRole('img', { name: 'Server CPU (%) history', exact: false }).waitFor()
  const path = await page.locator('path[data-series="mean"]').first().getAttribute('d')
  assert.equal((path.match(/M/g) || []).length, 2, 'Null bucket creates a gap')
  assert.equal(await page.getByText('No measurements in this window.', { exact: true }).count(), 1, 'Unsupported swap is not a zero graph')
  await page.screenshot({ path: join(evidence, 'desktop-light.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('button', { name: 'Rank by restarts', exact: true }).click()
  assert.match(await page.getByRole('table', { name: 'Top resource consumers' }).locator('tbody tr').first().innerText(), /Unmapped connector/)
  await page.getByRole('button', { name: 'Inspect resource Unmapped connector', exact: true }).click()
  const drawer = page.getByRole('dialog')
  await drawer.getByText('Container ID', { exact: true }).waitFor()
  await drawer.getByText('b'.repeat(64), { exact: true }).waitFor()
  assert.match(await drawer.innerText(), /CPU \(core-equivalents\)\s+Unknown/)
  assert.match(await drawer.innerText(), /CPU affinity \(cpuset\)\s+0-3/)
  assert.match(await drawer.innerText(), /OOM-killed evidence\s+Yes/)
  await page.screenshot({ path: join(evidence, 'resource-inspector.png'), animations: 'disabled' })
  await drawer.getByRole('button', { name: 'Close inspector', exact: true }).click()
  await page.getByRole('button', { name: 'Inspect resource Runtime alpha', exact: true }).click()
  const href = await page.getByRole('dialog').getByRole('link', { name: 'Alpha deployment' }).getAttribute('href')
  const target = new URL(href, base)
  assert.equal(target.searchParams.get('deploymentId'), 'deployment/alpha')
  assert.equal(target.searchParams.get('targetProfileId'), 'production/target')
  assert(monitoringCalls(state).some(call => call.query.resourceId === 'a'.repeat(64)))
  assert(monitoringCalls(state).every(call => call.method === 'GET'))
  assert.deepEqual(state.errors, [])
})

test('environment, role, search and range filters request only the selected stored history', async t => {
  const { page, state } = await fixture(t)
  await page.getByRole('button', { name: 'Inspect resource Runtime alpha', exact: true }).waitFor()
  await page.getByRole('combobox', { name: 'Role', exact: true }).click()
  await page.getByRole('option', { name: 'connector', exact: true }).click()
  assert.equal(await page.getByRole('table', { name: 'Top resource consumers' }).locator('tbody tr').count(), 1)
  await page.getByRole('textbox', { name: 'Search resources', exact: true }).fill('missing-exact-resource')
  await page.getByText('No resource samples match these filters.', { exact: true }).waitFor()
  await page.getByRole('textbox', { name: 'Search resources', exact: true }).fill('')
  await page.getByRole('combobox', { name: 'Environment', exact: true }).click()
  await page.getByRole('option', { name: 'production', exact: true }).click()
  assert.equal(await page.getByRole('table', { name: 'Server overview', exact: true }).locator('tbody tr').count(), 1)
  await page.getByRole('button', { name: 'History range 7d', exact: true }).click()
  await page.waitForFunction(() => window.fixtureClient.getQueryCache().findAll({ queryKey: ['infrastructure-monitoring'] }).some(query => query.queryKey.at(-1) === '7d' && query.state.status === 'success'))
  assert(monitoringCalls(state).some(call => call.path.includes('host-production/history') && call.query.range === '7d'))
  assert(!monitoringCalls(state).some(call => call.query.resourceId))
  assert.deepEqual(state.errors, [])
})

test('customer, operator, Desk, anonymous and auth-disabled pseudo-admin never request fleet data', async t => {
  for (const session of [
    { ...admin, role: 'CUSTOMER_ADMIN', customerId: 'customer-one' },
    { ...admin, role: 'PLATFORM_OPERATOR', canManageUsers: true },
    { ...admin, role: 'DESK_AGENT', canManageUsers: true },
    { ...admin, authenticated: false, enabled: false },
    { ...admin, customerId: 'customer-scope' },
  ]) {
    const { page, state } = await fixture(t, { session })
    await page.getByText('Platform administrator access is required.', { exact: true }).waitFor()
    assert.equal(monitoringCalls(state).length, 0)
    assert.equal((await sensitiveCache(page)).length, 0)
  }
})

test('auth changes purge all sensitive rows, drawer/history and old-admin cache without local storage', async t => {
  const { page, state } = await fixture(t)
  await page.getByRole('button', { name: 'Inspect resource Runtime alpha', exact: true }).click()
  await page.getByRole('dialog').getByText('Container ID', { exact: true }).waitFor()
  await changeSession(page, state, { ...admin, actorId: 'admin-two' })
  await page.getByRole('button', { name: 'Inspect resource Runtime alpha', exact: true }).waitFor()
  assert.equal(await page.getByRole('dialog').count(), 0)
  const cacheRows = await sensitiveCache(page)
  assert(cacheRows.every(query => query.key[1].includes('admin-two')))
  await changeSession(page, state, { ...admin, authenticated: false })
  await page.getByText('Platform administrator access is required.', { exact: true }).waitFor()
  await page.waitForFunction(() => window.fixtureClient.getQueryCache().findAll({ queryKey: ['infrastructure-monitoring'] }).length === 0)
  assert.equal(await page.getByRole('table').count(), 0)
  assert.equal(await page.evaluate(() => Object.values(localStorage).some(value => value.includes('Staging host') || value.includes('Runtime alpha'))), false)
  assert.deepEqual(state.errors, [])
})

test('empty history, history errors, details failure and fleet failure stay explicit', async t => {
  const { page, state } = await fixture(t, { historyEmpty: true, detailError: true })
  await page.getByText('No history received for this window.', { exact: true }).waitFor()
  await page.getByRole('button', { name: 'Inspect server Staging host', exact: true }).click()
  await page.getByRole('dialog').getByText('Server details unavailable.', { exact: false }).waitFor()
  await page.getByRole('dialog').getByText('Deployment / target footprints', { exact: true }).waitFor()
  await page.getByRole('dialog').getByRole('button', { name: 'Close inspector', exact: true }).click()
  state.historyError = true
  await page.getByRole('button', { name: 'History range 24h', exact: true }).click()
  await page.getByText('History unavailable.', { exact: false }).waitFor()
  state.fleetError = 503
  await page.getByRole('button', { name: 'Refresh stored observations', exact: true }).click()
  await page.getByText('Infrastructure observations could not be refreshed.', { exact: false }).waitFor()
  await page.getByRole('table', { name: 'Server overview', exact: true }).waitFor()
  assert.deepEqual(state.errors, [])
})

test('dark mobile overview and inspector fit the viewport with scrollable tables', async t => {
  const { page, state } = await fixture(t, { dark: true, viewport: { width: 390, height: 844 } })
  await page.getByRole('button', { name: 'Inspect server Staging host', exact: true }).waitFor()
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
  const axisFill = await page.locator('svg[role="img"] text').first().evaluate(element => getComputedStyle(element).fill)
  assert.notEqual(axisFill, 'rgb(0, 0, 0)', 'Dark chart labels use theme text colors')
  await page.screenshot({ path: join(evidence, 'mobile-dark.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('button', { name: 'Inspect server Staging host', exact: true }).click()
  const drawer = page.getByRole('dialog')
  await drawer.getByText('Server ID', { exact: true }).waitFor()
  assert.equal(await drawer.evaluate(element => element.scrollWidth <= element.clientWidth), true)
  await page.waitForFunction(() => Math.abs(document.querySelector('[role="dialog"]').getBoundingClientRect().left) < 1)
  await page.screenshot({ path: join(evidence, 'mobile-dark-inspector.png'), animations: 'disabled' })
  assert.deepEqual(state.errors, [])
})

test('visible pages poll stored observations every 30s and pause all monitoring queries while hidden', async t => {
  const { page, state } = await fixture(t, { clock: true })
  await page.getByRole('img', { name: 'Server CPU (%) history', exact: false }).waitFor()
  const count = () => monitoringCalls(state).filter(call => call.path === prefix).length
  const initial = count()
  await page.clock.fastForward(31_000)
  await page.waitForFunction(() => window.fixtureClient.getQueryCache().findAll({ queryKey: ['infrastructure-monitoring'] }).every(query => query.state.fetchStatus === 'idle'))
  assert(count() > initial)
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
    document.dispatchEvent(new Event('visibilitychange'))
  })
  await page.waitForFunction(() => window.fixtureClient.getQueryCache().findAll({ queryKey: ['infrastructure-monitoring'] }).every(query => query.options.enabled === false))
  const hiddenCount = monitoringCalls(state).length
  await page.clock.fastForward(91_000)
  assert.equal(monitoringCalls(state).length, hiddenCount)
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
    document.dispatchEvent(new Event('visibilitychange'))
  })
  await page.waitForFunction(() => window.fixtureClient.getQueryCache().findAll({ queryKey: ['infrastructure-monitoring'] }).every(query => query.options.enabled === true))
  assert.deepEqual(state.errors, [])
})

test('a denied backend read purges cached fleet observations instead of displaying them as a stale success', async t => {
  const { page, state } = await fixture(t)
  await page.getByRole('button', { name: 'Inspect resource Runtime alpha', exact: true }).waitFor()
  state.fleetError = 403
  await page.getByRole('button', { name: 'Refresh stored observations', exact: true }).click()
  await page.getByText('Monitoring read access was denied.', { exact: false }).waitFor()
  assert.equal(await page.getByRole('table').count(), 0)
  assert((await sensitiveCache(page)).every(query => !query.data))
  assert.deepEqual(state.errors, [])
})

test('late history responses after logout cannot repopulate a sensitive query', async t => {
  let release
  const holdHistory = new Promise(resolve => { release = resolve })
  const { page, state } = await fixture(t, { holdHistory })
  await page.getByRole('button', { name: 'Inspect resource Runtime alpha', exact: true }).waitFor()
  await page.waitForFunction(() => window.fixtureClient.getQueryCache().findAll({ queryKey: ['infrastructure-monitoring'] }).some(query => query.queryKey[2] === 'history' && query.state.fetchStatus === 'fetching'))
  await changeSession(page, state, { ...admin, authenticated: false })
  await page.getByText('Platform administrator access is required.', { exact: true }).waitFor()
  release()
  await page.waitForFunction(() => window.fixtureClient.getQueryCache().findAll({ queryKey: ['infrastructure-monitoring'] }).length === 0)
  assert.equal((await sensitiveCache(page)).length, 0)
  assert.deepEqual(state.errors, [])
})

test('an empty fleet and an initial read failure render useful states without placeholder rows or history calls', async t => {
  const empty = await fixture(t, { fleet: { sources: [{ id: 'offline-source', status: 'UNAVAILABLE', message: 'No upstream connection' }], servers: [] } })
  await empty.page.getByText('No servers have been returned by the configured monitoring sources.', { exact: true }).waitFor()
  await empty.page.getByText('No upstream connection', { exact: false }).waitFor()
  assert.equal(await empty.page.getByRole('table').count(), 0)
  assert(!monitoringCalls(empty.state).some(call => call.path.endsWith('/history')))
  const failed = await fixture(t, { fleetError: 503 })
  await failed.page.getByText('Infrastructure observations could not be refreshed.', { exact: false }).waitFor()
  assert.equal(await failed.page.getByRole('table').count(), 0)
  assert(!monitoringCalls(failed.state).some(call => call.path.endsWith('/history')))
  assert.deepEqual([...empty.state.errors, ...failed.state.errors], [])
})
