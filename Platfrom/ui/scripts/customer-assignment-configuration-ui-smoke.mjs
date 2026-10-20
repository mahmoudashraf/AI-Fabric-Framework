import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.ASSIGNMENT_CONFIGURATION_SMOKE_OUTPUT
  ?? '/private/tmp/01044-customer-assignment-configuration-ui-20261020/browser-r1'
assert.match(output, /^\/private\/tmp\/01044-customer-assignment-configuration-ui-[a-zA-Z0-9-]+\/browser-r[0-9]+$/)
await mkdir(output)
const save = (name, value) => writeFile(`${output}/${name}`, JSON.stringify(value, null, 2) + '\n', { flag: 'wx' })
const hash = (value) => createHash('sha256').update(value).digest('hex')
const sourcePaths = ['src/api/platformApi.ts', 'src/components/customer/CustomerDeploymentConsole.tsx',
  'src/components/customer/customerDeploymentAssignments.ts', 'src/components/customer/KnownNoEffectSettlementPanel.tsx',
  'src/components/customer/customerAssignmentConfigurationIntents.ts',
  'src/components/customer/durableCommandHolds.ts', 'src/hooks/useScopedOneTimeValue.ts',
  'scripts/customer-assignment-configuration-ui-smoke.mjs',
  '../backend/src/main/java/com/ai/fabric/platform/backend/customerselfservice/model/CustomerStagingConsumerConfigurationContracts.java',
  '../backend/src/main/java/com/ai/fabric/platform/backend/customerselfservice/web/CustomerStagingConsumerConfigurationController.java',
  '../backend/src/main/java/com/ai/fabric/platform/backend/customerselfservice/service/CustomerStagingConsumerConfigurationService.java']
const sourcePins = await Promise.all(sourcePaths.map(async path => ({ path, sha256: hash(await readFile(`${root}/${path}`)) })))
await save('source.before.json', sourcePins)
const startedAt = new Date().toISOString(), results = [], journals = []
let server, browser, base, failure = null, activeContexts = 0
const cleanup = { browserCreated: false, browserClosed: null, serverClosed: false, contexts: null }
const fixtureTime = '2026-10-20T00:00:00Z'
const app = (suffix, rowVersion) => ({ id: `awi-00000000-0000-4000-8000-00000000000${suffix}`,
  installationId: `different-public-${suffix}`, customerId: 'customer', consumerId: 'existing-app',
  displayName: `Draft ${suffix}`, status: 'DRAFT', rowVersion, activatedAt: null, disabledAt: null,
  appSlug: `draft-${suffix}`, deliveryMode: 'embedded', allowedOrigins: ['https://owned.invalid'], customDomains: [],
  deploymentId: null, releaseId: null, createdAt: fixtureTime, updatedAt: fixtureTime })
const consumer = (id, overrides = {}) => ({ consumerId: id, customerId: 'customer', displayName: id,
  status: 'ACTIVE', boundDeploymentId: null, boundReleaseId: null, boundTargetProfileId: null,
  bindingRevision: 2, createdAt: fixtureTime, updatedAt: fixtureTime, ...overrides })
const offer = { id: 'offer-reviewed', name: 'Reviewed workspace', fingerprint: 'offer-pin', templateId: 'ai-only', blockers: [],
  entitlement: { customerId: 'customer', status: 'ACTIVE', revision: 4 }, bindingSlots: [],
  workspaceProfiles: [{ id: 'reviewed-workspace-profile', label: 'Reviewed workspace settings',
    interaction: {}, theme: null, authentication: {} }],
  settings: [{ setting: 'AI_STATE_STORAGE_MODE', type: 'CHOICE', allowedValues: ['MANAGED_POSTGRES', 'CUSTOMER_POSTGRES'] }],
  targets: ['staging-a', 'staging-b'].map(id => ({ id, intent: 'CUSTOMER_STAGING', readiness: 'READY',
    capabilities: ['READ', 'APP_CONFIGURE', 'BINDING_REPLACE'], limits: { stagingExpiryHours: 24 }, blockers: [] })) }
