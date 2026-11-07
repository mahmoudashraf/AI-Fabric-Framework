import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'

const root = fileURLToPath(new URL('..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'ai-behaviour-vite-'))
const screenshots = await mkdtemp(join(tmpdir(), 'ai-behaviour-ui-'))
const keys = ['tone', 'answerBreadth', 'answerLength', 'nextSteps']
const promptKeys = ['systemPrompt', 'intentExtractionPrompt', 'actionSelectionPrompt', 'clarificationPrompt', 'answerGenerationPrompt', 'retrievalPrompt', 'assistantUiPrompt']
const catalogue = { schemaVersion: 'loomai-ai-behaviour-v1', revision: 1,
  defaults: { tone: 'professional', answerBreadth: 'grounded', answerLength: 'balanced', nextSteps: 'off' },
  groups: [
    ['tone', 'Conversation tone', ['professional', 'warm', 'consultative', 'technical']],
    ['answerBreadth', 'Answer breadth', ['grounded', 'advisory', 'exploratory']],
    ['answerLength', 'Answer length', ['brief', 'balanced', 'detailed']],
    ['nextSteps', 'Next steps', ['off', 'contextual']],
  ].map(([key, label, options]) => ({ key, label, options: options.map(id => ({ id,
    label: id[0].toUpperCase() + id.slice(1), description: `${label}: ${id} catalogue description.`, instructions: `${key}/${id} fixture instructions.` })) })) }
const original = { ...Object.fromEntries(promptKeys.map(key => [key, `Original ${key} domain instructions.\n`])),
  ragSimilarityThreshold: 0.2, ragMaxDocumentsUsedForContext: 12, ragMaxContextChars: 8000,
  responseGenerationMaxTokensConcise: 600, responseGenerationMaxTokensStandard: 1500, responseGenerationMaxTokensDeep: 2500,
  smartSuggestionsEnabled: true, futureSetting: { keep: ['all', 'values'], flag: false } }
function profiled(tone = 'professional') {
  return { ...structuredClone(original), aiBehaviour: { schemaVersion: catalogue.schemaVersion, revision: 1,
    ...Object.fromEntries(keys.map(key => {
      const option = catalogue.groups.find(group => group.key === key).options.find(option => option.id === (key === 'tone' ? tone : catalogue.defaults[key]))
      return [key, { presetId: option.id, revision: 1, instructions: option.instructions }]
    })) } }
}
// Controlled API fixture: tests exercise the actual shared page, not a second UI or live AI.
function compose(body) {
  const config = structuredClone(body.promptConfig)
  if (body.selections) {
    config.aiBehaviour ??= { schemaVersion: catalogue.schemaVersion, revision: 1 }
    for (const key of keys) {
      if (!body.selections[key] && config.aiBehaviour[key]) continue
      const option = catalogue.groups.find(group => group.key === key).options.find(option => option.id === (body.selections[key] ?? catalogue.defaults[key]))
      config.aiBehaviour[key] = { presetId: option.id, revision: 1, instructions: option.instructions }
    }
  }
  if (config.aiBehaviour) for (const key of keys) {
    const profile = config.aiBehaviour[key]
    const option = catalogue.groups.find(group => group.key === key).options.find(option => option.id === profile.presetId)
    if (option && profile.instructions !== option.instructions) profile.presetId = 'custom'
  }
  const effective = structuredClone(config)
  if (config.aiBehaviour) {
    for (const key of ['answerGenerationPrompt', 'retrievalPrompt', 'assistantUiPrompt', 'intentExtractionPrompt']) {
      effective[key] = `${config[key]}\nCOMPILED FIXTURE PROFILES\n${keys.map(group => config.aiBehaviour[group].instructions).join('\n')}`
    }
    effective.smartSuggestionsEnabled = false
  }
  return { promptConfig: config, effectivePromptConfig: effective, customInstructionsPreserved: true,
    changes: [{ key: 'answerGenerationPrompt', before: body.promptConfig.answerGenerationPrompt, after: effective.answerGenerationPrompt }] }
}
const server = await createServer({ root, cacheDir, configFile: false, envFile: false,
  server: { host: '127.0.0.1', port: 0 }, plugins: [{ name: 'ai-behaviour-page-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === '/__ai-behaviour-entry.jsx') return '\0ai-behaviour-entry.jsx'
      if (id === '/__ai-behaviour-workspace' || id.endsWith('/DeploymentWorkspaceContext')) return '\0ai-behaviour-workspace.js'
    },
    transform(code, id) { if (id === '\0ai-behaviour-entry.jsx') return transformWithEsbuild(code, 'fixture.jsx', { loader: 'jsx', jsx: 'automatic' }) },
    load(id) {
      if (id === '\0ai-behaviour-workspace.js') return `import { createContext, useContext } from 'react';
        export const Workspace = createContext(null); export const useDeploymentWorkspace = () => useContext(Workspace);`
      if (id === '\0ai-behaviour-entry.jsx') return `
        import React, { useState } from 'react'; import { createRoot } from 'react-dom/client';
        import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
        import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
        import { Workspace } from '/__ai-behaviour-workspace';
        import { setPlatformAccessTokenProvider } from '/src/api/platformApi.ts';
        import { PromptsPage } from '/src/pages/PromptsPage.tsx';
        const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } });
        setPlatformAccessTokenProvider(async () => 'fixture-session');
        const setEditorBufferState = value => { window.__editorBuffer = value; };
        function App() {
          const [deployment, setDeployment] = useState('dep-one'); const [editable, setEditable] = useState(!location.search.includes('readonly'));
          window.__deployment = setDeployment; window.__editable = setEditable;
          return <Workspace.Provider value={{ selectedDeploymentId: deployment,
            workspace: { deployment: { runtimeBaseUrl: 'https://fixture.invalid', activeVersion: 'v1' },
              access: { canEdit: editable, canOperate: editable }, draft: { revisionNumber: 1 } }, setEditorBufferState }}>
            <PromptsPage /></Workspace.Provider>;
        }
        createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}>
          <ThemeProvider theme={createTheme()}><CssBaseline /><main style={{ maxWidth: 1240, padding: 16, margin: 'auto' }}><App /></main></ThemeProvider>
        </QueryClientProvider>);
      `
    },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (new URL(req.url, 'http://fixture').pathname !== '/__ai-behaviour') return next()
      res.setHeader('Content-Type', 'text/html')
      res.end(await vite.transformIndexHtml('/__ai-behaviour', `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div>
        <script>window.__PLATFORM_RUNTIME_CONFIG__ = { apiBaseUrl: location.origin };</script><script type="module" src="/__ai-behaviour-entry.jsx"></script></body></html>`))
    }) },
  }] })
