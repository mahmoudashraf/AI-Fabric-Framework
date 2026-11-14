import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile, mkdtemp } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { tmpdir } from 'node:os'
import path from 'node:path'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const root = fileURLToPath(new URL('../', import.meta.url))
const providerPath = path.join(root, 'src/pages/ProvidersPage.tsx')
const settingsPath = path.join(root, 'src/brain/BrainSettingsPage.tsx')
const source = await readFile(providerPath, 'utf8')
function compile(source, resolve = require) {
  const output = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText
  const exports = {}
  new Function('exports', 'require', output)(exports, resolve)
  return exports
}
const terminology = compile(await readFile(path.join(root, 'src/deploymentTerminology.ts'), 'utf8'))
const helpers = compile(`${source}\nexport { readProviderForm, buildProviderConfigDraft, buildFocusedProviderConfigDraft, providerFormKeys, SEMANTIC_RECOVERY_FIELDS, syncVectorStrategy, syncProvisioningMode, syncVectorStoragePosture };`, name => {
  if (name === '../deploymentTerminology') return terminology
  if (name.startsWith('../')) return {}
  return require(name)
})

const providerConfig = {
  llmProvider: 'openai', embeddingProvider: 'openai', vectorStrategy: 'qdrant',
  vectorProvisioningMode: 'EXTERNAL_EXISTING', vectorStoragePosture: 'SHARED',
  openaiModel: 'saved-model', openaiEmbeddingModel: 'embedding-model',
  qdrantHost: 'https://vector.example.test', qdrantCollectionPrefix: 'keep-scope',
  pineconeNamespacePrefix: 'inactive-but-preserved', qdrantTimeout: 42,
  runtimeProfile: 'runtime-managed', connectorProfile: 'connector-hosted',
  customExtension: { nested: ['keep', { value: 17 }], enabled: true },
  orchestrationModeOverrides: { customMode: { keep: true }, executor: {
    customExecutor: 'keep', semanticRecovery: { futurePolicy: { keep: true }, missingTarget: 'LLM_REPAIR_THEN_CLARIFY' },
  } },
}

test('owned settings files parse and type-check against their real imported contracts', () => {
  const options = ts.parseJsonConfigFileContent(ts.readConfigFile(path.join(root, 'tsconfig.app.json'), ts.sys.readFile).config,
    ts.sys, root).options
  const program = ts.createProgram([providerPath, settingsPath], options)
  const owned = new Set([providerPath, settingsPath])
  const diagnostics = ts.getPreEmitDiagnostics(program).filter(item => item.file && owned.has(item.file.fileName))
  assert.deepEqual(diagnostics.map(item => ts.flattenDiagnosticMessageText(item.messageText, '\n')), [])
})

test('a focused no-op preserves the exact object, including unknown and inactive settings', () => {
  const before = structuredClone(providerConfig)
  const form = helpers.readProviderForm(providerConfig)
  assert.deepEqual(helpers.buildFocusedProviderConfigDraft(providerConfig, form, form), providerConfig)
  assert.deepEqual(providerConfig, before)
})

test('a model edit does not normalize storage, defaults, metadata, or recovery policies', () => {
  const saved = helpers.readProviderForm(providerConfig)
  const next = helpers.buildFocusedProviderConfigDraft(providerConfig, saved, { ...saved, openaiModel: 'new-model' })
  assert.deepEqual(next, { ...providerConfig, openaiModel: 'new-model' })
})

test('all form fields retain their round-trip mapper and explicit controls', () => {
  const saved = helpers.readProviderForm(providerConfig)
  for (const key of helpers.providerFormKeys) {
    assert.ok(key in saved, `${key} is read from the draft`)
    if (key.startsWith('semanticRecovery')) {
      assert.ok(helpers.SEMANTIC_RECOVERY_FIELDS.some(field => field.key === key), `${key} retains its policy control`)
    } else if (key === 'pineconeManagedIndexEnabled') {
      assert.ok(source.includes('nextConfig.pineconeManagedIndexEnabled'), 'Pinecone index management remains derived from provisioning mode')
    } else assert.ok(source.includes(`handleFieldChange('${key}',`), `${key} retains its editable control`)
    const candidate = { ...saved, [key]: typeof saved[key] === 'boolean' ? !saved[key] : `changed-${key}` }
    const next = helpers.buildFocusedProviderConfigDraft(providerConfig, saved, candidate)
    assert.deepEqual(next.customExtension, providerConfig.customExtension, `${key} preserves unknown metadata`)
  }
})

test('recovery editing preserves unknown nested policies and executor/mode configuration', () => {
  const saved = helpers.readProviderForm(providerConfig)
  const next = helpers.buildFocusedProviderConfigDraft(providerConfig, saved, {
    ...saved, semanticRecoveryUnknownAction: 'LLM_REPLAN_TO_INFORMATION',
  })
  const overrides = next.orchestrationModeOverrides
  assert.deepEqual(overrides.customMode, providerConfig.orchestrationModeOverrides.customMode)
  assert.equal(overrides.executor.customExecutor, 'keep')
  assert.deepEqual(overrides.executor.semanticRecovery.futurePolicy, { keep: true })
  assert.equal(overrides.executor.semanticRecovery.unknownAction, 'LLM_REPLAN_TO_INFORMATION')
})