const deployments = ['dep-a', 'dep-b'].map(id => ({ deploymentId: id, name: id, status: 'DRAFT', environmentName: 'customer-staging',
  tenantId: `tenant-${id}`, offerId: offer.id, offerFingerprint: offer.fingerprint, activeVersionId: null,
  bootstrapTargetProfileId: id === 'dep-a' ? 'staging-a' : 'staging-b', authorizationEntitlementRevision: 4, createdAt: fixtureTime }))
const button = page => page.getByRole('button', { name: 'Configure existing assignment', exact: true })
const settings = (page, suffix) => page.getByRole('combobox', { name: `Settings for ${app(suffix, 0).id}`, exact: true })
const configurationCalls = f => f.state.calls.filter(call => call.path.endsWith('/staging-consumer-configurations'))
async function until(check, label) {
  const end = Date.now() + 10_000
  while (!(await check())) {
    if (Date.now() > end) throw new Error(`Timed out: ${label}`)
    await new Promise(resolve => setTimeout(resolve, 20))
  }
}

async function fixture(name) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 1100 }, serviceWorkers: 'block' })
  activeContexts++
  const page = await context.newPage()
  page.setDefaultTimeout(10_000)
  const state = { name, calls: [], errors: [], escaped: [], holdRead: false, holdConfigure: false, pendingRead: [], pendingConfigure: [],
    apps: [app(2, 3), app(1, 0), { ...app(9, 4), consumerId: 'another-app', status: 'ACTIVE' }],
    expectedProfiles: {}, denyConfigure: false,
    consumers: [consumer('existing-app'), consumer('spare-app'), consumer('foreign-app', { customerId: 'foreign' }),
      consumer('disabled-app', { status: 'DISABLED' }), consumer('other-bound', { boundDeploymentId: 'dep-other' })], versions: [] }
  journals.push(state)
  page.on('pageerror', error => state.errors.push(error.stack))
  await context.route('**/*', async route => {
    try {
      const url = new URL(route.request().url())
      if (url.origin !== new URL(base).origin) { state.escaped.push(url.href); return route.abort() }
      if (!url.pathname.startsWith('/api/')) return route.continue()
      const method = route.request().method(), path = url.pathname, rawBody = route.request().postData()
      const body = rawBody ? route.request().postDataJSON() : null
      state.calls.push({ path, method, body, rawBody })
      const json = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
      if (path === '/api/platform/auth/session') return json({ authenticated: true, actorId: await page.evaluate(() => window.__actorValue),
        role: 'CUSTOMER_ADMIN', customerId: 'customer', authenticationMode: 'SESSION' })
      if (path === '/api/customer-self-service/catalogue') return json([offer])
      if (path === '/api/customer-self-service/catalogue/offer-reviewed') return json(offer)
      if (path === '/api/customer-self-service/invitations') return json([])
      if (path === '/api/customer-self-service/deployments') return json(deployments)
      if (path === '/api/platform/customers/customer/consumers') return json(state.consumers)
      if (path === '/api/platform/customers/customer/ai-workspace-installations') {
        if (state.holdRead) { state.holdRead = false; state.pendingRead.push(() => json(state.apps)); return }
        return json(state.apps)
      }
      if (path === '/api/platform/preferences') return json(body ?? {})
      const draft = /^\/api\/deployments\/(dep-[ab])\/draft$/.exec(path)
      if (draft) return json({ id: `draft-${draft[1]}`, deploymentId: draft[1], status: 'DRAFT', revisionNumber: 1,
        updatedAt: fixtureTime, providerConfig: { aiStateStorageMode: 'MANAGED_POSTGRES' } })
      if (/^\/api\/deployments\/dep-[ab]\/versions$/.test(path)) return json(state.versions)
      if (/^\/api\/deployments\/dep-[ab]\/releases$/.test(path) || /\/(admission-reservations|retirements)$/.test(path)) return json([])
      if (path.endsWith('/staging-consumer-configurations') && method === 'POST') {
        assert.deepEqual(Object.keys(body).sort(), ['consumerId', 'expectedBindingRevision', 'expectedDraftWorkspaces', 'expectedEntitlementRevision', 'operationKey'])
        assert.equal(body.consumerId, 'existing-app'); assert.equal(body.expectedBindingRevision, 2)
        assert.equal(body.expectedEntitlementRevision, 4); assert.match(body.operationKey, /^[A-Za-z0-9._:-]{1,128}$/)
        assert.deepEqual(body.expectedDraftWorkspaces, state.apps.filter(item => item.consumerId === body.consumerId)
          .map(item => ({ installationEntityId: item.id, expectedRowVersion: item.rowVersion,
            ...(Object.hasOwn(state.expectedProfiles, item.id) ? { selectedWorkspaceProfileId: state.expectedProfiles[item.id] } : {}) }))
          .sort((a, b) => a.installationEntityId < b.installationEntityId ? -1 : 1))
        if (state.denyConfigure) return json({ success: false, errorCode: 'SETTING_FORBIDDEN', message: 'SETTING_FORBIDDEN' }, 403)
        const deploymentId = path.includes('/dep-a/') ? 'dep-a' : 'dep-b'
        const reply = async (lost = false, apply = true) => {
          const current = state.consumers.find(item => item.consumerId === body.consumerId)
          if (apply) {
            Object.assign(current, { boundDeploymentId: deploymentId, boundTargetProfileId: deploymentId === 'dep-a' ? 'staging-a' : 'staging-b',
              boundReleaseId: null, bindingRevision: 3 })
            state.apps = state.apps.map(item => item.consumerId === body.consumerId ? { ...item, deploymentId,
              rowVersion: item.rowVersion + 1 + (state.expectedProfiles[item.id] ? 1 : 0) } : item)
          }
          if (lost) return route.abort('connectionreset')
          return json(current)
        }
        if (state.holdConfigure) { state.pendingConfigure.push(reply); return }
        return reply()
      }
      state.errors.push(`Unhandled ${method} ${path}`)
      return json({ message: 'Unhandled mocked route; no forwarding' }, 500)
    } catch (error) {
      state.errors.push(error.stack)
      await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'Fixture assertion failed' }) })
        .catch(() => route.abort().catch(() => undefined))
    }
  })
  const f = { state, page, async close() {
    await save(`${name}.actual.json`, { ...state, pendingRead: state.pendingRead.length, pendingConfigure: state.pendingConfigure.length })
    try { await context.close() } finally { activeContexts-- }
    assert.deepEqual(state.errors, []); assert.deepEqual(state.escaped, [])
  } }
  try {
    await page.goto(`${base}assignment-fixture`, { waitUntil: 'networkidle' })
    await button(page).waitFor()
    await until(async () => await button(page).isEnabled(), 'complete owned DRAFT review')
    assert.equal(configurationCalls(f).length, 0, 'mount and selection must not configure automatically')
    return f
  } catch (error) { await f.close(); throw error }
}

