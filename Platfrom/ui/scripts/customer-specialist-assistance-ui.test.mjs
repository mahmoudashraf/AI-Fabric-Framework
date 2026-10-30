import test, { before, after } from 'node:test'
import assert from 'node:assert/strict'
import { mkdir, mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'

const root = fileURLToPath(new URL('..', import.meta.url))
const moduleId = '\0specialist-assistance-customer-fixture.jsx'
let server, browser, cache, base
const evidence = process.env.CUSTOMER_SUPPORT_UI_EVIDENCE_DIR
const stamp = '2026-10-30T12:00:00Z'

before(async () => {
  cache = await mkdtemp(join(tmpdir(), 'specialist-assistance-ui-'))
  if (evidence) await mkdir(evidence, { recursive: true })
  server = await createServer({ root, configFile: false, envFile: false, cacheDir: cache,
    esbuild: { jsx: 'automatic' }, logLevel: 'error', server: { host: '127.0.0.1', port: 0 },
    plugins: [{ name: 'specialist-assistance-fixture',
      resolveId(id) { if (id === '/specialist-assistance-fixture.jsx') return moduleId },
      async load(id) {
        if (id !== moduleId) return
        const source = `
          import React, { useState } from 'react';
          import { createRoot } from 'react-dom/client';
          import { createMemoryRouter, RouterProvider } from 'react-router-dom';
          import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
          import { Button } from '@mui/material';
          import { PlatformAuthProvider } from '/src/auth/PlatformAuthProvider.tsx';
          import { CustomerConsoleTheme } from '/src/theme/CustomerConsoleTheme.tsx';
          import { CustomerConfigurationPanel } from '/src/components/customer/CustomerConfigurationPanel.tsx';
          function Fixture() {
            const [deploymentId, setDeployment] = useState('dep-a');
            return <PlatformAuthProvider><CustomerConsoleTheme>
              <CustomerConfigurationPanel open title="Acme Support / Configuration" scopeKey={deploymentId}
                deploymentId={deploymentId} onClose={() => {}}>
                <p>Existing deployment configuration</p>
                <Button onClick={() => setDeployment(id => id === 'dep-a' ? 'dep-b' : 'dep-a')}>Switch fixture deployment</Button>
              </CustomerConfigurationPanel>
            </CustomerConsoleTheme></PlatformAuthProvider>;
          }
          const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
          const router = createMemoryRouter([{ path: '/', element: <Fixture /> }]);
          createRoot(document.getElementById('root')).render(<QueryClientProvider client={queryClient}><RouterProvider router={router}/></QueryClientProvider>);
        `
        return (await transformWithEsbuild(source, 'specialist-assistance-fixture.jsx', { loader: 'jsx', jsx: 'automatic' })).code
      },
      configureServer(vite) { vite.middlewares.use((request, response, next) => {
        if (new URL(request.url, 'http://localhost').pathname !== '/specialist-fixture') return next()
        response.setHeader('Content-Type', 'text/html')
        response.end('<!doctype html><html><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin};</script><script type="module" src="/specialist-assistance-fixture.jsx"></script></body></html>')
      }) },
    }],
  })
  await server.listen()
  base = server.resolvedUrls.local[0]
  browser = await chromium.launch({ headless: true })
})
after(async () => { await browser?.close(); await server?.close(); if (cache) await rm(cache, { recursive: true, force: true }) })

function fixtureState({ enabled = true, behaviorType = 'CONVERSATIONAL', canEdit = true } = {}) {
  const provenance = dep => ({ marketplaceManaged: true, marketplaceInstallId: `${dep}-install`, marketplacePluginId: 'approved-plugin', marketplacePluginVersion: '1.0.0' })
  const behavior = dep => ({ type: behaviorType, channelBindings: ['CHAT'], executionExtensions: enabled ? ['CONVERSATION_HANDOVER', 'SPECIALIST_ASSISTANCE'] : ['CONVERSATION_HANDOVER'],
    conversationHandover: { enabled: true, customerOwnedSetting: dep },
    ...(enabled ? { specialistAssistance: { enabled: true,
      areas: [{ id: 'setup', name: 'Product setup', purpose: 'Explain product setup.', instructions: 'Use approved product documentation.', vectorSpaces: ['guide'], readActions: [] },
        { id: 'account', name: 'Account help', purpose: 'Read customer-authorized account status.', instructions: 'Use approved read tools and state uncertainty.', vectorSpaces: [], readActions: ['get_status'] }],
      resolutions: [{ id: 'investigate', name: 'Setup investigation', objective: 'Coordinate useful setup and account checks.', areaIds: ['setup', 'account'], allowParallel: false }],
      limits: { maxDecisions: 4, maxWorkerInvocations: 2, maxParallelWorkers: 2, deadlineSeconds: 75 },
    } } : {}) })
  return { enabled, behaviorType, customerId: 'customer-a', canEdit, disableInstalls: false,
    drafts: Object.fromEntries(['dep-a', 'dep-b'].map(dep => [dep, { id: `${dep}-draft`, deploymentId: dep, revisionNumber: 1, status: 'MODIFIED',
      updatedAt: stamp, actionsConfig: { actions: [
        { name: 'get_status', accessMode: 'READ', displayName: 'Current account status', ...provenance(dep) },
        { name: 'delete_account', accessMode: 'WRITE', ...provenance(dep) }, { name: 'manual_read', accessMode: 'READ' },
      ] }, knowledgeSourceConfig: { sources: [{ id: 'guide-source', displayName: 'Approved product guide', entityType: 'guide', enabled: true, ...provenance(dep) }] },
      promptConfig: { keep: 'unrelated prompt configuration' }, behaviorConfig: behavior(dep) }])),
    calls: [], errors: [], holdWorkspace: null,
  }
}

async function fixture(t, options = {}) {
  const state = fixtureState(options), context = await browser.newContext({ viewport: options.viewport ?? { width: 1440, height: 1000 } })
  await context.addInitScript(theme => localStorage.setItem('loomai-customer-console-color-scheme', theme), options.theme ?? 'dark')
  const page = await context.newPage()
  page.setDefaultTimeout(20000)
  page.on('pageerror', error => state.errors.push(error.message))
  const json = (route, data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
  await page.route(`${base}api/**`, async route => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method()
    const body = request.postDataJSON()
    state.calls.push({ path, method, body })
    if (path === '/api/platform/auth/customer-config') return json(route, { enabled: false })
    if (path === '/api/platform/auth/session') return json(route, { enabled: true, authenticated: true,
      actorId: 'owner-a', customerId: state.customerId, role: 'CUSTOMER_ADMIN', authenticationMode: 'SESSION' })
    if (path === '/api/marketplace/plugins/approved-plugin/versions/1.0.0') return json(route, {
      id: 'plugin-version', pluginId: 'approved-plugin', version: '1.0.0', status: 'PUBLISHED',
      manifest: { contributions: { actions: [{ actionId: 'get_status', readOnly: true }, { actionId: 'delete_account', readOnly: false }],
        knowledgeSources: [{ id: 'guide-source', entityType: 'guide' }] } },
    })
    const match = path.match(/^\/api\/deployments\/(dep-[ab])\/(.*)$/)
    if (match) {
      const [, dep, resource] = match, draft = state.drafts[dep]
      if (resource === 'workspace') {
        if (state.holdWorkspace?.dep === dep) await state.holdWorkspace.promise
        return json(route, { deployment: { id: dep, behaviorType: state.behaviorType, binding: { customerId: 'customer-a' } }, access: { canEdit: state.canEdit } })
      }
      if (resource === 'draft') return json(route, draft)
      if (resource === 'marketplace-installs') return json(route, [{ id: `${dep}-install`, deploymentId: dep, pluginId: 'approved-plugin', pluginVersion: '1.0.0', pluginVersionId: 'plugin-version',
        pluginDisplayName: 'Approved account integration', status: state.disableInstalls ? 'DISABLED' : 'ENABLED', entitlement: { entitledForCompilation: true } }])
      if (resource === 'versions') return json(route, [{ id: 'published-one', deploymentId: dep, versionLabel: 'r1', status: 'PUBLISHED' }])
      if (resource === 'versions/published-one/artifacts/deployment-manifest.json') return json(route, { deploymentId: dep, versionId: 'published-one', behaviorConfig: { specialistAssistance: {
        enabled: true, contentHash: 'sha256:server-approved-hash', compiled: { contractVersion: 'LOOMAI_SPECIALIST_ASSISTANCE_V1',
          resources: [{ apiVersion: 'ai.fabric/v1', kind: 'Specialist', metadata: { name: 'server-only-resource', version: '9' } }],
          invocations: [{ id: 'setup', name: 'Published setup area', targetType: 'SPECIALIST', targetRef: 'server-only-resource@9' }] },
      } } })
    }
    const update = path.match(/^\/api\/deployment-drafts\/(dep-[ab])-draft$/)
    if (update && method === 'PUT') {
      const draft = state.drafts[update[1]]
      draft.behaviorConfig = structuredClone(body.behaviorConfig); draft.updatedAt = '2026-10-30T13:00:00Z'
      return json(route, draft)
    }
    state.errors.push(`Unexpected API ${method} ${path}`)
    return json(route, { message: 'Unexpected fixture request' }, 404)
  })
  t.after(async () => { await context.close(); assert.deepEqual(state.errors, []) })
  await page.goto(`${base}specialist-fixture`)
  await page.getByText('Existing deployment configuration', { exact: true }).waitFor().catch(async error => {
    throw new Error(`${error.message}\nBrowser errors: ${JSON.stringify(state.errors)}\nRequests: ${JSON.stringify(state.calls)}\nPage: ${await page.locator('body').innerText()}`)
  })
  return { page, state }
}

const until = async (predicate, label) => {
  const deadline = Date.now() + 10000
  while (!predicate()) { if (Date.now() > deadline) throw new Error(label); await new Promise(resolve => setTimeout(resolve, 30)) }
}
async function editArea(page, name, nextName) {
  await page.getByRole('button', { name: `Edit ${name}`, exact: true }).click()
  await page.getByRole('textbox', { name: 'Area name', exact: true }).fill(nextName)
  await page.getByRole('button', { name: 'Use changes', exact: true }).click()
  await page.getByText(nextName, { exact: true }).first().waitFor()
}

test('shared panel edits areas, plans and read-only compiled preview through the existing draft API', async t => {
  const { page, state } = await fixture(t)
  await page.getByRole('button', { name: 'Edit Account help', exact: true }).waitFor()
  await page.getByRole('button', { name: 'Edit Account help', exact: true }).click()
  await page.getByRole('textbox', { name: 'Area name', exact: true }).fill('Customer account help')
  await page.getByRole('combobox', { name: 'Installed READ tools' }).click()
  await page.getByRole('option', { name: /Current account status/ }).waitFor()
  assert.equal(await page.getByRole('option', { name: /delete_account|manual_read/ }).count(), 0)
  await page.keyboard.press('Escape')
  await page.getByRole('combobox', { name: 'Approved knowledge sources' }).click()
  await page.getByRole('option', { name: /Approved product guide/ }).click()
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: 'Use changes', exact: true }).click()
  await page.getByRole('button', { name: 'Edit Setup investigation', exact: true }).click()
  await page.getByRole('checkbox', { name: 'Allow independent parallel checks' }).check()
  await page.getByRole('textbox', { name: 'Objective', exact: true }).fill('Investigate setup and account information using approved checks.')
  await page.getByRole('button', { name: 'Use changes', exact: true }).click()
  await page.getByRole('tab', { name: 'Advanced', exact: true }).click()
  await page.getByText('SPECIALIST / server-only-resource@9', { exact: true }).waitFor()
  assert.equal(await page.getByRole('textbox', { name: /target|scope|URL/i }).count(), 0)
  await page.getByRole('spinbutton', { name: 'Deadline (seconds)' }).fill('90')
  state.drafts['dep-a'].promptConfig = { keep: 'changed elsewhere while editing' }
  await page.getByRole('button', { name: 'Save support capabilities', exact: true }).click()
  await page.getByText('Support capabilities saved to the draft.', { exact: false }).waitFor()
  const writes = state.calls.filter(call => call.method === 'PUT')
  assert.equal(writes.length, 1)
  assert.equal(writes[0].path, '/api/deployment-drafts/dep-a-draft')
  assert.deepEqual(Object.keys(writes[0].body), ['behaviorConfig'])
  const saved = writes[0].body.behaviorConfig
  assert.deepEqual(saved.executionExtensions, ['CONVERSATION_HANDOVER', 'SPECIALIST_ASSISTANCE'])
  assert.equal(saved.conversationHandover.enabled, true)
  assert.equal(saved.specialistAssistance.areas[1].name, 'Customer account help')
  assert.deepEqual(saved.specialistAssistance.areas[1].vectorSpaces, ['guide'])
  assert.equal(saved.specialistAssistance.resolutions[0].allowParallel, true)
  assert.equal(saved.specialistAssistance.limits.deadlineSeconds, 90)
  assert.equal('compiled' in saved.specialistAssistance, false)
  assert.deepEqual(state.drafts['dep-a'].promptConfig, { keep: 'changed elsewhere while editing' })
  assert.equal(state.calls.filter(call => call.method === 'POST').length, 0, 'The editor must not publish, apply or invoke AI')
})