test('focused changes merge onto the freshest provider config without undoing other saved fields', () => {
  const saved = helpers.readProviderForm(providerConfig)
  const current = structuredClone(providerConfig)
  current.qdrantHost = 'https://updated-remotely.example.test'
  current.orchestrationModeOverrides.executor.semanticRecovery.extractionFailure = 'LLM_REPAIR_THEN_CLARIFY'
  const next = helpers.buildFocusedProviderConfigDraft(current, saved, {
    ...saved, openaiModel: 'local-model', semanticRecoveryUnknownAction: 'LLM_REPLAN_TO_INFORMATION',
  })
  assert.equal(next.qdrantHost, current.qdrantHost)
  assert.equal(next.openaiModel, 'local-model')
  assert.equal(next.orchestrationModeOverrides.executor.semanticRecovery.extractionFailure, 'LLM_REPAIR_THEN_CLARIFY')
  assert.equal(next.orchestrationModeOverrides.executor.semanticRecovery.unknownAction, 'LLM_REPLAN_TO_INFORMATION')
})

test('storage changes retain existing provisioning and tenant-scope mapping', () => {
  const saved = helpers.readProviderForm(providerConfig)
  for (const strategy of ['lucene', 'memory', 'qdrant', 'pinecone', 'weaviate', 'milvus']) {
    const form = helpers.syncVectorStrategy(saved, strategy)
    const next = helpers.buildFocusedProviderConfigDraft(providerConfig, saved, form)
    const existing = helpers.buildProviderConfigDraft(providerConfig, form)
    assert.equal(next.vectorStrategy, existing.vectorStrategy)
    assert.equal(next.vectorProvisioningMode ?? saved.vectorProvisioningMode, existing.vectorProvisioningMode)
    assert.deepEqual(next.customExtension, providerConfig.customExtension)
    assert.equal(next.openaiModel, providerConfig.openaiModel)
  }
})

const externalMilvusConfig = {
  ...providerConfig, vectorStrategy: 'milvus', milvusExternalBindingRequired: true,
  milvusAuthenticationType: 'TOKEN', milvusRuntimeTokenSecretName: 'MANAGED_EXTERNAL_VECTOR_TOKEN_TEST',
  milvusHost: 'byo.example.test', milvusPort: '19530', milvusSecure: false,
  milvusDatabaseName: 'default', milvusCollectionPrefix: 'customer_tenant_',
}

test('Milvus BYO to managed serializes the managed auth and clears only external connection references', () => {
  const saved = helpers.readProviderForm(externalMilvusConfig)
  const form = { ...helpers.syncProvisioningMode(saved, 'PLATFORM_MANAGED'),
    zillizCloudProjectId: 'project-test', zillizCloudRegionId: 'aws-eu-central-1' }
  for (const next of [helpers.buildProviderConfigDraft(externalMilvusConfig, form),
    helpers.buildFocusedProviderConfigDraft(externalMilvusConfig, saved, form)]) {
    assert.equal(next.vectorProvisioningMode, 'PLATFORM_MANAGED')
    assert.equal(next.vectorStoragePosture, 'DEDICATED')
    assert.equal(next.milvusExternalBindingRequired, false)
    assert.equal(next.milvusAuthenticationType, 'USERNAME_PASSWORD')
    assert.equal(next.milvusPort, '443')
    assert.equal(next.milvusSecure, true)
    for (const key of ['milvusHost', 'milvusRuntimeTokenSecretName', 'milvusRuntimeUsernameSecretName',
      'milvusRuntimePasswordSecretName', 'milvusExternalConnection']) assert.equal(key in next, false, key)
    assert.equal(next.zillizCloudProjectId, 'project-test')
    assert.equal(next.milvusCollectionPrefix, externalMilvusConfig.milvusCollectionPrefix)
    assert.deepEqual(next.customExtension, externalMilvusConfig.customExtension)
  }
  assert.equal(externalMilvusConfig.milvusExternalBindingRequired, true, 'The source is not mutated')
})

test('unsaved managed/BYO toggles restore shared BYO transport, auth and references exactly', () => {
  for (const authentication of [
    { milvusAuthenticationType: 'TOKEN', milvusRuntimeTokenSecretName: 'MANAGED_EXTERNAL_VECTOR_TOKEN_TEST' },
    { milvusAuthenticationType: 'USERNAME_PASSWORD', milvusRuntimeUsernameSecretName: 'EXTERNAL_USERNAME', milvusRuntimePasswordSecretName: 'EXTERNAL_PASSWORD' },
  ]) {
    const base = { ...externalMilvusConfig, ...authentication }
    if (authentication.milvusAuthenticationType !== 'TOKEN') delete base.milvusRuntimeTokenSecretName
    const saved = helpers.readProviderForm(base)
    const managed = helpers.syncProvisioningMode(saved, 'PLATFORM_MANAGED')
    const restored = helpers.syncProvisioningMode(managed, 'EXTERNAL_EXISTING')
    assert.equal(restored.vectorStoragePosture, 'SHARED')
    assert.equal(restored.milvusPort, '19530')
    assert.equal(restored.milvusSecure, false)
    assert.equal(restored.milvusHost, base.milvusHost)
    assert.deepEqual(helpers.buildFocusedProviderConfigDraft(base, saved, restored), base)
    const edited = helpers.buildFocusedProviderConfigDraft(base, saved, { ...restored, milvusTimeout: '45' })
    assert.deepEqual(edited, { ...base, milvusTimeout: '45' })
  }
})

