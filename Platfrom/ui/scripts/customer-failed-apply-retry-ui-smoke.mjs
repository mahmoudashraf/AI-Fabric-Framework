import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const resumeMode = process.env.POST_PROVISION_RESUME_SMOKE === '1'
const scenarioFilter = process.env.FAILED_APPLY_RETRY_SMOKE_SCENARIO ?? ''
const workflow = resumeMode ? {
  plan: 'post-provision-resume-plan', execute: 'resume-post-provisioning', family: 'PostProvisionResume',
  preview: 'Preview setup continuation', action: 'Resume setup', title: 'Resume setup', reason: 'Setup continuation reason',
  consent: 'I accept the remaining dependency provisioning work.', confirmation: 'I confirm this setup plan and its remaining provisioning work.',
  boundary: 'COOLIFY_DEPENDENCY_ADMISSION_FAILED_AFTER_PROVISIONING_V1', error: 'POST_PROVISION_RESUME_LOCAL_STATE_CHANGED',
  ineligible: 'Setup continuation is not eligible', notice: 'Setup continuation authorized',
  held: 'A setup continuation intent is recorded. Reconcile the original command before any further resume.', acceptance: 'acceptRemainingDependencyProvisioning',
} : {
  plan: 'failed-apply-retry-plan', execute: 'retry-failed-apply', family: 'FailedApplyRetry',
  preview: 'Preview owned-service retry', action: 'Authorize owned-service redeploy', title: 'Retry failed owned services', reason: 'Redeploy reason',
  consent: 'I accept redeployment of both owned runtime and connector services.', confirmation: 'I confirm this plan authorizes real runtime and connector redeploys.',
  boundary: 'INITIAL_COOLIFY_CONNECTOR_STARTUP_FAILURE_V1', error: 'FAILED_APPLY_RETRY_LOCAL_STATE_CHANGED',
  ineligible: 'Redeploy retry is not eligible', notice: 'Owned-service redeploy authorized',
  held: 'A redeploy intent is recorded. Reconcile the original command before any further retry.', acceptance: 'acceptOwnedServiceRedeploy',
}
const output = process.env.FAILED_APPLY_RETRY_SMOKE_OUTPUT
  ?? `/private/tmp/01044-${resumeMode ? 'post-provision-resume' : 'failed-apply-retry'}-functional-ui-20261020/browser-r1`
