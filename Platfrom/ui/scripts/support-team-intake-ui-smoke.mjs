import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'
import { mkdir } from 'node:fs/promises'

const root = fileURLToPath(new URL('..', import.meta.url))
const entry = '\0desk-handoff-fixture.jsx', authModule = '\0desk-handoff-auth.jsx'
const evidence = process.env.SUPPORT_UI_OUTPUT || '/private/tmp/loomai-support-ui-01057'
await mkdir(evidence, { recursive: true })
const calls = [], errors = []
const config = { enabled: true, deskSignInUrl: 'https://desk.example.test/',
  deskClients: [{ clientId: 'registered-desk', redirectUris: ['https://desk.example.test/login/oauth2/code/supabase'] }] }
const server = await createServer({ root, configFile: false, envFile: false, logLevel: 'error', esbuild: { jsx: 'automatic' },
  server: { host: '127.0.0.1', port: 0 }, plugins: [{ name: 'desk-handoff-fixture', enforce: 'pre',
    resolveId(id) { if (id === '/desk-handoff-fixture.jsx') return entry; if (id.endsWith('/auth/PlatformAuthProvider')) return authModule },
    load(id) {
      if (id === entry) return `import React from'react';import{createRoot}from'react-dom/client';import{BrowserRouter}from'react-router-dom';
        import{SupportTeamIntakePage}from'/src/pages/SupportTeamIntakePage.tsx';window.__deskConfig=${JSON.stringify(config)};
        createRoot(document.getElementById('root')).render(React.createElement(BrowserRouter,null,React.createElement(SupportTeamIntakePage)));`
      if (id === authModule) return `import{useEffect,useState}from'react';export function usePlatformAuth(){const[,render]=useState(0);
        useEffect(()=>{const fn=()=>render(n=>n+1);addEventListener('fixture-config',fn);return()=>removeEventListener('fixture-config',fn)},[]);
        return{session:window.__platformAdmin?{authenticated:true,role:'PLATFORM_ADMIN',actorId:'admin'}:null,customerAuthLoading:false,customerConfig:window.__deskConfig};}`
    },
    configureServer(vite) { vite.middlewares.use((req, res, next) => {
      if (new URL(req.url, 'http://fixture').pathname !== '/support/team-intake') return next()
      res.setHeader('Content-Type', 'text/html')
      res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0"><div id="root"></div><script type="module" src="/desk-handoff-fixture.jsx"></script></body></html>')
    }) },
  }] })
await server.listen()
const browser = await chromium.launch({ headless: true })
let page
try {
  page = await browser.newPage({ viewport: { width: 1450, height: 940 } })
  page.setDefaultTimeout(15_000)
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    calls.push(path)
    return route.fulfill({ status: 410, json: { message: 'Retired fixture API must not be called.' } })
  })
  await page.goto(server.resolvedUrls.local[0] + 'support/team-intake')
  const desk = page.getByRole('link', { name: 'Open LoomAI Desk', exact: true })
  await desk.waitFor({ timeout: 60_000 })
  assert.equal(await desk.getAttribute('href'), 'https://desk.example.test/oauth2/authorization/supabase')
  assert.equal(await page.getByRole('textbox').count(), 0)
  assert.equal(await page.getByRole('table').count(), 0)
  assert.equal(await page.locator('iframe').count(), 0)
  assert.deepEqual(calls, [])
  await page.screenshot({ path: `${evidence}/05-desk-handoff.png`, fullPage: true })
  for (const invalid of [{ ...config, deskSignInUrl: 'javascript:alert(1)' }, { ...config, deskClients: [] }, { ...config, enabled: false }]) {
    await page.evaluate(value => { window.__deskConfig = value; dispatchEvent(new Event('fixture-config')) }, invalid)
    await page.getByRole('alert').filter({ hasText: 'Desk sign-in is not configured.' }).waitFor()
    assert.equal(await desk.count(), 0)
  }
  await page.evaluate(value => { window.__deskConfig = value; window.__platformAdmin = true; dispatchEvent(new Event('fixture-config')) }, config)
  await desk.waitFor()
  assert.equal(await page.getByRole('table').count(), 0, 'Platform admin is not a Desk authorization grant')
  await page.setViewportSize({ width: 390, height: 844 })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
  await page.screenshot({ path: `${evidence}/06-desk-handoff-mobile.png`, fullPage: true })
  let authorization
  await page.route('https://desk.example.test/oauth2/authorization/supabase', route => {
    authorization = route.request().headers().authorization
    return route.fulfill({ contentType: 'text/html', body: '<main>Existing Desk sign-in</main>' })
  })
  await desk.click()
  await page.getByText('Existing Desk sign-in').waitFor()
  assert.equal(authorization, undefined)
  assert.deepEqual(calls, [])
  assert.deepEqual(errors, [])
  console.log('Desk handoff smoke passed: registered navigation, unsafe/missing/disabled destinations denied, no staff ticket API/UI, no inherited admin authority, mobile layout and no bearer forwarding.')
} finally { await browser.close(); await server.close() }
