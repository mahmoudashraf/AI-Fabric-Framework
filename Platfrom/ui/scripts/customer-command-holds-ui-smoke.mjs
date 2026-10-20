import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.CUSTOMER_COMMAND_HOLD_SMOKE_OUTPUT
  ?? '/private/tmp/01044-durable-customer-command-holds-ui-20261020/browser-r1'
assert.match(output, /^\/private\/tmp\/01044-durable-customer-command-holds-ui-[a-zA-Z0-9-]+\/browser-r[0-9]+$/)
await mkdir(output)
const save = (name, value) => writeFile(`${output}/${name}`, JSON.stringify(value, null, 2) + '\n', { flag: 'wx' })
const hash = value => createHash('sha256').update(value).digest('hex')
const paths = ['src/components/customer/KnownNoEffectSettlementPanel.tsx', 'src/components/customer/durableCommandHolds.ts',
  'src/components/customer/CustomerDeploymentConsole.tsx', 'src/api/platformApi.ts', 'scripts/customer-command-holds-ui-smoke.mjs']
const sourcePins = await Promise.all(paths.map(async path => ({ path, sha256: hash(await readFile(`${root}/${path}`)) })))
await save('source.before.json', sourcePins)
const startedAt = new Date().toISOString(), results = [], journals = []
let server, browser, base, failure = null, activeContexts = 0
const cleanup = { browserCreated: false, browserClosed: null, serverClosed: false, contexts: null }
const reservationId = '00000000-0000-4000-8000-000000000001'
const confirmation = 'I confirm these failed releases and consumer detachments.'
const previewButton = page => page.getByRole('button', { name: 'Preview settlement', exact: true })
const settleButton = page => page.getByRole('button', { name: 'Settle failed reservation', exact: true })
async function until(check, label) {
  const end = Date.now() + 10_000
  while (!(await check())) {
    if (Date.now() > end) throw new Error(`Timed out: ${label}`)
    await new Promise(resolve => setTimeout(resolve, 20))
  }
}
async function prepare(page, reason = 'Original explicit review') {
  await page.getByRole('textbox', { name: 'Settlement reason', exact: true }).fill(reason)
  await previewButton(page).click()
  await page.getByLabel(confirmation, { exact: true }).check()
}

async function fixture(name, { storageFailure = false } = {}) {
  const context = await browser.newContext({ viewport: { width: 1120, height: 900 }, serviceWorkers: 'block' })
  activeContexts++
  const state = { name, calls: [], errors: [], escaped: [], hold: false, pending: [], plan: null }
  journals.push(state)
  context.on('page', page => page.on('pageerror', error => state.errors.push(error.stack)))
  if (storageFailure) await context.addInitScript(() => {
    const set = Storage.prototype.setItem
    Storage.prototype.setItem = function(key, value) {
      if (key.startsWith('platform.command-hold.v1:')) throw new DOMException('Synthetic quota denial', 'QuotaExceededError')
      return set.call(this, key, value)
    }
  })
  await context.route('**/*', async route => {
    try {
      const url = new URL(route.request().url())
      if (url.origin !== new URL(base).origin) { state.escaped.push(url.href); return route.abort() }
      if (!url.pathname.startsWith('/api/')) return route.continue()
      const path = url.pathname, method = route.request().method(), raw = route.request().postData(), body = raw ? route.request().postDataJSON() : null
      state.calls.push({ path, method, body, raw })
      const json = value => route.fulfill({ contentType: 'application/json', body: JSON.stringify(value) })
      if (path.endsWith('/known-no-effect-plan') && method === 'POST') {
        state.plan = { proofFingerprint: hash('proof'), command: { fence: { reservationId, revision: 1, generation: 'server-generation' },
          ...body, versionId: 'version', versionConfigHash: hash('config'), manifestFingerprint: hash('manifest'), bindingFingerprint: hash('binding'),
          failedReleases: [{ releaseId: 'failed-release', authorizationOperationKey: 'original-auth', authorizationRequestFingerprint: hash('auth') }],
          consumerDetachments: [] } }
        return json(state.plan)
      }
      if (path.endsWith('/settle-known-no-effect') && method === 'POST') {
        assert.equal(raw, JSON.stringify(state.plan.command))
        const reply = lost => lost ? route.abort('connectionreset') : json({ id: 'server-receipt', reservationId,
          generation: 'server-generation', fromRevision: 1, revision: 2, deploymentId: 'deployment', targetProfileId: 'staging-a',
          outcome: 'SETTLED_KNOWN_NO_EFFECT', reclamationPerformed: false, settledAt: '2026-10-20T00:00:00Z',
          failedReleaseIds: ['failed-release'], consumerDetachments: [] })
        if (state.hold) { state.pending.push(reply); return }
        return reply(false)
      }
      state.errors.push(`Unhandled ${method} ${path}`)
      return route.fulfill({ status: 500, contentType: 'application/json', body: '{"message":"Unhandled mock API"}' })
    } catch (error) {
      state.errors.push(error.stack)
      await route.abort().catch(() => undefined)
    }
  })
  const page = await context.newPage()
  const f = { state, context, page, plans: () => state.calls.filter(call => call.path.endsWith('/known-no-effect-plan')),
    settlements: () => state.calls.filter(call => call.path.endsWith('/settle-known-no-effect')),
    async close() {
      await save(`${name}.actual.json`, { ...state, pending: state.pending.length })
      try { await context.close() } finally { activeContexts-- }
      assert.deepEqual(state.errors, []); assert.deepEqual(state.escaped, [])
    } }
  try {
    await page.goto(`${base}hold-fixture`, { waitUntil: 'networkidle' }); await previewButton(page).waitFor()
    assert.equal(f.plans().length, 0); assert.equal(f.settlements().length, 0)
    return f
  } catch (error) { await f.close(); throw error }
}
async function scenario(name, options, action) {
  const f = await fixture(name, options)
  try { await action(f) }
  catch (error) {
    await f.page.screenshot({ path: `${output}/${name}.failure.png`, fullPage: true }).catch(() => undefined)
    await writeFile(`${output}/${name}.failure.txt`, `${error.stack}\n\n${await f.page.locator('body').innerText()}`, { flag: 'wx' })
    throw error
  } finally { await f.close() }
  results.push({ name, status: 'PASS', planPosts: f.plans().length, settlementPosts: f.settlements().length })
  console.log(`PASS ${name}`)
}

