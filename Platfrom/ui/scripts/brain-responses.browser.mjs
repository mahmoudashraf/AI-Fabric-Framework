import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'

const root = fileURLToPath(new URL('..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'brain-responses-vite-'))
const screenshots = await mkdtemp(join(tmpdir(), 'brain-responses-shots-'))
const keys = ['tone', 'answerBreadth', 'answerLength', 'nextSteps']
const promptKeys = ['systemPrompt', 'intentExtractionPrompt', 'actionSelectionPrompt', 'clarificationPrompt', 'answerGenerationPrompt', 'retrievalPrompt', 'assistantUiPrompt']
const catalogue = { schemaVersion: 'loomai-ai-behaviour-v1', revision: 1,
  defaults: { tone: 'professional', answerBreadth: 'grounded', answerLength: 'balanced', nextSteps: 'off' },
  groups: [
    ['tone', 'Conversation tone', ['professional', 'warm', 'consultative', 'technical']],
    ['answerBreadth', 'Answer breadth', ['grounded', 'advisory', 'exploratory']],
    ['answerLength', 'Answer length', ['brief', 'balanced', 'detailed']],
    ['nextSteps', 'Next steps', ['off', 'contextual']],
  ].map(([key, label, options]) => ({ key, label, options: options.map(id => ({ id, label: id[0].toUpperCase() + id.slice(1),
    description: `${label}: ${id} catalogue description.`, instructions: `${key}/${id} fixture instructions.` })) })) }
const original = { ...Object.fromEntries(promptKeys.map(key => [key, `  Exact ${key} instructions.\n`])),
  ragSimilarityThreshold: 0.2, ragMaxContextChars: 9000, futureSetting: { values: ['keep', false] },
  aiBehaviour: { schemaVersion: catalogue.schemaVersion, revision: 1, ...Object.fromEntries(keys.map(key => {
    const option = catalogue.groups.find(group => group.key === key).options.find(option => option.id === catalogue.defaults[key])
    return [key, { presetId: option.id, revision: 1, instructions: option.instructions }]
  })) } }
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
    if (option && option.instructions !== profile.instructions) profile.presetId = 'custom'
  }
  const effective = structuredClone(config)
  if (config.aiBehaviour) for (const key of ['answerGenerationPrompt', 'retrievalPrompt', 'assistantUiPrompt', 'intentExtractionPrompt']) {
    effective[key] = `${config[key] ?? ''}\nCOMPILED FIXTURE\n${keys.map(group => config.aiBehaviour[group].instructions).join('\n')}`
  }
  return { promptConfig: config, effectivePromptConfig: effective, customInstructionsPreserved: true,
    changes: [{ key: 'answerGenerationPrompt', before: body.promptConfig.answerGenerationPrompt, after: effective.answerGenerationPrompt }] }
}

