import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer, transformWithEsbuild } from 'vite'
import { chromium } from 'playwright'
import ts from 'typescript'
import { actionCatalogueFixture } from './actionDefinitionFixtures.mjs'

const exact = {}
new Function('require', 'exports', ts.transpileModule(
  await readFile(new URL('../lib/losslessJson.ts', import.meta.url), 'utf8'),
  { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } },
).outputText)(createRequire(import.meta.url), exact)
const { cloneExactJson, exactNumber, parseExactJson, stringifyExactJson } = exact
const fixture = structuredClone(actionCatalogueFixture)
fixture.actions[0].params[0].defaultValue = exactNumber('9007199254740993')
fixture.actions[0].params[0].futureParamField.providerId = exactNumber('123456789012345678901234567890')
fixture.actions[0].params[2].defaultValue[0].providerId = exactNumber('9007199254740995')
fixture.actions[0].route.request.body.numericOwnerId = exactNumber('9007199254740997')
fixture.actions[1].futureSiblingField.providerId = exactNumber('9007199254740999')
fixture.futureRootField.preciseId = exactNumber('9007199254741001')

const root = fileURLToPath(new URL('../..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'action-definitions-vite-'))
const screenshots = await mkdtemp(join(tmpdir(), 'action-definitions-screenshots-'))
const entry = [
  "import React, { useState } from 'react'; import { createRoot } from 'react-dom/client';",
  "import { QueryClient, QueryClientProvider } from '@tanstack/react-query';",
  "import { CssBaseline, ThemeProvider, Button } from '@mui/material';",
  "import { createConsoleTheme } from '/src/theme/consoleTheme.ts';",
  "import { BrainEditorContext } from '/src/brain/BrainWorkspaceContext.tsx';",
  "import { ActionsPage } from '/src/pages/ActionsPage.tsx';",
  "import { ActionDefinitionEditor } from '/src/actions/ActionDefinitionEditor.tsx';",
  "const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false }, mutations: { retry: false } } });",
  "window.__queryClient = client;",
  "function App() {",
  "  const [editor, register] = useState(null); const [buffer, setBuffer] = useState('{\"actions\":[]}'); const [disabled, setDisabled] = useState(false);",
  "  window.__setBuffer = setBuffer; window.__disable = setDisabled; window.__buffer = buffer; window.__editor = editor;",
  "  return new URLSearchParams(location.search).has('isolated') ? <ActionDefinitionEditor editorValue={buffer} onChange={setBuffer} disabled={disabled} /> :",
  "    <BrainEditorContext.Provider value={register}>",
  "      <Button disabled={!editor?.canSave || editor?.busy} onClick={() => editor?.save?.()}>Save draft</Button>",
  "      <Button disabled={!editor?.dirty || editor?.busy} onClick={() => editor?.discard?.()}>Discard</Button>",
  "      <ActionsPage brainId=\"brain-a\" actionName=\"lookup_order\" />",
  "    </BrainEditorContext.Provider>;",
  "}",
  "createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}>",
  "  <ThemeProvider theme={createConsoleTheme(new URLSearchParams(location.search).get('theme') || 'light')}><CssBaseline />",
  "    <main style={{ maxWidth: 1000, margin: 'auto', padding: 16 }}><App /></main>",
  "  </ThemeProvider></QueryClientProvider>);",
].join('\n')

const server = await createServer({ root, cacheDir, configFile: false, envFile: false,
  server: { host: '127.0.0.1', port: 0, hmr: false },
  plugins: [{ name: 'action-definition-fixture',
    resolveId(id) { if (id === '/__actions-entry.jsx') return '\0actions-entry.jsx' },
    transform(code, id) { if (id === '\0actions-entry.jsx') return transformWithEsbuild(code, 'actions-entry.jsx', { loader: 'jsx', jsx: 'automatic' }) },
    load(id) { if (id === '\0actions-entry.jsx') return entry },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (new URL(req.url, 'http://fixture').pathname !== '/__actions') return next()
      res.setHeader('Content-Type', 'text/html')
      res.end(await vite.transformIndexHtml('/__actions', '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin,environment:"staging"}</script><script type="module" src="/__actions-entry.jsx"></script></body></html>'))
    }) },
  }],
})

