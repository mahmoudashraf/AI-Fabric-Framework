import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer, transformWithEsbuild } from 'vite'
import { chromium } from 'playwright'
import { parse, stringify } from 'lossless-json'

const root = fileURLToPath(new URL('../..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'mcp-action-editor-cache-'))
const evidence = await mkdtemp(join(tmpdir(), 'mcp-action-editor-evidence-'))
const initial = `{"actions":[{"name":"mcp_exact","displayName":"Company account details","description":"Read the requested account.","adapterType":"mcp-tool","accessMode":"READ","requiresConfirmation":true,"inputSchema":{"type":"object","properties":{"reference":{"type":"string"},"limit":{"type":"integer","maximum":9223372036854775807},"options":{"type":"object","properties":{"notify":{"type":"boolean"}},"additionalProperties":false}},"required":["reference"]},"outputSchema":{"type":"object"},"execution":{"adapterType":"mcp-tool","retry":{"maxAttempts":1},"mcp":{"toolName":"Accounts.Lookup.Exact","connectionId":"conn-owned","serverRef":"server-owned","toolSchemaHash":"hash-native"}},"params":[{"name":"reference","type":"STRING","required":true,"description":"Account reference.","askUser":true,"defaultValue":"original reference"},{"name":"limit","type":"INTEGER","required":false,"max":9223372036854775807,"defaultValue":10},{"name":"options","type":"OBJECT","required":false,"properties":{"notify":{"type":"BOOLEAN"}},"defaultValue":{"notify":false}}]},{"name":"http_lookup","adapterType":"connector-http","accessMode":"READ","params":[{"name":"orderReference","type":"STRING","required":true}],"route":{"method":"GET","path":"/orders"}}],"mcpServers":[{"serverRef":"server-owned","connectionId":"conn-owned","auth":{"mode":"BEARER_TOKEN_SECRET_REF","secretRef":"CUSTOMER_KEY_REFERENCE"}}],"future":{"keep":9007199254740997}}`
const entry = `
import React,{useState}from'react';import{createRoot}from'react-dom/client';import{BrowserRouter}from'react-router-dom';
import{Box}from'@mui/material';import{CustomerConsoleTheme}from'/src/theme/CustomerConsoleTheme.tsx';
import{BrainWorkspaceContext}from'/src/brain/BrainWorkspaceContext.tsx';import{ActionDefinitionEditor}from'/src/actions/ActionDefinitionEditor.tsx';
function App(){const[value,setValue]=useState(${JSON.stringify(initial)});window.__editorValue=value;window.__routes??=[];
 const href=(section='')=>'/smart-brains/brain-a/'+section+'?targetProfileId=stage';
 return <BrainWorkspaceContext.Provider value={{brainId:'brain-a',targetProfileId:'stage',href,targetProfile:null,release:null,target:null}}><Box sx={{maxWidth:1100,p:{xs:2,sm:3},mx:'auto'}}><ActionDefinitionEditor editorValue={value} onChange={setValue} initialActionName="mcp_exact" onEditRoute={name=>window.__routes.push(name)}/></Box></BrainWorkspaceContext.Provider>}
createRoot(document.getElementById('root')).render(<BrowserRouter><CustomerConsoleTheme><App/></CustomerConsoleTheme></BrowserRouter>);
`
const server = await createServer({ root, cacheDir, configFile: false, envFile: false, resolve: { dedupe: ['react', 'react-dom'] }, logLevel: 'error', server: { host: '127.0.0.1', port: 0, hmr: false }, plugins: [{
  name: 'mcp-action-editor-fixture', enforce: 'pre', resolveId(id) { if (id === '/__mcp-action.jsx') return '\0mcp-action.jsx' },
  async load(id) { if (id === '\0mcp-action.jsx') return (await transformWithEsbuild(entry, 'mcp-action.jsx', { loader: 'jsx', jsx: 'automatic' })).code },
  configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
    if (!req.url.startsWith('/smart-brains/')) return next()
    res.setHeader('Content-Type', 'text/html')
    res.end(await vite.transformIndexHtml('/', '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{margin:0}</style></head><body><div id="root"></div><script type="module" src="/__mcp-action.jsx"></script></body></html>'))
  }) },
}] })
let browser
await server.listen()
const base = `http://127.0.0.1:${server.httpServer.address().port}`
try {
  browser = await chromium.launch({ headless: true })
  for (const [width, dark] of [[1440, true], [1440, false], [390, true]]) {
    const page = await browser.newPage({ viewport: { width, height: 1000 } })
    const errors = [], external = [], apiCalls = []
    page.setDefaultTimeout(12000)
    page.on('pageerror', error => errors.push(error.message))
    await page.addInitScript(value => localStorage.setItem('loomai-customer-console-color-scheme', value ? 'dark' : 'light'), dark)
    await page.route('**/*', route => new URL(route.request().url()).origin === base ? route.continue() : (external.push(route.request().url()), route.abort()))
    await page.route('**/api/**', route => { apiCalls.push(route.request().url()); return route.abort() })
    try {
      await page.goto(`${base}/smart-brains/brain-a/capabilities/actions?targetProfileId=stage`)
      await page.getByRole('heading', { name: 'Action definitions' }).waitFor()
      const destination = page.getByRole('region', { name: 'MCP destination' })
      await destination.getByText('Accounts.Lookup.Exact', { exact: true }).waitFor()
      await destination.getByText('conn-owned', { exact: true }).waitFor()
      assert.equal(await page.getByRole('button', { name: /^HTTP route for/ }).count(), 0)
      assert.equal(await destination.getByRole('link', { name: 'View MCP connection' }).getAttribute('href'), '/smart-brains/brain-a/capabilities?targetProfileId=stage&view=mcp&mcp=conn-owned')
      await page.getByRole('button', { name: 'Native MCP schemas (read-only)', exact: true }).click()
      assert.match(await page.getByLabel('MCP native input schema').innerText(), /9223372036854775807/)
      assert.equal(await destination.locator('textarea').count(), 0)
      await page.screenshot({ path: join(evidence, `destination-${width}-${dark ? 'dark' : 'light'}.png`), fullPage: true })
      await page.getByRole('button', { name: 'Input parameters (3)', exact: true }).click()
      assert.equal(await page.getByRole('button', { name: 'Add parameter', exact: true }).count(), 0)
      assert.equal(await page.getByRole('button', { name: 'Remove parameter 1', exact: true }).count(), 0)
      assert.equal(await page.getByRole('textbox', { name: 'Parameter name', exact: false }).first().getAttribute('readonly'), '')
      assert.equal(await page.getByRole('combobox', { name: /^Type/ }).first().isDisabled(), true)
      assert.equal(await page.getByRole('checkbox', { name: 'Required', exact: true }).first().isDisabled(), true)
      await page.getByRole('textbox', { name: 'Parameter description', exact: true }).first().fill('Keep this locally configured description')
      await page.getByRole('checkbox', { name: 'Ask user for missing value', exact: true }).first().uncheck()
      await page.getByRole('combobox', { name: /^Source/ }).first().click()
      await page.getByRole('option', { name: 'INTERNAL', exact: true }).click()
      await page.getByRole('button', { name: 'Local default & sensitivity', exact: true }).nth(1).click()
      await page.getByRole('button', { name: 'Edit Default value for parameter 2', exact: true }).click()
      await page.getByRole('textbox', { name: 'Default value JSON', exact: true }).fill('9007199254740993')
      await page.getByRole('button', { name: 'Apply to buffer', exact: true }).click()
      await page.getByRole('button', { name: 'Local default & sensitivity', exact: true }).nth(2).click()
      await page.getByRole('button', { name: 'Edit Default value for parameter 3', exact: true }).click()
      await page.getByRole('textbox', { name: 'Default value JSON', exact: true }).fill('{"notify":true,"id":9007199254740999,"items":[false,2,"three"]}')
      await page.getByRole('button', { name: 'Apply to buffer', exact: true }).click()
      const after = parse(await page.evaluate(() => window.__editorValue)), before = parse(initial)
      assert.equal(after.actions[0].params[0].description, 'Keep this locally configured description')
      assert.equal(after.actions[0].params[0].askUser, false)
      assert.equal(after.actions[0].params[0].visibility, 'INTERNAL')
      assert.equal(String(after.actions[0].params[1].defaultValue), '9007199254740993')
      assert.equal(after.actions[0].params[2].defaultValue.notify, true)
      assert.equal(String(after.actions[0].params[2].defaultValue.id), '9007199254740999')
      assert.equal(stringify(after.actions[0].inputSchema), stringify(before.actions[0].inputSchema))
      assert.equal(stringify(after.actions[0].execution), stringify(before.actions[0].execution))
      assert.equal(stringify(after.mcpServers), stringify(before.mcpServers))
      assert.equal(stringify(after.actions[1]), stringify(before.actions[1]))
      await page.getByRole('button', { name: 'Advanced action JSON', exact: true }).click()
      const localInput = page.getByRole('textbox', { name: 'Local action settings JSON', exact: true })
      const local = parse(await localInput.inputValue())
      assert.equal(local.inputSchema, undefined); assert.equal(local.outputSchema, undefined); assert.equal(local.execution, undefined)
      local.description = 'Changed through the local JSON editor'
      await localInput.fill(stringify(local, undefined, 2))
      await page.getByRole('button', { name: 'Apply to buffer', exact: true }).click()
      assert.equal(parse(await page.evaluate(() => window.__editorValue)).actions[0].description, 'Changed through the local JSON editor')
      await page.getByRole('button', { name: 'Advanced action JSON', exact: true }).click()
      const invalid = parse(await localInput.inputValue()); invalid.inputSchema = { type: 'object' }
      await localInput.fill(stringify(invalid, undefined, 2))
      await page.getByRole('button', { name: 'Apply to buffer', exact: true }).click()
      await page.getByText(/MCP destination and native schemas are read-only here/).waitFor()
      await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click()
      await page.getByRole('combobox', { name: /^Action(?:\s|$)/ }).click()
      await page.getByRole('option', { name: 'http_lookup', exact: true }).click()
      await page.getByRole('button', { name: 'HTTP route for http_lookup', exact: true }).click()
      assert.deepEqual(await page.evaluate(() => window.__routes), ['http_lookup'])
      assert.equal(await page.getByRole('region', { name: 'MCP destination' }).count(), 0)
      await page.getByRole('button', { name: 'Input parameters (1)', exact: true }).click()
      assert.equal(await page.getByRole('button', { name: 'Add parameter', exact: true }).isEnabled(), true)
      assert.equal(await page.getByRole('button', { name: 'Remove parameter 1', exact: true }).isEnabled(), true)
      assert.equal(await page.getByRole('combobox', { name: /^Type/ }).isEnabled(), true)
      await page.getByRole('button', { name: 'Advanced action JSON', exact: true }).click()
      const httpInput = page.getByRole('textbox', { name: 'Action definition JSON', exact: true })
      assert.equal(parse(await httpInput.inputValue()).route.path, '/orders')
      await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click()
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true)
      assert.deepEqual(errors, []); assert.deepEqual(external, []); assert.deepEqual(apiCalls, [], 'configuration editing must not call business tools')
      process.stdout.write(`PASS action editor ${width} ${dark ? 'dark' : 'light'}: exact destination, typed inputs, protected native schema, HTTP regression\n`)
    } catch (error) { await page.screenshot({ path: join(evidence, `FAILED-${width}-${dark ? 'dark' : 'light'}.png`), fullPage: true }); process.stdout.write(`Failure evidence: ${evidence}\n`); throw error }
    finally { await page.close() }
  }
  process.stdout.write(`Action editor evidence: ${evidence}\n`)
} finally { await browser?.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
