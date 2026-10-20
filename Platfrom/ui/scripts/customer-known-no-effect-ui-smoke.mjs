import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.KNOWN_NO_EFFECT_SMOKE_OUTPUT
  ?? '/private/tmp/01044-customer-known-no-effect-ui-smoke-20261020-r1'
assert.match(output, /^\/private\/tmp\/01044-customer-known-no-effect-ui-smoke-[a-zA-Z0-9-]+$/)
await mkdir(output)
const startedAt = new Date().toISOString()
const hash = (bytes) => createHash('sha256').update(bytes).digest('hex')
const save = (name, value) => writeFile(`${output}/${name}`, JSON.stringify(value, null, 2) + '\n', { flag: 'wx' })
const sourcePaths = ['src/api/platformApi.ts', 'src/components/customer/KnownNoEffectSettlementPanel.tsx',
  'src/components/customer/CustomerDeploymentConsole.tsx', 'src/components/customer/customerDeploymentAssignments.ts',
  'src/components/customer/customerAiStateStorage.ts', 'src/components/customer/CustomerAiStateStorageBinding.tsx',
  'src/hooks/useScopedOneTimeValue.ts', 'scripts/customer-ai-state-storage-ui-smoke.mjs',
  'scripts/customer-known-no-effect-ui-smoke.mjs']
const sourcePins = await Promise.all(sourcePaths.map(async (path) => {
  const bytes = await readFile(`${root}/${path}`)
  return { path, sha256: hash(bytes), bytes: bytes.length }
}))
await save('source.before.json', sourcePins)

const ids = { offer: 'offer-reviewed', a: 'dep-a', b: 'dep-b', targetA: 'customer-staging-a', targetB: 'customer-staging-b',
  reservationA: '00000000-0000-4000-8000-000000000001', reservationB: '00000000-0000-4000-8000-000000000002' }
const reservation = (target, released = false) => ({
  id: target === ids.targetA ? ids.reservationA : ids.reservationB,
  deploymentId: ids.a, targetProfileId: target, intent: 'CUSTOMER_STAGING', operation: 'APPLY',
  revision: released ? 2 : 1, state: released ? 'RELEASED' : 'RESERVED', expiryState: released ? 'RELEASED' : 'CURRENT',
  expiresAt: '2026-10-22T00:00:00Z', keepUntil: null,
})
const version = (id) => ({ id: `version-${id}`, deploymentId: id, sourceDraftId: `draft-${id}`, versionLabel: 'v1',
  status: 'PUBLISHED', configHash: hash(id), publishedAt: '2026-10-20T00:00:00Z', reindexRequired: false })
const offer = { id: ids.offer, name: 'Reviewed AI workspace', fingerprint: 'reviewed-offer-pin', templateId: 'ai-only', blockers: [],
  entitlement: { customerId: 'customer', status: 'ACTIVE', revision: 4 },
  settings: [{ setting: 'AI_STATE_STORAGE_MODE', type: 'CHOICE', allowedValues: ['MANAGED_POSTGRES', 'CUSTOMER_POSTGRES'] }],
  targets: [ids.targetA, ids.targetB].map((id) => ({ id, intent: 'CUSTOMER_STAGING', readiness: 'READY',
    capabilities: ['READ', 'BINDING_REPLACE'], limits: { stagingExpiryHours: 24 }, blockers: [] })), bindingSlots: [] }
const deployments = [ids.a, ids.b].map((id) => ({ deploymentId: id,
  name: id === ids.a ? 'Original failed workspace' : 'Fresh PostgreSQL workspace', environmentName: 'customer-staging',
  status: 'DRAFT', tenantId: `tenant-${id}`, offerId: ids.offer, offerFingerprint: offer.fingerprint,
  bootstrapTargetProfileId: id === ids.a ? ids.targetA : ids.targetB,
  authorizationEntitlementRevision: 4, activeVersionId: null, createdAt: '2026-10-20T00:00:00Z' }))