test('a changed behavior draft is not overwritten', async t => {
  const { page, state } = await fixture(t)
  await editArea(page, 'Account help', 'Edited account help')
  state.drafts['dep-a'].behaviorConfig.conversationHandover.changedElsewhere = true
  await page.getByRole('button', { name: 'Save support capabilities', exact: true }).click()
  await page.getByText('The behavior draft changed.', { exact: false }).waitFor()
  assert.equal(state.calls.filter(call => call.method === 'PUT').length, 0)
})

test('revoked installed authority and changed customer identity stop before saving', async t => {
  const { page, state } = await fixture(t)
  await editArea(page, 'Account help', 'Edited account help')
  state.disableInstalls = true
  await page.getByRole('button', { name: 'Save support capabilities', exact: true }).click()
  await page.getByText('Some selections are no longer available.', { exact: false }).waitFor()
  assert.equal(state.calls.filter(call => call.method === 'PUT').length, 0)
  state.disableInstalls = false; state.customerId = 'other-customer'
  await page.getByRole('button', { name: 'Save support capabilities', exact: true }).click()
  await page.getByText('Customer access changed.', { exact: false }).waitFor()
  assert.equal(state.calls.filter(call => call.method === 'PUT').length, 0)
})

test('optional assistance remains disabled until explicitly configured, without AI calls', async t => {
  const { page, state } = await fixture(t, { enabled: false })
  await page.getByText('Optional specialist assistance is disabled.').waitFor()
  assert.equal(state.calls.some(call => call.path.endsWith('marketplace-installs')), false)
  await page.getByRole('checkbox', { name: 'Specialist assistance', exact: true }).check()
  await page.getByRole('button', { name: 'Add support area', exact: true }).click()
  await page.getByRole('textbox', { name: 'Area name', exact: true }).fill('Billing questions')
  await page.getByRole('textbox', { name: 'Purpose', exact: true }).fill('Explain approved billing information.')
  await page.getByRole('textbox', { name: 'Instructions', exact: true }).fill('Use the available evidence and make uncertainty visible.')
  await until(() => state.calls.some(call => call.path.includes('/api/marketplace/plugins/')), 'Installed choices should load')
  await page.getByRole('button', { name: 'Use changes', exact: true }).click()
  await page.getByLabel('Synthetic capability preview').getByText('Billing questions', { exact: true }).waitFor()
  await page.getByRole('button', { name: 'Save support capabilities', exact: true }).click()
  await page.getByText('Support capabilities saved to the draft.', { exact: false }).waitFor()
  assert.equal(state.calls.filter(call => call.method === 'PUT').length, 1)
  assert.equal(state.calls.filter(call => call.method === 'POST').length, 0)
})

