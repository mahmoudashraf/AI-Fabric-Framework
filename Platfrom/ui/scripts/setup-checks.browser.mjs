import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer, transformWithEsbuild } from 'vite'
import { chromium } from 'playwright'

// Real summary, query hook and API helper; saves and validation are in-memory fixtures.
const root = fileURLToPath(new URL('..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'setup-checks-vite-'))
const output = await mkdtemp(join(tmpdir(), 'setup-checks-evidence-'))
const server = await createServer({ root, cacheDir, configFile: false, envFile: false,
  resolve: { dedupe: ['react', 'react-dom'] }, server: { host: '127.0.0.1', port: 0, hmr: false }, logLevel: 'error',
  plugins: [{ name: 'setup-checks-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === '/__setup-entry.jsx') return '\0setup-entry.jsx'
      if (id === '../auth/PlatformAuthProvider') return '\0setup-auth.js'
    },
    async load(id) {
      if (id === '\0setup-auth.js') return 'export function usePlatformAuth(){return {session:window.__actor}}'
      if (id !== '\0setup-entry.jsx') return
      return (await transformWithEsbuild(`
        import React,{useState} from 'react';import {createRoot} from 'react-dom/client';
        import {QueryClient,QueryClientProvider,useMutation} from '@tanstack/react-query';
        import {ThemeProvider,CssBaseline,Stack,Button,TextField,Alert,Typography} from '@mui/material';
        import {createConsoleTheme} from '/src/theme/consoleTheme.ts';
        import {platformRequest,setPlatformAccessTokenProvider} from '/src/api/platformApi.ts';
        import {useBrainSetupChecks} from '/src/brain/setupChecks.ts';
        import {BrainSetupChecks} from '/src/brain/BrainSetupChecks.tsx';
        window.__actor={actorId:'actor-a',customerId:'customer-a'};
        setPlatformAccessTokenProvider(async()=>window.__actor.actorId);
        const client=new QueryClient({defaultOptions:{queries:{retry:false,refetchOnWindowFocus:false},mutations:{retry:false}}});window.__client=client;
        const readonly=new URLSearchParams(location.search).has('readonly');
        function Panel({view,revision,onRevision}){
          const checks=useBrainSetupChecks('brain-a',{enabled:view==='updates'});
          const [input,setInput]=useState('');const [saved,setSaved]=useState(false);
          const validation=useMutation({mutationFn:()=>platformRequest('/api/deployment-drafts/draft-a/validate',{method:'POST'} )});
          const save=useMutation({mutationFn:()=>platformRequest('/api/deployment-drafts/draft-a',{method:'PUT',body:JSON.stringify({value:input})}),
            onSuccess:draft=>{setSaved(true);onRevision(draft.revisionNumber);checks.runAfterSave(draft.deploymentId)}});
          return <Stack gap={2}>
            <Typography component='h1' fontSize={22}>{view==='updates'?'Updates':'Connection'}</Typography>
            {view==='updates'&&!readonly&&<Button variant='outlined' disabled={validation.isPending||checks.pending}
              onClick={()=>{validation.mutate();void checks.run('ALL').catch(()=>undefined)}}>Check setup</Button>}
            {validation.isSuccess&&<Typography>Configuration valid</Typography>}
            {view==='connection'&&<><TextField label='Connection label' value={input} onChange={e=>setInput(e.target.value)} disabled={readonly}/>
              {!readonly&&<Button disabled={save.isPending} onClick={()=>save.mutate()}>Save configuration</Button>}</>}
            {saved&&<Alert severity='success'>Configuration saved.</Alert>}{save.isError&&<Alert severity='error'>Configuration was not saved.</Alert>}
            <BrainSetupChecks checks={checks} canCheck={!readonly} draftId='draft-a' revisionNumber={revision}/>
          </Stack>
        }
        function App(){const [view,setView]=useState(new URLSearchParams(location.search).get('view')||'updates');const [revision,setRevision]=useState(3);const [actor,setActor]=useState(0);
          return <Stack gap={2} sx={{p:2,maxWidth:850,mx:'auto',minWidth:0}}>
            <Stack direction='row' gap={1} flexWrap='wrap'><Button onClick={()=>setView('updates')}>Updates</Button><Button onClick={()=>setView('connection')}>Connection</Button>
              <Button onClick={()=>setRevision(r=>r+1)}>Newer draft</Button><Button onClick={()=>{client.clear();window.__actor={actorId:'actor-b',customerId:'customer-b'};setActor(a=>a+1)}}>Switch account</Button></Stack>
            <Panel key={view+actor} view={view} revision={revision} onRevision={setRevision}/>
          </Stack>}
        createRoot(document.getElementById('root')).render(<React.StrictMode><QueryClientProvider client={client}><ThemeProvider theme={createConsoleTheme(new URLSearchParams(location.search).get('theme')==='light'?'light':'dark')}><CssBaseline/><App/></ThemeProvider></QueryClientProvider></React.StrictMode>);
      `, 'setup-entry.jsx', { loader: 'jsx', jsx: 'automatic' })).code
    },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (!new URL(req.url, 'http://fixture').pathname.startsWith('/fixture')) return next()
      res.setHeader('Content-Type', 'text/html')
      res.end(await vite.transformIndexHtml('/__setup', '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin};</script><script type="module" src="/__setup-entry.jsx"></script></body></html>'))
    }) },
  }],
})

