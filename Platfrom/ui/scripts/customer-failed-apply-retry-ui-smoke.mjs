import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.FAILED_APPLY_RETRY_SMOKE_OUTPUT
  ?? '/private/tmp/01044-failed-apply-retry-functional-ui-20261020/browser-r1'
assert.match(output, /^\/private\/tmp\/01044-failed-apply-retry-functional-ui-[a-zA-Z0-9-]+\/browser-r[0-9]+$/)
await mkdir(output)
const save = (name, value) => writeFile(`${output}/${name}`, JSON.stringify(value, null, 2) + '\n', { flag: 'wx' })
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const paths = ['src/api/platformApi.ts', 'src/components/customer/CustomerDeploymentConsole.tsx',
  'src/components/customer/durableCommandHolds.ts', 'src/hooks/useScopedOneTimeValue.ts',
  'scripts/customer-failed-apply-retry-ui-smoke.mjs',
  '../backend/src/main/java/com/ai/fabric/platform/backend/deployment/admission/FailedApplyRetryContracts.java',
  '../backend/src/main/java/com/ai/fabric/platform/backend/deployment/admission/FailedApplyRetryController.java']
const pins = await Promise.all(paths.map(async path => ({ path, sha256: hash(await readFile(`${root}/${path}`)) })))
await save('source.before.json', pins)
const startedAt = new Date().toISOString(), results = [], journals = []
let server, browser, base, contexts = 0, failure = null
const cleanup = { browserClosed: false, serverClosed: false, contexts: null }
const time = '2026-10-20T20:00:00Z'
const offer = { id: 'offer', fingerprint: 'offer-pin', name: 'Reviewed offer', blockers: [], templateId: 'reviewed-template', settings: [], bindingSlots: [],
  entitlement: { customerId: 'customer', status: 'ACTIVE', revision: 7 }, targets: ['a', 'b'].map(id => ({ id: `stage-${id}`, intent: 'CUSTOMER_STAGING',
    readiness: 'READY', capabilities: ['READ', 'APPLY'], limits: { stagingExpiryHours: 24 }, blockers: [] })) }
const deployments = ['a', 'b'].map(id => ({ deploymentId: `dep-${id}`, tenantId: `tenant-${id}`, name: `dep-${id}`, status: 'DRAFT',
  offerId: 'offer', offerFingerprint: 'offer-pin', bootstrapTargetProfileId: `stage-${id}`, activeVersionId: null, environmentName: 'staging', createdAt: time }))
const reservation = id => ({ id: `reservation-${id}`, deploymentId: `dep-${id}`, targetProfileId: `stage-${id}`, state: 'OUTCOME_UNKNOWN', revision: 3,
  intent: 'CUSTOMER_STAGING', operation: 'APPLY', expiryState: 'CURRENT', expiresAt: '2026-10-21T20:00:00Z', keepUntil: null })
const release = id => ({ id: `failed-${id}`, deploymentId: `dep-${id}`, deploymentVersionId: `version-${id}`, targetProfileId: `stage-${id}`,
  status: 'FAILED', provisioningStatus: 'FAILED', verificationStatus: 'SKIPPED', currentStepDescription: 'Synthetic retained preparation failure', createdAt: time })