test('scope change cancels a pending save before its write', async t => {
  const { page, state } = await fixture(t)
  await editArea(page, 'Account help', 'Edited account help')
  let release
  state.holdWorkspace = { dep: 'dep-a', promise: new Promise(resolve => { release = resolve }) }
  const before = state.calls.filter(call => call.path === '/api/deployments/dep-a/workspace').length
  await page.getByRole('button', { name: 'Save support capabilities', exact: true }).click()
  await until(() => state.calls.filter(call => call.path === '/api/deployments/dep-a/workspace').length > before, 'Fresh scope recheck should start')
  await page.getByRole('button', { name: 'Switch fixture deployment', exact: true }).click()
  release()
  await page.getByRole('button', { name: 'Edit Account help', exact: true }).waitFor()
  assert.equal(state.calls.filter(call => call.method === 'PUT').length, 0)
  assert.equal(await page.getByText('Support capabilities saved to the draft.', { exact: false }).count(), 0)
})

test('a read-only customer sees the configuration without mutation controls', async t => {
  const { page, state } = await fixture(t, { canEdit: false })
  await page.getByText('This deployment is read-only for your account.').waitFor()
  assert.equal(await page.getByRole('checkbox', { name: 'Specialist assistance', exact: true }).isDisabled(), true)
  assert.equal(await page.getByRole('button', { name: 'Edit Account help', exact: true }).isDisabled(), true)
  assert.equal(await page.getByRole('button', { name: 'Save support capabilities', exact: true }).isDisabled(), true)
  assert.equal(state.calls.some(call => call.method !== 'GET'), false)
})