const release = (id) => ({ id: `release-${id}`, deploymentId: id, deploymentVersionId: version(id).id,
  targetProfileId: id === ids.a ? ids.targetA : ids.targetB,
  status: id === ids.a ? 'FAILED' : 'APPLIED_VERIFIED', verificationStatus: id === ids.a ? 'FAILED' : 'PASSED',
  provisioningStatus: id === ids.a ? 'NOT_STARTED' : 'ACTIVE', currentStepDescription: id === ids.a ? 'Failed before provider effects' : 'Verified staging',
  createdAt: '2026-10-20T00:00:00Z' })
const consumer = (id, name, deploymentId = null, overrides = {}) => ({ consumerId: id, customerId: 'customer',
  displayName: name, status: 'ACTIVE', description: null, boundDeploymentId: deploymentId,
  boundDeploymentName: deploymentId, boundDeploymentEnvironment: null, boundDeploymentStatus: null,
  boundReleaseId: null, boundReleaseStatus: null,
  boundTargetProfileId: deploymentId === ids.a ? ids.targetA : deploymentId === ids.b ? ids.targetB : null,
  bindingRevision: 1, lastBoundAt: null, createdAt: '2026-10-20T00:00:00Z', updatedAt: '2026-10-20T00:00:00Z', ...overrides })

const workspaceId = '\0settlement-workspace.js', entryId = '\0settlement-entry.jsx'
let server, browser, base, activeFixtures = 0
const results = [], fixtures = []
const cleanup = { browserCreated: false, browserClosed: null, serverClosed: false, activeFixtures: null }
let failure = null

async function waitUntil(predicate, description) {
  const until = Date.now() + 10_000
  while (!(await predicate())) {
    if (Date.now() > until) throw new Error(`Timed out: ${description}`)
    await new Promise((resolve) => setTimeout(resolve, 20))
  }
}
const settleCalls = (f) => f.state.calls.filter((call) => call.path.endsWith('/settle-known-no-effect'))
const planCalls = (f) => f.state.calls.filter((call) => call.path.endsWith('/known-no-effect-plan'))
const confirmLabel = 'I confirm these failed releases and consumer detachments.'
const submitButton = (page) => page.getByRole('button', { name: 'Settle failed reservation', exact: true })
async function preview(page) {
  await page.getByRole('textbox', { name: 'Settlement reason', exact: true }).fill('Review the original known-no-effect failure')
  await page.getByRole('button', { name: 'Preview settlement', exact: true }).click()
  await page.getByLabel(confirmLabel, { exact: true }).waitFor()
}
async function choose(page, name, option) {
  await page.getByRole('combobox', { name: new RegExp(`^${name}`) }).click()
  await page.getByRole('option', { name: option, exact: true }).click()
}

