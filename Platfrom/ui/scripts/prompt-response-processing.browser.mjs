import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer, transformWithEsbuild } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'prompt-processing-vite-'))
const output = await mkdtemp(join(tmpdir(), 'prompt-processing-ui-'))
const server = await createServer({ root, cacheDir, configFile: false, envFile: false, server: { host: '127.0.0.1', port: 0 },
  plugins: [{ name: 'prompt-processing-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === '/__processing-entry.jsx') return '\0processing-entry.jsx'
      if (id.endsWith('/workspace/DeploymentWorkspaceContext')) return '\0processing-workspace.js'
      if (id.endsWith('/workspace/useDeploymentWorkspaceEditorState')) return '\0processing-editor.js'
    },
    transform(code, id) { if (id === '\0processing-entry.jsx') return transformWithEsbuild(code, 'entry.jsx', { loader: 'jsx', jsx: 'automatic' }) },
    load(id) {
      if (id === '\0processing-workspace.js') return `export const useDeploymentWorkspace=()=>({selectedDeploymentId:'dep-fixture',workspace:{deployment:{name:'Fixture'},access:{canEdit:true,canOperate:false},draft:{revisionNumber:1}}});`
      if (id === '\0processing-editor.js') return 'export function useDeploymentWorkspaceEditorState(){}'
      if (id === '\0processing-entry.jsx') return `import React from'react';import{createRoot}from'react-dom/client';import{QueryClient,QueryClientProvider}from'@tanstack/react-query';
        import{ThemeProvider,createTheme,CssBaseline}from'@mui/material';import{PromptsPage}from'/src/pages/PromptsPage.tsx';
        const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
        createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}><ThemeProvider theme={createTheme()}><CssBaseline/><main style={{padding:16}}><PromptsPage/></main></ThemeProvider></QueryClientProvider>);`
    },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (req.url !== '/__processing') return next()
      res.setHeader('Content-Type', 'text/html')
      res.end(await vite.transformIndexHtml('/__processing', '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/__processing-entry.jsx"></script></body></html>'))
    }) },
  }],
})
let browser
try {
  await server.listen()
  const base = `http://127.0.0.1:${server.httpServer.address().port}`
  browser = await chromium.launch()
  const page = await browser.newPage({ viewport: { width: 1360, height: 1000 } }); page.setDefaultTimeout(15000)
  const errors = [], writes = []
  let draft = { id: 'drf-fixture', revisionNumber: 1, providerConfig: {}, promptConfig: { systemPrompt: 'Preserve customer boundaries.' } }
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', route => new URL(route.request().url()).origin === base ? route.continue() : route.abort())
  await page.route(base + '/api/**', route => {
    const req = route.request(), path = new URL(req.url()).pathname
    const send = value => route.fulfill({ contentType: 'application/json', body: JSON.stringify(value) })
    if (path.endsWith('/draft')) return send(draft)
    if (path === '/api/deployment-drafts/drf-fixture' && req.method() === 'PUT') {
      const body = req.postDataJSON(); writes.push(body); draft = { ...draft, ...body, revisionNumber: draft.revisionNumber + 1 }; return send(draft)
    }
    if (path.endsWith('/prompt-revisions') || path.endsWith('/deployment-curated-modules')) return send([])
    if (path.endsWith('/prompt-baseline')) return send({ promptConfig: {} })
    if (path.endsWith('/poc/prompt-session')) return send({ active: false, promptConfig: {} })
    errors.push('Unexpected ' + req.method() + ' ' + path); return route.fulfill({ status: 500, body: '{}' })
  })
  await page.goto(base + '/__processing')
  const details = page.locator('details').filter({ has: page.locator('summary', { hasText: 'Advanced response processing' }) })
  await details.locator('summary').waitFor(); assert.equal(await details.getAttribute('open'), null)
  await details.locator('summary').click()
  const toggle = page.getByRole('checkbox', { name: 'Remove generic assistance closers (phrase-based)', exact: true })
  await toggle.waitFor(); assert.equal(await toggle.isChecked(), false)
  await toggle.check(); await page.getByRole('alert').filter({ hasText: 'Phrase-based rewriting can remove useful' }).waitFor()
  await page.getByRole('button', { name: 'Save prompt draft', exact: true }).click()
  await page.waitForFunction(() => document.querySelector('button[type]') !== null)
  await page.waitForTimeout(150)
  assert.equal(writes.length, 1); assert.equal(writes[0].promptConfig.removeGenericAssistanceClosers, true)
  assert.equal(writes[0].promptConfig.systemPrompt, 'Preserve customer boundaries.')
  await page.reload(); await details.locator('summary').click(); await toggle.waitFor(); assert.equal(await toggle.isChecked(), true)
  await toggle.uncheck(); await page.getByRole('button', { name: 'Save prompt draft', exact: true }).click()
  await page.waitForTimeout(150); assert.equal(writes.at(-1).promptConfig.removeGenericAssistanceClosers, false)
  await page.setViewportSize({ width: 390, height: 844 }); await page.screenshot({ path: output + '/advanced-mobile.png', fullPage: true })
  assert.deepEqual(errors, []); console.log(JSON.stringify({ passed: true, scenarios: ['collapsed-advanced', 'default-off', 'explicit-enable-warning', 'save-reload', 'disable-save', 'existing-prompts-preserved', 'mobile-render'], screenshots: output }))
} finally { await browser?.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