assert.match(output, /^\/private\/tmp\/01044-(post-provision-resume|failed-apply-retry)-functional-ui-[a-zA-Z0-9-]+\/browser-r[0-9]+$/)
await mkdir(output)
const save = (name, value) => writeFile(`${output}/${name}`, JSON.stringify(value, null, 2) + '\n', { flag: 'wx' })
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const paths = ['src/api/platformApi.ts', 'src/components/customer/CustomerDeploymentConsole.tsx',
  'src/components/customer/durableCommandHolds.ts', 'src/hooks/useScopedOneTimeValue.ts',
  'src/components/customer/postProvisionResumeHolds.ts',
  'scripts/customer-failed-apply-retry-ui-smoke.mjs',
  `../backend/src/main/java/com/ai/fabric/platform/backend/deployment/admission/${workflow.family}Contracts.java`,
  `../backend/src/main/java/com/ai/fabric/platform/backend/deployment/admission/${workflow.family}Controller.java`]
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
const preview = page => page.getByRole('button', { name: workflow.preview, exact: true })
const recover = page => page.getByRole('button', { name: workflow.action, exact: true })
const consent = workflow.consent
const confirmation = workflow.confirmation
async function until(check, label) {
  const end = Date.now() + 10_000
  while (!(await check())) {
    if (Date.now() > end) throw new Error(`Timed out: ${label}`)
    await new Promise(resolve => setTimeout(resolve, 20))
  }
}
async function prepare(page) {
  await page.getByRole('textbox', { name: workflow.reason, exact: true }).fill('Explicit reviewed continuation or retry')
  assert.equal(await preview(page).isDisabled(), true)
  await page.getByRole('checkbox', { name: consent, exact: true }).check()
  await preview(page).click(); await recover(page).waitFor()
}
async function scenario(name, action) {
  if (scenarioFilter && !scenarioFilter.split(',').includes(name)) return
  const context = await browser.newContext({ viewport: { width: 1280, height: 1000 }, serviceWorkers: 'block' }); contexts++
  const page = await context.newPage(); page.setDefaultTimeout(10_000)
  const state = { calls: [], errors: [], escaped: [], pendingPlans: [], pendingRecoveries: [], plans: {}, denyPlan: false,
    holdPlan: false, holdRecovery: false, malformedReceipt: false, recovered: [], revisions: {}, releaseStatuses: {}, authorizationCounts: {} }
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
        : [{ ...release(records[1]), ...(state.recovered.includes(records[1]) ? { status: 'APPLY_REQUESTED', provisioningStatus: 'QUEUED', verificationStatus: 'PENDING' } : {}),
          ...(state.releaseStatuses[records[1]] ? { status: state.releaseStatuses[records[1]] } : {}) }])
      const admission = /^\/api\/customer-self-service\/catalogue\/offer\/deployments\/dep-([ab])\/admission-reservations$/.exec(path)
      if (admission) return json([{ ...reservation(admission[1]), revision: state.revisions[admission[1]] ?? 3,
        ...(state.recovered.includes(admission[1]) ? { state: 'RESERVED', revision: (state.revisions[admission[1]] ?? 3) + 1 } : {}) }])
      if (path.endsWith('/retirements')) return json([])
      const command = new RegExp(`^/api/customer-self-service/catalogue/offer/deployments/dep-([ab])/admission-reservations/reservation-([ab])/(${workflow.plan}|${workflow.execute})$`).exec(path)
      if (command) {
        assert.equal(command[1], command[2]); assert.equal(method, 'POST')
        const id = command[1]
        if (command[3] === workflow.plan) {
          assert.deepEqual(Object.keys(body).sort(), [workflow.acceptance, 'operationKey', 'reason', 'releaseId'].sort())
          assert.equal(body[workflow.acceptance], true)
          assert.equal(body.releaseId, `failed-${id}`)
          if (state.denyPlan) return json({ success: false, errorCode: workflow.error, message: workflow.error }, 409)
          const response = { command: { fence: { reservationId: `reservation-${id}`, revision: state.revisions[id] ?? 3, generation: `server-generation-${id}` },
            ...body, proofFingerprint: hash(`server-proof-${id}`) }, boundary: workflow.boundary,
            ...(resumeMode ? { remainingPhases: ['managed_dependencies', 'source_sync', 'post_apply_verification'], runtimeRedeployRequired: false,
              retainedAllocationIds: ['retained-allocation'] } : { redeployedServiceRoles: ['runtime', 'connector'] }) }
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
          state.authorizationCounts[id] = (state.authorizationCounts[id] ?? 0) + 1
          return json({ receiptId: `receipt-${id}${state.authorizationCounts[id] === 1 ? '' : `-${state.authorizationCounts[id]}`}`,
            reservationId: `reservation-${id}`, generation: `server-generation-${id}`, fromRevision: body.fence.revision,
            revision: body.fence.revision + 1, releaseId: `failed-${id}`, status: 'APPLY_REQUESTED', retainedAllocationIds: ['retained-allocation'],
            financialMutationPerformed: false, authorizedAt: time, ...(resumeMode ? { runtimeRedeployRequired: false } : {}) })
        }
        if (state.holdRecovery) { state.pendingRecoveries.push(reply); return }
        return reply(false)
      }
      state.errors.push(`Unhandled ${method} ${path}`); return json({ message: 'Unmocked route; never forwarded' }, 500)
    } catch (error) { state.errors.push(error.stack); await route.abort().catch(() => {}) }
  })
  const plans = () => state.calls.filter(call => call.path.endsWith(`/${workflow.plan}`))
  const recoveries = () => state.calls.filter(call => call.path.endsWith(`/${workflow.execute}`))
  try {
    await page.goto(`${base}prepared-recovery-fixture`, { waitUntil: 'networkidle' }); await preview(page).waitFor()
    assert.equal(plans().length, 0); assert.equal(recoveries().length, 0)
    await action({ page, state, plans, recoveries })
    assert.deepEqual(state.errors, []); assert.deepEqual(state.escaped, [])
    assert.equal(state.calls.some(call => call.method !== 'GET' && !call.path.endsWith(`/${workflow.plan}`)
      && !call.path.endsWith(`/${workflow.execute}`) && call.path !== '/api/platform/preferences'), false, 'no other recovery/resume/retry fallback, separate apply, funding, settlement or privileged write')
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
    server: { host: '127.0.0.1', port: resumeMode ? 41807 : 41805, strictPort: true }, plugins: [{ name: 'failed-apply-retry-console-fixture', enforce: 'pre',
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
    const panel = page.getByRole('heading', { name: workflow.title, level: 6, exact: true }).locator('..')
    await page.getByText(resumeMode ? 'Remaining phases: managed_dependencies, source_sync, post_apply_verification' : 'Redeployed roles: runtime, connector', { exact: true }).waitFor()
    await panel.scrollIntoViewIfNeeded(); await panel.screenshot({ path: `${output}/desktop.png`, animations: 'disabled' })
    await page.setViewportSize({ width: 390, height: 844 }); await panel.scrollIntoViewIfNeeded()
    await panel.screenshot({ path: `${output}/mobile.png`, animations: 'disabled' })
    assert.equal(await panel.evaluate(element => element.scrollWidth > element.clientWidth + 1), false)
    await page.getByRole('checkbox', { name: confirmation, exact: true }).check(); await recover(page).click()
    await page.getByText(new RegExp(`${workflow.notice}\\. Receipt receipt-a\\.`)).waitFor()
    assert.equal(recoveries().length, 1); assert.equal(plans().length, 1)
  })
  await scenario('ineligible-plan-shown-without-execution-or-retry', async ({ page, state, plans, recoveries }) => {
    state.denyPlan = true
    await page.getByRole('textbox', { name: workflow.reason, exact: true }).fill('Explicit review')
    await page.getByRole('checkbox', { name: consent, exact: true }).check()
    await preview(page).click(); await page.getByText(`${workflow.ineligible}: ${workflow.error}`, { exact: true }).waitFor()
    assert.equal(plans().length, 1); assert.equal(recoveries().length, 0); assert.equal(await recover(page).count(), 0)
  })
  await scenario('unknown-loss-doubleclick-remount-actor-and-target-hold', async ({ page, state, plans, recoveries }) => {
    await prepare(page); await page.getByRole('checkbox', { name: confirmation, exact: true }).check()
    state.holdRecovery = true; await recover(page).dblclick({ delay: 30 })
    await until(() => state.pendingRecoveries.length === 1, 'one dispatched recovery')
    await state.pendingRecoveries.shift()(true)
    await page.getByText(workflow.held, { exact: true }).waitFor()
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
    await page.getByRole('textbox', { name: workflow.reason, exact: true }).fill('Explicit review')
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
    assert.equal(await page.getByText(workflow.notice, { exact: false }).count(), 0)
    assert.equal(recoveries().length, 1)
    assert.equal((await page.evaluate(() => window.__invalidations)).some(item => item.queryKey?.[0] === 'customer-admission-reservations' && item.queryKey[2] === 'dep-b'), false)
  })
  await scenario('http200-without-receipt-is-unknown-and-held', async ({ page, state, recoveries }) => {
    await prepare(page); state.malformedReceipt = true
    await page.getByRole('checkbox', { name: confirmation, exact: true }).check(); await recover(page).click()
    await page.getByText(workflow.held, { exact: true }).waitFor()
    assert.equal(await page.getByText(workflow.notice, { exact: false }).count(), 0); assert.equal(recoveries().length, 1)
    assert.equal(await preview(page).isDisabled(), true)
  })
  if (resumeMode) await scenario('known-receipt-later-terminal-failure-new-explicit-intent-after-reload', async ({ page, state, plans, recoveries }) => {
    await prepare(page); await page.getByRole('checkbox', { name: confirmation, exact: true }).check(); await recover(page).click()
    await page.getByText(/Setup continuation authorized\. Receipt receipt-a\./).waitFor()
    assert.equal(recoveries().length, 1)
    state.recovered = []; state.revisions.a = 4
    await page.reload({ waitUntil: 'networkidle' }); await preview(page).waitFor()
    assert.equal(await page.getByRole('textbox', { name: workflow.reason, exact: true }).isDisabled(), true, 'ACK revision itself is not a fresh terminal-failure fence')
    state.revisions.a = 5; state.releaseStatuses.a = 'APPLY_REQUESTED'
    await page.reload({ waitUntil: 'networkidle' }); assert.equal(await preview(page).count(), 0, 'same release must be terminal FAILED')
    state.releaseStatuses.a = 'FAILED'
    await page.reload({ waitUntil: 'networkidle' }); await preview(page).waitFor()
    assert.equal(await page.getByRole('textbox', { name: workflow.reason, exact: true }).isEnabled(), true)
    assert.equal(plans().length, 1); assert.equal(recoveries().length, 1, 'new fence does not auto-dispatch')
    await prepare(page)
    assert.equal(plans().length, 2); assert.equal(recoveries().length, 1)
    assert.notEqual(plans()[0].body.operationKey, plans()[1].body.operationKey)
    assert.equal(state.plans.a.command.fence.revision, 5)
    await page.getByRole('checkbox', { name: confirmation, exact: true }).check(); await recover(page).click()
    await page.getByText(/Setup continuation authorized\. Receipt receipt-a-2\./).waitFor()
    assert.equal(recoveries().length, 2)
    const journals = await page.evaluate(() => Object.keys(localStorage).filter(key => key.startsWith('platform.post-resume-receipts.v1:'))
      .map(key => JSON.parse(localStorage.getItem(key))))
    assert.equal(journals.length, 1)
    assert.deepEqual(journals[0].events.map(event => event.kind), ['CLAIM', 'ACK', 'CLAIM', 'ACK'])
  })
  if (resumeMode) await scenario('unknown-response-later-failed-fence-remount-stays-held', async ({ page, state, plans, recoveries }) => {
    await prepare(page); await page.getByRole('checkbox', { name: confirmation, exact: true }).check()
    state.holdRecovery = true; await recover(page).click(); await until(() => state.pendingRecoveries.length === 1, 'one ambiguous resume')
    await state.pendingRecoveries.shift()(true)
    await page.getByText(workflow.held, { exact: true }).waitFor()
    state.revisions.a = 50
    await page.reload({ waitUntil: 'networkidle' }); await preview(page).waitFor()
    assert.equal(await preview(page).isDisabled(), true)
    await page.evaluate(() => window.__actor('other-owner'))
    await until(async () => await page.evaluate(() => window.__actorValue) === 'other-owner', 'new actor mounted')
    assert.equal(await preview(page).isDisabled(), true)
    assert.equal(plans().length, 1); assert.equal(recoveries().length, 1)
    const events = await page.evaluate(() => Object.keys(localStorage).filter(key => key.startsWith('platform.post-resume-receipts.v1:'))
      .flatMap(key => JSON.parse(localStorage.getItem(key)).events))
    assert.deepEqual(events.map(event => event.kind), ['CLAIM'])
  })
  for (const pin of pins) assert.equal(hash(await readFile(`${root}/${pin.path}`)), pin.sha256, `Source drift: ${pin.path}`)
} catch (error) { failure = { message: error.message, stack: error.stack }; process.exitCode = 1; console.error(error.stack) }
finally {
  if (browser) { await browser.close(); cleanup.browserClosed = !browser.isConnected() }
  if (server) { await server.close(); cleanup.serverClosed = !server.httpServer?.listening }
  cleanup.contexts = contexts
  const proof = { phase: resumeMode ? 'ACTUAL_CONSOLE_POST_PROVISION_RESUME_LOOPBACK_SMOKE' : 'ACTUAL_CONSOLE_FAILED_APPLY_RETRY_LOOPBACK_SMOKE', status: failure ? 'FAIL' : 'PASS', startedAt,
    finishedAt: new Date().toISOString(), plannedScenarios: scenarioFilter ? scenarioFilter.split(',').length : resumeMode ? 8 : 6, scenarioFilter, results, failure, sourcePins: pins, cleanup,
    nonLoopbackAttempts: journals.flatMap(item => item.escaped), allApiRequestsIntercepted: true,
    limits: 'Actual Console/API, synthetic normal owner endpoint fixtures; no hosted/backend/provider/funding/P5/P6 acceptance.' }
  if (!cleanup.browserClosed || !cleanup.serverClosed || contexts !== 0) { proof.status = 'FAIL'; process.exitCode = 1 }
  await save('qualification.actual.json', proof); console.log(JSON.stringify({ status: proof.status, completedScenarios: results.length, cleanup, output }))
}