async function scenario(name, action) {
  const f = await fixture(name)
  try { await action(f) }
  catch (error) {
    await f.page.screenshot({ path: `${output}/${name}.failure.png`, fullPage: true }).catch(() => undefined)
    await writeFile(`${output}/${name}.failure.txt`, `${error.stack}\n\n${await f.page.locator('body').innerText()}`, { flag: 'wx' })
    throw error
  } finally { await f.close() }
  results.push({ name, status: 'PASS', configurationPosts: configurationCalls(f).length })
  console.log(`PASS ${name}`)
}

try {
  server = await createServer({ root, configFile: false, envFile: false, cacheDir: `${output}/vite-cache`,
    esbuild: { jsx: 'automatic' }, server: { host: '127.0.0.1', port: 41793, strictPort: true },
    plugins: [{ name: 'assignment-configuration-source-fixture', enforce: 'pre',
      resolveId(id, importer) {
        if (id === '/assignment-entry.jsx') return '\0assignment-entry.jsx'
        if (id.endsWith('/workspace/DeploymentWorkspaceContext') && importer?.endsWith('/CustomerDeploymentConsole.tsx')) return '\0assignment-workspace.js'
      },
      load(id) {
        if (id === '\0assignment-workspace.js') return `import {useState} from 'react';export function useDeploymentWorkspace(){
          const [id,setId]=useState('dep-a');return {requestedDeploymentId:id,setSelectedDeploymentId:setId};}`
        if (id === '\0assignment-entry.jsx') return `import React,{useState} from 'react';import {createRoot} from 'react-dom/client';
          import {QueryClient,QueryClientProvider} from '@tanstack/react-query';import {MemoryRouter} from 'react-router-dom';
          import {CustomerDeploymentConsole} from '/src/components/customer/CustomerDeploymentConsole.tsx';
          const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
          function Harness(){const [actor,setActor]=useState('owner');window.__actor=setActor;window.__actorValue=actor;
            return React.createElement(QueryClientProvider,{client},React.createElement(MemoryRouter,null,
              React.createElement(CustomerDeploymentConsole,{session:{actorId:actor,authenticationMode:'SESSION',customerId:'customer',role:'CUSTOMER_ADMIN'}})));}
          createRoot(document.getElementById('root')).render(React.createElement(Harness));`
      },
      configureServer(vite) { vite.middlewares.use((request, response, next) => {
        if (request.url !== '/assignment-fixture') return next()
        response.setHeader('Content-Type', 'text/html')
        response.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1" /></head><body style="margin:16px"><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/assignment-entry.jsx"></script></body></html>')
      }) },
    }],
  })
  await server.listen(); base = server.resolvedUrls.local[0]
  assert.equal(new URL(base).hostname, '127.0.0.1')
  browser = await chromium.launch({ headless: true }); cleanup.browserCreated = true

  await scenario('explicit-pre-v1-complete-draft-fence', async f => {
    await f.page.getByText('DRAFT workspaces retained: 2', { exact: true }).waitFor()
    for (const suffix of [1, 2]) assert.equal(await settings(f.page, suffix).innerText(), 'Keep current settings')
    await settings(f.page, 1).click()
    await f.page.getByRole('option', { name: 'Reviewed workspace settings', exact: true }).click()
    await settings(f.page, 2).click()
    await f.page.getByRole('option', { name: 'Reviewed workspace settings', exact: true }).click()
    await settings(f.page, 2).click()
    await f.page.getByRole('option', { name: 'Keep current settings', exact: true }).click()
    f.state.expectedProfiles = { [app(1, 0).id]: 'reviewed-workspace-profile', [app(2, 0).id]: null }
    assert.equal(configurationCalls(f).length, 0, 'profile choices must not submit automatically')
    const preserved = rows => rows.map(({ displayName, appSlug, deliveryMode, allowedOrigins, customDomains, status, activatedAt }) =>
      ({ displayName, appSlug, deliveryMode, allowedOrigins, customDomains, status, activatedAt }))
    const before = preserved(structuredClone(f.state.apps))
    const panel = f.page.getByText('DRAFT workspaces retained: 2', { exact: true }).locator('..').locator('..')
    await panel.scrollIntoViewIfNeeded()
    await f.page.screenshot({ path: `${output}/desktop.png`, animations: 'disabled' })
    await f.page.setViewportSize({ width: 390, height: 844 }); await panel.scrollIntoViewIfNeeded()
    const bounds = await panel.boundingBox()
    assert.ok(bounds && bounds.x >= 0 && bounds.x + bounds.width <= 391)
    assert.equal(await panel.evaluate(element => element.scrollWidth > element.clientWidth + 1), false)
    await panel.screenshot({ path: `${output}/mobile.png`, animations: 'disabled' })
    await button(f.page).click()
    await f.page.getByText('Existing assignment configured. No release was activated.', { exact: true }).waitFor()
    assert.equal(configurationCalls(f).length, 1)
    assert.equal(await button(f.page).count(), 0)
    assert.equal(f.state.consumers[0].boundReleaseId, null)
    assert.deepEqual(preserved(f.state.apps), before)
    assert.equal(f.state.apps.find(item => item.id === app(1, 0).id).rowVersion, 2, 'mock adoption and binding may each advance app rowVersion')
    assert.equal(f.state.calls.some(call => /\/(staging-consumer-activations|staging-consumer-assignments)$/.test(call.path)), false)
  })
  await scenario('changed-owned-draft-list-no-post', async f => {
    f.state.apps[0].rowVersion++
    await button(f.page).click()
    await until(async () => f.state.calls.filter(call => call.path.endsWith('/ai-workspace-installations')).length >= 3
      && await button(f.page).isEnabled(), 'changed apps refetched and stale error suppressed')
    assert.equal(configurationCalls(f).length, 0)
  })
  await scenario('published-v1-during-preflight-no-post', async f => {
    f.state.versions = [{ id: 'new-v1', deploymentId: 'dep-a', status: 'PUBLISHED', versionLabel: 'v1', configHash: 'new-version-pin' }]
    await button(f.page).click()
    await f.page.getByText('Assignment or workspace configuration changed. Refresh and review before configuring.', { exact: true }).waitFor()
    assert.equal(configurationCalls(f).length, 0)
  })
  await scenario('changed-target-actor-and-late-completion', async f => {
    f.state.holdRead = true
    await button(f.page).click(); await until(() => f.state.pendingRead.length === 1, 'held preflight')
    await f.page.getByRole('button', { name: 'Manage deployment', exact: true }).nth(1).click()
    await f.page.getByRole('heading', { name: 'dep-b', level: 5, exact: true }).waitFor()
    f.state.holdRead = false; await f.state.pendingRead.shift()()
    await until(async () => await button(f.page).isEnabled(), 'new scope after rejected stale preflight')
    assert.equal(configurationCalls(f).length, 0)
    assert.equal(await f.page.getByText('Assignment or workspace configuration changed. Refresh and review before configuring.', { exact: true }).count(), 0)
    await f.page.getByRole('button', { name: 'Manage deployment', exact: true }).nth(0).click()
    f.state.holdConfigure = true
    await button(f.page).click(); await until(() => f.state.pendingConfigure.length === 1, 'held configuration POST')
    await f.page.getByRole('button', { name: 'Manage deployment', exact: true }).nth(1).click()
    await f.page.evaluate(() => window.__actor('different-owner'))
    await f.state.pendingConfigure.shift()()
    await until(async () => (await f.page.getByRole('combobox', { name: /^Customer app assignment/ }).innerText()).includes('spare-app'), 'old result updates only its captured binding')
    assert.equal(await f.page.getByText('Existing assignment configured. No release was activated.', { exact: true }).count(), 0)
    assert.equal(configurationCalls(f).length, 1)
    assert.ok(configurationCalls(f)[0].path.includes('/dep-a/'))
  })
  await scenario('double-click-lost-response-hold', async f => {
    await settings(f.page, 1).click()
    await f.page.getByRole('option', { name: 'Reviewed workspace settings', exact: true }).click()
    f.state.expectedProfiles = { [app(1, 0).id]: 'reviewed-workspace-profile' }
    f.state.holdConfigure = true
    await button(f.page).dblclick({ delay: 40 })
    await until(() => f.state.pendingConfigure.length === 1, 'one configuration dispatch')
    assert.equal(configurationCalls(f).length, 1)
    await f.state.pendingConfigure.shift()(true, false)
    await f.page.getByText('Assignment configuration response is unconfirmed. Reconcile its binding; do not resubmit.', { exact: true }).waitFor()
    assert.equal(await button(f.page).isDisabled(), true)
    assert.equal(await settings(f.page, 1).isDisabled(), true)
    await f.page.getByRole('button', { name: 'Manage deployment', exact: true }).nth(1).click()
    assert.equal(await button(f.page).isDisabled(), true)
    assert.equal(await settings(f.page, 1).innerText(), 'Keep current settings', 'new target does not inherit the old profile choice')
    assert.equal(await settings(f.page, 1).isDisabled(), true, 'changing target or profile choice cannot bypass the held row fence')
    await f.page.reload({ waitUntil: 'networkidle' })
    await button(f.page).waitFor()
    assert.equal(await button(f.page).isDisabled(), true)
    await f.page.evaluate(() => window.__actor('another-owner'))
    await until(async () => await button(f.page).isDisabled(), 'original consumer hold after authority change')
    f.state.apps[0].rowVersion++
    f.state.consumers[0].bindingRevision++
    await f.page.reload({ waitUntil: 'networkidle' })
    await button(f.page).waitFor()
    assert.equal(await button(f.page).isDisabled(), true, 'fresh row revisions cannot escape UNKNOWN')
    assert.equal(await f.page.getByRole('button', { name: 'Review rejected assignment', exact: true }).count(), 0)
    assert.equal(configurationCalls(f).length, 1)
    assert.equal(await f.page.getByText('Existing assignment configured. No release was activated.', { exact: true }).count(), 0)
  })
  await scenario('typed-rejection-explicit-fresh-review-new-intent', async f => {
    const before = structuredClone({ apps: f.state.apps, consumers: f.state.consumers })
    f.state.denyConfigure = true
    await button(f.page).click()
    await f.page.getByRole('button', { name: 'Review rejected assignment', exact: true }).waitFor()
    assert.equal(configurationCalls(f).length, 1)
    assert.deepEqual(configurationCalls(f)[0].body.expectedDraftWorkspaces,
      [app(1, 0), app(2, 3)].map(item => ({ installationEntityId: item.id, expectedRowVersion: item.rowVersion })))
    assert.equal(await button(f.page).isDisabled(), true)
    for (const suffix of [1, 2]) assert.equal(await settings(f.page, suffix).innerText(), 'Keep current settings')
    assert.deepEqual({ apps: f.state.apps, consumers: f.state.consumers }, before)
    assert.equal(await f.page.getByText('Existing assignment configured. No release was activated.', { exact: true }).count(), 0)
    await f.page.reload({ waitUntil: 'networkidle' })
    await f.page.getByRole('button', { name: 'Review rejected assignment', exact: true }).click()
    const confirm = f.page.getByRole('button', { name: 'Allow new reviewed intent', exact: true })
    await confirm.waitFor(); assert.equal(await confirm.isDisabled(), true)
    assert.equal(configurationCalls(f).length, 1, 'review and remount do not dispatch')
    await f.page.getByRole('checkbox', { name: 'I reviewed the rejected assignment and current DRAFT apps.', exact: true }).check()
    await confirm.click()
    await f.page.getByText('Known rejection reconciled. No assignment binding was changed.', { exact: true }).waitFor()
    assert.equal(configurationCalls(f).length, 1, 'confirmation is local/read-only, not a retry')
    assert.deepEqual({ apps: f.state.apps, consumers: f.state.consumers }, before)
    await until(async () => await button(f.page).isEnabled(), 'explicit new intent enabled')
    await settings(f.page, 1).click()
    await f.page.getByRole('option', { name: 'Reviewed workspace settings', exact: true }).click()
    f.state.expectedProfiles = { [app(1, 0).id]: 'reviewed-workspace-profile' }
    f.state.denyConfigure = false
    await button(f.page).click()
    await f.page.getByText('Existing assignment configured. No release was activated.', { exact: true }).waitFor()
    assert.equal(configurationCalls(f).length, 2)
    assert.notEqual(configurationCalls(f)[0].body.operationKey, configurationCalls(f)[1].body.operationKey)
    assert.equal(configurationCalls(f)[1].body.expectedDraftWorkspaces[0].expectedRowVersion, 0, 'no unbound-app edit bypass')
  })
  for (const pin of sourcePins) assert.equal(hash(await readFile(`${root}/${pin.path}`)), pin.sha256, `Source changed: ${pin.path}`)
} catch (error) {
  failure = { message: error.message, stack: error.stack }; console.error(error.stack); process.exitCode = 1
} finally {
  if (browser) { await browser.close(); cleanup.browserClosed = !browser.isConnected() }
  if (server) { await server.close(); cleanup.serverClosed = !server.httpServer?.listening }
  cleanup.contexts = activeContexts
  const proof = { phase: 'ACTUAL_CONFIGURATION_ONLY_CUSTOMER_UI_SMOKE', startedAt, finishedAt: new Date().toISOString(),
    status: failure ? 'FAIL' : 'PASS', plannedScenarios: 6, completedScenarios: results.length, results, failure, sourcePins, cleanup,
    nonLoopbackAttempts: journals.flatMap(item => item.escaped), base, allApiRequestsIntercepted: true, noHostedActions: true,
    limits: 'Actual Console and normal API transport with synthetic loopback fixtures. Not backend, hosted, activation, P5 or P6 qualification.' }
  if ((cleanup.browserCreated && !cleanup.browserClosed) || !cleanup.serverClosed || cleanup.contexts !== 0) {
    proof.status = 'FAIL'; proof.cleanupIncomplete = true; process.exitCode = 1
  }
  await save('qualification.actual.json', proof)
  console.log(JSON.stringify({ status: proof.status, completedScenarios: results.length, cleanup, proof: `${output}/qualification.actual.json` }))
}