let browser, page
try {
  await server.listen()
  const base = 'http://127.0.0.1:' + server.httpServer.address().port
  browser = await chromium.launch({ headless: true })
  page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
  page.setDefaultTimeout(15000)
  const errors = [], writes = [], unexpected = []
  page.on('pageerror', error => errors.push(error.message))
  let draft = { id: 'draft-a', revisionNumber: 1, status: 'DRAFT', actionsConfig: cloneExactJson(fixture),
    routingConfig: { actions: { lookup_order: cloneExactJson(fixture.actions[0].route) }, connector: {}, authz: {} } }
  const initialRouting = cloneExactJson(draft.routingConfig)
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url())
    if (url.origin !== base) { unexpected.push(request.url()); return route.abort() }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const send = body => route.fulfill({ status: 200, contentType: 'application/json', body: stringifyExactJson(body) })
    if (url.pathname === '/api/deployments/brain-a/workspace') return send({ access: { canEdit: true, canOperate: false }, deployment: { id: 'brain-a', name: 'Order Brain', runtimeBaseUrl: null } })
    if (url.pathname === '/api/deployments/brain-a/draft') return send(draft)
    if (url.pathname === '/api/deployment-drafts/draft-a' && request.method() === 'PUT') {
      const body = parseExactJson(request.postData())
      writes.push(body)
      draft = { ...draft, ...body, revisionNumber: draft.revisionNumber + 1 }
      return send(draft)
    }
    unexpected.push(request.method() + ' ' + url.pathname)
    return route.fulfill({ status: 404, body: '{}' })
  })
  const raw = () => page.locator('textarea[aria-label="Actions JSON configuration"]')
  const current = async () => parseExactJson(await raw().inputValue())

  if (!process.argv.includes('--isolated-only')) {
  await page.goto(base + '/__actions')
  await page.getByLabel('Display name', { exact: true }).waitFor()
  assert.equal(String((await current()).actions[0].params[0].defaultValue), '9007199254740993', 'Loading the existing API draft must preserve large defaults')
  await page.getByLabel('Display name', { exact: true }).fill('Current order status')
  assert.equal(await page.getByLabel('Name', { exact: false }).first().getAttribute('readonly'), '')
  await page.getByRole('button', { name: 'Access & confirmation', exact: true }).click()
  await page.getByLabel('Allow anonymous access', { exact: true }).check()
  await page.getByLabel('Require confirmation', { exact: true }).check()
  await page.getByLabel('Confirmation message', { exact: true }).fill('Look up this customer order?')
  await page.getByRole('button', { name: 'Input parameters (3)', exact: true }).click()
  await page.getByLabel('Parameter description', { exact: true }).nth(0).fill('Updated trusted order reference.')
  await page.getByLabel('Ask user for missing value', { exact: true }).nth(0).check()
  await page.getByRole('combobox', { name: 'Source', exact: false }).nth(0).click()
  await page.getByRole('option', { name: 'USER', exact: true }).click()
  let value = await current()
  assert.equal(value.actions[0].params[0].visibility, 'USER')
  assert.equal(value.actions[0].params[0].askUser, true)
  assert.deepEqual(value.actions[0].params[0].resolveFrom, fixture.actions[0].params[0].resolveFrom)
  assert.deepEqual(value.actions[0].params[2], fixture.actions[0].params[2])
  await page.getByRole('button', { name: 'Add parameter', exact: true }).click()
  await page.getByLabel('Parameter name', { exact: false }).nth(3).fill('customerNote')
  await page.getByRole('combobox', { name: 'Type', exact: false }).nth(3).click()
  await page.getByRole('option', { name: 'OBJECT', exact: true }).click()
  await page.getByRole('button', { name: 'Remove parameter 4', exact: true }).click()

  await page.getByRole('button', { name: 'Advanced action JSON', exact: true }).click()
  const dialog = page.getByRole('dialog')
  const json = dialog.getByLabel('Action definition JSON', { exact: true })
  const originalAction = parseExactJson(await json.inputValue())
  await json.fill('{')
  await dialog.getByRole('button', { name: 'Apply to buffer', exact: true }).click()
  await dialog.getByRole('alert').waitFor()
  await json.fill(stringifyExactJson({ ...originalAction, name: 'renamed_order' }))
  await dialog.getByRole('button', { name: 'Apply to buffer', exact: true }).click()
  await dialog.getByText('Action names cannot be changed here until route and other dependency renaming is supported.').waitFor()
  originalAction.params[2].items.properties.count = { type: 'INTEGER', min: 1, defaultValue: 2 }
  originalAction.futureActionField.extra = ['keep', 'this']
  await json.fill(stringifyExactJson(originalAction))
  await dialog.getByRole('button', { name: 'Apply to buffer', exact: true }).click()
  await page.getByLabel('Category', { exact: true }).fill('customer_service')
  value = await current()
  assert.deepEqual(value.actions[0].futureActionField.extra, ['keep', 'this'])
  assert.deepEqual(value.actions[0].route, fixture.actions[0].route)
  assert.deepEqual(value.actions[1], fixture.actions[1])
  assert.deepEqual(value.futureRootField, fixture.futureRootField)
  assert.equal(String(value.actions[0].params[0].defaultValue), '9007199254740993')
  assert.equal(String(value.actions[0].params[0].futureParamField.providerId), '123456789012345678901234567890')
  assert.equal(writes.length, 0, 'Form and JSON edits must not save independently')
  await page.getByRole('button', { name: 'Save draft', exact: true }).click()
  await page.waitForFunction(() => window.__editor?.dirty === false)
  assert.equal(writes.length, 1)
  assert.deepEqual(writes[0].actionsConfig, value)
  assert.equal(String(writes[0].actionsConfig.actions[0].params[0].defaultValue), '9007199254740993', 'Manual save must send an exact number, not a rounded ID or wrapper object')
  assert.ok(!stringifyExactJson(writes[0]).includes('"isLosslessNumber"'))
  assert.equal(Object.hasOwn(writes[0], 'routingConfig'), false, 'Untouched routing must not be rewritten')
  assert.deepEqual(draft.routingConfig, initialRouting)
  console.log('PASS form / Advanced JSON / existing workspace save preserve >2^53 numbers, rich parameters, other actions and routes')

  await page.getByRole('button', { name: 'HTTP route for lookup_order', exact: true }).click()
  assert.equal(await page.getByRole('textbox', { name: 'Relative path', exact: true }).inputValue(), fixture.actions[0].route.path)
  await page.getByRole('combobox').filter({ hasText: 'HTTP routes & connector' }).click()
  await page.getByRole('option', { name: 'Action definitions & access', exact: true }).click()
  assert.equal(writes.length, 1, 'The route shortcut must only navigate to the existing route form')

  await page.getByRole('button', { name: 'Remove selected action', exact: true }).click()
  await page.getByRole('dialog').getByText('This action has a connector route. Clear its route target in HTTP routes & connector and save the draft before removing it.').waitFor()
  assert.equal(await page.getByRole('dialog').getByRole('button', { name: 'Remove action', exact: true }).isDisabled(), true)
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click()
  await page.getByRole('button', { name: 'Add action', exact: true }).click()
  await page.getByLabel('New action name', { exact: false }).fill('LOOKUP_ORDER')
  assert.equal(await page.getByRole('dialog').getByRole('button', { name: 'Add action', exact: true }).isDisabled(), true)
  await page.getByLabel('New action name', { exact: false }).fill('lookup_customer')
  await page.getByRole('dialog').getByRole('button', { name: 'Add action', exact: true }).click()
  await page.getByLabel('Display name', { exact: true }).fill('Customer details')
  await page.getByRole('button', { name: 'Remove selected action', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Remove action', exact: true }).click()
  assert.equal((await current()).actions.length, 2)
  console.log('PASS add, duplicate-name feedback, removal confirmation and route dependency blocking')

  await page.getByRole('combobox').filter({ hasText: 'submit_callback' }).click()
  await page.getByRole('option', { name: 'Current order status', exact: true }).click()
  await page.getByRole('button', { name: 'Advanced catalogue JSON', exact: true }).click()
  const savedBuffer = await raw().inputValue()
  await raw().fill('{')
  await page.getByText('Repair the current buffer in Advanced catalogue JSON.', { exact: false }).waitFor()
  assert.equal(await page.getByRole('button', { name: 'Add action', exact: true }).count(), 0)
  await raw().fill(savedBuffer)
  await page.getByLabel('Display name', { exact: true }).fill('Discarded edit')
  await page.getByRole('button', { name: 'Discard', exact: true }).click()
  await page.waitForFunction(() => window.__editor?.dirty === false)
  assert.equal(await page.getByLabel('Display name', { exact: true }).inputValue(), 'Current order status')
  console.log('PASS invalid raw JSON is not replaced by saved fallback; discard restores the form')

  await page.getByRole('button', { name: 'Advanced catalogue JSON', exact: true }).click()
  await page.getByRole('button', { name: 'Access & confirmation', exact: true }).click()
  await page.getByRole('button', { name: 'Input parameters (3)', exact: true }).click()
  await page.waitForFunction(() => Array.from(document.querySelectorAll('.MuiCollapse-root')).every(element =>
    element.classList.contains('MuiCollapse-entered') || element.classList.contains('MuiCollapse-hidden')))
  await page.screenshot({ path: join(screenshots, 'desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: join(screenshots, 'mobile.png'), fullPage: true })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
  for (const label of ['Display name', 'Description', 'Category']) {
    const box = await page.getByLabel(label, { exact: true }).boundingBox()
    assert.ok(box.x >= 0 && box.x + box.width <= 391, label + ' must fit on mobile')
  }
  console.log('PASS desktop/mobile layout and input bounds')

  await page.getByLabel('Display name', { exact: true }).fill('Older manual typing')
  await page.evaluate(() => window.__queryClient.invalidateQueries({ queryKey: ['deployment-draft', 'brain-a'] }))
  assert.equal(await page.getByLabel('Display name', { exact: true }).inputValue(), 'Older manual typing',
    'An unchanged background read must not clear local typing')
  draft = { ...draft, updatedAt: '2026-11-10T12:00:00Z',
    actionsConfig: { ...draft.actionsConfig, actions: draft.actionsConfig.actions.map((action, index) =>
      index === 0 ? { ...action, displayName: 'Assistant-saved order status' } : action) },
    routingConfig: { ...draft.routingConfig, actions: { ...draft.routingConfig.actions,
      lookup_order: { ...draft.routingConfig.actions.lookup_order, path: '/orders/by-reference' } } } }
  const sameRevision = draft.revisionNumber
  await page.evaluate(() => window.__queryClient.invalidateQueries({ queryKey: ['deployment-draft', 'brain-a'] }))
  await page.waitForFunction(() => window.__editor?.dirty === false)
  assert.equal(await page.getByLabel('Display name', { exact: true }).inputValue(), 'Assistant-saved order status')
  assert.deepEqual(await current(), draft.actionsConfig)
  assert.equal(draft.revisionNumber, sameRevision)
  await page.getByRole('combobox').filter({ hasText: 'Action definitions & access' }).click()
  await page.getByRole('option', { name: 'HTTP routes & connector', exact: true }).click()
  assert.equal(await page.getByRole('textbox', { name: 'Relative path', exact: true }).inputValue(), '/orders/by-reference')
  assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).isDisabled(), true)
  assert.equal(writes.length, 1, 'Refreshing an assistant save must not trigger another save')
  console.log('PASS query-cache invalidation adopts same-revision saved assistant actions, routes and clean baseline')
  }

  await page.goto(base + '/__actions?isolated')
  await page.getByText('No action definitions in this draft.', { exact: true }).waitFor()
  await page.evaluate(value => window.__setBuffer(value), stringifyExactJson(fixture))
  const isolatedCurrent = async () => parseExactJson(await page.evaluate(() => window.__buffer))
  await page.getByRole('button', { name: 'Result presentation', exact: true }).click()
  await page.getByRole('combobox', { name: 'Result presentation hint', exact: false }).click()
  await page.getByRole('option', { name: 'DETAIL', exact: true }).click()
  await page.getByRole('button', { name: 'Input parameters (3)', exact: true }).click()
  await page.getByRole('button', { name: 'Defaults & validation', exact: true }).nth(0).click()
  const editField = async (label, parameterNumber, source) => {
    await page.getByRole('button', { name: `Edit ${label} for parameter ${parameterNumber}`, exact: true }).click()
    await page.getByRole('dialog').getByLabel(`${label} JSON`, { exact: true }).fill(source)
    await page.getByRole('dialog').getByRole('button', { name: 'Apply to buffer', exact: true }).click()
  }
  await editField('Default value', 1, '9007199254741019')
  await editField('Allowed values', 1, '[1]')
  await page.getByRole('dialog').getByText('allowedValues must be an array of strings.', { exact: true }).waitFor()
  await page.getByRole('dialog').getByLabel('Allowed values JSON', { exact: true }).fill('["OPEN","CLOSED"]')
  await page.getByRole('dialog').getByRole('button', { name: 'Apply to buffer', exact: true }).click()
  await page.getByLabel('Minimum (min)', { exact: true }).nth(0).fill('-9007199254740993')
  await page.getByLabel('Maximum (max)', { exact: true }).nth(0).fill('9223372036854775807')
  await page.getByLabel('Maximum (max)', { exact: true }).nth(0).fill('9223372036854775808')
  await page.getByText('Enter a signed 64-bit integer, or leave blank. Invalid input is not applied.', { exact: true }).waitFor()
  assert.equal(String((await isolatedCurrent()).actions[0].params[0].max), '9223372036854775807')
  await page.getByLabel('Maximum (max)', { exact: true }).nth(0).fill('9223372036854775807')
  await page.getByLabel('Sensitive value', { exact: true }).nth(0).uncheck()
  await page.getByRole('button', { name: 'Trusted input & nested schema', exact: true }).nth(0).click()
  const resolver = cloneExactJson(fixture.actions[0].params[0].resolveFrom)
  resolver.future = { id: exactNumber('9007199254741021') }
  await editField('Trusted resolver (resolveFrom)', 1, stringifyExactJson(resolver))
  await page.getByRole('button', { name: 'Trusted input & nested schema', exact: true }).nth(2).click()
  const items = cloneExactJson(fixture.actions[0].params[2].items)
  items.properties.id = { type: 'INTEGER', defaultValue: exactNumber('9007199254741023') }
  await editField('Array items schema', 3, stringifyExactJson(items))
  await page.getByRole('combobox', { name: 'Type', exact: false }).nth(2).click()
  await page.getByRole('option', { name: 'OBJECT', exact: true }).click()
  await editField('Object properties schema', 3, '{"stockId":{"type":"INTEGER","defaultValue":9007199254741025}}')
  await editField('Required properties', 3, '["stockId"]')
  let isolated = await isolatedCurrent()
  assert.equal(isolated.actions[0].resultPresentationHint, 'DETAIL')
  assert.equal(String(isolated.actions[0].params[0].defaultValue), '9007199254741019')
  assert.equal(String(isolated.actions[0].params[0].min), '-9007199254740993')
  assert.equal(isolated.actions[0].params[0].sensitive, false)
  assert.deepEqual(isolated.actions[0].params[0].allowedValues, ['OPEN', 'CLOSED'])
  assert.deepEqual(isolated.actions[0].params[0].resolveFrom, resolver)
  assert.deepEqual(isolated.actions[0].params[2].items, items)
  assert.equal(String(isolated.actions[0].params[2].properties.stockId.defaultValue), '9007199254741025')
  assert.deepEqual(isolated.actions[0].params[2].requiredProperties, ['stockId'])
  assert.deepEqual(isolated.actions[0].params[0].futureParamField, fixture.actions[0].params[0].futureParamField)
  assert.deepEqual(isolated.actions[0].route, fixture.actions[0].route)
  assert.deepEqual(isolated.actions[1], fixture.actions[1])
  assert.deepEqual(isolated.futureRootField, fixture.futureRootField)
  await page.getByRole('button', { name: 'Clear Allowed values for parameter 1', exact: true }).click()
  assert.equal(Object.hasOwn((await isolatedCurrent()).actions[0].params[0], 'allowedValues'), false)
  await page.getByRole('button', { name: 'Edit Default value for parameter 1', exact: true }).click()
  await page.evaluate(() => window.__setBuffer(window.__buffer.replace('"category": "orders"', '"category": "newer"')))
  assert.equal(await page.getByRole('dialog').getByRole('button', { name: 'Apply to buffer', exact: true }).isDisabled(), true)
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click()
  await page.waitForFunction(() => Array.from(document.querySelectorAll('.MuiCollapse-root')).every(element =>
    element.classList.contains('MuiCollapse-entered') || element.classList.contains('MuiCollapse-hidden')))
  await page.screenshot({ path: join(screenshots, 'advanced-fields-desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: join(screenshots, 'advanced-fields-mobile.png'), fullPage: true })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
  console.log('PASS result hints, focused defaults/enums/resolvers/nested schemas, exact Long bounds, clear/stale controls and mobile layout')

  await page.getByRole('button', { name: 'Advanced action JSON', exact: true }).click()
  await page.evaluate(() => window.__setBuffer(JSON.stringify({ actions: [{ name: 'newer_action', accessMode: 'READ' }] })))
  await page.getByRole('dialog').getByText('The current buffer changed. Close and reopen Advanced action JSON before editing.').waitFor()
  assert.equal(await page.getByRole('dialog').getByRole('button', { name: 'Apply to buffer', exact: true }).isDisabled(), true)
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click()
  await page.evaluate(() => window.__disable(true))
  assert.equal(await page.getByLabel('Display name', { exact: true }).isDisabled(), true)
  assert.equal(await page.getByRole('button', { name: 'Add action', exact: true }).isDisabled(), true)
  await page.evaluate(() => { window.__disable(false); window.__setBuffer('{"actions":[{"name":"bad_params","accessMode":"READ","params":{}}]}') })
  await page.getByRole('button', { name: 'Input parameters (0)', exact: true }).click()
  await page.getByText('The params field must be an array. Repair it in Advanced action JSON.', { exact: true }).waitFor()
  console.log('PASS empty, disabled, malformed parameter and stale Advanced JSON states')
  assert.deepEqual(errors, [])
  assert.deepEqual(unexpected, [])
  console.log('Screenshots: ' + screenshots)
} catch (error) {
  await page?.screenshot({ path: join(screenshots, 'failure.png'), fullPage: true })
  console.error('Failure screenshot: ' + screenshots)
  throw error
} finally {
  await browser?.close()
  await server.close()
  await rm(cacheDir, { recursive: true, force: true })
}