test('returning from saved managed mode requires an explicit external endpoint, not managed identity reuse', () => {
  const base = { ...externalMilvusConfig, vectorProvisioningMode: 'PLATFORM_MANAGED', vectorStoragePosture: 'DEDICATED',
    milvusExternalBindingRequired: false, milvusAuthenticationType: 'USERNAME_PASSWORD',
    milvusHost: 'owned-managed.example.test', milvusPort: '443', milvusSecure: true,
    milvusRuntimeUsernameSecretName: 'MANAGED_USERNAME', milvusRuntimePasswordSecretName: 'MANAGED_PASSWORD' }
  delete base.milvusRuntimeTokenSecretName
  const saved = helpers.readProviderForm(base)
  const byo = helpers.syncProvisioningMode(saved, 'EXTERNAL_EXISTING')
  assert.equal(byo.milvusHost, '')
  for (const form of [byo, { ...byo, milvusHost: 'explicit-byo.example.test' }]) {
    for (const next of [helpers.buildProviderConfigDraft(base, form), helpers.buildFocusedProviderConfigDraft(base, saved, form)]) {
      assert.equal(next.milvusHost, form.milvusHost || undefined)
      assert.equal(next.milvusPort, '19530')
      assert.equal(next.milvusSecure, false)
      assert.equal(next.milvusAuthenticationType, 'USERNAME_PASSWORD')
      assert.equal(next.milvusExternalBindingRequired, false)
      for (const key of ['milvusRuntimeTokenSecretName', 'milvusRuntimeUsernameSecretName', 'milvusRuntimePasswordSecretName']) {
        assert.equal(key in next, false, key)
      }
    }
  }
})

test('editing stale managed settings fixes fields that the normalized form delta would otherwise miss', () => {
  const base = { ...externalMilvusConfig, vectorProvisioningMode: 'PLATFORM_MANAGED',
    milvusRuntimeUsernameSecretName: 'OLD_USERNAME', milvusRuntimePasswordSecretName: 'OLD_PASSWORD',
    zillizCloudProjectId: 'old-project' }
  const saved = helpers.readProviderForm(base)
  const next = helpers.buildFocusedProviderConfigDraft(base, saved, { ...saved, zillizCloudProjectId: 'reviewed-project' })
  assert.equal(next.milvusExternalBindingRequired, false)
  assert.equal(next.milvusAuthenticationType, 'USERNAME_PASSWORD')
  assert.equal(next.vectorStoragePosture, 'DEDICATED')
  assert.equal(next.milvusPort, '443')
  assert.equal(next.milvusSecure, true)
  for (const key of ['milvusHost', 'milvusRuntimeTokenSecretName', 'milvusRuntimeUsernameSecretName', 'milvusRuntimePasswordSecretName']) {
    assert.equal(key in next, false, key)
  }
  assert.equal(next.zillizCloudProjectId, 'reviewed-project')
  assert.deepEqual(next.customExtension, base.customExtension)
  assert.deepEqual(helpers.buildFocusedProviderConfigDraft(base, saved, saved), base)
  assert.deepEqual(helpers.buildFocusedProviderConfigDraft(base, saved, { ...saved, openaiModel: 'changed' }), { ...base, openaiModel: 'changed' })
})

test('leaving Milvus cannot retain its required binding flag on another search provider', () => {
  const saved = helpers.readProviderForm(externalMilvusConfig)
  for (const strategy of ['lucene', 'memory', 'qdrant', 'pinecone', 'weaviate']) {
    const form = helpers.syncVectorStrategy(saved, strategy)
    const next = helpers.buildFocusedProviderConfigDraft(externalMilvusConfig, saved, form)
    assert.equal(next.vectorStrategy, strategy)
    assert.equal(next.milvusExternalBindingRequired, false)
    assert.deepEqual(next.customExtension, externalMilvusConfig.customExtension)
  }
})

test('mode cleanup does not hide unsupported literal credentials from backend validation', () => {
  const base = { ...externalMilvusConfig, milvusToken: 'invalid-literal-test-value' }
  const saved = helpers.readProviderForm(base)
  const next = helpers.buildFocusedProviderConfigDraft(base, saved, helpers.syncProvisioningMode(saved, 'PLATFORM_MANAGED'))
  assert.equal(next.milvusToken, base.milvusToken)
})