// Isolated shell and API fixtures; the actual Responses, Instructions and Advanced components run unchanged.
const server = await createServer({ root, cacheDir, configFile: false, envFile: false,
  server: { host: '127.0.0.1', port: 0 }, plugins: [{ name: 'brain-responses-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === '/__brain-responses-entry.jsx') return '\0brain-responses-entry.jsx'
      if (id === '/__brain-responses-deployment' || id.endsWith('/DeploymentWorkspaceContext')) return '\0brain-responses-deployment.js'
    },
    transform(code, id) { if (id === '\0brain-responses-entry.jsx') return transformWithEsbuild(code, 'fixture.jsx', { loader: 'jsx', jsx: 'automatic' }) },
    load(id) {
      if (id === '\0brain-responses-deployment.js') return `import { createContext, useContext } from 'react';
        export const Workspace = createContext(null); export const useDeploymentWorkspace = () => useContext(Workspace);`
      if (id === '\0brain-responses-entry.jsx') return `
        import React, { useState } from 'react'; import { createRoot } from 'react-dom/client';
        import { BrowserRouter, useLocation, useNavigate } from 'react-router-dom';
        import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
        import { ThemeProvider, CssBaseline, Button } from '@mui/material';
        import { Workspace } from '/__brain-responses-deployment';
        import { BrainWorkspaceContext, BrainEditorContext } from '/src/brain/BrainWorkspaceContext.tsx';
        import { BrainResponsesPage } from '/src/brain/BrainResponsesPage.tsx';
        import { createConsoleTheme } from '/src/theme/consoleTheme.ts';
        import { setPlatformAccessTokenProvider } from '/src/api/platformApi.ts';
        setPlatformAccessTokenProvider(async () => 'fixture-session');
        const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } });
        const setEditorBufferState = () => {};
        function App() {
          const location = useLocation(), navigate = useNavigate();
          const [editor, setEditor] = useState(null);
          const brainId = location.pathname.split('/')[2], targetProfileId = new URLSearchParams(location.search).get('targetProfileId') || 'target-staging';
          window.__refetch = () => client.invalidateQueries({ queryKey: ['deployment-draft', brainId] });
          window.__navigate = navigate;
          const editable = !location.search.includes('readonly');
          const href = section => '/smart-brains/' + brainId + '/' + (section || '') + '?targetProfileId=' + targetProfileId;
          return <Workspace.Provider value={{ selectedDeploymentId: brainId, workspace: { deployment: { runtimeBaseUrl: 'https://fixture.invalid', activeVersion: 'v1' },
            access: { canEdit: editable, canOperate: editable }, draft: { revisionNumber: 1 } }, setEditorBufferState }}>
            <BrainWorkspaceContext.Provider value={{ brainId, targetProfileId, href, target: null, release: null, targetProfile: null }}>
            <BrainEditorContext.Provider value={setEditor}>
              <div style={{ height: 56, borderBottom: '1px solid gray', padding: 16 }}>LoomAI / Responses fixture</div>
              <style>{'@media(min-width:900px){.fixture-task{margin-left:210px}}'}</style>
              <div className="fixture-task" style={{ height: 64, padding: 16 }}>Editing shared draft / Staging</div>
              <main className="fixture-task" style={{ height: 'calc(100dvh - 184px)', minHeight: 0, padding: 24, overflow: 'hidden' }}><BrainResponsesPage /></main>
              <footer style={{ height: 64, display: 'flex', alignItems: 'center', gap: 12, padding: '8px 24px', borderTop: '1px solid gray' }}>
                <span role="status">{editor?.message}</span><span role="alert">{editor?.error}</span>
                {editor?.discard && <Button disabled={editor.busy || !editor.dirty} onClick={editor.discard}>Discard</Button>}
                {editor?.save && <Button variant="contained" disabled={editor.busy || !editor.canSave} onClick={() => Promise.resolve(editor.save()).catch(() => {})}>Save draft</Button>}
              </footer>
            </BrainEditorContext.Provider></BrainWorkspaceContext.Provider>
          </Workspace.Provider>;
        }
        createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}><BrowserRouter>
          <ThemeProvider theme={createConsoleTheme(location.search.includes('dark') ? 'dark' : 'light')}><CssBaseline /><App /></ThemeProvider>
        </BrowserRouter></QueryClientProvider>);
      `
    },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (!new URL(req.url, 'http://fixture').pathname.startsWith('/smart-brains/')) return next()
      res.setHeader('Content-Type', 'text/html')
      res.end(await vite.transformIndexHtml(req.url, `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div>
        <script>window.__PLATFORM_RUNTIME_CONFIG__ = { apiBaseUrl: location.origin };</script><script type="module" src="/__brain-responses-entry.jsx"></script></body></html>`))
    }) },
  }] })