test('disabled drafts can still inspect their actual immutable published resources', async t => {
  const { page, state } = await fixture(t, { enabled: false })
  await page.getByText('Optional specialist assistance is disabled.').waitFor()
  await page.getByRole('tab', { name: 'Advanced', exact: true }).click()
  await page.getByText('SPECIALIST / server-only-resource@9', { exact: true }).waitFor()
  assert.equal(state.calls.some(call => call.path.endsWith('marketplace-installs')), false)
  assert.equal(state.calls.some(call => call.method !== 'GET'), false)
})

test('narrow configuration and area drawer remain usable without horizontal overflow', async t => {
  const { page } = await fixture(t, { viewport: { width: 390, height: 844 } })
  await page.getByRole('button', { name: 'Edit Account help', exact: true }).waitFor()
  await page.screenshot({ path: join(evidence ?? cache, 'support-capabilities-narrow.png') })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true)
  await page.getByRole('button', { name: 'Edit Account help', exact: true }).click()
  await page.getByRole('textbox', { name: 'Instructions', exact: true }).waitFor()
  const box = await page.getByRole('textbox', { name: 'Area name', exact: true }).boundingBox()
  assert.ok(box && box.x >= 0 && box.x + box.width <= 390)
  await page.screenshot({ path: join(evidence ?? cache, 'support-area-narrow.png') })
  await page.getByRole('button', { name: 'Cancel', exact: true }).click()
})

for (const theme of ['light', 'dark']) test(`configuration and sliding editors fit the customer ${theme} theme`, async t => {
  const { page, state } = await fixture(t, { theme })
  await page.getByRole('button', { name: 'Edit Account help', exact: true }).waitFor()
  await page.screenshot({ path: join(evidence ?? cache, `support-capabilities-${theme}.png`) })
  await page.getByRole('button', { name: 'Edit Account help', exact: true }).click()
  await page.getByRole('textbox', { name: 'Instructions', exact: true }).waitFor()
  await page.screenshot({ path: join(evidence ?? cache, `support-area-${theme}.png`) })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true)
  assert.equal(state.calls.some(call => call.method !== 'GET'), false)
})