test('focused settings keep staff provider operations outside customer scope', async () => {
  const settings = await readFile(settingsPath, 'utf8')
  assert.match(source, /<ProvidersEditor[\s\S]*?configurationOnly scoped view=/)
  assert.match(source, /selectedDeploymentId && !configurationOnly/)
  assert.match(settings, /brainId=\{brainId\} configurationOnly view=/)
  assert.doesNotMatch(settings, /['"]\/(security|users|providers|access)['"]|DeploymentProviderOperationsPanel/)
})

const fixture = {
  providerConfig,
  draft: { id: 'draft-settings', deploymentId: 'brain/a & b', revisionNumber: 3, status: 'DRAFT',
    entityConfig: { entities: [{ entityType: 'documents' }] }, promptConfig: { untouched: true } },
}

test('focused settings browser workflows and responsive views', { timeout: 120_000 }, async t => {
  const { createServer } = await import('vite')
  const { chromium } = await import('playwright')
  const apiModule = `
    const initial = ${JSON.stringify(fixture)};
    const state = window.__settingsTest = { writes: [], probes: [], draft: { ...initial.draft, providerConfig: initial.providerConfig }, failSave: false, saveDelay: 0 };
    const readonly = new URLSearchParams(location.search).has('readonly');
    export const fetchDeploymentWorkspace = async id => {
      if (state.failWorkspace) throw new Error('Workspace refresh unavailable');
      return { deployment: { id, name: 'Scoped Brain', binding: { customerId: 'customer-a', tenantId: 'tenant-a', customerName: 'Customer A', tenantName: 'Tenant A' } }, access: { canEdit: !readonly }, template: {} };
    };
    export const fetchDeploymentDraft = async id => { if (new URLSearchParams(location.search).has('draftError')) throw new Error('Draft unavailable'); return structuredClone(state.draft) };
    export const updateDeploymentDraft = async (id, payload) => {
      state.writes.push({ id, payload: structuredClone(payload) });
      if (state.saveDelay) await new Promise(resolve => setTimeout(resolve, state.saveDelay));
      if (state.failSave) throw new Error('Fixture save rejected');
      state.draft = { ...state.draft, ...structuredClone(payload), revisionNumber: state.draft.revisionNumber + 1 };
      return structuredClone(state.draft);
    };
    export const fetchDeploymentSecretUsage = async () => ({ secrets: [] });
    export const validateDeploymentDraft = async () => ({ issues: [], publishReady: true, errorCount: 0, warningCount: 0 });
    export const fetchDeploymentProviderConnectivity = async id => { state.probes.push({ id }); return { managedVectorTargets: [], probes: [], effectiveSecretResolutions: [], summaryMessage: 'Fixture probe complete' } };
    export const probeDeploymentProviderConnectivity = async (id, payload) => { state.probes.push({ id, payload }); return { managedVectorTargets: [], probes: [], effectiveSecretResolutions: [], summaryMessage: 'Fixture probe complete' } };
  `
  const harness = `
    import React, { useState, useCallback } from 'react';
    import { createRoot } from 'react-dom/client';
    import { BrowserRouter, useLocation } from 'react-router-dom';
    import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
    import { ThemeProvider, CssBaseline, Box, Button, Stack, Typography } from '@mui/material';
    import { createConsoleTheme } from '/src/theme/consoleTheme.ts';
    import { BrainWorkspaceContext, BrainEditorContext } from '/src/brain/BrainWorkspaceContext.tsx';
    import { BrainSettingsPage, BrainModelsPage, BrainAdvancedPage } from '/src/brain/BrainSettingsPage.tsx';
    import { ProvidersPage } from '/src/pages/ProvidersPage.tsx';
    import { brainPath } from '/src/brain/brainNavigation.ts';
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity, gcTime: Infinity }, mutations: { retry: false } } });
    function App() {
      const location = useLocation();
      const [editor, setEditor] = useState(null);
      const register = useCallback(value => { setEditor(value); window.__editor = value }, []);
      window.__settingsTest.refetch = config => client.setQueryData(['deployment-draft', 'brain/a & b'], { ...window.__settingsTest.draft, providerConfig: config });
      window.__settingsTest.refreshWorkspace = () => client.invalidateQueries({ queryKey: ['deployment-workspace', 'brain/a & b'] });
      const value = { brainId: 'brain/a & b', targetProfileId: 'target/staging', href: section => brainPath('brain/a & b', section, 'target/staging') };
      return <ThemeProvider theme={createConsoleTheme(new URLSearchParams(location.search).get('mode') || 'light')}><CssBaseline />
        <BrainWorkspaceContext.Provider value={value}><BrainEditorContext.Provider value={register}>
          <Box sx={{ height: '100dvh', display: 'flex', flexDirection: 'column', p: { xs: 2, md: 3 }, minWidth: 0 }}>
            <Box sx={{ flex: 1, minHeight: 0, display: 'flex' }}>{location.pathname.includes('/settings') ? <BrainSettingsPage />
              : location.pathname.endsWith('/models') ? <BrainModelsPage />
              : location.pathname.endsWith('/knowledge/search') ? <Box sx={{flex:1,overflow:'auto'}}><Typography component="h1">Search settings</Typography><ProvidersPage brainId={value.brainId} configurationOnly view="storage" /></Box>
              : location.pathname.endsWith('/advanced/retained-tabs') ? <Box sx={{flex:1,overflow:'auto'}}><Typography component="h1">Retained technical editor</Typography><ProvidersPage brainId={value.brainId} configurationOnly view="technical-files" /></Box>
              : location.pathname.includes('/advanced') ? <BrainAdvancedPage />
              : <Typography component="h1">Action configuration</Typography>}</Box>
            <Stack component="footer" direction="row" gap={1} sx={{ pt: 2, flexShrink: 0, flexWrap: 'wrap' }}>
              <Typography data-testid="footer-state">{editor?.dirty ? 'Unsaved changes' : 'Saved'}</Typography>
              {editor?.error && <Typography role="alert">{editor.error}</Typography>}
              {editor?.message && <Typography>{editor.message}</Typography>}
              {editor?.discard && <Button disabled={editor.busy} onClick={() => editor.discard()}>Discard</Button>}
              {editor?.save && <Button disabled={editor.busy || !editor.canSave} onClick={() => editor.save()}>Save draft</Button>}
            </Stack>
          </Box>
        </BrainEditorContext.Provider></BrainWorkspaceContext.Provider>
      </ThemeProvider>;
    }
    createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}><BrowserRouter><App /></BrowserRouter></QueryClientProvider>);
  `
  const cacheDir = await mkdtemp(path.join(tmpdir(), 'brain-settings-vite-'))
  const server = await createServer({ root, cacheDir, configFile: false, logLevel: 'error',
    server: { host: '127.0.0.1', port: 0 }, esbuild: { jsx: 'automatic' },
    plugins: [{ name: 'brain-settings-fixtures', enforce: 'pre',
      resolveId(id, importer) {
        if (id === '/settings-harness.tsx') return '\0settings-harness.tsx'
        if (id.endsWith('/auth/PlatformAuthProvider')) return '\0settings-auth.js'
        if (id.endsWith('/api/setupChecksApi')) return '\0settings-checks.js'
        if (id.endsWith('/api/platformApi')) return '\0settings-api.js'
        if (id.endsWith('/components/DeploymentProviderOperationsPanel')) return '\0settings-operations.jsx'
        if (id.endsWith('/components/customer/CustomerExistingVectorBinding')) return '\0settings-vector.jsx'
        if (id.endsWith('/workspace/DeploymentWorkspaceContext')) return '\0settings-workspace.js'
        if (id.endsWith('/workspace/useDeploymentWorkspaceEditorState')) return '\0settings-legacy.js'
      },
      load(id) {
        if (id === '\0settings-auth.js') return `export const usePlatformAuth=()=>({session:{actorId:'fixture',customerId:'fixture-customer',role:'CUSTOMER_ADMIN'}})`
        if (id === '\0settings-checks.js') return `
          export const fetchDeploymentSetupChecks=async()=>({deploymentId:'brain/a & b',draftId:window.__settingsTest.draft.id,revisionNumber:window.__settingsTest.draft.revisionNumber,services:[],verifiedCount:0,failedCount:0,notTestedCount:0,checkedAt:null});
          export const runDeploymentSetupChecks=fetchDeploymentSetupChecks;
        `
        if (id === '\0settings-harness.tsx') return harness
        if (id === '\0settings-api.js') return apiModule
        if (id === '\0settings-operations.jsx') return 'export function DeploymentProviderOperationsPanel() { throw new Error("Staff-only operations rendered") }'
        if (id === '\0settings-vector.jsx') return 'export function CustomerExistingVectorBinding() { return null }'
        if (id === '\0settings-workspace.js') return 'export function useDeploymentWorkspace() { throw new Error("Scoped editor used a legacy Brain selector") }'
        if (id === '\0settings-legacy.js') return 'export function useDeploymentWorkspaceEditorState() { throw new Error("Scoped editor registered the legacy footer") }'
      },
      transform(code, id) {
        if (id === '\0settings-harness.tsx') return ts.transpileModule(code, { compilerOptions: {
          target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext, jsx: ts.JsxEmit.ReactJSX,
        } }).outputText
      },
      configureServer(server) {
        server.middlewares.use(async (req, res, next) => {
          if (!req.url?.startsWith('/smart-brains/')) return next()
          res.setHeader('Content-Type', 'text/html')
          res.end(await server.transformIndexHtml(req.url, '<html><body><div id="root"></div><script type="module" src="/settings-harness.tsx"></script></body></html>'))
        })
      },
    }],
  })
  let browser
  try {
    await server.listen()
    browser = await chromium.launch({ headless: true })
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    const origin = server.resolvedUrls.local[0].replace(/\/$/, '')
    const canonical = { models: 'models', storage: 'knowledge/search', 'technical-files': 'advanced/retained-tabs' }
    const url = (section = '', query = '') => `${origin}/smart-brains/brain%2Fa%20%26%20b/${canonical[section] ?? ('settings' + (section ? '/' + section : ''))}?targetProfileId=target%2Fstaging${query}`
    const waitField = label => page.getByLabel(label, { exact: true }).waitFor()
    const saved = () => page.waitForFunction(() => window.__editor && !window.__editor.dirty && !window.__editor.busy)
    const save = async () => { await page.getByRole('button', { name: 'Save draft', exact: true }).click(); await saved() }
    const choose = async (label, option) => {
      await page.getByLabel(label, { exact: true }).click()
      await page.getByRole('option', { name: option, exact: true }).click()
    }

    await t.test('legacy bookmarks redirect to canonical owners with exact Brain and environment', async () => {
      for (const [old, destination] of Object.entries({ '': 'advanced/runtime', models: 'models', storage: 'knowledge/search', access: 'capabilities/actions', delivery: 'capabilities/actions', 'technical-files': 'advanced/runtime' })) {
        await page.goto(`${origin}/smart-brains/brain%2Fa%20%26%20b/settings${old ? '/' + old : ''}?targetProfileId=target%2Fstaging`)
        await page.waitForURL(value => value.pathname.endsWith('/' + destination))
        assert.equal(new URL(page.url()).searchParams.get('targetProfileId'), 'target/staging')
        assert.ok(new URL(page.url()).pathname.startsWith('/smart-brains/brain%2Fa%20%26%20b/'))
        if (old === 'delivery') assert.equal(new URL(page.url()).searchParams.get('panel'), 'webhooks')
      }
    })

    await t.test('models save through the real draft API wrapper without rewriting other sections', async () => {
      await page.goto(url('models'))
      await waitField('OpenAI LLM model')
      assert.equal(await page.getByLabel('Vector strategy', { exact: true }).count(), 0)
      assert.equal(await page.getByLabel('Embedding provider', { exact: true }).count(), 0)
      assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).count(), 1)
      await page.getByLabel('OpenAI LLM model', { exact: true }).fill('edited-model')
      await page.waitForFunction(() => window.__editor?.dirty)
      await save()
      const writes = await page.evaluate(() => window.__settingsTest.writes)
      assert.deepEqual(writes, [{ id: 'draft-settings', payload: { providerConfig: { ...providerConfig, openaiModel: 'edited-model' } } }])
      assert.equal(await page.getByLabel('OpenAI LLM model', { exact: true }).inputValue(), 'edited-model')
    })

    await t.test('refetch cannot overwrite a dirty buffer; discard adopts the latest saved data', async () => {
      await page.getByLabel('OpenAI LLM model', { exact: true }).fill('unsaved-model')
      await page.evaluate(() => window.__settingsTest.refetch({ ...window.__settingsTest.draft.providerConfig, openaiModel: 'remote-model' }))
      assert.equal(await page.getByLabel('OpenAI LLM model', { exact: true }).inputValue(), 'unsaved-model')
      await page.getByRole('button', { name: 'Discard', exact: true }).click()
      await saved()
      assert.equal(await page.getByLabel('OpenAI LLM model', { exact: true }).inputValue(), 'remote-model')
    })

    await t.test('failed and pending saves retain edits and expose actual footer state', async () => {
      await page.evaluate(() => { window.__settingsTest.failSave = true; window.__settingsTest.saveDelay = 200 })
      await page.getByLabel('OpenAI LLM model', { exact: true }).fill('retry-model')
      await page.getByRole('button', { name: 'Save draft', exact: true }).click()
      await page.waitForFunction(() => window.__editor?.busy)
      assert.equal(await page.getByRole('button', { name: 'Discard', exact: true }).isDisabled(), true)
      await page.getByRole('alert').filter({ hasText: 'Fixture save rejected' }).waitFor()
      assert.equal(await page.getByLabel('OpenAI LLM model', { exact: true }).inputValue(), 'retry-model')
      assert.equal(await page.evaluate(() => window.__editor.dirty), true)
      await page.evaluate(() => { window.__settingsTest.failSave = false })
      await save()
    })

    await t.test('a workspace refresh failure does not unmount or forget a dirty editor', async () => {
      await page.goto(url('models'))
      await waitField('OpenAI LLM model')
      await page.getByLabel('OpenAI LLM model', { exact: true }).fill('keep-local-buffer')
      await page.evaluate(async () => { window.__settingsTest.failWorkspace = true; await window.__settingsTest.refreshWorkspace() })
      await page.getByRole('alert').filter({ hasText: 'Workspace refresh unavailable' }).waitFor()
      assert.equal(await page.getByLabel('OpenAI LLM model', { exact: true }).inputValue(), 'keep-local-buffer')
      assert.equal(await page.evaluate(() => window.__editor.dirty), true)
      await page.evaluate(async () => { window.__settingsTest.failWorkspace = false; await window.__settingsTest.refreshWorkspace() })
      await page.getByRole('button', { name: 'Discard', exact: true }).click()
      await saved()
    })

    await t.test('purpose overrides use their own bounded view and save exact units/values', async () => {
      await page.goto(url('models'))
      await waitField('OpenAI LLM model')
      assert.equal(await page.getByLabel('OpenAI base URL', { exact: true }).count(), 0)
      await page.getByRole('tab', { name: 'Purpose overrides', exact: true }).click()
      await waitField('Orchestration model override')
      assert.equal(await page.getByLabel('OpenAI LLM model', { exact: true }).count(), 0)
      await page.getByLabel('Orchestration model override', { exact: true }).fill('planner-model')
      await page.getByLabel('Generation timeout (s)', { exact: true }).fill('65')
      await page.getByRole('tab', { name: 'Models & fallback', exact: true }).click()
      await save()
      const config = await page.evaluate(() => window.__settingsTest.draft.providerConfig)
      assert.deepEqual(config, { ...providerConfig, orchestrationModel: 'planner-model', generationTimeout: '65' })
    })

    await t.test('storage edits are focused and persist embedding/vector changes', async () => {
      await page.goto(url('storage'))
      await waitField('Search engine')
      assert.equal(await page.getByLabel('Embedding provider', { exact: true }).count(), 0)
      await page.getByRole('button', { name: 'Embedding model', exact: true }).click()
      await waitField('Embedding provider')
      assert.equal(await page.getByLabel('LLM provider', { exact: true }).count(), 0)
      assert.equal(await page.getByLabel('Orchestration model override', { exact: true }).count(), 0)
      await page.getByLabel('OpenAI embedding model', { exact: true }).fill('new-embedding')
      await page.getByRole('button', { name: 'Service connection', exact: true }).click()
      await page.getByLabel('Qdrant host', { exact: true }).fill('https://new-vector.example.test')
      await page.getByRole('button', { name: 'Customer separation', exact: true }).click()
      await waitField('Data isolation')
      await page.getByRole('button', { name: 'Embedding model', exact: true }).click()
      assert.equal(await page.getByLabel('OpenAI embedding model', { exact: true }).inputValue(), 'new-embedding')
      await save()
      const config = await page.evaluate(() => window.__settingsTest.writes[0].payload.providerConfig)
      assert.deepEqual(config, { ...providerConfig, openaiEmbeddingModel: 'new-embedding', qdrantHost: 'https://new-vector.example.test' })
      for (const provider of ['Azure OpenAI', 'Cohere', 'Gemini', 'ONNX']) {
        await choose('Embedding provider', provider)
        await save()
      }
      for (const strategy of ['Pinecone', 'Weaviate', 'Milvus', 'Lucene', 'Memory', 'Qdrant']) {
        await page.getByRole('button', { name: 'Search service', exact: true }).click()
        await choose('Search engine', strategy)
        await save()
        await page.getByRole('button', { name: 'Service connection', exact: true }).click()
        if (['Lucene', 'Memory'].includes(strategy)) await page.getByRole('alert').filter({ hasText: 'no external service endpoint' }).waitFor()
        else await waitField(strategy === 'Pinecone' ? 'Pinecone index' : `${strategy} host`)
      }
    })

    await t.test('Milvus hosting controls restore unsaved BYO settings and submit managed-only connection fields', async () => {
      await page.goto(url('storage'))
      await waitField('Search engine')
      await page.evaluate(config => {
        window.__settingsTest.draft.providerConfig = config
        window.__settingsTest.refetch(config)
      }, externalMilvusConfig)
      await page.getByLabel('Search engine', { exact: true }).filter({ hasText: 'Milvus' }).waitFor()
      await choose('Hosting', 'Platform-managed')
      await page.getByRole('button', { name: 'Customer separation', exact: true }).click()
      assert.equal(await page.getByLabel('Data isolation', { exact: true }).textContent(), 'Dedicated')
      await page.getByRole('button', { name: 'Search service', exact: true }).click()
      await choose('Hosting', 'Bring your own')
      await saved()
      await page.getByRole('button', { name: 'Customer separation', exact: true }).click()
      assert.equal(await page.getByLabel('Data isolation', { exact: true }).textContent(), 'Shared')
      await page.getByRole('button', { name: 'Service connection', exact: true }).click()
      assert.equal(await page.getByLabel('Milvus host', { exact: true }).inputValue(), externalMilvusConfig.milvusHost)
      assert.equal(await page.getByLabel('Milvus port', { exact: true }).inputValue(), '19530')
      assert.equal(await page.getByLabel('Use secure Milvus transport', { exact: true }).isChecked(), false)
      assert.equal(await page.evaluate(() => window.__settingsTest.writes.length), 0)
      await page.getByRole('button', { name: 'Search service', exact: true }).click()
      await choose('Hosting', 'Platform-managed')
      await page.getByRole('button', { name: 'Service connection', exact: true }).click()
      assert.equal(await page.getByLabel('Resolved Milvus host', { exact: true }).inputValue(), '')
      assert.equal(await page.getByLabel('Resolved Milvus port', { exact: true }).inputValue(), '443')
      await page.getByLabel('Zilliz Cloud project ID', { exact: true }).fill('project-test')
      await page.getByLabel('Zilliz Cloud region ID', { exact: true }).fill('aws-eu-central-1')
      await save()
      const config = await page.evaluate(() => window.__settingsTest.writes[0].payload.providerConfig)
      assert.equal(config.milvusExternalBindingRequired, false)
      assert.equal(config.milvusAuthenticationType, 'USERNAME_PASSWORD')
      assert.equal(config.vectorStoragePosture, 'DEDICATED')
      assert.equal(config.milvusPort, '443')
      assert.equal(config.milvusSecure, true)
      assert.equal('milvusRuntimeTokenSecretName' in config, false)
      assert.equal('milvusHost' in config, false)
      assert.equal('milvusExternalConnection' in config, false)
      assert.deepEqual(config.customExtension, externalMilvusConfig.customExtension)
      await page.getByRole('button', { name: 'Search service', exact: true }).click()
      await choose('Hosting', 'Bring your own')
      await page.getByRole('button', { name: 'Service connection', exact: true }).click()
      assert.equal(await page.getByLabel('Milvus host', { exact: true }).inputValue(), '', 'A saved managed configuration does not reuse its old BYO host')
    })

    await t.test('technical views retain recovery, provider parameters, raw unknown fields and probes', async () => {
      await page.goto(url('technical-files'))
      await waitField('Runtime profile')
      assert.equal(await page.getByLabel('LLM provider', { exact: true }).count(), 0)
      await page.getByRole('tab', { name: 'Recovery policy', exact: true }).click()
      await waitField('Unknown or unavailable action')
      await choose('Unknown or unavailable action', 'Model replan to information')
      await save()
      assert.deepEqual(await page.evaluate(() => window.__settingsTest.draft.providerConfig.orchestrationModeOverrides.executor.semanticRecovery.futurePolicy), { keep: true })
      await page.getByRole('tab', { name: 'Provider parameters', exact: true }).click()
      await waitField('OpenAI base URL')
      await page.getByRole('tab', { name: 'Provider JSON', exact: true }).click()
      const raw = page.getByLabel('Provider configuration JSON', { exact: true })
      await raw.fill('{broken')
      await page.waitForFunction(() => window.__editor?.error)
      assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).isDisabled(), true)
      assert.equal(await page.getByRole('tab', { name: 'Runtime wiring', exact: true }).isDisabled(), true)
      await page.getByRole('button', { name: 'Discard', exact: true }).click()
      await raw.fill('[]')
      await page.getByRole('alert').filter({ hasText: 'must be a JSON object' }).waitFor()
      const config = { ...providerConfig, futureProvider: { unknown: ['retained', 9] } }
      await raw.fill(JSON.stringify(config))
      await save()
      assert.deepEqual(await page.evaluate(() => window.__settingsTest.draft.providerConfig), config)
      await page.getByRole('tab', { name: 'Provider checks', exact: true }).click()
      await page.getByRole('button', { name: 'Run vendor probes', exact: true }).click()
      await page.getByText('Fixture probe complete').waitFor()
      assert.equal(await page.evaluate(() => window.__settingsTest.probes[0].id), 'brain/a & b')
    })

    await t.test('read-only and draft failures cannot save', async () => {
      await page.goto(url('models', '&readonly=1'))
      await waitField('OpenAI LLM model')
      assert.equal(await page.getByLabel('OpenAI LLM model', { exact: true }).isDisabled(), true)
      assert.equal(await page.getByLabel('LLM provider', { exact: true }).getAttribute('aria-disabled'), 'true')
      assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).isDisabled(), true)
      assert.equal(await page.evaluate(() => window.__settingsTest.writes.length), 0)
      await page.goto(url('models', '&draftError=1'))
      await page.getByRole('alert').filter({ hasText: 'Draft unavailable' }).waitFor()
      assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).isDisabled(), true)
      await page.goto(url('storage', '&draftError=1'))
      await page.getByRole('alert').filter({ hasText: 'Draft unavailable' }).waitFor()
      assert.equal(await page.getByText('Draft search:', { exact: false }).count(), 0)
      await page.getByRole('button', { name: 'Service connection', exact: true }).click()
      assert.equal(await page.getByText('no external service endpoint', { exact: false }).count(), 0)
      assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).isDisabled(), true)
    })

    await t.test('light/dark desktop and mobile stay within the viewport', async () => {
      const screenshots = await mkdtemp(path.join(tmpdir(), 'brain-settings-01064-'))
      for (const mode of ['light', 'dark']) for (const width of [1280, 390, 320]) {
        await page.setViewportSize({ width, height: 900 })
        for (const section of ['', 'models', 'storage', 'technical-files', 'access', 'delivery']) {
          await page.goto(url(section, `&mode=${mode}`))
          await page.getByRole('heading', { level: 1 }).waitFor()
          if (section === 'models') await waitField('LLM provider')
          if (section === 'storage') await waitField('Search engine')
          if (section === 'technical-files') await waitField('Runtime profile')
          assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, `${section} ${mode} ${width}`)
          if (width !== 320 && ['models', 'storage', 'technical-files'].includes(section)) {
            await page.screenshot({ path: path.join(screenshots, `${section}-${mode}-${width}.png`), fullPage: true })
          }
        }
      }
      t.diagnostic(`Responsive screenshots: ${screenshots}`)
    })
    assert.deepEqual(errors, [], 'No browser page errors')
  } finally {
    await browser?.close()
    await server.close()
  }
})