const preview = page => page.getByRole('button', { name: 'Preview owned-service retry', exact: true })
const recover = page => page.getByRole('button', { name: 'Authorize owned-service redeploy', exact: true })
const consent = 'I accept redeployment of both owned runtime and connector services.'
const confirmation = 'I confirm this plan authorizes real runtime and connector redeploys.'
async function until(check, label) {
  const end = Date.now() + 10_000
  while (!(await check())) {
    if (Date.now() > end) throw new Error(`Timed out: ${label}`)
    await new Promise(resolve => setTimeout(resolve, 20))
  }
}
async function prepare(page) {
  await page.getByRole('textbox', { name: 'Redeploy reason', exact: true }).fill('Explicit owned service redeploy review')
  assert.equal(await preview(page).isDisabled(), true)
  await page.getByRole('checkbox', { name: consent, exact: true }).check()
  await preview(page).click(); await recover(page).waitFor()
}
async function scenario(name, action) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 1000 }, serviceWorkers: 'block' }); contexts++
  const page = await context.newPage(); page.setDefaultTimeout(10_000)
  const state = { calls: [], errors: [], escaped: [], pendingPlans: [], pendingRecoveries: [], plans: {}, denyPlan: false,
    holdPlan: false, holdRecovery: false, malformedReceipt: false, recovered: [] }
  journals.push(state)
  page.on('pageerror', error => state.errors.push(error.stack))
  await context.route('**/*', async route => {
    try {
      const url = new URL(route.request().url())
      if (url.origin !== new URL(base).origin) { state.escaped.push(url.href); return route.abort() }
      if (!url.pathname.startsWith('/api/')) return route.continue()
      const path = url.pathname, method = route.request().method(), raw = route.request().postData(), body = raw ? route.request().postDataJSON() : null
      state.calls.push({ path, method, body, raw })
      const json = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
      if (path === '/api/platform/auth/session') return json({ authenticated: true, actorId: await page.evaluate(() => window.__actorValue),
        customerId: 'customer', role: 'CUSTOMER_ADMIN', authenticationMode: 'SESSION' })
      if (path === '/api/customer-self-service/catalogue') return json([offer])
      if (path === '/api/customer-self-service/invitations') return json([])
      if (path === '/api/customer-self-service/deployments') return json(deployments)
      if (path === '/api/platform/customers/customer/consumers') return json([])
      if (path === '/api/platform/preferences') return json(body ?? {})
      const records = /^\/api\/deployments\/dep-([ab])\/(versions|releases)$/.exec(path)
      if (records) return json(records[2] === 'versions' ? [{ id: `version-${records[1]}`, deploymentId: `dep-${records[1]}`, versionLabel: 'v1', status: 'PUBLISHED' }]
        : [{ ...release(records[1]), ...(state.recovered.includes(records[1]) ? { status: 'APPLY_REQUESTED', provisioningStatus: 'QUEUED', verificationStatus: 'PENDING' } : {}) }])
      const admission = /^\/api\/customer-self-service\/catalogue\/offer\/deployments\/dep-([ab])\/admission-reservations$/.exec(path)
      if (admission) return json([{ ...reservation(admission[1]), ...(state.recovered.includes(admission[1]) ? { state: 'RESERVED', revision: 4 } : {}) }])
      if (path.endsWith('/retirements')) return json([])
      const command = /^\/api\/customer-self-service\/catalogue\/offer\/deployments\/dep-([ab])\/admission-reservations\/reservation-([ab])\/(failed-apply-retry-plan|retry-failed-apply)$/.exec(path)
      if (command) {
        assert.equal(command[1], command[2]); assert.equal(method, 'POST')
        const id = command[1]
        if (command[3] === 'failed-apply-retry-plan') {
          assert.deepEqual(Object.keys(body).sort(), ['acceptOwnedServiceRedeploy', 'operationKey', 'reason', 'releaseId'])
          assert.equal(body.acceptOwnedServiceRedeploy, true)
          assert.equal(body.releaseId, `failed-${id}`)
          if (state.denyPlan) return json({ success: false, errorCode: 'FAILED_APPLY_RETRY_LOCAL_STATE_CHANGED', message: 'FAILED_APPLY_RETRY_LOCAL_STATE_CHANGED' }, 409)
          const response = { command: { fence: { reservationId: `reservation-${id}`, revision: 3, generation: `server-generation-${id}` },
            ...body, proofFingerprint: hash(`server-proof-${id}`) }, boundary: 'INITIAL_COOLIFY_CONNECTOR_STARTUP_FAILURE_V1', redeployedServiceRoles: ['runtime', 'connector'] }
          state.plans[id] = response
          const reply = () => json(response)
          if (state.holdPlan) { state.pendingPlans.push(reply); return }
          return reply()
        }
        assert.equal(raw, JSON.stringify(state.plans[id].command), 'execute exactly the server-returned command bytes')
        const reply = lost => {
          if (lost) return route.abort('connectionreset')
          if (state.malformedReceipt) return json({ accepted: true })
          state.recovered.push(id)
          return json({ receiptId: `receipt-${id}`, reservationId: `reservation-${id}`, generation: `server-generation-${id}`, fromRevision: 3,
            revision: 4, releaseId: `failed-${id}`, status: 'APPLY_REQUESTED', retainedAllocationIds: ['retained-allocation'],
            financialMutationPerformed: false, authorizedAt: time })
        }
        if (state.holdRecovery) { state.pendingRecoveries.push(reply); return }
        return reply(false)
      }
      state.errors.push(`Unhandled ${method} ${path}`); return json({ message: 'Unmocked route; never forwarded' }, 500)
    } catch (error) { state.errors.push(error.stack); await route.abort().catch(() => {}) }
  })
  const plans = () => state.calls.filter(call => call.path.endsWith('/failed-apply-retry-plan'))
  const recoveries = () => state.calls.filter(call => call.path.endsWith('/retry-failed-apply'))
  try {
    await page.goto(`${base}prepared-recovery-fixture`, { waitUntil: 'networkidle' }); await preview(page).waitFor()
    assert.equal(plans().length, 0); assert.equal(recoveries().length, 0)
    await action({ page, state, plans, recoveries })
    assert.deepEqual(state.errors, []); assert.deepEqual(state.escaped, [])
    assert.equal(state.calls.some(call => call.method !== 'GET' && !call.path.endsWith('/failed-apply-retry-plan')
      && !call.path.endsWith('/retry-failed-apply') && call.path !== '/api/platform/preferences'), false, 'no recovery fallback, separate apply, funding, settlement or privileged write')
    results.push({ name, status: 'PASS', plans: plans().length, recoveries: recoveries().length }); console.log(`PASS ${name}`)
  } catch (error) {
    await page.screenshot({ path: `${output}/${name}.failure.png`, fullPage: true }).catch(() => {})
    await writeFile(`${output}/${name}.failure.txt`, `${error.stack}\n${await page.locator('body').innerText()}`, { flag: 'wx' }); throw error
  } finally {
    await save(`${name}.actual.json`, { ...state, pendingPlans: state.pendingPlans.length, pendingRecoveries: state.pendingRecoveries.length })
    await context.close(); contexts--
  }
}
try {
  server = await createServer({ root, configFile: false, envFile: false, cacheDir: `${output}/vite-cache`, esbuild: { jsx: 'automatic' },
    server: { host: '127.0.0.1', port: 41805, strictPort: true }, plugins: [{ name: 'failed-apply-retry-console-fixture', enforce: 'pre',
      resolveId(id, importer) {
        if (id === '/prepared-recovery-entry.jsx') return '\0prepared-recovery-entry.jsx'
        if (id.endsWith('/workspace/DeploymentWorkspaceContext') && importer?.endsWith('/CustomerDeploymentConsole.tsx')) return '\0prepared-recovery-workspace.js'
      }, load(id) {
        if (id === '\0prepared-recovery-workspace.js') return `import {useState} from 'react';export function useDeploymentWorkspace(){const [id,setId]=useState('dep-a');return {requestedDeploymentId:id,setSelectedDeploymentId:setId};}`
        if (id === '\0prepared-recovery-entry.jsx') return `import React,{useState} from 'react';import {createRoot} from 'react-dom/client';import {MemoryRouter} from 'react-router-dom';
          import {QueryClient,QueryClientProvider} from '@tanstack/react-query';import {CustomerDeploymentConsole} from '/src/components/customer/CustomerDeploymentConsole.tsx';
          const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});window.__invalidations=[];
          const invalidate=client.invalidateQueries.bind(client);client.invalidateQueries=(filters,...rest)=>{window.__invalidations.push(filters);return invalidate(filters,...rest)};
          function Harness(){const [actor,setActor]=useState('owner'),[mount,setMount]=useState(0);window.__actor=setActor;window.__actorValue=actor;window.__remount=()=>setMount(value=>value+1);
            return React.createElement(QueryClientProvider,{client},React.createElement(MemoryRouter,null,React.createElement(CustomerDeploymentConsole,{key:mount,
              session:{authenticated:true,actorId:actor,customerId:'customer',role:'CUSTOMER_ADMIN',authenticationMode:'SESSION'}})));}
          createRoot(document.getElementById('root')).render(React.createElement(Harness));`
      }, configureServer(vite) { vite.middlewares.use((req, res, next) => {
        if (req.url !== '/prepared-recovery-fixture') return next()
        res.setHeader('Content-Type', 'text/html'); res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:16px"><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/prepared-recovery-entry.jsx"></script></body></html>')
      }) },
    }] })
  await server.listen(); base = server.resolvedUrls.local[0]; assert.equal(new URL(base).hostname, '127.0.0.1')
  browser = await chromium.launch({ headless: true })
  await scenario('explicit-preview-confirm-exact-command-receipt-no-apply', async ({ page, plans, recoveries }) => {
    await prepare(page); assert.equal(plans().length, 1); assert.equal(recoveries().length, 0); assert.equal(await recover(page).isDisabled(), true)
    const panel = page.getByText('Retry failed owned services', { exact: true }).locator('..')
    await page.getByText('Redeployed roles: runtime, connector', { exact: true }).waitFor()
    await panel.scrollIntoViewIfNeeded(); await panel.screenshot({ path: `${output}/desktop.png`, animations: 'disabled' })
    await page.setViewportSize({ width: 390, height: 844 }); await panel.scrollIntoViewIfNeeded()
    await panel.screenshot({ path: `${output}/mobile.png`, animations: 'disabled' })
    assert.equal(await panel.evaluate(element => element.scrollWidth > element.clientWidth + 1), false)
    await page.getByRole('checkbox', { name: confirmation, exact: true }).check(); await recover(page).click()
    await page.getByText(/Owned-service redeploy authorized\. Receipt receipt-a\./).waitFor()
    assert.equal(recoveries().length, 1); assert.equal(plans().length, 1)
  })
  await scenario('ineligible-plan-shown-without-execution-or-retry', async ({ page, state, plans, recoveries }) => {
    state.denyPlan = true
    await page.getByRole('textbox', { name: 'Redeploy reason', exact: true }).fill('Explicit review')
    await page.getByRole('checkbox', { name: consent, exact: true }).check()
    await preview(page).click(); await page.getByText('Redeploy retry is not eligible: FAILED_APPLY_RETRY_LOCAL_STATE_CHANGED', { exact: true }).waitFor()
    assert.equal(plans().length, 1); assert.equal(recoveries().length, 0); assert.equal(await recover(page).count(), 0)
  })
  await scenario('unknown-loss-doubleclick-remount-actor-and-target-hold', async ({ page, state, plans, recoveries }) => {
    await prepare(page); await page.getByRole('checkbox', { name: confirmation, exact: true }).check()
    state.holdRecovery = true; await recover(page).dblclick({ delay: 30 })
    await until(() => state.pendingRecoveries.length === 1, 'one dispatched recovery')
    await state.pendingRecoveries.shift()(true)
    await page.getByText('A redeploy intent is recorded. Reconcile the original command before any further retry.', { exact: true }).waitFor()
    await page.evaluate(() => window.__remount()); await preview(page).waitFor(); assert.equal(await preview(page).isDisabled(), true)
    await page.evaluate(() => window.__actor('other-owner')); assert.equal(await preview(page).isDisabled(), true)
    await page.getByRole('button', { name: 'Manage deployment', exact: true }).nth(1).click()
    await page.getByRole('heading', { name: 'dep-b', level: 5, exact: true }).waitFor()
    await page.getByRole('button', { name: 'Manage deployment', exact: true }).nth(0).click()
    await preview(page).waitFor(); assert.equal(await preview(page).isDisabled(), true)
    await page.reload({ waitUntil: 'networkidle' }); await preview(page).waitFor(); assert.equal(await preview(page).isDisabled(), true)
    assert.equal(plans().length, 1); assert.equal(recoveries().length, 1)
  })
  await scenario('late-plan-after-actor-roundtrip-is-not-confirmable', async ({ page, state, recoveries }) => {
    state.holdPlan = true
    await page.getByRole('textbox', { name: 'Redeploy reason', exact: true }).fill('Explicit review')
    await page.getByRole('checkbox', { name: consent, exact: true }).check(); await preview(page).click()
    await until(() => state.pendingPlans.length === 1, 'held plan')
    await page.evaluate(() => window.__actor('other-owner'))
    await until(async () => await page.evaluate(() => window.__actorValue) === 'other-owner', 'changed actor rendered')
    await page.evaluate(() => window.__actor('owner'))
    await until(async () => await page.evaluate(() => window.__actorValue) === 'owner', 'original actor rendered with new review')
    await state.pendingPlans.shift()()
    assert.equal(await page.getByRole('checkbox', { name: confirmation, exact: true }).count(), 0); assert.equal(recoveries().length, 0)
  })
  await scenario('late-valid-receipt-invalidates-captured-deployment-not-new-selection', async ({ page, state, recoveries }) => {
    await prepare(page); await page.getByRole('checkbox', { name: confirmation, exact: true }).check()
    state.holdRecovery = true; await recover(page).click(); await until(() => state.pendingRecoveries.length === 1, 'held recovery')
    await page.getByRole('button', { name: 'Manage deployment', exact: true }).nth(1).click()
    await page.getByRole('heading', { name: 'dep-b', level: 5, exact: true }).waitFor(); await page.evaluate(() => { window.__invalidations = [] })
    await state.pendingRecoveries.shift()(false)
    await until(async () => (await page.evaluate(() => window.__invalidations)).some(item => item.queryKey?.[0] === 'customer-admission-reservations' && item.queryKey[2] === 'dep-a'), 'captured invalidation')
    assert.equal(await page.getByText(/Owned-service redeploy authorized/).count(), 0)
    assert.equal(recoveries().length, 1)
    assert.equal((await page.evaluate(() => window.__invalidations)).some(item => item.queryKey?.[0] === 'customer-admission-reservations' && item.queryKey[2] === 'dep-b'), false)
  })
  await scenario('http200-without-receipt-is-unknown-and-held', async ({ page, state, recoveries }) => {
    await prepare(page); state.malformedReceipt = true
    await page.getByRole('checkbox', { name: confirmation, exact: true }).check(); await recover(page).click()
    await page.getByText('A redeploy intent is recorded. Reconcile the original command before any further retry.', { exact: true }).waitFor()
    assert.equal(await page.getByText(/Owned-service redeploy authorized/).count(), 0); assert.equal(recoveries().length, 1)
    assert.equal(await preview(page).isDisabled(), true)
  })
  for (const pin of pins) assert.equal(hash(await readFile(`${root}/${pin.path}`)), pin.sha256, `Source drift: ${pin.path}`)
} catch (error) { failure = { message: error.message, stack: error.stack }; process.exitCode = 1; console.error(error.stack) }
finally {
  if (browser) { await browser.close(); cleanup.browserClosed = !browser.isConnected() }
  if (server) { await server.close(); cleanup.serverClosed = !server.httpServer?.listening }
  cleanup.contexts = contexts
  const proof = { phase: 'ACTUAL_CONSOLE_FAILED_APPLY_RETRY_LOOPBACK_SMOKE', status: failure ? 'FAIL' : 'PASS', startedAt,
    finishedAt: new Date().toISOString(), plannedScenarios: 6, results, failure, sourcePins: pins, cleanup,
    nonLoopbackAttempts: journals.flatMap(item => item.escaped), allApiRequestsIntercepted: true,
    limits: 'Actual Console/API, synthetic normal owner endpoint fixtures; no hosted/backend/provider/funding/P5/P6 acceptance.' }
  if (!cleanup.browserClosed || !cleanup.serverClosed || contexts !== 0) { proof.status = 'FAIL'; process.exitCode = 1 }
  await save('qualification.actual.json', proof); console.log(JSON.stringify({ status: proof.status, completedScenarios: results.length, cleanup, output }))
}