async function fixture(name, { consoleView = false, holdPlan = false, holdSettle = false, invalidReceipt = false } = {}) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 1100 }, serviceWorkers: 'block' })
  activeFixtures += 1
  const page = await context.newPage()
  const state = { name, calls: [], errors: [], escaped: [], requestFailures: [], pendingPlan: [], pendingSettle: [], plans: {},
    holdPlan, holdSettle, invalidReceipt, settled: false,
    consumers: [consumer('source-app', 'Original app', ids.a), consumer('spare-app', 'Owned spare'),
      consumer('destination-app', 'Destination current', ids.b), consumer('foreign-app', 'Foreign app', null, { customerId: 'foreign' }),
      consumer('inactive-app', 'Inactive app', null, { status: 'DISABLED' }), consumer('other-bound', 'Other bound', 'dep-other'),
      consumer('partial-unassigned', 'Partial binding', null, { boundTargetProfileId: 'stale-target' })] }
  fixtures.push(state)
  page.on('pageerror', (error) => state.errors.push(error.message))
  page.on('requestfailed', (request) => state.requestFailures.push({ url: request.url(), error: request.failure()?.errorText }))
  await context.route('**/*', async (route) => {
    try {
    const url = new URL(route.request().url())
    if (url.origin !== new URL(base).origin) { state.escaped.push(url.href); return route.abort() }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const request = route.request(), path = url.pathname, method = request.method()
    const rawBody = request.postData(), body = rawBody ? request.postDataJSON() : null
    state.calls.push({ path, query: url.search, method, body, rawBody })
    const json = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
    if (path.endsWith('/known-no-effect-plan') && method === 'POST') {
      const id = decodeURIComponent(path.split('/').at(-2)), target = id === ids.reservationA ? ids.targetA : ids.targetB
      assert.ok([ids.reservationA, ids.reservationB].includes(id), 'unexpected reservation')
      const plan = { proofFingerprint: hash(`proof-${id}`), command: {
        fence: { reservationId: id, revision: 1, generation: `server-generation-${target}` }, ...body,
        manifestFingerprint: hash(`manifest-${id}`), versionId: version(ids.a).id, versionConfigHash: hash(ids.a),
        bindingFingerprint: hash(`binding-${target}`), failedReleases: [{ releaseId: release(ids.a).id,
          authorizationOperationKey: 'original-auth-key', authorizationRequestFingerprint: hash('original-auth-request') }],
        consumerDetachments: [{ consumerEntityId: 'source-app', expectedBindingRevision: 1,
          draftWorkspaces: [{ installationEntityId: 'awi-00000000-0000-4000-8000-000000000003', expectedRowVersion: 0 }] }],
      } }
      state.plans[id] = plan
      if (state.holdPlan) { state.pendingPlan.push(() => json(plan)); return }
      return json(plan)
    }
    if (path.endsWith('/settle-known-no-effect') && method === 'POST') {
      const id = decodeURIComponent(path.split('/').at(-2)), plan = state.plans[id]
      assert.ok(plan, 'settle without a preview')
      assert.equal(rawBody, JSON.stringify(plan.command), 'the exact server command must be posted unchanged')
      const reply = async (lost = false) => {
        state.settled = true
        state.consumers = state.consumers.map((item) => item.consumerId !== 'source-app' ? item
          : { ...item, boundDeploymentId: null, boundTargetProfileId: null, boundReleaseId: null, bindingRevision: 2 })
        if (lost) return route.abort('connectionreset')
        if (state.invalidReceipt) return json({ accepted: true })
        return json({ id: `receipt-${id}`, reservationId: id, generation: plan.command.fence.generation, fromRevision: 1, revision: 2,
          deploymentId: ids.a, targetProfileId: id === ids.reservationA ? ids.targetA : ids.targetB,
          outcome: 'SETTLED_KNOWN_NO_EFFECT', reclamationPerformed: false, settledAt: '2026-10-20T15:13:45.342086Z',
          failedReleaseIds: plan.command.failedReleases.map((item) => item.releaseId),
          consumerDetachments: plan.command.consumerDetachments.map((item) => ({ consumerEntityId: item.consumerEntityId,
            fromBindingRevision: item.expectedBindingRevision, toBindingRevision: item.expectedBindingRevision + 1,
            historyId: 'server-history', operationKey: 'server-detachment-key', preservedDraftWorkspaces: item.draftWorkspaces })) })
      }
      if (state.holdSettle) { state.pendingSettle.push(reply); return }
      return reply()
    }
    if (path === '/api/customer-self-service/catalogue') return json([offer])
    if (path === '/api/customer-self-service/invitations') return json([])
    if (path === '/api/customer-self-service/deployments') return json(deployments)
    if (path === '/api/platform/customers/customer/consumers') return json(state.consumers)
    if (path === '/api/platform/preferences') return json(body ?? {})
    const draft = /^\/api\/deployments\/(dep-[ab])\/draft$/.exec(path)
    if (draft) return json({ id: `draft-${draft[1]}`, deploymentId: draft[1], revisionNumber: 1,
      updatedAt: '2026-10-20T00:00:00Z', providerConfig: { aiStateStorageMode: 'MANAGED_POSTGRES' } })
    const versions = /^\/api\/deployments\/(dep-[ab])\/versions$/.exec(path)
    if (versions) return json([version(versions[1])])
    const releases = /^\/api\/deployments\/(dep-[ab])\/releases$/.exec(path)
    if (releases) return json([release(releases[1])])
    if (path.endsWith('/retirements')) return json([])
    if (path.endsWith('/admission-reservations')) {
      const id = path.includes('/dep-a/') ? ids.a : ids.b
      if (method === 'POST') return json({ ...reservation(ids.targetB), id: 'destination-allocation', deploymentId: ids.b, state: 'ACTIVE' })
      return json(id === ids.a ? [reservation(ids.targetA, state.settled)]
        : [{ ...reservation(ids.targetB), id: 'destination-allocation', deploymentId: ids.b, state: 'ACTIVE' }])
    }
    if (path.endsWith('/placement-evidence') && method === 'POST') return json({ id: 'placement', intent: 'CUSTOMER_STAGING' })
    if (path.endsWith('/staging-consumer-activations') && method === 'POST') {
      assert.ok(path.includes('/dep-b/'))
      const selected = state.consumers.find((item) => item.consumerId === body.consumerId)
      assert.ok(selected && selected.customerId === 'customer' && selected.status === 'ACTIVE')
      assert.equal(body.expectedBindingRevision, selected.bindingRevision)
      Object.assign(selected, { boundDeploymentId: ids.b, boundTargetProfileId: ids.targetB, boundReleaseId: release(ids.b).id,
        boundReleaseStatus: 'APPLIED_VERIFIED', bindingRevision: selected.bindingRevision + 1 })
      return json(selected)
    }
    state.errors.push(`Unhandled ${method} ${path}`)
    return json({ message: 'Unhandled fixture route; no forwarding' }, 500)
    } catch (error) {
      state.errors.push(error.stack)
      await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'Fixture assertion failed' }) })
        .catch(() => route.abort().catch(() => undefined))
    }
  })
  const f = { state, page, context, async close() {
    await save(`${name}.actual.json`, { ...state, pendingPlan: state.pendingPlan.length, pendingSettle: state.pendingSettle.length })
    try { await context.close() } finally { activeFixtures -= 1 }
    assert.deepEqual(state.escaped, [], 'non-loopback requests were attempted and aborted')
    assert.deepEqual(state.errors, [], 'page or fixture errors')
  } }
  try {
    await page.goto(`${base}settlement-fixture?view=${consoleView ? 'console' : 'panel'}`, { waitUntil: 'networkidle' })
    await page.getByText('Failed release settlement', { exact: true }).waitFor()
    assert.equal(planCalls(f).length, 0, 'mount must not prepare a plan automatically')
    assert.equal(settleCalls(f).length, 0, 'mount must not settle automatically')
    return f
  } catch (error) { await f.close(); throw error }
}