let browser, passed = 0, base
const service = (id, status, extra = {}) => ({ id, label: id === 'inventory' ? 'Inventory API' : id === 'docs' ? 'Knowledge storage' : 'AI model', status,
  message: status === 'FAILED' ? 'Authorization rejected.' : status === 'VERIFIED' ? 'Safe connection check succeeded.' : 'Inference is not executed by setup checks.',
  checkedAt: status === 'NOT_TESTED' ? null : new Date().toISOString(), usedBy: id === 'inventory' ? ['Search stock', 'Create enquiry (not executed)'] : [],
  httpStatus: status === 'FAILED' ? 401 : status === 'VERIFIED' ? 200 : null, ...extra })
const result = (revision = 3, services = [service('inventory', 'VERIFIED'), service('docs', 'FAILED'), service('model', 'NOT_TESTED')]) => ({
  deploymentId: 'brain-a', draftId: 'draft-a', revisionNumber: revision, checkedAt: new Date().toISOString(), services,
  verifiedCount: services.filter(s => s.status === 'VERIFIED').length,
  failedCount: services.filter(s => s.status === 'FAILED').length, notTestedCount: services.filter(s => s.status === 'NOT_TESTED').length,
})
async function run(name, test, options = {}) {
  const page = await browser.newPage({ viewport: options.viewport ?? { width: 1280, height: 900 } })
  page.setDefaultTimeout(20000)
  if (options.clock) await page.clock.install()
  const calls = [], errors = [], unexpected = []
  let revision = 3, recorded = options.result ?? result(), failCheck = false, failSave = false, hold = false, release = null
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', route => new URL(route.request().url()).origin === base ? route.continue() : (unexpected.push(route.request().url()), route.abort()))
  await page.route(`${base}/api/**`, async route => {
    const req = route.request(), path = new URL(req.url()).pathname, method = req.method(), body = req.postDataJSON()
    calls.push({ path, method, body })
    const send = (payload, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(payload) })
    if (path === '/api/deployments/brain-a/setup-checks') {
      if (method === 'GET') return send(recorded)
      if (method === 'POST') {
        if (hold) await new Promise(resolve => { release = resolve })
        if (failCheck) { failCheck = false; return send({ message: 'Safe probe unavailable.' }, 503) }
        recorded = result(revision, body.mode === 'FAILED' ? [service('inventory', 'VERIFIED'), service('docs', 'VERIFIED'), service('model', 'NOT_TESTED')] : undefined)
        return send(recorded)
      }
    }
    if (path === '/api/deployment-drafts/draft-a/validate' && method === 'POST') return send({ publishReady: true })
    if (path === '/api/deployment-drafts/draft-a' && method === 'PUT') {
      if (failSave) { failSave = false; return send({ message: 'Not saved.' }, 409) }
      return send({ deploymentId: 'brain-a', id: 'draft-a', revisionNumber: ++revision })
    }
    unexpected.push(`${method} ${path}`); return send({ message: 'Unexpected request' }, 404)
  })
  try {
    await page.goto(`${base}/fixture?${new URLSearchParams({ theme: options.theme ?? 'dark', view: options.view ?? 'updates', ...(options.readonly ? { readonly: '1' } : {}) })}`)
    await page.getByRole('heading').waitFor()
    await test({ page, calls, failCheck: () => { failCheck = true }, failSave: () => { failSave = true },
      holdCheck: () => { hold = true }, releaseCheck: () => { hold = false; release?.() },
      setResult: next => { recorded = next } })
    assert.deepEqual(errors, []); assert.deepEqual(unexpected, [])
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    if (await page.getByRole('dialog', { name: 'Connection checks', exact: true }).count()) {
      await page.getByRole('dialog', { name: 'Connection checks', exact: true }).evaluate(node => Promise.all(node.getAnimations({subtree:true}).filter(a => a.playState === 'running').map(a => a.finished.catch(() => {}))))
    }
    await page.screenshot({ path: join(output, `${++passed}.png`), fullPage: true })
    console.log(`PASS ${name}`)
  } catch (error) {
    release?.()
    await page.screenshot({ path: join(output, 'failure.png'), fullPage: true })
    console.error({ name, errors, unexpected, output }); throw error
  } finally { await page.close() }
}
const posts = calls => calls.filter(c => c.path.endsWith('/setup-checks') && c.method === 'POST')
async function waitFor(assertion) {
  for (let i = 0; i < 100; i++) { if (await assertion()) return; await new Promise(resolve => setTimeout(resolve, 50)) }
  assert.fail('Expected browser state was not reached.')
}
await server.listen()
base = server.resolvedUrls.local[0].replace(/\/$/, '')
try {
  browser = await chromium.launch()
  await run('recorded GET only; one combined check and failed-only retry', async ({ page, calls }) => {
    await page.getByText('Connections: 1 verified · 1 failed · 1 not tested', { exact: true }).waitFor()
    assert.equal(posts(calls).length, 0)
    await page.getByRole('button', { name: 'Check setup', exact: true }).click()
    await waitFor(() => posts(calls).length === 1)
    assert.equal(posts(calls)[0].body.mode, 'ALL')
    assert.equal(calls.filter(c => c.path.endsWith('/validate')).length, 1)
    await page.getByText('Configuration valid', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Details', exact: true }).click()
    await page.getByText('Inventory API', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: /Test connection/ }).count(), 0)
    assert.equal(await page.getByRole('button', { name: 'Retry failed checks', exact: true }).count(), 1)
    await page.getByRole('button', { name: 'Retry failed checks', exact: true }).click()
    await page.getByText('Connections: 2 verified · 0 failed · 1 not tested', { exact: true }).waitFor()
    assert.deepEqual(posts(calls).map(c => c.body.mode), ['ALL', 'FAILED'])
  })
  await run('one CHANGED run after save; typing and navigation do not probe', async ({ page, calls }) => {
    await page.getByLabel('Connection label').fill('Updated inventory')
    assert.equal(posts(calls).length, 0)
    await page.getByRole('button', { name: 'Save configuration', exact: true }).click()
    await page.getByText('Configuration saved.', { exact: true }).waitFor()
    await page.getByText('Connections: 1 verified · 1 failed · 1 not tested', { exact: true }).waitFor()
    assert.deepEqual(posts(calls).map(c => c.body.mode), ['CHANGED'])
    await page.getByRole('button', { name: 'Updates', exact: true }).click()
    await page.getByText('Connections: 1 verified · 1 failed · 1 not tested', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Connection', exact: true }).click()
    assert.deepEqual(posts(calls).map(c => c.body.mode), ['CHANGED'])
  }, { view: 'connection' })
  await run('check failure remains separate from successful save; failed save never probes', async ({ page, calls, failCheck, failSave }) => {
    failCheck()
    await page.getByRole('button', { name: 'Save configuration', exact: true }).click()
    await page.getByText('Configuration saved.', { exact: true }).waitFor()
    await page.getByText(/Configuration saved\. Setup checks could not complete/).waitFor()
    assert.equal(await page.getByText('Configuration was not saved.', { exact: true }).count(), 0)
    failSave()
    await page.getByRole('button', { name: 'Save configuration', exact: true }).click()
    await page.getByText('Configuration was not saved.', { exact: true }).waitFor()
    assert.equal(posts(calls).length, 1)
  }, { view: 'connection' })
  await run('different draft revision is never presented as current', async ({ page }) => {
    await page.getByText('Connections: 1 verified · 1 failed · 1 not tested', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Newer draft', exact: true }).click()
    await page.getByText('Previous check: 1 verified · 1 failed · 1 not tested', { exact: true }).waitFor()
    await page.getByText(/These results are from another draft revision/).waitFor()
  })
  await run('expired evidence is visibly historical; metadata polling never starts probes', async ({ page, calls }) => {
    await page.getByText('Previous check: 1 verified · 1 failed · 1 not tested', { exact: true }).waitFor()
    await page.getByText('These connection results have expired. Check setup again.', { exact: true }).waitFor()
    const before = calls.filter(c => c.method === 'GET').length
    await page.clock.fastForward(31_000)
    await waitFor(() => calls.filter(c => c.method === 'GET').length > before)
    assert.equal(posts(calls).length, 0)
  }, { clock: true, result: result(3, [service('inventory', 'VERIFIED', { checkedAt: new Date(Date.now() - 6 * 60_000).toISOString() }), service('docs', 'FAILED'), service('model', 'NOT_TESTED')]) })
  await run('account replacement discards an in-flight previous actor result', async ({ page, holdCheck, releaseCheck, setResult, calls }) => {
    await page.getByText('Connections: 1 verified · 1 failed · 1 not tested', { exact: true }).waitFor()
    holdCheck()
    await page.getByRole('button', { name: 'Check setup', exact: true }).click()
    await waitFor(() => posts(calls).length === 1)
    setResult(result(3, [service('account-b', 'NOT_TESTED', { label: 'Account B service' })]))
    await page.getByRole('button', { name: 'Switch account', exact: true }).click()
    await page.getByText('Connections: 0 verified · 0 failed · 1 not tested', { exact: true }).waitFor()
    releaseCheck()
    await page.getByRole('button', { name: 'Details', exact: true }).click()
    await page.getByText('Account B service', { exact: true }).waitFor()
    assert.equal(await page.getByText('Inventory API', { exact: true }).count(), 0)
    assert.equal(await page.evaluate(() => window.__client.getQueriesData({queryKey:['deployment-setup-checks','actor-a']}).length), 0)
  })
  await run('read-only, narrow light layout has details but no execution controls', async ({ page, calls }) => {
    await page.getByRole('button', { name: 'Details', exact: true }).click()
    await page.getByText('Inventory API', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Check setup', exact: true }).count(), 0)
    assert.equal(await page.getByRole('button', { name: 'Retry failed checks', exact: true }).count(), 0)
    assert.equal(posts(calls).length, 0)
  }, { readonly: true, viewport: { width: 390, height: 844 }, theme: 'light' })

  const updates = await readFile(join(root, 'src/brain/BrainUpdatesPage.tsx'), 'utf8')
  assert.ok(updates.includes('validate.mutate()') && updates.includes("setupChecks.run('ALL')"))
  assert.ok(!updates.includes('>Check draft</Button>'))
  for (const file of ['pages/ProvidersPage.tsx', 'pages/ActionsPage.tsx', 'pages/MarketplacePage.tsx']) {
    const source = await readFile(join(root, 'src', file), 'utf8')
    assert.ok(source.includes('setupChecks.runAfterSave('), `${file} must wire save-triggered checks`)
    assert.ok(!source.includes("setupChecks.run('ALL')"), `${file} must not duplicate Check setup`)
  }
  console.log(`PASS setup-check save/Updates integration wiring; ${passed} browser scenarios; evidence: ${output}`)
} finally {
  await browser?.close()
  await server.close()
  await rm(cacheDir, { recursive: true, force: true })
}