try {
  server = await createServer({ root, configFile: false, envFile: false, cacheDir: `${output}/vite-cache`,
    esbuild: { jsx: 'automatic' }, server: { host: '127.0.0.1', port: 41795, strictPort: true },
    plugins: [{ name: 'durable-command-source-fixture', enforce: 'pre',
      resolveId(id) { if (id === '/hold-entry.jsx') return '\0hold-entry.jsx' },
      load(id) {
        if (id !== '\0hold-entry.jsx') return
        return `import React,{useState} from 'react';import {createRoot} from 'react-dom/client';
          import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
          import {KnownNoEffectSettlementPanel} from '/src/components/customer/KnownNoEffectSettlementPanel.tsx';
          const client=new QueryClient();window.__settled=[];window.__invalidations=[];
          const invalidate=client.invalidateQueries.bind(client);client.invalidateQueries=(filters,...rest)=>{
            window.__invalidations.push(filters);return invalidate(filters,...rest);};
          function Harness(){const [authority,setAuthority]=useState({actorId:'owner',customerId:'customer',authenticationMode:'SESSION'});
            const [mount,setMount]=useState(0),[target,setTarget]=useState('staging-a'),[recordTarget,setRecordTarget]=useState('staging-a');
            window.__authority=patch=>setAuthority(old=>({...old,...patch}));window.__remount=()=>setMount(old=>old+1);
            window.__target=setTarget;window.__recordTarget=setRecordTarget;
            const reservation={id:'${reservationId}',deploymentId:'deployment',targetProfileId:recordTarget,revision:1,state:'RESERVED',
              intent:'CUSTOMER_STAGING',operation:'APPLY',expiresAt:null,expiryState:'CURRENT',keepUntil:null};
            return React.createElement(KnownNoEffectSettlementPanel,{key:mount,authority,targetProfileId:target,
              offerId:'offer',deploymentId:'deployment',reservation,candidateFailed:true,onSettled:(receipt,scope)=>window.__settled.push({receipt,scope})});}
          createRoot(document.getElementById('root')).render(React.createElement(QueryClientProvider,{client},React.createElement(Harness)));`
      },
      configureServer(vite) { vite.middlewares.use((request, response, next) => {
        if (request.url !== '/hold-fixture') return next()
        response.setHeader('Content-Type', 'text/html')
        response.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1" /></head><body style="margin:24px"><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/hold-entry.jsx"></script></body></html>')
      }) },
    }],
  })
  await server.listen(); base = server.resolvedUrls.local[0]; assert.equal(new URL(base).hostname, '127.0.0.1')
  browser = await chromium.launch({ headless: true }); cleanup.browserCreated = true

  await scenario('lost-response-survives-remount-reload-and-authority', {}, async f => {
    await prepare(f.page); f.state.hold = true; await settleButton(f.page).dblclick({ delay: 40 })
    await until(() => f.state.pending.length === 1, 'one held settlement')
    await f.state.pending.shift()(true)
    await f.page.getByText('Settlement response is unconfirmed. Do not resubmit; reconcile the reservation.', { exact: true }).waitFor()
    await f.page.evaluate(() => window.__remount()); await until(async () => await previewButton(f.page).isDisabled(), 'remount hold')
    await f.page.reload({ waitUntil: 'networkidle' }); assert.equal(await previewButton(f.page).isDisabled(), true)
    await f.page.evaluate(() => window.__authority({ actorId: 'another-owner' }))
    assert.equal(await previewButton(f.page).isDisabled(), true)
    await f.page.evaluate(() => window.__authority({ authenticationMode: 'API_KEY' }))
    await until(async () => await previewButton(f.page).count() === 0, 'non-session authority hides panel')
    await f.page.evaluate(() => window.__authority({ authenticationMode: 'SESSION', customerId: 'other-customer' }))
    await previewButton(f.page).waitFor()
    await f.page.evaluate(() => window.__authority({ customerId: 'customer' }))
    await until(async () => await previewButton(f.page).isDisabled(), 'original customer hold restored')
    const secondPage = await f.context.newPage(); await secondPage.goto(`${base}hold-fixture`, { waitUntil: 'networkidle' })
    assert.equal(await previewButton(secondPage).isDisabled(), true)
    assert.equal(f.plans().length, 1); assert.equal(f.settlements().length, 1)
    const holds = await f.page.evaluate(() => Object.keys(localStorage).filter(key => key.startsWith('platform.command-hold.v1:')).map(key => localStorage.getItem(key)))
    assert.equal(holds.length, 1); assert.doesNotMatch(holds[0], /Original explicit review/)
    await f.page.screenshot({ path: `${output}/durable-hold-desktop.png`, animations: 'disabled' })
  })
  await scenario('authority-reset-and-active-target-filter', {}, async f => {
    await prepare(f.page)
    const firstKey = f.plans()[0].body.operationKey
    await f.page.evaluate(() => window.__authority({ actorId: 'new-owner' }))
    await previewButton(f.page).waitFor()
    assert.equal(await f.page.getByRole('textbox', { name: 'Settlement reason', exact: true }).inputValue(), '')
    assert.equal(await f.page.getByLabel(confirmation, { exact: true }).count(), 0)
    await prepare(f.page, 'New authority explicit review')
    assert.notEqual(f.plans()[1].body.operationKey, firstKey)
    await f.page.evaluate(() => window.__target('staging-b'))
    await until(async () => await previewButton(f.page).count() === 0 && await settleButton(f.page).count() === 0, 'mismatched active target hides old reservation')
    await f.page.evaluate(() => window.__recordTarget('staging-b'))
    await previewButton(f.page).waitFor(); assert.equal(await f.page.getByLabel(confirmation, { exact: true }).count(), 0)
    assert.equal(f.settlements().length, 0)
  })
  await scenario('storage-write-failure-sends-no-settlement', { storageFailure: true }, async f => {
    await prepare(f.page); await settleButton(f.page).click()
    await f.page.getByText('Submission protection is unavailable. No new request may be sent; reconcile the command.', { exact: true }).waitFor()
    assert.equal(f.settlements().length, 0)
    assert.deepEqual(await f.page.evaluate(() => window.__settled), [])
  })
  await scenario('late-receipt-keeps-original-authority-and-target', {}, async f => {
    await prepare(f.page); f.state.hold = true; await settleButton(f.page).click()
    await until(() => f.state.pending.length === 1, 'held valid response')
    await f.page.evaluate(() => { window.__authority({ actorId: 'other-owner', customerId: 'other-customer' }); window.__target('staging-b'); window.__recordTarget('staging-b') })
    await f.state.pending.shift()(false)
    await until(async () => (await f.page.evaluate(() => window.__settled)).length === 1, 'captured receipt callback')
    const actual = await f.page.evaluate(() => ({ callbacks: window.__settled, invalidations: window.__invalidations }))
    assert.deepEqual(actual.callbacks[0].scope, { authority: { actorId: 'owner', customerId: 'customer', authenticationMode: 'SESSION' },
      offerId: 'offer', deploymentId: 'deployment', reservationId, targetProfileId: 'staging-a', revision: 1 })
    assert.deepEqual(actual.invalidations, [{ queryKey: ['customer-admission-reservations', 'offer', 'deployment', 'staging-a'], exact: true }])
    assert.equal(await f.page.getByText(/Settlement receipt server-receipt/).count(), 0)
    assert.equal(f.settlements().length, 1)
  })
  for (const pin of sourcePins) assert.equal(hash(await readFile(`${root}/${pin.path}`)), pin.sha256, `Source changed: ${pin.path}`)
} catch (error) { failure = { message: error.message, stack: error.stack }; console.error(error.stack); process.exitCode = 1 }
finally {
  if (browser) { await browser.close(); cleanup.browserClosed = !browser.isConnected() }
  if (server) { await server.close(); cleanup.serverClosed = !server.httpServer?.listening }
  cleanup.contexts = activeContexts
  const proof = { phase: 'ACTUAL_DURABLE_HOLD_AND_AUTHORITY_UI_REGRESSIONS', startedAt, finishedAt: new Date().toISOString(),
    status: failure ? 'FAIL' : 'PASS', plannedScenarios: 4, completedScenarios: results.length, results, failure, sourcePins, cleanup,
    nonLoopbackAttempts: journals.flatMap(item => item.escaped), base, allApiRequestsIntercepted: true, noHostedActions: true,
    limits: 'New remount/auth/target/hold regressions only; original six tests and R3 are not treated as evidence of these fixes.' }
  if ((cleanup.browserCreated && !cleanup.browserClosed) || !cleanup.serverClosed || cleanup.contexts !== 0) {
    proof.status = 'FAIL'; proof.cleanupIncomplete = true; process.exitCode = 1
  }
  await save('qualification.actual.json', proof)
  console.log(JSON.stringify({ status: proof.status, completedScenarios: results.length, cleanup, proof: `${output}/qualification.actual.json` }))
}