async function scenario(name, options, action) {
  const f = await fixture(name, options)
  try {
    await action(f)
  } catch (error) {
    await f.page.screenshot({ path: `${output}/${name}.failure.png`, fullPage: true }).catch(() => undefined)
    await writeFile(`${output}/${name}.failure.txt`, `${error.stack}\n\n${await f.page.locator('body').innerText()}`, { flag: 'wx' })
    throw error
  } finally { await f.close() }
  results.push({ name, status: 'PASS', planPosts: planCalls(f).length, settlementPosts: settleCalls(f).length })
  console.log(`PASS ${name}`)
}

try {
  server = await createServer({ root, configFile: false, envFile: false, cacheDir: `${output}/vite-cache`,
    esbuild: { jsx: 'automatic' }, server: { host: '127.0.0.1', port: 41791 },
    plugins: [{ name: 'known-no-effect-source-fixture', enforce: 'pre',
      resolveId(id, importer) {
        if (id === '/settlement-entry.jsx') return entryId
        if (id.endsWith('/workspace/DeploymentWorkspaceContext') && importer?.endsWith('/CustomerDeploymentConsole.tsx')) return workspaceId
      },
      load(id) {
        if (id === workspaceId) return `import {useState} from 'react';
          export function useDeploymentWorkspace(){const [id,setId]=useState('dep-a');
          return {requestedDeploymentId:id,setSelectedDeploymentId:setId};}`
        if (id === entryId) return `import React,{useState} from 'react';import {createRoot} from 'react-dom/client';
          import {QueryClient,QueryClientProvider} from '@tanstack/react-query';import {MemoryRouter} from 'react-router-dom';
          import {CustomerDeploymentConsole} from '/src/components/customer/CustomerDeploymentConsole.tsx';
          import {KnownNoEffectSettlementPanel} from '/src/components/customer/KnownNoEffectSettlementPanel.tsx';
          const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
          window.__settled=[];
          function Panel(){const [target,setTarget]=useState('${ids.targetA}');
            const reservation=target==='${ids.targetA}'?${JSON.stringify(reservation(ids.targetA))}:${JSON.stringify(reservation(ids.targetB))};
            return React.createElement('main',{style:{maxWidth:760,margin:'24px auto',padding:16}},
              React.createElement('label',null,'Fixture target ',React.createElement('select',{value:target,onChange:e=>setTarget(e.target.value)},
                React.createElement('option',{value:'${ids.targetA}'},'${ids.targetA}'),React.createElement('option',{value:'${ids.targetB}'},'${ids.targetB}'))),
              React.createElement(KnownNoEffectSettlementPanel,{authority:{actorId:'owner',customerId:'customer',authenticationMode:'SESSION'},
                targetProfileId:target,offerId:'${ids.offer}',deploymentId:'${ids.a}',reservation,candidateFailed:true,
                onSettled:(receipt,scope)=>window.__settled.push({receipt,scope})}));}
          const consoleView=new URLSearchParams(location.search).get('view')==='console';
          createRoot(document.getElementById('root')).render(React.createElement(QueryClientProvider,{client},
            React.createElement(MemoryRouter,null,consoleView?React.createElement(CustomerDeploymentConsole,
              {session:{customerId:'customer',actorId:'owner',role:'CUSTOMER_ADMIN',authenticationMode:'SESSION'}}):React.createElement(Panel))));`
      },
      configureServer(vite) { vite.middlewares.use((request, response, next) => {
        if (request.url?.split('?')[0] !== '/settlement-fixture') return next()
        response.setHeader('Content-Type', 'text/html')
        response.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1" /></head><body style="margin:16px"><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/settlement-entry.jsx"></script></body></html>')
      }) },
    }],
  })
  await server.listen()
  base = server.resolvedUrls.local[0]
  assert.equal(new URL(base).hostname, '127.0.0.1')
  browser = await chromium.launch({ headless: true })
  cleanup.browserCreated = true

  await scenario('explicit-preview-and-command', {}, async (f) => {
    await preview(f.page)
    assert.equal(planCalls(f).length, 1)
    assert.deepEqual(Object.keys(planCalls(f)[0].body).sort(), ['operationKey', 'reason'])
    await f.page.getByText('release-dep-a', { exact: true }).waitFor()
    await f.page.getByText(/Preserved DRAFT: awi-00000000/).waitFor()
    assert.equal(await submitButton(f.page).isDisabled(), true)
    assert.equal(settleCalls(f).length, 0)
    await f.page.getByLabel(confirmLabel, { exact: true }).check()
    assert.equal(settleCalls(f).length, 0, 'confirmation alone must not submit')
    await submitButton(f.page).click()
    await f.page.getByText(/Settlement receipt receipt-/).waitFor()
    assert.equal(settleCalls(f).length, 1)
    const callbacks = await f.page.evaluate(() => window.__settled)
    assert.equal(callbacks.length, 1)
    assert.deepEqual(callbacks[0].scope, { authority: {actorId:'owner',customerId:'customer',authenticationMode:'SESSION'},
      offerId: ids.offer, deploymentId: ids.a,
      reservationId: ids.reservationA, targetProfileId: ids.targetA, revision: 1 })
  })

  await scenario('lost-response-and-double-click', { holdSettle: true }, async (f) => {
    await preview(f.page)
    await f.page.getByLabel(confirmLabel, { exact: true }).check()
    await submitButton(f.page).dblclick({ delay: 40 })
    await waitUntil(() => f.state.pendingSettle.length === 1, 'one held settlement')
    assert.equal(settleCalls(f).length, 1)
    await f.state.pendingSettle.shift()(true)
    await f.page.getByText('Settlement response is unconfirmed. Do not resubmit; reconcile the reservation.', { exact: true }).waitFor()
    assert.equal(await submitButton(f.page).isDisabled(), true)
    assert.deepEqual(await f.page.evaluate(() => window.__settled), [])
    assert.equal(await f.page.getByText(/Settlement receipt receipt-/).count(), 0)
    await f.page.getByLabel('Fixture target', { exact: false }).selectOption(ids.targetB)
    await f.page.getByLabel('Fixture target', { exact: false }).selectOption(ids.targetA)
    assert.equal(await f.page.getByRole('button', { name: 'Preview settlement', exact: true }).isDisabled(), true)
    assert.equal(planCalls(f).length, 1)
    assert.equal(settleCalls(f).length, 1, 'lost response must not trigger replan or retry')
    assert.equal(f.state.settled, true, 'synthetic server accepted before the response was lost')
  })

  await scenario('stale-preview-after-target-change', { holdPlan: true }, async (f) => {
    await f.page.getByRole('textbox', { name: 'Settlement reason', exact: true }).fill('Original target review')
    await f.page.getByRole('button', { name: 'Preview settlement', exact: true }).click()
    await waitUntil(() => f.state.pendingPlan.length === 1, 'held original target preview')
    await f.page.getByLabel('Fixture target', { exact: false }).selectOption(ids.targetB)
    await f.page.getByRole('button', { name: 'Preview settlement', exact: true }).waitFor()
    const response = f.page.waitForResponse((r) => r.url().endsWith('/known-no-effect-plan'))
    await f.state.pendingPlan.shift()(); await response
    await f.page.getByRole('textbox', { name: 'Settlement reason', exact: true }).fill('New target review')
    assert.equal(await f.page.getByLabel(confirmLabel, { exact: true }).count(), 0)
    assert.equal(await submitButton(f.page).count(), 0)
    assert.equal(settleCalls(f).length, 0)
    f.state.holdPlan = false
    await f.page.getByRole('button', { name: 'Preview settlement', exact: true }).click()
    await f.page.getByLabel(confirmLabel, { exact: true }).waitFor()
    assert.equal(planCalls(f).length, 2)
    assert.ok(planCalls(f)[1].path.includes(ids.reservationB))
    assert.notEqual(planCalls(f)[0].body.operationKey, planCalls(f)[1].body.operationKey)
    assert.equal(planCalls(f)[1].body.reason, 'New target review')
    assert.equal(await f.page.getByLabel(confirmLabel, { exact: true }).isChecked(), false)
    assert.equal(settleCalls(f).length, 0)
  })

  await scenario('parent-settlement-and-owned-reuse', { consoleView: true }, async (f) => {
    const assignment = f.page.getByRole('combobox', { name: /^Customer app assignment/ })
    await assignment.click()
    assert.deepEqual(await f.page.getByRole('option').allTextContents(), ['Original app \u00b7 revision 1', 'Owned spare \u00b7 revision 1 \u00b7 Unassigned'])
    await f.page.getByRole('option', { name: 'Owned spare \u00b7 revision 1 \u00b7 Unassigned', exact: true }).click()
    assert.equal(await f.page.getByRole('button', { name: 'Create staging assignment', exact: true }).count(), 0)
    await choose(f.page, 'Customer app assignment', 'Original app \u00b7 revision 1')
    await preview(f.page)
    const panel = f.page.getByText('Failed release settlement', { exact: true }).locator('..')
    await panel.scrollIntoViewIfNeeded()
    await f.page.screenshot({ path: `${output}/desktop.png`, animations: 'disabled' })
    await f.page.setViewportSize({ width: 390, height: 844 })
    await panel.scrollIntoViewIfNeeded()
    const bounds = await panel.boundingBox()
    assert.ok(bounds && bounds.x >= 0 && bounds.x + bounds.width <= 391, 'mobile panel must fit the viewport')
    const overflow = await panel.evaluate((element) => element.scrollWidth > element.clientWidth + 1)
    assert.equal(overflow, false, 'mobile panel has horizontal content overflow')
    await panel.screenshot({ path: `${output}/mobile.png`, animations: 'disabled' })
    await f.page.setViewportSize({ width: 1280, height: 1100 })
    await f.page.getByLabel(confirmLabel, { exact: true }).check()
    await submitButton(f.page).click()
    await waitUntil(async () => (await assignment.innerText()).includes('revision 2'), 'parent refresh after settlement')
    assert.equal(await f.page.getByText('Failed release settlement', { exact: true }).count(), 0)
    assert.equal(settleCalls(f).length, 1)
    await f.page.getByRole('button', { name: 'Manage deployment', exact: true }).nth(1).click()
    await f.page.getByRole('heading', { name: 'Fresh PostgreSQL workspace', level: 5, exact: true }).waitFor()
    await assignment.click()
    assert.deepEqual(await f.page.getByRole('option').allTextContents(), ['Destination current \u00b7 revision 1',
      'Original app \u00b7 revision 2 \u00b7 Unassigned', 'Owned spare \u00b7 revision 1 \u00b7 Unassigned'])
    await f.page.getByRole('option', { name: 'Original app \u00b7 revision 2 \u00b7 Unassigned', exact: true }).click()
    await f.page.getByRole('button', { name: 'Use latest verified staging release', exact: true }).click()
    await f.page.getByText('App assignment now resolves to the latest verified staging release.', { exact: true }).waitFor()
    const activations = f.state.calls.filter((call) => call.path.endsWith('/staging-consumer-activations'))
    assert.equal(activations.length, 1)
    assert.deepEqual({ ...activations[0].body, operationKey: '<stable-key>' }, { consumerId: 'source-app', releaseId: release(ids.b).id,
      expectedBindingRevision: 2, expectedEntitlementRevision: 4, operationKey: '<stable-key>' })
    assert.equal(f.state.calls.some((call) => call.path.endsWith('/staging-consumer-assignments')), false)
    assert.equal(f.state.calls.some((call) => call.method === 'POST' && call.path.endsWith('/consumers')), false)
    await assignment.scrollIntoViewIfNeeded()
    await f.page.screenshot({ path: `${output}/owned-reuse-desktop.png`, animations: 'disabled' })
  })

  await scenario('http-200-without-receipt-holds', { invalidReceipt: true }, async (f) => {
    await preview(f.page)
    await f.page.getByLabel(confirmLabel, { exact: true }).check()
    await submitButton(f.page).click()
    await f.page.getByText('Settlement response is unconfirmed. Do not resubmit; reconcile the reservation.', { exact: true }).waitFor()
    assert.equal(settleCalls(f).length, 1)
    assert.equal(await submitButton(f.page).isDisabled(), true)
    assert.equal(await f.page.getByText(/Settlement receipt receipt-/).count(), 0)
    assert.deepEqual(await f.page.evaluate(() => window.__settled), [])
  })
  for (const pin of sourcePins) assert.equal(hash(await readFile(`${root}/${pin.path}`)), pin.sha256, `source changed during run: ${pin.path}`)
} catch (error) {
  failure = { message: error.message, stack: error.stack }
  console.error(error.stack)
  process.exitCode = 1
} finally {
  if (browser) { await browser.close(); cleanup.browserClosed = !browser.isConnected() }
  if (server) { await server.close(); cleanup.serverClosed = !server.httpServer?.listening }
  cleanup.activeFixtures = activeFixtures
  const proof = { phase: 'ACTUAL_LOOPBACK_SOURCE_UI_SMOKE', startedAt, finishedAt: new Date().toISOString(),
    status: failure ? 'FAIL' : 'PASS', plannedScenarios: 5, completedScenarios: results.length, scenarios: results, failure,
    sourcePins, base, cleanup, nonLoopbackAttempts: fixtures.flatMap((state) => state.escaped),
    noHostedCalls: true, allApiRequestsIntercepted: true, noBackendOrComponentEdits: true, noDeployment: true,
    limits: 'Source-rendered local mocked APIs only. No hosted settlement, provider action, P5 or P6 qualification. Console preference POSTs are intercepted fixture behavior, not settlement automation.' }
  if ((cleanup.browserCreated && !cleanup.browserClosed) || !cleanup.serverClosed || cleanup.activeFixtures !== 0) {
    proof.status = 'FAIL'; proof.cleanupIncomplete = true; process.exitCode = 1
  }
  await save('qualification.actual.json', proof)
  console.log(JSON.stringify({ status: proof.status, completedScenarios: results.length, proof: `${output}/qualification.actual.json`, cleanup }))
}