await server.listen()
const baseUrl = `http://127.0.0.1:${server.httpServer.address().port}`
const browser = await chromium.launch({ headless: true })
let passed = 0
async function run(name, execute, options = {}) {
  const page = await browser.newPage({ viewport: options.viewport ?? { width: 1440, height: 900 } })
  page.setDefaultTimeout(15000)
  const calls = [], errors = [], unexpected = []
  let saved = structuredClone(options.config ?? original), failSave = options.failSave, failCompose = options.failCompose
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', route => {
    if (new URL(route.request().url()).origin === baseUrl) return route.continue()
    unexpected.push(route.request().url()); return route.abort()
  })
  const draft = () => ({ id: 'draft-one', deploymentId: 'dep-one', promptConfig: structuredClone(saved), providerConfig: { curatedModuleId: 'default' }, revisionNumber: 1 })
  await page.route(`${baseUrl}/api/**`, async route => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method()
    const body = request.postData() ? JSON.parse(request.postData()) : null
    calls.push({ path, method, body })
    const send = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
    if (path === '/api/deployment-ai-behaviour-profiles') return send(catalogue)
    if (path.endsWith('/draft')) return send(draft())
    if (path.endsWith('/ai-behaviour/compose')) {
      if (failCompose) { failCompose = false; return send({ message: 'Composition unavailable.' }, 503) }
      return send(compose(body))
    }
    if (path === '/api/deployment-drafts/draft-one' && method === 'PUT') {
      assert.deepEqual(Object.keys(body), ['promptConfig'])
      if (failSave) { failSave = false; return send({ message: 'Save failed. Retry.' }, 503) }
      saved = structuredClone(body.promptConfig); return send(draft())
    }
    if (path === '/api/deployment-curated-modules') return send([{ id: 'default', name: 'Default', promptPresetId: 'default', description: 'Fixture baseline' }])
    if (path.endsWith('/prompt-baseline')) return send({ versionId: 'v1', promptConfig: original, populatedPromptCount: 7 })
    if (path.endsWith('/prompt-revisions')) return send([])
    if (path.endsWith('/poc/prompt-session')) return send({ active: method === 'PUT', promptKeys: body ? Object.keys(body.promptPreview) : [], promptKeyCount: body ? Object.keys(body.promptPreview).length : 0 })
    if (path.endsWith('/poc-chat/query')) return send({ traceSummary: { resultType: 'INFORMATION', answer: 'Explicit fixture result', documentCount: 1 } })
    unexpected.push(`${method} ${path}`); return send({ message: 'Unexpected fixture request' }, 404)
  })
  const select = async (label, option) => {
    await page.getByRole('combobox', { name: label, exact: true }).click()
    await page.getByRole('option', { name: option, exact: true }).click()
  }
  try {
    await page.goto(`${baseUrl}/smart-brains/dep-one/responses${options.view ?? ''}?targetProfileId=target-staging${options.dark ? '&dark' : ''}${options.readonly ? '&readonly' : ''}`)
    await page.getByRole('heading', { name: 'How your AI answers' }).waitFor()
    if (!options.readonly) await page.getByRole('button', { name: 'Save draft', exact: true }).waitFor()
    await page.waitForFunction(() => !document.body.textContent.includes('Loading response settings...'))
    await execute({ page, calls, select, saved: () => saved, changeSaved: value => { saved = value } })
    assert.deepEqual(errors, [], `${name}: browser errors`)
    assert.deepEqual(unexpected, [], `${name}: unexpected requests`)
    console.log(`PASS ${++passed}: ${name}`)
  } finally { await page.close() }
}
const writes = calls => calls.filter(call => call.method === 'PUT')
try {
  await run('catalogue controls, explicit-only composition and lossless one-command save', async ({ page, calls, select, saved }) => {
    assert.equal(await page.getByRole('combobox').count(), 4)
    assert.equal(calls.filter(call => call.path.endsWith('/compose')).length, 0)
    await select('Tone', 'Consultative'); await select('Use of general advice', 'Helpful guidance')
    await select('Helpful next steps', 'When useful')
    assert.equal(calls.filter(call => call.path.endsWith('/compose')).length, 0)
    await page.getByText('Style example, not a live result.', { exact: false }).waitFor()
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; live configuration unchanged', { exact: true }).waitFor()
    assert.equal(writes(calls).length, 1)
    assert.equal(calls.filter(call => call.path.endsWith('/compose')).length, 1)
    for (const key of promptKeys) assert.equal(saved()[key], original[key])
    assert.deepEqual(saved().futureSetting, original.futureSetting)
    assert.deepEqual(saved().aiBehaviour.answerLength, original.aiBehaviour.answerLength)
    assert.equal(saved().aiBehaviour.tone.presetId, 'consultative')
    assert.match(await page.getByRole('link', { name: 'Try with your Brain' }).getAttribute('href'), /\/try\?targetProfileId=target-staging$/)
  })
  await run('failed save retains choices and supports retry', async ({ page, select, saved }) => {
    await select('Tone', 'Warm'); await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByRole('alert').filter({ hasText: 'Save failed. Retry.' }).waitFor()
    assert.equal(saved().aiBehaviour.tone.presetId, 'professional')
    assert.match(await page.getByRole('combobox', { name: 'Tone', exact: true }).textContent(), /Warm/)
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; live configuration unchanged', { exact: true }).waitFor()
    assert.equal(saved().aiBehaviour.tone.presetId, 'warm')
  }, { failSave: true })
  await run('composition failure never writes; discard restores saved choices', async ({ page, calls, select }) => {
    await select('Answer length', 'Detailed'); await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByRole('alert').filter({ hasText: 'Composition unavailable.' }).waitFor()
    assert.equal(writes(calls).length, 0)
    await page.getByRole('button', { name: 'Discard', exact: true }).click()
    assert.match(await page.getByRole('combobox', { name: 'Answer length', exact: true }).textContent(), /Balanced/)
    assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).isDisabled(), true)
  }, { failCompose: true })
  await run('newer saved prompts are not silently overwritten', async ({ page, calls, select, changeSaved }) => {
    await select('Tone', 'Warm'); changeSaved({ ...structuredClone(original), systemPrompt: 'Changed elsewhere' })
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByRole('button', { name: 'Reload saved draft' }).waitFor()
    assert.equal(writes(calls).length, 0)
    assert.match(await page.getByRole('combobox', { name: 'Tone', exact: true }).textContent(), /Warm/)
    await page.getByRole('button', { name: 'Reload saved draft' }).click()
    await page.waitForFunction(() => !document.body.textContent.includes('Your choices are retained.'))
    assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).isDisabled(), true)
  })
  await run('background refresh never discards local choices', async ({ page, calls, select, changeSaved }) => {
    await select('Tone', 'Warm')
    changeSaved({ ...structuredClone(original), systemPrompt: 'New remote base instructions' })
    await page.evaluate(() => window.__refetch())
    assert.match(await page.getByRole('combobox', { name: 'Tone', exact: true }).textContent(), /Warm/)
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByRole('button', { name: 'Reload saved draft' }).waitFor()
    assert.equal(writes(calls).length, 0)
  })
  await run('read-only profile controls cannot mutate', async ({ page, calls }) => {
    assert.equal(await page.locator('[role="combobox"][aria-disabled="true"]').count(), 4)
    assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).count(), 0)
    assert.equal(writes(calls).length, 0)
  }, { readonly: true })
  const custom = structuredClone(original); custom.aiBehaviour.tone = { presetId: 'custom', revision: 1, instructions: 'Exact custom tone.\n' }
  await run('a different profile choice retains custom instructions', async ({ page, select, saved }) => {
    assert.match(await page.getByRole('combobox', { name: 'Tone', exact: true }).textContent(), /Custom instructions/)
    await select('Answer length', 'Brief'); await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; live configuration unchanged', { exact: true }).waitFor()
    assert.deepEqual(saved().aiBehaviour.tone, custom.aiBehaviour.tone)
  }, { config: custom })
  await run('legacy instructions stay untouched until explicit defaults are saved', async ({ page, calls, saved }) => {
    assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).isDisabled(), true)
    assert.equal(calls.filter(call => call.path.endsWith('/compose')).length, 0)
    await page.getByRole('button', { name: 'Choose default profiles', exact: true }).click()
    assert.equal(writes(calls).length, 0)
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; live configuration unchanged', { exact: true }).waitFor()
    for (const key of promptKeys) assert.equal(saved()[key], original[key])
    for (const key of keys) assert.equal(saved().aiBehaviour[key].presetId, catalogue.defaults[key])
  }, { config: Object.fromEntries(Object.entries(original).filter(([key]) => key !== 'aiBehaviour')) })
  await run('Instructions edits base and profile text through the single footer', async ({ page, select, saved }) => {
    assert.equal(await page.getByRole('button', { name: 'Save prompt draft' }).count(), 0)
    await page.getByRole('textbox', { name: 'System Prompt', exact: true }).fill('Revised exact system\n')
    await select('Instruction set', 'Profile: Tone')
    await page.getByRole('textbox', { name: 'Profile instructions', exact: true }).fill('Exact custom tone\n')
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; live configuration unchanged', { exact: true }).waitFor()
    assert.equal(saved().systemPrompt, 'Revised exact system\n')
    assert.equal(saved().aiBehaviour.tone.presetId, 'custom')
    assert.equal(saved().aiBehaviour.tone.instructions, 'Exact custom tone\n')
    assert.deepEqual(saved().futureSetting, original.futureSetting)
  }, { view: '/instructions' })
  await run('focused Advanced settings save only edited settings and retain absent fields', async ({ page, select, saved }) => {
    await page.getByRole('spinbutton', { name: 'Max context characters' }).fill('10000')
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; live configuration unchanged', { exact: true }).waitFor()
    assert.equal(saved().ragMaxContextChars, 10000)
    assert.equal('responseGenerationMaxTokensStandard' in saved(), false)
    await select('Advanced view', 'Compiled release prompts')
    await page.getByRole('heading', { name: 'Release prompt preview' }).waitFor()
    await select('Advanced view', 'Curated baseline / reset')
    await page.getByRole('heading', { name: 'Curated module baseline' }).waitFor()
    await select('Advanced view', 'Prompt snapshots')
    await page.getByRole('heading', { name: 'Prompt revisions', exact: true }).waitFor()
    for (const key of promptKeys) assert.equal(saved()[key], original[key])
  }, { view: '/advanced' })
  await run('temporary overrides and explicit previews carry only seven compiled strings', async ({ page, calls, select }) => {
    await select('Advanced view', 'Temporary test-session override')
    await page.getByRole('button', { name: 'Activate for POC session' }).click()
    await page.getByText('Prompt hot apply is active', { exact: false }).waitFor()
    const session = calls.find(call => call.path.endsWith('/prompt-session') && call.method === 'PUT')
    assert.deepEqual(Object.keys(session.body.promptPreview), promptKeys)
    await select('Advanced view', 'Explicit prompt preview')
    await page.getByText('not a full-draft simulation or a sandbox.', { exact: false }).waitFor()
    assert.equal(calls.filter(call => call.path.endsWith('/poc-chat/query')).length, 0)
    await page.getByRole('textbox', { name: 'Preview query' }).fill('Compare these options')
    await page.getByRole('button', { name: 'Run preview overlay', exact: true }).click()
    await page.getByText('Explicit fixture result', { exact: false }).waitFor()
    const preview = calls.find(call => call.path.endsWith('/poc-chat/query'))
    assert.deepEqual(Object.keys(preview.body.promptPreview), promptKeys)
    assert.equal(writes(calls).filter(call => !call.path.endsWith('/prompt-session')).length, 0)
  }, { view: '/advanced' })
  for (const viewport of [{ width: 1440, height: 900 }, { width: 1366, height: 768 }, { width: 1280, height: 800 }, { width: 390, height: 844 }]) {
    for (const dark of [false, true]) await run(`${dark ? 'dark' : 'light'} layout ${viewport.width}x${viewport.height}`, async ({ page }) => {
      const bounds = await page.evaluate(() => ({ width: innerWidth, height: innerHeight, scrollWidth: document.documentElement.scrollWidth, scrollHeight: document.documentElement.scrollHeight }))
      assert.ok(bounds.scrollWidth <= bounds.width + 1)
      assert.ok(bounds.scrollHeight <= bounds.height + 1)
      const footer = await page.getByRole('button', { name: 'Save draft', exact: true }).boundingBox()
      assert.ok(footer.y + footer.height <= viewport.height)
      if (viewport.width >= 900) {
        const lastField = await page.getByRole('link', { name: 'View profile instructions' }).boundingBox()
        assert.ok(lastField.y + lastField.height < footer.y, 'All normal style controls fit above the footer')
      }
      await page.screenshot({ path: join(screenshots, `${dark ? 'dark' : 'light'}-${viewport.width}.png`) })
    }, { viewport, dark })
  }
  console.log(`${passed} browser scenarios passed. Screenshots: ${screenshots}`)
} finally {
  await browser.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true })
}