await server.listen()
const baseUrl = `http://127.0.0.1:${server.httpServer.address().port}`
const browser = await chromium.launch({ headless: true })
let passed = 0
async function run(name, test, options = {}) {
  const page = await browser.newPage({ viewport: options.mobile ? { width: 390, height: 844 } : { width: 1440, height: 1000 } })
  page.setDefaultTimeout(15000)
  const calls = [], errors = [], unexpected = [], revisions = [], snapshots = new Map(), releases = []
  let composeGate = null
  function holdCompose() {
    let started, release
    const gate = { started: new Promise(resolve => { started = resolve }), release: () => release() }
    composeGate = { started, wait: new Promise(resolve => { release = resolve }) }
    releases.push(gate.release)
    return gate
  }
  let saved = options.profiled ? profiled() : structuredClone(original), failCompose = options.failCompose, failCatalogue = options.failCatalogue
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', route => {
    if (new URL(route.request().url()).origin === baseUrl) return route.continue()
    unexpected.push(route.request().url()); return route.abort()
  })
  await page.route(`${baseUrl}/api/**`, async route => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method()
    const body = request.postData() ? JSON.parse(request.postData()) : null
    calls.push({ path, method, body })
    const send = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
    const draft = () => ({ id: 'draft-one', promptConfig: structuredClone(saved), providerConfig: { curatedModuleId: 'default', model: 'unchanged' } })
    if (path === '/api/deployment-ai-behaviour-profiles') {
      if (failCatalogue) { failCatalogue = false; return send({ message: 'Catalogue unavailable.' }, 503) }
      return send(catalogue)
    }
    if (path === '/api/deployment-curated-modules') return send([])
    if (path.endsWith('/draft')) return send(draft())
    if (path.endsWith('/prompt-baseline')) return send({ versionId: null, promptConfig: original, populatedPromptCount: 7 })
    if (path.endsWith('/ai-behaviour/compose')) {
      const gate = composeGate; composeGate = null
      if (gate) { gate.started(); await gate.wait }
      if (failCompose) { failCompose = false; return send({ message: 'Composition unavailable.' }, 503) }
      return send(compose(body))
    }
    if (path === '/api/deployment-drafts/draft-one' && method === 'PUT') {
      assert.deepEqual(Object.keys(body), ['promptConfig']); saved = structuredClone(body.promptConfig); return send(draft())
    }
    if (path.endsWith('/poc-chat/query')) return send({ traceSummary: { resultType: 'INFORMATION', answer: 'Fixture answer.', documentCount: 1 } })
    if (path.endsWith('/poc/prompt-session')) return send({ active: method === 'PUT', promptKeys: body ? Object.keys(body.promptPreview) : [], promptKeyCount: body ? Object.keys(body.promptPreview).length : 0 })
    if (path.endsWith('/prompt-revisions')) {
      if (method === 'POST') {
        const revision = { id: `revision-${revisions.length + 1}`, revisionLabel: body.revisionLabel || 'Snapshot', revisionSummary: body.revisionSummary,
          populatedPromptCount: 7, createdByDisplayName: 'Fixture operator', createdAt: '2026-11-07T10:00:00Z' }
        revisions.push(revision); snapshots.set(revision.id, structuredClone(saved)); return send(revision)
      }
      return send(revisions)
    }
    if (path.endsWith('/restore')) { saved = structuredClone(snapshots.get(path.split('/').at(-2))); return send(draft()) }
    unexpected.push(`${method} ${path}`); return send({ message: 'Unexpected fixture request' }, 404)
  })
  async function select(label, option) {
    await page.getByRole('combobox', { name: label, exact: true }).click()
    await page.getByRole('option', { name: option, exact: true }).click()
  }
  async function reviewApply() {
    await page.getByRole('button', { name: 'Review changes', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: 'Review AI behaviour changes' })
    await dialog.waitFor()
    await dialog.getByRole('button', { name: 'Apply to editor', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
  }
  try {
    await page.goto(`${baseUrl}/__ai-behaviour${options.readonly ? '?readonly' : ''}`)
    await page.getByRole('heading', { name: 'AI behaviour', exact: true }).waitFor()
    await page.getByRole('button', { name: 'Reset to saved', exact: true }).waitFor()
    await test({ page, calls, select, reviewApply, holdCompose, saved: () => saved })
    assert.deepEqual(errors, [], 'No page runtime errors')
    assert.deepEqual(unexpected, [], 'No unexpected endpoints or external calls')
    assert.equal(calls.some(call => /publish|release|provider/.test(call.path)), false, 'No release, provider or live-apply operations')
    console.log(`PASS ${name}`); passed++
  } catch (error) {
    console.error({ name, errors, unexpected, calls, comboboxes: await page.getByRole('combobox').evaluateAll(elements => elements.map(element => ({ text: element.textContent, label: element.getAttribute('aria-labelledby') }))), text: (await page.locator('body').innerText()).slice(0, 16000) })
    throw error
  } finally { for (const release of releases) release(); await page.close() }
}

try {
  await run('legacy state, catalogue choices, review cancellation, apply, retained base config and explicit save/reload', async t => {
    await t.page.getByText('Current instructions / not yet configured', { exact: true }).waitFor()
    assert.equal(t.calls.filter(call => call.method !== 'GET').length, 0)
    assert.equal(await t.page.getByRole('textbox', { name: 'System Prompt', exact: true }).isVisible(), false)
    assert.equal(await t.page.getByRole('textbox', { name: 'Session label', exact: true }).isVisible(), false)
    assert.equal(await t.page.getByRole('textbox', { name: 'Revision label', exact: true }).isVisible(), false)
    await t.page.getByRole('heading', { name: 'Try a conversation', exact: true }).waitFor()
    await t.page.getByText(/Only the seven managed prompt strings are previewed/).waitFor()
    for (const label of ['Conversation tone', 'Answer breadth', 'Answer length', 'Next steps']) await t.page.getByRole('combobox', { name: label, exact: true }).waitFor()
    await t.select('Conversation tone', 'Warm')
    await t.page.getByText('Conversation tone: warm catalogue description.', { exact: true }).waitFor()
    await t.page.getByRole('button', { name: 'Review changes', exact: true }).click()
    let dialog = t.page.getByRole('dialog')
    await dialog.getByText('answerGenerationPrompt', { exact: true }).waitFor()
    assert.equal(t.calls.filter(call => call.method === 'PUT').length, 0)
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    assert.equal(t.saved().aiBehaviour, undefined)
    await t.reviewApply()
    assert.equal(t.calls.filter(call => call.method === 'PUT').length, 0)
    await t.page.getByRole('button', { name: 'Save prompt draft', exact: true }).click()
    await t.page.getByText('Prompt draft saved.', { exact: true }).waitFor()
    assert.equal(t.saved().aiBehaviour.tone.presetId, 'warm')
    for (const key of Object.keys(original)) assert.deepEqual(t.saved()[key], original[key])
    await t.page.reload()
    await t.page.getByRole('combobox', { name: 'Conversation tone', exact: true }).filter({ hasText: 'Warm' }).waitFor()
    await t.page.screenshot({ path: join(screenshots, 'desktop-presets.png'), fullPage: true, animations: 'disabled' })
  })

  await run('custom instruction identity comes from API, base edits stay separate, reset and explicit preset reapply work', async t => {
    await t.page.getByRole('button', { name: 'Advanced profile instructions', exact: true }).click()
    await t.page.getByRole('textbox', { name: 'Conversation tone instructions', exact: true }).fill('A precise custom instruction.\n')
    await t.reviewApply()
    await t.page.getByRole('combobox', { name: 'Conversation tone', exact: true }).filter({ hasText: 'Custom instructions' }).waitFor()
    const request = t.calls.filter(call => call.path.endsWith('/ai-behaviour/compose')).at(-1)
    assert.equal(request.body.promptConfig.aiBehaviour.tone.presetId, 'professional')
    assert.equal(request.body.promptConfig.aiBehaviour.tone.instructions, 'A precise custom instruction.\n')
    assert.equal(request.body.selections, undefined)
    await t.select('Conversation tone', 'Professional'); await t.reviewApply()
    await t.page.getByRole('combobox', { name: 'Conversation tone', exact: true }).filter({ hasText: 'Professional' }).waitFor()
    await t.page.getByRole('button', { name: 'Advanced prompts and settings', exact: true }).click()
    await t.select('Answer breadth', 'Advisory')
    await t.page.getByRole('textbox', { name: 'Answer Generation Prompt', exact: true }).fill('Custom domain instructions retained verbatim.\n')
    await t.page.getByRole('combobox', { name: 'Answer breadth', exact: true }).filter({ hasText: 'Advisory' }).waitFor()
    await t.reviewApply()
    assert.equal(await t.page.getByRole('textbox', { name: 'Answer Generation Prompt', exact: true }).inputValue(), 'Custom domain instructions retained verbatim.\n')
    await t.page.getByRole('button', { name: 'Reset to saved', exact: true }).click()
    await t.page.getByRole('combobox', { name: 'Answer breadth', exact: true }).filter({ hasText: 'Grounded' }).waitFor()
    assert.equal(await t.page.getByRole('textbox', { name: 'Answer Generation Prompt', exact: true }).inputValue(), original.answerGenerationPrompt)
    assert.equal(t.calls.filter(call => call.method === 'PUT').length, 0)
  }, { profiled: true })

  await run('all four groups and defaults are buffered; preview and hot apply use only server-compiled seven prompts', async t => {
    for (const [label, option] of [['Conversation tone', 'Technical'], ['Answer breadth', 'Exploratory'], ['Answer length', 'Detailed'], ['Next steps', 'Contextual']]) await t.select(label, option)
    await t.reviewApply()
    await t.page.getByRole('textbox', { name: 'Preview query', exact: true }).fill('Explain our options.')
    await t.page.getByRole('button', { name: 'Run preview overlay', exact: true }).click()
    await t.page.getByText('Answer: Fixture answer.', { exact: true }).waitFor()
    const preview = t.calls.find(call => call.path.endsWith('/poc-chat/query')).body.promptPreview
    assert.deepEqual(Object.keys(preview), promptKeys)
    assert.match(preview.answerGenerationPrompt, /COMPILED FIXTURE PROFILES/)
    assert.match(preview.answerGenerationPrompt, /answerBreadth\/exploratory/)
    const hotApply = t.page.getByRole('button', { name: 'Advanced POC session hot apply', exact: true })
    assert.equal(await hotApply.getAttribute('aria-expanded'), 'false')
    await hotApply.click()
    await t.page.getByRole('button', { name: 'Activate for POC session', exact: true }).click()
    await t.page.getByText(/Prompt hot apply is active/).waitFor()
    assert.deepEqual(t.calls.find(call => call.path.endsWith('/poc/prompt-session') && call.method === 'PUT').body.promptPreview, preview)
    await t.page.getByRole('button', { name: 'Choose defaults', exact: true }).click()
    await t.reviewApply()
    for (const [label, option] of [['Conversation tone', 'Professional'], ['Answer breadth', 'Grounded'], ['Answer length', 'Balanced'], ['Next steps', 'Off']])
      await t.page.getByRole('combobox', { name: label, exact: true }).filter({ hasText: option }).waitFor()
  }, { profiled: true })

  await run('save snapshots and revision restore retain profile metadata using existing revision routes', async t => {
    const revisions = t.page.getByRole('button', { name: 'Advanced prompt revisions', exact: true })
    assert.equal(await revisions.getAttribute('aria-expanded'), 'false')
    await revisions.click()
    await t.page.getByRole('textbox', { name: 'Revision label', exact: true }).fill('Original profiles')
    await t.page.getByRole('button', { name: 'Create prompt revision', exact: true }).click()
    await t.page.getByText('Prompt revision created from the current saved draft.', { exact: true }).waitFor()
    await t.select('Conversation tone', 'Consultative'); await t.reviewApply()
    await t.page.getByRole('button', { name: 'Save prompt draft', exact: true }).click()
    await t.page.getByText('Prompt draft saved.', { exact: true }).waitFor()
    assert.equal(t.saved().aiBehaviour.tone.presetId, 'consultative')
    await t.page.getByRole('button', { name: 'Restore to draft', exact: true }).click()
    await t.page.getByRole('combobox', { name: 'Conversation tone', exact: true }).filter({ hasText: 'Professional' }).waitFor()
    assert.equal(t.saved().aiBehaviour.tone.presetId, 'professional')
  }, { profiled: true })

  await run('catalogue and composition failures remain visible and can be explicitly retried', async t => {
    await t.page.getByText('Catalogue unavailable.', { exact: true }).waitFor()
    await t.page.getByRole('button', { name: 'Retry', exact: true }).click()
    await t.select('Conversation tone', 'Warm')
    await t.page.getByRole('button', { name: 'Review changes', exact: true }).click()
    await t.page.getByText('Composition unavailable.', { exact: true }).waitFor()
    assert.equal(await t.page.getByRole('dialog').count(), 0)
    assert.equal(t.calls.filter(call => call.method === 'PUT').length, 0)
    await t.reviewApply()
  }, { failCatalogue: true, failCompose: true })

  await run('read-only access keeps behaviour choices and profile instructions disabled', async t => {
    const tone = t.page.getByRole('combobox', { name: 'Conversation tone', exact: true })
    await tone.waitFor(); assert.equal(await tone.getAttribute('aria-disabled'), 'true')
    await t.page.getByRole('button', { name: 'Advanced profile instructions', exact: true }).click()
    assert.equal(await t.page.getByRole('textbox', { name: 'Conversation tone instructions', exact: true }).isDisabled(), true)
    assert.equal(await t.page.getByRole('button', { name: 'Choose defaults', exact: true }).isDisabled(), true)
  }, { readonly: true, profiled: true })

  await run('a late composition for the previous deployment cannot apply to the newly selected deployment', async t => {
    await t.select('Conversation tone', 'Warm')
    const gate = t.holdCompose()
    await t.page.getByRole('button', { name: 'Review changes', exact: true }).click()
    await gate.started
    await t.page.evaluate(() => window.__deployment('dep-two'))
    await t.page.getByRole('combobox', { name: 'Conversation tone', exact: true }).filter({ hasText: 'Current instructions' }).waitFor()
    const response = t.page.waitForResponse(reply => reply.url().includes('/dep-one/ai-behaviour/compose'))
    gate.release(); await response
    await t.page.evaluate(() => new Promise(requestAnimationFrame))
    assert.equal(await t.page.getByRole('dialog').count(), 0)
    assert.equal(t.calls.filter(call => call.method === 'PUT').length, 0)
    assert.equal(await t.page.getByRole('button', { name: 'Review changes', exact: true }).isDisabled(), true)
  })

  await run('390px mobile selectors, stacked diff, basic navigation and reset fit the viewport', async t => {
    await t.select('Conversation tone', 'Warm')
    await t.page.getByRole('button', { name: 'Review changes', exact: true }).click()
    const dialog = t.page.getByRole('dialog'); await dialog.waitFor()
    assert.equal(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth + 1), true)
    await t.page.screenshot({ path: join(screenshots, 'mobile-review.png'), animations: 'disabled' })
    await dialog.getByRole('button', { name: 'Apply to editor', exact: true }).click()
    await t.page.getByRole('button', { name: 'Reset to saved', exact: true }).click()
    const overflow = await t.page.evaluate(() => document.documentElement.scrollWidth - innerWidth)
    assert.ok(overflow <= 1, `Page overflow ${overflow}px`)
    for (const control of await t.page.getByRole('combobox').all()) {
      const bounds = await control.boundingBox()
      assert.ok(bounds.x >= 15 && bounds.x + bounds.width <= 375, 'Behaviour controls keep the mobile page gutters')
    }
    await t.page.getByRole('heading', { name: 'AI behaviour', exact: true }).scrollIntoViewIfNeeded()
    await t.page.screenshot({ path: join(screenshots, 'mobile-presets.png'), animations: 'disabled' })
  }, { mobile: true })
  console.log(JSON.stringify({ passed, screenshots, evidence: 'Actual shared PromptsPage with controlled API fixtures; no live AI or deployment writes.' }))
} finally { await browser.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
